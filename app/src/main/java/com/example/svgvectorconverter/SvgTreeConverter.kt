package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import kotlin.math.abs

object SvgTreeConverter {
private var activeClipPathData: Map<String, String> = emptyMap()
private var activeMaskPathData: Map<String, String> = emptyMap()
private var activeAppliedClipPaths = 0
private var activeAppliedMasks = 0
private var activeResolvedUseExpansions = 0
private var activeUnresolvedUseReferences = 0
private var activeMarkerDefinitions: Map<String, MarkerDefinition> = emptyMap()
private var activeAppliedMarkers = 0
private var activeDashedStrokesDetected = 0
private var activeDashedStrokesApproximated = 0
private var activeNonScalingStrokesDetected = 0
private var activeNonScalingStrokesCompensated = 0
private var activeNonScalingStrokesUncertain = 0
private var activePatternDefinitions: Map<String, PatternDefinition> = emptyMap()
private var activeSvgFontDefinitions: Map<String, SvgFontDefinition> = emptyMap()
private var activePatternTileExpansions = 0
private var activePatternTilePathsEmitted = 0
private var activeTextElementsApproximated = 0
private var activeTextElementsConvertedToPaths = 0
private var activeTextGlyphPathsEmitted = 0
private var activeTextGlyphSpecificAdvances = 0
private var activeTextDefaultFontAdvances = 0
private var activeTextHorizontalKerningPairs = 0
private var activeTextVerticalKerningPairs = 0
private var activeTextHorizontalKerningPairsMatched = 0
private var activeTextVerticalKerningPairsMatched = 0
private var activeTextKerningAdjustmentsApplied = 0
private val activeMatchedHorizontalKerningPairs = linkedSetOf<SvgKerningPair>()
private val activeMatchedVerticalKerningPairs = linkedSetOf<SvgKerningPair>()
private val activeTextFontFamilies = linkedSetOf<String>()
private val activeTextFontWeights = linkedSetOf<String>()

val appliedClipPaths: Int get() = activeAppliedClipPaths
val appliedMasks: Int get() = activeAppliedMasks
val maskPathCount: Int get() = activeMaskPathData.size
val resolvedUseExpansions: Int get() = activeResolvedUseExpansions
val unresolvedUseReferences: Int get() = activeUnresolvedUseReferences
val appliedMarkers: Int get() = activeAppliedMarkers
val dashedStrokesDetected: Int get() = activeDashedStrokesDetected
val dashedStrokesApproximated: Int get() = activeDashedStrokesApproximated
val nonScalingStrokesDetected: Int get() = activeNonScalingStrokesDetected
val nonScalingStrokesCompensated: Int get() = activeNonScalingStrokesCompensated
val nonScalingStrokesUncertain: Int get() = activeNonScalingStrokesUncertain
val patternTileExpansions: Int get() = activePatternTileExpansions
val patternTilePathsEmitted: Int get() = activePatternTilePathsEmitted
val textElementsApproximated: Int get() = activeTextElementsApproximated
val textElementsConvertedToPaths: Int get() = activeTextElementsConvertedToPaths
val textGlyphPathsEmitted: Int get() = activeTextGlyphPathsEmitted
val textGlyphSpecificAdvances: Int get() = activeTextGlyphSpecificAdvances
val textDefaultFontAdvances: Int get() = activeTextDefaultFontAdvances
val textHorizontalKerningPairs: Int get() = activeTextHorizontalKerningPairs
val textVerticalKerningPairs: Int get() = activeTextVerticalKerningPairs
val textHorizontalKerningPairsMatched: Int get() = activeTextHorizontalKerningPairsMatched
val textVerticalKerningPairsMatched: Int get() = activeTextVerticalKerningPairsMatched
val textKerningAdjustmentsApplied: Int get() = activeTextKerningAdjustmentsApplied
val textFontFamilies: List<String> get() = activeTextFontFamilies.toList()
val textFontWeights: List<String> get() = activeTextFontWeights.toList()

private lateinit var appendElementPathCallback: (
    StringBuilder, Element, String,
    String?, String?, String?, String?, String?, String?,
    String?, String?, String?, String?, String?, String?
) -> Unit

private lateinit var appendBasicShapePathCallback: (
    StringBuilder, Element, String, String,
    String?, String?, String?, String?, String?, String?,
    String?, String?, String?, String?, String?, String?
) -> Unit

private lateinit var appendFlatPathsFallbackCallback: (StringBuilder, String, String) -> Unit
private lateinit var basicShapeToPathDataCallback: (Element, String) -> String?
private lateinit var floatAttrCallback: (Element, String) -> Float?
private lateinit var escapeXmlCallback: (String) -> String

data class MarkerPath(
    val pathData: String,
    val fill: String?,
    val stroke: String?,
    val strokeWidth: String?,
    val fillOpacity: String?,
    val strokeOpacity: String?
)

data class MarkerDefinition(
    val id: String,
    val refX: Float,
    val refY: Float,
    val markerWidth: Float,
    val markerHeight: Float,
    val viewBoxMinX: Float,
    val viewBoxMinY: Float,
    val viewBoxWidth: Float,
    val viewBoxHeight: Float,
    val orient: String,
    val markerUnits: String,
    val paths: List<MarkerPath>
)


data class PatternTilePath(
    val pathData: String,
    val fill: String?,
    val stroke: String?,
    val strokeWidth: String?,
    val fillOpacity: String?,
    val strokeOpacity: String?
)

data class PatternDefinition(
    val id: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val patternUnits: String,
    val patternTransform: AffineTransform = AffineTransform.identity(),
    val paths: List<PatternTilePath>
)

data class SvgGlyphOutline(
    val unicode: String,
    val pathData: String,
    val horizAdvX: Float? = null,
    val vertAdvY: Float? = null,
    val glyphName: String? = null
)

data class SvgKerningPair(
    val firstUnicodeValues: Set<String>,
    val secondUnicodeValues: Set<String>,
    val firstGlyphNames: Set<String>,
    val secondGlyphNames: Set<String>,
    val amount: Float
)

data class SvgFontDefinition(
    val id: String,
    val familyNames: Set<String>,
    val unitsPerEm: Float,
    val ascent: Float,
    val descent: Float,
    val horizAdvX: Float,
    val vertAdvY: Float,
    val glyphsByUnicode: Map<String, SvgGlyphOutline>,
    val glyphsByName: Map<String, SvgGlyphOutline>,
    val horizontalKerningPairs: List<SvgKerningPair> = emptyList(),
    val verticalKerningPairs: List<SvgKerningPair> = emptyList(),
    val missingGlyph: SvgGlyphOutline? = null
)

fun resetStats(
    clipPathData: Map<String, String>,
    maskPathData: Map<String, String> = emptyMap(),
    markerDefinitions: Map<String, MarkerDefinition> = emptyMap(),
    patternDefinitions: Map<String, PatternDefinition> = emptyMap(),
    svgFontDefinitions: Map<String, SvgFontDefinition> = emptyMap()
) {
    activeClipPathData = clipPathData
    activeMaskPathData = maskPathData
    activeMarkerDefinitions = markerDefinitions
    activePatternDefinitions = patternDefinitions
    activeSvgFontDefinitions = svgFontDefinitions
    activeAppliedClipPaths = 0
    activeAppliedMasks = 0
    activeResolvedUseExpansions = 0
    activeUnresolvedUseReferences = 0
    activeAppliedMarkers = 0
    activeDashedStrokesDetected = 0
    activeDashedStrokesApproximated = 0
    activeNonScalingStrokesDetected = 0
    activeNonScalingStrokesCompensated = 0
    activeNonScalingStrokesUncertain = 0
    activePatternTileExpansions = 0
    activePatternTilePathsEmitted = 0
    activeTextElementsApproximated = 0
    activeTextElementsConvertedToPaths = 0
    activeTextGlyphPathsEmitted = 0
    activeTextGlyphSpecificAdvances = 0
    activeTextDefaultFontAdvances = 0
    activeTextHorizontalKerningPairs = activeSvgFontDefinitions.values.sumOf { it.horizontalKerningPairs.size }
    activeTextVerticalKerningPairs = activeSvgFontDefinitions.values.sumOf { it.verticalKerningPairs.size }
    activeTextHorizontalKerningPairsMatched = 0
    activeTextVerticalKerningPairsMatched = 0
    activeTextKerningAdjustmentsApplied = 0
    activeMatchedHorizontalKerningPairs.clear()
    activeMatchedVerticalKerningPairs.clear()
    activeTextFontFamilies.clear()
    activeTextFontWeights.clear()
}

fun markerDefinition(id: String?): MarkerDefinition? {
    return id?.let { activeMarkerDefinitions[it] }
}

fun recordAppliedMarker() {
    activeAppliedMarkers++
}

fun recordDashedStroke(didApproximate: Boolean) {
    activeDashedStrokesDetected++
    if (didApproximate) {
        activeDashedStrokesApproximated++
    }
}

fun recordNonScalingStroke(didCompensate: Boolean, isUncertain: Boolean = false) {
    activeNonScalingStrokesDetected++
    if (didCompensate) {
        activeNonScalingStrokesCompensated++
    }
    if (isUncertain) {
        activeNonScalingStrokesUncertain++
    }
}


private fun effectiveVectorEffect(element: Element, style: String?, inheritedVectorEffect: String?): String {
    return SvgPaintResolver.styleValue(style, "vector-effect")
        ?: element.getAttribute("vector-effect").ifBlank { inheritedVectorEffect ?: "" }
}

private fun compensateNonScalingStrokeWidth(
    strokeWidth: String?,
    scaleX: Float,
    scaleY: Float
): Pair<String?, Boolean> {
    val raw = strokeWidth.orEmpty().trim()
    val numeric = raw.toFloatOrNull() ?: return Pair(strokeWidth, false)
    if (numeric <= 0f) return Pair(strokeWidth, false)

    val averageScale = ((abs(scaleX) + abs(scaleY)) / 2f).takeIf { it > 0.0001f } ?: return Pair(strokeWidth, false)
    if (nearEqual(averageScale, 1f)) return Pair(strokeWidth, true)

    return Pair(formatNumber(numeric / averageScale), true)
}

private fun isNonUniformScale(scaleX: Float, scaleY: Float): Boolean {
    return !nearEqual(abs(scaleX), abs(scaleY))
}

private fun nearEqual(a: Float, b: Float, epsilon: Float = 0.0001f): Boolean {
    return abs(a - b) <= epsilon
}

private fun formatNumber(value: Float): String {
    if (nearEqual(value, value.toInt().toFloat())) return value.toInt().toString()
    return String.format(java.util.Locale.US, "%.4f", value)
        .trimEnd('0')
        .trimEnd('.')
}


private data class ScaleEstimate(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

private fun scaleEstimateFromTransformMatrix(matrix: AffineTransform?): ScaleEstimate {
    if (matrix == null) return ScaleEstimate()

    // Estimate geometric scale from the matrix columns. This keeps non-scaling
    // stroke compensation working through nested transforms, <use> placement,
    // rotation+scale, and transforms that are flattened into child pathData.
    val xScale = kotlin.math.sqrt(matrix.a * matrix.a + matrix.b * matrix.b)
        .takeIf { it > 0.0001f } ?: 1f
    val yScale = kotlin.math.sqrt(matrix.c * matrix.c + matrix.d * matrix.d)
        .takeIf { it > 0.0001f } ?: 1f

    return ScaleEstimate(xScale, yScale)
}

private fun scaleEstimateFromTransformList(
    transforms: List<ParsedTransform>,
    transformOrigin: TransformOrigin? = null
): ScaleEstimate {
    return scaleEstimateFromTransformMatrix(
        SvgTransformParser.combineTransformListToMatrix(transforms, transformOrigin)
    )
}

private fun emitWithForcedStrokeWidth(strokeWidth: String?, block: () -> Unit) {
    val forcedStrokeWidth = strokeWidth?.trim()?.takeIf { it.isNotBlank() }
    if (forcedStrokeWidth == null) {
        block()
        return
    }

    SvgPathEmitter.pushForcedStrokeWidth(forcedStrokeWidth)
    try {
        block()
    } finally {
        SvgPathEmitter.popForcedStrokeWidth()
    }
}

fun collectClipPathData(
    svg: String,
    basicShapeToPathData: (Element, String) -> String?
): Map<String, String> {
    basicShapeToPathDataCallback = basicShapeToPathData
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
                    basicShapeToPathDataCallback(element, tag).orEmpty()
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



fun collectMarkerDefinitions(
    svg: String,
    basicShapeToPathData: (Element, String) -> String?
): Map<String, MarkerDefinition> {
    basicShapeToPathDataCallback = basicShapeToPathData
    val result = linkedMapOf<String, MarkerDefinition>()

    fun floatAttr(element: Element, name: String, fallback: Float): Float {
        return element.getAttribute(name)
            .replace("px", "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
            ?: fallback
    }

    fun markerViewBox(element: Element, markerWidth: Float, markerHeight: Float): SvgViewBox {
        val parts = element.getAttribute("viewBox")
            .trim()
            .split(Regex("[,\\s]+"))
            .mapNotNull { it.toFloatOrNull() }

        return if (parts.size >= 4 && parts[2] != 0f && parts[3] != 0f) {
            SvgViewBox(parts[0], parts[1], parts[2], parts[3])
        } else {
            SvgViewBox(0f, 0f, markerWidth.coerceAtLeast(0.001f), markerHeight.coerceAtLeast(0.001f))
        }
    }

    fun markerPaintPath(element: Element, tagName: String): MarkerPath? {
        val pathData = when (tagName) {
            "path" -> element.getAttribute("d").trim()
            "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                basicShapeToPathDataCallback(element, tagName).orEmpty()
            else -> ""
        }
        if (pathData.isBlank()) return null

        val style = element.getAttribute("style").ifBlank { null }
        return MarkerPath(
            pathData = pathData,
            fill = SvgPaintResolver.styleValue(style, "fill") ?: element.getAttribute("fill").ifBlank { null },
            stroke = SvgPaintResolver.styleValue(style, "stroke") ?: element.getAttribute("stroke").ifBlank { null },
            strokeWidth = SvgPaintResolver.styleValue(style, "stroke-width") ?: element.getAttribute("stroke-width").ifBlank { null },
            fillOpacity = SvgPaintResolver.styleValue(style, "fill-opacity") ?: element.getAttribute("fill-opacity").ifBlank { null },
            strokeOpacity = SvgPaintResolver.styleValue(style, "stroke-opacity") ?: element.getAttribute("stroke-opacity").ifBlank { null }
        )
    }

    fun collectMarkerPaths(element: Element, depth: Int = 0): List<MarkerPath> {
        if (depth > 20) return emptyList()

        val paths = mutableListOf<MarkerPath>()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            val tag = childElement.tagName.substringAfter(":").lowercase()
            when (tag) {
                "path", "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                    markerPaintPath(childElement, tag)?.let { paths.add(it) }
                "g", "defs", "symbol" -> paths.addAll(collectMarkerPaths(childElement, depth + 1))
            }
        }
        return paths
    }

    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element
            val tag = element.tagName.substringAfter(":").lowercase()

            if (tag == "marker") {
                val id = element.getAttribute("id").trim()
                val markerWidth = floatAttr(element, "markerWidth", 3f)
                val markerHeight = floatAttr(element, "markerHeight", 3f)
                val viewBox = markerViewBox(element, markerWidth, markerHeight)
                val paths = collectMarkerPaths(element)

                if (id.isNotBlank() && paths.isNotEmpty()) {
                    result[id] = MarkerDefinition(
                        id = id,
                        refX = floatAttr(element, "refX", 0f),
                        refY = floatAttr(element, "refY", 0f),
                        markerWidth = markerWidth,
                        markerHeight = markerHeight,
                        viewBoxMinX = viewBox.minX,
                        viewBoxMinY = viewBox.minY,
                        viewBoxWidth = viewBox.width,
                        viewBoxHeight = viewBox.height,
                        orient = element.getAttribute("orient").ifBlank { "0" },
                        markerUnits = element.getAttribute("markerUnits").ifBlank { "strokeWidth" },
                        paths = paths
                    )
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                visit(children.item(i))
            }
        }

        visit(document.documentElement)
    } catch (_: Exception) {
        return emptyMap()
    }

    return result
}


fun collectPatternDefinitions(
    svg: String,
    basicShapeToPathData: (Element, String) -> String?
): Map<String, PatternDefinition> {
    basicShapeToPathDataCallback = basicShapeToPathData
    val result = linkedMapOf<String, PatternDefinition>()

    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(svg)))

