package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import kotlin.math.abs
import kotlin.math.sqrt

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
private var activeInvalidDashArrays = 0
private var activeDashSolidFallbacks = 0
private var activeOddDashListsDuplicated = 0
private var activeInvalidDashOffsetFallbacks = 0
private var activeNonScalingStrokesDetected = 0
private var activeNonScalingStrokesCompensated = 0
private var activeNonScalingStrokesUncertain = 0
private var activePatternDefinitions: Map<String, PatternDefinition> = emptyMap()
private var activeSvgFontDefinitions: Map<String, SvgFontDefinition> = emptyMap()
private var activePatternTileExpansions = 0
private var activePatternTilePathsEmitted = 0
private var activeTextHorizontalKerningPairs = 0
private var activeTextVerticalKerningPairs = 0
private var activeDisplayNoneElementsSkipped = 0
private var activeVisibilityHiddenElementsSkipped = 0
private var activeNestedSvgViewports = 0
private var activeNestedSvgViewportClips = 0
private var activeNestedSvgPercentageViewports = 0
private var activeNestedSvgOverflowHidden = 0
private var activeNestedSvgOverflowVisible = 0
private var activeNestedSvgOverflowAuto = 0
private var activeNestedSvgOverflowScroll = 0
private var activeNestedSvgOverflowUnsupported = 0

// Complete affine transform from the current node to the root viewport.
// This must include transforms represented as Android groups as well as
// transforms flattened into pathData, because both affect non-scaling strokes.
private val effectiveTransformStack = mutableListOf(AffineTransform.identity())

private fun currentEffectiveTransform(): AffineTransform =
    effectiveTransformStack.lastOrNull() ?: AffineTransform.identity()

private fun pushEffectiveTransform(local: AffineTransform?) {
    val parent = currentEffectiveTransform()
    effectiveTransformStack.add(if (local == null) parent else parent.multiply(local))
}

private fun popEffectiveTransform() {
    if (effectiveTransformStack.size > 1) {
        effectiveTransformStack.removeAt(effectiveTransformStack.lastIndex)
    }
}

