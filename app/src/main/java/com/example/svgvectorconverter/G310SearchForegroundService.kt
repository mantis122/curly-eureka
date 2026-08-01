package com.example.svgvectorconverter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the expensive G3.10 search in a foreground service.
 *
 * The service pauses cooperatively when Android reports a severe thermal state
 * or when the battery temperature reaches the fallback threshold. While paused,
 * its partial wake lock is released so the phone can cool normally.
 */
class G310SearchForegroundService : Service() {
    private val stateLock = Object()
    private val stopRequested = AtomicBoolean(false)
    private var manuallyPaused = false
    private var thermallyPaused = false
    private var workerThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var thermalListener: Any? = null
    private var lastThermalStatus = -1
    private var lastBatteryTemperatureC: Float? = null
    @Volatile private var lastBatteryPollNanos: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerThermalListener()
        updateBatteryTemperature()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startSearchIfNeeded()
            ACTION_PAUSE -> setManualPause(true)
            ACTION_RESUME -> setManualPause(false)
            ACTION_STOP -> requestStop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        requestStop()
        unregisterThermalListener()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startSearchIfNeeded() {
        synchronized(stateLock) {
            if (workerThread?.isAlive == true) {
                publishSnapshot()
                return
            }
            stopRequested.set(false)
            manuallyPaused = false
            thermallyPaused = shouldThermallyPause()
            SnapshotStore.update(
                Snapshot(
                    status = if (thermallyPaused) Status.THERMAL_PAUSED else Status.RUNNING,
                    completedCases = 0,
                    totalCases = TOTAL_CASES,
                    workerCount = WORKER_COUNT,
                    perSeedProcessed = List(SEED_COUNT) { 0 },
                    thermalStatus = lastThermalStatus,
                    batteryTemperatureC = lastBatteryTemperatureC,
                    message = statusMessage()
                )
            )
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        updateWakeLockForState()

        workerThread = Thread({
            val report = try {
                SvgBidirectionalPolylineGeometrySearch.runDefault(
                    progressCallback = { progress ->
                        updateBatteryTemperature()
                        synchronized(stateLock) {
                            SnapshotStore.update(
                                Snapshot(
                                    status = currentStatus(),
                                    completedCases = progress.completedCases,
                                    totalCases = progress.totalCases,
                                    workerCount = progress.workerCount,
                                    perSeedProcessed = progress.perSeedProcessed,
                                    thermalStatus = lastThermalStatus,
                                    batteryTemperatureC = lastBatteryTemperatureC,
                                    message = statusMessage()
                                )
                            )
                        }
                        publishSnapshot()
                    },
                    controlCheckpoint = { awaitRunnableState() }
                )
            } catch (_: CancellationException) {
                null
            } catch (_: InterruptedException) {
                null
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.10 automated bidirectional polyline geometry comparator differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                }
            }

            if (report != null && !stopRequested.get()) {
                saveReport(report)
                synchronized(stateLock) {
                    val previous = SnapshotStore.snapshot
                    SnapshotStore.update(
                        previous.copy(
                            status = Status.COMPLETED,
                            completedCases = previous.totalCases,
                            reportAvailable = true,
                            message = "G3.10 completed"
                        )
                    )
                }
            } else {
                synchronized(stateLock) {
                    val previous = SnapshotStore.snapshot
                    SnapshotStore.update(
                        previous.copy(
                            status = Status.STOPPED,
                            message = "G3.10 stopped"
                        )
                    )
                }
            }
            releaseWakeLock()
            publishSnapshot()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }, "G3.10-search-service").apply { start() }
    }

    private fun awaitRunnableState() {
        val now = System.nanoTime()
        if (now - lastBatteryPollNanos >= BATTERY_POLL_INTERVAL_NANOS) {
            lastBatteryPollNanos = now
            updateBatteryTemperature()
        }
        synchronized(stateLock) {
            while (!stopRequested.get() && (manuallyPaused || thermallyPaused)) {
                updateWakeLockForState()
                try {
                    stateLock.wait(1_000L)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                }
                updateBatteryTemperature()
                refreshThermalPauseFromFallback()
            }
            if (stopRequested.get()) throw CancellationException("G3.10 stopped")
            updateWakeLockForState()
        }
    }