        fun numberAttr(element: Element, name: String, fallback: Float): Float {
            return element.getAttribute(name).trim().removeSuffix("px").toFloatOrNull() ?: fallback
        }

        fun tilePath(element: Element, tagName: String): PatternTilePath? {
            val pathData = when (tagName) {
                "path" -> element.getAttribute("d").trim()
                "rect", "circle", "ellipse", "line", "polyline", "polygon" -> basicShapeToPathDataCallback(element, tagName).orEmpty()
                else -> ""
            }
            if (pathData.isBlank()) return null
            val style = element.getAttribute("style").ifBlank { null }
            val fill = SvgPaintResolver.styleValue(style, "fill") ?: element.getAttribute("fill").ifBlank { null }
            val stroke = SvgPaintResolver.styleValue(style, "stroke") ?: element.getAttribute("stroke").ifBlank { null }
            return PatternTilePath(
                pathData = pathData,
                fill = fill,
                stroke = stroke,
                strokeWidth = SvgPaintResolver.styleValue(style, "stroke-width") ?: element.getAttribute("stroke-width").ifBlank { null },
                fillOpacity = SvgPaintResolver.styleValue(style, "fill-opacity") ?: element.getAttribute("fill-opacity").ifBlank { null },
                strokeOpacity = SvgPaintResolver.styleValue(style, "stroke-opacity") ?: element.getAttribute("stroke-opacity").ifBlank { null }
            )
        }

        fun collectTilePaths(element: Element, depth: Int = 0): List<PatternTilePath> {
            if (depth > 20) return emptyList()
            val paths = mutableListOf<PatternTilePath>()
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType != Node.ELEMENT_NODE) continue
                val childElement = child as Element
                val tag = childElement.tagName.substringAfter(":").lowercase()
                when (tag) {
                    "path", "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                        tilePath(childElement, tag)?.let { paths.add(it) }
                    "g", "defs", "symbol" -> paths.addAll(collectTilePaths(childElement, depth + 1))
                }
            }
            return paths
        }

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element
            val tag = element.tagName.substringAfter(":").lowercase()
            if (tag == "pattern") {
                val id = element.getAttribute("id").trim()
                val width = numberAttr(element, "width", 0f)
                val height = numberAttr(element, "height", 0f)
                val paths = collectTilePaths(element)
                val patternUnits = element.getAttribute("patternUnits").ifBlank { "objectBoundingBox" }
                if (id.isNotBlank() && width > 0f && height > 0f && paths.isNotEmpty()) {
                    result[id] = PatternDefinition(
                        id = id,
                        x = numberAttr(element, "x", 0f),
                        y = numberAttr(element, "y", 0f),
                        width = width,
                        height = height,
                        patternUnits = patternUnits,
                        patternTransform = SvgTransformParser.combineTransformListToMatrix(
                            SvgTransformParser.parseTransformList(element.getAttribute("patternTransform"))
                        ) ?: AffineTransform.identity(),
                        paths = paths
                    )
                }
            }
            val children = element.childNodes
            for (i in 0 until children.length) visit(children.item(i))
        }

        visit(document.documentElement)
    } catch (_: Exception) {
        return emptyMap()
    }

    return result
}