val appliedClipPaths: Int get() = activeAppliedClipPaths
val appliedMasks: Int get() = activeAppliedMasks
val maskPathCount: Int get() = activeMaskPathData.size
val resolvedUseExpansions: Int get() = activeResolvedUseExpansions
val unresolvedUseReferences: Int get() = activeUnresolvedUseReferences
val appliedMarkers: Int get() = activeAppliedMarkers
val dashedStrokesDetected: Int get() = activeDashedStrokesDetected
val dashedStrokesApproximated: Int get() = activeDashedStrokesApproximated
val invalidDashArrays: Int get() = activeInvalidDashArrays
val dashSolidFallbacks: Int get() = activeDashSolidFallbacks
val oddDashListsDuplicated: Int get() = activeOddDashListsDuplicated
val invalidDashOffsetFallbacks: Int get() = activeInvalidDashOffsetFallbacks
val nonScalingStrokesDetected: Int get() = activeNonScalingStrokesDetected
val nonScalingStrokesCompensated: Int get() = activeNonScalingStrokesCompensated
val nonScalingStrokesUncertain: Int get() = activeNonScalingStrokesUncertain
val patternTileExpansions: Int get() = activePatternTileExpansions
val patternTilePathsEmitted: Int get() = activePatternTilePathsEmitted
val textElementsApproximated: Int get() = SvgTextConverter.textElementsApproximated
val textElementsConvertedToPaths: Int get() = SvgTextConverter.textElementsConvertedToPaths
val textGlyphPathsEmitted: Int get() = SvgTextConverter.textGlyphPathsEmitted
val textGlyphSpecificAdvances: Int get() = SvgTextConverter.textGlyphSpecificAdvances
val textDefaultFontAdvances: Int get() = SvgTextConverter.textDefaultFontAdvances
val textMissingGlyphFallbacks: Int get() = SvgTextConverter.textMissingGlyphFallbacks
val textGlyphNameLookups: Int get() = SvgTextConverter.textGlyphNameLookups
val textHorizontalKerningPairs: Int get() = activeTextHorizontalKerningPairs
val textVerticalKerningPairs: Int get() = activeTextVerticalKerningPairs
val textHorizontalKerningPairsMatched: Int get() = SvgTextConverter.textHorizontalKerningPairsMatched
val textVerticalKerningPairsMatched: Int get() = SvgTextConverter.textVerticalKerningPairsMatched
val textKerningAdjustmentsApplied: Int get() = SvgTextConverter.textKerningAdjustmentsApplied
val textLengthSpacingAdjustments: Int get() = SvgTextConverter.textLengthSpacingAdjustments
val textLengthSpacingAndGlyphsAdjustments: Int get() = SvgTextConverter.textLengthSpacingAndGlyphsAdjustments
val textGlyphRotationsApplied: Int get() = SvgTextConverter.textGlyphRotationsApplied
val textLetterSpacingAdjustmentsApplied: Int get() = SvgTextConverter.textLetterSpacingAdjustmentsApplied
val textWordSpacingAdjustmentsApplied: Int get() = SvgTextConverter.textWordSpacingAdjustmentsApplied
val textDecorationPathsEmitted: Int get() = SvgTextConverter.textDecorationPathsEmitted
val textBidiRunsReordered: Int get() = SvgTextConverter.textBidiRunsReordered
val textDirections: List<String> get() = SvgTextConverter.textDirections
val textUnicodeBidiModes: List<String> get() = SvgTextConverter.textUnicodeBidiModes
val paintOrderElementsApplied: Int get() = SvgPathEmitter.paintOrderElementsApplied + SvgTextConverter.textPaintOrderElementsApplied
val textPathsConverted: Int get() = SvgTextConverter.textPathsConverted
val textPathGlyphsEmitted: Int get() = SvgTextConverter.textPathGlyphsEmitted
val textFontFamilies: List<String> get() = SvgTextConverter.textFontFamilies
val textFontWeights: List<String> get() = SvgTextConverter.textFontWeights
val displayNoneElementsSkipped: Int get() = activeDisplayNoneElementsSkipped
val visibilityHiddenElementsSkipped: Int get() = activeVisibilityHiddenElementsSkipped
val hiddenDrawableElementsSkipped: Int get() = activeDisplayNoneElementsSkipped + activeVisibilityHiddenElementsSkipped
val nestedSvgViewports: Int get() = activeNestedSvgViewports
val nestedSvgViewportClips: Int get() = activeNestedSvgViewportClips
val nestedSvgPercentageViewports: Int get() = activeNestedSvgPercentageViewports
val nestedSvgOverflowHidden: Int get() = activeNestedSvgOverflowHidden
val nestedSvgOverflowVisible: Int get() = activeNestedSvgOverflowVisible
val nestedSvgOverflowAuto: Int get() = activeNestedSvgOverflowAuto
val nestedSvgOverflowScroll: Int get() = activeNestedSvgOverflowScroll
val nestedSvgOverflowUnsupported: Int get() = activeNestedSvgOverflowUnsupported
val nestedSvgOverflowApproximated: Int get() = activeNestedSvgOverflowAuto + activeNestedSvgOverflowScroll

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
    activeInvalidDashArrays = 0
    activeDashSolidFallbacks = 0
    activeOddDashListsDuplicated = 0
    activeInvalidDashOffsetFallbacks = 0
    activeNonScalingStrokesDetected = 0
    activeNonScalingStrokesCompensated = 0
    activeNonScalingStrokesUncertain = 0
    activePatternTileExpansions = 0
    activePatternTilePathsEmitted = 0
    activeTextHorizontalKerningPairs = activeSvgFontDefinitions.values.sumOf { it.horizontalKerningPairs.size }
    activeTextVerticalKerningPairs = activeSvgFontDefinitions.values.sumOf { it.verticalKerningPairs.size }
    activeDisplayNoneElementsSkipped = 0
    activeVisibilityHiddenElementsSkipped = 0
    activeNestedSvgViewports = 0
    activeNestedSvgViewportClips = 0
    activeNestedSvgPercentageViewports = 0
    activeNestedSvgOverflowHidden = 0
    activeNestedSvgOverflowVisible = 0
    activeNestedSvgOverflowAuto = 0
    activeNestedSvgOverflowScroll = 0
    activeNestedSvgOverflowUnsupported = 0
    effectiveTransformStack.clear()
    effectiveTransformStack.add(AffineTransform.identity())
    SvgTextConverter.resetStats()
    SvgPathEmitter.resetStats()
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

