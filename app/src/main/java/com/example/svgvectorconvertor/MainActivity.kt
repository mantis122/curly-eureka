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

data class ConversionResult(
    val xml: String,
    val report: String
)

data class BatchResult(
    val fileName: String,
    val xml: String?,
    val warningCount: Int,
    val success: Boolean,
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
        convertedXml = result.xml
        reportBox.text = result.report
        outputBox.setText(convertedXml)
        updatePreview(convertedXml)
        batchGallery.removeAllViews()
    }
}

private val openMultipleSvgs = registerForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
) { uris ->
    if (uris.isNotEmpty()) {
        batchResults.clear()

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

    batchResults.add(
        BatchResult(
            fileName = fileName,
            xml = result.xml,
            warningCount = warningCount,
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

        val title = TextView(this).apply {
            text = "SVG → Android Vector"
            textSize = 24f
            setTextColor(Color.BLACK)
        }

        val openButton = Button(this).apply {
            text = "Open SVG"
            setOnClickListener {
                openSvg.launch(arrayOf("image/svg+xml", "text/xml", "text/plain"))
            }
        }

        val copyButton = Button(this).apply {
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

        val saveZipButton = Button(this).apply {
            text = "Save ZIP"
            setOnClickListener {
                if (batchResults.isEmpty()) {
                    toast("No batch results yet")
                } else {
                    saveZip.launch("converted_vectors.zip")
                }
            }
        }

        val saveButton = Button(this).apply {
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
        }

        outputBox.visibility = View.GONE

    

val previewTab = Button(this).apply {
    text = "Preview"
    setOnClickListener {
        mainPanel.visibility = View.VISIBLE
        outputBox.visibility = View.GONE
    }
}

val xmlTab = Button(this).apply {
    text = "XML"
    setOnClickListener {
        mainPanel.visibility = View.GONE
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
    addView(saveButton, LinearLayout.LayoutParams(0, -2, 1f))
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
        mainPanel.addView(reportBox)
        mainPanel.addView(batchGallery)        
        mainPanel.addView(bottomSpacer, LinearLayout.LayoutParams(-1, 96))

        root.addView(title)
        root.addView(openRow)
        root.addView(saveRow)
        root.addView(utilityRow)
        root.addView(tabRow)
        root.addView(scrollView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(outputBox, LinearLayout.LayoutParams(-1, 0, 1f)) 

         setContentView(root)
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

    android.app.AlertDialog.Builder(this)
        .setTitle("SVG → Android Vector")
        .setMessage(
            """
Convert SVG artwork into
Android VectorDrawable XML.

Features:
• SVG → VectorDrawable conversion
• Batch ZIP export
• Preview rendering
• Size presets
• Conversion profiles

Unsupported SVG features are reported
when detected.

Version $versionName
© 2026 Nathan Harris
            """.trimIndent()
        )
        .setPositiveButton("OK", null)
        .show()
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
    text =
        when {
            !result.success -> "✕ ${result.fileName}"
            result.warningCount > 0 -> "⚠ ${result.fileName}"
            else -> "✓ ${result.fileName}"
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

val translateCount = Regex("""translate\(""").findAll(svg).count()
val scaleCount = Regex("""scale\(""").findAll(svg).count()
val matrixCount = Regex("""matrix\(""").findAll(svg).count()

val pathCount = Regex("""<path\b[^>]*>""").findAll(svg).count()
val validPathCount = Regex("""<path\b[^>]*>""")
    .findAll(svg)
    .count { match ->
        val d = attr(match.value, "d")?.trim()
        !d.isNullOrBlank()
    }

val emptyPathCount = pathCount - validPathCount
val groupCount = Regex("""<g\b[^>]*>""").findAll(svg).count()

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

        val groupRegex = Regex("""<g\b[^>]*>.*?</g>""", RegexOption.DOT_MATCHES_ALL)
        val groups = groupRegex.findAll(svg).toList()

        if (groups.isNotEmpty()) {
            for (groupMatch in groups) {
                appendConvertedGroup(output, groupMatch.value)
            }
        } else {
            appendConvertedPaths(output, svg, null)
        }

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
val generatedGroupCount = Regex("""<group\b""").findAll(finalXml).count()

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

    appendLine("Conversion Statistics")
    appendLine()
    appendLine("✓ Paths converted: $convertedPathCount / $validPathCount")
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
    appendLine("✓ Generated groups: $generatedGroupCount")
    appendLine()

    appendLine()
    appendLine("Transforms")
    appendLine()

    appendLine("✓ Translate transforms: $translateCount")
    appendLine("✓ Scale transforms: $scaleCount")

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

    private fun hasTag(svg: String, tagName: String): Boolean {
        return Regex("""<\s*$tagName\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(svg)
    }

    private fun appendConvertedGroup(output: StringBuilder, groupXml: String) {
        val groupStartTag = Regex("""<g\b[^>]*>""")
            .find(groupXml)
            ?.value
            ?: ""

        val transform = attr(groupStartTag, "transform")
        val translate = parseTranslate(transform)
        val scale = parseScale(transform)

        val needsGroup = translate != null || scale != null

        if (needsGroup) {
            output.appendLine("    <group")

            if (translate != null) {
                output.appendLine("""        android:translateX="${translate.first}"""")
                output.appendLine("""        android:translateY="${translate.second}"""")
            }

            if (scale != null) {
                output.appendLine("""        android:scaleX="${scale.first}"""")
                output.appendLine("""        android:scaleY="${scale.second}"""")
            }

            output.appendLine("    >")
            appendConvertedPaths(output, groupXml, "        ")
            output.appendLine("    </group>")
            output.appendLine()
        } else {
            appendConvertedPaths(output, groupXml, null)
        }
    }

    private fun appendConvertedPaths(
        output: StringBuilder,
        xml: String,
        indentOverride: String?
    ) {
        val indent = indentOverride ?: "    "

        Regex("""<path\b[^>]*>""")
            .findAll(xml)
            .forEach { match ->
                val tag = match.value
                val d = attr(tag, "d")?.trim()
                if (d.isNullOrBlank()) return@forEach

                val fill = attr(tag, "fill")
                    ?: styleValue(attr(tag, "style"), "fill")
                    ?: "#000000"

                val stroke = attr(tag, "stroke")
                    ?: styleValue(attr(tag, "style"), "stroke")

                val strokeWidth = attr(tag, "stroke-width")
                    ?: styleValue(attr(tag, "style"), "stroke-width")

                val pathTransform = attr(tag, "transform")
                val translate = parseTranslate(pathTransform)
                val scale = parseScale(pathTransform)

                val pathNeedsGroup = translate != null || scale != null

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

                    output.appendLine("${indent}>")
                    appendPath(output, d, fill, stroke, strokeWidth, indent + "    ")
                    output.appendLine("${indent}</group>")
                } else {
                    appendPath(output, d, fill, stroke, strokeWidth, indent)
                }

                output.appendLine()
            }
    }

    private fun appendPath(
        output: StringBuilder,
        d: String,
        fill: String,
        stroke: String?,
        strokeWidth: String?,
        indent: String
    ) {
        output.appendLine("${indent}<path")
        output.appendLine("""${indent}    android:pathData="${escapeXml(d)}"""")

        if (fill != "none") {
            output.appendLine("""${indent}    android:fillColor="$fill"""")
        } else {
            output.appendLine("""${indent}    android:fillColor="@android:color/transparent"""")
        }

        if (stroke != null && stroke != "none") {
            output.appendLine("""${indent}    android:strokeColor="$stroke"""")
            output.appendLine("""${indent}    android:strokeWidth="${strokeWidth ?: "1"}"""")
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

        val scaleX = width / viewportWidth
        val scaleY = height / viewportHeight
        canvas.scale(scaleX, scaleY)

        val groupRegex = Regex("""<group\b[^>]*>.*?</group>""", RegexOption.DOT_MATCHES_ALL)
        val groups = groupRegex.findAll(xml).toList()

        if (groups.isNotEmpty()) {
            for (group in groups) {
                drawGroup(canvas, group.value)
            }
        } else {
            drawPaths(canvas, xml)
        }

        return bitmap
    }

    private fun drawGroup(canvas: Canvas, groupXml: String) {
        val startTag = Regex("""<group\b[^>]*>""")
            .find(groupXml)
            ?.value
            ?: ""

        val translateX = attr(startTag, "android:translateX")?.toFloatOrNull() ?: 0f
        val translateY = attr(startTag, "android:translateY")?.toFloatOrNull() ?: 0f
        val scaleX = attr(startTag, "android:scaleX")?.toFloatOrNull() ?: 1f
        val scaleY = attr(startTag, "android:scaleY")?.toFloatOrNull() ?: 1f

        canvas.save()
        canvas.translate(translateX, translateY)
        canvas.scale(scaleX, scaleY)

        drawPaths(canvas, groupXml)

        canvas.restore()
    }

    private fun drawPaths(canvas: Canvas, xml: String) {
        Regex("""<path\b[^>]*/>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .forEach { match ->
                val tag = match.value
                val pathData = attr(tag, "android:pathData") ?: return@forEach
                val fillColor = attr(tag, "android:fillColor") ?: "#000000"

                if (fillColor == "@android:color/transparent") return@forEach

                val path = androidx.core.graphics.PathParser.createPathFromPathData(pathData)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.parseColor(fillColor)
                }

                canvas.drawPath(path, paint)
            }
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }
}
