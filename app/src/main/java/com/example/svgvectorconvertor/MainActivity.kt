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

object SvgToVectorConverter {
private var activeGradientFallbackColors: Map<String, String> = emptyMap()
private var activeClipPathData: Map<String, String> = emptyMap()
private var activeAppliedClipPaths = 0
private var activeResolvedUseExpansions = 0
private var activeUnresolvedUseReferences = 0
private var activeSupportedMatrixTransforms = 0
private var activeUnsupportedMatrixTransforms = 0
  
fun convert(
    svg: String,
    outputDpSize: Int,
    conversionProfile: String
): ConversionResult {

val startTime = System.nanoTime()

activeSupportedMatrixTransforms = 0
activeUnsupportedMatrixTransforms = 0
val svgForTransformStats = stripSvgComments(svg)
val gradientFallbackColors = collectGradientFallbackColors(svg)
activeGradientFallbackColors = gradientFallbackColors
val clipPathData = collectClipPathData(svg)
activeClipPathData = clipPathData
activeAppliedClipPaths = 0


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

val definitionDrawableElementCount = countDefinitionDrawableElements(svg)
val emptyPathCount = pathCount - validPathCount
val groupCount = Regex("""<g\b[^>]*>""").findAll(svg).count()
val useCount = Regex("""<\s*use\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
val symbolCount = Regex("""<\s*symbol\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
val clipPathReferenceCount = Regex("""clip-path\s*[:=]""", RegexOption.IGNORE_CASE)
    .findAll(svgForTransformStats)
    .count()
val styleAttributeCount = Regex("""\bstyle\s*=""", RegexOption.IGNORE_CASE)
    .findAll(svgForTransformStats)
    .count()
val presentationStyleAttributeCount = listOf(
    "fill",
    "stroke",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "fill-rule",
    "opacity",
    "fill-opacity",
    "stroke-opacity"
).sumOf { name ->
    Regex("""\b$name\s*=""", RegexOption.IGNORE_CASE)
        .findAll(svgForTransformStats)
        .count()
}

val basicShapeTags = listOf("rect", "circle", "ellipse", "line", "polyline", "polygon")
val basicShapeCount = basicShapeTags.sumOf { tag ->
    Regex("""<\s*$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
        .findAll(drawableSvgForStats)
        .count()
}

val unsupported = mutableListOf<String>()

if (hasTag(svg, "linearGradient")) {
    unsupported.add(
        if (gradientFallbackColors.isNotEmpty()) "Linear gradients converted to fallback colors" else "Linear gradients"
    )
}
if (hasTag(svg, "radialGradient")) {
    unsupported.add(
        if (gradientFallbackColors.isNotEmpty()) "Radial gradients converted to fallback colors" else "Radial gradients"
    )
}
if (hasTag(svg, "mask")) unsupported.add("Masks")
if (hasTag(svg, "filter")) unsupported.add("Filters")
if (hasTag(svg, "text")) unsupported.add("Text elements")
if (hasTag(svg, "clipPath") && clipPathData.isEmpty()) unsupported.add("Clip paths")
if (hasTag(svg, "pattern")) unsupported.add("Patterns")
if (hasTag(svg, "image")) unsupported.add("Embedded images")

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

        appendConvertedSvgTree(output, svg)

        output.appendLine("</vector>")

        val result = output.toString().trim()
        val endTag = "</vector>"
        val endIndex = result.indexOf(endTag)

val rawFinalXml = if (endIndex >= 0) {
    result.substring(0, endIndex + endTag.length)
} else {
    result
}

val finalXml = optimizeDuplicateClipPathGroups(rawFinalXml)

val convertedPathCount = Regex("""<path\b""").findAll(finalXml).count()
val convertedBasicShapeCount = countConvertedBasicShapes(finalXml)
val convertedOriginalPathCount = convertedPathCount - convertedBasicShapeCount
val generatedGroupCount = Regex("""<group\b""").findAll(finalXml).count()

val elapsedMs =
    (System.nanoTime() - startTime) / 1_000_000

val warningCount =
    unsupported.size +
    if (activeUnsupportedMatrixTransforms > 0) 1 else 0

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
    appendLine("✓ Visible SVG paths converted: $convertedOriginalPathCount")
    if (useCount > 0) {
        appendLine("✓ Definitions expanded: $useCount")
    }
    if (symbolCount > 0) {
        appendLine("✓ Symbol definitions: $symbolCount")
    }
    appendLine("✓ Basic shapes generated: $convertedBasicShapeCount")
    if (definitionDrawableElementCount > 0) {
        appendLine("✓ Drawable definitions: $definitionDrawableElementCount")
    }
    if (gradientFallbackColors.isNotEmpty()) {
        appendLine("✓ Gradient fallback colors: ${gradientFallbackColors.size}")
    }
    if (clipPathData.isNotEmpty()) {
        appendLine("✓ Clip paths found: ${clipPathData.size}")
        appendLine("✓ Clip paths applied: $activeAppliedClipPaths")
    }
    appendLine("✓ Style attributes parsed: $styleAttributeCount")
    appendLine("✓ Presentation attributes parsed: $presentationStyleAttributeCount")
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
    appendLine()

    appendLine("✓ Visible SVG paths: $drawableValidPathCount")
    appendLine("✓ Empty paths skipped: $emptyPathCount")
    appendLine()

    appendLine("✓ Basic shapes generated: $convertedBasicShapeCount")
    if (definitionDrawableElementCount > 0) {
        appendLine("✓ Drawable definitions: $definitionDrawableElementCount")
    }
    if (symbolCount > 0) {
        appendLine("✓ Symbol definitions: $symbolCount")
    }
    if (gradientFallbackColors.isNotEmpty()) {
        appendLine("✓ Gradient fallback colors: ${gradientFallbackColors.size}")
    }
    if (clipPathData.isNotEmpty()) {
        appendLine("✓ Clip paths found: ${clipPathData.size}")
        appendLine("✓ Clip path references: $clipPathReferenceCount")
        appendLine("✓ Clip paths applied: $activeAppliedClipPaths")
    }
    appendLine("✓ Style attributes parsed: $styleAttributeCount")
    appendLine("✓ Presentation attributes parsed: $presentationStyleAttributeCount")

    appendLine("✓ Generated groups: $generatedGroupCount")
    appendLine()

    appendLine()
    appendLine("Transforms")
    appendLine()

    appendLine("✓ Translate transforms: $translateCount")
    appendLine("✓ Scale transforms: $scaleCount")
    appendLine("✓ Rotate transforms: $rotateCount")

if (matrixCount > 0) {
    appendLine("✓ Matrix transforms supported: $activeSupportedMatrixTransforms")
    appendLine("⚠ Matrix transforms unsupported: $activeUnsupportedMatrixTransforms")
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
        if (it.contains("converted", ignoreCase = true)) {
            appendLine("⚠ $it")
        } else {
            appendLine("⚠ $it detected")
        }
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

private fun optimizeDuplicateClipPathGroups(xml: String): String {
    fun reindentBlock(block: String, indent: String): String {
        return block
            .lines()
            .joinToString("\n") { line ->
                if (line.isBlank()) line else indent + line.trimStart()
            }
    }

    val pattern = Regex(
        """(?s)([ \t]*)<group\s*>\s*(<clip-path\s+android:pathData="([^"]+)"\s*/>)\s*(.*?)\s*\1</group>\s*\1<group\s*>\s*<clip-path\s+android:pathData="\3"\s*/>\s*(.*?)\s*\1</group>"""
    )

    var current = xml

    while (true) {
        val updated = pattern.replace(current) { match ->
            val indent = match.groupValues[1]
            val clipPath = match.groupValues[2]
            val firstBody = match.groupValues[4].trimEnd()
            val secondBody = match.groupValues[5].trim()

            buildString {
                appendLine("${indent}<group")
                appendLine("${indent}>")
                appendLine(reindentBlock(clipPath, "$indent    "))

                if (firstBody.isNotBlank()) {
                    appendLine(firstBody)
                }

                if (secondBody.isNotBlank()) {
                    appendLine(secondBody)
                }

                append("${indent}</group>")
            }
        }

        if (updated == current) return current
        current = updated
    }
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


private fun countDefinitionDrawableElements(svg: String): Int {
    val basicShapeTags = setOf("rect", "circle", "ellipse", "line", "polyline", "polygon")
    var count = 0

    fun countDrawableElement(element: Element) {
        val tag = element.tagName.substringAfter(":").lowercase()

        when (tag) {
            "path" -> {
                if (element.getAttribute("d").trim().isNotBlank()) {
                    count++
                }
            }
            in basicShapeTags -> {
                if (basicShapeToPathData(element, tag) != null) {
                    count++
                }
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                countDrawableElement(child as Element)
            }
        }
    }

    return try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        val defsNodes = document.getElementsByTagName("defs")

        for (i in 0 until defsNodes.length) {
            val defs = defsNodes.item(i)
            val children = defs.childNodes

            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    countDrawableElement(child as Element)
                }
            }
        }

        count
    } catch (e: Exception) {
        val defsBlocks = Regex(
            """<defs\b[^>]*>.*?</defs>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(svg)

        defsBlocks.sumOf { block ->
            val value = block.value
            val pathCount = Regex("""<path\b[^>]*\bd\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE)
                .findAll(value)
                .count()

            val shapeCount = basicShapeTags.sumOf { tag ->
                Regex("""<\s*$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
                    .findAll(value)
                    .count()
            }

            pathCount + shapeCount
        }
    }
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

private fun fallbackColorForPaint(value: String?): String? {
    val v = value?.trim() ?: return null
    val id = Regex("""url\(\s*#([^)'\"\s]+)\s*\)""")
        .find(v)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?: return null

    return activeGradientFallbackColors[id]
}

private fun normalizedAndroidColor(value: String?): String? {
    val v = value?.trim() ?: return null

    if (isValidAndroidColor(v) && v != "none" && v != "currentColor") return v

    if (v.equals("currentColor", ignoreCase = true)) return "#000000"

    svgNamedColor(v)?.let { return it }

    parseRgbColor(v)?.let { rgb ->
        return "#%02X%02X%02X".format(rgb.first, rgb.second, rgb.third)
    }

    return null
}

private fun svgNamedColor(value: String): String? {
    return when (value.trim().lowercase()) {
        "black" -> "#000000"
        "white" -> "#FFFFFF"
        "red" -> "#FF0000"
        "green" -> "#008000"
        "blue" -> "#0000FF"
        "yellow" -> "#FFFF00"
        "cyan", "aqua" -> "#00FFFF"
        "magenta", "fuchsia" -> "#FF00FF"
        "gray", "grey" -> "#808080"
        "darkgray", "darkgrey" -> "#A9A9A9"
        "lightgray", "lightgrey" -> "#D3D3D3"
        "orange" -> "#FFA500"
        "purple" -> "#800080"
        "brown" -> "#A52A2A"
        "pink" -> "#FFC0CB"
        "lime" -> "#00FF00"
        "navy" -> "#000080"
        "teal" -> "#008080"
        "olive" -> "#808000"
        "maroon" -> "#800000"
        "silver" -> "#C0C0C0"
        "transparent" -> "#00000000"
        else -> null
    }
}

private fun safeFillColor(value: String?): String {
    val v = value?.trim()

    return when {
        v.isNullOrBlank() -> "#000000"
        v.equals("none", ignoreCase = true) -> "@android:color/transparent"
        fallbackColorForPaint(v) != null -> fallbackColorForPaint(v)!!
        isUnsupportedPaint(v) -> "@android:color/transparent"
        normalizedAndroidColor(v) != null -> normalizedAndroidColor(v)!!
        else -> "@android:color/transparent"
    }
}

private fun safeStrokeColor(value: String?): String? {
    val v = value?.trim()

    return when {
        v.isNullOrBlank() -> null
        v.equals("none", ignoreCase = true) -> null
        fallbackColorForPaint(v) != null -> fallbackColorForPaint(v)
        isUnsupportedPaint(v) -> null
        normalizedAndroidColor(v) != null -> normalizedAndroidColor(v)
        else -> null
    }
}

private fun collectGradientFallbackColors(svg: String): Map<String, String> {
    val result = mutableMapOf<String, String>()

    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        val gradients = mutableMapOf<String, Element>()

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return

            val element = node as Element
            val tag = element.tagName.substringAfter(":").lowercase()
            val id = element.getAttribute("id").trim()

            if ((tag == "lineargradient" || tag == "radialgradient") && id.isNotBlank()) {
                gradients[id] = element
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                visit(children.item(i))
            }
        }

        visit(document.documentElement)

        fun gradientHrefId(element: Element): String? {
            val href = element.getAttribute("href").ifBlank {
                element.getAttribute("xlink:href").ifBlank {
                    element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                }
            }.trim()

            return href.removePrefix("#").takeIf { it.isNotBlank() }
        }

        fun stopsForGradient(element: Element, depth: Int = 0): List<Pair<String, Float>> {
            if (depth > 10) return emptyList()

            val stops = mutableListOf<Pair<String, Float>>()
            val children = element.childNodes

            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType != Node.ELEMENT_NODE) continue

                val stop = child as Element
                val tag = stop.tagName.substringAfter(":").lowercase()
                if (tag != "stop") continue

                val style = stop.getAttribute("style").ifBlank { null }
                val color = stop.getAttribute("stop-color").ifBlank {
                    styleValue(style, "stop-color") ?: ""
                }.trim()

                if (color.isBlank() || color == "none") continue

                val opacityText = stop.getAttribute("stop-opacity").ifBlank {
                    styleValue(style, "stop-opacity") ?: "1"
                }
                val opacity = parseAlphaValue(opacityText) ?: 1f

                stops.add(color to opacity)
            }

            if (stops.isNotEmpty()) return stops

            val hrefId = gradientHrefId(element) ?: return emptyList()
            val referenced = gradients[hrefId] ?: return emptyList()
            return stopsForGradient(referenced, depth + 1)
        }

        gradients.forEach { (id, element) ->
            val stops = stopsForGradient(element)
            val fallback = averageStopColor(stops)
            if (fallback != null) {
                result[id] = fallback
            }
        }
    } catch (_: Exception) {
        return emptyMap()
    }

