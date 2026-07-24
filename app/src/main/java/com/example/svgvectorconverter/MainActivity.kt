package com.example.svgvectorconverter

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