fun collectMaskPathData(
    svg: String,
    basicShapeToPathData: (Element, String) -> String?
): Map<String, String> {
    basicShapeToPathDataCallback = basicShapeToPathData
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

        fun maskElementLooksVisible(element: Element): Boolean {
            val style = element.getAttribute("style").ifBlank { null }
            val fill = SvgPaintResolver.styleValue(style, "fill")
                ?: element.getAttribute("fill").ifBlank { "white" }
            val stroke = SvgPaintResolver.styleValue(style, "stroke")
                ?: element.getAttribute("stroke").ifBlank { "" }
            val opacity = SvgPaintResolver.styleValue(style, "opacity")
                ?: element.getAttribute("opacity").ifBlank { "1" }
            val fillOpacity = SvgPaintResolver.styleValue(style, "fill-opacity")
                ?: element.getAttribute("fill-opacity").ifBlank { "1" }

            val alpha = SvgPaintResolver.combineAlpha(opacity, fillOpacity)?.toFloatOrNull() ?: 1f
            if (alpha <= 0.01f) return false

            val normalizedFill = fill.trim().lowercase()
            val normalizedStroke = stroke.trim().lowercase()
            if (normalizedFill == "none" && normalizedStroke.isBlank()) return false
            if (normalizedFill == "black" || normalizedFill == "#000" || normalizedFill == "#000000") return false
            return true
        }

        fun elementToPathData(element: Element, depth: Int = 0): String {
            if (depth > 20) return ""

            val tag = element.tagName.substringAfter(":").lowercase()

            return when (tag) {
                "path" -> if (maskElementLooksVisible(element)) element.getAttribute("d").trim() else ""
                "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                    if (maskElementLooksVisible(element)) basicShapeToPathDataCallback(element, tag).orEmpty() else ""
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

            if (tag == "mask") {
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

fun clipPathIdFromValue(value: String?): String? {
    val v = value?.trim() ?: return null
    return Regex("""url\(\s*#([^)'"\s]+)\s*\)""")
        .find(v)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

fun maskIdFromValue(value: String?): String? = clipPathIdFromValue(value)

private fun effectiveClipOrMaskValue(element: Element, style: String?, inheritedClipPath: String?): String {
    val clipPathValue = SvgPaintResolver.styleValue(style, "clip-path")
        ?: element.getAttribute("clip-path").ifBlank { "" }
    if (clipPathValue.isNotBlank()) return clipPathValue

    val maskValue = SvgPaintResolver.styleValue(style, "mask")
        ?: element.getAttribute("mask").ifBlank { "" }
    if (maskValue.isNotBlank()) return maskValue

    return inheritedClipPath ?: ""
}

fun hasClipPathData(clipPathId: String?): Boolean {
    return clipPathId != null && (activeClipPathData.containsKey(clipPathId) || activeMaskPathData.containsKey(clipPathId))
}

fun appendClipPath(output: StringBuilder, clipPathId: String?, indent: String): Boolean {
    val id = clipPathId ?: return false
    val clipData = activeClipPathData[id]
    val maskData = activeMaskPathData[id]
    val pathData = clipData ?: maskData ?: return false

    if (clipData != null) {
        activeAppliedClipPaths++
    } else {
        activeAppliedMasks++
        output.appendLine("${indent}<!-- approximated <mask> as <clip-path> -->")
    }

    output.appendLine("""${indent}<clip-path""")
    output.appendLine("""${indent}    android:pathData="${escapeXmlCallback(pathData)}"""")
    output.appendLine("""${indent}/>""")
    return true
}


fun collectSvgFontDefinitions(svg: String): Map<String, SvgFontDefinition> {
    val result = linkedMapOf<String, SvgFontDefinition>()

    fun numberAttr(element: Element, name: String, fallback: Float): Float {
        return element.getAttribute(name)
            .trim()
            .removeSuffix("px")
            .toFloatOrNull()
            ?: fallback
    }

    fun normalizedFamilyNames(raw: String): Set<String> {
        return raw.split(',')
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotBlank() }
            .toSet()
    }


    fun listAttr(element: Element, name: String): Set<String> {
        return element.getAttribute(name)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun kerningPair(element: Element): SvgKerningPair? {
        val amount = element.getAttribute("k").trim().toFloatOrNull() ?: return null
        val firstUnicode = listAttr(element, "u1")
        val secondUnicode = listAttr(element, "u2")
        val firstNames = listAttr(element, "g1")
        val secondNames = listAttr(element, "g2")
        if ((firstUnicode.isEmpty() && firstNames.isEmpty()) ||
            (secondUnicode.isEmpty() && secondNames.isEmpty())) return null
        return SvgKerningPair(
            firstUnicodeValues = firstUnicode,
            secondUnicodeValues = secondUnicode,
            firstGlyphNames = firstNames,
            secondGlyphNames = secondNames,
            amount = amount
        )
    }

    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(svg)))

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element
            val tag = element.tagName.substringAfter(":").lowercase()

            if (tag == "font") {
                val id = element.getAttribute("id").trim()
                val fontHorizAdvX = numberAttr(element, "horiz-adv-x", 1024f).coerceAtLeast(1f)
                val fontVertAdvYRaw = element.getAttribute("vert-adv-y").trim().toFloatOrNull()
                var unitsPerEm = 1000f
                var ascent = 800f
                var descent = -200f
                val familyNames = linkedSetOf<String>()
                val glyphs = linkedMapOf<String, SvgGlyphOutline>()
                val glyphsByName = linkedMapOf<String, SvgGlyphOutline>()
                val horizontalKerningPairs = mutableListOf<SvgKerningPair>()
                val verticalKerningPairs = mutableListOf<SvgKerningPair>()
                var missingGlyph: SvgGlyphOutline? = null

                val children = element.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType != Node.ELEMENT_NODE) continue
                    val childElement = child as Element
                    when (childElement.tagName.substringAfter(":").lowercase()) {
                        "font-face" -> {
                            unitsPerEm = numberAttr(childElement, "units-per-em", unitsPerEm).coerceAtLeast(1f)
                            ascent = numberAttr(childElement, "ascent", ascent)
                            descent = numberAttr(childElement, "descent", descent)
                            familyNames.addAll(normalizedFamilyNames(childElement.getAttribute("font-family")))
                        }
                        "glyph" -> {
                            val unicode = childElement.getAttribute("unicode")
                            val d = childElement.getAttribute("d").trim()
                            if (unicode.isNotEmpty() && d.isNotBlank()) {
                                val glyph = SvgGlyphOutline(
                                    unicode = unicode,
                                    pathData = d,
                                    horizAdvX = childElement.getAttribute("horiz-adv-x").trim().toFloatOrNull(),
                                    vertAdvY = childElement.getAttribute("vert-adv-y").trim().toFloatOrNull(),
                                    glyphName = childElement.getAttribute("glyph-name").trim().ifBlank { null }
                                )
                                glyphs[unicode] = glyph
                                glyph.glyphName?.let { glyphsByName[it] = glyph }
                            }
                        }
                        "missing-glyph" -> {
                            val d = childElement.getAttribute("d").trim()
                            if (d.isNotBlank()) {
                                missingGlyph = SvgGlyphOutline(
                                    unicode = "",
                                    pathData = d,
                                    horizAdvX = childElement.getAttribute("horiz-adv-x").trim().toFloatOrNull(),
                                    vertAdvY = childElement.getAttribute("vert-adv-y").trim().toFloatOrNull(),
                                    glyphName = childElement.getAttribute("glyph-name").trim().ifBlank { null }
                                )
                            }
                        }
                        "hkern" -> kerningPair(childElement)?.let { horizontalKerningPairs.add(it) }
                        "vkern" -> kerningPair(childElement)?.let { verticalKerningPairs.add(it) }
                    }
                }

                if (id.isNotBlank()) familyNames.add(id)
                if (id.isNotBlank() && glyphs.isNotEmpty()) {
                    result[id] = SvgFontDefinition(
                        id = id,
                        familyNames = familyNames,
                        unitsPerEm = unitsPerEm,
                        ascent = ascent,
                        descent = descent,
                        horizAdvX = fontHorizAdvX,
                        vertAdvY = (fontVertAdvYRaw ?: unitsPerEm).coerceAtLeast(1f),
                        glyphsByUnicode = glyphs,
                        glyphsByName = glyphsByName,
                        horizontalKerningPairs = horizontalKerningPairs,
                        verticalKerningPairs = verticalKerningPairs,
                        missingGlyph = missingGlyph
                    )
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) visit(children.item(i))
        }

        visit(document.documentElement)
    } catch (_: Exception) {
        return emptyMap()
    }

    return result
}

fun appendConvertedSvgTree(
    output: StringBuilder,
    svg: String,
    appendElementPath: (
        StringBuilder, Element, String,
        String?, String?, String?, String?, String?, String?,
        String?, String?, String?, String?, String?, String?
    ) -> Unit,
    appendBasicShapePath: (
        StringBuilder, Element, String, String,
        String?, String?, String?, String?, String?, String?,
        String?, String?, String?, String?, String?, String?
    ) -> Unit,
    appendFlatPathsFallback: (StringBuilder, String, String) -> Unit,
    basicShapeToPathData: (Element, String) -> String?,
    floatAttr: (Element, String) -> Float?,
    escapeXml: (String) -> String
) {
    appendElementPathCallback = appendElementPath
    appendBasicShapePathCallback = appendBasicShapePath
    appendFlatPathsFallbackCallback = appendFlatPathsFallback
    basicShapeToPathDataCallback = basicShapeToPathData
    floatAttrCallback = floatAttr
    escapeXmlCallback = escapeXml
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
        appendFlatPathsFallbackCallback(output, svg, "    ")
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
    val clipPathValue = effectiveClipOrMaskValue(element, style, inheritedClipPath)

    val clipId = clipPathIdFromValue(clipPathValue)
    return clipId?.takeIf { activeClipPathData.containsKey(it) || activeMaskPathData.containsKey(it) }
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
    activeClipPathId: String?,
    inheritedScaleX: Float = 1f,
    inheritedScaleY: Float = 1f,
    inheritedVectorEffect: String? = null
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
                    clipId,
                    inheritedScaleX,
                    inheritedScaleY,
                    inheritedVectorEffect
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
                activeClipPathId,
                inheritedScaleX,
                inheritedScaleY,
                inheritedVectorEffect
            )
            i++
        }
    }
}


