package com.example.svgvectorconverter

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
    val direct = listOf(
        element.getAttribute("glyph-name"),
        element.getAttribute("glyphRef"),
        element.getAttribute("glyph-ref")
    ).firstOrNull { it.isNotBlank() }

    val href = element.getAttribute("href").ifBlank { element.getAttribute("xlink:href") }
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
    val runs = textApproximationRuns(element)
    if (runs.isEmpty() || fontDefinitions.isEmpty()) return false

    data class GlyphTextRun(
        val run: TextApproximationRun,
        val font: SvgFontDefinition,
        val fontSize: Float,
        val fontFamily: String,
        val fontWeight: String,
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
        val vertical: Boolean,
        val explicitX: Float?,
        val explicitY: Float?,
        val dx: Float,
        val dy: Float,
        val xValues: List<Float>,
        val yValues: List<Float>,
        val dxValues: List<Float>,
        val dyValues: List<Float>,
        val sourceTag: String,
        val glyphNames: List<String>?
    )

    val rawPreparedRuns = runs.mapNotNull { run ->
        val runStyle = run.element.getAttribute("style").ifBlank { null }
        val fontSize = (inheritedTextStyleValue(run.element, "font-size")
            ?.let { Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""").find(it)?.value }
            ?.toFloatOrNull()
            ?: 16f).coerceAtLeast(1f)
        val fontFamily = inheritedTextStyleValue(run.element, "font-family").orEmpty()
        val font = SvgFontResolver.findMatchingFont(fontDefinitions, fontFamily) ?: return@mapNotNull null
        val fontWeight = normalizeTextFontWeight(inheritedTextStyleValue(run.element, "font-weight"))
        val vertical = isVerticalWritingMode(run.element)
        val isParentTextRun = run.element === element
        val lengthOwners = textLengthOwners(run.element, element)
        val lengthOwner = lengthOwners.firstOrNull()
        GlyphTextRun(
            run = run,
            font = font,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            advance = SvgFontResolver.textRunAdvance(font, run.text, fontSize, vertical = vertical, glyphNames = run.glyphNames).coerceAtLeast(fontSize * 0.15f),
            glyphCount = SvgFontResolver.textRunGlyphCount(font, run.text, run.glyphNames),
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
            vertical = vertical,
            explicitX = if (!isParentTextRun && run.element.hasAttribute("x")) textNumericAttr(run.element, "x") else null,
            explicitY = if (!isParentTextRun && run.element.hasAttribute("y")) textNumericAttr(run.element, "y") else null,
            dx = if (!isParentTextRun) textNumericAttr(run.element, "dx") ?: 0f else 0f,
            dy = if (!isParentTextRun) textNumericAttr(run.element, "dy") ?: 0f else 0f,
            xValues = textNumericListAttr(run.element, "x"),
            yValues = textNumericListAttr(run.element, "y"),
            dxValues = textNumericListAttr(run.element, "dx"),
            dyValues = textNumericListAttr(run.element, "dy"),
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
    val remainingGlyphsByLengthOwner = lengthGroups.mapValues { (_, group) -> group.glyphCount }.toMutableMap()

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
        for ((glyphIndex, resolved) in resolvedGlyphs.withIndex()) {
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
            val placement = if (prepared.vertical) {
                AffineTransform(
                    a = scale,
                    b = 0f,
                    c = 0f,
                    d = -scale * prepared.glyphAxisScale,
                    e = currentX,
                    f = currentY
                )
            } else {
                AffineTransform(
                    a = scale * prepared.glyphAxisScale,
                    b = 0f,
                    c = 0f,
                    d = -scale,
                    e = currentX,
                    f = currentY
                )
            }
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
            if (isMissingGlyphFallback) activeTextMissingGlyphFallbacks++

            if (resolved.fromGlyphName) activeTextGlyphNameLookups++

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
            val advance = naturalAdvance * prepared.glyphAxisScale + extraSpacing
            for (group in owningGroups) {
                val remaining = remainingGlyphsByLengthOwner[group.owner] ?: 0
                remainingGlyphsByLengthOwner[group.owner] = (remaining - 1).coerceAtLeast(0)
            }
            if (prepared.vertical) {
                currentY += advance
            } else {
                currentX += advance
            }
            emittedGlyphs++
            globalGlyphIndex++
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