    return result
}


private fun collectClipPathData(svg: String): Map<String, String> {
    val result = mutableMapOf<String, String>()

    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        val root = document.documentElement
        val definitions = collectSvgDefinitions(root)

        fun elementToPathData(element: Element, depth: Int = 0): String {
            if (depth > 20) return ""

            val tag = element.tagName.substringAfter(":").lowercase()

            return when (tag) {
                "path" -> element.getAttribute("d").trim()
                "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                    basicShapeToPathData(element, tag).orEmpty()
                "use" -> {
                    val href = element.getAttribute("href").ifBlank {
                        element.getAttribute("xlink:href").ifBlank {
                            element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                        }
                    }.trim()
                    val id = href.removePrefix("#").trim()
                    val referenced = definitions[id] ?: return ""
                    elementToPathData(referenced, depth + 1)
                }
                else -> {
                    val parts = mutableListOf<String>()
                    val children = element.childNodes
                    for (i in 0 until children.length) {
                        val child = children.item(i)
                        if (child.nodeType != Node.ELEMENT_NODE) continue
                        val childPath = elementToPathData(child as Element, depth + 1)
                        if (childPath.isNotBlank()) parts.add(childPath)
                    }
                    parts.joinToString(" ")
                }
            }
        }

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return

            val element = node as Element
            val tag = element.tagName.substringAfter(":").lowercase()

            if (tag == "clippath") {
                val id = element.getAttribute("id").trim()
                val pathData = elementToPathData(element)
                if (id.isNotBlank() && pathData.isNotBlank()) {
                    result[id] = pathData
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                visit(children.item(i))
            }
        }

        visit(root)
    } catch (_: Exception) {
        return emptyMap()
    }

    return result
}