private fun inverseAffineTransform(matrix: AffineTransform): AffineTransform? {
    val determinant = matrix.a * matrix.d - matrix.b * matrix.c
    if (kotlin.math.abs(determinant) < 0.000001f) return null

    val inverseA = matrix.d / determinant
    val inverseB = -matrix.b / determinant
    val inverseC = -matrix.c / determinant
    val inverseD = matrix.a / determinant
    val inverseE = (matrix.c * matrix.f - matrix.d * matrix.e) / determinant
    val inverseF = (matrix.b * matrix.e - matrix.a * matrix.f) / determinant

    return AffineTransform(
        a = inverseA,
        b = inverseB,
        c = inverseC,
        d = inverseD,
        e = inverseE,
        f = inverseF
    )
}

private fun SimpleBounds.transformedBy(matrix: AffineTransform): SimpleBounds {
    val points = listOf(
        matrix.mapPoint(minX, minY),
        matrix.mapPoint(maxX, minY),
        matrix.mapPoint(maxX, maxY),
        matrix.mapPoint(minX, maxY)
    )
    val xs = points.map { it.first }
    val ys = points.map { it.second }
    return SimpleBounds(
        minX = xs.minOrNull() ?: minX,
        minY = ys.minOrNull() ?: minY,
        maxX = xs.maxOrNull() ?: maxX,
        maxY = ys.maxOrNull() ?: maxY
    )
}

private fun patternIdFromPaint(value: String?): String? {
    val raw = value?.trim() ?: return null
    return Regex("""url\(\s*#([^)'"\s]+)\s*\)""")
        .find(raw)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private data class SimpleBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = (maxX - minX).coerceAtLeast(0.001f)
    val height: Float get() = (maxY - minY).coerceAtLeast(0.001f)

    fun intersects(other: SimpleBounds): Boolean {
        return maxX > other.minX && minX < other.maxX && maxY > other.minY && minY < other.maxY
    }
}

private fun approximateBoundsFromPathData(pathData: String): SimpleBounds? {
    val nums = Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        .findAll(pathData)
        .mapNotNull { it.value.toFloatOrNull() }
        .toList()
    if (nums.size < 2) return null
    val xs = mutableListOf<Float>()
    val ys = mutableListOf<Float>()
    var i = 0
    while (i + 1 < nums.size) {
        xs.add(nums[i])
        ys.add(nums[i + 1])
        i += 2
    }
    if (xs.isEmpty() || ys.isEmpty()) return null
    return SimpleBounds(xs.minOrNull() ?: return null, ys.minOrNull() ?: return null, xs.maxOrNull() ?: return null, ys.maxOrNull() ?: return null)
}

private fun appendPatternFillApproximation(
    output: StringBuilder,
    targetPathData: String,
    rawFill: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    indent: String
): Boolean {
    val patternId = patternIdFromPaint(rawFill) ?: return false
    val pattern = activePatternDefinitions[patternId] ?: return false
    val bounds = approximateBoundsFromPathData(targetPathData) ?: return false
    if (!pattern.patternUnits.equals("userSpaceOnUse", ignoreCase = true)) return false

    val patternSpaceBounds = inverseAffineTransform(pattern.patternTransform)
        ?.let { bounds.transformedBy(it) }
        ?: bounds

    val startX = kotlin.math.floor(((patternSpaceBounds.minX - pattern.x) / pattern.width).toDouble()).toInt() * pattern.width + pattern.x
    val startY = kotlin.math.floor(((patternSpaceBounds.minY - pattern.y) / pattern.height).toDouble()).toInt() * pattern.height + pattern.y
    val endX = patternSpaceBounds.maxX
    val endY = patternSpaceBounds.maxY
    var emitted = 0
    val maxTilePaths = 600
    val tileBoundsCache = pattern.paths.map { tile ->
        tile to approximateBoundsFromPathData(tile.pathData)
    }

    output.appendLine("${indent}<group>")
    output.appendLine("${indent}    <!-- pattern #$patternId approximated by repeated tile paths -->")
    output.appendLine("${indent}    <clip-path")
    output.appendLine("${indent}        android:pathData=\"${escapeXmlCallback(targetPathData)}\"")
    output.appendLine("${indent}    />")

    var y = startY
    while (y <= endY && emitted < maxTilePaths) {
        var x = startX
        while (x <= endX && emitted < maxTilePaths) {
            tileBoundsCache.forEach { (tile, localBounds) ->
                if (emitted >= maxTilePaths) return@forEach
                val tileTransform = pattern.patternTransform.multiply(AffineTransform(e = x, f = y))
                val transformedBounds = localBounds?.transformedBy(tileTransform)
                if (transformedBounds != null && !transformedBounds.intersects(bounds)) {
                    return@forEach
                }

                val translated = SvgPathDataTransformer.applyAffineTransform(
                    tile.pathData,
                    tileTransform
                ) ?: tile.pathData
                val tileBounds = approximateBoundsFromPathData(translated)
                if (tileBounds == null || tileBounds.intersects(bounds)) {
                    val fill = SvgPaintResolver.safeFillColor(tile.fill ?: "#000000")
                    val stroke = SvgPaintResolver.safeStrokeColor(tile.stroke)
                    SvgPathEmitter.appendRawPathForPatternTile(
                        output = output,
                        d = translated,
                        fill = fill,
                        stroke = stroke,
                        strokeWidth = tile.strokeWidth,
                        fillAlpha = SvgPaintResolver.combineAlpha(inheritedOpacity, tile.fillOpacity ?: inheritedFillOpacity),
                        strokeAlpha = SvgPaintResolver.combineAlpha(inheritedOpacity, tile.strokeOpacity),
                        indent = indent + "    "
                    )
                    emitted++
                }
            }
            x += pattern.width
        }
        y += pattern.height
    }

    output.appendLine("${indent}</group>")
    output.appendLine()
    activePatternTileExpansions++
    activePatternTilePathsEmitted += emitted
    return true
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
    activeClipPathId: String? = null,
    inheritedScaleX: Float = 1f,
    inheritedScaleY: Float = 1f,
    inheritedVectorEffect: String? = null
) {
    if (node.nodeType != Node.ELEMENT_NODE) return

    val element = node as Element
val style = element.getAttribute("style").ifBlank { null }

val currentFill = SvgPaintResolver.styleValue(style, "fill")
    ?: element.getAttribute("fill").ifBlank { inheritedFill ?: "" }

val currentStroke = SvgPaintResolver.styleValue(style, "stroke")
    ?: element.getAttribute("stroke").ifBlank { inheritedStroke ?: "" }

val currentStrokeWidth = SvgPaintResolver.styleValue(style, "stroke-width")
    ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

val currentVectorEffect = effectiveVectorEffect(element, style, inheritedVectorEffect)

val currentStrokeLineCap = SvgPaintResolver.styleValue(style, "stroke-linecap")
    ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

val currentStrokeLineJoin = SvgPaintResolver.styleValue(style, "stroke-linejoin")
    ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

val currentStrokeMiterLimit = SvgPaintResolver.styleValue(style, "stroke-miterlimit")
    ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

val currentFillRule = SvgPaintResolver.styleValue(style, "fill-rule")
    ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

val currentOpacity = SvgPaintResolver.inheritedOpacity(
    inheritedOpacity,
    SvgPaintResolver.styleValue(style, "opacity")
        ?: element.getAttribute("opacity").ifBlank { "" }
)

val currentFillOpacity = SvgPaintResolver.inheritedPaintOpacity(
    inheritedFillOpacity,
    SvgPaintResolver.styleValue(style, "fill-opacity")
        ?: element.getAttribute("fill-opacity").ifBlank { "" }
)

val currentStrokeOpacity = SvgPaintResolver.inheritedPaintOpacity(
    inheritedStrokeOpacity,
    SvgPaintResolver.styleValue(style, "stroke-opacity")
        ?: element.getAttribute("stroke-opacity").ifBlank { "" }
)

val currentClipPath = effectiveClipOrMaskValue(element, style, inheritedClipPath)

val currentTransformOrigin = SvgTransformParser.parseTransformOrigin(
    SvgPaintResolver.styleValue(style, "transform-origin")
        ?: element.getAttribute("transform-origin").ifBlank { "" }
)

   val tagName = element.tagName.substringAfter(":").lowercase()

    val isDrawableElement = tagName == "path" || tagName == "rect" || tagName == "circle" ||
        tagName == "ellipse" || tagName == "line" || tagName == "polyline" || tagName == "polygon"
    val hasNonScalingStroke = currentVectorEffect.trim().equals("non-scaling-stroke", ignoreCase = true)
    val strokeWidthForEmission = if (isDrawableElement && hasNonScalingStroke) {
        val (compensatedStrokeWidth, didCompensate) = compensateNonScalingStrokeWidth(
            currentStrokeWidth,
            inheritedScaleX,
            inheritedScaleY
        )
        recordNonScalingStroke(
            didCompensate = didCompensate,
            isUncertain = didCompensate && isNonUniformScale(inheritedScaleX, inheritedScaleY)
        )
        compensatedStrokeWidth
    } else {
        currentStrokeWidth
    }

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
                activeClipPathId,
                inheritedScaleX,
                inheritedScaleY,
                inheritedVectorEffect
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
                activeClipPathId,
                inheritedScaleX,
                inheritedScaleY,
                inheritedVectorEffect
            )
        }

        "g" -> {
            val transform = element.getAttribute("transform")
    .ifBlank { SvgPaintResolver.styleValue(style, "transform") ?: "" }
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

                val groupMatrix = SvgTransformParser.combineTransformListToMatrix(transforms, currentTransformOrigin)
                val combinedTransform = groupMatrix?.toAndroidGroupTransform(
                    preferredPivotX = currentTransformOrigin?.x,
                    preferredPivotY = currentTransformOrigin?.y
                )
                val flattenGroupMatrix = groupMatrix != null && combinedTransform == null
                val groupScaleEstimate = scaleEstimateFromTransformMatrix(groupMatrix)
                val childScaleX = inheritedScaleX * groupScaleEstimate.scaleX
                val childScaleY = inheritedScaleY * groupScaleEstimate.scaleY

                if (combinedTransform != null) {
                    SvgTransformParser.appendCombinedTransformGroupStart(output, combinedTransform, currentIndent)
                    currentIndent += "    "
                    openedGroupCount++
                }

                if (flattenGroupMatrix && groupMatrix != null) {
                    output.appendLine("${currentIndent}<!-- group transform flattened into child pathData -->")
                    SvgPathEmitter.pushFlattenTransform(groupMatrix)
                }

                try {
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
                        if (hasClipPath) groupClipPathId else activeClipPathId,
                        childScaleX,
                        childScaleY,
                        currentVectorEffect
                    )
                } finally {
                    if (flattenGroupMatrix) {
                        SvgPathEmitter.popFlattenTransform()
                    }
                }

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
                    activeClipPathId,
                    inheritedScaleX,
                    inheritedScaleY,
                    currentVectorEffect
                )
            }
        }

        "text" -> {
            val convertedGlyphOutlines = appendTextGlyphOutlines(
                output = output,
                element = element,
                indent = indent,
                inheritedFill = currentFill,
                inheritedStroke = currentStroke,
                inheritedStrokeWidth = strokeWidthForEmission,
                inheritedOpacity = currentOpacity,
                inheritedFillOpacity = currentFillOpacity,
                inheritedStrokeOpacity = currentStrokeOpacity
            )
            if (!convertedGlyphOutlines) {
                appendTextApproximation(
                    output = output,
                    element = element,
                    indent = indent,
                    inheritedFill = currentFill,
                    inheritedStroke = currentStroke,
                    inheritedStrokeWidth = strokeWidthForEmission,
                    inheritedOpacity = currentOpacity,
                    inheritedFillOpacity = currentFillOpacity,
                    inheritedStrokeOpacity = currentStrokeOpacity
                )
            }
        }

        "tspan", "textpath" -> {
            // These are handled as part of their parent <text> approximation.
            return
        }

        "path" -> {
            val sourcePathData = element.getAttribute("d").trim()
            if (sourcePathData.isNotBlank() && appendPatternFillApproximation(output, sourcePathData, currentFill, currentOpacity, currentFillOpacity, indent)) {
                return
            }
            emitWithForcedStrokeWidth(strokeWidthForEmission) {
                appendElementPathCallback(
                    output,
                    element,
                    indent,
                    currentFill,
                    currentStroke,
                    strokeWidthForEmission,
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
        }

        "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
            val sourcePathData = basicShapeToPathDataCallback(element, tagName)
            if (!sourcePathData.isNullOrBlank() && tagName != "line" && appendPatternFillApproximation(output, sourcePathData, currentFill, currentOpacity, currentFillOpacity, indent)) {
                return
            }
            emitWithForcedStrokeWidth(strokeWidthForEmission) {
                appendBasicShapePathCallback(
                    output,
                    element,
                    tagName,
                    indent,
                    currentFill,
                    currentStroke,
                    strokeWidthForEmission,
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
                activeClipPathId,
                inheritedScaleX,
                inheritedScaleY,
                currentVectorEffect
            )
        }
    }
}