fun recordDashedStrokeInvalid(solidFallback: Boolean) {
    activeDashedStrokesDetected++
    activeInvalidDashArrays++
    if (solidFallback) activeDashSolidFallbacks++
}

fun recordOddDashListDuplicated() {
    activeOddDashListsDuplicated++
}

fun recordInvalidDashOffsetFallback() {
    activeInvalidDashOffsetFallbacks++
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



private fun authoredStyleProperty(element: Element, name: String): String? {
    val style = element.getAttribute("style").ifBlank { null }
    return SvgPaintResolver.styleValue(style, name)
        ?: element.getAttribute(name).trim().ifBlank { null }
}

private fun directDisplayValue(element: Element): String =
    authoredStyleProperty(element, "display")
        ?.trim()
        ?.lowercase()
        .orEmpty()

private fun authoredVisibilityValue(element: Element): String? =
    authoredStyleProperty(element, "visibility")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() }

private fun effectiveVisibility(element: Element, inheritedVisibility: String?): String {
    return when (val authored = authoredVisibilityValue(element)) {
        null, "inherit", "unset" -> inheritedVisibility ?: "visible"
        "initial", "revert", "revert-layer" -> "visible"
        else -> authored
    }
}

private fun isVisibilityHidden(value: String?): Boolean {
    val normalized = value?.trim()?.lowercase().orEmpty()
    return normalized == "hidden" || normalized == "collapse"
}

private fun isDirectlyDisplayNone(element: Element): Boolean =
    directDisplayValue(element) == "none"

private fun countDrawableElementsInSubtree(element: Element): Int {
    var count = 0

    fun visit(node: Node) {
        if (node.nodeType != Node.ELEMENT_NODE) return
        val current = node as Element
        val tag = current.tagName.substringAfter(":").lowercase()

        if (tag == "defs" || tag == "symbol" || tag == "clippath" ||
            tag == "mask" || tag == "marker" || tag == "pattern" ||
            tag == "lineargradient" || tag == "radialgradient" || tag == "filter") {
            return
        }

        if (tag == "path" || tag == "rect" || tag == "circle" ||
            tag == "ellipse" || tag == "line" || tag == "polyline" ||
            tag == "polygon" || tag == "text" || tag == "use") {
            count++
            if (tag == "text" || tag == "use") return
        }

        val children = current.childNodes
        for (i in 0 until children.length) visit(children.item(i))
    }

    visit(element)
    return count
}

private fun recordDisplayNoneSubtree(element: Element): Boolean {
    if (!isDirectlyDisplayNone(element)) return false
    activeDisplayNoneElementsSkipped += countDrawableElementsInSubtree(element)
    return true
}

private fun recordVisibilityHiddenDrawable() {
    activeVisibilityHiddenElementsSkipped++
}


private fun overflowValue(element: Element, style: String?): String {
    return SvgPaintResolver.styleValue(style, "overflow")
        ?: element.getAttribute("overflow").trim().ifBlank { "visible" }
}

