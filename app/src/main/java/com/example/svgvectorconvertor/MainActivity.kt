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

    private val prefs by lazy {
        getSharedPreferences("svg_converter_settings", MODE_PRIVATE)
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

        outputDpSize = prefs.getInt("outputDpSize", 24)
        conversionProfile = prefs.getString(
            "conversionProfile",
            "Default"
        ) ?: "Default"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 80, 32, 32)
            setBackgroundColor(Color.rgb(250, 248, 240))
        }

        val title = TextView(this).apply {
            text = "SVG → Android Vector"
            textSize = 24f
            setTextColor(Color.BLACK)
        }

        val versionLabel = TextView(this).apply {
            text = "v${getVersionName()}"
            textSize = 12f
            setTextColor(Color.GRAY)
        }

        val openButton = Button(this).apply {
            text = "Open SVG"
            setOnClickListener {
                openSvg.launch(arrayOf("image/svg+xml", "text/xml", "text/plain"))
            }
        }

        copyButton = Button(this).apply {
            text = "Copy XML"
            setOnClickListener { copyConvertedXml() }
        }

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

        val batchButton = Button(this).apply {
            text = "Batch SVGs"
            setOnClickListener {
                openMultipleSvgs.launch(arrayOf("image/svg+xml", "text/xml", "text/plain"))
            }
        }

        saveZipButton = Button(this).apply {
            text = "Save ZIP"
            setOnClickListener {
                if (batchResults.isEmpty()) {
                    toast("No batch results yet")
                } else {
                    saveZip.launch("converted_vectors.zip")
                }
            }
        }

        saveXmlButton = Button(this).apply {
            text = "Save XML"
            setOnClickListener {
                if (convertedXml.isBlank()) {
                    toast("Nothing to save yet")
                } else {
                    saveXml.launch(suggestedFileName)
                }
            }
        }

        val aboutButton = Button(this).apply {
            text = "About"
            setOnClickListener {
                showAboutDialog()
            }
        }

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

        val previewTab = Button(this).apply {
            text = "Preview"
            setOnClickListener { showPreviewTab() }
        }

        val xmlTab = Button(this).apply {
            text = "XML"
            setOnClickListener { showXmlTab() }
        }

        val openRow = horizontalRow(openButton, batchButton)
        val saveRow = horizontalRow(saveXmlButton, saveZipButton)

        val utilityRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            addView(horizontalRow(copyButton, sizeButton))
            addView(profileButton, LinearLayout.LayoutParams(-1, -2))
            addView(aboutButton, LinearLayout.LayoutParams(-1, -2))
        }

        val tabRow = horizontalRow(previewTab, xmlTab)

        reportBox = TextView(this).apply {
            text = "No SVG converted yet"
            textSize = 14f
            setTextColor(Color.BLACK)
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
        saveOutputDpSize()
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

        prefs.edit()
            .putString("conversionProfile", conversionProfile)
            .putInt("outputDpSize", outputDpSize)
            .apply()

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

    private fun saveOutputDpSize() {
        prefs.edit()
            .putInt("outputDpSize", outputDpSize)
            .apply()
    }

    private fun sizeButtonText(): String {
        return if (outputDpSize > 0) {
            "Size: ${outputDpSize}dp"
        } else {
            "Size: SVG"
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
        previewBox.visibility = View.VISIBLE
        reportBox.visibility = View.VISIBLE
        batchGallery.visibility = View.VISIBLE
        outputBox.visibility = View.GONE
    }

    private fun showXmlTab() {
        previewBox.visibility = View.GONE
        reportBox.visibility = View.GONE
        batchGallery.visibility = View.GONE
        outputBox.visibility = View.VISIBLE
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

        val title = TextView(this).apply {
            text = "SVG → Android Vector"
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 16)
        }

        val description = TextView(this).apply {
            text =
                """
                Convert SVG artwork into
                Android VectorDrawable XML.
                """.trimIndent()

            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        val features = TextView(this).apply {
            text =
                """
                Features

                ✓ SVG → VectorDrawable conversion
                ✓ Batch ZIP export
                ✓ Preview rendering
                ✓ Size presets
                ✓ Conversion profiles
                """.trimIndent()

            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        val note = TextView(this).apply {
            text =
                """
                Unsupported SVG features are reported
                when detected.
                """.trimIndent()

            textSize = 16f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 28)
        }

        val footer = TextView(this).apply {
            text =
                """
                Version ${getVersionName()}
                © 2026 Nathan Harris
                """.trimIndent()

            textSize = 14f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
        }

        layout.addView(icon)
        layout.addView(title)
        layout.addView(description)
        layout.addView(features)
        layout.addView(note)
        layout.addView(footer)

        android.app.AlertDialog.Builder(this)
            .setView(layout)
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
    return try {
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