private fun textNumericAttr(element: Element, name: String): Float? {
    val raw = element.getAttribute(name).trim()
    if (raw.isBlank()) return null
    return raw
        .split(Regex("[,\\s]+"))
        .firstOrNull()
        ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
        ?.toFloatOrNull()
}

private fun textStyleValue(element: Element, style: String?, name: String): String? {
    return SvgPaintResolver.styleValue(style, name)
        ?: element.getAttribute(name).ifBlank { null }
}

private fun inheritedTextStyleValue(element: Element, name: String): String? {
    var current: Node? = element
    while (current is Element) {
        val currentStyle = current.getAttribute("style").ifBlank { null }
        val value = textStyleValue(current, currentStyle, name)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("inherit", ignoreCase = true) }
        if (value != null) return value
        current = current.parentNode
    }
    return null
}

private fun normalizedTextAnchor(element: Element): String {
    return inheritedTextStyleValue(element, "text-anchor")
        ?.lowercase()
        ?.takeIf { it == "start" || it == "middle" || it == "end" }
        ?: "start"
}

private fun isVerticalWritingMode(element: Element): Boolean {
    val value = inheritedTextStyleValue(element, "writing-mode")
        ?.trim()
        ?.lowercase()
        ?: return false

    return value == "tb" ||
        value == "tb-rl" ||
        value == "vertical-rl" ||
        value == "vertical-lr"
}

private fun normalizedDominantBaseline(element: Element): String {
    return (inheritedTextStyleValue(element, "dominant-baseline")
        ?: inheritedTextStyleValue(element, "alignment-baseline")
        ?: "alphabetic")
        .lowercase()
}

private fun textTopForBaseline(y: Float, dy: Float, height: Float, baseline: String): Float {
    val baselineY = y + dy
    return when (baseline) {
        "middle", "central", "mathematical" -> baselineY - height / 2f
        "hanging", "text-before-edge", "before-edge" -> baselineY
        "text-after-edge", "after-edge" -> baselineY - height
        "ideographic" -> baselineY - height * 0.88f
        "alphabetic", "auto", "baseline" -> baselineY - height * 0.8f
        else -> baselineY - height * 0.8f
    }
}

private fun textContentForApproximation(element: Element): String {
    fun collect(node: Node, out: StringBuilder) {
        when (node.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> out.append(node.nodeValue.orEmpty())
            Node.ELEMENT_NODE -> {
                val childElement = node as Element
                val tag = childElement.tagName.substringAfter(":").lowercase()
                if (tag == "textpath") return
                val children = childElement.childNodes
                for (i in 0 until children.length) collect(children.item(i), out)
            }
        }
    }

    val out = StringBuilder()
    collect(element, out)
    return normalizeTextWhitespace(out.toString())
}

private fun normalizeTextWhitespace(value: String): String {
    return value.replace(Regex("\\s+"), " ").trim()
}

private data class TextApproximationRun(
    val text: String,
    val element: Element
)

private fun textHasPositionAttribute(element: Element): Boolean {
    return element.hasAttribute("x") ||
        element.hasAttribute("y") ||
        element.hasAttribute("dx") ||
        element.hasAttribute("dy")
}

private fun textApproximationRuns(element: Element): List<TextApproximationRun> {
    val runs = mutableListOf<TextApproximationRun>()

    fun collectInlineRuns(owner: Element) {
        val pendingText = StringBuilder()

        fun flushPendingText() {
            val text = normalizeTextWhitespace(pendingText.toString())
            if (text.isNotBlank()) {
                runs.add(TextApproximationRun(text, owner))
            }
            pendingText.clear()
        }

        val children = owner.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    pendingText.append(child.nodeValue.orEmpty())
                }

                Node.ELEMENT_NODE -> {
                    val childElement = child as Element
                    val tag = childElement.tagName.substringAfter(":").lowercase()
                    when (tag) {
                        "tspan" -> {
                            flushPendingText()
                            collectInlineRuns(childElement)
                        }

                        "textpath" -> {
                            flushPendingText()
                        }

                        else -> {
                            flushPendingText()
                            collectInlineRuns(childElement)
                        }
                    }
                }
            }
        }

        flushPendingText()
    }

    collectInlineRuns(element)

    if (runs.isEmpty()) {
        val fallbackText = textContentForApproximation(element)
        if (fallbackText.isNotBlank()) {
            runs.add(TextApproximationRun(fallbackText, element))
        }
    }

    return runs
}


private fun normalizeTextFontWeight(raw: String?): String {
    val value = raw
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() && it != "inherit" }
        ?: return "400"

    return when (value) {
        "normal" -> "400"
        "bold" -> "700"
        "bolder" -> "700"
        "lighter" -> "300"
        else -> {
            val numeric = value.toIntOrNull()
            if (numeric != null) numeric.coerceIn(100, 900).toString() else value
        }
    }
}

private fun widthFactorForTextWeight(fontWeight: String): Float {
    val numeric = fontWeight.toIntOrNull()
    return when {
        numeric != null && numeric <= 300 -> 0.56f
        numeric == 400 || fontWeight == "normal" -> 0.60f
        numeric == 500 -> 0.61f
        numeric == 600 -> 0.62f
        numeric == 700 || fontWeight == "bold" -> 0.64f
        numeric != null && numeric >= 800 -> 0.66f
        else -> 0.60f
    }
}


private fun codePointStrings(text: String): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < text.length) {
        val cp = text.codePointAt(index)
        result.add(String(Character.toChars(cp)))
        index += Character.charCount(cp)
    }
    return result
}

