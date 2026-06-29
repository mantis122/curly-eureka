package com.example.svgvectorconverter

import android.os.*
import android.content.*
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.provider.OpenableColumns
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.PI

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
    if (uri != null) {
        suggestedFileName = makeXmlFileName(uri)

        val svg = contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: ""

val result = SvgToVectorConverter.convert(
    svg,
    outputDpSize,
    conversionProfile
)
previewBox.visibility = View.VISIBLE
        convertedXml = result.xml
        reportBox.text = result.report
        outputBox.setText(convertedXml)
        updatePreview(convertedXml)
        batchGallery.removeAllViews()
        batchResults.clear()
        updateActionButtons()

    }
}

private val openMultipleSvgs = registerForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
) { uris ->
    if (uris.isNotEmpty()) {
        batchResults.clear()

convertedXml = ""
previewBox.setImageDrawable(null)
outputBox.setText("")
updateActionButtons()

        uris.forEach { uri ->
            val svg = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""

    val fileName = makeXmlFileName(uri)

try {
val result = SvgToVectorConverter.convert(
    svg,
    outputDpSize,
    conversionProfile
)   

    val warningCount =
        result.report.lines().count { it.startsWith("⚠") }

val definitionPathCount =
    Regex("""Drawable definitions:\s*(\d+)""")
        .find(result.report)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0

    batchResults.add(
        BatchResult(
    fileName = fileName,
    xml = result.xml,
    warningCount = warningCount,
    definitionPathCount = definitionPathCount,
    success = true
)
    )
} catch (e: Exception) {
    batchResults.add(
        BatchResult(
            fileName = fileName,
            xml = null,
            warningCount = 0,
            success = false,
            error = e.message
        )
    )
}       

        }

        val successCount = batchResults.count { it.success }
val warningCount = batchResults.count {
    it.success && it.warningCount > 0
}
val failureCount = batchResults.count { !it.success }

reportBox.text =
    """
🟢 Batch Conversion Complete

Success: $successCount
Warnings: $warningCount
Failed: $failureCount

Ready to save ZIP
""".trimIndent()

val xmlOutput = buildString {
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

outputBox.setText(xmlOutput)

        showBatchGallery()

previewBox.visibility = View.GONE
updateActionButtons()

        toast("${batchResults.size} files converted")
    }
}

private val saveZip = registerForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    if (uri != null) {
        contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                batchResults
    .filter { it.success && it.xml != null }
    .forEach { result ->
                    zip.putNextEntry(ZipEntry(result.fileName))
                    zip.write(result.xml!!.toByteArray()) 
                    zip.closeEntry()
                }
            }
        }
        toast("ZIP saved")
    }
}


    private val saveXml = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use {
                it.write(convertedXml.toByteArray())
            }
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

val versionName = try {
    packageManager
        .getPackageInfo(packageName, 0)
        .versionName
} catch (e: Exception) {
    "1.0"
}

val title = TextView(this).apply {
    text = "SVG → Android Vector"
    textSize = 24f
    setTextColor(Color.BLACK)
}

val versionLabel = TextView(this).apply {
    text = "v$versionName"
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
            setOnClickListener {
                if (convertedXml.isBlank()) {
                    toast("Nothing to copy yet")
                } else {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("vector.xml", convertedXml))
                    toast("Copied")
                }
            }
        }


val sizeButton = Button(this).apply {
    text =
    if (outputDpSize > 0)
        "Size: ${outputDpSize}dp"
    else
        "Size: SVG"
    setOnClickListener {
        val options = arrayOf("24dp", "48dp", "Keep SVG size", "Custom...")
        android.app.AlertDialog.Builder(this@MainActivity)
            .setTitle("Output Size")
            .setItems(options) { _, which ->
               when (which) {
    0 -> {
        outputDpSize = 24

        prefs.edit()
        .putInt("outputDpSize", outputDpSize)
        .apply()

        text = "Size: 24dp"
    }
    1 -> {
        outputDpSize = 48

        prefs.edit()
        .putInt("outputDpSize", outputDpSize)
        .apply()

        text = "Size: 48dp"
    }
    2 -> {
        outputDpSize = -1

        prefs.edit()
        .putInt("outputDpSize", outputDpSize)
        .apply()

        text = "Size: SVG"
    }
    3 -> {
        showCustomSizeDialog(this)
    }
}
            }
            .show()
    }
}


