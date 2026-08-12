package com.example.svgvectorconverter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.graphics.drawable.BitmapDrawable

class MainActivity : ComponentActivity() {
    private lateinit var outputBox: EditText
    private var convertedXml = ""
    private lateinit var reportBox: TextView
    private lateinit var previewBox: ImageView
    private var suggestedFileName = "converted_vector.xml"
    private lateinit var mainPanel: LinearLayout
    private val batchResults = mutableListOf<BatchResult>()
    private lateinit var batchGallery: LinearLayout
    private var outputDpSize = 24
    private var conversionProfile = "Default"
    private lateinit var copyButton: Button
    private lateinit var saveXmlButton: Button
    private lateinit var saveZipButton: Button
    private lateinit var copyReportButton: Button
    private lateinit var saveReportTextButton: Button
    private lateinit var saveReportImageButton: Button
    private lateinit var reportActionsRow: LinearLayout
    private var currentRegressionReport = ""
    private var currentDifferentialSearchReport = ""
    private var currentPostScaleDifferentialReport = ""
    private var currentPostScaleStageAddbackReport = ""
    private var currentIdempotencePathReuseReport = ""
    private var currentPathFixedPointReport = ""
    private var currentFinalCommandConvergenceReport = ""
    private var currentPostSerializationGeometryConvergenceReport = ""
    private var currentCollinearGeometrySafetyReport = ""
    private var currentBidirectionalPolylineGeometryReport = ""
    private var currentOrderedCollinearTraversalReport = ""
    private var currentBidirectionalComparatorRepairReport = ""
    private var currentExactTraversalShortCircuitReport = ""
    private var currentG314ConvergenceReport = ""

    private fun makeButton(
        label: String,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
    }