private fun normalizedFontFamilyCandidates(raw: String?): List<String> {
    return raw.orEmpty()
        .split(',')
        .map { it.trim().trim('\'', '"') }
        .filter { it.isNotBlank() }
}

private fun matchingSvgFont(fontFamily: String?): SvgFontDefinition? {
    if (activeSvgFontDefinitions.isEmpty()) return null
    val candidates = normalizedFontFamilyCandidates(fontFamily)
    if (candidates.isEmpty() && activeSvgFontDefinitions.size == 1) {
        return activeSvgFontDefinitions.values.first()
    }
    for (candidate in candidates) {
        activeSvgFontDefinitions.values.firstOrNull { font ->
            font.id.equals(candidate, ignoreCase = true) ||
                font.familyNames.any { it.equals(candidate, ignoreCase = true) }
        }?.let { return it }
    }
    return null
}

private fun glyphForText(font: SvgFontDefinition, remainingText: String): Pair<SvgGlyphOutline, Int>? {
    if (remainingText.isEmpty()) return null
    val direct = font.glyphsByUnicode[remainingText.take(1)]
    var best: Pair<SvgGlyphOutline, Int>? = direct?.let { it to 1 }
    for ((unicode, glyph) in font.glyphsByUnicode) {
        if (unicode.length > (best?.second ?: 0) && remainingText.startsWith(unicode)) {
            best = glyph to unicode.length
        }
    }
    return best ?: font.missingGlyph?.let { missing ->
        val cp = remainingText.codePointAt(0)
        missing to Character.charCount(cp)
    }
}

private fun glyphAdvance(font: SvgFontDefinition, glyph: SvgGlyphOutline?, vertical: Boolean = false): Float {
    return if (vertical) {
        glyph?.vertAdvY?.takeIf { it > 0f } ?: font.vertAdvY
    } else {
        glyph?.horizAdvX?.takeIf { it > 0f } ?: font.horizAdvX
    }
}

private fun hasGlyphSpecificAdvance(glyph: SvgGlyphOutline, vertical: Boolean = false): Boolean {
    return if (vertical) {
        glyph.vertAdvY?.takeIf { it > 0f } != null
    } else {
        glyph.horizAdvX?.takeIf { it > 0f } != null
    }
}

private fun kerningMatchesValue(values: Set<String>, glyph: SvgGlyphOutline): Boolean {
    if (values.isEmpty()) return false
    if (glyph.unicode in values) return true
    return values.any { token ->
        token.length == 1 && glyph.unicode.length == 1 && token == glyph.unicode
    }
}

private fun kerningMatchesName(values: Set<String>, glyph: SvgGlyphOutline): Boolean {
    val name = glyph.glyphName ?: return false
    return name in values
}

private fun matchingKerningPair(
    font: SvgFontDefinition,
    first: SvgGlyphOutline?,
    second: SvgGlyphOutline?,
    vertical: Boolean = false
): SvgKerningPair? {
    if (first == null || second == null) return null
    val pairs = if (vertical) font.verticalKerningPairs else font.horizontalKerningPairs
    if (pairs.isEmpty()) return null
    return pairs.firstOrNull { candidate ->
        (kerningMatchesValue(candidate.firstUnicodeValues, first) || kerningMatchesName(candidate.firstGlyphNames, first)) &&
            (kerningMatchesValue(candidate.secondUnicodeValues, second) || kerningMatchesName(candidate.secondGlyphNames, second))
    }
}

private fun kerningAdjustment(
    font: SvgFontDefinition,
    first: SvgGlyphOutline?,
    second: SvgGlyphOutline?,
    vertical: Boolean = false,
    recordMatch: Boolean = false
): Float {
    val pair = matchingKerningPair(font, first, second, vertical) ?: return 0f
    if (recordMatch && abs(pair.amount) > 0.001f) {
        if (vertical) {
            activeMatchedVerticalKerningPairs.add(pair)
            activeTextVerticalKerningPairsMatched = activeMatchedVerticalKerningPairs.size
        } else {
            activeMatchedHorizontalKerningPairs.add(pair)
            activeTextHorizontalKerningPairsMatched = activeMatchedHorizontalKerningPairs.size
        }
    }
    return pair.amount
}

private fun textRunAdvance(font: SvgFontDefinition, text: String, fontSize: Float, vertical: Boolean = false): Float {
    val scale = fontSize / font.unitsPerEm
    var index = 0
    var total = 0f
    var previousGlyph: SvgGlyphOutline? = null
    while (index < text.length) {
        val match = glyphForText(font, text.substring(index))
        if (match == null) {
            val cp = text.codePointAt(index)
            total += glyphAdvance(font, null, vertical = vertical) * scale
            previousGlyph = null
            index += Character.charCount(cp)
        } else {
            val glyph = match.first
            total -= kerningAdjustment(font, previousGlyph, glyph, vertical = vertical) * scale
            total += glyphAdvance(font, glyph, vertical = vertical) * scale
            previousGlyph = glyph
            index += match.second
        }
    }
    return total
}

private fun appendTextGlyphOutlines(
    output: StringBuilder,
    element: Element,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
): Boolean {
    val runs = textApproximationRuns(element)
    if (runs.isEmpty() || activeSvgFontDefinitions.isEmpty()) return false

    data class GlyphTextRun(
        val run: TextApproximationRun,
        val font: SvgFontDefinition,
        val fontSize: Float,
        val fontFamily: String,
        val fontWeight: String,
        val advance: Float,
        val fill: String,
        val stroke: String?,
        val strokeWidth: String?,
        val opacity: String?,
        val fillOpacity: String?,
        val strokeOpacity: String?,
        val anchor: String,
        val baseline: String,
        val vertical: Boolean,
        val explicitX: Float?,
        val explicitY: Float?,
        val dx: Float,
        val dy: Float,
        val sourceTag: String
    )

    val preparedRuns = runs.mapNotNull { run ->
        val runStyle = run.element.getAttribute("style").ifBlank { null }
        val fontSize = (inheritedTextStyleValue(run.element, "font-size")
            ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
            ?.toFloatOrNull()
            ?: 16f).coerceAtLeast(1f)
        val fontFamily = inheritedTextStyleValue(run.element, "font-family").orEmpty()
        val font = matchingSvgFont(fontFamily) ?: return@mapNotNull null
        val fontWeight = normalizeTextFontWeight(inheritedTextStyleValue(run.element, "font-weight"))
        val vertical = isVerticalWritingMode(run.element)
        val isParentTextRun = run.element === element
        GlyphTextRun(
            run = run,
            font = font,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            advance = textRunAdvance(font, run.text, fontSize, vertical = vertical).coerceAtLeast(fontSize * 0.15f),
            fill = inheritedTextStyleValue(run.element, "fill") ?: inheritedFill ?: "#000000",
            stroke = inheritedTextStyleValue(run.element, "stroke")
                ?: inheritedStroke?.trim()?.takeIf { it.isNotBlank() },
            strokeWidth = inheritedTextStyleValue(run.element, "stroke-width")
                ?: inheritedStrokeWidth?.trim()?.takeIf { it.isNotBlank() },
            opacity = SvgPaintResolver.inheritedOpacity(inheritedOpacity, inheritedTextStyleValue(run.element, "opacity") ?: ""),
            fillOpacity = SvgPaintResolver.inheritedPaintOpacity(inheritedFillOpacity, inheritedTextStyleValue(run.element, "fill-opacity") ?: ""),
            strokeOpacity = SvgPaintResolver.inheritedPaintOpacity(inheritedStrokeOpacity, inheritedTextStyleValue(run.element, "stroke-opacity") ?: ""),
            anchor = normalizedTextAnchor(run.element),
            baseline = normalizedDominantBaseline(run.element),
            vertical = vertical,
            explicitX = if (!isParentTextRun && run.element.hasAttribute("x")) textNumericAttr(run.element, "x") else null,
            explicitY = if (!isParentTextRun && run.element.hasAttribute("y")) textNumericAttr(run.element, "y") else null,
            dx = if (!isParentTextRun) textNumericAttr(run.element, "dx") ?: 0f else 0f,
            dy = if (!isParentTextRun) textNumericAttr(run.element, "dy") ?: 0f else 0f,
            sourceTag = run.element.tagName.substringAfter(":").lowercase()
        )
    }

    if (preparedRuns.size != runs.size || preparedRuns.isEmpty()) return false

    val textStyle = element.getAttribute("style").ifBlank { null }
    val baseX = textNumericAttr(element, "x") ?: 0f
    val baseY = textNumericAttr(element, "y") ?: 0f
    val baseDx = textNumericAttr(element, "dx") ?: 0f
    val baseDy = textNumericAttr(element, "dy") ?: 0f
    val baseAnchor = normalizedTextAnchor(element)
    val baseVertical = isVerticalWritingMode(element)
    val hasPositionedRuns = runs.any { it.element !== element && textHasPositionAttribute(it.element) }
    val totalAdvance = preparedRuns.sumOf { it.advance.toDouble() }.toFloat()

    var currentX = if (baseVertical) {
        baseX + baseDx
    } else {
        when (baseAnchor) {
            "middle" -> baseX + baseDx - totalAdvance / 2f
            "end" -> baseX + baseDx - totalAdvance
            else -> baseX + baseDx
        }
    }
    var currentY = if (baseVertical) {
        when (baseAnchor) {
            "middle" -> baseY + baseDy - totalAdvance / 2f
            "end" -> baseY + baseDy - totalAdvance
            else -> baseY + baseDy
        }
    } else {
        baseY + baseDy
    }
    var emittedGlyphs = 0

    val elementTransform = element.getAttribute("transform")
        .ifBlank { SvgPaintResolver.styleValue(textStyle, "transform") ?: "" }
    val transformOrigin = SvgTransformParser.parseTransformOrigin(
        SvgPaintResolver.styleValue(textStyle, "transform-origin")
            ?: element.getAttribute("transform-origin").ifBlank { "" }
    )
    val elementMatrix = SvgTransformParser.combineTransformListToMatrix(
        SvgTransformParser.parseTransformList(elementTransform),
        transformOrigin
    )

    var emittedGlyphComment = false

    for (prepared in preparedRuns) {
        if (hasPositionedRuns) {
            if (prepared.explicitX != null) currentX = prepared.explicitX
            if (prepared.explicitY != null) currentY = prepared.explicitY
            currentX += prepared.dx
            currentY += prepared.dy
            if (prepared.vertical) {
                currentY = when (prepared.anchor) {
                    "middle" -> currentY - prepared.advance / 2f
                    "end" -> currentY - prepared.advance
                    else -> currentY
                }
            } else {
                currentX = when (prepared.anchor) {
                    "middle" -> currentX - prepared.advance / 2f
                    "end" -> currentX - prepared.advance
                    else -> currentX
                }
            }
        }

        if (prepared.fontFamily.isNotBlank()) activeTextFontFamilies.add(prepared.fontFamily)
        if (prepared.fontWeight.isNotBlank()) activeTextFontWeights.add(prepared.fontWeight)

        val scale = prepared.fontSize / prepared.font.unitsPerEm
        var index = 0
        while (index < prepared.run.text.length) {
            val match = glyphForText(prepared.font, prepared.run.text.substring(index))
            if (match == null) {
                val cp = prepared.run.text.codePointAt(index)
                if (prepared.vertical) {
                    currentY += glyphAdvance(prepared.font, null, vertical = true) * scale
                } else {
                    currentX += glyphAdvance(prepared.font, null, vertical = false) * scale
                }
                index += Character.charCount(cp)
                continue
            }

            val glyph = match.first
            val placement = AffineTransform(
                a = scale,
                b = 0f,
                c = 0f,
                d = -scale,
                e = currentX,
                f = currentY
            )
            var pathData = SvgPathDataTransformer.applyAffineTransform(glyph.pathData, placement) ?: glyph.pathData
            if (elementMatrix != null) {
                pathData = SvgPathDataTransformer.applyAffineTransform(pathData, elementMatrix) ?: pathData
            }
            pathData = SvgPathEmitter.applyCurrentFlattenTransform(pathData)

            if (!emittedGlyphComment) {
                output.appendLine("${indent}<!-- converted text to glyph outline paths from embedded SVG font -->")
                emittedGlyphComment = true
            }

            val safeFill = SvgPaintResolver.safeFillColor(prepared.fill)
            val safeStroke = SvgPaintResolver.safeStrokeColor(prepared.stroke)
            val fillAlpha = SvgPaintResolver.combineAlpha(prepared.opacity, prepared.fillOpacity)
            val strokeAlpha = SvgPaintResolver.combineAlpha(prepared.opacity, prepared.strokeOpacity)

            output.appendLine("${indent}<path")
            output.appendLine("${indent}    android:pathData=\"${escapeXmlCallback(pathData)}\"")
            output.appendLine("${indent}    android:fillColor=\"$safeFill\"")
            if (fillAlpha != null) output.appendLine("${indent}    android:fillAlpha=\"$fillAlpha\"")
            if (safeStroke != null) {
                output.appendLine("${indent}    android:strokeColor=\"$safeStroke\"")
                output.appendLine("${indent}    android:strokeWidth=\"${prepared.strokeWidth?.takeIf { it.isNotBlank() } ?: "1"}\"")
                if (strokeAlpha != null) output.appendLine("${indent}    android:strokeAlpha=\"$strokeAlpha\"")
            }
            output.appendLine("${indent}/>")

            if (hasGlyphSpecificAdvance(glyph, vertical = prepared.vertical)) activeTextGlyphSpecificAdvances++ else activeTextDefaultFontAdvances++

            val nextStart = index + match.second
            val nextGlyph = if (nextStart < prepared.run.text.length) {
                glyphForText(prepared.font, prepared.run.text.substring(nextStart))?.first
            } else null
            val kern = kerningAdjustment(prepared.font, glyph, nextGlyph, vertical = prepared.vertical, recordMatch = true)
            if (abs(kern) > 0.001f) activeTextKerningAdjustmentsApplied++
            val advance = (glyphAdvance(prepared.font, glyph, vertical = prepared.vertical) - kern) * scale
            if (prepared.vertical) {
                currentY += advance
            } else {
                currentX += advance
            }
            index += match.second
            emittedGlyphs++
        }
    }

    if (emittedGlyphs == 0) return false
    output.appendLine()
    activeTextElementsConvertedToPaths++
    activeTextGlyphPathsEmitted += emittedGlyphs
    return true
}

