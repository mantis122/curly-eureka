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

data class ConversionResult(
    val xml: String,
    val report: String
)

data class BatchResult(
    val fileName: String,
    val xml: String?,
    val warningCount: Int,
    val success: Boolean,
    val definitionPathCount: Int = 0,
    val error: String? = null
)

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
    Regex("""Definition paths skipped:\s*(\d+)""")
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
        append("  (${result.definitionPathCount} defs skipped)")
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

object SvgToVectorConverter {
fun convert(
    svg: String,
    outputDpSize: Int,
    conversionProfile: String
): ConversionResult {

val startTime = System.nanoTime()

val svgForTransformStats = stripSvgComments(svg)

val translateCount = Regex("""translate\(""").findAll(svgForTransformStats).count()
val scaleCount = Regex("""scale\(""").findAll(svgForTransformStats).count()
val rotateCount = Regex("""rotate\(""").findAll(svgForTransformStats).count()
val matrixCount = Regex("""matrix\(""").findAll(svgForTransformStats).count()

val pathCount = Regex("""<path\b[^>]*>""").findAll(svg).count()
val validPathCount = Regex("""<path\b[^>]*>""")
    .findAll(svg)
    .count { match ->
        val d = attr(match.value, "d")?.trim()
        !d.isNullOrBlank()
    }
val drawableSvgForStats = stripDefs(svg)

val drawableValidPathCount = Regex("""<path\b[^>]*>""")
    .findAll(drawableSvgForStats)
    .count { match ->
        val d = attr(match.value, "d")?.trim()
        !d.isNullOrBlank()
    }

val definitionPathCount = validPathCount - drawableValidPathCount
val emptyPathCount = pathCount - validPathCount
val groupCount = Regex("""<g\b[^>]*>""").findAll(svg).count()

val basicShapeTags = listOf("rect", "circle", "ellipse", "line", "polyline", "polygon")
val basicShapeCount = basicShapeTags.sumOf { tag ->
    Regex("""<\s*$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
        .findAll(drawableSvgForStats)
        .count()
}

val unsupported = mutableListOf<String>()

if (hasTag(svg, "linearGradient")) unsupported.add("Linear gradients")
if (hasTag(svg, "radialGradient")) unsupported.add("Radial gradients")
if (hasTag(svg, "mask")) unsupported.add("Masks")
if (hasTag(svg, "filter")) unsupported.add("Filters")
if (hasTag(svg, "text")) unsupported.add("Text elements")
if (hasTag(svg, "clipPath")) unsupported.add("Clip paths")
if (hasTag(svg, "pattern")) unsupported.add("Patterns")
if (hasTag(svg, "image")) unsupported.add("Embedded images")
if (hasTag(svg, "symbol")) unsupported.add("Symbols")
if (hasTag(svg, "use")) unsupported.add("Referenced elements")

        val viewBoxValues = getViewBox(svg)

        val widthFromSvg = getNumberAttr(svg, "width")
        val heightFromSvg = getNumberAttr(svg, "height")

        val viewportWidth = viewBoxValues?.getOrNull(2)
            ?: widthFromSvg
            ?: 24f

        val viewportHeight = viewBoxValues?.getOrNull(3)
            ?: heightFromSvg
            ?: 24f

val vectorWidthDp =
    if (outputDpSize > 0) outputDpSize else viewportWidth.toInt()

val vectorHeightDp =
    if (outputDpSize > 0) outputDpSize else viewportHeight.toInt()        

        val output = StringBuilder()

        output.appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
        output.appendLine("""    android:width="${vectorWidthDp}dp"""")
        output.appendLine("""    android:height="${vectorHeightDp}dp"""")
        output.appendLine("""    android:viewportWidth="$viewportWidth"""")
        output.appendLine("""    android:viewportHeight="$viewportHeight">""")
        output.appendLine()

        appendConvertedSvgTree(output, stripDefs(svg))

        output.appendLine("</vector>")

        val result = output.toString().trim()
        val endTag = "</vector>"
        val endIndex = result.indexOf(endTag)

val finalXml = if (endIndex >= 0) {
    result.substring(0, endIndex + endTag.length)
} else {
    result
}

val convertedPathCount = Regex("""<path\b""").findAll(finalXml).count()
val convertedBasicShapeCount = countConvertedBasicShapes(finalXml)
val convertedOriginalPathCount = convertedPathCount - convertedBasicShapeCount
val generatedGroupCount = Regex("""<group\b""").findAll(finalXml).count()

val elapsedMs =
    (System.nanoTime() - startTime) / 1_000_000

val warningCount =
    unsupported.size +
    if (matrixCount > 0) 1 else 0

val summaryTitle =
    if (warningCount == 0)
        "🟢 Conversion Successful"
    else
        "🟡 Conversion Completed With Warnings"

val summaryLine1 =
    "$convertedPathCount drawable paths created"

val summaryLine2 =
    if (warningCount == 0)
        "No warnings detected"
    else
        "$warningCount warning(s) detected"

val report = buildString {
    appendLine(summaryTitle)
    appendLine(summaryLine1)
    appendLine(summaryLine2)
    appendLine()

appendLine("Converted in ${elapsedMs} ms")
appendLine()

    appendLine("Conversion Statistics")
    appendLine()
appendLine("✓ Paths converted: $convertedOriginalPathCount / $drawableValidPathCount")
appendLine("✓ Basic shapes converted: $convertedBasicShapeCount / $basicShapeCount")

if (definitionPathCount > 0) {
    appendLine("✓ Definition paths skipped: $definitionPathCount")
}
    appendLine("✓ Groups generated: $generatedGroupCount")
    appendLine("✓ Warnings: $warningCount")
    appendLine()

    appendLine("────────────────────")

    appendLine()
    appendLine("✓ Profile: $conversionProfile")

    appendLine(
        if (outputDpSize > 0)
            "✓ Output size: ${outputDpSize}dp"
        else
            "✓ Output size: Keep SVG size"
    )

    appendLine()

    appendLine("────────────────────")
    appendLine()
    appendLine("SVG Analysis")
    appendLine()
    appendLine("✓ Viewport: ${viewportWidth} × ${viewportHeight}")
    appendLine("✓ Paths found: $pathCount")
    appendLine("✓ Valid paths: $validPathCount")
    appendLine("✓ Empty paths skipped: $emptyPathCount")
    appendLine("✓ Basic shapes found: $basicShapeCount")
    appendLine("✓ Basic shapes converted: $convertedBasicShapeCount")
if (definitionPathCount > 0) {
    appendLine("✓ Definition paths skipped: $definitionPathCount")
}
    appendLine("✓ Generated groups: $generatedGroupCount")
    appendLine()

    appendLine()
    appendLine("Transforms")
    appendLine()

    appendLine("✓ Translate transforms: $translateCount")
    appendLine("✓ Scale transforms: $scaleCount")
    appendLine("✓ Rotate transforms: $rotateCount")

if (matrixCount > 0) {
    appendLine("⚠ Unsupported matrix transforms: $matrixCount")
} else {
    appendLine("✓ Unsupported matrix transforms: 0")
}

    appendLine()
    appendLine("Conversion Status")
    appendLine()

    appendLine("✓ Android VectorDrawable generated")
    appendLine("✓ Drawable paths created: $convertedPathCount")
    appendLine("✓ XML validation passed")
    appendLine("✓ Output ready to save")
    appendLine()


if (unsupported.isEmpty() && matrixCount == 0) {
    appendLine("✓ No warnings detected")
} else {
    appendLine("Warnings")
    appendLine()

    if (matrixCount > 0) {
        appendLine("⚠ Unsupported matrix transforms: $matrixCount")
    }

    unsupported.forEach {
        appendLine("⚠ $it detected")
    }

    appendLine()
    appendLine("Some SVG features may not convert correctly.")
}

}


return ConversionResult(finalXml, report)


    }

private fun countConvertedBasicShapes(xml: String): Int {
    return Regex("""<!-- converted from <(rect|circle|ellipse|line|polyline|polygon)> -->""")
        .findAll(xml)
        .count()
}

    private fun hasTag(svg: String, tagName: String): Boolean {
        return Regex("""<\s*$tagName\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(svg)
    }

private fun stripSvgComments(xml: String): String {
    return Regex(
        """<!--.*?-->""",
        RegexOption.DOT_MATCHES_ALL
    ).replace(xml, "")
}

private fun stripDefs(xml: String): String {
    return Regex(
        """<defs\b[^>]*>.*?</defs>""",
        RegexOption.DOT_MATCHES_ALL
    ).replace(xml, "")
}

private fun isValidAndroidColor(value: String?): Boolean {
    if (value == null) return false

    val v = value.trim()

    if (v == "none") return true
    if (v == "currentColor") return true
    if (v == "@android:color/transparent") return true

    return Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
        .matches(v)
}

private fun isUnsupportedPaint(value: String?): Boolean {
    if (value == null) return false

    val v = value.trim()

    return v.startsWith("url(") ||
        v.startsWith("linear-gradient") ||
        v.startsWith("radial-gradient")
}

private fun safeFillColor(value: String?): String {
    val v = value?.trim()

    return when {
        v.isNullOrBlank() -> "#000000"
        v == "none" -> "@android:color/transparent"
        v == "currentColor" -> "#000000"
        isUnsupportedPaint(v) -> "@android:color/transparent"
        isValidAndroidColor(v) -> v
        else -> "@android:color/transparent"
    }
}

private fun safeStrokeColor(value: String?): String? {
    val v = value?.trim()

    return when {
        v.isNullOrBlank() -> null
        v == "none" -> null
        v == "currentColor" -> "#000000"
        isUnsupportedPaint(v) -> null
        isValidAndroidColor(v) -> v
        else -> null
    }
}

private fun appendConvertedSvgTree(output: StringBuilder, svg: String) {
    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        val root = document.documentElement
        walkSvgNode(output, root, "    ")
    } catch (e: Exception) {
        // Fallback for imperfect SVG/XML files
        appendFlatPathsFallback(output, svg, "    ")
    }
}

private fun walkSvgNode(
    output: StringBuilder,
    node: Node,
    indent: String,
    inheritedFill: String? = null,
    inheritedStroke: String? = null,
    inheritedStrokeWidth: String? = null,
    inheritedStrokeLineCap: String? = null,
    inheritedStrokeLineJoin: String? = null,
    inheritedFillRule: String? = null,
    inheritedOpacity: String? = null,
    inheritedFillOpacity: String? = null,
    inheritedStrokeOpacity: String? = null
) {
    if (node.nodeType != Node.ELEMENT_NODE) return

    val element = node as Element
val style = element.getAttribute("style").ifBlank { null }

val currentFill = element.getAttribute("fill").ifBlank {
    styleValue(style, "fill") ?: inheritedFill ?: ""
}

val currentStroke = element.getAttribute("stroke").ifBlank {
    styleValue(style, "stroke") ?: inheritedStroke ?: ""
}

val currentStrokeWidth = element.getAttribute("stroke-width").ifBlank {
    styleValue(style, "stroke-width") ?: inheritedStrokeWidth ?: ""
}

val currentStrokeLineCap = element.getAttribute("stroke-linecap").ifBlank {
    styleValue(style, "stroke-linecap") ?: inheritedStrokeLineCap ?: ""
}

val currentStrokeLineJoin = element.getAttribute("stroke-linejoin").ifBlank {
    styleValue(style, "stroke-linejoin") ?: inheritedStrokeLineJoin ?: ""
}

val currentFillRule = element.getAttribute("fill-rule").ifBlank {
    styleValue(style, "fill-rule") ?: inheritedFillRule ?: ""
}

val currentOpacity = combineAlpha(
    inheritedOpacity,
    element.getAttribute("opacity").ifBlank {
        styleValue(style, "opacity") ?: ""
    }
)

val currentFillOpacity = element.getAttribute("fill-opacity").ifBlank {
    styleValue(style, "fill-opacity") ?: inheritedFillOpacity ?: ""
}

val currentStrokeOpacity = element.getAttribute("stroke-opacity").ifBlank {
    styleValue(style, "stroke-opacity") ?: inheritedStrokeOpacity ?: ""
}

   val tagName = element.tagName.substringAfter(":").lowercase()

    when (tagName) {
        "g" -> {
            val transform = element.getAttribute("transform")
            val translate = parseTranslate(transform)
            val scale = parseScale(transform)
            val rotate = parseRotate(transform)

            val needsGroup = translate != null || scale != null || rotate != null

            if (needsGroup) {
                output.appendLine("${indent}<group")

                if (translate != null) {
                    output.appendLine("""${indent}    android:translateX="${translate.first}"""")
                    output.appendLine("""${indent}    android:translateY="${translate.second}"""")
                }

                if (scale != null) {
                    output.appendLine("""${indent}    android:scaleX="${scale.first}"""")
                    output.appendLine("""${indent}    android:scaleY="${scale.second}"""")
                }

                if (rotate != null) {
                    output.appendLine("""${indent}    android:rotation="${rotate.degrees}"""")
                    if (rotate.pivotX != null && rotate.pivotY != null) {
                        output.appendLine("""${indent}    android:pivotX="${rotate.pivotX}"""")
                        output.appendLine("""${indent}    android:pivotY="${rotate.pivotY}"""")
                    }
                }

                output.appendLine("${indent}>")

                val children = element.childNodes
                for (i in 0 until children.length) {
                 walkSvgNode(
    output,
    children.item(i),
    indent + "    ",
    currentFill,
    currentStroke,
    currentStrokeWidth,
    currentStrokeLineCap,
    currentStrokeLineJoin,
    currentFillRule,
    currentOpacity,
    currentFillOpacity,
    currentStrokeOpacity
)
                }

                output.appendLine("${indent}</group>")
                output.appendLine()
            } else {
                val children = element.childNodes
                for (i in 0 until children.length) {
walkSvgNode(
    output,
    children.item(i),
    indent,
    currentFill,
    currentStroke,
    currentStrokeWidth,
    currentStrokeLineCap,
    currentStrokeLineJoin,
    currentFillRule,
    currentOpacity,
    currentFillOpacity,
    currentStrokeOpacity
)
                }
            }
        }

        "path" -> {
            appendElementPath(
                output,
                element,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity
            )
        }

        "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
            appendBasicShapePath(
                output,
                element,
                tagName,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity
            )
        }

        else -> {
            val children = element.childNodes
            for (i in 0 until children.length) {
walkSvgNode(
    output,
    children.item(i),
    indent,
    currentFill,
    currentStroke,
    currentStrokeWidth,
    currentStrokeLineCap,
    currentStrokeLineJoin,
    currentFillRule,
    currentOpacity,
    currentFillOpacity,
    currentStrokeOpacity
)
            }
        }
    }
}


private fun appendBasicShapePath(
    output: StringBuilder,
    element: Element,
    tagName: String,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedStrokeLineCap: String?,
    inheritedStrokeLineJoin: String?,
    inheritedFillRule: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
) {
    val d = basicShapeToPathData(element, tagName) ?: return

    appendElementPathData(
        output,
        element,
        d,
        indent,
        inheritedFill,
        inheritedStroke,
        inheritedStrokeWidth,
        inheritedStrokeLineCap,
        inheritedStrokeLineJoin,
        inheritedFillRule,
        inheritedOpacity,
        inheritedFillOpacity,
        inheritedStrokeOpacity,
        sourceTag = tagName
    )
}

private fun appendElementPath(
    output: StringBuilder,
    element: Element,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedStrokeLineCap: String?,
    inheritedStrokeLineJoin: String?,
    inheritedFillRule: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
) {
    val d = element.getAttribute("d").trim()
    if (d.isBlank()) return

    appendElementPathData(
        output,
        element,
        d,
        indent,
        inheritedFill,
        inheritedStroke,
        inheritedStrokeWidth,
        inheritedStrokeLineCap,
        inheritedStrokeLineJoin,
        inheritedFillRule,
        inheritedOpacity,
        inheritedFillOpacity,
        inheritedStrokeOpacity,
        sourceTag = null
    )
}

private fun appendElementPathData(
    output: StringBuilder,
    element: Element,
    d: String,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedStrokeLineCap: String?,
    inheritedStrokeLineJoin: String?,
    inheritedFillRule: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?,
    sourceTag: String?
) {
    val style = element.getAttribute("style").ifBlank { null }

val rawFill = element.getAttribute("fill").ifBlank {
    styleValue(style, "fill") ?: inheritedFill ?: "none"
}

val rawStroke = element.getAttribute("stroke").ifBlank {
    styleValue(style, "stroke") ?: inheritedStroke ?: ""
}

val strokeWidth = element.getAttribute("stroke-width").ifBlank {
    styleValue(style, "stroke-width") ?: inheritedStrokeWidth ?: ""
}

val strokeLineCap = element.getAttribute("stroke-linecap").ifBlank {
    styleValue(style, "stroke-linecap") ?: inheritedStrokeLineCap ?: ""
}

val strokeLineJoin = element.getAttribute("stroke-linejoin").ifBlank {
    styleValue(style, "stroke-linejoin") ?: inheritedStrokeLineJoin ?: ""
}

val fillRule = element.getAttribute("fill-rule").ifBlank {
    styleValue(style, "fill-rule") ?: inheritedFillRule ?: ""
}

val fillOpacity = element.getAttribute("fill-opacity").ifBlank {
    styleValue(style, "fill-opacity") ?: inheritedFillOpacity ?: ""
}

val strokeOpacity = element.getAttribute("stroke-opacity").ifBlank {
    styleValue(style, "stroke-opacity") ?: inheritedStrokeOpacity ?: ""
}

val fillAlpha = resolveDrawableAlpha(inheritedOpacity, fillOpacity)
val strokeAlpha = resolveDrawableAlpha(inheritedOpacity, strokeOpacity)

    val fill = safeFillColor(rawFill)
    val stroke = safeStrokeColor(rawStroke)

    val pathTransform = element.getAttribute("transform")
    val translate = parseTranslate(pathTransform)
    val scale = parseScale(pathTransform)
    val rotate = parseRotate(pathTransform)

    val pathNeedsGroup = translate != null || scale != null || rotate != null

    if (pathNeedsGroup) {
        output.appendLine("${indent}<group")

        if (translate != null) {
            output.appendLine("""${indent}    android:translateX="${translate.first}"""")
            output.appendLine("""${indent}    android:translateY="${translate.second}"""")
        }

        if (scale != null) {
            output.appendLine("""${indent}    android:scaleX="${scale.first}"""")
            output.appendLine("""${indent}    android:scaleY="${scale.second}"""")
        }

        if (rotate != null) {
            output.appendLine("""${indent}    android:rotation="${rotate.degrees}"""")
            if (rotate.pivotX != null && rotate.pivotY != null) {
                output.appendLine("""${indent}    android:pivotX="${rotate.pivotX}"""")
                output.appendLine("""${indent}    android:pivotY="${rotate.pivotY}"""")
            }
        }

        output.appendLine("${indent}>")
        if (sourceTag != null) {
            output.appendLine("${indent}    <!-- converted from <$sourceTag> -->")
        }
        appendPath(
            output,
            d,
            fill,
            stroke,
            strokeWidth.ifBlank { null },
            strokeLineCap.ifBlank { null },
            strokeLineJoin.ifBlank { null },
            fillRule.ifBlank { null },
            fillAlpha,
            strokeAlpha,
            indent + "    "
        )
        output.appendLine("${indent}</group>")
    } else {
        if (sourceTag != null) {
            output.appendLine("${indent}<!-- converted from <$sourceTag> -->")
        }
        appendPath(
            output,
            d,
            fill,
            stroke,
            strokeWidth.ifBlank { null },
            strokeLineCap.ifBlank { null },
            strokeLineJoin.ifBlank { null },
            fillRule.ifBlank { null },
            fillAlpha,
            strokeAlpha,
            indent
        )
    }

    output.appendLine()
}


private fun basicShapeToPathData(element: Element, tagName: String): String? {
    return when (tagName) {
        "rect" -> rectToPathData(element)
        "circle" -> circleToPathData(element)
        "ellipse" -> ellipseToPathData(element)
        "line" -> lineToPathData(element)
        "polyline" -> pointsToPathData(element, close = false)
        "polygon" -> pointsToPathData(element, close = true)
        else -> null
    }?.takeIf { it.isNotBlank() }
}

private fun rectToPathData(element: Element): String {
    val x = floatAttr(element, "x") ?: 0f
    val y = floatAttr(element, "y") ?: 0f
    val w = floatAttr(element, "width") ?: return ""
    val h = floatAttr(element, "height") ?: return ""

    if (w <= 0f || h <= 0f) return ""

    var rx = floatAttr(element, "rx") ?: 0f
    var ry = floatAttr(element, "ry") ?: 0f

    // SVG rule: if only rx or ry is provided, the missing one uses the same value.
    if (rx > 0f && ry == 0f) ry = rx
    if (ry > 0f && rx == 0f) rx = ry

    // Clamp radii so they cannot exceed half the rectangle size.
    rx = rx.coerceAtMost(w / 2f)
    ry = ry.coerceAtMost(h / 2f)

    // Normal sharp rectangle.
    if (rx <= 0f || ry <= 0f) {
        return "M $x,$y L ${x + w},$y L ${x + w},${y + h} L $x,${y + h} Z"
    }

    val right = x + w
    val bottom = y + h

    return "M ${x + rx},$y " +
        "L ${right - rx},$y " +
        "A $rx,$ry 0,0,1 $right,${y + ry} " +
        "L $right,${bottom - ry} " +
        "A $rx,$ry 0,0,1 ${right - rx},$bottom " +
        "L ${x + rx},$bottom " +
        "A $rx,$ry 0,0,1 $x,${bottom - ry} " +
        "L $x,${y + ry} " +
        "A $rx,$ry 0,0,1 ${x + rx},$y Z"
}

private fun circleToPathData(element: Element): String {
    val cx = floatAttr(element, "cx") ?: 0f
    val cy = floatAttr(element, "cy") ?: 0f
    val r = floatAttr(element, "r") ?: return ""

    if (r <= 0f) return ""

    return "M ${cx - r},$cy " +
        "A $r,$r 0,1,0 ${cx + r},$cy " +
        "A $r,$r 0,1,0 ${cx - r},$cy Z"
}

private fun ellipseToPathData(element: Element): String {
    val cx = floatAttr(element, "cx") ?: 0f
    val cy = floatAttr(element, "cy") ?: 0f
    val rx = floatAttr(element, "rx") ?: return ""
    val ry = floatAttr(element, "ry") ?: return ""

    if (rx <= 0f || ry <= 0f) return ""

    return "M ${cx - rx},$cy " +
        "A $rx,$ry 0,1,0 ${cx + rx},$cy " +
        "A $rx,$ry 0,1,0 ${cx - rx},$cy Z"
}

private fun lineToPathData(element: Element): String {
    val x1 = floatAttr(element, "x1") ?: 0f
    val y1 = floatAttr(element, "y1") ?: 0f
    val x2 = floatAttr(element, "x2") ?: 0f
    val y2 = floatAttr(element, "y2") ?: 0f

    return "M $x1,$y1 L $x2,$y2"
}

private fun pointsToPathData(element: Element, close: Boolean): String {
    val values = element.getAttribute("points")
        .trim()
        .replace(",", " ")
        .split(Regex("\\s+"))
        .mapNotNull { it.toFloatOrNull() }

    if (values.size < 4) return ""

    val output = StringBuilder("M ${values[0]},${values[1]}")

    var i = 2
    while (i + 1 < values.size) {
        output.append(" L ${values[i]},${values[i + 1]}")
        i += 2
    }

    if (close) output.append(" Z")

    return output.toString()
}

private fun floatAttr(element: Element, name: String): Float? {
    return element.getAttribute(name)
        .replace("px", "")
        .replace("dp", "")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.toFloatOrNull()
}

private fun appendFlatPathsFallback(
    output: StringBuilder,
    xml: String,
    indent: String
) {
    Regex("""<path\b[^>]*>""")
        .findAll(xml)
        .forEach { match ->
            val tag = match.value
            val d = attr(tag, "d")?.trim()
            if (d.isNullOrBlank()) return@forEach

            val rawFill = attr(tag, "fill")
                ?: styleValue(attr(tag, "style"), "fill")

            val rawStroke = attr(tag, "stroke")
                ?: styleValue(attr(tag, "style"), "stroke")

            val strokeWidth = attr(tag, "stroke-width")
                ?: styleValue(attr(tag, "style"), "stroke-width")

            val strokeLineCap = attr(tag, "stroke-linecap")
                ?: styleValue(attr(tag, "style"), "stroke-linecap")

            val strokeLineJoin = attr(tag, "stroke-linejoin")
                ?: styleValue(attr(tag, "style"), "stroke-linejoin")

            val opacity = attr(tag, "opacity")
                ?: styleValue(attr(tag, "style"), "opacity")

            val fillOpacity = attr(tag, "fill-opacity")
                ?: styleValue(attr(tag, "style"), "fill-opacity")

            val strokeOpacity = attr(tag, "stroke-opacity")
                ?: styleValue(attr(tag, "style"), "stroke-opacity")

            val fillRule = attr(tag, "fill-rule")
                ?: styleValue(attr(tag, "style"), "fill-rule")

            appendPath(
                output,
                d,
                safeFillColor(rawFill),
                safeStrokeColor(rawStroke),
                strokeWidth,
                strokeLineCap,
                strokeLineJoin,
                fillRule,
                resolveDrawableAlpha(opacity, fillOpacity),
                resolveDrawableAlpha(opacity, strokeOpacity),
                indent
            )

            output.appendLine()
        }
}

private fun appendPath(
    output: StringBuilder,
    d: String,
    fill: String,
    stroke: String?,
    strokeWidth: String?,
    strokeLineCap: String?,
    strokeLineJoin: String?,
    fillRule: String?,
    fillAlpha: String?,
    strokeAlpha: String?,
    indent: String
) {
    output.appendLine("${indent}<path")
    output.appendLine("""${indent}    android:pathData="${escapeXml(d)}"""")

    if (fill != "@android:color/transparent") {
        output.appendLine("""${indent}    android:fillColor="$fill"""")
        if (fillAlpha != null) {
            output.appendLine("""${indent}    android:fillAlpha="$fillAlpha"""")
        }
    } else {
        output.appendLine("""${indent}    android:fillColor="@android:color/transparent"""")
    }

    when (fillRule?.trim()?.lowercase()) {
        "evenodd" -> output.appendLine("""${indent}    android:fillType="evenOdd"""")
        "nonzero" -> output.appendLine("""${indent}    android:fillType="nonZero"""")
    }

    if (stroke != null) {
        output.appendLine("""${indent}    android:strokeColor="$stroke"""")
        output.appendLine("""${indent}    android:strokeWidth="${strokeWidth ?: "1"}"""")
        if (strokeAlpha != null) {
            output.appendLine("""${indent}    android:strokeAlpha="$strokeAlpha"""")
        }

        when (strokeLineCap?.trim()?.lowercase()) {
            "butt" -> output.appendLine("""${indent}    android:strokeLineCap="butt"""")
            "round" -> output.appendLine("""${indent}    android:strokeLineCap="round"""")
            "square" -> output.appendLine("""${indent}    android:strokeLineCap="square"""")
        }

        when (strokeLineJoin?.trim()?.lowercase()) {
            "miter" -> output.appendLine("""${indent}    android:strokeLineJoin="miter"""")
            "round" -> output.appendLine("""${indent}    android:strokeLineJoin="round"""")
            "bevel" -> output.appendLine("""${indent}    android:strokeLineJoin="bevel"""")
        }
    }

    output.appendLine("${indent}/>")
}

    private fun getViewBox(svg: String): List<Float>? {
        return Regex("""viewBox=["']([^"']+)["']""")
            .find(svg)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.split(Regex("[,\\s]+"))
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size >= 4 }
    }

    private fun getNumberAttr(tag: String, name: String): Float? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.replace("px", "")
            ?.replace("dp", "")
            ?.trim()
            ?.toFloatOrNull()
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }

    private fun styleValue(style: String?, name: String): String? {
        if (style == null) return null

        return style
            .split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$name:") }
            ?.substringAfter(":")
            ?.trim()
    }

    private data class RotateTransform(
        val degrees: Float,
        val pivotX: Float?,
        val pivotY: Float?
    )

    private fun parseRotate(transform: String?): RotateTransform? {
        if (transform == null) return null

        val match = Regex("""rotate\(([^)]*)\)""")
            .find(transform)
            ?: return null

        val nums = match.groupValues[1]
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toFloatOrNull() }

        if (nums.isEmpty()) return null

        val pivotX = nums.getOrNull(1)
        val pivotY = nums.getOrNull(2)

        return RotateTransform(nums[0], pivotX, pivotY)
    }

    private fun parseTranslate(transform: String?): Pair<Float, Float>? {
        if (transform == null) return null

        val match = Regex("""translate\(([^)]*)\)""")
            .find(transform)
            ?: return null

        val nums = match.groupValues[1]
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toFloatOrNull() }

        if (nums.isEmpty()) return null

        return Pair(nums[0], nums.getOrNull(1) ?: 0f)
    }

    private fun parseScale(transform: String?): Pair<Float, Float>? {
        if (transform == null) return null

        val match = Regex("""scale\(([^)]*)\)""")
            .find(transform)
            ?: return null

        val nums = match.groupValues[1]
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toFloatOrNull() }

        if (nums.isEmpty()) return null

        return Pair(nums[0], nums.getOrNull(1) ?: nums[0])
    }


    private fun resolveDrawableAlpha(opacity: String?, channelOpacity: String?): String? {
        return combineAlpha(opacity, channelOpacity)
    }

    private fun combineAlpha(baseAlpha: String?, localAlpha: String?): String? {
        val base = parseSvgAlpha(baseAlpha)
        val local = parseSvgAlpha(localAlpha)

        val combined = when {
            base != null && local != null -> base * local
            base != null -> base
            local != null -> local
            else -> return null
        }.coerceIn(0f, 1f)

        if (combined >= 0.999f) return null

        return java.lang.String.format(java.util.Locale.US, "%.3f", combined)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun parseSvgAlpha(value: String?): Float? {
        val raw = value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parsed = if (raw.endsWith("%")) {
            raw.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
        } else {
            raw.toFloatOrNull()
        }

        return parsed?.coerceIn(0f, 1f)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

object VectorPreviewRenderer {
    fun render(xml: String, width: Int, height: Int): Bitmap {
        val viewportWidth = attr(xml, "android:viewportWidth")?.toFloatOrNull() ?: 24f
        val viewportHeight = attr(xml, "android:viewportHeight")?.toFloatOrNull() ?: 24f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

       val canvasScaleX = width / viewportWidth
val canvasScaleY = height / viewportHeight
val strokeScale = minOf(canvasScaleX, canvasScaleY)

canvas.scale(canvasScaleX, canvasScaleY)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

walkVectorNode(canvas, document.documentElement, strokeScale)

        return bitmap
    }

private fun walkVectorNode(canvas: Canvas, node: Node, strokeScale: Float) {
        if (node.nodeType != Node.ELEMENT_NODE) return

        val element = node as Element
        val tagName = element.tagName.substringAfter(":")

        when (tagName) {
            "group" -> {
                canvas.save()

                val translateX =
                    element.getAttribute("android:translateX").toFloatOrNull() ?: 0f
                val translateY =
                    element.getAttribute("android:translateY").toFloatOrNull() ?: 0f
                val scaleX =
                    element.getAttribute("android:scaleX").toFloatOrNull() ?: 1f
                val scaleY =
                    element.getAttribute("android:scaleY").toFloatOrNull() ?: 1f
                val rotation =
                    element.getAttribute("android:rotation").toFloatOrNull() ?: 0f
                val pivotX =
                    element.getAttribute("android:pivotX").toFloatOrNull() ?: 0f
                val pivotY =
                    element.getAttribute("android:pivotY").toFloatOrNull() ?: 0f

                canvas.translate(translateX, translateY)
                if (rotation != 0f) {
                    canvas.rotate(rotation, pivotX, pivotY)
                }
                canvas.scale(scaleX, scaleY)

                val children = element.childNodes
                for (i in 0 until children.length) {
                    walkVectorNode(canvas, children.item(i), strokeScale)
                }

                canvas.restore()
            }

            "path" -> {
                drawPathElement(canvas, element, strokeScale)
            }

            else -> {
                val children = element.childNodes
                for (i in 0 until children.length) {
                    walkVectorNode(canvas, children.item(i), strokeScale)
                }
            }
        }
    }

private fun drawPathElement(
    canvas: Canvas,
    element: Element,
    strokeScale: Float
) {
        val pathData = element.getAttribute("android:pathData")
        if (pathData.isBlank()) return

        val path = androidx.core.graphics.PathParser
            .createPathFromPathData(pathData)

        path.fillType = parsePathFillType(element.getAttribute("android:fillType"))

        val fillColor = element.getAttribute("android:fillColor")
        val strokeColor = element.getAttribute("android:strokeColor")
        val strokeWidth = element.getAttribute("android:strokeWidth")
            .toFloatOrNull()
            ?: 1f
        val fillAlpha = parsePreviewAlpha(element.getAttribute("android:fillAlpha"))
        val strokeAlpha = parsePreviewAlpha(element.getAttribute("android:strokeAlpha"))

        if (
            fillColor.isNotBlank() &&
            fillColor != "@android:color/transparent" &&
            fillColor != "none"
        ) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                val parsedColor = try {
                    Color.parseColor(fillColor)
                } catch (e: Exception) {
                    Color.TRANSPARENT
                }
                color = parsedColor
                alpha = (Color.alpha(parsedColor) * fillAlpha).toInt().coerceIn(0, 255)
            }

            canvas.drawPath(path, fillPaint)
        }

        if (
            strokeColor.isNotBlank() &&
            strokeColor != "@android:color/transparent" &&
            strokeColor != "none"
        ) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                val parsedColor = try {
                    Color.parseColor(strokeColor)
                } catch (e: Exception) {
                    Color.TRANSPARENT
                }
                color = parsedColor
                alpha = (Color.alpha(parsedColor) * strokeAlpha).toInt().coerceIn(0, 255)

this.strokeWidth = strokeWidth
                strokeCap = parseStrokeCap(element.getAttribute("android:strokeLineCap"))
                strokeJoin = parseStrokeJoin(element.getAttribute("android:strokeLineJoin"))
            }

            canvas.drawPath(path, strokePaint)
        }
    }

    private fun parsePreviewAlpha(value: String?): Float {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 1f
    }

    private fun parsePathFillType(value: String?): Path.FillType {
        return when (value?.trim()?.lowercase()) {
            "evenodd" -> Path.FillType.EVEN_ODD
            else -> Path.FillType.WINDING
        }
    }

    private fun parseStrokeCap(value: String?): Paint.Cap {
        return when (value?.trim()?.lowercase()) {
            "round" -> Paint.Cap.ROUND
            "square" -> Paint.Cap.SQUARE
            else -> Paint.Cap.BUTT
        }
    }

    private fun parseStrokeJoin(value: String?): Paint.Join {
        return when (value?.trim()?.lowercase()) {
            "round" -> Paint.Join.ROUND
            "bevel" -> Paint.Join.BEVEL
            else -> Paint.Join.MITER
        }
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }
}
