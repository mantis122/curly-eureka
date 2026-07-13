package com.example.svgvectorconverter

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Typeface

import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.math.abs

object SvgTextConverter {
    private var activeTextElementsApproximated = 0
    private var activeTextElementsConvertedToPaths = 0
    private var activeTextGlyphPathsEmitted = 0
    private var activeTextGlyphSpecificAdvances = 0
    private var activeTextDefaultFontAdvances = 0
    private var activeTextMissingGlyphFallbacks = 0
    private var activeTextGlyphNameLookups = 0
    private var activeTextHorizontalKerningPairsMatched = 0
    private var activeTextVerticalKerningPairsMatched = 0
    private var activeTextKerningAdjustmentsApplied = 0
    private var activeTextLengthSpacingAdjustments = 0
    private var activeTextLengthSpacingAndGlyphsAdjustments = 0
    private var activeTextGlyphRotationsApplied = 0
    private var activeTextLetterSpacingAdjustmentsApplied = 0
    private var activeTextPathsConverted = 0
    private var activeTextPathGlyphsEmitted = 0
    private val activeMatchedHorizontalKerningPairs = linkedSetOf<SvgKerningPair>()
    private val activeMatchedVerticalKerningPairs = linkedSetOf<SvgKerningPair>()
    private val activeTextFontFamilies = linkedSetOf<String>()
    private val activeTextFontWeights = linkedSetOf<String>()

    val textElementsApproximated: Int get() = activeTextElementsApproximated
    val textElementsConvertedToPaths: Int get() = activeTextElementsConvertedToPaths
    val textGlyphPathsEmitted: Int get() = activeTextGlyphPathsEmitted
    val textGlyphSpecificAdvances: Int get() = activeTextGlyphSpecificAdvances
    val textDefaultFontAdvances: Int get() = activeTextDefaultFontAdvances
    val textMissingGlyphFallbacks: Int get() = activeTextMissingGlyphFallbacks
    val textGlyphNameLookups: Int get() = activeTextGlyphNameLookups
    val textHorizontalKerningPairsMatched: Int get() = activeTextHorizontalKerningPairsMatched
    val textVerticalKerningPairsMatched: Int get() = activeTextVerticalKerningPairsMatched
    val textKerningAdjustmentsApplied: Int get() = activeTextKerningAdjustmentsApplied
    val textLengthSpacingAdjustments: Int get() = activeTextLengthSpacingAdjustments
    val textLengthSpacingAndGlyphsAdjustments: Int get() = activeTextLengthSpacingAndGlyphsAdjustments
    val textGlyphRotationsApplied: Int get() = activeTextGlyphRotationsApplied
    val textLetterSpacingAdjustmentsApplied: Int get() = activeTextLetterSpacingAdjustmentsApplied
    val textPathsConverted: Int get() = activeTextPathsConverted
    val textPathGlyphsEmitted: Int get() = activeTextPathGlyphsEmitted
    val textFontFamilies: List<String> get() = activeTextFontFamilies.toList()
    val textFontWeights: List<String> get() = activeTextFontWeights.toList()

    fun resetStats() {
        activeTextElementsApproximated = 0
        activeTextElementsConvertedToPaths = 0
        activeTextGlyphPathsEmitted = 0
        activeTextGlyphSpecificAdvances = 0
        activeTextDefaultFontAdvances = 0
        activeTextMissingGlyphFallbacks = 0
        activeTextGlyphNameLookups = 0
        activeTextHorizontalKerningPairsMatched = 0
        activeTextVerticalKerningPairsMatched = 0
        activeTextKerningAdjustmentsApplied = 0
        activeTextLengthSpacingAdjustments = 0
        activeTextLengthSpacingAndGlyphsAdjustments = 0
        activeTextGlyphRotationsApplied = 0
        activeTextLetterSpacingAdjustmentsApplied = 0
        activeTextPathsConverted = 0
        activeTextPathGlyphsEmitted = 0
        activeMatchedHorizontalKerningPairs.clear()
        activeMatchedVerticalKerningPairs.clear()
        activeTextFontFamilies.clear()
        activeTextFontWeights.clear()
    }

