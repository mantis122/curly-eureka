package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class SvgGlyphOutline(
    val unicode: String,
    val pathData: String,
    val horizAdvX: Float? = null,
    val vertAdvY: Float? = null,
    val glyphName: String? = null,
    val transform: AffineTransform? = null
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

data class ResolvedTextGlyph(
    val glyph: SvgGlyphOutline,
    val consumedChars: Int,
    val fromGlyphName: Boolean,
    val isWhitespace: Boolean = false
)

object SvgFontResolver {

    fun collectDefinitions(svg: String): Map<String, SvgFontDefinition> {
        val result = linkedMapOf<String, SvgFontDefinition>()

        fun numberAttr(element: Element, name: String, fallback: Float): Float =
            element.getAttribute(name).trim().removeSuffix("px").toFloatOrNull() ?: fallback

        fun normalizedFamilyNames(raw: String): Set<String> = raw.split(',')
            .map { it.trim().trim('\'', '"') }
            .filter { it.isNotBlank() }
            .toSet()

        fun listAttr(element: Element, name: String): Set<String> = element.getAttribute(name)
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        fun glyphTransform(element: Element): AffineTransform? {
            val style = element.getAttribute("style").ifBlank { null }
            val rawTransform = element.getAttribute("transform")
                .ifBlank { SvgPaintResolver.styleValue(style, "transform") ?: "" }
            if (rawTransform.isBlank()) return null

            val rawOrigin = element.getAttribute("transform-origin")
                .ifBlank { SvgPaintResolver.styleValue(style, "transform-origin") ?: "" }
            val origin = SvgTransformParser.parseTransformOrigin(rawOrigin)
            return SvgTransformParser.combineTransformListToMatrix(
                SvgTransformParser.parseTransformList(rawTransform),
                origin
            )
        }

        fun kerningPair(element: Element): SvgKerningPair? {
            val amount = element.getAttribute("k").trim().toFloatOrNull() ?: return null
            val firstUnicode = listAttr(element, "u1")
            val secondUnicode = listAttr(element, "u2")
            val firstNames = listAttr(element, "g1")
            val secondNames = listAttr(element, "g2")
            if ((firstUnicode.isEmpty() && firstNames.isEmpty()) ||
                (secondUnicode.isEmpty() && secondNames.isEmpty())
            ) return null
            return SvgKerningPair(firstUnicode, secondUnicode, firstNames, secondNames, amount)
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
                if (element.tagName.substringAfter(":").lowercase() == "font") {
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
                                val glyphNames = buildList {
                                    addAll(childElement.getAttribute("glyph-name").split(',')
                                        .map { it.trim() }.filter { it.isNotBlank() })
                                    childElement.getAttribute("id").trim()
                                        .takeIf { it.isNotBlank() }?.let(::add)
                                }.distinct()
                                if (d.isNotBlank() && (unicode.isNotEmpty() || glyphNames.isNotEmpty())) {
                                    val glyph = SvgGlyphOutline(
                                        unicode, d,
                                        childElement.getAttribute("horiz-adv-x").trim().toFloatOrNull(),
                                        childElement.getAttribute("vert-adv-y").trim().toFloatOrNull(),
                                        glyphNames.firstOrNull(), glyphTransform(childElement)
                                    )
                                    if (unicode.isNotEmpty()) glyphs[unicode] = glyph
                                    glyphNames.forEach { glyphsByName[it] = glyph }
                                }
                            }
                            "missing-glyph" -> {
                                val d = childElement.getAttribute("d").trim()
                                if (d.isNotBlank()) {
                                    missingGlyph = SvgGlyphOutline(
                                        "", d,
                                        childElement.getAttribute("horiz-adv-x").trim().toFloatOrNull(),
                                        childElement.getAttribute("vert-adv-y").trim().toFloatOrNull(),
                                        childElement.getAttribute("glyph-name").trim().ifBlank { null },
                                        glyphTransform(childElement)
                                    )
                                }
                            }
                            "hkern" -> kerningPair(childElement)?.let(horizontalKerningPairs::add)
                            "vkern" -> kerningPair(childElement)?.let(verticalKerningPairs::add)
                        }
                    }

                    if (id.isNotBlank()) familyNames.add(id)
                    if (id.isNotBlank() && (glyphs.isNotEmpty() || glyphsByName.isNotEmpty() || missingGlyph != null)) {
                        result[id] = SvgFontDefinition(
                            id, familyNames, unitsPerEm, ascent, descent, fontHorizAdvX,
                            (fontVertAdvYRaw ?: unitsPerEm).coerceAtLeast(1f), glyphs, glyphsByName,
                            horizontalKerningPairs, verticalKerningPairs, missingGlyph
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

    fun findMatchingFont(
        definitions: Map<String, SvgFontDefinition>,
        fontFamily: String?
    ): SvgFontDefinition? {
        if (definitions.isEmpty()) return null
        val candidates = fontFamily.orEmpty().split(',')
            .map { it.trim().trim('\'', '"') }.filter { it.isNotBlank() }
        if (candidates.isEmpty() && definitions.size == 1) return definitions.values.first()
        for (candidate in candidates) {
            definitions.values.firstOrNull { font ->
                font.id.equals(candidate, true) || font.familyNames.any { it.equals(candidate, true) }
            }?.let { return it }
        }
        return null
    }

    fun resolveGlyphs(font: SvgFontDefinition, text: String, glyphNames: List<String>? = null): List<ResolvedTextGlyph> {
        if (!glyphNames.isNullOrEmpty()) {
            return glyphNames.mapNotNull { name ->
                val named = glyphByName(font, name)
                val glyph = named ?: font.missingGlyph ?: return@mapNotNull null
                ResolvedTextGlyph(glyph, 0, named != null)
            }
        }
        val result = mutableListOf<ResolvedTextGlyph>()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val consumedChars = Character.charCount(codePoint)

            // Whitespace affects text layout but normally has no drawable outline.
            // Represent it as an invisible advance-only glyph instead of falling back
            // to <missing-glyph>, which would incorrectly emit a path and warning.
            if (Character.isWhitespace(codePoint)) {
                result.add(
                    ResolvedTextGlyph(
                        glyph = SvgGlyphOutline(
                            unicode = String(Character.toChars(codePoint)),
                            pathData = ""
                        ),
                        consumedChars = consumedChars,
                        fromGlyphName = false,
                        isWhitespace = true
                    )
                )
                index += consumedChars
                continue
            }

            val match = glyphForText(font, text.substring(index))
            if (match == null) {
                index += consumedChars
            } else {
                result.add(ResolvedTextGlyph(match.first, match.second, false))
                index += match.second
            }
        }
        return result
    }

    fun glyphAdvance(font: SvgFontDefinition, glyph: SvgGlyphOutline?, vertical: Boolean = false): Float =
        if (vertical) glyph?.vertAdvY?.takeIf { it > 0f } ?: font.vertAdvY
        else glyph?.horizAdvX?.takeIf { it > 0f } ?: font.horizAdvX

    fun hasGlyphSpecificAdvance(glyph: SvgGlyphOutline, vertical: Boolean = false): Boolean =
        if (vertical) glyph.vertAdvY?.takeIf { it > 0f } != null
        else glyph.horizAdvX?.takeIf { it > 0f } != null

    fun matchingKerningPair(
        font: SvgFontDefinition,
        first: SvgGlyphOutline?,
        second: SvgGlyphOutline?,
        vertical: Boolean = false
    ): SvgKerningPair? {
        if (first == null || second == null) return null
        val pairs = if (vertical) font.verticalKerningPairs else font.horizontalKerningPairs
        return pairs.firstOrNull { candidate ->
            (matchesUnicode(candidate.firstUnicodeValues, first) || matchesName(candidate.firstGlyphNames, first)) &&
                (matchesUnicode(candidate.secondUnicodeValues, second) || matchesName(candidate.secondGlyphNames, second))
        }
    }

    fun textRunAdvance(font: SvgFontDefinition, text: String, fontSize: Float, vertical: Boolean = false, glyphNames: List<String>? = null): Float {
        val scale = fontSize / font.unitsPerEm
        val glyphs = resolveGlyphs(font, text, glyphNames)
        var total = 0f
        var previous: SvgGlyphOutline? = null
        for (resolved in glyphs) {
            total -= (matchingKerningPair(font, previous, resolved.glyph, vertical)?.amount ?: 0f) * scale
            total += glyphAdvance(font, resolved.glyph, vertical) * scale
            previous = resolved.glyph
        }
        return total
    }

    fun textRunGlyphCount(font: SvgFontDefinition, text: String, glyphNames: List<String>? = null): Int =
        resolveGlyphs(font, text, glyphNames).size

    private fun glyphByName(font: SvgFontDefinition, name: String): SvgGlyphOutline? =
        font.glyphsByName[name] ?: font.glyphsByName.entries.firstOrNull { it.key.equals(name, true) }?.value

    private fun glyphForText(font: SvgFontDefinition, remainingText: String): Pair<SvgGlyphOutline, Int>? {
        if (remainingText.isEmpty()) return null
        var best = font.glyphsByUnicode[remainingText.take(1)]?.let { it to 1 }
        for ((unicode, glyph) in font.glyphsByUnicode) {
            if (unicode.length > (best?.second ?: 0) && remainingText.startsWith(unicode)) best = glyph to unicode.length
        }
        return best ?: font.missingGlyph?.let { it to Character.charCount(remainingText.codePointAt(0)) }
    }

    private fun matchesUnicode(values: Set<String>, glyph: SvgGlyphOutline): Boolean =
        values.isNotEmpty() && (glyph.unicode in values || values.any { it.length == 1 && glyph.unicode.length == 1 && it == glyph.unicode })

    private fun matchesName(values: Set<String>, glyph: SvgGlyphOutline): Boolean =
        glyph.glyphName?.let { it in values } ?: false
}
