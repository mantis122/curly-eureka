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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Foreground, thermally managed, persistently resumable G3.10 search service. */
class G310SearchForegroundService : Service() {
    private val stateLock = Object()
    private val checkpointWriteLock = Any()
    private val stopRequested = AtomicBoolean(false)
    private var manuallyPaused = false
    private var thermallyPaused = false
    private var workerThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var thermalListener: Any? = null
    private var lastThermalStatus = -1
    private var lastBatteryTemperatureC: Float? = null
    @Volatile private var lastBatteryPollNanos: Long = 0L
    private var activeElapsedBeforeSegmentMs = 0L
    private var activeSegmentStartedMs: Long? = null
    private var currentResumeState: SvgBidirectionalPolylineGeometrySearch.ResumeState? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerThermalListener()
        updateBatteryTemperature()
        restorePersistentSnapshot()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startSearchIfNeeded(restart = false)
            ACTION_RESTART -> startSearchIfNeeded(restart = true)
            ACTION_PAUSE -> setManualPause(true)
            ACTION_RESUME -> setManualPause(false)
            ACTION_STOP -> requestStop(preserveCheckpoint = true)
            ACTION_DELETE_CHECKPOINT -> if (workerThread?.isAlive != true) clearCheckpoint(this)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        requestStop(preserveCheckpoint = true)
        unregisterThermalListener()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun startSearchIfNeeded(restart: Boolean) {
        synchronized(stateLock) {
            if (workerThread?.isAlive == true) {
                publishSnapshot()
                return
            }
            if (restart) {
                clearCheckpoint(this)
                reportFile(this).delete()
                currentResumeState = null
                activeElapsedBeforeSegmentMs = 0L
            } else {
                val restored = readCheckpoint(this)
                currentResumeState = restored?.resumeState
                activeElapsedBeforeSegmentMs = restored?.activeElapsedMs ?: 0L
            }
            stopRequested.set(false)
            manuallyPaused = false
            thermallyPaused = shouldThermallyPause()
            if (!thermallyPaused) beginActiveSegment()
            val processed = currentResumeState?.perSeedStates?.map { it.processedCases }
                ?: List(SEED_COUNT) { 0 }
            SnapshotStore.update(
                Snapshot(
                    status = if (thermallyPaused) Status.THERMAL_PAUSED else Status.RUNNING,
                    completedCases = processed.sum(),
                    totalCases = TOTAL_CASES,
                    workerCount = WORKER_COUNT,
                    perSeedProcessed = processed,
                    thermalStatus = lastThermalStatus,
                    batteryTemperatureC = lastBatteryTemperatureC,
                    reportAvailable = reportFile(this).exists(),
                    checkpointAvailable = checkpointFile(this).exists(),
                    elapsedMillis = activeElapsedMillis(),
                    estimatedRemainingMillis = estimateRemainingMillis(processed.sum()),
                    lastSavedMillis = checkpointFile(this).takeIf { it.exists() }?.lastModified() ?: 0L,
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
                            val elapsed = activeElapsedMillis()
                            SnapshotStore.update(
                                SnapshotStore.snapshot.copy(
                                    status = currentStatus(),
                                    completedCases = progress.completedCases,
                                    totalCases = progress.totalCases,
                                    workerCount = progress.workerCount,
                                    perSeedProcessed = progress.perSeedProcessed,
                                    thermalStatus = lastThermalStatus,
                                    batteryTemperatureC = lastBatteryTemperatureC,
                                    elapsedMillis = elapsed,
                                    estimatedRemainingMillis = estimateRemainingMillis(progress.completedCases, elapsed),
                                    checkpointAvailable = checkpointFile(this).exists(),
                                    message = statusMessage()
                                )
                            )
                        }
                        publishSnapshot()
                    },
                    controlCheckpoint = { awaitRunnableState() },
                    resumeState = currentResumeState,
                    checkpointCallback = { state ->
                        currentResumeState = state
                        val elapsed = activeElapsedMillis()
                        writeCheckpoint(this, state, elapsed)
                        synchronized(stateLock) {
                            SnapshotStore.update(
                                SnapshotStore.snapshot.copy(
                                    checkpointAvailable = true,
                                    lastSavedMillis = System.currentTimeMillis(),
                                    elapsedMillis = elapsed,
                                    estimatedRemainingMillis = estimateRemainingMillis(
                                        state.perSeedStates.sumOf { it.processedCases }, elapsed
                                    )
                                )
                            )
                        }
                    }
                )
            } catch (_: CancellationException) {
                null
            } catch (_: InterruptedException) {
                null
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.10 automated bidirectional polyline geometry comparator differential stress search")
                    appendLine();appendLine("RESULT: The search could not be completed.");appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                }
            }

            endActiveSegment()
            if (report != null && !stopRequested.get()) {
                saveReport(report)
                clearCheckpoint(this)
                synchronized(stateLock) {
                    SnapshotStore.update(SnapshotStore.snapshot.copy(
                        status = Status.COMPLETED,
                        completedCases = TOTAL_CASES,
                        perSeedProcessed = List(SEED_COUNT) { CASES_PER_SEED },
                        reportAvailable = true,
                        checkpointAvailable = false,
                        elapsedMillis = activeElapsedMillis(),
                        estimatedRemainingMillis = 0L,
                        message = "G3.10 completed"
                    ))
                }
            } else {
                currentResumeState?.let { writeCheckpoint(this, it, activeElapsedMillis()) }
                synchronized(stateLock) {
                    SnapshotStore.update(SnapshotStore.snapshot.copy(
                        status = Status.STOPPED,
                        checkpointAvailable = checkpointFile(this).exists(),
                        elapsedMillis = activeElapsedMillis(),
                        message = if (checkpointFile(this).exists()) "G3.10 stopped; progress saved" else "G3.10 stopped"
                    ))
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
                endActiveSegment()
                currentResumeState?.let { writeCheckpoint(this, it, activeElapsedMillis()) }
                updateWakeLockForState()
                try { stateLock.wait(1_000L) }
                catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); throw interrupted }
                updateBatteryTemperature()
                refreshThermalPauseFromFallback()
            }
            if (stopRequested.get()) throw CancellationException("G3.10 stopped")
            beginActiveSegment()
            updateWakeLockForState()
        }
    }

    private fun setManualPause(paused: Boolean) {
        synchronized(stateLock) {
            manuallyPaused = paused
            if (paused) endActiveSegment() else if (!thermallyPaused) beginActiveSegment()
            currentResumeState?.let { writeCheckpoint(this, it, activeElapsedMillis()) }
            stateLock.notifyAll()
            SnapshotStore.update(SnapshotStore.snapshot.copy(
                status = currentStatus(), elapsedMillis = activeElapsedMillis(), message = statusMessage()
            ))
        }
        updateWakeLockForState();publishSnapshot()
    }

    private fun requestStop(preserveCheckpoint: Boolean) {
        stopRequested.set(true)
        endActiveSegment()
        if (preserveCheckpoint) currentResumeState?.let { writeCheckpoint(this, it, activeElapsedMillis()) }
        synchronized(stateLock) { stateLock.notifyAll() }
        workerThread?.interrupt();releaseWakeLock()
    }

    private fun beginActiveSegment() {
        if (activeSegmentStartedMs == null) activeSegmentStartedMs = SystemClock.elapsedRealtime()
    }

    private fun endActiveSegment() {
        activeSegmentStartedMs?.let { activeElapsedBeforeSegmentMs += SystemClock.elapsedRealtime() - it }
        activeSegmentStartedMs = null
    }

    private fun activeElapsedMillis(): Long = activeElapsedBeforeSegmentMs +
        (activeSegmentStartedMs?.let { SystemClock.elapsedRealtime() - it } ?: 0L)

    private fun estimateRemainingMillis(completed:Int, elapsed:Long = activeElapsedMillis()): Long? {
        if (completed <= 0 || elapsed <= 0L || completed >= TOTAL_CASES) return if (completed >= TOTAL_CASES) 0L else null
        return ((TOTAL_CASES - completed).toDouble() * elapsed.toDouble() / completed.toDouble()).toLong().coerceAtLeast(0L)
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val listener = PowerManager.OnThermalStatusChangedListener { status ->
            synchronized(stateLock) {
                val wasPaused = thermallyPaused
                lastThermalStatus = status
                thermallyPaused = status >= PowerManager.THERMAL_STATUS_SEVERE || ((lastBatteryTemperatureC ?: 0f) >= PAUSE_TEMPERATURE_C)
                if (!wasPaused && thermallyPaused) endActiveSegment()
                if (wasPaused && !thermallyPaused && !manuallyPaused) beginActiveSegment()
                if (thermallyPaused) currentResumeState?.let { writeCheckpoint(this, it, activeElapsedMillis()) }
                stateLock.notifyAll()
                SnapshotStore.update(SnapshotStore.snapshot.copy(
                    status=currentStatus(),thermalStatus=status,batteryTemperatureC=lastBatteryTemperatureC,
                    elapsedMillis=activeElapsedMillis(),message=statusMessage()
                ))
            }
            updateWakeLockForState();publishSnapshot()
        }
        thermalListener=listener;powerManager.addThermalStatusListener(mainExecutor,listener)
        lastThermalStatus=powerManager.currentThermalStatus
    }

    private fun unregisterThermalListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val listener=thermalListener as? PowerManager.OnThermalStatusChangedListener ?: return
        (getSystemService(POWER_SERVICE) as PowerManager).removeThermalStatusListener(listener);thermalListener=null
    }

    private fun updateBatteryTemperature() {
        val batteryIntent=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenthsC=batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE) ?: Int.MIN_VALUE
        var changed=false
        synchronized(stateLock) {
            if(tenthsC!=Int.MIN_VALUE&&tenthsC>0) lastBatteryTemperatureC=tenthsC/10f
            val previous=thermallyPaused;refreshThermalPauseFromFallback();changed=previous!=thermallyPaused
            if(changed){
                if(thermallyPaused) endActiveSegment() else if(!manuallyPaused) beginActiveSegment()
                if(thermallyPaused) currentResumeState?.let { writeCheckpoint(this,it,activeElapsedMillis()) }
                SnapshotStore.update(SnapshotStore.snapshot.copy(
                    status=currentStatus(),thermalStatus=lastThermalStatus,batteryTemperatureC=lastBatteryTemperatureC,
                    elapsedMillis=activeElapsedMillis(),message=statusMessage()
                ));stateLock.notifyAll()
            }
        }
        if(changed&&workerThread?.isAlive==true){updateWakeLockForState();publishSnapshot()}
    }

    private fun refreshThermalPauseFromFallback() {
        val temperature=lastBatteryTemperatureC
        val severe=Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q&&lastThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE
        thermallyPaused=when{
            severe->true;temperature==null->false
            thermallyPaused&&temperature>RESUME_TEMPERATURE_C->true
            temperature>=PAUSE_TEMPERATURE_C->true;else->false
        }
    }
    private fun shouldThermallyPause()=(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q&&lastThermalStatus>=PowerManager.THERMAL_STATUS_SEVERE)||((lastBatteryTemperatureC?:0f)>=PAUSE_TEMPERATURE_C)
    private fun currentStatus()=when{stopRequested.get()->Status.STOPPED;thermallyPaused->Status.THERMAL_PAUSED;manuallyPaused->Status.MANUAL_PAUSED;else->Status.RUNNING}
    private fun statusMessage():String{
        val temp=lastBatteryTemperatureC?.let{String.format(Locale.US,"%.1f°C",it)}
        return when(currentStatus()){
            Status.THERMAL_PAUSED->"Cooling pause${temp?.let{" at $it"}?:""}; progress saved"
            Status.MANUAL_PAUSED->"Paused by user${temp?.let{" • $it"}?:""}; progress saved"
            Status.RUNNING->"Running${temp?.let{" • $it"}?:""}"
            Status.COMPLETED->"G3.10 completed";Status.STOPPED->"G3.10 stopped";Status.IDLE->"G3.10 idle"
        }
    }
    private fun updateWakeLockForState(){if(currentStatus()==Status.RUNNING)acquireWakeLock()else releaseWakeLock()}
    private fun acquireWakeLock(){if(wakeLock?.isHeld==true)return;wakeLock=(getSystemService(POWER_SERVICE)as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"$packageName:G3.10Search").apply{setReferenceCounted(false);acquire()}}
    private fun releaseWakeLock(){wakeLock?.let{if(it.isHeld)it.release()};wakeLock=null}

    private fun publishSnapshot(){
        val snapshot=SnapshotStore.snapshot
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName).apply{putExtra(EXTRA_STATUS,snapshot.status.name);putExtra(EXTRA_COMPLETED,snapshot.completedCases);putExtra(EXTRA_TOTAL,snapshot.totalCases)})
        (getSystemService(NOTIFICATION_SERVICE)as NotificationManager).notify(NOTIFICATION_ID,buildNotification())
    }
    private fun buildNotification():Notification{
        val s=SnapshotStore.snapshot
        val content=buildString{append(s.message);append(" • ");append(String.format(Locale.US,"%.1f%%",s.percentComplete));s.estimatedRemainingMillis?.let{append(" • ETA ");append(formatDuration(it))}}
        val builder=NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("G3.10 geometry search").setContentText(content)
            .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setOnlyAlertOnce(true).setOngoing(s.status in setOf(Status.RUNNING,Status.MANUAL_PAUSED,Status.THERMAL_PAUSED))
            .setProgress(s.totalCases.coerceAtLeast(1),s.completedCases,false)
        when(s.status){Status.RUNNING->builder.addAction(0,"Pause",servicePendingIntent(ACTION_PAUSE,1));Status.MANUAL_PAUSED->builder.addAction(0,"Resume",servicePendingIntent(ACTION_RESUME,2));else->Unit}
        if(s.status in setOf(Status.RUNNING,Status.MANUAL_PAUSED,Status.THERMAL_PAUSED))builder.addAction(0,"Stop",servicePendingIntent(ACTION_STOP,3))
        return builder.build()
    }
    private fun servicePendingIntent(action:String,requestCode:Int)=PendingIntent.getService(this,requestCode,Intent(this,G310SearchForegroundService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun createNotificationChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)(getSystemService(NOTIFICATION_SERVICE)as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL_ID,"G3.10 geometry search",NotificationManager.IMPORTANCE_LOW).apply{description="Resumable progress and thermal controls for G3.10"})}
    private fun saveReport(report:String){atomicWrite(reportFile(this),report)}

    enum class Status{IDLE,RUNNING,MANUAL_PAUSED,THERMAL_PAUSED,COMPLETED,STOPPED}
    data class Snapshot(
        val status:Status=Status.IDLE,val completedCases:Int=0,val totalCases:Int=TOTAL_CASES,
        val workerCount:Int=WORKER_COUNT,val perSeedProcessed:List<Int> = List(SEED_COUNT){0},
        val thermalStatus:Int=-1,val batteryTemperatureC:Float?=null,val reportAvailable:Boolean=false,
        val checkpointAvailable:Boolean=false,val elapsedMillis:Long=0L,val estimatedRemainingMillis:Long?=null,
        val lastSavedMillis:Long=0L,val message:String="G3.10 idle"
    ){val percentComplete:Double get()=if(totalCases<=0)100.0 else completedCases*100.0/totalCases}
    private object SnapshotStore{@Volatile var snapshot=Snapshot();private set;fun update(value:Snapshot){snapshot=value}}

    private data class StoredCheckpoint(val resumeState:SvgBidirectionalPolylineGeometrySearch.ResumeState,val activeElapsedMs:Long)

    private fun restorePersistentSnapshot(){
        val stored=readCheckpoint(this);val reportExists=reportFile(this).exists()
        if(stored!=null){
            currentResumeState=stored.resumeState;activeElapsedBeforeSegmentMs=stored.activeElapsedMs
            val per=stored.resumeState.perSeedStates.map{it.processedCases}
            SnapshotStore.update(Snapshot(status=Status.STOPPED,completedCases=per.sum(),perSeedProcessed=per,
                reportAvailable=reportExists,checkpointAvailable=true,elapsedMillis=stored.activeElapsedMs,
                estimatedRemainingMillis=estimateRemainingMillis(per.sum(),stored.activeElapsedMs),
                lastSavedMillis=checkpointFile(this).lastModified(),message="Saved G3.10 progress available"))
        }else if(reportExists) SnapshotStore.update(Snapshot(status=Status.COMPLETED,completedCases=TOTAL_CASES,perSeedProcessed=List(SEED_COUNT){CASES_PER_SEED},reportAvailable=true,message="G3.10 completed"))
    }

    private fun writeCheckpoint(context:Context,state:SvgBidirectionalPolylineGeometrySearch.ResumeState,activeElapsedMs:Long){
        synchronized(checkpointWriteLock){
            val root=JSONObject().put("version",CHECKPOINT_VERSION).put("casesPerSeed",state.casesPerSeed)
                .put("seeds",JSONArray(state.seeds)).put("activeElapsedMs",activeElapsedMs).put("savedAt",System.currentTimeMillis())
            val states=JSONArray();state.perSeedStates.forEach{states.put(partialToJson(it))};root.put("states",states)
            atomicWrite(checkpointFile(context),root.toString())
        }
    }
    private fun readCheckpoint(context:Context):StoredCheckpoint?=try{
        val file=checkpointFile(context);if(!file.exists())return null
        val root=JSONObject(file.readText());if(root.optInt("version")!=CHECKPOINT_VERSION)return null
        val cases=root.getInt("casesPerSeed");val seedArray=root.getJSONArray("seeds")
        val seeds=List(seedArray.length()){seedArray.getLong(it)}
        if(cases!=CASES_PER_SEED||seeds!=DEFAULT_SEEDS)return null
        val array=root.getJSONArray("states");if(array.length()!=seeds.size)return null
        StoredCheckpoint(SvgBidirectionalPolylineGeometrySearch.ResumeState(cases,seeds,List(array.length()){partialFromJson(array.getJSONObject(it))}),root.optLong("activeElapsedMs",0L))
    }catch(_:Throwable){null}
    private fun partialToJson(s:SvgPathDataOptimizer.BidirectionalPolylinePartialState)=JSONObject()
        .put("processed",s.processedCases).put("generated",s.generatedCases).put("valid",s.validCases).put("rejected",s.rejectedGeneratedCases)
        .put("changed",s.candidateChangedCases).put("collinear",s.collinearChangedCases).put("candidateMismatch",s.candidateMismatchCases)
        .put("directMismatch",s.directCollinearMismatchCases).put("sourceFirst",s.sourceToFirstMismatchCases).put("sourceSecond",s.sourceToSecondMismatchCases)
        .put("comparisons",s.comparisons).put("elapsedNanos",s.elapsedNanos).put("witnesses",JSONArray().apply{s.witnesses.forEach{put(witnessToJson(it))}})
    private fun partialFromJson(o:JSONObject)=SvgPathDataOptimizer.BidirectionalPolylinePartialState(
        o.getInt("processed"),o.getInt("generated"),o.getInt("valid"),o.getInt("rejected"),o.getInt("changed"),o.getInt("collinear"),
        o.getInt("candidateMismatch"),o.getInt("directMismatch"),o.getInt("sourceFirst"),o.getInt("sourceSecond"),o.getInt("comparisons"),o.optLong("elapsedNanos",0L),
        o.getJSONArray("witnesses").let{a->List(a.length()){witnessFromJson(a.getJSONObject(it))}}
    )
    private fun witnessToJson(w:SvgPathDataOptimizer.BidirectionalPolylineWitness)=JSONObject()
        .put("case",w.caseNumber).put("source",w.source).put("first",w.firstPass).put("pre",w.preCollinear).put("post",w.postCollinear)
        .put("candidate",w.finalCandidate).put("second",w.independentSecondPass).put("cm",w.candidateMismatch).put("dm",w.directCollinearMismatch)
        .put("sf",w.sourceToFirstMismatch).put("ss",w.sourceToSecondMismatch).put("cr",w.candidateReason).put("dr",w.directReason)
        .put("c12",w.candidateFirstToSecondDeviation).put("c21",w.candidateSecondToFirstDeviation).put("d12",w.directFirstToSecondDeviation).put("d21",w.directSecondToFirstDeviation)
        .put("point",w.offendingPoint).put("segment",w.nearestSegment)
    private fun witnessFromJson(o:JSONObject)=SvgPathDataOptimizer.BidirectionalPolylineWitness(o.getInt("case"),o.getString("source"),o.getString("first"),o.getString("pre"),o.getString("post"),o.getString("candidate"),o.getString("second"),o.getBoolean("cm"),o.getBoolean("dm"),o.getBoolean("sf"),o.getBoolean("ss"),o.getString("cr"),o.getString("dr"),o.getDouble("c12"),o.getDouble("c21"),o.getDouble("d12"),o.getDouble("d21"),o.getString("point"),o.getString("segment"))

    companion object{
        const val ACTION_START="com.example.svgvectorconverter.G310_START";const val ACTION_RESTART="com.example.svgvectorconverter.G310_RESTART"
        const val ACTION_PAUSE="com.example.svgvectorconverter.G310_PAUSE";const val ACTION_RESUME="com.example.svgvectorconverter.G310_RESUME"
        const val ACTION_STOP="com.example.svgvectorconverter.G310_STOP";const val ACTION_DELETE_CHECKPOINT="com.example.svgvectorconverter.G310_DELETE_CHECKPOINT"
        const val ACTION_STATE_CHANGED="com.example.svgvectorconverter.G310_STATE_CHANGED";const val EXTRA_STATUS="status";const val EXTRA_COMPLETED="completed";const val EXTRA_TOTAL="total"
        private const val CHANNEL_ID="g310_geometry_search";private const val NOTIFICATION_ID=310
        private const val REPORT_FILE_NAME="g3_10_bidirectional_polyline_geometry_report.txt";private const val CHECKPOINT_FILE_NAME="g3_10_checkpoint.json"
        private const val CHECKPOINT_VERSION=1;private const val TOTAL_CASES=100_000;private const val CASES_PER_SEED=25_000;private const val WORKER_COUNT=4;private const val SEED_COUNT=4
        private val DEFAULT_SEEDS=listOf(0x6316_2026L,0x6316_0001L,0x6316_0002L,0x1D40_2026L)
        private const val PAUSE_TEMPERATURE_C=42.0f;private const val RESUME_TEMPERATURE_C=38.5f;private const val BATTERY_POLL_INTERVAL_NANOS=2_000_000_000L
        fun snapshot():Snapshot=SnapshotStore.snapshot
        fun reportFile(context:Context)=File(context.filesDir,REPORT_FILE_NAME);fun checkpointFile(context:Context)=File(context.filesDir,CHECKPOINT_FILE_NAME)
        fun readReport(context:Context)=reportFile(context).takeIf{it.exists()}?.readText().orEmpty();fun hasCheckpoint(context:Context)=checkpointFile(context).exists()
        fun clearPreviousReport(context:Context){reportFile(context).delete();SnapshotStore.update(Snapshot(checkpointAvailable=hasCheckpoint(context),message=if(hasCheckpoint(context))"Saved G3.10 progress available" else "G3.10 idle"))}
        fun clearCheckpoint(context:Context){checkpointFile(context).delete();if(!reportFile(context).exists())SnapshotStore.update(Snapshot())}
        fun formatDuration(ms:Long):String{val total=(ms/1000).coerceAtLeast(0);val d=total/86400;val h=(total%86400)/3600;val m=(total%3600)/60;val s=total%60;return when{d>0->"${d}d ${h}h";h>0->"${h}h ${m}m";m>0->"${m}m ${s}s";else->"${s}s"}}
        private fun atomicWrite(file:File,text:String){file.parentFile?.mkdirs();val temp=File(file.parentFile,"${file.name}.tmp");temp.writeText(text);if(!temp.renameTo(file)){file.writeText(text);temp.delete()}}
    }
}