    private fun formatNumber(value: Float): String {
        if (kotlin.math.abs(value - value.toInt().toFloat()) <= 0.0001f) {
            return value.toInt().toString()
        }
        return String.format(java.util.Locale.US, "%.4f", value)
            .trimEnd('0')
            .trimEnd('.')
    }

private fun textNumericListAttr(element: Element, name: String): List<Float> {
    val raw = element.getAttribute(name).trim()
    if (raw.isBlank()) return emptyList()

    return Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        .findAll(raw)
        .mapNotNull { it.value.toFloatOrNull() }
        .toList()
}

private fun textNumericAttr(element: Element, name: String): Float? {
    return textNumericListAttr(element, name).firstOrNull()
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

private fun textLengthValue(rawValue: String?, fontSize: Float): Float {
    val raw = rawValue?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return 0f
    if (raw == "normal" || raw == "initial" || raw == "unset") return 0f

    val match = Regex("""^([-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)(em|ex|px|pt|pc|mm|cm|in)?$""")
        .matchEntire(raw) ?: return 0f
    val number = match.groupValues[1].toFloatOrNull() ?: return 0f
    return when (match.groupValues[2]) {
        "em" -> fontSize * number
        "ex" -> fontSize * 0.5f * number
        "pt" -> number * (96f / 72f)
        "pc" -> number * 16f
        "mm" -> number * (96f / 25.4f)
        "cm" -> number * (96f / 2.54f)
        "in" -> number * 96f
        else -> number
    }
}

private fun resolvedLetterSpacing(element: Element, fontSize: Float): Float {
    return textLengthValue(inheritedTextStyleValue(element, "letter-spacing"), fontSize)
}


private fun baselineShiftValue(value: String?, fontSize: Float): Float {
    val raw = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return 0f
    return when (raw) {
        "baseline", "inherit", "initial", "unset" -> 0f
        "sub" -> -fontSize * 0.20f
        "super" -> fontSize * 0.40f
        else -> {
            val match = Regex("""^([-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)(%|em|ex|px|pt|pc|mm|cm|in)?$""")
                .matchEntire(raw) ?: return 0f
            val number = match.groupValues[1].toFloatOrNull() ?: return 0f
            when (match.groupValues[2]) {
                "%" -> fontSize * number / 100f
                "em" -> fontSize * number
                "ex" -> fontSize * 0.5f * number
                "pt" -> number * (96f / 72f)
                "pc" -> number * 16f
                "mm" -> number * (96f / 25.4f)
                "cm" -> number * (96f / 2.54f)
                "in" -> number * 96f
                else -> number
            }
        }
    }
}

/**
 * Returns the cumulative SVG baseline shift in user units. Positive values move
 * text upward, matching SVG's baseline-shift semantics in a downward-positive Y axis.
 */
private fun resolvedBaselineShift(element: Element, fontSize: Float, stopAt: Element? = null): Float {
    var current: Node? = element
    var shift = 0f
    while (current is Element) {
        val style = current.getAttribute("style").ifBlank { null }
        val raw = textStyleValue(current, style, "baseline-shift")
        if (!raw.isNullOrBlank() && !raw.trim().equals("inherit", ignoreCase = true)) {
            shift += baselineShiftValue(raw, fontSize)
        }
        if (current === stopAt) break
        current = current.parentNode
    }
    return shift
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

private val supportedBaselineValues = setOf(
    "auto", "baseline", "alphabetic", "ideographic", "hanging", "mathematical",
    "central", "middle", "text-before-edge", "before-edge", "text-after-edge", "after-edge",
    "text-top", "text-bottom"
)

private fun normalizedBaselineValue(value: String?): String? {
    val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    return normalized.takeIf { it in supportedBaselineValues }
}

private fun normalizedDominantBaseline(element: Element): String {
    val alignment = normalizedBaselineValue(inheritedTextStyleValue(element, "alignment-baseline"))
    if (alignment != null && alignment != "auto" && alignment != "baseline") return alignment

    return normalizedBaselineValue(inheritedTextStyleValue(element, "dominant-baseline"))
        ?.takeUnless { it == "auto" || it == "baseline" }
        ?: "alphabetic"
}

/**
 * Converts an SVG baseline coordinate into the alphabetic baseline expected by
 * Android Paint.getTextPath(). FontMetrics values use Android coordinates:
 * ascent/top are negative and descent/bottom are positive.
 */
private fun androidAlphabeticBaselineFor(
    requestedBaselineY: Float,
    metrics: Paint.FontMetrics,
    baseline: String
): Float {
    return when (baseline) {
        "middle", "central" -> requestedBaselineY - (metrics.ascent + metrics.descent) / 2f
        "mathematical" -> requestedBaselineY - (metrics.ascent + metrics.descent) * 0.45f
        "hanging" -> requestedBaselineY - metrics.ascent * 0.8f
        "text-before-edge", "before-edge", "text-top" -> requestedBaselineY - metrics.top
        "text-after-edge", "after-edge", "text-bottom" -> requestedBaselineY - metrics.bottom
        "ideographic" -> requestedBaselineY - metrics.descent
        else -> requestedBaselineY
    }
}

/** Returns the alphabetic-baseline shift for an embedded SVG font. */
private fun embeddedAlphabeticBaselineOffset(
    font: SvgFontDefinition,
    fontSize: Float,
    baseline: String
): Float {
    val scale = fontSize / font.unitsPerEm
    val ascent = font.ascent * scale
    val descent = kotlin.math.abs(font.descent) * scale
    return when (baseline) {
        "middle", "central" -> (ascent - descent) / 2f
        "mathematical" -> (ascent - descent) * 0.45f
        "hanging" -> ascent * 0.8f
        "text-before-edge", "before-edge", "text-top" -> ascent
        "text-after-edge", "after-edge", "text-bottom" -> -descent
        "ideographic" -> -descent
        else -> 0f
    }
}

private fun textTopForBaseline(y: Float, dy: Float, height: Float, baseline: String): Float {
    val baselineY = y + dy
    return when (baseline) {
        "middle", "central", "mathematical" -> baselineY - height / 2f
        "hanging", "text-before-edge", "before-edge", "text-top" -> baselineY
        "text-after-edge", "after-edge", "text-bottom" -> baselineY - height
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
    val element: Element,
    val glyphNames: List<String>? = null
)

private fun textHasPositionAttribute(element: Element): Boolean {
    return element.hasAttribute("x") ||
        element.hasAttribute("y") ||
        element.hasAttribute("dx") ||
        element.hasAttribute("dy")
}

private fun glyphNameReferences(element: Element): List<String> {
    val tag = element.tagName.substringAfter(":").lowercase()
    val direct = listOf(
        element.getAttribute("glyph-name"),
        element.getAttribute("glyphRef"),
        element.getAttribute("glyph-ref")
    ).firstOrNull { it.isNotBlank() }

    val href = if (tag == "textpath") "" else element.getAttribute("href").ifBlank { element.getAttribute("xlink:href") }
    val raw = direct ?: href.substringAfterLast('#', "")
    return raw
        .split(Regex("""[\s,]+"""))
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
}

private fun textApproximationRuns(element: Element): List<TextApproximationRun> {
    val rootGlyphNames = glyphNameReferences(element)
    if (rootGlyphNames.isNotEmpty()) {
        return listOf(TextApproximationRun(
            text = normalizeTextWhitespace(element.textContent.orEmpty()),
            element = element,
            glyphNames = rootGlyphNames
        ))
    }

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
                            val glyphNames = glyphNameReferences(childElement)
                            if (glyphNames.isNotEmpty()) {
                                runs.add(TextApproximationRun(
                                    text = normalizeTextWhitespace(childElement.textContent.orEmpty()),
                                    element = childElement,
                                    glyphNames = glyphNames
                                ))
                            } else {
                                collectInlineRuns(childElement)
                            }
                        }

                        "altglyph" -> {
                            flushPendingText()
                            val glyphNames = glyphNameReferences(childElement)
                            if (glyphNames.isNotEmpty()) {
                                runs.add(TextApproximationRun(
                                    text = normalizeTextWhitespace(childElement.textContent.orEmpty()),
                                    element = childElement,
                                    glyphNames = glyphNames
                                ))
                            } else {
                                collectInlineRuns(childElement)
                            }
                        }

                        "textpath" -> {
                            flushPendingText()
                        }

                        else -> {
                            flushPendingText()
                            val glyphNames = glyphNameReferences(childElement)
                            if (glyphNames.isNotEmpty()) {
                                runs.add(TextApproximationRun(
                                    text = normalizeTextWhitespace(childElement.textContent.orEmpty()),
                                    element = childElement,
                                    glyphNames = glyphNames
                                ))
                            } else {
                                collectInlineRuns(childElement)
                            }
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

private fun kerningAdjustment(
    font: SvgFontDefinition,
    first: SvgGlyphOutline?,
    second: SvgGlyphOutline?,
    vertical: Boolean = false,
    recordMatch: Boolean = false
): Float {
    val pair = SvgFontResolver.matchingKerningPair(font, first, second, vertical) ?: return 0f
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

private fun explicitTextLength(element: Element): Float? {
    val style = element.getAttribute("style").ifBlank { null }
    val raw = textStyleValue(element, style, "textLength") ?: return null
    return Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        .find(raw)
        ?.value
        ?.toFloatOrNull()
        ?.takeIf { it >= 0f }
}

private fun explicitLengthAdjust(element: Element): String {
    val style = element.getAttribute("style").ifBlank { null }
    return when ((textStyleValue(element, style, "lengthAdjust") ?: "spacing")
        .trim()
        .lowercase()) {
        "spacingandglyphs" -> "spacingandglyphs"
        else -> "spacing"
    }
}

private fun nearestTextLengthOwner(runElement: Element, rootTextElement: Element): Element? {
    var current: Node? = runElement
    while (current is Element) {
        if (explicitTextLength(current) != null) return current
        if (current === rootTextElement) break
        current = current.parentNode
    }
    return null
}

private fun textLengthOwners(runElement: Element, rootTextElement: Element): List<Element> {
    val owners = mutableListOf<Element>()
    var current: Node? = runElement
    while (current is Element) {
        if (explicitTextLength(current) != null) owners.add(current)
        if (current === rootTextElement) break
        current = current.parentNode
    }
    return owners
}


private fun descendantTextPaths(element: Element): List<Element> {
    val result = mutableListOf<Element>()
    fun visit(node: Node) {
        if (node.nodeType != Node.ELEMENT_NODE) return
        val child = node as Element
        if (child.tagName.substringAfter(":").equals("textPath", ignoreCase = true)) {
            result.add(child)
            return
        }
        val children = child.childNodes
        for (i in 0 until children.length) visit(children.item(i))
    }
    val children = element.childNodes
    for (i in 0 until children.length) visit(children.item(i))
    return result
}

private fun hrefId(element: Element): String? {
    val href = element.getAttribute("href").ifBlank {
        element.getAttribute("xlink:href").ifBlank {
            element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        }
    }.trim()
    return href.removePrefix("#").takeIf { it.isNotBlank() }
}

private fun findElementById(root: Element, id: String): Element? {
    if (root.getAttribute("id") == id) return root
    val children = root.childNodes
    for (i in 0 until children.length) {
        val child = children.item(i)
        if (child.nodeType == Node.ELEMENT_NODE) {
            val found = findElementById(child as Element, id)
            if (found != null) return found
        }
    }
    return null
}

private fun referencedTextPathData(textPath: Element): String? {
    val id = hrefId(textPath) ?: return null
    val documentRoot = textPath.ownerDocument?.documentElement ?: return null
    val referenced = findElementById(documentRoot, id) ?: return null
    val tag = referenced.tagName.substringAfter(":").lowercase()
    var data = when (tag) {
        "path" -> referenced.getAttribute("d").trim().takeIf { it.isNotBlank() }
        "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
            SvgShapeConverters.basicShapeToPathData(referenced, tag)
        else -> null
    } ?: return null

    // A referenced path is measured in the coordinate system produced by its
    // complete ancestor transform chain, not only by its own transform.
    val chain = mutableListOf<Element>()
    var current: Node? = referenced
    while (current is Element) {
        chain.add(current)
        if (current === documentRoot) break
        current = current.parentNode
    }

    var matrix: AffineTransform? = null
    for (element in chain.asReversed()) {
        val elementMatrix = elementTransformMatrix(element) ?: continue
        matrix = if (matrix == null) elementMatrix else matrix.multiply(elementMatrix)
    }
    if (matrix != null) {
        data = SvgPathDataTransformer.applyAffineTransform(data, matrix) ?: data
    }
    return data
}

private fun startOffset(textPath: Element, pathLength: Float): Float {
    val style = textPath.getAttribute("style").ifBlank { null }
    val raw = (SvgPaintResolver.styleValue(style, "start-offset")
        ?: SvgPaintResolver.styleValue(style, "startOffset")
        ?: textPath.getAttribute("startOffset").ifBlank { null })?.trim().orEmpty()
    if (raw.endsWith("%")) {
        return (raw.removeSuffix("%").trim().toFloatOrNull() ?: 0f) * pathLength / 100f
    }
    return Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        .find(raw)?.value?.toFloatOrNull() ?: 0f
}

private fun textPathMethod(textPath: Element): String {
    val style = textPath.getAttribute("style").ifBlank { null }
    val raw = (SvgPaintResolver.styleValue(style, "method")
        ?: textPath.getAttribute("method").ifBlank { null })
        ?.trim()?.lowercase()
    return if (raw == "stretch") "stretch" else "align"
}

private fun textPathSpacing(textPath: Element): String {
    val style = textPath.getAttribute("style").ifBlank { null }
    val raw = (SvgPaintResolver.styleValue(style, "spacing")
        ?: textPath.getAttribute("spacing").ifBlank { null })
        ?.trim()?.lowercase()
    return if (raw == "auto") "auto" else "exact"
}

private fun elementTransformMatrix(element: Element): AffineTransform? {
    val style = element.getAttribute("style").ifBlank { null }
    val transform = element.getAttribute("transform")
        .ifBlank { SvgPaintResolver.styleValue(style, "transform") ?: "" }
    if (transform.isBlank()) return null

    val origin = SvgTransformParser.parseTransformOrigin(
        SvgPaintResolver.styleValue(style, "transform-origin")
            ?: element.getAttribute("transform-origin").ifBlank { "" }
    )
    return SvgTransformParser.combineTransformListToMatrix(
        SvgTransformParser.parseTransformList(transform),
        origin
    )
}

/**
 * Composes transforms from the direct child of [stopExclusive] through [element].
 * This is used for <textPath> and nested <tspan> transforms; the root <text>
 * transform is applied separately so it is not applied twice.
 */
private fun descendantTransformMatrix(
    element: Element,
    stopExclusive: Element
): AffineTransform? {
    val chain = mutableListOf<Element>()
    var current: Node? = element
    while (current is Element && current !== stopExclusive) {
        chain.add(current)
        current = current.parentNode
    }

    var result: AffineTransform? = null
    for (item in chain.asReversed()) {
        val matrix = elementTransformMatrix(item) ?: continue
        result = if (result == null) matrix else result.multiply(matrix)
    }
    return result
}

private fun appendTextPathGlyphOutlines(
    output: StringBuilder,
    rootText: Element,
    indent: String,
    fontDefinitions: Map<String, SvgFontDefinition>,
    escapeXml: (String) -> String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
): Int {
    if (fontDefinitions.isEmpty()) return 0
    val textPaths = descendantTextPaths(rootText)
    if (textPaths.isEmpty()) return 0

    val rootMatrix = elementTransformMatrix(rootText)

    var emitted = 0
    var commentWritten = false

    for (textPath in textPaths) {
        val pathData = referencedTextPathData(textPath) ?: continue
        val measured = SvgPathSampler.measure(pathData) ?: continue
        val method = textPathMethod(textPath)
        val spacingMode = textPathSpacing(textPath)
        val runs = textApproximationRuns(textPath)
        if (runs.isEmpty()) continue

        data class PathRun(
            val run: TextApproximationRun,
            val font: SvgFontDefinition,
            val fontSize: Float,
            val baselineShift: Float,
            val fontFamily: String,
            val fontWeight: String,
            val letterSpacing: Float,
            val fill: String,
            val stroke: String?,
            val strokeWidth: String?,
            val opacity: String?,
            val fillOpacity: String?,
            val strokeOpacity: String?,
            val rotateOwners: List<Element>,
            val glyphs: List<ResolvedTextGlyph>,
            val naturalAdvances: List<Float>,
            val localMatrix: AffineTransform?
        )

        val prepared = runs.mapNotNull { run ->
            val fontSize = (inheritedTextStyleValue(run.element, "font-size")
                ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
                ?.toFloatOrNull() ?: 16f).coerceAtLeast(1f)
            val family = inheritedTextStyleValue(run.element, "font-family").orEmpty()
            val font = SvgFontResolver.findMatchingFont(fontDefinitions, family) ?: return@mapNotNull null
            val glyphs = SvgFontResolver.resolveGlyphs(font, run.text, run.glyphNames)
            val scale = fontSize / font.unitsPerEm
            val advances = glyphs.mapIndexed { index, resolved ->
                val next = glyphs.getOrNull(index + 1)?.glyph
                val kern = kerningAdjustment(font, resolved.glyph, next, vertical = false, recordMatch = true)
                if (abs(kern) > 0.001f) activeTextKerningAdjustmentsApplied++
                (SvgFontResolver.glyphAdvance(font, resolved.glyph, vertical = false) - kern) * scale
            }
            PathRun(
                run = run,
                font = font,
                fontSize = fontSize,
                baselineShift = resolvedBaselineShift(run.element, fontSize, rootText),
                fontFamily = family,
                fontWeight = normalizeTextFontWeight(inheritedTextStyleValue(run.element, "font-weight")),
                letterSpacing = resolvedLetterSpacing(run.element, fontSize),
                fill = inheritedTextStyleValue(run.element, "fill") ?: inheritedFill ?: "#000000",
                stroke = inheritedTextStyleValue(run.element, "stroke")
                    ?: inheritedStroke?.trim()?.takeIf { it.isNotBlank() },
                strokeWidth = inheritedTextStyleValue(run.element, "stroke-width")
                    ?: inheritedStrokeWidth?.trim()?.takeIf { it.isNotBlank() },
                opacity = SvgPaintResolver.inheritedOpacity(
                    inheritedOpacity,
                    inheritedTextStyleValue(run.element, "opacity") ?: ""
                ),
                fillOpacity = SvgPaintResolver.inheritedPaintOpacity(
                    inheritedFillOpacity,
                    inheritedTextStyleValue(run.element, "fill-opacity") ?: ""
                ),
                strokeOpacity = SvgPaintResolver.inheritedPaintOpacity(
                    inheritedStrokeOpacity,
                    inheritedTextStyleValue(run.element, "stroke-opacity") ?: ""
                ),
                rotateOwners = buildList {
                    var current: Node? = run.element
                    while (current is Element) {
                        if (current.hasAttribute("rotate") &&
                            textNumericListAttr(current, "rotate").isNotEmpty()
                        ) {
                            add(current)
                        }
                        if (current === rootText) break
                        current = current.parentNode
                    }
                },
                glyphs = glyphs,
                naturalAdvances = advances,
                localMatrix = descendantTransformMatrix(run.element, rootText)
            )
        }
        if (prepared.size != runs.size) continue

        val glyphCount = prepared.sumOf { it.glyphs.size }
        if (glyphCount == 0) continue

        val naturalTotalAdvance = prepared.sumOf { it.naturalAdvances.sum().toDouble() }.toFloat() +
            prepared.mapIndexed { runIndex, item ->
                val gaps = item.glyphs.size - if (runIndex == prepared.lastIndex) 1 else 0
                item.letterSpacing * gaps.coerceAtLeast(0)
            }.sum()
        val lengthOwner = nearestTextLengthOwner(textPath, rootText)
        val targetTextLength = lengthOwner?.let { explicitTextLength(it) }
        val lengthAdjust = lengthOwner?.let { explicitLengthAdjust(it) } ?: "spacing"

        val glyphAxisScale =
            if (targetTextLength != null &&
                lengthAdjust == "spacingandglyphs" &&
                naturalTotalAdvance > 0.0001f
            ) {
                activeTextLengthSpacingAndGlyphsAdjustments++
                targetTextLength / naturalTotalAdvance
            } else {
                1f
            }

        val spacingAdjustment =
            if (targetTextLength != null &&
                lengthAdjust == "spacing" &&
                glyphCount > 1
            ) {
                activeTextLengthSpacingAdjustments++
                (targetTextLength - naturalTotalAdvance) / (glyphCount - 1).toFloat()
            } else {
                0f
            }

        // SVG allows spacing="auto" to let a renderer improve visual spacing.
        // VectorDrawable has no equivalent, so preserve the authored advances.
        // textLength/lengthAdjust above still take precedence when supplied.
        @Suppress("UNUSED_VARIABLE")
        val resolvedSpacingMode = spacingMode

        val adjustedTotalAdvance = when {
            targetTextLength != null -> targetTextLength
            else -> naturalTotalAdvance
        }

        val anchor = normalizedTextAnchor(textPath)
        var cursor = startOffset(textPath, measured.length) +
            (textNumericAttr(textPath, "dx") ?: 0f)
        cursor = when (anchor) {
            "middle" -> cursor - adjustedTotalAdvance / 2f
            "end" -> cursor - adjustedTotalAdvance
            else -> cursor
        }

        val baselineOffset = textNumericAttr(textPath, "dy") ?: 0f
        val rotateIndex = mutableMapOf<Element, Int>()
        var globalGlyphIndex = 0
        var pathGlyphs = 0

        for (run in prepared) {
            if (run.fontFamily.isNotBlank()) activeTextFontFamilies.add(run.fontFamily)
            if (run.fontWeight.isNotBlank()) activeTextFontWeights.add(run.fontWeight)

            val fontScale = run.fontSize / run.font.unitsPerEm
            var runSourceOffset = 0
            for ((index, resolved) in run.glyphs.withIndex()) {
                val consumedChars = resolved.consumedChars.coerceAtLeast(1)
                val sourceEnd = (runSourceOffset + consumedChars).coerceAtMost(run.run.text.length)
                val sourceSegment = run.run.text.substring(runSourceOffset, sourceEnd)
                val sourceIsWhitespace = sourceSegment.isNotEmpty() && sourceSegment.all { it.isWhitespace() }
                val naturalAdvance = run.naturalAdvances[index]
                val scaledAdvance = naturalAdvance * glyphAxisScale
                val hasFollowingGlyph = globalGlyphIndex < glyphCount - 1
                val authoredSpacing = if (hasFollowingGlyph) run.letterSpacing * glyphAxisScale else 0f
                val advanceAfterGlyph =
                    scaledAdvance + authoredSpacing + if (hasFollowingGlyph) spacingAdjustment else 0f
                if (hasFollowingGlyph && abs(authoredSpacing) > 0.0001f) {
                    activeTextLetterSpacingAdjustmentsApplied++
                }
                val centerDistance = cursor + scaledAdvance / 2f

                val rotateOwner = run.rotateOwners.firstOrNull()
                val extraRotation = rotateOwner?.let { owner ->
                    val values = textNumericListAttr(owner, "rotate")
                    val i = rotateIndex[owner] ?: 0
                    when {
                        values.isEmpty() -> 0f
                        i < values.size -> values[i]
                        else -> values.last()
                    }
                } ?: 0f

                val centerIsPlaceable = measured.isClosed ||
                    (centerDistance >= 0f && centerDistance <= measured.length)
                if (!resolved.isWhitespace && centerIsPlaceable) {
                    val sample = measured.sample(
                        centerDistance,
                        wrapClosed = measured.isClosed
                    )
                    if (sample != null) {
                        val tangentRadians = Math.toRadians(sample.angleDegrees.toDouble())
                        val tangentX = kotlin.math.cos(tangentRadians).toFloat()
                        val tangentY = kotlin.math.sin(tangentRadians).toFloat()
                        val normalX = -tangentY
                        val normalY = tangentX
                        val runBaselineOffset = baselineOffset - run.baselineShift
                        val originX =
                            sample.x - tangentX * scaledAdvance / 2f + normalX * runBaselineOffset
                        val originY =
                            sample.y - tangentY * scaledAdvance / 2f + normalY * runBaselineOffset

                        val angle = sample.angleDegrees + extraRotation
                        if (abs(extraRotation) > 0.0001f) activeTextGlyphRotationsApplied++

                        var glyphData = resolved.glyph.pathData
                        if (resolved.glyph.transform != null) {
                            glyphData = SvgPathDataTransformer.applyAffineTransform(
                                glyphData,
                                resolved.glyph.transform
                            ) ?: glyphData
                        }

                        var finalData = if (method == "stretch") {
                            val extraRadians = Math.toRadians(extraRotation.toDouble())
                            val extraCos = kotlin.math.cos(extraRadians).toFloat()
                            val extraSin = kotlin.math.sin(extraRadians).toFloat()
                            SvgPathSampler.mapFlattenedPath(glyphData) { glyphX, glyphY ->
                                val localX = glyphX * fontScale * glyphAxisScale
                                val localY = -glyphY * fontScale
                                val rotatedX = extraCos * localX - extraSin * localY
                                val rotatedY = extraSin * localX + extraCos * localY
                                val pointDistance = cursor + rotatedX
                                val pointSample = measured.sample(
                                    pointDistance,
                                    wrapClosed = measured.isClosed
                                ) ?: return@mapFlattenedPath null
                                val pointRadians =
                                    Math.toRadians(pointSample.angleDegrees.toDouble())
                                val pointNormalX =
                                    -kotlin.math.sin(pointRadians).toFloat()
                                val pointNormalY =
                                    kotlin.math.cos(pointRadians).toFloat()
                                SvgPathSampler.Point(
                                    pointSample.x +
                                        pointNormalX * (runBaselineOffset + rotatedY),
                                    pointSample.y +
                                        pointNormalY * (runBaselineOffset + rotatedY)
                                )
                            } ?: glyphData
                        } else {
                            val radians = Math.toRadians(angle.toDouble())
                            val cos = kotlin.math.cos(radians).toFloat()
                            val sin = kotlin.math.sin(radians).toFloat()
                            val base = AffineTransform(
                                fontScale * glyphAxisScale,
                                0f,
                                0f,
                                -fontScale,
                                originX,
                                originY
                            )
                            val placement = AffineTransform(
                                a = cos * base.a - sin * base.b,
                                b = sin * base.a + cos * base.b,
                                c = cos * base.c - sin * base.d,
                                d = sin * base.c + cos * base.d,
                                e = base.e,
                                f = base.f
                            )
                            SvgPathDataTransformer.applyAffineTransform(
                                glyphData,
                                placement
                            ) ?: glyphData
                        }

                        if (run.localMatrix != null) {
                            finalData = SvgPathDataTransformer.applyAffineTransform(
                                finalData,
                                run.localMatrix
                            ) ?: finalData
                        }
                        if (rootMatrix != null) {
                            finalData = SvgPathDataTransformer.applyAffineTransform(
                                finalData,
                                rootMatrix
                            ) ?: finalData
                        }
                        finalData = SvgPathEmitter.applyCurrentFlattenTransform(finalData)

                        if (!commentWritten) {
                            output.appendLine(
                                "${indent}<!-- converted textPath glyph outlines from embedded SVG font -->"
                            )
                            commentWritten = true
                        }

                        val safeFill = SvgPaintResolver.safeFillColor(run.fill)
                        val safeStroke = SvgPaintResolver.safeStrokeColor(run.stroke)
                        val fillAlpha = SvgPaintResolver.combineAlpha(run.opacity, run.fillOpacity)
                        val strokeAlpha = SvgPaintResolver.combineAlpha(
                            run.opacity,
                            run.strokeOpacity
                        )

                        output.appendLine("${indent}<path")
                        output.appendLine(
                            "${indent}    android:pathData=\"${escapeXml(finalData)}\""
                        )
                        output.appendLine("${indent}    android:fillColor=\"$safeFill\"")
                        if (fillAlpha != null) {
                            output.appendLine(
                                "${indent}    android:fillAlpha=\"$fillAlpha\""
                            )
                        }
                        if (safeStroke != null) {
                            output.appendLine(
                                "${indent}    android:strokeColor=\"$safeStroke\""
                            )
                            output.appendLine(
                                "${indent}    android:strokeWidth=\"" +
                                    "${run.strokeWidth?.takeIf { it.isNotBlank() } ?: "1"}\""
                            )
                            if (strokeAlpha != null) {
                                output.appendLine(
                                    "${indent}    android:strokeAlpha=\"$strokeAlpha\""
                                )
                            }
                        }
                        output.appendLine("${indent}/>")

                        if (SvgFontResolver.hasGlyphSpecificAdvance(
                                resolved.glyph,
                                vertical = false
                            )
                        ) {
                            activeTextGlyphSpecificAdvances++
                        } else {
                            activeTextDefaultFontAdvances++
                        }
                        if (run.font.missingGlyph === resolved.glyph && !sourceIsWhitespace) {
                            activeTextMissingGlyphFallbacks++
                        }
                        if (resolved.fromGlyphName) activeTextGlyphNameLookups++
                        pathGlyphs++
                        emitted++
                    }
                }

                cursor += advanceAfterGlyph
                globalGlyphIndex++
                for (owner in run.rotateOwners) {
                    rotateIndex[owner] = (rotateIndex[owner] ?: 0) + 1
                }
                runSourceOffset = sourceEnd
            }
        }

        if (pathGlyphs > 0) activeTextPathsConverted++
    }

    if (emitted > 0) {
        output.appendLine()
        activeTextPathGlyphsEmitted += emitted
        activeTextGlyphPathsEmitted += emitted
    }
    return emitted
}


private fun androidTypefaceFor(element: Element): Typeface {
    val familyRaw = inheritedTextStyleValue(element, "font-family")
        ?.split(',')
        ?.firstOrNull()
        ?.trim()
        ?.trim('"', '\'')
        ?.takeIf { it.isNotBlank() }
        ?: "sans-serif"

    val family = when (familyRaw.lowercase()) {
        "system-ui", "sans", "sans-serif", "arial", "helvetica" -> "sans-serif"
        "serif", "times", "times new roman" -> "serif"
        "monospace", "mono", "courier", "courier new" -> "monospace"
        "cursive" -> "cursive"
        else -> familyRaw
    }

    val weight = normalizeTextFontWeight(inheritedTextStyleValue(element, "font-weight"))
        .toIntOrNull() ?: 400
    val italic = inheritedTextStyleValue(element, "font-style")
        ?.trim()
        ?.lowercase()
        .let { it == "italic" || it == "oblique" }

    val style = when {
        weight >= 600 && italic -> Typeface.BOLD_ITALIC
        weight >= 600 -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return Typeface.create(family, style)
}

private fun androidPathToVectorPathData(path: Path): String? {
    val measure = PathMeasure(path, false)
    val out = StringBuilder()
    val pos = FloatArray(2)
    var hasAny = false

    do {
        val length = measure.length
        if (length > 0.01f) {
            val step = (length / 180f).coerceIn(0.45f, 2.0f)
            var distance = 0f
            var first = true
            while (distance < length) {
                if (measure.getPosTan(distance, pos, null)) {
                    if (first) {
                        if (out.isNotEmpty()) out.append(' ')
                        out.append("M ").append(formatNumber(pos[0])).append(',').append(formatNumber(pos[1]))
                        first = false
                        hasAny = true
                    } else {
                        out.append(" L ").append(formatNumber(pos[0])).append(',').append(formatNumber(pos[1]))
                    }
                }
                distance += step
            }
            if (measure.getPosTan(length, pos, null)) {
                out.append(" L ").append(formatNumber(pos[0])).append(',').append(formatNumber(pos[1]))
            }
            out.append(" Z")
        }
    } while (measure.nextContour())

    return out.toString().takeIf { hasAny }
}

private fun appendAndroidSystemFontOutlines(
    output: StringBuilder,
    element: Element,
    indent: String,
    escapeXml: (String) -> String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
): Boolean {
    if (descendantTextPaths(element).isNotEmpty()) return false

    val runs = textApproximationRuns(element)
    if (runs.isEmpty()) return false

    data class AndroidRun(
        val run: TextApproximationRun,
        val paint: Paint,
        val width: Float,
        val verticalAdvance: Float,
        val fontSize: Float,
        val fill: String,
        val stroke: String?,
        val strokeWidth: String?,
        val opacity: String?,
        val fillOpacity: String?,
        val strokeOpacity: String?,
        val anchor: String,
        val baseline: String,
        val baselineShift: Float,
        val vertical: Boolean,
        val explicitX: Float?,
        val explicitY: Float?,
        val dx: Float,
        val dy: Float
    )

    val prepared = runs.mapNotNull { run ->
        if (run.text.isBlank()) return@mapNotNull null
        val fontSize = inheritedTextStyleValue(run.element, "font-size")
            ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
            ?.toFloatOrNull()
            ?.coerceAtLeast(1f)
            ?: 16f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = fontSize
            typeface = androidTypefaceFor(run.element)
            isSubpixelText = true
        }
        val vertical = isVerticalWritingMode(run.element)
        AndroidRun(
            run = run,
            paint = paint,
            width = paint.measureText(run.text),
            verticalAdvance = codePointStrings(run.text).size * fontSize,
            fontSize = fontSize,
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
            baselineShift = resolvedBaselineShift(run.element, fontSize, element),
            vertical = vertical,
            // The root <text> x/y values establish the initial cursor and must not be
            // reapplied as run-level positioning. Reapplying root x here would erase
            // the root text-anchor offset calculated below.
            explicitX = if (run.element !== element && run.element.hasAttribute("x")) textNumericAttr(run.element, "x") else null,
            explicitY = if (run.element !== element && run.element.hasAttribute("y")) textNumericAttr(run.element, "y") else null,
            dx = textNumericAttr(run.element, "dx") ?: 0f,
            dy = textNumericAttr(run.element, "dy") ?: 0f
        )
    }
    if (prepared.isEmpty()) return false

    val rootX = textNumericAttr(element, "x") ?: 0f
    val rootY = textNumericAttr(element, "y") ?: 0f
    val rootDx = textNumericAttr(element, "dx") ?: 0f
    val rootDy = textNumericAttr(element, "dy") ?: 0f
    val rootVertical = isVerticalWritingMode(element)
    val totalAdvance = prepared.sumOf {
        (if (it.vertical) it.verticalAdvance else it.width).toDouble()
    }.toFloat()
    val anchor = normalizedTextAnchor(element)
    var cursorX = rootX + rootDx
    var cursorY = rootY + rootDy
    if (rootVertical) {
        cursorY -= when (anchor) {
            "middle" -> totalAdvance / 2f
            "end" -> totalAdvance
            else -> 0f
        }
    } else {
        cursorX -= when (anchor) {
            "middle" -> totalAdvance / 2f
            "end" -> totalAdvance
            else -> 0f
        }
    }

    val textStyle = element.getAttribute("style").ifBlank { null }
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

    var emitted = 0
    fun anchorOffset(anchorValue: String, width: Float): Float {
        return when (anchorValue) {
            "middle" -> width / 2f
            "end" -> width
            else -> 0f
        }
    }

    for (item in prepared) {
        // A positioned child run (normally a <tspan x="...">) starts a new
        // anchored text chunk. Apply that run's own anchor against its width.
        if (item.vertical) {
            if (item.explicitX != null) cursorX = item.explicitX
            if (item.explicitY != null) {
                cursorY = item.explicitY - anchorOffset(item.anchor, item.verticalAdvance)
            }
        } else {
            if (item.explicitX != null) {
                cursorX = item.explicitX - anchorOffset(item.anchor, item.width)
            }
            if (item.explicitY != null) cursorY = item.explicitY
        }
        cursorX += item.dx
        cursorY += item.dy

        val glyphTexts = if (item.vertical) codePointStrings(item.run.text) else listOf(item.run.text)
        for (glyphText in glyphTexts) {
            val androidPath = Path()
            val glyphWidth = item.paint.measureText(glyphText)
            val glyphX = if (item.vertical) cursorX - glyphWidth / 2f else cursorX
            val drawBaselineY = androidAlphabeticBaselineFor(
                requestedBaselineY = cursorY - item.baselineShift,
                metrics = item.paint.fontMetrics,
                baseline = item.baseline
            )
            item.paint.getTextPath(glyphText, 0, glyphText.length, glyphX, drawBaselineY, androidPath)
            var pathData = androidPathToVectorPathData(androidPath)
            if (pathData != null) {
                if (elementMatrix != null) {
                    pathData = SvgPathDataTransformer.applyAffineTransform(pathData, elementMatrix) ?: pathData
                }
                pathData = SvgPathEmitter.applyCurrentFlattenTransform(pathData)

                if (emitted == 0) {
                    output.appendLine("${indent}<!-- converted text to outlines using Android system font -->")
                }

                val safeFill = SvgPaintResolver.safeFillColor(item.fill)
                val safeStroke = SvgPaintResolver.safeStrokeColor(item.stroke)
                val fillAlpha = SvgPaintResolver.combineAlpha(item.opacity, item.fillOpacity)
                val strokeAlpha = SvgPaintResolver.combineAlpha(item.opacity, item.strokeOpacity)

                output.appendLine("${indent}<path")
                output.appendLine("${indent}    android:pathData=\"${escapeXml(pathData)}\"")
                output.appendLine("${indent}    android:fillColor=\"$safeFill\"")
                output.appendLine("${indent}    android:fillType=\"evenOdd\"")
                if (fillAlpha != null) output.appendLine("${indent}    android:fillAlpha=\"$fillAlpha\"")
                if (safeStroke != null) {
                    output.appendLine("${indent}    android:strokeColor=\"$safeStroke\"")
                    output.appendLine("${indent}    android:strokeWidth=\"${item.strokeWidth?.takeIf { it.isNotBlank() } ?: "1"}\"")
                    if (strokeAlpha != null) output.appendLine("${indent}    android:strokeAlpha=\"$strokeAlpha\"")
                }
                output.appendLine("${indent}/>")
                emitted++
            }

            if (item.vertical) cursorY += item.fontSize else cursorX += glyphWidth
        }

        val family = inheritedTextStyleValue(item.run.element, "font-family").orEmpty()
        val weight = normalizeTextFontWeight(inheritedTextStyleValue(item.run.element, "font-weight"))
        if (family.isNotBlank()) activeTextFontFamilies.add(family)
        if (weight.isNotBlank()) activeTextFontWeights.add(weight)
    }

    if (emitted == 0) return false
    output.appendLine()
    activeTextGlyphPathsEmitted += emitted
    activeTextElementsConvertedToPaths++
    return true
}

fun appendTextGlyphOutlines(
    output: StringBuilder,
    element: Element,
    indent: String,
    fontDefinitions: Map<String, SvgFontDefinition>,
    escapeXml: (String) -> String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?
): Boolean {
    val textPathGlyphs = appendTextPathGlyphOutlines(
        output, element, indent, fontDefinitions, escapeXml, inheritedFill, inheritedStroke,
        inheritedStrokeWidth, inheritedOpacity, inheritedFillOpacity, inheritedStrokeOpacity
    )
    val runs = textApproximationRuns(element)
    if (runs.isEmpty() || fontDefinitions.isEmpty()) {
        if (textPathGlyphs > 0) {
            activeTextElementsConvertedToPaths++
            return true
        }
        return appendAndroidSystemFontOutlines(
            output = output,
            element = element,
            indent = indent,
            escapeXml = escapeXml,
            inheritedFill = inheritedFill,
            inheritedStroke = inheritedStroke,
            inheritedStrokeWidth = inheritedStrokeWidth,
            inheritedOpacity = inheritedOpacity,
            inheritedFillOpacity = inheritedFillOpacity,
            inheritedStrokeOpacity = inheritedStrokeOpacity
        )
    }

    data class GlyphTextRun(
        val run: TextApproximationRun,
        val font: SvgFontDefinition,
        val fontSize: Float,
        val fontFamily: String,
        val fontWeight: String,
        val letterSpacing: Float,
        val advance: Float,
        val glyphCount: Int,
        val lengthOwner: Element?,
        val lengthOwners: List<Element>,
        val targetTextLength: Float?,
        val lengthAdjust: String,
        val spacingAdjustment: Float,
        val glyphAxisScale: Float,
        val fill: String,
        val stroke: String?,
        val strokeWidth: String?,
        val opacity: String?,
        val fillOpacity: String?,
        val strokeOpacity: String?,
        val anchor: String,
        val baseline: String,
        val baselineShift: Float,
        val vertical: Boolean,
        val explicitX: Float?,
        val explicitY: Float?,
        val dx: Float,
        val dy: Float,
        val xValues: List<Float>,
        val yValues: List<Float>,
        val dxValues: List<Float>,
        val dyValues: List<Float>,
        val rotateOwners: List<Element>,
        val sourceTag: String,
        val glyphNames: List<String>?
    )

    val rawPreparedRuns = runs.mapIndexedNotNull { runIndex, run ->
        val runStyle = run.element.getAttribute("style").ifBlank { null }
        val fontSize = (inheritedTextStyleValue(run.element, "font-size")
            ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
            ?.toFloatOrNull()
            ?: 16f).coerceAtLeast(1f)
        val fontFamily = inheritedTextStyleValue(run.element, "font-family").orEmpty()
        val font = SvgFontResolver.findMatchingFont(fontDefinitions, fontFamily) ?: return@mapIndexedNotNull null
        val fontWeight = normalizeTextFontWeight(inheritedTextStyleValue(run.element, "font-weight"))
        val vertical = isVerticalWritingMode(run.element)
        val isParentTextRun = run.element === element
        val lengthOwners = textLengthOwners(run.element, element)
        val lengthOwner = lengthOwners.firstOrNull()
        val glyphCount = SvgFontResolver.textRunGlyphCount(font, run.text, run.glyphNames)
        val letterSpacing = resolvedLetterSpacing(run.element, fontSize)
        val letterSpacingGapCount = (glyphCount - if (runIndex == runs.lastIndex) 1 else 0).coerceAtLeast(0)
        GlyphTextRun(
            run = run,
            font = font,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            advance = (SvgFontResolver.textRunAdvance(font, run.text, fontSize, vertical = vertical, glyphNames = run.glyphNames) +
                letterSpacing * letterSpacingGapCount).coerceAtLeast(fontSize * 0.15f),
            glyphCount = glyphCount,
            lengthOwner = lengthOwner,
            lengthOwners = lengthOwners,
            targetTextLength = lengthOwner?.let { explicitTextLength(it) },
            lengthAdjust = lengthOwner?.let { explicitLengthAdjust(it) } ?: "spacing",
            spacingAdjustment = 0f,
            glyphAxisScale = 1f,
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
            baselineShift = resolvedBaselineShift(run.element, fontSize, element),
            vertical = vertical,
            explicitX = if (!isParentTextRun && run.element.hasAttribute("x")) textNumericAttr(run.element, "x") else null,
            explicitY = if (!isParentTextRun && run.element.hasAttribute("y")) textNumericAttr(run.element, "y") else null,
            dx = if (!isParentTextRun) textNumericAttr(run.element, "dx") ?: 0f else 0f,
            dy = if (!isParentTextRun) textNumericAttr(run.element, "dy") ?: 0f else 0f,
            xValues = textNumericListAttr(run.element, "x"),
            yValues = textNumericListAttr(run.element, "y"),
            dxValues = textNumericListAttr(run.element, "dx"),
            dyValues = textNumericListAttr(run.element, "dy"),
            rotateOwners = buildList {
                var current: Node? = run.element
                while (current is Element) {
                    if (current.hasAttribute("rotate") && textNumericListAttr(current, "rotate").isNotEmpty()) add(current)
                    if (current === element) break
                    current = current.parentNode
                }
            },
            sourceTag = run.element.tagName.substringAfter(":").lowercase(),
            glyphNames = run.glyphNames
        )
    }

    if (rawPreparedRuns.size != runs.size || rawPreparedRuns.isEmpty()) return false

    data class TextLengthGroup(
        val owner: Element,
        val target: Float,
        val mode: String,
        val naturalAdvance: Float,
        val glyphCount: Int,
        val spacingAdjustment: Float,
        val glyphAxisScale: Float,
        val parentOwner: Element?
    )

    val allLengthOwners = rawPreparedRuns
        .flatMap { it.lengthOwners }
        .distinct()

    val parentOwnerByOwner = allLengthOwners.associateWith { owner ->
        var current: Node? = owner.parentNode
        var parent: Element? = null
        while (current is Element) {
            if (explicitTextLength(current) != null) {
                parent = current
                break
            }
            if (current === element) break
            current = current.parentNode
        }
        parent
    }

    fun ownerDepth(owner: Element): Int {
        var depth = 0
        var current: Node? = owner.parentNode
        while (current is Element) {
            depth++
            if (current === element) break
            current = current.parentNode
        }
        return depth
    }

    val lengthGroupsMutable = linkedMapOf<Element, TextLengthGroup>()
    for (owner in allLengthOwners.sortedByDescending(::ownerDepth)) {
        val target = explicitTextLength(owner) ?: continue
        val mode = explicitLengthAdjust(owner)
        val directNaturalAdvance = rawPreparedRuns
            .filter { it.lengthOwner === owner }
            .sumOf { it.advance.toDouble() }
            .toFloat()
        val directGlyphCount = rawPreparedRuns
            .filter { it.lengthOwner === owner }
            .sumOf { it.glyphCount }
        val childGroups = lengthGroupsMutable.values.filter { it.parentOwner === owner }
        val naturalAdvance = directNaturalAdvance + childGroups.sumOf { it.target.toDouble() }.toFloat()
        val glyphCount = directGlyphCount + childGroups.sumOf { it.glyphCount }
        val spacingAdjustment = if (mode == "spacing" && glyphCount > 1) {
            (target - naturalAdvance) / (glyphCount - 1).toFloat()
        } else 0f
        val glyphAxisScale = if (mode == "spacingandglyphs" && naturalAdvance > 0.0001f) {
            target / naturalAdvance
        } else 1f

        if (mode == "spacing" && glyphCount > 1) activeTextLengthSpacingAdjustments++
        if (mode == "spacingandglyphs" && naturalAdvance > 0.0001f) {
            activeTextLengthSpacingAndGlyphsAdjustments++
        }

        lengthGroupsMutable[owner] = TextLengthGroup(
            owner = owner,
            target = target,
            mode = mode,
            naturalAdvance = naturalAdvance,
            glyphCount = glyphCount,
            spacingAdjustment = spacingAdjustment,
            glyphAxisScale = glyphAxisScale,
            parentOwner = parentOwnerByOwner[owner]
        )
    }
    val lengthGroups = lengthGroupsMutable.toMap()

    val preparedRuns = rawPreparedRuns.map { prepared ->
        val nearestGroup = prepared.lengthOwner?.let { lengthGroups[it] }
        val cumulativeAxisScale = prepared.lengthOwners
            .mapNotNull { lengthGroups[it] }
            .fold(1f) { acc, group -> acc * group.glyphAxisScale }
        prepared.copy(
            targetTextLength = nearestGroup?.target,
            lengthAdjust = nearestGroup?.mode ?: "spacing",
            spacingAdjustment = nearestGroup?.spacingAdjustment ?: 0f,
            glyphAxisScale = cumulativeAxisScale
        )
    }

    val totalNaturalAdvance = rawPreparedRuns.sumOf { it.advance.toDouble() }.toFloat()
    val topLevelGroups = lengthGroups.values.filter { it.parentOwner == null }
    val totalAdvance = totalNaturalAdvance + topLevelGroups.sumOf { group ->
        val rawSubtreeAdvance = rawPreparedRuns
            .filter { group.owner in it.lengthOwners }
            .sumOf { it.advance.toDouble() }
        group.target.toDouble() - rawSubtreeAdvance
    }.toFloat()

    val textStyle = element.getAttribute("style").ifBlank { null }
    val rootXValues = textNumericListAttr(element, "x")
    val rootYValues = textNumericListAttr(element, "y")
    val rootDxValues = textNumericListAttr(element, "dx")
    val rootDyValues = textNumericListAttr(element, "dy")
    val baseX = rootXValues.firstOrNull() ?: 0f
    val baseY = rootYValues.firstOrNull() ?: 0f
    val baseDx = rootDxValues.firstOrNull() ?: 0f
    val baseDy = rootDyValues.firstOrNull() ?: 0f
    val baseAnchor = normalizedTextAnchor(element)
    val baseVertical = isVerticalWritingMode(element)
    val hasPositionedRuns = runs.any { it.element !== element && textHasPositionAttribute(it.element) }

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
    var globalGlyphIndex = 0

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
    val totalGlyphCount = rawPreparedRuns.sumOf { it.glyphCount }
    val remainingGlyphsByLengthOwner = lengthGroups.mapValues { (_, group) -> group.glyphCount }.toMutableMap()
    val rotateIndexByOwner = mutableMapOf<Element, Int>()

    for (prepared in preparedRuns) {
        if (hasPositionedRuns) {
            if (prepared.explicitX != null) currentX = prepared.explicitX
            if (prepared.explicitY != null) currentY = prepared.explicitY
            currentX += prepared.dx
            currentY += prepared.dy
            if (prepared.vertical) {
                currentY = when (prepared.anchor) {
                    "middle" -> currentY - (prepared.targetTextLength ?: prepared.advance) / 2f
                    "end" -> currentY - (prepared.targetTextLength ?: prepared.advance)
                    else -> currentY
                }
            } else {
                currentX = when (prepared.anchor) {
                    "middle" -> currentX - (prepared.targetTextLength ?: prepared.advance) / 2f
                    "end" -> currentX - (prepared.targetTextLength ?: prepared.advance)
                    else -> currentX
                }
            }
        }

        if (prepared.fontFamily.isNotBlank()) activeTextFontFamilies.add(prepared.fontFamily)
        if (prepared.fontWeight.isNotBlank()) activeTextFontWeights.add(prepared.fontWeight)

        val scale = prepared.fontSize / prepared.font.unitsPerEm
        val resolvedGlyphs = SvgFontResolver.resolveGlyphs(prepared.font, prepared.run.text, prepared.glyphNames)
        var runSourceOffset = 0
        for ((glyphIndex, resolved) in resolvedGlyphs.withIndex()) {
            val consumedChars = resolved.consumedChars.coerceAtLeast(1)
            val sourceEnd = (runSourceOffset + consumedChars).coerceAtMost(prepared.run.text.length)
            val sourceSegment = prepared.run.text.substring(runSourceOffset, sourceEnd)
            val sourceIsWhitespace = sourceSegment.isNotEmpty() && sourceSegment.all { it.isWhitespace() }
            // Index 0 is already applied when the root cursor is initialized or when a
            // positioned <tspan> begins. Subsequent local values position later glyphs.
            // Root lists continue across child spans when the child does not override them.
            val localX = if (glyphIndex > 0) prepared.xValues.getOrNull(glyphIndex) else null
            val localY = if (glyphIndex > 0) prepared.yValues.getOrNull(glyphIndex) else null
            val localDx = if (glyphIndex > 0) prepared.dxValues.getOrNull(glyphIndex) else null
            val localDy = if (glyphIndex > 0) prepared.dyValues.getOrNull(glyphIndex) else null

            val rootX = if (globalGlyphIndex > 0 && (prepared.run.element === element || prepared.xValues.isEmpty())) {
                rootXValues.getOrNull(globalGlyphIndex)
            } else null
            val rootY = if (globalGlyphIndex > 0 && (prepared.run.element === element || prepared.yValues.isEmpty())) {
                rootYValues.getOrNull(globalGlyphIndex)
            } else null
            val rootDx = if (globalGlyphIndex > 0 && (prepared.run.element === element || prepared.dxValues.isEmpty())) {
                rootDxValues.getOrNull(globalGlyphIndex)
            } else null
            val rootDy = if (globalGlyphIndex > 0 && (prepared.run.element === element || prepared.dyValues.isEmpty())) {
                rootDyValues.getOrNull(globalGlyphIndex)
            } else null

            if (localX != null || rootX != null) currentX = localX ?: rootX!!
            if (localY != null || rootY != null) currentY = localY ?: rootY!!
            currentX += localDx ?: rootDx ?: 0f
            currentY += localDy ?: rootDy ?: 0f

            val glyph = resolved.glyph
            val isMissingGlyphFallback = prepared.font.missingGlyph === glyph

            val rotateOwner = prepared.rotateOwners.firstOrNull()
            val rotateDegrees = rotateOwner?.let { owner ->
                val values = textNumericListAttr(owner, "rotate")
                val index = rotateIndexByOwner[owner] ?: 0
                when {
                    values.isEmpty() -> 0f
                    index < values.size -> values[index]
                    else -> values.last()
                }
            } ?: 0f

            if (!resolved.isWhitespace) {
                val baselineOffset = embeddedAlphabeticBaselineOffset(
                    font = prepared.font,
                    fontSize = prepared.fontSize,
                    baseline = prepared.baseline
                )
                val basePlacement = if (prepared.vertical) {
                    AffineTransform(
                        a = scale,
                        b = 0f,
                        c = 0f,
                        d = -scale * prepared.glyphAxisScale,
                        e = currentX + baselineOffset - prepared.baselineShift,
                        f = currentY
                    )
                } else {
                    AffineTransform(
                        a = scale * prepared.glyphAxisScale,
                        b = 0f,
                        c = 0f,
                        d = -scale,
                        e = currentX,
                        f = currentY + baselineOffset - prepared.baselineShift
                    )
                }
                val placement = if (abs(rotateDegrees) > 0.0001f) {
                    val radians = Math.toRadians(rotateDegrees.toDouble())
                    val cos = kotlin.math.cos(radians).toFloat()
                    val sin = kotlin.math.sin(radians).toFloat()
                    activeTextGlyphRotationsApplied++
                    AffineTransform(
                        a = cos * basePlacement.a - sin * basePlacement.b,
                        b = sin * basePlacement.a + cos * basePlacement.b,
                        c = cos * basePlacement.c - sin * basePlacement.d,
                        d = sin * basePlacement.c + cos * basePlacement.d,
                        e = basePlacement.e,
                        f = basePlacement.f
                    )
                } else basePlacement
                var glyphPathData = glyph.pathData
                if (glyph.transform != null) {
                    glyphPathData = SvgPathDataTransformer.applyAffineTransform(glyphPathData, glyph.transform)
                        ?: glyphPathData
                }

                var pathData = SvgPathDataTransformer.applyAffineTransform(glyphPathData, placement) ?: glyphPathData
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
                output.appendLine("${indent}    android:pathData=\"${escapeXml(pathData)}\"")
                output.appendLine("${indent}    android:fillColor=\"$safeFill\"")
                if (fillAlpha != null) output.appendLine("${indent}    android:fillAlpha=\"$fillAlpha\"")
                if (safeStroke != null) {
                    output.appendLine("${indent}    android:strokeColor=\"$safeStroke\"")
                    output.appendLine("${indent}    android:strokeWidth=\"${prepared.strokeWidth?.takeIf { it.isNotBlank() } ?: "1"}\"")
                    if (strokeAlpha != null) output.appendLine("${indent}    android:strokeAlpha=\"$strokeAlpha\"")
                }
                output.appendLine("${indent}/>")

                if (SvgFontResolver.hasGlyphSpecificAdvance(glyph, vertical = prepared.vertical)) activeTextGlyphSpecificAdvances++ else activeTextDefaultFontAdvances++
                if (isMissingGlyphFallback && !sourceIsWhitespace) activeTextMissingGlyphFallbacks++

                if (resolved.fromGlyphName) activeTextGlyphNameLookups++
            }

            val nextGlyph = resolvedGlyphs.getOrNull(glyphIndex + 1)?.glyph
            val kern = kerningAdjustment(prepared.font, glyph, nextGlyph, vertical = prepared.vertical, recordMatch = true)
            if (abs(kern) > 0.001f) activeTextKerningAdjustmentsApplied++
            val owningGroups = prepared.lengthOwners.mapNotNull { lengthGroups[it] }
            val extraSpacing = owningGroups.sumOf { group ->
                val remaining = remainingGlyphsByLengthOwner[group.owner] ?: 0
                if (group.mode == "spacing" && remaining > 1) {
                    group.spacingAdjustment.toDouble()
                } else 0.0
            }.toFloat()
            val naturalAdvance = (SvgFontResolver.glyphAdvance(prepared.font, glyph, vertical = prepared.vertical) - kern) * scale
            val hasFollowingGlyph = globalGlyphIndex < totalGlyphCount - 1
            val authoredSpacing = if (hasFollowingGlyph) prepared.letterSpacing * prepared.glyphAxisScale else 0f
            if (hasFollowingGlyph && abs(authoredSpacing) > 0.0001f) {
                activeTextLetterSpacingAdjustmentsApplied++
            }
            val advance = naturalAdvance * prepared.glyphAxisScale + authoredSpacing + extraSpacing
            for (group in owningGroups) {
                val remaining = remainingGlyphsByLengthOwner[group.owner] ?: 0
                remainingGlyphsByLengthOwner[group.owner] = (remaining - 1).coerceAtLeast(0)
            }
            if (prepared.vertical) {
                currentY += advance
            } else {
                currentX += advance
            }
            for (owner in prepared.rotateOwners) {
                rotateIndexByOwner[owner] = (rotateIndexByOwner[owner] ?: 0) + 1
            }
            if (!resolved.isWhitespace) emittedGlyphs++
            globalGlyphIndex++
            runSourceOffset = sourceEnd
        }
    }

    if (emittedGlyphs == 0) return false
    output.appendLine()
    activeTextElementsConvertedToPaths++
    activeTextGlyphPathsEmitted += emittedGlyphs
    return true
}

fun appendTextApproximation(
    output: StringBuilder,
    element: Element,
    indent: String,
    escapeXml: (String) -> String,
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
        val baselineShift: Float,
        val explicitX: Float?,
        val explicitY: Float?,
        val dx: Float,
        val dy: Float,
        val sourceTag: String,
        val glyphNames: List<String>?
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
        val letterSpacing = resolvedLetterSpacing(run.element, fontSize)
        val width = (textLength ?: (charCount * fontSize * widthFactor +
            letterSpacing * (charCount - 1).coerceAtLeast(0))).coerceAtLeast(fontSize * 0.35f)
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
            baselineShift = resolvedBaselineShift(run.element, fontSize, element),
            explicitX = if (!isParentTextRun && run.element.hasAttribute("x")) textNumericAttr(run.element, "x") else null,
            explicitY = if (!isParentTextRun && run.element.hasAttribute("y")) textNumericAttr(run.element, "y") else null,
            dx = if (!isParentTextRun) textNumericAttr(run.element, "dx") ?: 0f else 0f,
            dy = if (!isParentTextRun) textNumericAttr(run.element, "dy") ?: 0f else 0f,
            sourceTag = run.element.tagName.substringAfter(":").lowercase(),
            glyphNames = run.glyphNames
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
            top = textTopForBaseline(currentY - prepared.baselineShift, 0f, prepared.height, prepared.baseline)
            currentX += prepared.width
            positionMode = "cursor"
        } else {
            left = currentLeft
            top = textTopForBaseline(baseY - prepared.baselineShift, baseDy, prepared.height, baseline)
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
        output.appendLine("${indent}     \"${escapeXml(run.text)}\"")
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
            output.appendLine("${indent}     font-family=\"${escapeXml(prepared.fontFamily)}\"")
        }
        if (prepared.fontWeight.isNotBlank()) {
            output.appendLine("${indent}     font-weight=\"${escapeXml(prepared.fontWeight)}\"")
        }
        output.appendLine("${indent}-->")
        output.appendLine("${indent}<path")
        output.appendLine("${indent}    android:pathData=\"${escapeXml(pathData)}\"")
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


}
