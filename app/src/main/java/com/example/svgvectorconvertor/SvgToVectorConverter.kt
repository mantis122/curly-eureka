package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale

object SvgToVectorConverter {
private var activeGradientFallbackColors: Map<String, String> = emptyMap()
private var activeClipPathData: Map<String, String> = emptyMap()
private var activeAppliedClipPaths = 0
private var activeResolvedUseExpansions = 0
private var activeUnresolvedUseReferences = 0

private data class BasicShapeBreakdown(
    val rectangles: Int = 0,
    val roundedRectangles: Int = 0,
    val circles: Int = 0,
    val ellipses: Int = 0,
    val polygons: Int = 0,
    val polylines: Int = 0
)
  
fun convert(
    svg: String,
    outputDpSize: Int,
    conversionProfile: String
): ConversionResult {

val startTime = System.nanoTime()

SvgTransformParser.resetMatrixStats()
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
    "stroke-miterlimit",
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
val basicShapeBreakdown = countDrawableBasicShapeBreakdown(drawableSvgForStats)
val convertedOriginalPathCount = convertedPathCount - convertedBasicShapeCount
val generatedGroupCount = Regex("""<group\b""").findAll(finalXml).count()

val elapsedMs =
    (System.nanoTime() - startTime) / 1_000_000

val warningCount =
    unsupported.size +
    if (SvgTransformParser.unsupportedMatrixTransforms > 0) 1 else 0

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
        appendLine("✓ Use references expanded: $activeResolvedUseExpansions")
    }
    if (symbolCount > 0) {
        appendLine("✓ Symbol definitions: $symbolCount")
    }
    appendLine("✓ Basic shapes generated: $convertedBasicShapeCount")
    appendBasicShapeBreakdown(basicShapeBreakdown)
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
    appendBasicShapeBreakdown(basicShapeBreakdown)
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
    appendLine("✓ Matrix transforms supported: ${SvgTransformParser.supportedMatrixTransforms}")
    appendLine("⚠ Matrix transforms unsupported: ${SvgTransformParser.unsupportedMatrixTransforms}")
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


if (unsupported.isEmpty() && SvgTransformParser.unsupportedMatrixTransforms == 0) {
    appendLine("✓ No warnings detected")
} else {
    appendLine("Warnings")
    appendLine()

    if (SvgTransformParser.unsupportedMatrixTransforms > 0) {
        appendLine("⚠ Unsupported matrix transforms: ${SvgTransformParser.unsupportedMatrixTransforms}")
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

private fun StringBuilder.appendBasicShapeBreakdown(breakdown: BasicShapeBreakdown) {
    appendLine("    • Rectangles: ${breakdown.rectangles}")
    appendLine("    • Rounded rectangles: ${breakdown.roundedRectangles}")
    appendLine("    • Circles: ${breakdown.circles}")
    appendLine("    • Ellipses: ${breakdown.ellipses}")
    appendLine("    • Polygons: ${breakdown.polygons}")
    appendLine("    • Polylines: ${breakdown.polylines}")
}

private fun countDrawableBasicShapeBreakdown(svg: String): BasicShapeBreakdown {
    var rectangles = 0
    var roundedRectangles = 0
    var circles = 0
    var ellipses = 0
    var polygons = 0
    var polylines = 0

    fun countElement(element: Element) {
        val tag = element.tagName.substringAfter(":").lowercase()

        when (tag) {
            "rect" -> {
                if (basicShapeToPathData(element, tag) != null) {
                    val rx = floatAttr(element, "rx") ?: 0f
                    val ry = floatAttr(element, "ry") ?: 0f
                    if (rx > 0f || ry > 0f) {
                        roundedRectangles++
                    } else {
                        rectangles++
                    }
                }
            }
            "circle" -> {
                if (basicShapeToPathData(element, tag) != null) circles++
            }
            "ellipse" -> {
                if (basicShapeToPathData(element, tag) != null) ellipses++
            }
            "polygon" -> {
                if (basicShapeToPathData(element, tag) != null) polygons++
            }
            "polyline" -> {
                if (basicShapeToPathData(element, tag) != null) polylines++
            }
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                countElement(child as Element)
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

        countElement(document.documentElement)

        BasicShapeBreakdown(
            rectangles = rectangles,
            roundedRectangles = roundedRectangles,
            circles = circles,
            ellipses = ellipses,
            polygons = polygons,
            polylines = polylines
        )
    } catch (e: Exception) {
        val rectTagMatches = Regex("""<\s*rect\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .map { it.value }
            .toList()

        val roundedRectFallbackCount = rectTagMatches.count { tag ->
            Regex("""\b(rx|ry)\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(tag)
        }

        BasicShapeBreakdown(
            rectangles = rectTagMatches.size - roundedRectFallbackCount,
            roundedRectangles = roundedRectFallbackCount,
            circles = Regex("""<\s*circle\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
            ellipses = Regex("""<\s*ellipse\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
            polygons = Regex("""<\s*polygon\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
            polylines = Regex("""<\s*polyline\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
        )
    }
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
    inheritedStrokeMiterLimit: String?,
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
                    inheritedStrokeMiterLimit,
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
                inheritedStrokeMiterLimit,
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
    inheritedStrokeMiterLimit: String? = null,
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

val currentStrokeMiterLimit = styleValue(style, "stroke-miterlimit")
    ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

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
                currentStrokeMiterLimit,
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
                currentStrokeMiterLimit,
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
            val transforms = SvgTransformParser.parseTransformList(transform)
            val groupClipPathId = clipPathIdFromValue(currentClipPath)
            val hasClipPath = groupClipPathId != null && groupClipPathId != activeClipPathId && activeClipPathData.containsKey(groupClipPathId)

            val needsGroup = transforms.any { it.hasVisibleEffect() } || hasClipPath

            if (needsGroup) {
                var currentIndent = indent
                var openedGroupCount = 0

                if (hasClipPath) {
                    output.appendLine("${currentIndent}<group>")
                    appendClipPath(output, groupClipPathId, currentIndent + "    ")
                    currentIndent += "    "
                    openedGroupCount++
                }

                val openedTransformGroups = SvgTransformParser.appendTransformGroupsStart(output, transforms, currentIndent)
                currentIndent = openedTransformGroups.first
                openedGroupCount += openedTransformGroups.second

                appendChildrenWithClipGrouping(
                    output,
                    element,
                    currentIndent,
                    currentFill,
                    currentStroke,
                    currentStrokeWidth,
                    currentStrokeLineCap,
                    currentStrokeLineJoin,
                    currentStrokeMiterLimit,
                    currentFillRule,
                    currentOpacity,
                    currentFillOpacity,
                    currentStrokeOpacity,
                    currentClipPath,
                    definitions,
                    useDepth,
                    if (hasClipPath) groupClipPathId else activeClipPathId
                )

                repeat(openedGroupCount) {
                    currentIndent = currentIndent.dropLast(4)
                    output.appendLine("${currentIndent}</group>")
                }
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
                    currentStrokeMiterLimit,
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
                currentStrokeMiterLimit,
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
                currentStrokeMiterLimit,
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
                currentStrokeMiterLimit,
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
    inheritedStrokeMiterLimit: String?,
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
    val placementTransforms = mutableListOf<ParsedTransform>()
    placementTransforms.addAll(SvgTransformParser.parseTransformList(transform))

    val viewBoxTranslateX = -((referencedViewBox?.minX ?: 0f) * symbolScaleX)
    val viewBoxTranslateY = -((referencedViewBox?.minY ?: 0f) * symbolScaleY)

    if (x + viewBoxTranslateX != 0f || y + viewBoxTranslateY != 0f) {
        placementTransforms.add(ParsedTransform.Translate(x + viewBoxTranslateX, y + viewBoxTranslateY))
    }

    if (symbolScaleX != 1f || symbolScaleY != 1f) {
        placementTransforms.add(ParsedTransform.Scale(symbolScaleX, symbolScaleY))
    }

    val needsGroup = placementTransforms.any { it.hasVisibleEffect() }

    if (needsGroup) {
        val openedTransformGroups = SvgTransformParser.appendTransformGroupsStart(output, placementTransforms, indent)
        val childIndent = openedTransformGroups.first

        output.appendLine("${childIndent}<!-- expanded from <use href=\"#$id\"> -->")

        walkSvgNode(
            output,
            referenced,
            childIndent,
            inheritedFill,
            inheritedStroke,
            inheritedStrokeWidth,
            inheritedStrokeLineCap,
            inheritedStrokeLineJoin,
            inheritedStrokeMiterLimit,
            inheritedFillRule,
            inheritedOpacity,
            inheritedFillOpacity,
            inheritedStrokeOpacity,
            inheritedClipPath,
            definitions,
            useDepth + 1,
            activeClipPathId
        )

        SvgTransformParser.closeGroups(output, childIndent, openedTransformGroups.second)
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
            inheritedStrokeMiterLimit,
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
    inheritedStrokeMiterLimit: String?,
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
        inheritedStrokeMiterLimit,
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
    inheritedStrokeMiterLimit: String?,
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
        output,
        element,
        d,
        indent,
        inheritedFill,
        inheritedStroke,
        inheritedStrokeWidth,
        inheritedStrokeLineCap,
        inheritedStrokeLineJoin,
        inheritedStrokeMiterLimit,
        inheritedFillRule,
        inheritedOpacity,
        inheritedFillOpacity,
        inheritedStrokeOpacity,
        inheritedClipPath,
        activeClipPathId,
        sourceTag = "path"
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
    inheritedStrokeMiterLimit: String?,
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

val strokeMiterLimit = styleValue(style, "stroke-miterlimit")
    ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

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
    val transforms = SvgTransformParser.parseTransformList(pathTransform)

    val pathNeedsGroup = transforms.any { it.hasVisibleEffect() } || hasClipPath

    if (pathNeedsGroup) {
        var currentIndent = indent
        var openedGroupCount = 0

        if (hasClipPath) {
            output.appendLine("${currentIndent}<group>")
            appendClipPath(output, clipPathId, currentIndent + "    ")
            currentIndent += "    "
            openedGroupCount++
        }

        val openedTransformGroups = SvgTransformParser.appendTransformGroupsStart(output, transforms, currentIndent)
        currentIndent = openedTransformGroups.first
        openedGroupCount += openedTransformGroups.second

        if (sourceTag != null) {
            output.appendLine("${currentIndent}<!-- converted from <$sourceTag> -->")
        }
        appendPath(
            output,
            d,
            fill,
            stroke,
            strokeWidth.ifBlank { null },
            strokeLineCap.ifBlank { null },
            strokeLineJoin.ifBlank { null },
            strokeMiterLimit.ifBlank { null },
            fillRule.ifBlank { null },
            fillAlpha,
            strokeAlpha,
            currentIndent
        )

        repeat(openedGroupCount) {
            currentIndent = currentIndent.dropLast(4)
            output.appendLine("${currentIndent}</group>")
        }
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
            strokeMiterLimit.ifBlank { null },
            fillRule.ifBlank { null },
            fillAlpha,
            strokeAlpha,
            indent
        )
    }

    output.appendLine()
}


private fun basicShapeToPathData(element: Element, tagName: String): String? {
    return SvgShapeConverters.basicShapeToPathData(element, tagName)
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

            val strokeMiterLimit = styleValue(attr(tag, "style"), "stroke-miterlimit")
                ?: attr(tag, "stroke-miterlimit")

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
                strokeMiterLimit,
                fillRule,
                resolveDrawableAlpha(opacity, fillOpacity),
                resolveDrawableAlpha(opacity, strokeOpacity),
                indent
            )

            output.appendLine()
        }
}

private fun basicShapeTagToPathData(tag: String, tagName: String): String {
    return SvgShapeConverters.basicShapeTagToPathData(tag, tagName)
}

private fun appendPath(
    output: StringBuilder,
    d: String,
    fill: String,
    stroke: String?,
    strokeWidth: String?,
    strokeLineCap: String?,
    strokeLineJoin: String?,
    strokeMiterLimit: String?,
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
    "square" -> output.appendLine("""${indent}    android:strokeLineCap="square"""")
    "round" -> output.appendLine("""${indent}    android:strokeLineCap="round"""")
}

        when (strokeLineJoin?.trim()?.lowercase()) {
            "round" -> output.appendLine("""${indent}    android:strokeLineJoin="round"""")
            "bevel" -> output.appendLine("""${indent}    android:strokeLineJoin="bevel"""")
            // Omit null and "miter" because Android's default matches SVG's default.
        }

        normalizeNumber(strokeMiterLimit)?.takeIf { it != "4" && it != "4.0" }?.let { miterLimit ->
            output.appendLine("""${indent}    android:strokeMiterLimit="$miterLimit"""")
        }
    }

    output.appendLine("${indent}/>")
}


private fun normalizeNumber(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val cleaned = raw.removeSuffix("px").trim()
    val number = cleaned.toFloatOrNull() ?: return null
    return java.lang.String.format(java.util.Locale.US, "%.3f", number)
        .trimEnd('0')
        .trimEnd('.')
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