    private fun setManualPause(paused: Boolean) {
        synchronized(stateLock) {
            manuallyPaused = paused
            stateLock.notifyAll()
            val previous = SnapshotStore.snapshot
            SnapshotStore.update(previous.copy(status = currentStatus(), message = statusMessage()))
        }
        updateWakeLockForState()
        publishSnapshot()
    }

    private fun requestStop() {
        stopRequested.set(true)
        synchronized(stateLock) { stateLock.notifyAll() }
        workerThread?.interrupt()
        releaseWakeLock()
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            synchronized(stateLock) {
                lastThermalStatus = status
                thermallyPaused = status >= PowerManager.THERMAL_STATUS_SEVERE ||
                    ((lastBatteryTemperatureC ?: 0f) >= PAUSE_TEMPERATURE_C)
                stateLock.notifyAll()
                val previous = SnapshotStore.snapshot
                SnapshotStore.update(previous.copy(
                    status = currentStatus(),
                    thermalStatus = status,
                    batteryTemperatureC = lastBatteryTemperatureC,
                    message = statusMessage()
                ))
            }
            updateWakeLockForState()
            publishSnapshot()
        }
        thermalListener = listener
        powerManager.addThermalStatusListener(mainExecutor, listener)
        lastThermalStatus = powerManager.currentThermalStatus
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        powerManager.removeThermalStatusListener(listener)
        thermalListener = null
    }

    private fun updateBatteryTemperature() {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenthsC = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?: Int.MIN_VALUE
        var thermalStateChanged = false
        synchronized(stateLock) {
            if (tenthsC != Int.MIN_VALUE && tenthsC > 0) {
                lastBatteryTemperatureC = tenthsC / 10f
            }
            val previousThermalPause = thermallyPaused
            refreshThermalPauseFromFallback()
            thermalStateChanged = previousThermalPause != thermallyPaused
            if (thermalStateChanged) {
                val previous = SnapshotStore.snapshot
                SnapshotStore.update(previous.copy(
                    status = currentStatus(),
                    thermalStatus = lastThermalStatus,
                    batteryTemperatureC = lastBatteryTemperatureC,
                    message = statusMessage()
                ))
                stateLock.notifyAll()
            }
        }
        if (thermalStateChanged && workerThread?.isAlive == true) {
            updateWakeLockForState()
            publishSnapshot()
        }
    }

    private fun refreshThermalPauseFromFallback() {
        val temperature = lastBatteryTemperatureC
        val severeStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            lastThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        thermallyPaused = when {
            severeStatus -> true
            temperature == null -> false
            thermallyPaused && temperature > RESUME_TEMPERATURE_C -> true
            temperature >= PAUSE_TEMPERATURE_C -> true
            else -> false
        }
    }

    private fun shouldThermallyPause(): Boolean {
        val severeStatus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            lastThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        return severeStatus || ((lastBatteryTemperatureC ?: 0f) >= PAUSE_TEMPERATURE_C)
    }

    private fun currentStatus(): Status = when {
        stopRequested.get() -> Status.STOPPED
        thermallyPaused -> Status.THERMAL_PAUSED
        manuallyPaused -> Status.MANUAL_PAUSED
        else -> Status.RUNNING
    }

    private fun statusMessage(): String {
        val temp = lastBatteryTemperatureC?.let { String.format(Locale.US, "%.1f°C", it) }
        return when (currentStatus()) {
            Status.THERMAL_PAUSED -> "Cooling pause${temp?.let { " at $it" } ?: ""}; resumes automatically"
            Status.MANUAL_PAUSED -> "Paused by user${temp?.let { " • $it" } ?: ""}"
            Status.RUNNING -> "Running${temp?.let { " • $it" } ?: ""}"
            Status.COMPLETED -> "G3.10 completed"
            Status.STOPPED -> "G3.10 stopped"
            Status.IDLE -> "G3.10 idle"
        }
    }

    private fun updateWakeLockForState() {
        if (currentStatus() == Status.RUNNING) acquireWakeLock() else releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:G3.10Search"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun publishSnapshot() {
        val snapshot = SnapshotStore.snapshot
        val intent = Intent(ACTION_STATE_CHANGED).setPackage(packageName).apply {
            putExtra(EXTRA_STATUS, snapshot.status.name)
            putExtra(EXTRA_COMPLETED, snapshot.completedCases)
            putExtra(EXTRA_TOTAL, snapshot.totalCases)
        }
        sendBroadcast(intent)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val snapshot = SnapshotStore.snapshot
        val launchIntent = Intent(this, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("G3.10 geometry search")
            .setContentText(snapshot.message)
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(snapshot.status in setOf(Status.RUNNING, Status.MANUAL_PAUSED, Status.THERMAL_PAUSED))
            .setProgress(snapshot.totalCases.coerceAtLeast(1), snapshot.completedCases, false)

        when (snapshot.status) {
            Status.RUNNING -> builder.addAction(
                0, "Pause", servicePendingIntent(ACTION_PAUSE, 1)
            )
            Status.MANUAL_PAUSED -> builder.addAction(
                0, "Resume", servicePendingIntent(ACTION_RESUME, 2)
            )
            Status.THERMAL_PAUSED -> Unit
            else -> Unit
        }
        if (snapshot.status in setOf(Status.RUNNING, Status.MANUAL_PAUSED, Status.THERMAL_PAUSED)) {
            builder.addAction(0, "Stop", servicePendingIntent(ACTION_STOP, 3))
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, G310SearchForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "G3.10 geometry search",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progress and thermal-pausing controls for the G3.10 stress search"
            }
        )
    }

    private fun saveReport(report: String) {
        reportFile(this).writeText(report)
    }

    enum class Status { IDLE, RUNNING, MANUAL_PAUSED, THERMAL_PAUSED, COMPLETED, STOPPED }

    data class Snapshot(
        val status: Status = Status.IDLE,
        val completedCases: Int = 0,
        val totalCases: Int = TOTAL_CASES,
        val workerCount: Int = WORKER_COUNT,
        val perSeedProcessed: List<Int> = List(SEED_COUNT) { 0 },
        val thermalStatus: Int = -1,
        val batteryTemperatureC: Float? = null,
        val reportAvailable: Boolean = false,
        val message: String = "G3.10 idle"
    ) {
        val percentComplete: Double
            get() = if (totalCases <= 0) 100.0 else completedCases * 100.0 / totalCases
    }

    private object SnapshotStore {
        @Volatile var snapshot: Snapshot = Snapshot()
            private set
        fun update(value: Snapshot) { snapshot = value }
    }

    companion object {
        const val ACTION_START = "com.example.svgvectorconverter.G310_START"
        const val ACTION_PAUSE = "com.example.svgvectorconverter.G310_PAUSE"
        const val ACTION_RESUME = "com.example.svgvectorconverter.G310_RESUME"
        const val ACTION_STOP = "com.example.svgvectorconverter.G310_STOP"
        const val ACTION_STATE_CHANGED = "com.example.svgvectorconverter.G310_STATE_CHANGED"
        const val EXTRA_STATUS = "status"
        const val EXTRA_COMPLETED = "completed"
        const val EXTRA_TOTAL = "total"

        private const val CHANNEL_ID = "g310_geometry_search"
        private const val NOTIFICATION_ID = 310
        private const val REPORT_FILE_NAME = "g3_10_bidirectional_polyline_geometry_report.txt"
        private const val TOTAL_CASES = 100_000
        private const val WORKER_COUNT = 4
        private const val SEED_COUNT = 4
        private const val PAUSE_TEMPERATURE_C = 42.0f
        private const val RESUME_TEMPERATURE_C = 38.5f
        private const val BATTERY_POLL_INTERVAL_NANOS = 2_000_000_000L

        fun snapshot(): Snapshot = SnapshotStore.snapshot
        fun reportFile(context: Context): File = File(context.filesDir, REPORT_FILE_NAME)
        fun readReport(context: Context): String = reportFile(context).takeIf { it.exists() }?.readText().orEmpty()
        fun clearPreviousReport(context: Context) {
            reportFile(context).delete()
            SnapshotStore.update(Snapshot())
        }
    }
}