val profileButton = Button(this).apply {
    text = "Profile: $conversionProfile"
    setOnClickListener {
        val options = arrayOf(
            "Default",
            "Android Icon",
            "Material Icon",
            "Keep SVG"
        )

        android.app.AlertDialog.Builder(this@MainActivity)
            .setTitle("Conversion Profile")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        conversionProfile = "Default"
                        outputDpSize = 24

                        prefs.edit()
                        .putString("conversionProfile", conversionProfile)
                        .putInt("outputDpSize", outputDpSize)
                        .apply()

                        text = "Profile: Default"
                        sizeButton.text = "Size: 24dp"
                    }
                    1 -> {
                        conversionProfile = "Android Icon"
                        outputDpSize = 24

                        prefs.edit()
                        .putString("conversionProfile", conversionProfile)
                        .putInt("outputDpSize", outputDpSize)
                        .apply()

                        text = "Profile: Android Icon"
                        sizeButton.text = "Size: 24dp"
                    }
                    2 -> {
                        conversionProfile = "Material Icon"
                        outputDpSize = 24

                        prefs.edit()
                        .putString("conversionProfile", conversionProfile)
                        .putInt("outputDpSize", outputDpSize)
                        .apply()

                        text = "Profile: Material"
                        sizeButton.text = "Size: 24dp"
                    }
                    3 -> {
                        conversionProfile = "Keep SVG"
                        outputDpSize = -1

                        prefs.edit()
                        .putString("conversionProfile", conversionProfile)
                        .putInt("outputDpSize", outputDpSize)
                        .apply()

                        text = "Profile: Keep SVG"
                        sizeButton.text = "Size: SVG"
                    }
                }
            }
            .show()
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

    val bottomSpacer = Space(this)

        outputBox = EditText(this).apply {
    hint = "Converted VectorDrawable XML will appear here"
    setTextColor(Color.BLACK)
    setHintTextColor(Color.GRAY)
    textSize = 12f
    gravity = Gravity.TOP or Gravity.START
    isSingleLine = false
    setHorizontallyScrolling(true)
    minLines = 20
}
        outputBox.visibility = View.GONE

    

val previewTab = Button(this).apply {
    text = "Preview"
    setOnClickListener {
        previewBox.visibility = View.VISIBLE
        reportBox.visibility = View.VISIBLE
        batchGallery.visibility = View.VISIBLE
        outputBox.visibility = View.GONE
    }
}

val xmlTab = Button(this).apply {
    text = "XML"
    setOnClickListener {
        previewBox.visibility = View.GONE
        reportBox.visibility = View.GONE
        batchGallery.visibility = View.GONE
        outputBox.visibility = View.VISIBLE
    }
}
val openRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(openButton, LinearLayout.LayoutParams(0, -2, 1f))
    addView(batchButton, LinearLayout.LayoutParams(0, -2, 1f))
}

val saveRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(saveXmlButton, LinearLayout.LayoutParams(0, -2, 1f))
    addView(saveZipButton, LinearLayout.LayoutParams(0, -2, 1f))
}

val utilityRow = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL

    addView(
        LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(copyButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(sizeButton, LinearLayout.LayoutParams(0, -2, 1f))
        }
    )

    addView(
        profileButton,
        LinearLayout.LayoutParams(-1, -2)
    )

    addView(
        aboutButton,
        LinearLayout.LayoutParams(-1, -2)
    )

}

val tabRow = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    addView(previewTab, LinearLayout.LayoutParams(0, -2, 1f))
    addView(xmlTab, LinearLayout.LayoutParams(0, -2, 1f))
}

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
mainPanel.addView(bottomSpacer, LinearLayout.LayoutParams(-1, 96))

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
                outputDpSize = size
                sizeButton.text = "Size: ${size}dp"
                prefs.edit()
                .putInt("outputDpSize", outputDpSize)
                .apply()
        
    }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun showAboutDialog() {
    val versionName = try {
        packageManager
            .getPackageInfo(packageName, 0)
            .versionName
    } catch (e: Exception) {
        "1.0"
    }

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
Version $versionName
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

private fun makeXmlFileName(uri: android.net.Uri): String {
    var displayName: String? = null

    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            displayName = cursor.getString(nameIndex)
        }
    }

    val name = displayName ?: "converted_vector.svg"

    val baseName = name
        .substringBeforeLast(".")
        .lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "converted_vector" }

    return "$baseName.xml"
} 

private fun showBatchGallery() {
    batchGallery.removeAllViews()
    
val heading = TextView(this).apply {
    text = "Batch Results"
    textSize = 20f
    setTextColor(Color.BLACK)
    setPadding(0, 24, 0, 12)
}

batchGallery.addView(heading)

    batchResults.forEach { result ->

 val label = TextView(this).apply {
    text = buildString {

    append(
        when {
            !result.success -> "✕ "
            result.warningCount > 0 -> "⚠ "
            else -> "✓ "
        }
    )

    append(result.fileName)

    if (result.definitionPathCount > 0) {
        append("  (${result.definitionPathCount} defs available)")
    }
}
    textSize = 16f
    setTextColor(Color.BLACK)
    setPadding(0, 16, 0, 6)
}
        batchGallery.addView(label)

        val xml = result.xml ?: return@forEach

        val image = ImageView(this).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            scaleType = ImageView.ScaleType.FIT_CENTER

            try {
                val bitmap = VectorPreviewRenderer.render(xml, 256, 256)
                setImageDrawable(BitmapDrawable(resources, bitmap))
            } catch (e: Exception) {
                setImageDrawable(null)
            }
        }

        batchGallery.addView(
            image,
            LinearLayout.LayoutParams(-1, 220)
        )
    }
}

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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

}