private fun appendViewportClip(
    output: StringBuilder,
    width: Float,
    height: Float,
    indent: String
) {
    val w = formatNumber(width.coerceAtLeast(0f))
    val h = formatNumber(height.coerceAtLeast(0f))
    output.appendLine("${indent}<clip-path")
    output.appendLine("${indent}    android:pathData=\"M 0,0 L $w,0 L $w,$h L 0,$h Z\"")
    output.appendLine("${indent}/>")
    activeNestedSvgViewportClips++
}

private enum class AspectRatioAlignX { MIN, MID, MAX }
private enum class AspectRatioAlignY { MIN, MID, MAX }
private enum class AspectRatioScaleMode { MEET, SLICE, NONE }

private data class PreserveAspectRatio(
    val alignX: AspectRatioAlignX = AspectRatioAlignX.MID,
    val alignY: AspectRatioAlignY = AspectRatioAlignY.MID,
    val scaleMode: AspectRatioScaleMode = AspectRatioScaleMode.MEET
)

private data class NestedSvgViewport(
    val x: Float,
    val y: Float,
    val width: Float?,
    val height: Float?,
    val viewBox: SvgViewBox?,
    val preserveAspectRatio: PreserveAspectRatio
)

private fun parsePreserveAspectRatio(element: Element): PreserveAspectRatio {
    val tokens = element.getAttribute("preserveAspectRatio")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .toMutableList()

    // SVG allows an optional leading "defer" token. It matters only when an
    // external referenced resource supplies its own value, so it can be ignored
    // for a directly rendered nested <svg> viewport.
    if (tokens.firstOrNull()?.equals("defer", ignoreCase = true) == true) {
        tokens.removeAt(0)
    }

    val alignToken = tokens.firstOrNull()?.lowercase().orEmpty()
    if (alignToken == "none") {
        return PreserveAspectRatio(scaleMode = AspectRatioScaleMode.NONE)
    }

    val alignX = when {
        alignToken.startsWith("xmin") -> AspectRatioAlignX.MIN
        alignToken.startsWith("xmax") -> AspectRatioAlignX.MAX
        else -> AspectRatioAlignX.MID
    }
    val alignY = when {
        alignToken.endsWith("ymin") -> AspectRatioAlignY.MIN
        alignToken.endsWith("ymax") -> AspectRatioAlignY.MAX
        else -> AspectRatioAlignY.MID
    }
    val scaleMode = if (tokens.drop(1).any { it.equals("slice", ignoreCase = true) }) {
        AspectRatioScaleMode.SLICE
    } else {
        AspectRatioScaleMode.MEET
    }

    return PreserveAspectRatio(alignX, alignY, scaleMode)
}

private fun isPercentageLength(element: Element, name: String): Boolean =
    element.getAttribute(name).trim().endsWith("%")

private fun resolveViewportLength(
    element: Element,
    name: String,
    percentageBasis: Float?
): Float? {
    val raw = element.getAttribute(name).trim()
    if (raw.isBlank()) return null

    if (raw.endsWith("%")) {
        val percent = raw.dropLast(1).trim().toFloatOrNull() ?: return null
        val basis = percentageBasis ?: return null
        return basis * percent / 100f
    }

    return floatAttrCallback(element, name)
}

private fun nestedSvgViewport(
    element: Element,
    parentViewportWidth: Float?,
    parentViewportHeight: Float?
): NestedSvgViewport {
    val viewBox = elementViewBox(element)
    val usesPercentage = listOf("x", "y", "width", "height")
        .any { isPercentageLength(element, it) }
    if (usesPercentage) activeNestedSvgPercentageViewports++

    val x = resolveViewportLength(element, "x", parentViewportWidth) ?: 0f
    val y = resolveViewportLength(element, "y", parentViewportHeight) ?: 0f
    val width = resolveViewportLength(element, "width", parentViewportWidth) ?: viewBox?.width
    val height = resolveViewportLength(element, "height", parentViewportHeight) ?: viewBox?.height

    return NestedSvgViewport(
        x = x,
        y = y,
        width = width,
        height = height,
        viewBox = viewBox,
        preserveAspectRatio = parsePreserveAspectRatio(element)
    )
}