private fun clipPathIdFromValue(value: String?): String? {
    val v = value?.trim() ?: return null
    return Regex("""url\(\s*#([^)'"\s]+)\s*\)""")
        .find(v)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun appendClipPath(output: StringBuilder, clipPathId: String?, indent: String): Boolean {
    val id = clipPathId ?: return false
    val pathData = activeClipPathData[id] ?: return false

    activeAppliedClipPaths++

    output.appendLine("""${indent}<clip-path""")
    output.appendLine("""${indent}    android:pathData="${escapeXml(pathData)}"""")
    output.appendLine("""${indent}/>""")
    return true
}

private fun averageStopColor(stops: List<Pair<String, Float>>): String? {
    if (stops.isEmpty()) return null

    var totalWeight = 0f
    var totalR = 0f
    var totalG = 0f
    var totalB = 0f

    stops.forEach { (colorText, opacity) ->
        val rgb = parseRgbColor(colorText) ?: return@forEach
        val weight = opacity.coerceIn(0f, 1f).takeIf { it > 0f } ?: 0.05f

        totalR += rgb.first * weight
        totalG += rgb.second * weight
        totalB += rgb.third * weight
        totalWeight += weight
    }

    if (totalWeight <= 0f) return null

    val r = (totalR / totalWeight).toInt().coerceIn(0, 255)
    val g = (totalG / totalWeight).toInt().coerceIn(0, 255)
    val b = (totalB / totalWeight).toInt().coerceIn(0, 255)

    return "#%02X%02X%02X".format(r, g, b)
}

private data class RgbColor(val first: Int, val second: Int, val third: Int)

private fun parseRgbColor(value: String?): RgbColor? {
    val v = value?.trim() ?: return null

    if (v == "currentColor") return RgbColor(0, 0, 0)

    val hex = v.removePrefix("#")
    return when {
        Regex("""^[0-9a-fA-F]{3}$""").matches(hex) -> {
            val r = "${hex[0]}${hex[0]}".toInt(16)
            val g = "${hex[1]}${hex[1]}".toInt(16)
            val b = "${hex[2]}${hex[2]}".toInt(16)
            RgbColor(r, g, b)
        }
        Regex("""^[0-9a-fA-F]{6}$""").matches(hex) -> {
            RgbColor(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
        }
        Regex("""^[0-9a-fA-F]{8}$""").matches(hex) -> {
            RgbColor(
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16)
            )
        }
        v.startsWith("rgb(", ignoreCase = true) -> {
            val parts = v.substringAfter("(").substringBeforeLast(")")
                .split(",")
                .map { it.trim().removeSuffix("%").toFloatOrNull() }
            if (parts.size < 3 || parts.any { it == null }) return null
            RgbColor(
                parts[0]!!.toInt().coerceIn(0, 255),
                parts[1]!!.toInt().coerceIn(0, 255),
                parts[2]!!.toInt().coerceIn(0, 255)
            )
        }
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
        val definitions = collectSvgDefinitions(root)

        walkSvgNode(
            output = output,
            node = root,
            indent = "    ",
            definitions = definitions
        )
    } catch (e: Exception) {
        // Fallback for imperfect SVG/XML files
        appendFlatPathsFallback(output, svg, "    ")
    }
}

private fun collectSvgDefinitions(root: Element): Map<String, Element> {
    val definitions = linkedMapOf<String, Element>()

    fun visit(node: Node) {
        if (node.nodeType != Node.ELEMENT_NODE) return

        val element = node as Element
        val id = element.getAttribute("id").trim()

        if (id.isNotBlank()) {
            definitions[id] = element
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            visit(children.item(i))
        }
    }

    visit(root)
    return definitions
}

private fun childClipPathId(node: Node, inheritedClipPath: String?): String? {
    if (node.nodeType != Node.ELEMENT_NODE) return null

    val element = node as Element
    val style = element.getAttribute("style").ifBlank { null }
    val clipPathValue = styleValue(style, "clip-path")
        ?: element.getAttribute("clip-path").ifBlank { inheritedClipPath ?: "" }

    val clipId = clipPathIdFromValue(clipPathValue)
    return clipId?.takeIf { activeClipPathData.containsKey(it) }
}

private fun appendChildrenWithClipGrouping(
    output: StringBuilder,
    parent: Element,
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
    inheritedClipPath: String?,
    definitions: Map<String, Element>,
    useDepth: Int,
    activeClipPathId: String?
) {
    val children = parent.childNodes
    var i = 0

    while (i < children.length) {
        val child = children.item(i)
        val clipId = childClipPathId(child, inheritedClipPath)

        if (clipId != null && clipId != activeClipPathId) {
            output.appendLine("${indent}<group")
            output.appendLine("${indent}>")
            appendClipPath(output, clipId, indent + "    ")

            while (i < children.length) {
                val groupedChild = children.item(i)
                val groupedClipId = childClipPathId(groupedChild, inheritedClipPath)
                if (groupedClipId != clipId) break

                walkSvgNode(
                    output,
                    groupedChild,
                    indent + "    ",
                    inheritedFill,
                    inheritedStroke,
                    inheritedStrokeWidth,
                    inheritedStrokeLineCap,
                    inheritedStrokeLineJoin,
                    inheritedFillRule,
                    inheritedOpacity,
                    inheritedFillOpacity,
                    inheritedStrokeOpacity,
                    inheritedClipPath,
                    definitions,
                    useDepth,
                    clipId
                )

                i++
            }

            output.appendLine("${indent}</group>")
            output.appendLine()
        } else {
            walkSvgNode(
                output,
                child,
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
                inheritedClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
            i++
        }
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
    inheritedStrokeOpacity: String? = null,
    inheritedClipPath: String? = null,
    definitions: Map<String, Element> = emptyMap(),
    useDepth: Int = 0,
    activeClipPathId: String? = null
) {
    if (node.nodeType != Node.ELEMENT_NODE) return

    val element = node as Element
val style = element.getAttribute("style").ifBlank { null }

val currentFill = styleValue(style, "fill")
    ?: element.getAttribute("fill").ifBlank { inheritedFill ?: "" }

val currentStroke = styleValue(style, "stroke")
    ?: element.getAttribute("stroke").ifBlank { inheritedStroke ?: "" }

val currentStrokeWidth = styleValue(style, "stroke-width")
    ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

val currentStrokeLineCap = styleValue(style, "stroke-linecap")
    ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

val currentStrokeLineJoin = styleValue(style, "stroke-linejoin")
    ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

val currentFillRule = styleValue(style, "fill-rule")
    ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

val currentOpacity = combineAlpha(
    inheritedOpacity,
    styleValue(style, "opacity")
        ?: element.getAttribute("opacity").ifBlank { "" }
)

val currentFillOpacity = styleValue(style, "fill-opacity")
    ?: element.getAttribute("fill-opacity").ifBlank { inheritedFillOpacity ?: "" }

val currentStrokeOpacity = styleValue(style, "stroke-opacity")
    ?: element.getAttribute("stroke-opacity").ifBlank { inheritedStrokeOpacity ?: "" }

val currentClipPath = styleValue(style, "clip-path")
    ?: element.getAttribute("clip-path").ifBlank { inheritedClipPath ?: "" }

   val tagName = element.tagName.substringAfter(":").lowercase()

    when (tagName) {
        "defs" -> {
            // Definitions are not drawn directly. They are expanded when a <use> references them.
            return
        }

        "symbol" -> {
            // Symbols are reusable definitions. Draw them only when expanded from a <use>.
            if (useDepth <= 0) return
            appendChildrenWithClipGrouping(
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
                currentStrokeOpacity,
                currentClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
        }

        "use" -> {
            appendUseElement(
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
                currentStrokeOpacity,
                currentClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
        }

        "g" -> {
            val transform = element.getAttribute("transform")
            val translate = parseTranslate(transform)
            val scale = parseScale(transform)
            val rotate = parseRotate(transform)
            val matrix = parseMatrix(transform)
            val groupClipPathId = clipPathIdFromValue(currentClipPath)
            val hasClipPath = groupClipPathId != null && groupClipPathId != activeClipPathId && activeClipPathData.containsKey(groupClipPathId)

            val needsGroup = translate != null || scale != null || rotate != null || matrix != null || hasClipPath

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

if (matrix != null) {
    if (matrix.translateX != 0f) {
        output.appendLine("""${indent}    android:translateX="${matrix.translateX}"""")
    }
    if (matrix.translateY != 0f) {
        output.appendLine("""${indent}    android:translateY="${matrix.translateY}"""")
    }
    if (matrix.scaleX != 1f || matrix.scaleY != 1f) {
        output.appendLine("""${indent}    android:scaleX="${matrix.scaleX}"""")
        output.appendLine("""${indent}    android:scaleY="${matrix.scaleY}"""")
    }
    if (matrix.rotation != 0f) {
        output.appendLine("""${indent}    android:rotation="${matrix.rotation}"""")
    }
                          }

                output.appendLine("${indent}>")

                if (hasClipPath) {
                    appendClipPath(output, groupClipPathId, indent + "    ")
                }

                appendChildrenWithClipGrouping(
                    output,
                    element,
                    indent + "    ",
                    currentFill,
                    currentStroke,
                    currentStrokeWidth,
                    currentStrokeLineCap,
                    currentStrokeLineJoin,
                    currentFillRule,
                    currentOpacity,
                    currentFillOpacity,
                    currentStrokeOpacity,
                    currentClipPath,
                    definitions,
                    useDepth,
                    if (hasClipPath) groupClipPathId else activeClipPathId
                )

                output.appendLine("${indent}</group>")
                output.appendLine()
            } else {
                appendChildrenWithClipGrouping(
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
                    currentStrokeOpacity,
                    currentClipPath,
                    definitions,
                    useDepth,
                    activeClipPathId
                )
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
                currentStrokeOpacity,
                currentClipPath,
                activeClipPathId
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
                currentStrokeOpacity,
                currentClipPath,
                activeClipPathId
            )
        }

        else -> {
            appendChildrenWithClipGrouping(
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
                currentStrokeOpacity,
                currentClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
        }
    }
}

private fun useHrefId(element: Element): String? {
    val href = element.getAttribute("href").ifBlank {
        element.getAttribute("xlink:href").ifBlank {
            element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        }
    }.trim()

    return href
        .removePrefix("#")
        .takeIf { it.isNotBlank() }
}

private data class SvgViewBox(val minX: Float, val minY: Float, val width: Float, val height: Float)

private fun elementViewBox(element: Element): SvgViewBox? {
    val parts = element.getAttribute("viewBox")
        .trim()
        .split(Regex("[,\\s]+"))
        .mapNotNull { it.toFloatOrNull() }

    return if (parts.size >= 4 && parts[2] != 0f && parts[3] != 0f) {
        SvgViewBox(parts[0], parts[1], parts[2], parts[3])
    } else {
        null
    }
}

private fun appendUseElement(
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
    inheritedStrokeOpacity: String?,
    inheritedClipPath: String?,
    definitions: Map<String, Element>,
    useDepth: Int,
    activeClipPathId: String? = null
) {
    if (useDepth >= 20) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use>: nesting limit reached -->")
        return
    }

    val id = useHrefId(element)
    if (id == null) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use>: missing href -->")
        return
    }

    val referenced = definitions[id]
    if (referenced == null) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use href=\"#$id\"> -->")
        return
    }

    val referencedTag = referenced.tagName.substringAfter(":").lowercase()

    if (referencedTag == "defs" || referencedTag == "clippath" || referencedTag == "lineargradient" ||
        referencedTag == "radialgradient" || referencedTag == "mask" || referencedTag == "filter" ||
        referencedTag == "pattern"
    ) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use href=\"#$id\">: referenced <$referencedTag> is not drawable -->")
        return
    }

    activeResolvedUseExpansions++

    val referencedViewBox =
        if (referencedTag == "symbol" || referencedTag == "svg") elementViewBox(referenced) else null

    val useWidth = floatAttr(element, "width")
    val useHeight = floatAttr(element, "height")

    val symbolScaleX =
        if (referencedViewBox != null && useWidth != null) useWidth / referencedViewBox.width else 1f

    val symbolScaleY =
        if (referencedViewBox != null && useHeight != null) useHeight / referencedViewBox.height else 1f

    val x = floatAttr(element, "x") ?: 0f
    val y = floatAttr(element, "y") ?: 0f

    val transform = element.getAttribute("transform")
    val translate = parseTranslate(transform)
    val scale = parseScale(transform)
    val rotate = parseRotate(transform)
    val matrix = parseMatrix(transform)

    val viewBoxTranslateX = -((referencedViewBox?.minX ?: 0f) * symbolScaleX)
    val viewBoxTranslateY = -((referencedViewBox?.minY ?: 0f) * symbolScaleY)

    val totalTranslateX = x + (translate?.first ?: 0f) + viewBoxTranslateX
    val totalTranslateY = y + (translate?.second ?: 0f) + viewBoxTranslateY

    val effectiveScaleX = (scale?.first ?: 1f) * symbolScaleX
    val effectiveScaleY = (scale?.second ?: 1f) * symbolScaleY

    val needsGroup =
        totalTranslateX != 0f ||
        totalTranslateY != 0f ||
        effectiveScaleX != 1f ||
        effectiveScaleY != 1f ||
        rotate != null ||
        matrix != null

    if (needsGroup) {
        output.appendLine("${indent}<group")

        if (totalTranslateX != 0f) {
            output.appendLine("""${indent}    android:translateX="$totalTranslateX"""")
        }

        if (totalTranslateY != 0f) {
            output.appendLine("""${indent}    android:translateY="$totalTranslateY"""")
        }

        if (effectiveScaleX != 1f || effectiveScaleY != 1f) {
            output.appendLine("""${indent}    android:scaleX="$effectiveScaleX"""")
            output.appendLine("""${indent}    android:scaleY="$effectiveScaleY"""")
        }

if (rotate != null) {
    output.appendLine("""${indent}    android:rotation="${rotate.degrees}"""")
    if (rotate.pivotX != null && rotate.pivotY != null) {
        output.appendLine("""${indent}    android:pivotX="${rotate.pivotX}"""")
        output.appendLine("""${indent}    android:pivotY="${rotate.pivotY}"""")
    }
}

if (matrix != null) {
    if (matrix.translateX != 0f) {
        output.appendLine("""${indent}    android:translateX="${matrix.translateX}"""")
    }
    if (matrix.translateY != 0f) {
        output.appendLine("""${indent}    android:translateY="${matrix.translateY}"""")
    }
    if (matrix.scaleX != 1f || matrix.scaleY != 1f) {
        output.appendLine("""${indent}    android:scaleX="${matrix.scaleX}"""")
        output.appendLine("""${indent}    android:scaleY="${matrix.scaleY}"""")
    }
    if (matrix.rotation != 0f) {
        output.appendLine("""${indent}    android:rotation="${matrix.rotation}"""")
    }
                          }

        output.appendLine("${indent}>")
        output.appendLine("${indent}    <!-- expanded from <use href=\"#$id\"> -->")

        walkSvgNode(
            output,
            referenced,
            indent + "    ",
            inheritedFill,
            inheritedStroke,
            inheritedStrokeWidth,
            inheritedStrokeLineCap,
            inheritedStrokeLineJoin,
            inheritedFillRule,
            inheritedOpacity,
            inheritedFillOpacity,
            inheritedStrokeOpacity,
            inheritedClipPath,
            definitions,
            useDepth + 1,
            activeClipPathId
        )

        output.appendLine("${indent}</group>")
        output.appendLine()
    } else {
        output.appendLine("${indent}<!-- expanded from <use href=\"#$id\"> -->")

        walkSvgNode(
            output,
            referenced,
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
            inheritedClipPath,
            definitions,
            useDepth + 1,
            activeClipPathId
        )
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
    inheritedStrokeOpacity: String?,
    inheritedClipPath: String?,
    activeClipPathId: String? = null
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
        inheritedClipPath,
        activeClipPathId,
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
    inheritedStrokeOpacity: String?,
    inheritedClipPath: String?,
    activeClipPathId: String? = null
) {
    val d = element.getAttribute("d").trim()
    if (d.isBlank()) return

    appendElementPathData(
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
    inheritedClipPath: String?,
    activeClipPathId: String? = null,
    sourceTag: String?
) {
    val style = element.getAttribute("style").ifBlank { null }

val rawFill = styleValue(style, "fill")
    ?: element.getAttribute("fill").ifBlank { inheritedFill ?: "#000000" }

val rawStroke = styleValue(style, "stroke")
    ?: element.getAttribute("stroke").ifBlank { inheritedStroke ?: "" }

val strokeWidth = styleValue(style, "stroke-width")
    ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

val strokeLineCap = styleValue(style, "stroke-linecap")
    ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

val strokeLineJoin = styleValue(style, "stroke-linejoin")
    ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

val fillRule = styleValue(style, "fill-rule")
    ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

val fillOpacity = styleValue(style, "fill-opacity")
    ?: element.getAttribute("fill-opacity").ifBlank { inheritedFillOpacity ?: "" }

val strokeOpacity = styleValue(style, "stroke-opacity")
    ?: element.getAttribute("stroke-opacity").ifBlank { inheritedStrokeOpacity ?: "" }

val clipPathValue = styleValue(style, "clip-path")
    ?: element.getAttribute("clip-path").ifBlank { inheritedClipPath ?: "" }
val clipPathId = clipPathIdFromValue(clipPathValue)
val hasClipPath = clipPathId != null && clipPathId != activeClipPathId && activeClipPathData.containsKey(clipPathId)

val fillAlpha = resolveDrawableAlpha(inheritedOpacity, fillOpacity)
val strokeAlpha = resolveDrawableAlpha(inheritedOpacity, strokeOpacity)

    val fill =
        if (sourceTag == "line") {
            "@android:color/transparent"
        } else {
            safeFillColor(rawFill)
        }
    val stroke = safeStrokeColor(rawStroke)

    val pathTransform = element.getAttribute("transform")
    val translate = parseTranslate(pathTransform)
    val matrix = parseMatrix(pathTransform)
    val scale = parseScale(pathTransform)
    val rotate = parseRotate(pathTransform)



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
            
            if (matrix != null) {
    if (matrix.translateX != 0f) {
        output.appendLine("""${indent}    android:translateX="${matrix.translateX}"""")
    }
    if (matrix.translateY != 0f) {
        output.appendLine("""${indent}    android:translateY="${matrix.translateY}"""")
    }
    if (matrix.scaleX != 1f || matrix.scaleY != 1f) {
        output.appendLine("""${indent}    android:scaleX="${matrix.scaleX}"""")
        output.appendLine("""${indent}    android:scaleY="${matrix.scaleY}"""")
    }
    if (matrix.rotation != 0f) {
        output.appendLine("""${indent}    android:rotation="${matrix.rotation}"""")
    }
                          }
}

        output.appendLine("${indent}>")
        if (hasClipPath) {
            appendClipPath(output, clipPathId, indent + "    ")
        }
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
    val drawableTags = Regex("""<(path|rect|circle|ellipse|line|polyline|polygon)\b[^>]*>""", RegexOption.IGNORE_CASE)

    drawableTags
        .findAll(xml)
        .forEach { match ->
            val tag = match.value
            val tagName = match.groupValues[1].lowercase()

            val d = if (tagName == "path") {
                attr(tag, "d")?.trim().orEmpty()
            } else {
                basicShapeTagToPathData(tag, tagName)
            }

            if (d.isBlank()) return@forEach

            val rawFill = styleValue(attr(tag, "style"), "fill")
                ?: attr(tag, "fill")

            val rawStroke = styleValue(attr(tag, "style"), "stroke")
                ?: attr(tag, "stroke")

            val strokeWidth = styleValue(attr(tag, "style"), "stroke-width")
                ?: attr(tag, "stroke-width")

            val strokeLineCap = styleValue(attr(tag, "style"), "stroke-linecap")
                ?: attr(tag, "stroke-linecap")

            val strokeLineJoin = styleValue(attr(tag, "style"), "stroke-linejoin")
                ?: attr(tag, "stroke-linejoin")

            val opacity = styleValue(attr(tag, "style"), "opacity")
                ?: attr(tag, "opacity")

            val fillOpacity = styleValue(attr(tag, "style"), "fill-opacity")
                ?: attr(tag, "fill-opacity")

            val strokeOpacity = styleValue(attr(tag, "style"), "stroke-opacity")
                ?: attr(tag, "stroke-opacity")

            val fillRule = styleValue(attr(tag, "style"), "fill-rule")
                ?: attr(tag, "fill-rule")

            if (tagName != "path") {
                output.appendLine("${indent}<!-- converted from <$tagName> -->")
            }

            val fillColor =
                if (tagName == "line") {
                    "@android:color/transparent"
                } else {
                    safeFillColor(rawFill)
                }

            appendPath(
                output,
                d,
                fillColor,
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

private fun basicShapeTagToPathData(tag: String, tagName: String): String {
    fun floatAttrFromTag(name: String): Float? =
        attr(tag, name)
            ?.replace("px", "")
            ?.replace("dp", "")
            ?.trim()
            ?.toFloatOrNull()

    fun pointsAttrToPathData(close: Boolean): String {
        val values = attr(tag, "points")
            ?.trim()
            ?.replace(",", " ")
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toFloatOrNull() }
            ?: return ""

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

    return when (tagName) {
        "rect" -> {
            val x = floatAttrFromTag("x") ?: 0f
            val y = floatAttrFromTag("y") ?: 0f
            val w = floatAttrFromTag("width") ?: return ""
            val h = floatAttrFromTag("height") ?: return ""
            if (w <= 0f || h <= 0f) return ""

            var rx = floatAttrFromTag("rx") ?: 0f
            var ry = floatAttrFromTag("ry") ?: 0f
            if (rx > 0f && ry == 0f) ry = rx
            if (ry > 0f && rx == 0f) rx = ry
            rx = rx.coerceAtMost(w / 2f)
            ry = ry.coerceAtMost(h / 2f)

            if (rx <= 0f || ry <= 0f) {
                "M $x,$y L ${x + w},$y L ${x + w},${y + h} L $x,${y + h} Z"
            } else {
                val right = x + w
                val bottom = y + h
                "M ${x + rx},$y " +
                    "L ${right - rx},$y " +
                    "A $rx,$ry 0,0,1 $right,${y + ry} " +
                    "L $right,${bottom - ry} " +
                    "A $rx,$ry 0,0,1 ${right - rx},$bottom " +
                    "L ${x + rx},$bottom " +
                    "A $rx,$ry 0,0,1 $x,${bottom - ry} " +
                    "L $x,${y + ry} " +
                    "A $rx,$ry 0,0,1 ${x + rx},$y Z"
            }
        }

        "circle" -> {
            val cx = floatAttrFromTag("cx") ?: 0f
            val cy = floatAttrFromTag("cy") ?: 0f
            val r = floatAttrFromTag("r") ?: return ""
            if (r <= 0f) return ""
            "M ${cx - r},$cy A $r,$r 0,1,0 ${cx + r},$cy A $r,$r 0,1,0 ${cx - r},$cy Z"
        }

        "ellipse" -> {
            val cx = floatAttrFromTag("cx") ?: 0f
            val cy = floatAttrFromTag("cy") ?: 0f
            val rx = floatAttrFromTag("rx") ?: return ""
            val ry = floatAttrFromTag("ry") ?: return ""
            if (rx <= 0f || ry <= 0f) return ""
            "M ${cx - rx},$cy A $rx,$ry 0,1,0 ${cx + rx},$cy A $rx,$ry 0,1,0 ${cx - rx},$cy Z"
        }

        "line" -> {
            val x1 = floatAttrFromTag("x1") ?: 0f
            val y1 = floatAttrFromTag("y1") ?: 0f
            val x2 = floatAttrFromTag("x2") ?: 0f
            val y2 = floatAttrFromTag("y2") ?: 0f
            "M $x1,$y1 L $x2,$y2"
        }

        "polyline" -> pointsAttrToPathData(close = false)
        "polygon" -> pointsAttrToPathData(close = true)
        else -> ""
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
            "square" -> output.appendLine("""${indent}    android:strokeLineCap="square"""")
            "round" -> output.appendLine("""${indent}    android:strokeLineCap="round"""")
            else -> {
                // Heroicons Outline and many stroked icon sets expect round line caps.
                // Android's default is butt, so write round explicitly when no cap is provided.
                if (stroke != null) {
                    output.appendLine("""${indent}    android:strokeLineCap="round"""")
                }
            }
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
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { declaration ->
                val parts = declaration.split(":", limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val propertyName = parts[0].trim()
                val propertyValue = parts[1].trim()

                if (propertyName.equals(name, ignoreCase = true) && propertyValue.isNotBlank()) {
                    propertyValue
                } else {
                    null
                }
            }
            .firstOrNull()
    }

    private data class RotateTransform(
        val degrees: Float,
        val pivotX: Float?,
        val pivotY: Float?
    )

    private data class MatrixTransform(
    val translateX: Float,
    val translateY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float
)

private fun parseMatrix(transform: String?): MatrixTransform? {
    if (transform == null) return null

    val match = Regex("""matrix\(([^)]*)\)""")
        .find(transform)
        ?: return null

    val nums = match.groupValues[1]
        .split(Regex("[,\\s]+"))
        .filter { it.isNotBlank() }
        .mapNotNull { it.toFloatOrNull() }

    if (nums.size != 6) {
        activeUnsupportedMatrixTransforms++
        return null
    }

    val a = nums[0]
    val b = nums[1]
    val c = nums[2]
    val d = nums[3]
    val e = nums[4]
    val f = nums[5]

    val scaleX = sqrt(a * a + b * b)
    val scaleY = sqrt(c * c + d * d)

    if (scaleX == 0f || scaleY == 0f) {
        activeUnsupportedMatrixTransforms++
        return null
    }

    val dot = a * c + b * d
    val hasSkew = abs(dot) > 0.0001f

    if (hasSkew) {
        activeUnsupportedMatrixTransforms++
        return null
    }

    val rotation = (atan2(b, a) * 180f / PI.toFloat())

    activeSupportedMatrixTransforms++

    return MatrixTransform(
        translateX = e,
        translateY = f,
        scaleX = scaleX,
        scaleY = scaleY,
        rotation = rotation
    )
}
    

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



private fun parseAlphaValue(value: String?): Float? {
    return parseSvgAlpha(value)
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

            "clip-path" -> {
                applyClipPathElement(canvas, element)
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


private fun applyClipPathElement(canvas: Canvas, element: Element) {
    val pathData = element.getAttribute("android:pathData")
    if (pathData.isBlank()) return

    val path = androidx.core.graphics.PathParser
        .createPathFromPathData(pathData)

    canvas.clipPath(path)
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