    private fun makeText(
        value: String,
        sizeSp: Float,
        color: Int,
        gravityValue: Int = Gravity.START,
        paddingBottom: Int = 0
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            gravity = gravityValue
            if (paddingBottom > 0) {
                setPadding(0, 0, 0, paddingBottom)
            }
        }
    }

    private fun setMainContentState(
        showPreviewContent: Boolean
    ) {
        previewBox.visibility = if (showPreviewContent) View.VISIBLE else View.GONE
        reportBox.visibility = if (showPreviewContent) View.VISIBLE else View.GONE
        reportActionsRow.visibility = if (showPreviewContent) View.VISIBLE else View.GONE
        batchGallery.visibility = if (showPreviewContent) View.VISIBLE else View.GONE
        outputBox.visibility = if (showPreviewContent) View.GONE else View.VISIBLE
    }

    private val openSvg = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { convertSingleSvg(it) }
    }

    private val openMultipleSvgs = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            convertBatchSvgs(uris)
        }
    }

    private val saveZip = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            FileIoHelpers.writeZipToUri(this, uri, batchResults)
            toast("ZIP saved")
        }
    }

    private val saveXml = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            FileIoHelpers.writeTextToUri(this, uri, convertedXml)
            toast("Saved")
        }
    }

    private val saveReportText = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            FileIoHelpers.writeTextToUri(this, uri, currentReportText())
            toast("Report saved")
        }
    }

    private val saveReportImage = registerForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            val bitmap = createReportBitmap()
            if (bitmap == null) {
                toast("Could not create report image")
            } else {
                try {
                    FileIoHelpers.writeBitmapPngToUri(this, uri, bitmap)
                    toast("Report image saved")
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private val saveRegressionReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentRegressionReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentRegressionReport)
            toast("Regression report saved")
        }
    }

    private val saveDifferentialSearchReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentDifferentialSearchReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(
                this,
                uri,
                currentDifferentialSearchReport
            )
            toast("Differential search report saved")
        }
    }

    private val savePostScaleDifferentialReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentPostScaleDifferentialReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(
                this,
                uri,
                currentPostScaleDifferentialReport
            )
            toast("G2.25 differential search report saved")
        }
    }

    private val savePostScaleStageAddbackReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentPostScaleStageAddbackReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(
                this,
                uri,
                currentPostScaleStageAddbackReport
            )
            toast("G2.26 stage-addback report saved")
        }
    }

    private val saveIdempotencePathReuseReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentIdempotencePathReuseReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(
                this,
                uri,
                currentIdempotencePathReuseReport
            )
            toast("G3.4 differential search report saved")
        }
    }


    private val savePathFixedPointReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentPathFixedPointReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentPathFixedPointReport)
            toast("G3.5 fixed-point report saved")
        }
    }

    private val saveFinalCommandConvergenceReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentFinalCommandConvergenceReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentFinalCommandConvergenceReport)
            toast("G3.6 convergence report saved")
        }
    }

    private val savePostSerializationGeometryConvergenceReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentPostSerializationGeometryConvergenceReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(
                this,
                uri,
                currentPostSerializationGeometryConvergenceReport
            )
            toast("G3.7 geometry convergence report saved")
        }
    }

    private val saveCollinearGeometrySafetyReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentCollinearGeometrySafetyReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentCollinearGeometrySafetyReport)
            toast("G3.8 geometry-safety report saved")
        }
    }

    private val saveBidirectionalPolylineGeometryReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentBidirectionalPolylineGeometryReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentBidirectionalPolylineGeometryReport)
            toast("G3.10 bidirectional-polyline report saved")
        }
    }

    private val saveOrderedCollinearTraversalReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentOrderedCollinearTraversalReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentOrderedCollinearTraversalReport)
            toast("G3.11 ordered-traversal report saved")
        }
    }

    private val saveBidirectionalComparatorRepairReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentBidirectionalComparatorRepairReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentBidirectionalComparatorRepairReport)
            toast("G3.12 comparator-repair report saved")
        }
    }

    private val saveExactTraversalShortCircuitReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentExactTraversalShortCircuitReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentExactTraversalShortCircuitReport)
            toast("G3.13 exact-traversal report saved")
        }
    }

    private val saveG314ConvergenceReport = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && currentG314ConvergenceReport.isNotBlank()) {
            FileIoHelpers.writeTextToUri(this, uri, currentG314ConvergenceReport)
            toast("G3.14 convergence-corpus report saved")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val savedSettings = ConverterSettingsStore.load(this)
        outputDpSize = savedSettings.outputDpSize
        conversionProfile = savedSettings.conversionProfile

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 80, 32, 32)
            setBackgroundColor(Color.rgb(250, 248, 240))
        }

        val title = makeText("SVG → Android Vector", 24f, Color.BLACK)
        val versionLabel = makeText("v${getVersionName()}", 12f, Color.GRAY)

        val openButton = makeButton("Open SVG") {
            openSvg.launch(arrayOf("image/svg+xml", "text/xml", "text/plain"))
        }

        copyButton = makeButton("Copy XML") { copyConvertedXml() }

        val sizeButton = Button(this).apply {
            text = sizeButtonText()
            setOnClickListener {
                showOutputSizeDialog(this)
            }
        }

        val profileButton = Button(this).apply {
            text = "Profile: $conversionProfile"
            setOnClickListener {
                showProfileDialog(this, sizeButton)
            }
        }

        val batchButton = makeButton("Batch SVGs") {
            openMultipleSvgs.launch(arrayOf("image/svg+xml", "text/xml", "text/plain"))
        }

        saveZipButton = makeButton("Save ZIP") { saveBatchZip() }
        saveXmlButton = makeButton("Save XML") { saveSingleXml() }
        copyReportButton = makeButton("Copy Report") { copyReport() }
        saveReportTextButton = makeButton("Save Report .txt") { saveCurrentReportText() }
        saveReportImageButton = makeButton("Save Report Image") { saveCurrentReportImage() }
        val developerButton = makeButton("Developer Tools") { showDeveloperToolsDialog() }
        val aboutButton = makeButton("About") { showAboutDialog() }

        previewBox = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(24, 24, 24, 24)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        batchGallery = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        outputBox = EditText(this).apply {
            hint = "Converted VectorDrawable XML will appear here"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            setHorizontallyScrolling(true)
            minLines = 20
            visibility = View.GONE
        }

        val previewTab = makeButton("Preview") { showPreviewTab() }
        val xmlTab = makeButton("XML") { showXmlTab() }

        val openRow = horizontalRow(openButton, batchButton)
        val saveRow = horizontalRow(saveXmlButton, saveZipButton)

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(horizontalRow(copyButton, sizeButton))
            addView(profileButton, LinearLayout.LayoutParams(-1, -2))
            addView(developerButton, LinearLayout.LayoutParams(-1, -2))
            addView(aboutButton, LinearLayout.LayoutParams(-1, -2))
        }

        val tabRow = horizontalRow(previewTab, xmlTab)

        reportBox = makeText("No SVG converted yet", 14f, Color.BLACK).apply {
            setPadding(0, 16, 0, 16)
        }

        reportActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(horizontalRow(copyReportButton, saveReportTextButton))
            addView(saveReportImageButton, LinearLayout.LayoutParams(-1, -2))
        }

        mainPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(mainPanel)
        }

        mainPanel.addView(previewBox, LinearLayout.LayoutParams(-1, 450))
        mainPanel.addView(outputBox, LinearLayout.LayoutParams(-1, -2))
        mainPanel.addView(reportActionsRow)
        mainPanel.addView(reportBox)
        mainPanel.addView(batchGallery)
        mainPanel.addView(Space(this), LinearLayout.LayoutParams(-1, 96))

        root.addView(title)
        root.addView(versionLabel)
        root.addView(openRow)
        root.addView(saveRow)
        root.addView(utilityRow)
        root.addView(tabRow)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        updateActionButtons()
    }

    private fun convertSingleSvg(uri: Uri) {
        suggestedFileName = FileIoHelpers.makeXmlFileName(this, uri)

        val result = SvgToVectorConverter.convert(
            FileIoHelpers.readTextFromUri(this, uri),
            outputDpSize,
            conversionProfile
        )

        convertedXml = result.xml
        reportBox.text = result.report
        outputBox.setText(convertedXml)
        updatePreview(convertedXml)

        batchResults.clear()
        batchGallery.removeAllViews()
        previewBox.visibility = View.VISIBLE
        updateActionButtons()
    }

    private fun convertBatchSvgs(uris: List<Uri>) {
        batchResults.clear()
        clearSingleConversionUi()

        uris.forEach { uri ->
            batchResults.add(convertBatchSvg(uri))
        }

        reportBox.text = buildBatchReport()
        outputBox.setText(buildBatchXmlOutput())
        showBatchGallery()

        previewBox.visibility = View.GONE
        updateActionButtons()

        toast("${batchResults.size} files converted")
    }

    private fun convertBatchSvg(uri: Uri): BatchResult {
        val fileName = FileIoHelpers.makeXmlFileName(this, uri)

        return try {
            val result = SvgToVectorConverter.convert(
                FileIoHelpers.readTextFromUri(this, uri),
                outputDpSize,
                conversionProfile
            )

            BatchResult(
                fileName = fileName,
                xml = result.xml,
                warningCount = countWarnings(result.report),
                definitionPathCount = countDefinitionPaths(result.report),
                success = true
            )
        } catch (e: Exception) {
            BatchResult(
                fileName = fileName,
                xml = null,
                warningCount = 0,
                success = false,
                error = e.message
            )
        }
    }

    private fun clearSingleConversionUi() {
        convertedXml = ""
        previewBox.setImageDrawable(null)
        outputBox.setText("")
        updateActionButtons()
    }

    private fun buildBatchReport(): String {
        val successCount = batchResults.count { it.success }
        val warningCount = batchResults.count {
            it.success && it.warningCount > 0
        }
        val failureCount = batchResults.count { !it.success }

        return """
            🟢 Batch Conversion Complete

            Success: $successCount
            Warnings: $warningCount
            Failed: $failureCount

            Ready to save ZIP
        """.trimIndent()
    }

    private fun buildBatchXmlOutput(): String {
        return buildString {
            appendLine("Batch XML Output")
            appendLine("${batchResults.size} files selected")
            appendLine("${batchResults.count { it.success }} converted")
            appendLine("${batchResults.count { !it.success }} failed")
            appendLine()
            appendLine("────────────────────")
            appendLine()

            batchResults.forEach { result ->
                appendLine("===== ${result.fileName} =====")

                if (result.success && result.xml != null) {
                    appendLine(result.xml)
                } else {
                    appendLine("FAILED: ${result.error ?: "Unknown error"}")
                }

                appendLine()
            }
        }
    }

    private fun countWarnings(report: String): Int {
        return report.lines().count { it.startsWith("⚠") }
    }

    private fun countDefinitionPaths(report: String): Int {
        return Regex("""Drawable definitions:\s*(\d+)""")
            .find(report)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    private fun showOutputSizeDialog(sizeButton: Button) {
        val options = arrayOf("24dp", "48dp", "Keep SVG size", "Custom...")

        android.app.AlertDialog.Builder(this)
            .setTitle("Output Size")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setOutputDpSize(24, sizeButton)
                    1 -> setOutputDpSize(48, sizeButton)
                    2 -> setOutputDpSize(-1, sizeButton)
                    3 -> showCustomSizeDialog(sizeButton)
                }
            }
            .show()
    }

    private fun setOutputDpSize(size: Int, sizeButton: Button) {
        outputDpSize = size
        ConverterSettingsStore.saveOutputDpSize(this, outputDpSize)
        sizeButton.text = sizeButtonText()
    }

    private fun showProfileDialog(profileButton: Button, sizeButton: Button) {
        val options = arrayOf(
            "Default",
            "Android Icon",
            "Material Icon",
            "Keep SVG"
        )

        android.app.AlertDialog.Builder(this)
            .setTitle("Conversion Profile")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> applyProfile("Default", 24, profileButton, sizeButton)
                    1 -> applyProfile("Android Icon", 24, profileButton, sizeButton)
                    2 -> applyProfile("Material Icon", 24, profileButton, sizeButton)
                    3 -> applyProfile("Keep SVG", -1, profileButton, sizeButton)
                }
            }
            .show()
    }

    private fun applyProfile(
        profile: String,
        size: Int,
        profileButton: Button,
        sizeButton: Button
    ) {
        conversionProfile = profile
        outputDpSize = size

        ConverterSettingsStore.save(
            this,
            ConverterSettings(
                outputDpSize = outputDpSize,
                conversionProfile = conversionProfile
            )
        )

        profileButton.text = "Profile: $conversionProfile"
        sizeButton.text = sizeButtonText()
    }

    private fun showCustomSizeDialog(sizeButton: Button) {
        val input = EditText(this).apply {
            hint = "Example: 32"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("Custom Output Size")
            .setMessage("Enter dp size:")
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                val size = input.text.toString().toIntOrNull()

                if (size == null || size <= 0) {
                    toast("Invalid size")
                } else {
                    setOutputDpSize(size, sizeButton)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun sizeButtonText(): String {
        return if (outputDpSize > 0) {
            "Size: ${outputDpSize}dp"
        } else {
            "Size: SVG"
        }
    }

    private fun saveSingleXml() {
        if (convertedXml.isBlank()) {
            toast("Nothing to save yet")
        } else {
            saveXml.launch(suggestedFileName)
        }
    }

    private fun saveBatchZip() {
        if (batchResults.isEmpty()) {
            toast("No batch results yet")
        } else {
            saveZip.launch("converted_vectors.zip")
        }
    }

    private fun copyConvertedXml() {
        if (convertedXml.isBlank()) {
            toast("Nothing to copy yet")
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("vector.xml", convertedXml)
        )
        toast("Copied")
    }


    private fun currentReportText(): String {
        return reportBox.text?.toString().orEmpty()
    }

    private fun hasReport(): Boolean {
        val report = currentReportText().trim()
        return report.isNotEmpty() && report != "No SVG converted yet"
    }

    private fun copyReport() {
        if (!hasReport()) {
            toast("No report to copy yet")
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("conversion_report.txt", currentReportText())
        )
        toast("Report copied")
    }

    private fun saveCurrentReportText() {
        if (!hasReport()) {
            toast("No report to save yet")
            return
        }

        saveReportText.launch(makeReportFileName("txt"))
    }

    private fun saveCurrentReportImage() {
        if (!hasReport()) {
            toast("No report to save yet")
            return
        }

        saveReportImage.launch(makeReportFileName("png"))
    }

    private fun makeReportFileName(extension: String): String {
        val baseName = if (batchResults.isNotEmpty()) {
            "batch_conversion"
        } else {
            suggestedFileName.substringBeforeLast('.')
        }
        return "${baseName}_report.$extension"
    }

    private fun createReportBitmap(): Bitmap? {
        val reportText = currentReportText()
        if (reportText.isBlank()) return null

        val horizontalPadding = 32
        val verticalPadding = 32
        val width = (resources.displayMetrics.widthPixels - horizontalPadding * 2)
            .coerceAtLeast(320)

        val imageTextView = TextView(this).apply {
            text = reportText
            textSize = reportBox.textSize / resources.displayMetrics.scaledDensity
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            gravity = Gravity.START
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        }

        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        imageTextView.measure(widthSpec, heightSpec)

        val height = imageTextView.measuredHeight
        if (height <= 0) return null

        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                imageTextView.layout(0, 0, width, height)
                imageTextView.draw(canvas)
            }
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun showPreviewTab() {
        setMainContentState(showPreviewContent = true)
    }

    private fun showXmlTab() {
        setMainContentState(showPreviewContent = false)
    }

    private fun horizontalRow(
        left: View,
        right: View
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            addView(right, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    private fun showDeveloperToolsDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        layout.addView(
            makeText(
                "Developer Tools",
                21f,
                Color.BLACK,
                Gravity.START,
                paddingBottom = 20
            )
        )

        layout.addView(
            makeText(
                "Regression Testing",
                16f,
                Color.DKGRAY,
                Gravity.START,
                paddingBottom = 8
            )
        )

        val runButton = makeButton("Run Bundled Regression Suite") {
            runBundledRegressionSuite()
        }
        layout.addView(runButton, LinearLayout.LayoutParams(-1, -2))

        layout.addView(
            makeText(
                """
                Runs the five bundled E1.2 fixtures and checks conversion,
                path counts, warnings, optimizer idempotence, final-output
                validation, and required or forbidden XML fragments.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        layout.addView(
            makeText(
                "Optimizer Stress Testing",
                16f,
                Color.DKGRAY,
                Gravity.START,
                paddingBottom = 8
            )
        )

        val differentialSearchButton =
            makeButton("Run F4.3 Differential Search") {
                runCommandNumericDifferentialSearch()
            }
        layout.addView(
            differentialSearchButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Generates 100,000 deterministic paths and compares one
                F3.1 → F4.2 optimization round with a second round. Use this
                to determine whether the F4.3 fixed-point pass provides any
                additional reduction.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val postScaleDifferentialButton =
            makeButton("Run G2.25 Post-Scale Differential Search") {
                runPostScaleDifferentialSearch()
            }
        layout.addView(
            postScaleDifferentialButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Generates deterministic transform-heavy paths, applies
                positive uniform scales, then compares the current full
                post-scale optimizer with the G2.24 narrowed pipeline.
                Checks both byte equality and canonical path geometry.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val postScaleStageAddbackButton =
            makeButton("Run G2.26 Stage-Addback Differential Search") {
                runPostScaleStageAddbackSearch()
            }
        layout.addView(
            postScaleStageAddbackButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Reuses the G2.25 deterministic transform-heavy corpus and
                compares progressively richer post-scale optimizer pipelines.
                The full optimizer remains authoritative. Exact byte equality
                is the production-switch criterion; geometry sampling is
                bounded diagnostic context for non-identical candidates.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val idempotencePathReuseButton =
            makeButton("Run G3.4 Stable-Path Reuse Differential Search") {
                runIdempotencePathReuseDifferentialSearch()
            }
        layout.addView(
            idempotencePathReuseButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Generates 100,000 deterministic VectorDrawable cases. Each
                case runs one common production pass, then compares a fully
                independent second-pass path recomputation with G3.3 final-path
                stable-output reuse. Exact second-pass XML and the idempotence
                verdict must match.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val pathFixedPointButton =
            makeButton("Run G3.5.1 Parallel Path Fixed-Point Investigation") {
                runPathFixedPointInvestigationSearch()
            }
        layout.addView(pathFixedPointButton, LinearLayout.LayoutParams(-1, -2))

        layout.addView(
            makeText(
                """
                Generates 100,000 deterministic path stress cases. Each path
                is optimized repeatedly and G3.5 records the first pass-2
                stage that changes the pass-1 spelling. This identifies the
                stage-order interaction preventing one-pass fixed points.
                Production idempotence remains fully independent.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val finalCommandConvergenceButton =
            makeButton("Run G3.6 Final-Command Convergence Search") {
                runFinalCommandConvergenceSearch()
            }
        layout.addView(finalCommandConvergenceButton, LinearLayout.LayoutParams(-1, -2))

        layout.addView(
            makeText(
                """
                Generates 100,000 deterministic path stress cases using four
                parallel workers. G3.6 reruns only the syntax/command
                serialization tail identified by G3.5, compares it with an
                independent full second pass, then verifies that the candidate
                is a full-optimizer fixed point. Production behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val postSerializationGeometryConvergenceButton =
            makeButton("Run G3.7 Post-Serialization Geometry Convergence Search") {
                runPostSerializationGeometryConvergenceSearch()
            }
        layout.addView(
            postSerializationGeometryConvergenceButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Reuses the exact G3.6 100,000-case corpus with four parallel
                workers. G3.7 reparses pass-1 output, reruns only redundant
                non-drawing geometry cleanup and exact collinear line
                consolidation, then reruns the command/serialization tail. It
                must match an independent full pass 2, remain fixed under a
                full verification pass, and preserve sampled path geometry.
                Production behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val collinearGeometrySafetyButton =
            makeButton("Run G3.8 Collinear Geometry-Safety Investigation") {
                runCollinearGeometrySafetySearch()
            }
        layout.addView(
            collinearGeometrySafetyButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Reuses the exact G3.7 100,000-case corpus with four parallel
                workers. G3.8 prioritizes every geometry mismatch, reruns a
                denser 2,048-sample comparison on changed paths, compares the
                geometry immediately before and after collinear consolidation,
                and reports the first differing token plus maximum deviation.
                Production optimization behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val subdivisionInvariantGeometryButton =
            makeButton("Run G3.10 Bidirectional Polyline Geometry Comparator") {
                runBidirectionalPolylineGeometrySearch()
            }
        layout.addView(
            subdivisionInvariantGeometryButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Reuses the exact G3.8/G3.9 100,000-case corpus with four parallel
                workers. G3.10 compares ordered flattened subpaths using
                bidirectional point-to-polyline distance with adaptive midpoint
                refinement, plus endpoint, closure, and traveled-length checks.
                Production optimization behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val orderedTraversalButton =
            makeButton("Run G3.11 Ordered Collinear Traversal Safety") {
                runOrderedCollinearTraversalSearch()
            }
        layout.addView(
            orderedTraversalButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Replays all five G3.10 survivors, then runs 100,000 targeted
                L/H/V stress cases with four parallel workers. G3.11 uses exact
                ordered traversal signatures to detect endpoint changes,
                reversals/backtracking, and line-distance changes without the
                adaptive polyline sampler. Production behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val comparatorRepairButton =
            makeButton("Run G3.12 Repaired Bidirectional Comparator Validation") {
                runBidirectionalComparatorRepairSearch()
            }
        layout.addView(
            comparatorRepairButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Replays all five G3.10 direct-collinear survivors through the
                repaired comparator, using exact BigDecimal endpoint bookkeeping
                and subdivision-invariant traveled-length accounting. It also
                runs positive controls plus endpoint, skipped-traversal,
                reversal, perpendicular-offset, and closure mutations to prove
                the repair did not simply make the comparator more permissive.
                Production optimization behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val exactTraversalShortCircuitButton =
            makeButton("Run G3.13 Exact Traversal Short-Circuit Validation") {
                runExactTraversalShortCircuitSearch()
            }
        layout.addView(
            exactTraversalShortCircuitButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Replays the five G3.10 survivors and targeted controls/mutations.
                When G3.11's exact BigDecimal traversal signature proves two
                paths have identical ordered geometry, G3.13 accepts them before
                Float flattening or traveled-length bookkeeping. Non-identical
                traversal falls through to the existing G3.12 bidirectional
                comparator. Production optimization behavior is unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val g314Button =
            makeButton("Run G3.14 G3.7 Corpus with G3.13 Comparator") {
                runG314ConvergenceCorpusSearch()
            }
        layout.addView(
            g314Button,
            LinearLayout.LayoutParams(-1, -2)
        )

        layout.addView(
            makeText(
                """
                Reruns the exact 100,000-case G3.7 convergence corpus with four
                parallel workers. Every changed convergence candidate is checked
                by the validated G3.13 comparator: exact ordered traversal first,
                with the repaired bidirectional comparator only as fallback.
                Reports exact short-circuits, fallbacks, geometry mismatches,
                pass-2 agreement, and fixed-point verification. Production
                optimization behavior remains unchanged.
                """.trimIndent(),
                14f,
                Color.GRAY,
                Gravity.START,
                paddingBottom = 20
            )
        )

        val diagnosticsHeading = makeText(
            "Future Diagnostics",
            16f,
            Color.DKGRAY,
            Gravity.START,
            paddingBottom = 8
        )
        layout.addView(diagnosticsHeading)

        val validateButton = makeButton("Validate Last Conversion — coming soon") {}
        validateButton.isEnabled = false
        layout.addView(validateButton, LinearLayout.LayoutParams(-1, -2))

        val optimizerButton = makeButton("Optimizer Statistics — coming soon") {}
        optimizerButton.isEnabled = false
        layout.addView(optimizerButton, LinearLayout.LayoutParams(-1, -2))

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(layout)
        }

        android.app.AlertDialog.Builder(this)
            .setView(scrollView)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun runCommandNumericDifferentialSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }

        val progressBar = ProgressBar(this)
        val statusText = makeText(
            "Generating and comparing 100,000 paths…",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply {
            setPadding(0, 24, 0, 0)
        }

        val detailText = makeText(
            "This may take a little while on a phone.",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply {
            setPadding(0, 12, 0, 0)
        }

        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("F4.3 Differential Search")
            .setView(progressLayout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        Thread {
            val report = try {
                SvgCommandNumericDifferentialSearch.runDefault()
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("F4.3 automated differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(
                        throwable.message
                            ?: throwable::class.java.simpleName
                    )
                    appendLine()
                    appendLine(
                        "Check that SvgCommandNumericDifferentialSearch.kt " +
                            "and the updated SvgPathDataOptimizer.kt are " +
                            "included in the app."
                    )
                }
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentDifferentialSearchReport = report
                    showDifferentialSearchResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showDifferentialSearchResultsDialog(report: String) {
        val hasMismatch = report.contains(
            "Semantic mismatches: 0"
        ).not() && report.contains("Semantic mismatches:")
        val foundImprovement =
            report.contains("Second-round strict improvements:") &&
                !report.contains("Second-round strict improvements: 0")
        val failed = report.contains("could not be completed")

        val summaryText: String
        val summaryColor: Int

        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }

            hasMismatch -> {
                summaryText = "⚠ Semantic mismatches detected"
                summaryColor = Color.rgb(190, 110, 0)
            }

            foundImprovement -> {
                summaryText = "✓ F4.3 found additional savings"
                summaryColor = Color.rgb(30, 120, 55)
            }

            else -> {
                summaryText = "No second-round reduction found"
                summaryColor = Color.DKGRAY
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        layout.addView(
            makeText(
                summaryText,
                18f,
                summaryColor,
                Gravity.START,
                paddingBottom = 16
            )
        )

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val reportScroll = ScrollView(this).apply {
            addView(reportView)
        }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") {
            copyDifferentialSearchReport()
        }
        val saveButton = makeButton("Save .txt") {
            saveDifferentialSearchReport.launch(
                "f4_3_differential_search_report.txt"
            )
        }
        layout.addView(horizontalRow(copyButton, saveButton))

        val rerunButton = makeButton("Run Again") {
            runCommandNumericDifferentialSearch()
        }
        layout.addView(
            rerunButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("F4.3 Differential Search Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }

        dialog.show()
    }

    private fun copyDifferentialSearchReport() {
        if (currentDifferentialSearchReport.isBlank()) {
            toast("No differential search report to copy")
            return
        }

        val clipboard =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "f4_3_differential_search_report.txt",
                currentDifferentialSearchReport
            )
        )
        toast("Differential search report copied")
    }

    private fun runPostScaleDifferentialSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }

        val progressBar = ProgressBar(this)
        val statusText = makeText(
            "Generating, scaling, and comparing deterministic paths…",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply {
            setPadding(0, 24, 0, 0)
        }

        val detailText = makeText(
            "Compares full and narrowed post-scale optimization off the UI thread.",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply {
            setPadding(0, 12, 0, 0)
        }

        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G2.25 Post-Scale Differential Search")
            .setView(progressLayout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        Thread {
            val report = try {
                SvgPostScaleDifferentialSearch.runDefault()
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G2.25 automated post-scale differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine(
                        "Check that SvgPostScaleDifferentialSearch.kt and the " +
                            "G2.25 SvgPathDataOptimizer.kt are included in the app."
                    )
                }
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentPostScaleDifferentialReport = report
                    showPostScaleDifferentialResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showPostScaleDifferentialResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val geometryMismatch =
            report.contains("Sampled geometry mismatches:") &&
                !report.contains("Sampled geometry mismatches: 0")
        val byteDifference =
            report.contains("Byte differences:") &&
                !report.contains("Byte differences: 0")

        val summaryText: String
        val summaryColor: Int

        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }

            geometryMismatch -> {
                summaryText = "⚠ Sampled geometry mismatches detected"
                summaryColor = Color.rgb(190, 110, 0)
            }

            byteDifference -> {
                summaryText = "⚠ Full/narrowed serialization differences detected"
                summaryColor = Color.rgb(190, 110, 0)
            }

            else -> {
                summaryText = "✓ Full and narrowed outputs matched"
                summaryColor = Color.rgb(30, 120, 55)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        layout.addView(
            makeText(
                summaryText,
                18f,
                summaryColor,
                Gravity.START,
                paddingBottom = 16
            )
        )

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val reportScroll = ScrollView(this).apply {
            addView(reportView)
        }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") {
            copyPostScaleDifferentialReport()
        }
        val saveButton = makeButton("Save .txt") {
            savePostScaleDifferentialReport.launch(
                "g2_25_post_scale_differential_search_report.txt"
            )
        }
        layout.addView(horizontalRow(copyButton, saveButton))

        val rerunButton = makeButton("Run Again") {
            runPostScaleDifferentialSearch()
        }
        layout.addView(
            rerunButton,
            LinearLayout.LayoutParams(-1, -2)
        )

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G2.25 Differential Search Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }

        dialog.show()
    }

    private fun copyPostScaleDifferentialReport() {
        if (currentPostScaleDifferentialReport.isBlank()) {
            toast("No G2.25 differential search report to copy")
            return
        }

        val clipboard =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g2_25_post_scale_differential_search_report.txt",
                currentPostScaleDifferentialReport
            )
        )
        toast("G2.25 differential search report copied")
    }

    private fun runPostScaleStageAddbackSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }

        val progressBar = ProgressBar(this)
        val statusText = makeText(
            "Testing progressive post-scale pipelines…",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }

        val detailText = makeText(
            "Runs 100,000 deterministic source cases off the UI thread.",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }

        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G2.26 Stage-Addback Differential Search")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgPostScaleStageAddbackSearch.runDefault()
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G2.26 automated post-scale stage-addback differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine(
                        "Check that SvgPostScaleStageAddbackSearch.kt and the G2.26 " +
                            "SvgPathDataOptimizer.kt are included in the app."
                    )
                }
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentPostScaleStageAddbackReport = report
                    showPostScaleStageAddbackResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showPostScaleStageAddbackResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val exact = report.contains("RESULT: an exact stage-addback pipeline was found across every seed.")

        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            exact -> {
                summaryText = "✓ Exact reduced pipeline found"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "⚠ No exact reduced pipeline found"
                summaryColor = Color.rgb(190, 110, 0)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(
            makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16)
        )

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") { copyPostScaleStageAddbackReport() }
        val saveButton = makeButton("Save .txt") {
            savePostScaleStageAddbackReport.launch(
                "g2_26_post_scale_stage_addback_search_report.txt"
            )
        }
        layout.addView(horizontalRow(copyButton, saveButton))

        val rerunButton = makeButton("Run Again") { runPostScaleStageAddbackSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G2.26 Stage-Addback Search Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyPostScaleStageAddbackReport() {
        if (currentPostScaleStageAddbackReport.isBlank()) {
            toast("No G2.26 stage-addback report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g2_26_post_scale_stage_addback_search_report.txt",
                currentPostScaleStageAddbackReport
            )
        )
        toast("G2.26 stage-addback report copied")
    }

    private fun runIdempotencePathReuseDifferentialSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }

        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }

        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }

        val noteText = makeText(
            "Comparing independent and reused idempotence passes off the UI thread.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }

        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.4 Stable-Path Reuse Differential Search")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgIdempotencePathReuseDifferentialSearch.runDefault { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress = progress.completedCases.coerceIn(
                                0,
                                progressBar.max
                            )
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            detailText.text = String.format(
                                java.util.Locale.US,
                                "Seed %d of %d  •  %,d / %,d in current seed",
                                progress.seedIndex,
                                progress.seedCount,
                                progress.currentSeedProcessed,
                                progress.casesPerSeed
                            )
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.4 automated stable-path reuse differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine(
                        "Check that SvgIdempotencePathReuseDifferentialSearch.kt and the " +
                            "G3.4 SvgPathDataOptimizer.kt are included in the app."
                    )
                }
            }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentIdempotencePathReuseReport = report
                    showIdempotencePathReuseResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showIdempotencePathReuseResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val exact = report.contains(
            "RESULT: stable-path reuse matched independent recomputation across every seed."
        )

        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            exact -> {
                summaryText = "✓ Stable-path reuse matched exactly"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "⚠ Stable-path reuse mismatch detected"
                summaryColor = Color.rgb(190, 110, 0)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(
            makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16)
        )

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") { copyIdempotencePathReuseReport() }
        val saveButton = makeButton("Save .txt") {
            saveIdempotencePathReuseReport.launch(
                "g3_4_stable_path_reuse_differential_search_report.txt"
            )
        }
        layout.addView(horizontalRow(copyButton, saveButton))

        val rerunButton = makeButton("Run Again") {
            runIdempotencePathReuseDifferentialSearch()
        }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.4 Stable-Path Reuse Search Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyIdempotencePathReuseReport() {
        if (currentIdempotencePathReuseReport.isBlank()) {
            toast("No G3.4 differential search report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_4_stable_path_reuse_differential_search_report.txt",
                currentIdempotencePathReuseReport
            )
        )
        toast("G3.4 differential search report copied")
    }


    private fun runPathFixedPointInvestigationSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Tracing the first pass-2 path stage that changes each pass-1 result.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.5.1 Parallel Path Fixed-Point Investigation")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgPathFixedPointInvestigationSearch.runDefault { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress = progress.completedCases.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            val seedProgress = progress.perSeedProcessed
                                .mapIndexed { index, processed ->
                                    "S${index + 1}: ${String.format(java.util.Locale.US, "%,d", processed)}"
                                }
                                .joinToString("  •  ")
                            detailText.text = "Workers: ${progress.workerCount}  •  $seedProgress"
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.5 automated path optimizer fixed-point investigation")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgPathFixedPointInvestigationSearch.kt and the G3.5 SvgPathDataOptimizer.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentPathFixedPointReport = report
                    showPathFixedPointResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showPathFixedPointResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val fixed = report.contains("RESULT: every generated path was a fixed point after one optimizer pass.")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            fixed -> {
                summaryText = "✓ One-pass path fixed point confirmed"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "⚠ Path fixed-point drift detected"
                summaryColor = Color.rgb(190, 110, 0)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        val copyButton = makeButton("Copy Results") { copyPathFixedPointReport() }
        val saveButton = makeButton("Save .txt") {
            savePathFixedPointReport.launch("g3_5_path_fixed_point_investigation_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runPathFixedPointInvestigationSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.5.1 Parallel Path Fixed-Point Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyPathFixedPointReport() {
        if (currentPathFixedPointReport.isBlank()) {
            toast("No G3.5 fixed-point report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_5_path_fixed_point_investigation_report.txt",
                currentPathFixedPointReport
            )
        )
        toast("G3.5 fixed-point report copied")
    }

    private fun runFinalCommandConvergenceSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Testing the narrow final-command convergence candidate against an independent full pass.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.6 Final-Command Convergence Search")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgFinalCommandConvergenceSearch.runDefault { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress = progress.completedCases.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            val seedProgress = progress.perSeedProcessed
                                .mapIndexed { index, processed ->
                                    "S${index + 1}: ${String.format(java.util.Locale.US, "%,d", processed)}"
                                }
                                .joinToString("  •  ")
                            detailText.text = "Workers: ${progress.workerCount}  •  $seedProgress"
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.6 automated final-command convergence differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgFinalCommandConvergenceSearch.kt and the G3.6 SvgPathDataOptimizer.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentFinalCommandConvergenceReport = report
                    showFinalCommandConvergenceResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showFinalCommandConvergenceResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val exact = report.contains("RESULT: the G3.6 candidate exactly reproduced the independent second pass")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            exact -> {
                summaryText = "✓ Exact fixed-point convergence confirmed"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "⚠ Convergence candidate needs investigation"
                summaryColor = Color.rgb(190, 110, 0)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        val copyButton = makeButton("Copy Results") { copyFinalCommandConvergenceReport() }
        val saveButton = makeButton("Save .txt") {
            saveFinalCommandConvergenceReport.launch("g3_6_final_command_convergence_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runFinalCommandConvergenceSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.6 Final-Command Convergence Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyFinalCommandConvergenceReport() {
        if (currentFinalCommandConvergenceReport.isBlank()) {
            toast("No G3.6 convergence report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_6_final_command_convergence_report.txt",
                currentFinalCommandConvergenceReport
            )
        )
        toast("G3.6 convergence report copied")
    }

    private fun runPostSerializationGeometryConvergenceSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Testing post-serialization geometry cleanup plus the final command/serialization tail.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.7 Post-Serialization Geometry Convergence Search")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgPostSerializationGeometryConvergenceSearch.runDefault { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress = progress.completedCases.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            val seedProgress = progress.perSeedProcessed
                                .mapIndexed { index, processed ->
                                    "S${index + 1}: ${String.format(java.util.Locale.US, "%,d", processed)}"
                                }
                                .joinToString("  •  ")
                            detailText.text = "Workers: ${progress.workerCount}  •  $seedProgress"
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.7 automated post-serialization geometry convergence differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgPostSerializationGeometryConvergenceSearch.kt and the G3.7 SvgPathDataOptimizer.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentPostSerializationGeometryConvergenceReport = report
                    showPostSerializationGeometryConvergenceResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showPostSerializationGeometryConvergenceResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val exact = report.contains(
            "RESULT: the G3.7 candidate exactly reproduced the independent second pass"
        )
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            exact -> {
                summaryText = "✓ Exact geometry convergence confirmed"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "⚠ Geometry convergence needs investigation"
                summaryColor = Color.rgb(190, 110, 0)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        val copyButton = makeButton("Copy Results") {
            copyPostSerializationGeometryConvergenceReport()
        }
        val saveButton = makeButton("Save .txt") {
            savePostSerializationGeometryConvergenceReport.launch(
                "g3_7_post_serialization_geometry_convergence_report.txt"
            )
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") {
            runPostSerializationGeometryConvergenceSearch()
        }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.7 Post-Serialization Geometry Convergence Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyPostSerializationGeometryConvergenceReport() {
        if (currentPostSerializationGeometryConvergenceReport.isBlank()) {
            toast("No G3.7 geometry convergence report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_7_post_serialization_geometry_convergence_report.txt",
                currentPostSerializationGeometryConvergenceReport
            )
        )
        toast("G3.7 geometry convergence report copied")
    }

    private fun runCollinearGeometrySafetySearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Rechecking changed paths with dense geometry diagnostics and prioritized mismatch witnesses.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.8 Collinear Geometry-Safety Investigation")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgCollinearGeometrySafetySearch.runDefault { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress = progress.completedCases.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            val seedProgress = progress.perSeedProcessed
                                .mapIndexed { index, processed ->
                                    "S${index + 1}: ${String.format(java.util.Locale.US, "%,d", processed)}"
                                }
                                .joinToString("  •  ")
                            detailText.text = "Workers: ${progress.workerCount}  •  $seedProgress"
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.8 automated collinear consolidation geometry-safety differential stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgCollinearGeometrySafetySearch.kt and the G3.8 SvgPathDataOptimizer.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentCollinearGeometrySafetyReport = report
                    showCollinearGeometrySafetyResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showCollinearGeometrySafetyResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val denseMismatch = report.contains("RESULT: G3.8 reproduced dense geometry mismatches.")
        val comparatorOnly = report.contains("RESULT: G3.8 found only standard-sampler mismatches")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            denseMismatch -> {
                summaryText = "⚠ Dense geometry mismatches reproduced"
                summaryColor = Color.rgb(190, 110, 0)
            }
            comparatorOnly -> {
                summaryText = "⚠ Standard comparator mismatch only"
                summaryColor = Color.rgb(190, 110, 0)
            }
            else -> {
                summaryText = "✓ No geometry mismatch reproduced"
                summaryColor = Color.rgb(30, 120, 55)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        val copyButton = makeButton("Copy Results") { copyCollinearGeometrySafetyReport() }
        val saveButton = makeButton("Save .txt") {
            saveCollinearGeometrySafetyReport.launch("g3_8_collinear_geometry_safety_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runCollinearGeometrySafetySearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.8 Collinear Geometry-Safety Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyCollinearGeometrySafetyReport() {
        if (currentCollinearGeometrySafetyReport.isBlank()) {
            toast("No G3.8 geometry-safety report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_8_collinear_geometry_safety_report.txt",
                currentCollinearGeometrySafetyReport
            )
        )
        toast("G3.8 geometry-safety report copied")
    }

    private fun runOrderedCollinearTraversalSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Exact analytic traversal checks only; no adaptive polyline subdivision is used.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.11 Ordered Collinear Traversal Safety")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgOrderedCollinearTraversalSearch.runDefault { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress = progress.completedCases.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            val seedProgress = progress.perSeedProcessed
                                .mapIndexed { index, processed ->
                                    "S${index + 1}: ${String.format(java.util.Locale.US, "%,d", processed)}"
                                }
                                .joinToString("  •  ")
                            detailText.text = "Workers: ${progress.workerCount}  •  $seedProgress"
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.11 automated ordered collinear traversal-safety stress search")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgOrderedCollinearTraversalSearch.kt and the G3.11 SvgPathDataOptimizer.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentOrderedCollinearTraversalReport = report
                    showOrderedCollinearTraversalResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showOrderedCollinearTraversalResultsDialog(report: String) {
        val failed = report.contains("could not be completed", ignoreCase = true)
        val unsafe = report.contains("RESULT: G3.11 found an ordered-traversal safety violation.")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            unsafe -> {
                summaryText = "⚠ Ordered traversal violation detected"
                summaryColor = Color.rgb(190, 110, 0)
            }
            else -> {
                summaryText = "✓ Exact ordered traversal preserved"
                summaryColor = Color.rgb(30, 120, 55)
            }
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        val copyButton = makeButton("Copy Results") { copyOrderedCollinearTraversalReport() }
        val saveButton = makeButton("Save .txt") {
            saveOrderedCollinearTraversalReport.launch("g3_11_ordered_collinear_traversal_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runOrderedCollinearTraversalSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.11 Ordered Collinear Traversal Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyOrderedCollinearTraversalReport() {
        if (currentOrderedCollinearTraversalReport.isBlank()) {
            toast("No G3.11 ordered-traversal report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_11_ordered_collinear_traversal_report.txt",
                currentOrderedCollinearTraversalReport
            )
        )
        toast("G3.11 ordered-traversal report copied")
    }

    private fun runBidirectionalComparatorRepairSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 13
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 13",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Preparing survivor replays…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Diagnostic-only; production optimization is unchanged.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.12 Repaired Comparator Validation")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgBidirectionalComparatorRepairSearch.run { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalChecks.coerceAtLeast(1)
                            progressBar.progress = progress.completedChecks.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedChecks,
                                progress.totalChecks
                            )
                            detailText.text = progress.label
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.12 repaired bidirectional comparator validation")
                    appendLine()
                    appendLine("RESULT: The validation could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgBidirectionalComparatorRepairSearch.kt and the G3.12 versions of SvgPathSampler.kt and SvgPathDataOptimizer.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentBidirectionalComparatorRepairReport = report
                    showBidirectionalComparatorRepairResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showBidirectionalComparatorRepairResultsDialog(report: String) {
        val failed = report.contains("could not be completed", ignoreCase = true)
        val validationFailed = report.contains("RESULT: G3.12 comparator validation failed.")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Validation could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            validationFailed -> {
                summaryText = "⚠ Comparator repair still has failures"
                summaryColor = Color.rgb(190, 110, 0)
            }
            else -> {
                summaryText = "✓ Comparator bookkeeping repair validated"
                summaryColor = Color.rgb(30, 120, 55)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") { copyBidirectionalComparatorRepairReport() }
        val saveButton = makeButton("Save .txt") {
            saveBidirectionalComparatorRepairReport.launch("g3_12_bidirectional_comparator_repair_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runBidirectionalComparatorRepairSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.12 Comparator Repair Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyBidirectionalComparatorRepairReport() {
        if (currentBidirectionalComparatorRepairReport.isBlank()) {
            toast("No G3.12 comparator-repair report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_12_bidirectional_comparator_repair_report.txt",
                currentBidirectionalComparatorRepairReport
            )
        )
        toast("G3.12 comparator-repair report copied")
    }

    private fun runExactTraversalShortCircuitSearch() {
        val totalChecks = 17
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = totalChecks
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / $totalChecks",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Preparing survivor replays…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "Diagnostic-only; production optimization is unchanged.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.13 Exact Traversal Short-Circuit")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgExactTraversalShortCircuitSearch.run { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalChecks.coerceAtLeast(1)
                            progressBar.progress = progress.completedChecks.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedChecks,
                                progress.totalChecks
                            )
                            detailText.text = progress.label
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.13 exact ordered-traversal short-circuit validation")
                    appendLine()
                    appendLine("RESULT: The validation could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine("Check that SvgExactTraversalShortCircuitSearch.kt and the G3.13 version of SvgPathSampler.kt are included in the app.")
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentExactTraversalShortCircuitReport = report
                    showExactTraversalShortCircuitResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showExactTraversalShortCircuitResultsDialog(report: String) {
        val failed = report.contains("could not be completed", ignoreCase = true)
        val validationFailed = report.contains("RESULT: G3.13 short-circuit validation failed.")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Validation could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            validationFailed -> {
                summaryText = "⚠ Exact traversal short-circuit has failures"
                summaryColor = Color.rgb(190, 110, 0)
            }
            else -> {
                summaryText = "✓ Exact traversal short-circuit validated"
                summaryColor = Color.rgb(30, 120, 55)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") { copyExactTraversalShortCircuitReport() }
        val saveButton = makeButton("Save .txt") {
            saveExactTraversalShortCircuitReport.launch("g3_13_exact_traversal_short_circuit_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runExactTraversalShortCircuitSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.13 Exact Traversal Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyExactTraversalShortCircuitReport() {
        if (currentExactTraversalShortCircuitReport.isBlank()) {
            toast("No G3.13 exact-traversal report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_13_exact_traversal_short_circuit_report.txt",
                currentExactTraversalShortCircuitReport
            )
        )
        toast("G3.13 exact-traversal report copied")
    }


    private fun runG314ConvergenceCorpusSearch() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }
        val progressBar = ProgressBar(
            this,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = false
            max = 100_000
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText(
            "Progress: 0.0%  •  0 / 100,000",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 24, 0, 0) }
        val detailText = makeText(
            "Workers: starting…",
            13f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 12, 0, 0) }
        val noteText = makeText(
            "G3.13 exact traversal runs first; only unresolved pairs use the slower bidirectional fallback.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 8, 0, 0) }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(detailText)
        progressLayout.addView(noteText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.14 G3.7 Corpus + G3.13 Comparator")
            .setView(progressLayout)
            .setCancelable(false)
            .create()
        progressDialog.show()

        Thread {
            val report = try {
                SvgPostSerializationGeometryConvergenceSearch.runG314Default { progress ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && progressDialog.isShowing) {
                            progressBar.max = progress.totalCases.coerceAtLeast(1)
                            progressBar.progress =
                                progress.completedCases.coerceIn(0, progressBar.max)
                            statusText.text = String.format(
                                java.util.Locale.US,
                                "Progress: %.1f%%  •  %,d / %,d",
                                progress.percentComplete,
                                progress.completedCases,
                                progress.totalCases
                            )
                            val seedProgress = progress.perSeedProcessed
                                .mapIndexed { index, processed ->
                                    "S${index + 1}: ${
                                        String.format(
                                            java.util.Locale.US,
                                            "%,d",
                                            processed
                                        )
                                    }"
                                }
                                .joinToString("  •  ")
                            detailText.text =
                                "Workers: ${progress.workerCount}  •  $seedProgress"
                        }
                    }
                }
            } catch (throwable: Throwable) {
                buildString {
                    appendLine("G3.14 automated G3.7 convergence-corpus rerun with G3.13 geometry comparator")
                    appendLine()
                    appendLine("RESULT: The search could not be completed.")
                    appendLine()
                    appendLine(throwable.message ?: throwable::class.java.simpleName)
                    appendLine()
                    appendLine(
                        "Check that SvgPostSerializationGeometryConvergenceSearch.kt, " +
                            "SvgPathDataOptimizer.kt, and the G3.13 SvgPathSampler.kt " +
                            "are included in the app."
                    )
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentG314ConvergenceReport = report
                    showG314ConvergenceCorpusResultsDialog(report)
                }
            }
        }.start()
    }

    private fun showG314ConvergenceCorpusResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val exact = report.contains(
            "RESULT: G3.14 exactly reproduced the independent second pass"
        )
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            exact -> {
                summaryText = "✓ G3.14 convergence + geometry validation passed"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "⚠ G3.14 needs investigation"
                summaryColor = Color.rgb(190, 110, 0)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(
            makeText(
                summaryText,
                18f,
                summaryColor,
                Gravity.START,
                paddingBottom = 16
            )
        )
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Results") { copyG314ConvergenceReport() }
        val saveButton = makeButton("Save .txt") {
            saveG314ConvergenceReport.launch("g3_14_convergence_corpus_g313_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runG314ConvergenceCorpusSearch() }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.14 Convergence Corpus Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyG314ConvergenceReport() {
        if (currentG314ConvergenceReport.isBlank()) {
            toast("No G3.14 convergence-corpus report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_14_convergence_corpus_g313_report.txt",
                currentG314ConvergenceReport
            )
        )
        toast("G3.14 convergence-corpus report copied")
    }

    private fun runBidirectionalPolylineGeometrySearch(forceRestart: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 310)
        }

        val existing = G310SearchForegroundService.snapshot()
        val activeStatuses = setOf(
            G310SearchForegroundService.Status.RUNNING,
            G310SearchForegroundService.Status.MANUAL_PAUSED,
            G310SearchForegroundService.Status.THERMAL_PAUSED
        )
        val savedReport = G310SearchForegroundService.readReport(this)
        if (!forceRestart && existing.status !in activeStatuses && savedReport.isNotBlank()) {
            currentBidirectionalPolylineGeometryReport = savedReport
            showBidirectionalPolylineGeometryResultsDialog(savedReport)
            return
        }
        if (!forceRestart && existing.status !in activeStatuses && G310SearchForegroundService.hasCheckpoint(this)) {
            val savedProgress = String.format(
                java.util.Locale.US,
                "%,d / %,d cases (%.1f%%)",
                existing.completedCases,
                existing.totalCases,
                existing.percentComplete
            )
            val elapsed = G310SearchForegroundService.formatDuration(existing.elapsedMillis)
            val eta = existing.estimatedRemainingMillis?.let {
                G310SearchForegroundService.formatDuration(it)
            } ?: "calculating after resume"
            android.app.AlertDialog.Builder(this)
                .setTitle("Saved G3.10 Progress Found")
                .setMessage("Saved progress: $savedProgress\nActive elapsed time: $elapsed\nEstimated remaining: $eta")
                .setPositiveButton("Resume") { _, _ -> runBidirectionalPolylineGeometrySearchFromState(false) }
                .setNeutralButton("Restart") { _, _ -> runBidirectionalPolylineGeometrySearchFromState(true) }
                .setNegativeButton("Delete") { _, _ ->
                    G310SearchForegroundService.clearCheckpoint(this)
                    toast("Saved G3.10 checkpoint deleted")
                }
                .show()
            return
        }
        runBidirectionalPolylineGeometrySearchFromState(forceRestart)
    }

    private fun runBidirectionalPolylineGeometrySearchFromState(forceRestart: Boolean) {
        val existing = G310SearchForegroundService.snapshot()
        val activeStatuses = setOf(
            G310SearchForegroundService.Status.RUNNING,
            G310SearchForegroundService.Status.MANUAL_PAUSED,
            G310SearchForegroundService.Status.THERMAL_PAUSED
        )
        if (existing.status !in activeStatuses) {
            if (forceRestart) G310SearchForegroundService.clearPreviousReport(this)
            ContextCompat.startForegroundService(
                this,
                Intent(this, G310SearchForegroundService::class.java).setAction(
                    if (forceRestart) G310SearchForegroundService.ACTION_RESTART
                    else G310SearchForegroundService.ACTION_START
                )
            )
        }

        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 32)
        }
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100_000
            progress = existing.completedCases
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val statusText = makeText("Progress: loading…", 16f, Color.DKGRAY, Gravity.CENTER).apply {
            setPadding(0, 24, 0, 0)
        }
        val timingText = makeText("Elapsed: loading…", 14f, Color.DKGRAY, Gravity.CENTER).apply {
            setPadding(0, 12, 0, 0)
        }
        val detailText = makeText("Workers: starting…", 13f, Color.GRAY, Gravity.CENTER).apply {
            setPadding(0, 12, 0, 0)
        }
        val thermalText = makeText("Thermal monitoring active", 13f, Color.GRAY, Gravity.CENTER).apply {
            setPadding(0, 10, 0, 0)
        }
        val checkpointText = makeText("Checkpoint: preparing…", 12f, Color.GRAY, Gravity.CENTER).apply {
            setPadding(0, 10, 0, 0)
        }
        val noteText = makeText(
            "The foreground service continues with the screen off or while another app is open. " +
                "Progress is saved every 250 cases per seed and whenever the search pauses or stops.",
            12f,
            Color.GRAY,
            Gravity.CENTER
        ).apply { setPadding(0, 10, 0, 0) }
        val pauseResumeButton = makeButton("Pause") {
            val snapshot = G310SearchForegroundService.snapshot()
            val action = if (snapshot.status == G310SearchForegroundService.Status.MANUAL_PAUSED) {
                G310SearchForegroundService.ACTION_RESUME
            } else G310SearchForegroundService.ACTION_PAUSE
            startService(Intent(this, G310SearchForegroundService::class.java).setAction(action))
        }
        val stopButton = makeButton("Stop & Save") {
            startService(Intent(this, G310SearchForegroundService::class.java).setAction(G310SearchForegroundService.ACTION_STOP))
        }
        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)
        progressLayout.addView(timingText)
        progressLayout.addView(detailText)
        progressLayout.addView(thermalText)
        progressLayout.addView(checkpointText)
        progressLayout.addView(noteText)
        progressLayout.addView(horizontalRow(pauseResumeButton, stopButton))

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.10 Bidirectional Polyline Geometry Comparator")
            .setView(progressLayout)
            .setNegativeButton("Hide") { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .create()
        progressDialog.show()

        val handler = Handler(Looper.getMainLooper())
        lateinit var poll: Runnable
        poll = Runnable {
            if (!progressDialog.isShowing || isFinishing || isDestroyed) return@Runnable
            val snapshot = G310SearchForegroundService.snapshot()
            progressBar.max = snapshot.totalCases.coerceAtLeast(1)
            progressBar.progress = snapshot.completedCases.coerceIn(0, progressBar.max)
            statusText.text = String.format(
                java.util.Locale.US,
                "Progress: %.1f%%  •  %,d / %,d",
                snapshot.percentComplete,
                snapshot.completedCases,
                snapshot.totalCases
            )
            val elapsed = G310SearchForegroundService.formatDuration(snapshot.elapsedMillis)
            val eta = snapshot.estimatedRemainingMillis?.let {
                G310SearchForegroundService.formatDuration(it)
            } ?: "calculating…"
            timingText.text = "Active elapsed: $elapsed  •  Estimated remaining: $eta"
            detailText.text = "Workers: ${snapshot.workerCount}  •  " +
                snapshot.perSeedProcessed.mapIndexed { index, processed ->
                    "S${index + 1}: ${String.format(java.util.Locale.US, "%,d", processed)}"
                }.joinToString("  •  ")
            thermalText.text = snapshot.message
            thermalText.setTextColor(
                when (snapshot.status) {
                    G310SearchForegroundService.Status.THERMAL_PAUSED -> Color.rgb(190, 75, 0)
                    G310SearchForegroundService.Status.MANUAL_PAUSED -> Color.rgb(150, 105, 0)
                    else -> Color.GRAY
                }
            )
            checkpointText.text = when {
                snapshot.lastSavedMillis > 0L -> "Checkpoint saved • resume is available after an interruption"
                snapshot.checkpointAvailable -> "Saved checkpoint available"
                else -> "Checkpoint will be written at the next 250-case boundary"
            }
            pauseResumeButton.text = if (snapshot.status == G310SearchForegroundService.Status.MANUAL_PAUSED) "Resume" else "Pause"
            pauseResumeButton.isEnabled = snapshot.status != G310SearchForegroundService.Status.THERMAL_PAUSED

            when (snapshot.status) {
                G310SearchForegroundService.Status.COMPLETED -> {
                    progressDialog.dismiss()
                    val report = G310SearchForegroundService.readReport(this)
                    currentBidirectionalPolylineGeometryReport = report
                    if (report.isNotBlank()) showBidirectionalPolylineGeometryResultsDialog(report)
                    else toast("G3.10 completed, but its report could not be read")
                }
                G310SearchForegroundService.Status.STOPPED -> {
                    progressDialog.dismiss()
                    toast(if (snapshot.checkpointAvailable) "G3.10 stopped; progress saved" else "G3.10 search stopped")
                }
                else -> handler.postDelayed(poll, 750L)
            }
        }
        handler.post(poll)
        progressDialog.setOnDismissListener { handler.removeCallbacks(poll) }
    }

    private fun showBidirectionalPolylineGeometryResultsDialog(report: String) {
        val failed = report.contains("could not be completed")
        val invariantMismatch = report.contains("RESULT: G3.10 found direct collinear geometry differences") ||
            report.contains("RESULT: G3.10 cleared the direct collinear signal but found residual geometry differences")
        val comparatorArtifact = report.contains("RESULT: G3.10 classified the G3.9 direct-collinear signal as a comparator artifact on this corpus.")
        val summaryText: String
        val summaryColor: Int
        when {
            failed -> {
                summaryText = "✕ Search could not be completed"
                summaryColor = Color.rgb(180, 35, 35)
            }
            invariantMismatch -> {
                summaryText = "⚠ Bidirectional geometry mismatch remains"
                summaryColor = Color.rgb(190, 110, 0)
            }
            comparatorArtifact -> {
                summaryText = "✓ G3.9 signal classified as comparator artifact"
                summaryColor = Color.rgb(30, 120, 55)
            }
            else -> {
                summaryText = "✓ No bidirectional geometry mismatch reproduced"
                summaryColor = Color.rgb(30, 120, 55)
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }
        layout.addView(makeText(summaryText, 18f, summaryColor, Gravity.START, paddingBottom = 16))
        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        val reportScroll = ScrollView(this).apply { addView(reportView) }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        val copyButton = makeButton("Copy Results") { copyBidirectionalPolylineGeometryReport() }
        val saveButton = makeButton("Save .txt") {
            saveBidirectionalPolylineGeometryReport.launch("g3_10_bidirectional_polyline_geometry_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))
        val rerunButton = makeButton("Run Again") { runBidirectionalPolylineGeometrySearch(forceRestart = true) }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("G3.10 Bidirectional Polyline Geometry Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }
        dialog.show()
    }

    private fun copyBidirectionalPolylineGeometryReport() {
        if (currentBidirectionalPolylineGeometryReport.isBlank()) {
            toast("No G3.10 bidirectional-polyline report to copy")
            return
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "g3_10_bidirectional_polyline_geometry_report.txt",
                currentBidirectionalPolylineGeometryReport
            )
        )
        toast("G3.10 bidirectional-polyline report copied")
    }

    private fun runBundledRegressionSuite() {
        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
        }

        val progressBar = ProgressBar(this)
        val statusText = makeText(
            "Running 5 bundled regression tests…",
            16f,
            Color.DKGRAY,
            Gravity.CENTER
        ).apply {
            setPadding(0, 24, 0, 0)
        }

        progressLayout.addView(progressBar)
        progressLayout.addView(statusText)

        val progressDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Regression Suite")
            .setView(progressLayout)
            .setCancelable(false)
            .create()

        progressDialog.show()

        Thread {
            val suiteResult = try {
                SvgRegressionSuiteE1_2.run()
            } catch (throwable: Throwable) {
                null
            }

            val report = suiteResult?.toPlainTextReport()
                ?: buildString {
                    appendLine("Regression suite")
                    appendLine()
                    appendLine("Tests run: 0")
                    appendLine("Passed: 0")
                    appendLine("Failed: 1")
                    appendLine()
                    appendLine("✕ The regression suite could not be started.")
                    appendLine("  Check that SvgRegressionRunner.kt and")
                    appendLine("  SvgRegressionSuiteE1_2.kt are included in the app.")
                }

            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                    currentRegressionReport = report
                    showRegressionResultsDialog(
                        suiteResult = suiteResult,
                        report = report
                    )
                }
            }
        }.start()
    }

    private fun showRegressionResultsDialog(
        suiteResult: SvgRegressionRunner.SuiteResult?,
        report: String
    ) {
        val passed = suiteResult?.passed == true
        val passedCount = suiteResult?.passedCount ?: 0
        val failedCount = suiteResult?.failedCount ?: 1

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val summary = makeText(
            if (passed) {
                "✓ All $passedCount tests passed"
            } else {
                "✕ $failedCount test${if (failedCount == 1) "" else "s"} failed"
            },
            18f,
            if (passed) Color.rgb(30, 120, 55) else Color.rgb(180, 35, 35),
            Gravity.START,
            paddingBottom = 16
        )
        layout.addView(summary)

        val reportView = TextView(this).apply {
            text = report
            textSize = 13f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(248, 248, 248))
            setPadding(24, 24, 24, 24)
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val reportScroll = ScrollView(this).apply {
            addView(reportView)
        }
        layout.addView(
            reportScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val copyButton = makeButton("Copy Report") {
            copyRegressionReport()
        }
        val saveButton = makeButton("Save .txt") {
            saveRegressionReport.launch("svg_regression_report.txt")
        }
        layout.addView(horizontalRow(copyButton, saveButton))

        val rerunButton = makeButton("Run Again") {
            runBundledRegressionSuite()
        }
        layout.addView(rerunButton, LinearLayout.LayoutParams(-1, -2))

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Regression Suite Results")
            .setView(layout)
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnShowListener {
            val screenHeight = resources.displayMetrics.heightPixels
            dialog.window?.setLayout(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.86f).toInt()
            )
        }

        dialog.show()
    }

    private fun copyRegressionReport() {
        if (currentRegressionReport.isBlank()) {
            toast("No regression report to copy")
            return
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "svg_regression_report.txt",
                currentRegressionReport
            )
        )
        toast("Regression report copied")
    }

    private fun showAboutDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 24)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(160, 160)
        }

        val title = makeText(
            "SVG → Android Vector",
            22f,
            Color.BLACK,
            Gravity.CENTER,
            paddingBottom = 16
        ).apply {
            setPadding(0, 16, 0, 16)
        }

        val description = makeText(
            """
            Convert static SVG artwork into
            Android VectorDrawable XML.

            Supports most commonly used SVG 1.1 features,
            including advanced text converted to vector paths.
            """.trimIndent(),
            16f,
            Color.DKGRAY,
            Gravity.CENTER,
            paddingBottom = 28
        )

        val features = makeText(
            """
            Core Features

            ✓ Single and batch SVG conversion
            ✓ ZIP export, XML copy, and file saving
            ✓ Live VectorDrawable preview
            ✓ Size presets and conversion profiles
            ✓ Compatibility and visual-fidelity reporting

            Drawing and Paint

            ✓ Paths and basic shapes
            ✓ Fill, stroke, opacity, fill rules, and dashed strokes
            ✓ Linear and radial gradients
            ✓ Patterns, clip paths, masks, and markers
            ✓ Paint-order support
            ✓ defs, symbols, and use references

            CSS and Transforms

            ✓ Style and presentation attributes
            ✓ CSS selectors, custom properties, currentColor, and inline imports
            ✓ Translate, scale, rotate, skew, matrix, and transform-origin
            ✓ Nested and inherited transforms

            Advanced Text

            ✓ Embedded SVG fonts converted to vector paths
            ✓ Android system-font outline fallback
            ✓ Font family, weight, anchors, and baselines
            ✓ tspan, dx/dy lists, and per-glyph rotation
            ✓ Kerning with hkern and vkern
            ✓ textLength and lengthAdjust
            ✓ Letter spacing and word spacing
            ✓ Text decoration
            ✓ Vertical writing modes
            ✓ textPath with align, stretch, startOffset, and closed-path wrapping
            ✓ Right-to-left and bidirectional text
            """.trimIndent(),
            15f,
            Color.DKGRAY,
            Gravity.START,
            paddingBottom = 28
        )

        val releaseHighlights = makeText(
            """
            Version 1.3 Highlights

            • Full paint-order support
            • currentColor and context-fill/context-stroke
            • Improved gradient inheritance and paint resolution
            • Dashed stroke conversion and dash offset normalization
            • Non-scaling stroke and dash compensation
            • Marker rendering refinements
            • Cleaner exported paths with geometry cleanup
            """.trimIndent(),
            15f,
            Color.DKGRAY,
            Gravity.START,
            paddingBottom = 28
        )

        val note = makeText(
            """
            Unsupported or approximated SVG features are reported when detected.
            Some behavior may be approximated where Android VectorDrawable cannot represent SVG exactly.
            """.trimIndent(),
            15f,
            Color.DKGRAY,
            Gravity.CENTER,
            paddingBottom = 28
        )

        val footer = makeText(
            """
            Version ${getVersionName()}
            © 2026 Nathan Harris
            """.trimIndent(),
            14f,
            Color.GRAY,
            Gravity.CENTER
        )

        layout.addView(icon)
        layout.addView(title)
        layout.addView(description)
        layout.addView(features)
        layout.addView(releaseHighlights)
        layout.addView(note)
        layout.addView(footer)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(
                layout,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        android.app.AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateActionButtons() {
        copyButton.isEnabled = convertedXml.isNotBlank()
        saveXmlButton.isEnabled = convertedXml.isNotBlank()
        saveZipButton.isEnabled = batchResults.isNotEmpty()
        val reportAvailable = hasReport()
        copyReportButton.isEnabled = reportAvailable
        saveReportTextButton.isEnabled = reportAvailable
        saveReportImageButton.isEnabled = reportAvailable
    }

    private fun showBatchGallery() {
        BatchGalleryRenderer.render(
            context = this,
            container = batchGallery,
            results = batchResults
        )
    }

    private fun updatePreview(xml: String) {
        try {
            val bitmap = VectorPreviewRenderer.render(xml, 512, 512)
            previewBox.setImageDrawable(BitmapDrawable(resources, bitmap))
        } catch (e: Exception) {
            previewBox.setImageDrawable(null)
            reportBox.text = reportBox.text.toString() + "\n\nPreview failed: ${e.message}"
        }
    }

    private fun getVersionName(): String {
        return BuildConfig.VERSION_NAME.takeIf { it.isNotBlank() }
            ?: try {
                packageManager
                    .getPackageInfo(packageName, 0)
                    .versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