private fun appendTextApproximation(
    output: StringBuilder,
    element: Element,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
): Boolean {
    val runs = textApproximationRuns(element)
    if (runs.isEmpty()) return false

    val textStyle = element.getAttribute("style").ifBlank { null }
    val baseX = textNumericAttr(element, "x") ?: 0f
    val baseY = textNumericAttr(element, "y") ?: 0f
    val baseDx = textNumericAttr(element, "dx") ?: 0f
    val baseDy = textNumericAttr(element, "dy") ?: 0f
    val anchor = normalizedTextAnchor(element)
    val baseline = normalizedDominantBaseline(element)
    val hasPositionedRuns = runs.any { it.element !== element && textHasPositionAttribute(it.element) }

    data class PreparedTextRun(
        val run: TextApproximationRun,
        val fontSize: Float,
        val fontFamily: String,
        val fontWeight: String,
        val width: Float,
        val height: Float,
        val rawFill: String,
        val anchor: String,
        val baseline: String,
        val explicitX: Float?,
        val explicitY: Float?,
        val dx: Float,
        val dy: Float,
        val sourceTag: String
    )

    val preparedRuns = runs.map { run ->
        val runStyle = run.element.getAttribute("style").ifBlank { null }
        val fontSize = (inheritedTextStyleValue(run.element, "font-size")
            ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
            ?.toFloatOrNull()
            ?: 16f).coerceAtLeast(1f)

        val fontFamily = inheritedTextStyleValue(run.element, "font-family").orEmpty()
        val fontWeight = normalizeTextFontWeight(inheritedTextStyleValue(run.element, "font-weight"))
        val widthFactor = widthFactorForTextWeight(fontWeight)
        val charCount = run.text.codePointCount(0, run.text.length).coerceAtLeast(1)
        val textLength = textStyleValue(run.element, runStyle, "textLength")
            ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
            ?.toFloatOrNull()
        val width = (textLength ?: (charCount * fontSize * widthFactor)).coerceAtLeast(fontSize * 0.35f)
        val rawFill = inheritedTextStyleValue(run.element, "fill") ?: inheritedFill ?: "#000000"

        val isParentTextRun = run.element === element

        PreparedTextRun(
            run = run,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            width = width,
            height = fontSize,
            rawFill = rawFill,
            anchor = normalizedTextAnchor(run.element),
            baseline = normalizedDominantBaseline(run.element),
            explicitX = if (!isParentTextRun && run.element.hasAttribute("x")) textNumericAttr(run.element, "x") else null,
            explicitY = if (!isParentTextRun && run.element.hasAttribute("y")) textNumericAttr(run.element, "y") else null,
            dx = if (!isParentTextRun) textNumericAttr(run.element, "dx") ?: 0f else 0f,
            dy = if (!isParentTextRun) textNumericAttr(run.element, "dy") ?: 0f else 0f,
            sourceTag = run.element.tagName.substringAfter(":").lowercase()
        )
    }

    val totalWidth = preparedRuns.sumOf { it.width.toDouble() }.toFloat()
    var currentLeft = when (anchor) {
        "middle" -> baseX + baseDx - totalWidth / 2f
        "end" -> baseX + baseDx - totalWidth
        else -> baseX + baseDx
    }
    var currentX = baseX + baseDx
    var currentY = baseY + baseDy

    fun leftForAnchor(anchorValue: String, x: Float, width: Float): Float {
        return when (anchorValue) {
            "middle" -> x - width / 2f
            "end" -> x - width
            else -> x
        }
    }

    var emitted = false

    for (prepared in preparedRuns) {
        val run = prepared.run
        val runStyle = run.element.getAttribute("style").ifBlank { null }

        val left: Float
        val top: Float
        val resolvedTextX: Float
        val resolvedTextY: Float
        val positionMode: String
        if (hasPositionedRuns) {
            if (prepared.explicitX != null) currentX = prepared.explicitX
            if (prepared.explicitY != null) currentY = prepared.explicitY
            currentX += prepared.dx
            currentY += prepared.dy

            resolvedTextX = currentX
            resolvedTextY = currentY
            left = leftForAnchor(prepared.anchor, currentX, prepared.width)
            top = textTopForBaseline(currentY, 0f, prepared.height, prepared.baseline)
            currentX += prepared.width
            positionMode = "cursor"
        } else {
            left = currentLeft
            top = textTopForBaseline(baseY, baseDy, prepared.height, baseline)
            resolvedTextX = when (prepared.anchor) {
                "middle" -> left + prepared.width / 2f
                "end" -> left + prepared.width
                else -> left
            }
            resolvedTextY = baseY + baseDy
            currentLeft += prepared.width
            positionMode = "inline"
        }

        val right = left + prepared.width
        val bottom = top + prepared.height

        var pathData = "M ${formatNumber(left)},${formatNumber(top)} L ${formatNumber(right)},${formatNumber(top)} L ${formatNumber(right)},${formatNumber(bottom)} L ${formatNumber(left)},${formatNumber(bottom)} Z"

        val transform = element.getAttribute("transform")
            .ifBlank { SvgPaintResolver.styleValue(textStyle, "transform") ?: "" }
        val transformOrigin = SvgTransformParser.parseTransformOrigin(
            SvgPaintResolver.styleValue(textStyle, "transform-origin")
                ?: element.getAttribute("transform-origin").ifBlank { "" }
        )
        val elementMatrix = SvgTransformParser.combineTransformListToMatrix(
            SvgTransformParser.parseTransformList(transform),
            transformOrigin
        )
        if (elementMatrix != null) {
            pathData = SvgPathDataTransformer.applyAffineTransform(pathData, elementMatrix) ?: pathData
        }
        pathData = SvgPathEmitter.applyCurrentFlattenTransform(pathData)

        val outlineColor = SvgPaintResolver.safeFillColor(prepared.rawFill)
        val opacity = SvgPaintResolver.inheritedOpacity(
            inheritedOpacity,
            inheritedTextStyleValue(run.element, "opacity") ?: ""
        )
        val fillOpacity = SvgPaintResolver.inheritedPaintOpacity(
            inheritedFillOpacity,
            inheritedTextStyleValue(run.element, "fill-opacity") ?: ""
        )
        val outlineAlpha = SvgPaintResolver.combineAlpha(opacity, fillOpacity)

        if (prepared.fontFamily.isNotBlank()) activeTextFontFamilies.add(prepared.fontFamily)
        if (prepared.fontWeight.isNotBlank()) activeTextFontWeights.add(prepared.fontWeight)

        output.appendLine("${indent}<!-- text approximation:")
        output.appendLine("${indent}     \"${escapeXmlCallback(run.text)}\"")
        output.appendLine("${indent}     source=\"<${prepared.sourceTag}>\"")
        output.appendLine("${indent}     approximation=\"bounding-box placeholder\"")
        output.appendLine("${indent}     position-mode=\"$positionMode\"")
        output.appendLine("${indent}     resolved-x=\"${formatNumber(resolvedTextX)}\"")
        output.appendLine("${indent}     resolved-y=\"${formatNumber(resolvedTextY)}\"")
        output.appendLine("${indent}     font-size=\"${formatNumber(prepared.fontSize)}\"")
        output.appendLine("${indent}     text-anchor=\"${prepared.anchor}\"")
        output.appendLine("${indent}     dominant-baseline=\"${prepared.baseline}\"")
        if (prepared.explicitX != null) output.appendLine("${indent}     x=\"${formatNumber(prepared.explicitX)}\"")
        if (prepared.explicitY != null) output.appendLine("${indent}     y=\"${formatNumber(prepared.explicitY)}\"")
        if (prepared.dx != 0f) output.appendLine("${indent}     dx=\"${formatNumber(prepared.dx)}\"")
        if (prepared.dy != 0f) output.appendLine("${indent}     dy=\"${formatNumber(prepared.dy)}\"")
        if (prepared.fontFamily.isNotBlank()) {
            output.appendLine("${indent}     font-family=\"${escapeXmlCallback(prepared.fontFamily)}\"")
        }
        if (prepared.fontWeight.isNotBlank()) {
            output.appendLine("${indent}     font-weight=\"${escapeXmlCallback(prepared.fontWeight)}\"")
        }
        output.appendLine("${indent}-->")
        output.appendLine("${indent}<path")
        output.appendLine("${indent}    android:pathData=\"${escapeXmlCallback(pathData)}\"")
        output.appendLine("${indent}    android:fillColor=\"@android:color/transparent\"")
        output.appendLine("${indent}    android:strokeColor=\"$outlineColor\"")
        output.appendLine("${indent}    android:strokeWidth=\"1\"")
        if (outlineAlpha != null) output.appendLine("${indent}    android:strokeAlpha=\"$outlineAlpha\"")
        output.appendLine("${indent}/>")
        output.appendLine()
        activeTextElementsApproximated++
        emitted = true
    }

    return emitted
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


private fun explicitUsePaintValue(element: Element, style: String?, name: String): String? {
    return SvgPaintResolver.styleValue(style, name)
        ?: element.getAttribute(name).ifBlank { null }
}

private fun cloneReferencedElementWithUsePaintOverrides(
    referenced: Element,
    paintOverrides: Map<String, String>
): Element {
    if (paintOverrides.isEmpty()) return referenced

    val clone = referenced.cloneNode(true) as Element
    val overrideStyle = paintOverrides.entries.joinToString("; ") { (name, value) ->
        "$name: $value"
    }

    fun applyOverrides(node: Node) {
        if (node.nodeType != Node.ELEMENT_NODE) return

        val element = node as Element
        val existingStyle = element.getAttribute("style").trim()
        element.setAttribute(
            "style",
            if (existingStyle.isBlank()) "$overrideStyle;" else "$overrideStyle; $existingStyle"
        )

        val children = element.childNodes
        for (i in 0 until children.length) {
            applyOverrides(children.item(i))
        }
    }

    applyOverrides(clone)
    return clone
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
    activeClipPathId: String? = null,
    inheritedScaleX: Float = 1f,
    inheritedScaleY: Float = 1f,
    inheritedVectorEffect: String? = null
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

    if (referencedTag == "defs" ||
        referencedTag == "clippath" ||
        referencedTag == "lineargradient" ||
        referencedTag == "radialgradient" ||
        referencedTag == "mask" ||
        referencedTag == "filter" ||
        referencedTag == "pattern"
    ) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use href=\"#$id\">: referenced <$referencedTag> is not drawable -->")
        return
    }

    activeResolvedUseExpansions++

    val style = element.getAttribute("style").ifBlank { null }
    val useVectorEffect = effectiveVectorEffect(element, style, inheritedVectorEffect)

    val useFill = SvgPaintResolver.styleValue(style, "fill")
        ?: element.getAttribute("fill").ifBlank { inheritedFill ?: "" }

    val useStroke = SvgPaintResolver.styleValue(style, "stroke")
        ?: element.getAttribute("stroke").ifBlank { inheritedStroke ?: "" }

    val useStrokeWidth = SvgPaintResolver.styleValue(style, "stroke-width")
        ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

    val useStrokeLineCap = SvgPaintResolver.styleValue(style, "stroke-linecap")
        ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

    val useStrokeLineJoin = SvgPaintResolver.styleValue(style, "stroke-linejoin")
        ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

    val useStrokeMiterLimit = SvgPaintResolver.styleValue(style, "stroke-miterlimit")
        ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

    val useFillRule = SvgPaintResolver.styleValue(style, "fill-rule")
        ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

    // The <use> element has already had its own opacity/fill-opacity/stroke-opacity
    // folded into these inherited values by walkSvgNode(). Do not read the same
    // attributes again here, or element-level <use opacity="..."> gets multiplied twice.
    val useOpacity = inheritedOpacity ?: ""
    val useFillOpacity = inheritedFillOpacity ?: ""
    val useStrokeOpacity = inheritedStrokeOpacity ?: ""

    val useClipPath = effectiveClipOrMaskValue(element, style, inheritedClipPath)

    val usePaintOverrides = linkedMapOf<String, String>()
    listOf(
        "fill",
        "stroke",
        "stroke-width",
        "stroke-linecap",
        "stroke-linejoin",
        "stroke-miterlimit",
        "fill-rule"
    ).forEach { name ->
        explicitUsePaintValue(element, style, name)?.let { value ->
            usePaintOverrides[name] = value
        }
    }

    val referencedForUse = cloneReferencedElementWithUsePaintOverrides(referenced, usePaintOverrides)

    val transform = element.getAttribute("transform")
        .ifBlank { SvgPaintResolver.styleValue(style, "transform") ?: "" }

    val transformOrigin = SvgTransformParser.parseTransformOrigin(
        element.getAttribute("transform-origin")
            .ifBlank { SvgPaintResolver.styleValue(style, "transform-origin") ?: "" }
    )

    val referencedViewBox =
        if (referencedTag == "symbol" || referencedTag == "svg") elementViewBox(referenced) else null

    val useWidth = floatAttrCallback(element, "width")
    val useHeight = floatAttrCallback(element, "height")

    val symbolScaleX =
        if (referencedViewBox != null && useWidth != null) useWidth / referencedViewBox.width else 1f

    val symbolScaleY =
        if (referencedViewBox != null && useHeight != null) useHeight / referencedViewBox.height else 1f

    val x = floatAttrCallback(element, "x") ?: 0f
    val y = floatAttrCallback(element, "y") ?: 0f

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

    val placementScaleEstimate = scaleEstimateFromTransformList(placementTransforms, transformOrigin)

    val opened = SvgTransformParser.appendTransformGroupsStart(
        output,
        placementTransforms,
        indent,
        transformOrigin
    )

    val childIndent = opened.first
    val groupCount = opened.second

    val childScaleX = if (groupCount > 0) inheritedScaleX * placementScaleEstimate.scaleX else inheritedScaleX
    val childScaleY = if (groupCount > 0) inheritedScaleY * placementScaleEstimate.scaleY else inheritedScaleY

    if (placementTransforms.any { it.hasVisibleEffect() } && groupCount == 0) {
        output.appendLine("${indent}<!-- expanded from <use href=\"#$id\">; transform could not be represented -->")
    } else {
        output.appendLine("${childIndent}<!-- expanded from <use href=\"#$id\"> -->")
    }

    walkSvgNode(
        output,
        referencedForUse,
        childIndent,
        useFill,
        useStroke,
        useStrokeWidth,
        useStrokeLineCap,
        useStrokeLineJoin,
        useStrokeMiterLimit,
        useFillRule,
        useOpacity,
        useFillOpacity,
        useStrokeOpacity,
        useClipPath,
        definitions,
        useDepth + 1,
        activeClipPathId,
        childScaleX,
        childScaleY,
        useVectorEffect
    )

    SvgTransformParser.closeGroups(output, childIndent, groupCount)
    output.appendLine()
}


}