private fun rootViewportDimensions(root: Element): Pair<Float?, Float?> {
    val viewBox = elementViewBox(root)
    if (viewBox != null) return viewBox.width to viewBox.height

    val width = resolveViewportLength(root, "width", null)
    val height = resolveViewportLength(root, "height", null)
    return width to height
}

private fun nestedSvgViewBoxTransform(viewport: NestedSvgViewport): AffineTransform? {
    val viewBox = viewport.viewBox ?: return null
    val width = viewport.width ?: return null
    val height = viewport.height ?: return null
    if (width <= 0f || height <= 0f || viewBox.width == 0f || viewBox.height == 0f) return null

    val rawScaleX = width / viewBox.width
    val rawScaleY = height / viewBox.height
    val aspectRatio = viewport.preserveAspectRatio

    if (aspectRatio.scaleMode == AspectRatioScaleMode.NONE) {
        return AffineTransform(
            a = rawScaleX,
            b = 0f,
            c = 0f,
            d = rawScaleY,
            e = -viewBox.minX * rawScaleX,
            f = -viewBox.minY * rawScaleY
        )
    }

    val uniformScale = when (aspectRatio.scaleMode) {
        AspectRatioScaleMode.SLICE -> maxOf(rawScaleX, rawScaleY)
        AspectRatioScaleMode.MEET -> minOf(rawScaleX, rawScaleY)
        AspectRatioScaleMode.NONE -> error("Handled above")
    }

    val renderedWidth = viewBox.width * uniformScale
    val renderedHeight = viewBox.height * uniformScale
    val remainingX = width - renderedWidth
    val remainingY = height - renderedHeight

    val alignOffsetX = when (aspectRatio.alignX) {
        AspectRatioAlignX.MIN -> 0f
        AspectRatioAlignX.MID -> remainingX / 2f
        AspectRatioAlignX.MAX -> remainingX
    }
    val alignOffsetY = when (aspectRatio.alignY) {
        AspectRatioAlignY.MIN -> 0f
        AspectRatioAlignY.MID -> remainingY / 2f
        AspectRatioAlignY.MAX -> remainingY
    }

    return AffineTransform(
        a = uniformScale,
        b = 0f,
        c = 0f,
        d = uniformScale,
        e = alignOffsetX - viewBox.minX * uniformScale,
        f = alignOffsetY - viewBox.minY * uniformScale
    )
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

    // Android VectorDrawable exposes only one strokeWidth, so non-uniform
    // scaling cannot be represented exactly. Use the geometric mean of the
    // accumulated X/Y scales as the least-biased area-preserving estimate.
    // This reduces to exact uniform-scale compensation when |scaleX| == |scaleY|.
    val effectiveScale = sqrt(abs(scaleX * scaleY))
        .takeIf { it > 0.0001f }
        ?: return Pair(strokeWidth, false)

    if (nearEqual(effectiveScale, 1f)) return Pair(strokeWidth, true)

    return Pair(formatNumber(numeric / effectiveScale), true)
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

    // Use the singular values of the 2x2 linear part rather than the lengths
    // of its columns. Their product is |det(matrix)|, so the compensation in
    // compensateNonScalingStrokeWidth() becomes:
    //
    //     sqrt(scaleX * scaleY) = sqrt(|a*d - b*c|)
    //
    // This is area-preserving for arbitrary affine transforms. In particular,
    // a pure skew has determinant 1 and therefore does not incorrectly change
    // the approximated non-scaling stroke width. Equal singular values identify
    // a similarity transform (uniform scale + rotation/reflection), which remains
    // exact; unequal values correctly trigger the existing approximation warning.
    val a = matrix.a
    val b = matrix.b
    val c = matrix.c
    val d = matrix.d

    val trace = a * a + b * b + c * c + d * d
    val determinant = a * d - b * c
    val discriminant = kotlin.math.max(0f, trace * trace - 4f * determinant * determinant)
    val root = kotlin.math.sqrt(discriminant)

    val largestEigenvalue = kotlin.math.max(0f, (trace + root) / 2f)
    val smallestEigenvalue = kotlin.math.max(0f, (trace - root) / 2f)

    val majorScale = kotlin.math.sqrt(largestEigenvalue)
    val minorScale = kotlin.math.sqrt(smallestEigenvalue)

    if (majorScale <= 0.0001f || minorScale <= 0.0001f) {
        // A singular/near-singular transform has no stable inverse stroke scale.
        // Preserve the previous safe fallback instead of emitting an extreme value.
        return ScaleEstimate()
    }

    return ScaleEstimate(majorScale, minorScale)
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
        val (rootViewportWidth, rootViewportHeight) = rootViewportDimensions(root)

        walkSvgNode(
            output = output,
            node = root,
            indent = "    ",
            definitions = definitions,
            parentViewportWidth = rootViewportWidth,
            parentViewportHeight = rootViewportHeight
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

private fun childClipPathId(
    node: Node,
    inheritedClipPath: String?,
    inheritedVisibility: String?
): String? {
    if (node.nodeType != Node.ELEMENT_NODE) return null

    val element = node as Element
    if (isDirectlyDisplayNone(element) ||
        isVisibilityHidden(effectiveVisibility(element, inheritedVisibility))
    ) return null
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
    inheritedVectorEffect: String? = null,
    inheritedVisibility: String? = null,
    parentViewportWidth: Float? = null,
    parentViewportHeight: Float? = null
) {
    val children = parent.childNodes
    var i = 0

    while (i < children.length) {
        val child = children.item(i)
        val clipId = childClipPathId(child, inheritedClipPath, inheritedVisibility)

        if (clipId != null && clipId != activeClipPathId) {
            output.appendLine("${indent}<group")
            output.appendLine("${indent}>")
            appendClipPath(output, clipId, indent + "    ")

            while (i < children.length) {
                val groupedChild = children.item(i)
                val groupedClipId = childClipPathId(groupedChild, inheritedClipPath, inheritedVisibility)
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
                    inheritedVectorEffect,
                    inheritedVisibility,
                    parentViewportWidth,
                    parentViewportHeight
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
                inheritedVectorEffect,
                inheritedVisibility,
                parentViewportWidth,
                parentViewportHeight
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
    inheritedVectorEffect: String? = null,
    inheritedVisibility: String? = null,
    parentViewportWidth: Float? = null,
    parentViewportHeight: Float? = null
) {
    if (node.nodeType != Node.ELEMENT_NODE) return

    val element = node as Element
    if (recordDisplayNoneSubtree(element)) return
    val style = element.getAttribute("style").ifBlank { null }
    val currentVisibility = effectiveVisibility(element, inheritedVisibility)

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

    val isVisibilityControlledDrawable =
        tagName == "path" || tagName == "rect" || tagName == "circle" ||
        tagName == "ellipse" || tagName == "line" || tagName == "polyline" ||
        tagName == "polygon" || tagName == "text" || tagName == "use"

    if (isVisibilityControlledDrawable && isVisibilityHidden(currentVisibility)) {
        recordVisibilityHiddenDrawable()
        return
    }

    val isDrawableElement = tagName == "path" || tagName == "rect" || tagName == "circle" ||
        tagName == "ellipse" || tagName == "line" || tagName == "polyline" || tagName == "polygon"
    val hasNonScalingStroke = currentVectorEffect.trim().equals("non-scaling-stroke", ignoreCase = true)

    // Build one complete matrix for all ancestors plus the drawable's own
    // transform. Multiplying scale estimates level by level is incorrect when
    // rotations, skews, non-uniform scales, nested viewBoxes, or <use> placement
    // are combined. Singular values must be measured from the final matrix.
    val drawableTransformValue = if (isDrawableElement) {
        element.getAttribute("transform")
            .ifBlank { SvgPaintResolver.styleValue(style, "transform") ?: "" }
    } else {
        ""
    }
    val drawableTransformMatrix = if (drawableTransformValue.isNotBlank()) {
        SvgTransformParser.combineTransformListToMatrix(
            SvgTransformParser.parseTransformList(drawableTransformValue),
            currentTransformOrigin
        )
    } else {
        null
    }
    val completeDrawableTransform = drawableTransformMatrix?.let {
        currentEffectiveTransform().multiply(it)
    } ?: currentEffectiveTransform()
    val drawableScaleEstimate = scaleEstimateFromTransformMatrix(completeDrawableTransform)
    val effectiveStrokeScaleX = drawableScaleEstimate.scaleX
    val effectiveStrokeScaleY = drawableScaleEstimate.scaleY

    val strokeWidthForEmission = if (isDrawableElement && hasNonScalingStroke) {
        val (compensatedStrokeWidth, didCompensate) = compensateNonScalingStrokeWidth(
            currentStrokeWidth,
            effectiveStrokeScaleX,
            effectiveStrokeScaleY
        )
        recordNonScalingStroke(
            didCompensate = didCompensate,
            isUncertain = didCompensate && isNonUniformScale(effectiveStrokeScaleX, effectiveStrokeScaleY)
        )
        compensatedStrokeWidth
    } else {
        currentStrokeWidth
    }

    when (tagName) {
        "svg" -> {
            val isRootSvg = element === element.ownerDocument.documentElement
            if (isRootSvg) {
                appendChildrenWithClipGrouping(
                    output, element, indent, currentFill, currentStroke, currentStrokeWidth,
                    currentStrokeLineCap, currentStrokeLineJoin, currentStrokeMiterLimit,
                    currentFillRule, currentOpacity, currentFillOpacity, currentStrokeOpacity,
                    currentClipPath, definitions, useDepth, activeClipPathId,
                    inheritedScaleX, inheritedScaleY, currentVectorEffect, currentVisibility,
                    parentViewportWidth, parentViewportHeight
                )
                return
            }

            activeNestedSvgViewports++
            val viewport = nestedSvgViewport(element, parentViewportWidth, parentViewportHeight)
            val rawOverflow = overflowValue(element, style).trim().lowercase()
            val overflow = when (rawOverflow) {
                "hidden" -> {
                    activeNestedSvgOverflowHidden++
                    "hidden"
                }
                "auto" -> {
                    activeNestedSvgOverflowAuto++
                    "auto"
                }
                "scroll" -> {
                    activeNestedSvgOverflowScroll++
                    "scroll"
                }
                "visible", "inherit", "initial", "unset", "" -> {
                    activeNestedSvgOverflowVisible++
                    "visible"
                }
                else -> {
                    activeNestedSvgOverflowUnsupported++
                    activeNestedSvgOverflowVisible++
                    "visible"
                }
            }
            val shouldClip = overflow == "hidden" || overflow == "scroll" || overflow == "auto"

            var currentIndent = indent
            var openedGroups = 0

            if (!nearEqual(viewport.x, 0f) || !nearEqual(viewport.y, 0f)) {
                output.appendLine("${currentIndent}<group")
                output.appendLine("${currentIndent}    android:translateX=\"${formatNumber(viewport.x)}\"")
                output.appendLine("${currentIndent}    android:translateY=\"${formatNumber(viewport.y)}\"")
                output.appendLine("${currentIndent}>")
                currentIndent += "    "
                openedGroups++
            }

            if (shouldClip && viewport.width != null && viewport.height != null && viewport.width > 0f && viewport.height > 0f) {
                output.appendLine("${currentIndent}<group>")
                appendViewportClip(output, viewport.width, viewport.height, currentIndent + "    ")
                currentIndent += "    "
                openedGroups++
            }

            val viewBoxMatrix = nestedSvgViewBoxTransform(viewport)
            val viewBoxGroup = viewBoxMatrix?.toAndroidGroupTransform()
            val flattenViewBox = viewBoxMatrix != null && viewBoxGroup == null
            val scaleEstimate = scaleEstimateFromTransformMatrix(viewBoxMatrix)
            val childScaleX = inheritedScaleX * scaleEstimate.scaleX
            val childScaleY = inheritedScaleY * scaleEstimate.scaleY

            if (viewBoxGroup != null) {
                SvgTransformParser.appendCombinedTransformGroupStart(output, viewBoxGroup, currentIndent)
                currentIndent += "    "
                openedGroups++
            } else if (flattenViewBox && viewBoxMatrix != null) {
                output.appendLine("${currentIndent}<!-- nested <svg> viewBox transform flattened into child pathData -->")
                SvgPathEmitter.pushFlattenTransform(viewBoxMatrix)
            }

            pushEffectiveTransform(viewBoxMatrix)
            try {
                appendChildrenWithClipGrouping(
                    output, element, currentIndent, currentFill, currentStroke, currentStrokeWidth,
                    currentStrokeLineCap, currentStrokeLineJoin, currentStrokeMiterLimit,
                    currentFillRule, currentOpacity, currentFillOpacity, currentStrokeOpacity,
                    currentClipPath, definitions, useDepth, activeClipPathId,
                    childScaleX, childScaleY, currentVectorEffect, currentVisibility,
                    viewport.viewBox?.width ?: viewport.width,
                    viewport.viewBox?.height ?: viewport.height
                )
            } finally {
                popEffectiveTransform()
                if (flattenViewBox) SvgPathEmitter.popFlattenTransform()
            }

            repeat(openedGroups) {
                currentIndent = currentIndent.dropLast(4)
                output.appendLine("${currentIndent}</group>")
            }
            output.appendLine()
        }

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
                inheritedVectorEffect,
                currentVisibility,
                parentViewportWidth,
                parentViewportHeight
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
                inheritedVectorEffect,
                currentVisibility,
                parentViewportWidth,
                parentViewportHeight
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

                pushEffectiveTransform(groupMatrix)
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
                        currentVectorEffect,
                        currentVisibility,
                        parentViewportWidth,
                        parentViewportHeight
                    )
                } finally {
                    popEffectiveTransform()
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
                    currentVectorEffect,
                    currentVisibility,
                    parentViewportWidth,
                    parentViewportHeight
                )
            }
        }

        "text" -> {
            val convertedGlyphOutlines = SvgTextConverter.appendTextGlyphOutlines(
                output = output,
                element = element,
                indent = indent,
                fontDefinitions = activeSvgFontDefinitions,
                escapeXml = escapeXmlCallback,
                inheritedFill = currentFill,
                inheritedStroke = currentStroke,
                inheritedStrokeWidth = strokeWidthForEmission,
                inheritedOpacity = currentOpacity,
                inheritedFillOpacity = currentFillOpacity,
                inheritedStrokeOpacity = currentStrokeOpacity
            )
            if (!convertedGlyphOutlines) {
                SvgTextConverter.appendTextApproximation(
                    output = output,
                    element = element,
                    indent = indent,
                    escapeXml = escapeXmlCallback,
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
                currentVectorEffect,
                currentVisibility,
                parentViewportWidth,
                parentViewportHeight
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
    inheritedVectorEffect: String? = null,
    inheritedVisibility: String? = null,
    parentViewportWidth: Float? = null,
    parentViewportHeight: Float? = null
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

    val placementMatrix = SvgTransformParser.combineTransformListToMatrix(placementTransforms, transformOrigin)
    val placementScaleEstimate = scaleEstimateFromTransformMatrix(placementMatrix)

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

    pushEffectiveTransform(placementMatrix)
    try {
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
        useVectorEffect,
        inheritedVisibility,
        parentViewportWidth,
        parentViewportHeight
        )
    } finally {
        popEffectiveTransform()
    }

    SvgTransformParser.closeGroups(output, childIndent, groupCount)
    output.appendLine()
}


}
