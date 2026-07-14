package com.example.svgvectorconverter

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
            addView(aboutButton, LinearLayout.LayoutParams(-1, -2))
        }

        val tabRow = horizontalRow(previewTab, xmlTab)

        reportBox = makeText("No SVG converted yet", 14f, Color.BLACK).apply {
            setPadding(0, 16, 0, 16)
        }

        mainPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scrollView = ScrollView(this).apply {
            addView(mainPanel)
        }

        mainPanel.addView(previewBox, LinearLayout.LayoutParams(-1, 450))
        mainPanel.addView(outputBox, LinearLayout.LayoutParams(-1, -2))
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
            Version 1.2 Highlights

            • Complete vector-path text engine
            • Embedded SVG font rendering
            • Advanced text layout and textPath support
            • Letter spacing, word spacing, and decorations
            • Paint-order layering
            • RTL and bidirectional text
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
                ScrollView.LayoutParams(
                    ScrollView.LayoutParams.MATCH_PARENT,
                    ScrollView.LayoutParams.WRAP_CONTENT
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
