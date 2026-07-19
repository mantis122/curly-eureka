package com.example.svgvectorconverter

import java.math.BigDecimal

/**
 * Performs conservative cleanup of emitted VectorDrawable XML.
 *
 * The optimizer is intentionally lossless. It:
 * - normalizes pathData syntax without rounding or changing geometry;
 * - removes <path> elements that provably cannot draw anything;
 * - removes groups left empty after path removal.
 *
 * Clip paths are never treated as ordinary drawable paths, and uncertain paint
 * cases are retained rather than risking a visual change.
 */
internal object SvgPathDataOptimizer {
    data class Stats(
        val pathCount: Int = 0,
        val charactersBefore: Int = 0,
        val charactersAfter: Int = 0,
        val repeatedCommandsRemoved: Int = 0,
        val numbersNormalized: Int = 0,
        val emptyPathDataRemoved: Int = 0,
        val moveOnlyPathsRemoved: Int = 0,
        val invisiblePathsRemoved: Int = 0,
        val emptyGroupsRemoved: Int = 0,
        val redundantGroupsFlattened: Int = 0,
        val compatiblePathsMerged: Int = 0,
        val exactDuplicatePathsRemoved: Int = 0,
        val translatedGroupsFlattened: Int = 0,
        val translatedPaths: Int = 0,
        val scaledGroupsFlattened: Int = 0,
        val scaledPaths: Int = 0,
        val scaledStrokeWidths: Int = 0,
        val identityTransformAttributesRemoved: Int = 0,
        val shorterCommandFormsSelected: Int = 0,
        val relativeCommandsSelected: Int = 0,
        val axisCommandsSelected: Int = 0,
        val xmlCharactersBefore: Int = 0,
        val xmlCharactersAfter: Int = 0
    ) {
        val charactersSaved: Int
            get() = (charactersBefore - charactersAfter).coerceAtLeast(0)

        val reductionPercent: Double
            get() = if (charactersBefore > 0) {
                charactersSaved * 100.0 / charactersBefore.toDouble()
            } else {
                0.0
            }

        val pathsRemoved: Int
            get() = emptyPathDataRemoved + moveOnlyPathsRemoved + invisiblePathsRemoved

        val xmlCharactersSaved: Int
            get() = (xmlCharactersBefore - xmlCharactersAfter).coerceAtLeast(0)

        val xmlReductionPercent: Double
            get() = if (xmlCharactersBefore > 0) {
                xmlCharactersSaved * 100.0 / xmlCharactersBefore.toDouble()
            } else {
                0.0
            }
    }

    data class Result(
        val xml: String,
        val stats: Stats
    )

    private val pathDataAttributeRegex = Regex("""android:pathData\s*=\s*\"([^\"]*)\"""")
    private val tokenRegex = Regex(
        """[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:(?:\d+\.\d*)|(?:\.\d+)|(?:\d+))(?:[eE][-+]?\d+)?"""
    )
    private val pathElementRegex = Regex(
        """<path\b(?:\"[^\"]*\"|'[^']*'|[^>])*(?:/\s*>|>[\s\S]*?</path\s*>)""",
        RegexOption.IGNORE_CASE
    )
    private val innermostGroupRegex = Regex(
        """<group\b(?:\"[^\"]*\"|'[^']*'|[^>])*?>((?:(?!<group\b)[\s\S])*?)</group\s*>""",
        RegexOption.IGNORE_CASE
    )
    private val xmlCommentRegex = Regex("""<!--[\s\S]*?-->""")
    private val androidColorAttributeRegex = Regex(
        """(android:(?:fillColor|strokeColor)\s*=\s*)(["'])(#[0-9a-fA-F]{3,4})\2""",
        RegexOption.IGNORE_CASE
    )
    private val adjacentSimplePathRegex = Regex(
        """(<path\b(?:"[^"]*"|'[^']*'|[^>])*?/\s*>)([\s\n]*(?:<!--[\s\S]*?-->[\s\n]*)*)(<path\b(?:"[^"]*"|'[^']*'|[^>])*?/\s*>)""",
        RegexOption.IGNORE_CASE
    )
    private val androidAttributeRegex = Regex(
        """\bandroid:([A-Za-z0-9_]+)\s*=\s*(["'])(.*?)\2""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun optimizeVectorXml(xml: String): Result {
        var pathCount = 0
        var charactersBefore = 0
        var repeatedCommandsRemoved = 0
        var numbersNormalized = 0
        var shorterCommandFormsSelected = 0
        var relativeCommandsSelected = 0
        var axisCommandsSelected = 0

        val syntaxOptimizedXml = pathDataAttributeRegex.replace(xml) { match ->
            val original = match.groupValues[1]
            val optimized = optimizePathData(original)

            pathCount++
            charactersBefore += original.length
            repeatedCommandsRemoved += optimized.repeatedCommandsRemoved
            numbersNormalized += optimized.numbersNormalized
            shorterCommandFormsSelected += optimized.shorterCommandFormsSelected
            relativeCommandsSelected += optimized.relativeCommandsSelected
            axisCommandsSelected += optimized.axisCommandsSelected

            "android:pathData=\"${optimized.pathData}\""
        }

        val colorNormalizedXml = normalizeAndroidColorAttributes(syntaxOptimizedXml)

        var emptyPathDataRemoved = 0
        var moveOnlyPathsRemoved = 0
        var invisiblePathsRemoved = 0

        val pathsPrunedXml = pathElementRegex.replace(colorNormalizedXml) { match ->
            val element = match.value
            val pathData = attributeValue(element, "android:pathData")

            when {
                pathData == null -> element
                pathData.isBlank() -> {
                    emptyPathDataRemoved++
                    ""
                }
                !hasDrawableGeometry(pathData) -> {
                    moveOnlyPathsRemoved++
                    ""
                }
                isDefinitelyInvisible(element) -> {
                    invisiblePathsRemoved++
                    ""
                }
                else -> element
            }
        }

        val groupCleanup = removeEmptyGroups(pathsPrunedXml)
        val identityCleanup = removeIdentityGroupTransformAttributes(groupCleanup.xml)
        val groupFlattening = flattenRedundantGroups(identityCleanup.xml)
        val scaleFlattening = flattenUniformPositiveScaleGroups(groupFlattening.xml)
        val translationFlattening = flattenTranslationOnlyGroups(scaleFlattening.xml)
        val duplicateRemoval = removeExactAdjacentDuplicatePaths(translationFlattening.xml)
        val pathMerging = mergeCompatibleAdjacentPaths(duplicateRemoval.xml)
        val finalXml = formatVectorXml(pathMerging.xml)
        val charactersAfter = pathDataAttributeRegex.findAll(finalXml)
            .sumOf { it.groupValues[1].length }

        return Result(
            xml = finalXml,
            stats = Stats(
                pathCount = pathCount,
                charactersBefore = charactersBefore,
                charactersAfter = charactersAfter,
                repeatedCommandsRemoved = repeatedCommandsRemoved,
                numbersNormalized = numbersNormalized,
                emptyPathDataRemoved = emptyPathDataRemoved,
                moveOnlyPathsRemoved = moveOnlyPathsRemoved,
                invisiblePathsRemoved = invisiblePathsRemoved,
                emptyGroupsRemoved = groupCleanup.removedCount,
                redundantGroupsFlattened = groupFlattening.flattenedCount,
                compatiblePathsMerged = pathMerging.mergedCount,
                exactDuplicatePathsRemoved = duplicateRemoval.removedCount,
                translatedGroupsFlattened = translationFlattening.flattenedGroups,
                translatedPaths = translationFlattening.translatedPaths,
                scaledGroupsFlattened = scaleFlattening.flattenedGroups,
                scaledPaths = scaleFlattening.scaledPaths,
                scaledStrokeWidths = scaleFlattening.scaledStrokeWidths,
                identityTransformAttributesRemoved = identityCleanup.removedAttributes,
                shorterCommandFormsSelected = shorterCommandFormsSelected,
                relativeCommandsSelected = relativeCommandsSelected,
                axisCommandsSelected = axisCommandsSelected,
                xmlCharactersBefore = xml.length,
                xmlCharactersAfter = finalXml.length
            )
        )
    }


    /**
     * Expands CSS/SVG shorthand hex colors into Android-compatible forms.
     *
     * #RGB  -> #RRGGBB
     * #RGBA -> #AARRGGBB
     *
     * SVG/CSS places alpha last in #RGBA, while Android places alpha first.
     */
    private fun normalizeAndroidColorAttributes(xml: String): String {
        return androidColorAttributeRegex.replace(xml) { match ->
            val prefix = match.groupValues[1]
            val quote = match.groupValues[2]
            val raw = match.groupValues[3]
            val hex = raw.substring(1)

            val normalized = when (hex.length) {
                3 -> buildString(7) {
                    append('#')
                    for (digit in hex) {
                        append(digit)
                        append(digit)
                    }
                }
                4 -> buildString(9) {
                    append('#')
                    append(hex[3])
                    append(hex[3])
                    append(hex[0])
                    append(hex[0])
                    append(hex[1])
                    append(hex[1])
                    append(hex[2])
                    append(hex[2])
                }
                else -> raw
            }.uppercase()

            "$prefix$quote$normalized$quote"
        }
    }

    /**
     * Applies presentation-only XML cleanup after all structural optimization.
     * This does not change element order, attributes, or rendering semantics.
     */
    private fun formatVectorXml(xml: String): String {
        val sourceLines = xml.replace("\r\n", "\n").replace('\r', '\n').lines()
        val compacted = mutableListOf<String>()
        var index = 0

        while (index < sourceLines.size) {
            val line = sourceLines[index]

            // Safely compact only the exact two-line, attribute-free form:
            //     <group
            //     >
            // Never scan across attributes or child elements.
            val openingMatch = Regex("""^([ \t]*)<group[ \t]*$""", RegexOption.IGNORE_CASE)
                .matchEntire(line)
            if (openingMatch != null &&
                index + 1 < sourceLines.size &&
                Regex("""^[ \t]*>[ \t]*$""").matches(sourceLines[index + 1])
            ) {
                compacted += "${openingMatch.groupValues[1]}<group>"
                index += 2
                continue
            }

            compacted += line.trimEnd()
            index++
        }

        var formatted = removeOrphanedConversionComments(compacted.joinToString("\n"))

        // Collapse three or more consecutive blank lines to one blank line.
        formatted = Regex("""\n[ \t]*\n(?:[ \t]*\n)+""")
            .replace(formatted, "\n\n")

        // Remove blank padding directly after an opening group and directly
        // before a closing group using a line-based pass rather than a regex
        // that can span XML elements.
        val lines = formatted.lines()
        val output = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                val previous = output.lastOrNull()?.trim().orEmpty()
                if (previous == "<group>" || previous.endsWith(">") && previous.startsWith("<group ")) {
                    continue
                }
            }
            if (line.trimStart().startsWith("</group")) {
                while (output.lastOrNull()?.isBlank() == true) output.removeAt(output.lastIndex)
            }
            output += line
        }

        return output.joinToString("\n").trimEnd() + "\n"
    }

    private fun removeOrphanedConversionComments(xml: String): String {
        val lines = xml.lines()
        val output = mutableListOf<String>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("<!-- converted from ") ||
                line.trimStart().startsWith("<!-- converted text ") ||
                line.trimStart().startsWith("<!-- approximated marker ")
            ) {
                var next = index + 1
                while (next < lines.size && lines[next].isBlank()) next++
                val nextTrimmed = lines.getOrNull(next)?.trimStart().orEmpty()
                if (!nextTrimmed.startsWith("<path") &&
                    !nextTrimmed.startsWith("<group") &&
                    !nextTrimmed.startsWith("<clip-path")
                ) {
                    index++
                    continue
                }
            }
            output += line
            index++
        }

        return output.joinToString("\n")
    }


    private data class IdentityGroupCleanupResult(
        val xml: String,
        val removedAttributes: Int
    )

    /**
     * Removes identity transform attributes from VectorDrawable groups.
     *
     * Safe identity values are:
     * - translateX/translateY = 0
     * - scaleX/scaleY = 1
     * - rotation = 0
     * - pivotX/pivotY when the group has no effective scale or rotation
     *
     * Unknown attributes and non-numeric resource values are preserved. Once an
     * identity-only group becomes attribute-free, the normal redundant-group pass
     * may safely promote its children, subject to the existing clip-path guard.
     */
    private fun removeIdentityGroupTransformAttributes(xml: String): IdentityGroupCleanupResult {
        val groupOpeningRegex = Regex(
            """<group\b(?:\"[^\"]*\"|'[^']*'|[^>])*?>""",
            RegexOption.IGNORE_CASE
        )
        var removed = 0

        val cleaned = groupOpeningRegex.replace(xml) { match ->
            val tag = match.value
            val attrs = androidAttributeRegex.findAll(tag).toList()
            if (attrs.isEmpty()) return@replace tag

            fun numeric(name: String, default: BigDecimal): BigDecimal? {
                val attr = attrs.firstOrNull { it.groupValues[1].equals(name, true) }
                    ?: return default
                return attr.groupValues[3].trim().toBigDecimalOrNull()
            }

            val scaleX = numeric("scaleX", BigDecimal.ONE)
            val scaleY = numeric("scaleY", BigDecimal.ONE)
            val rotation = numeric("rotation", BigDecimal.ZERO)
            val pivotIsIrrelevant =
                scaleX != null && scaleY != null && rotation != null &&
                    scaleX.compareTo(BigDecimal.ONE) == 0 &&
                    scaleY.compareTo(BigDecimal.ONE) == 0 &&
                    rotation.compareTo(BigDecimal.ZERO) == 0

            val removable = attrs.filter { attr ->
                val name = attr.groupValues[1].lowercase()
                val value = attr.groupValues[3].trim().toBigDecimalOrNull()
                    ?: return@filter false
                when (name) {
                    "translatex", "translatey", "rotation" ->
                        value.compareTo(BigDecimal.ZERO) == 0
                    "scalex", "scaley" ->
                        value.compareTo(BigDecimal.ONE) == 0
                    "pivotx", "pivoty" -> pivotIsIrrelevant
                    else -> false
                }
            }

            if (removable.isEmpty()) return@replace tag

            val out = StringBuilder(tag)
            for (attr in removable.sortedByDescending { it.range.first }) {
                var start = attr.range.first
                var end = attr.range.last + 1
                while (start > 0 && out[start - 1].isWhitespace()) start--
                out.delete(start, end)
                removed++
            }

            out.toString()
                .replace(Regex("""\s+>"""), ">")
                .replace(Regex("""<group\s*>""", RegexOption.IGNORE_CASE), "<group>")
        }

        return IdentityGroupCleanupResult(cleaned, removed)
    }

    private data class GroupCleanupResult(
        val xml: String,
        val removedCount: Int
    )

    private data class GroupRange(
        val start: Int,
        val openingEnd: Int,
        val closingStart: Int,
        val end: Int
    )

    private fun removeEmptyGroups(xml: String): GroupCleanupResult {
        var current = xml
        var totalRemoved = 0

        while (true) {
            val ranges = findMatchedGroups(current)
            val removable = ranges.filter { range ->
                val body = current.substring(range.openingEnd, range.closingStart)
                xmlCommentRegex.replace(body, "").trim().isEmpty()
            }

            if (removable.isEmpty()) break

            val output = StringBuilder(current)
            for (range in removable.sortedByDescending { it.start }) {
                output.delete(range.start, range.end)
            }

            totalRemoved += removable.size
            current = output.toString()
        }

        return GroupCleanupResult(current, totalRemoved)
    }

    /**
     * Finds properly matched VectorDrawable <group>...</group> ranges with a
     * stack. This prevents a cleanup expression from pairing an outer opening
     * tag with an inner closing tag or leaving an unmatched </group>.
     */
    private fun findMatchedGroups(xml: String): List<GroupRange> {
        val tagRegex = Regex(
            """<group\b(?:"[^"]*"|'[^']*'|[^>])*?>|</group\s*>""",
            RegexOption.IGNORE_CASE
        )
        val stack = mutableListOf<Pair<Int, Int>>()
        val ranges = mutableListOf<GroupRange>()

        for (match in tagRegex.findAll(xml)) {
            if (match.value.startsWith("</", ignoreCase = true)) {
                val opening = stack.removeLastOrNull() ?: continue
                ranges += GroupRange(
                    start = opening.first,
                    openingEnd = opening.second,
                    closingStart = match.range.first,
                    end = match.range.last + 1
                )
            } else {
                stack += match.range.first to (match.range.last + 1)
            }
        }

        return ranges
    }

    private data class GroupFlatteningResult(
        val xml: String,
        val flattenedCount: Int
    )

    /**
     * Removes semantically redundant VectorDrawable groups.
     *
     * A group is flattened only when:
     * - its opening tag has no attributes; and
     * - its complete body contains no <clip-path> element.
     *
     * The clip-path restriction is deliberately conservative. A clip path
     * affects following siblings within its group, so moving that body into a
     * parent could expand the clipping scope and change rendering.
     */
    private fun flattenRedundantGroups(xml: String): GroupFlatteningResult {
        var current = xml
        var totalFlattened = 0

        while (true) {
            val candidate = findMatchedGroups(current)
                .sortedBy { it.end - it.start }
                .firstOrNull { range ->
                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    isAttributeFreeGroup(openingTag) &&
                        !Regex("""<clip-path\b""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(body)
                } ?: break

            val body = current.substring(candidate.openingEnd, candidate.closingStart)
            val replacement = removeOneIndentLevel(body)
            current = buildString(current.length) {
                append(current, 0, candidate.start)
                append(replacement)
                append(current, candidate.end, current.length)
            }
            totalFlattened++
        }

        return GroupFlatteningResult(current, totalFlattened)
    }

    private fun isAttributeFreeGroup(openingTag: String): Boolean {
        return Regex("""<group\s*>""", RegexOption.IGNORE_CASE)
            .matches(openingTag.trim())
    }

    private fun removeOneIndentLevel(body: String): String {
        val normalized = body.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.lines().toMutableList()
        if (lines.firstOrNull()?.isBlank() == true) lines.removeAt(0)
        if (lines.lastOrNull()?.isBlank() == true) lines.removeAt(lines.lastIndex)

        val nonBlank = lines.filter { it.isNotBlank() }
        val commonIndent = nonBlank.minOfOrNull { line ->
            line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) 0 else it }
        } ?: 0

        val dedented = lines.joinToString("\n") { line ->
            if (line.isBlank()) "" else line.drop(commonIndent)
        }

        return if (dedented.isEmpty()) "" else "\n$dedented\n"
    }


    private data class ScaleFlatteningResult(
        val xml: String,
        val flattenedGroups: Int,
        val scaledPaths: Int,
        val scaledStrokeWidths: Int
    )

    private data class UniformScale(
        val factor: BigDecimal,
        val pivotX: BigDecimal,
        val pivotY: BigDecimal
    )

    /**
     * Flattens a deliberately narrow class of uniform positive-scale groups.
     *
     * A group is eligible only when:
     * - its only attributes are scaleX/scaleY and optional pivotX/pivotY;
     * - scaleX and scaleY are equal, finite numeric values greater than zero;
     * - the effective scale is not 1;
     * - its body contains only comments, whitespace, and direct self-closing paths;
     * - it contains no nested group, clip-path, or nested aapt paint; and
     * - every explicit strokeWidth is numeric.
     *
     * Coordinates are scaled around the VectorDrawable pivot and numeric stroke
     * widths are multiplied by the same factor. Positive uniform scale preserves
     * arc sweep direction, stroke joins/caps, and path winding.
     */
    private fun flattenUniformPositiveScaleGroups(xml: String): ScaleFlatteningResult {
        var current = xml
        var groupsFlattened = 0
        var pathsScaled = 0
        var strokeWidthsScaled = 0

        while (true) {
            val candidate = findMatchedGroups(current)
                .sortedBy { it.end - it.start }
                .firstOrNull { range ->
                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    uniformScaleForGroup(openingTag) != null &&
                        isDirectSimplePathBody(body) &&
                        allExplicitStrokeWidthsAreNumeric(body)
                } ?: break

            val openingTag = current.substring(candidate.start, candidate.openingEnd)
            val scale = uniformScaleForGroup(openingTag) ?: break
            val body = current.substring(candidate.openingEnd, candidate.closingStart)
            var scaledCount = 0
            var strokeCount = 0
            var failed = false

            val scaledBody = pathElementRegex.replace(body) { match ->
                if (failed) return@replace match.value
                val element = match.value
                if (!element.trimEnd().endsWith("/>") || element.contains("<aapt:attr", true)) {
                    failed = true
                    return@replace element
                }

                val pathData = attributeValue(element, "android:pathData")
                if (pathData == null) {
                    failed = true
                    return@replace element
                }

                val scaledPathData = scalePathData(
                    pathData = pathData,
                    factor = scale.factor,
                    pivotX = scale.pivotX,
                    pivotY = scale.pivotY
                )
                if (scaledPathData == null) {
                    failed = true
                    return@replace element
                }

                var updated = replacePathData(element, scaledPathData)
                val strokeWidth = attributeValue(updated, "android:strokeWidth")
                if (strokeWidth != null) {
                    val numericWidth = strokeWidth.trim().toBigDecimalOrNull()
                    if (numericWidth == null) {
                        failed = true
                        return@replace element
                    }
                    updated = replaceAndroidAttribute(
                        updated,
                        "strokeWidth",
                        formatBigDecimal(numericWidth.multiply(scale.factor))
                    )
                    strokeCount++
                }

                scaledCount++
                updated
            }

            if (failed || scaledCount == 0) break

            val replacement = removeOneIndentLevel(scaledBody)
            current = buildString(current.length) {
                append(current, 0, candidate.start)
                append(replacement)
                append(current, candidate.end, current.length)
            }
            groupsFlattened++
            pathsScaled += scaledCount
            strokeWidthsScaled += strokeCount
        }

        return ScaleFlatteningResult(
            xml = current,
            flattenedGroups = groupsFlattened,
            scaledPaths = pathsScaled,
            scaledStrokeWidths = strokeWidthsScaled
        )
    }

    private fun uniformScaleForGroup(openingTag: String): UniformScale? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) || !trimmed.endsWith('>')) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val allowed = setOf("scalex", "scaley", "pivotx", "pivoty")
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it !in allowed }) return null
        if (names.count { it == "scalex" } > 1 || names.count { it == "scaley" } > 1 ||
            names.count { it == "pivotx" } > 1 || names.count { it == "pivoty" } > 1
        ) return null

        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val scaleX = attributes.firstOrNull { it.groupValues[1].equals("scaleX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val scaleY = attributes.firstOrNull { it.groupValues[1].equals("scaleY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ONE
        if (scaleX.compareTo(scaleY) != 0 || scaleX.compareTo(BigDecimal.ZERO) <= 0) return null
        if (scaleX.compareTo(BigDecimal.ONE) == 0) return null

        val pivotX = attributes.firstOrNull { it.groupValues[1].equals("pivotX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val pivotY = attributes.firstOrNull { it.groupValues[1].equals("pivotY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        return UniformScale(scaleX, pivotX, pivotY)
    }

    private fun allExplicitStrokeWidthsAreNumeric(body: String): Boolean {
        return pathElementRegex.findAll(body).all { match ->
            val strokeWidth = attributeValue(match.value, "android:strokeWidth")
            strokeWidth == null || strokeWidth.trim().toBigDecimalOrNull() != null
        }
    }

    private fun replaceAndroidAttribute(element: String, name: String, value: String): String {
        val regex = Regex(
            """(android:${Regex.escape(name)}\s*=\s*)(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(element) ?: return element
        val replacement =
            "${match.groupValues[1]}${match.groupValues[2]}$value${match.groupValues[2]}"
        return element.replaceRange(match.range, replacement)
    }

    private fun scalePathData(
        pathData: String,
        factor: BigDecimal,
        pivotX: BigDecimal,
        pivotY: BigDecimal
    ): String? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        if (segments.isEmpty()) return null

        fun scaledX(value: BigDecimal): BigDecimal =
            pivotX.add(value.subtract(pivotX).multiply(factor))
        fun scaledY(value: BigDecimal): BigDecimal =
            pivotY.add(value.subtract(pivotY).multiply(factor))

        val output = StringBuilder(pathData.length + 24)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val scaled = when (upper) {
                'M', 'L', 'T' -> listOf(scaledX(absolute[0]), scaledY(absolute[1]))
                'H' -> listOf(scaledX(absolute[0]))
                'V' -> listOf(scaledY(absolute[0]))
                'C' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3]),
                    scaledX(absolute[4]), scaledY(absolute[5])
                )
                'S', 'Q' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3])
                )
                'A' -> listOf(
                    absolute[0].multiply(factor),
                    absolute[1].multiply(factor),
                    absolute[2], absolute[3], absolute[4],
                    scaledX(absolute[5]), scaledY(absolute[6])
                )
                'Z' -> emptyList()
                else -> return null
            }

            output.append(upper)
            scaled.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                output.append(formatBigDecimal(value))
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        return optimizePathData(output.toString()).pathData
    }

    private data class TranslationFlatteningResult(
        val xml: String,
        val flattenedGroups: Int,
        val translatedPaths: Int
    )

    /**
     * Flattens a deliberately narrow class of translation-only groups.
     *
     * A group is eligible only when:
     * - its only attributes are android:translateX and/or android:translateY;
     * - at least one translation component is non-zero;
     * - its body contains only comments, whitespace, and direct self-closing paths;
     * - it contains no nested group, clip-path, or nested aapt paint.
     *
     * The translation is baked into every path coordinate, then the wrapper is
     * removed. Rejecting nested paint keeps viewport-space gradient semantics
     * unchanged. This is intentionally conservative for the first A7 pass.
     */
    private fun flattenTranslationOnlyGroups(xml: String): TranslationFlatteningResult {
        var current = xml
        var groupsFlattened = 0
        var pathsTranslated = 0

        while (true) {
            val candidate = findMatchedGroups(current)
                .sortedBy { it.end - it.start }
                .firstOrNull { range ->
                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    translationForGroup(openingTag) != null && isDirectSimplePathBody(body)
                } ?: break

            val openingTag = current.substring(candidate.start, candidate.openingEnd)
            val (dx, dy) = translationForGroup(openingTag) ?: break
            val body = current.substring(candidate.openingEnd, candidate.closingStart)
            var translatedCount = 0
            var failed = false

            val translatedBody = pathElementRegex.replace(body) { match ->
                if (failed) return@replace match.value
                val element = match.value
                if (!element.trimEnd().endsWith("/>") || element.contains("<aapt:attr", true)) {
                    failed = true
                    return@replace element
                }
                val pathData = attributeValue(element, "android:pathData")
                if (pathData == null) {
                    failed = true
                    return@replace element
                }
                val translated = translatePathData(pathData, dx, dy)
                if (translated == null) {
                    failed = true
                    element
                } else {
                    translatedCount++
                    replacePathData(element, translated)
                }
            }

            if (failed || translatedCount == 0) {
                // No later pass can make this same candidate eligible, so stop
                // rather than risking an endless retry loop.
                break
            }

            val replacement = removeOneIndentLevel(translatedBody)
            current = buildString(current.length) {
                append(current, 0, candidate.start)
                append(replacement)
                append(current, candidate.end, current.length)
            }
            groupsFlattened++
            pathsTranslated += translatedCount
        }

        return TranslationFlatteningResult(current, groupsFlattened, pathsTranslated)
    }

    private fun translationForGroup(openingTag: String): Pair<BigDecimal, BigDecimal>? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) || !trimmed.endsWith('>')) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it != "translatex" && it != "translatey" }) return null

        // Reject unknown/non-Android attributes as well. The text remaining after
        // removing the element name and recognized attributes must be empty.
        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val dx = attributes.firstOrNull { it.groupValues[1].equals("translateX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val dy = attributes.firstOrNull { it.groupValues[1].equals("translateY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (dx.compareTo(BigDecimal.ZERO) == 0 && dy.compareTo(BigDecimal.ZERO) == 0) return null
        return dx to dy
    }

    private fun isDirectSimplePathBody(body: String): Boolean {
        if (Regex("""<(?:group|clip-path)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)) return false
        if (body.contains("<aapt:attr", ignoreCase = true)) return false

        val withoutComments = xmlCommentRegex.replace(body, "")
        val withoutPaths = pathElementRegex.replace(withoutComments) { match ->
            if (match.value.trimEnd().endsWith("/>")) "" else match.value
        }
        return withoutPaths.isBlank() && pathElementRegex.containsMatchIn(body)
    }

    private fun translatePathData(
        pathData: String,
        dx: BigDecimal,
        dy: BigDecimal
    ): String? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        if (segments.isEmpty()) return null

        val output = StringBuilder(pathData.length + 16)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val translated = when (upper) {
                'M', 'L', 'T' -> listOf(absolute[0].add(dx), absolute[1].add(dy))
                'H' -> listOf(absolute[0].add(dx))
                'V' -> listOf(absolute[0].add(dy))
                'C' -> listOf(
                    absolute[0].add(dx), absolute[1].add(dy),
                    absolute[2].add(dx), absolute[3].add(dy),
                    absolute[4].add(dx), absolute[5].add(dy)
                )
                'S', 'Q' -> listOf(
                    absolute[0].add(dx), absolute[1].add(dy),
                    absolute[2].add(dx), absolute[3].add(dy)
                )
                'A' -> listOf(
                    absolute[0], absolute[1], absolute[2], absolute[3], absolute[4],
                    absolute[5].add(dx), absolute[6].add(dy)
                )
                'Z' -> emptyList()
                else -> return null
            }

            output.append(upper)
            translated.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                output.append(formatBigDecimal(value))
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        return optimizePathData(output.toString()).pathData
    }


    private data class PathMergingResult(
        val xml: String,
        val mergedCount: Int
    )


    private data class DuplicateRemovalResult(
        val xml: String,
        val removedCount: Int
    )

    /**
     * Removes only adjacent path elements that are exact rendered duplicates.
     *
     * This pass is deliberately conservative:
     * - both paths must be self-closing and contain no nested aapt paint;
     * - optimized pathData must be byte-for-byte identical;
     * - every Android rendering attribute must be identical;
     * - trim-path attributes are rejected; and
     * - all paints that can draw must be fully opaque.
     *
     * Requiring adjacency keeps the paths in the same immediate XML/group
     * context and avoids crossing clip, group, or ordering boundaries.
     */
    private fun removeExactAdjacentDuplicatePaths(xml: String): DuplicateRemovalResult {
        var current = xml
        var totalRemoved = 0

        while (true) {
            var removedThisPass = false
            val replaced = adjacentSimplePathRegex.replace(current) { match ->
                if (removedThisPass) return@replace match.value

                val first = match.groupValues[1]
                val separator = match.groupValues[2]
                val second = match.groupValues[3]

                if (!areSafeExactDuplicates(first, second)) {
                    match.value
                } else {
                    removedThisPass = true
                    totalRemoved++
                    // Keep comments between the paths above the surviving path.
                    separator + first
                }
            }

            if (!removedThisPass) break
            current = replaced
        }

        return DuplicateRemovalResult(current, totalRemoved)
    }

    private fun areSafeExactDuplicates(first: String, second: String): Boolean {
        if (first.contains("<aapt:attr", ignoreCase = true) ||
            second.contains("<aapt:attr", ignoreCase = true)
        ) return false

        val firstPathData = attributeValue(first, "android:pathData")?.trim() ?: return false
        val secondPathData = attributeValue(second, "android:pathData")?.trim() ?: return false
        if (firstPathData != secondPathData) return false

        val firstAttributes = canonicalPathAttributes(first)
        val secondAttributes = canonicalPathAttributes(second)
        if (firstAttributes != secondAttributes) return false

        if (firstAttributes.keys.any { it.startsWith("trimpath") }) return false

        return hasOnlyFullyOpaquePaint(firstAttributes)
    }

    private fun hasOnlyFullyOpaquePaint(attributes: Map<String, String>): Boolean {
        fun alphaIsOne(name: String): Boolean {
            val raw = attributes[name] ?: return true
            return raw.toDoubleOrNull()?.let { kotlin.math.abs(it - 1.0) <= 1e-9 } == true
        }

        fun colorIsOpaque(raw: String?): Boolean {
            if (raw == null || isTransparentColor(raw)) return false
            val value = raw.trim()
            if (!value.startsWith("#")) return false
            val hex = value.substring(1)
            return when (hex.length) {
                6 -> true
                8 -> hex.substring(0, 2).equals("FF", ignoreCase = true)
                else -> false
            }
        }

        val fill = attributes["fillcolor"]
        val stroke = attributes["strokecolor"]
        val fillDraws = fill != null && !isTransparentColor(fill)
        val strokeDraws = stroke != null && !isTransparentColor(stroke) &&
            ((attributes["strokewidth"]?.toDoubleOrNull() ?: 0.0) > 0.0)

        if (!fillDraws && !strokeDraws) return false
        if (!alphaIsOne("fillalpha") || !alphaIsOne("strokealpha")) return false
        if (fillDraws && !colorIsOpaque(fill)) return false
        if (strokeDraws && !colorIsOpaque(stroke)) return false
        return true
    }

    private data class Bounds(
        val minX: Double,
        val minY: Double,
        val maxX: Double,
        val maxY: Double
    ) {
        fun expanded(amount: Double): Bounds = Bounds(
            minX - amount,
            minY - amount,
            maxX + amount,
            maxY + amount
        )

        fun isStrictlyDisjointFrom(other: Bounds): Boolean {
            val epsilon = 1e-9
            return maxX < other.minX - epsilon ||
                other.maxX < minX - epsilon ||
                maxY < other.minY - epsilon ||
                other.maxY < minY - epsilon
        }
    }

    /**
     * Conservatively merges adjacent VectorDrawable paths by concatenating their
     * pathData values. A merge is allowed only when:
     * - both elements are self-closing paths with no nested aapt paint;
     * - every Android rendering attribute except pathData is identical;
     * - both pathData values use only M/L/H/V/Z commands; and
     * - their stroke-expanded bounds are provably disjoint.
     *
     * The disjointness requirement avoids changes to fill winding, even-odd
     * behavior, alpha compositing, and overlapping stroke coverage.
     */
    private fun mergeCompatibleAdjacentPaths(xml: String): PathMergingResult {
        var current = xml
        var totalMerged = 0

        while (true) {
            var mergedThisPass = false
            val replaced = adjacentSimplePathRegex.replace(current) { match ->
                if (mergedThisPass) return@replace match.value

                val first = match.groupValues[1]
                val separator = match.groupValues[2]
                val second = match.groupValues[3]
                val merged = mergePathElements(first, second)

                if (merged == null) {
                    match.value
                } else {
                    mergedThisPass = true
                    totalMerged++
                    // Preserve comments associated with the second element above
                    // the merged element. Conversion comments are later cleaned up
                    // if they become orphaned.
                    separator + merged
                }
            }

            if (!mergedThisPass) break
            current = replaced
        }

        return PathMergingResult(current, totalMerged)
    }

    private fun mergePathElements(first: String, second: String): String? {
        if (first.contains("<aapt:attr", ignoreCase = true) ||
            second.contains("<aapt:attr", ignoreCase = true)
        ) {
            return null
        }

        val firstPathData = attributeValue(first, "android:pathData") ?: return null
        val secondPathData = attributeValue(second, "android:pathData") ?: return null

        val firstAttributes = canonicalPathAttributes(first)
        val secondAttributes = canonicalPathAttributes(second)
        if (firstAttributes != secondAttributes) return null

        val firstBounds = compatiblePathBounds(firstPathData) ?: return null
        val secondBounds = compatiblePathBounds(secondPathData) ?: return null
        val strokeExpansion = sharedStrokeExpansion(firstAttributes)

        if (!firstBounds.expanded(strokeExpansion)
                .isStrictlyDisjointFrom(secondBounds.expanded(strokeExpansion))
        ) {
            return null
        }

        val combinedPathData = firstPathData.trim() + secondPathData.trim()
        return replacePathData(first, combinedPathData)
    }

    private fun canonicalPathAttributes(element: String): Map<String, String> {
        return androidAttributeRegex.findAll(element)
            .associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[3].trim()
            }
            .filterKeys { it != "pathdata" }
    }

    private fun sharedStrokeExpansion(attributes: Map<String, String>): Double {
        val strokeColor = attributes["strokecolor"] ?: return 0.0
        if (isTransparentColor(strokeColor)) return 0.0
        val strokeAlpha = attributes["strokealpha"]?.toDoubleOrNull() ?: 1.0
        if (strokeAlpha <= 0.0) return 0.0
        val strokeWidth = attributes["strokewidth"]?.toDoubleOrNull() ?: 0.0
        return (strokeWidth.coerceAtLeast(0.0) / 2.0)
    }

    private fun replacePathData(element: String, newPathData: String): String {
        val regex = Regex(
            """(\bandroid:pathData\s*=\s*)(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(element) ?: return element
        val replacement =
            "${match.groupValues[1]}${match.groupValues[2]}$newPathData${match.groupValues[2]}"
        return element.replaceRange(match.range, replacement)
    }

    /**
     * Returns conservative bounds for paths composed of:
     * M/L/H/V/Z plus cubic and quadratic Bézier commands C/S/Q/T.
     *
     * Bézier bounds use the control-point hull. A Bézier curve is always
     * contained by that hull, so these bounds may be larger than necessary but
     * are safe for proving that two rendered paths are disjoint.
     *
     * Arc commands remain unsupported until A6.2.
     */
    private fun compatiblePathBounds(pathData: String): Bounds? {
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        if (tokens.isEmpty()) return null

        var index = 0
        var command: Char? = null
        var currentX = 0.0
        var currentY = 0.0
        var subpathX = 0.0
        var subpathY = 0.0
        var previousCommand: Char? = null
        var previousCubicControlX = 0.0
        var previousCubicControlY = 0.0
        var previousQuadraticControlX = 0.0
        var previousQuadraticControlY = 0.0

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var hasPoint = false

        fun include(x: Double, y: Double) {
            minX = minOf(minX, x)
            minY = minOf(minY, y)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            hasPoint = true
        }

        fun hasNumbers(count: Int): Boolean {
            if (index + count > tokens.size) return false
            for (offset in 0 until count) {
                if (isCommand(tokens[index + offset])) return false
            }
            return true
        }

        fun number(): Double? {
            if (index >= tokens.size || isCommand(tokens[index])) return null
            return tokens[index++].toDoubleOrNull()
        }

        fun absoluteX(value: Double, relative: Boolean): Double =
            if (relative) currentX + value else value

        fun absoluteY(value: Double, relative: Boolean): Double =
            if (relative) currentY + value else value

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                command = tokens[index][0]
                index++
            }

            val active = command ?: return null
            val lower = active.lowercaseChar()
            val relative = active.isLowerCase()

            when (lower) {
                'm' -> {
                    if (!hasNumbers(2)) return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x = absoluteX(rawX, relative)
                    val y = absoluteY(rawY, relative)
                    currentX = x
                    currentY = y
                    subpathX = x
                    subpathY = y
                    include(x, y)
                    previousCommand = active
                    command = if (relative) 'l' else 'L'
                }

                'l' -> {
                    if (!hasNumbers(2)) return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    include(currentX, currentY)
                    currentX = absoluteX(rawX, relative)
                    currentY = absoluteY(rawY, relative)
                    include(currentX, currentY)
                    previousCommand = active
                }

                'h' -> {
                    if (!hasNumbers(1)) return null
                    val rawX = number() ?: return null
                    include(currentX, currentY)
                    currentX = absoluteX(rawX, relative)
                    include(currentX, currentY)
                    previousCommand = active
                }

                'v' -> {
                    if (!hasNumbers(1)) return null
                    val rawY = number() ?: return null
                    include(currentX, currentY)
                    currentY = absoluteY(rawY, relative)
                    include(currentX, currentY)
                    previousCommand = active
                }

                'c' -> {
                    if (!hasNumbers(6)) return null
                    val startX = currentX
                    val startY = currentY
                    val rawX1 = number() ?: return null
                    val rawY1 = number() ?: return null
                    val rawX2 = number() ?: return null
                    val rawY2 = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null

                    val x1 = if (relative) startX + rawX1 else rawX1
                    val y1 = if (relative) startY + rawY1 else rawY1
                    val x2 = if (relative) startX + rawX2 else rawX2
                    val y2 = if (relative) startY + rawY2 else rawY2
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(x1, y1)
                    include(x2, y2)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousCubicControlX = x2
                    previousCubicControlY = y2
                    previousCommand = active
                }

                's' -> {
                    if (!hasNumbers(4)) return null
                    val startX = currentX
                    val startY = currentY
                    val reflectedX =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('c', 's'))
                            2.0 * startX - previousCubicControlX
                        else startX
                    val reflectedY =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('c', 's'))
                            2.0 * startY - previousCubicControlY
                        else startY

                    val rawX2 = number() ?: return null
                    val rawY2 = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x2 = if (relative) startX + rawX2 else rawX2
                    val y2 = if (relative) startY + rawY2 else rawY2
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(reflectedX, reflectedY)
                    include(x2, y2)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousCubicControlX = x2
                    previousCubicControlY = y2
                    previousCommand = active
                }

                'q' -> {
                    if (!hasNumbers(4)) return null
                    val startX = currentX
                    val startY = currentY
                    val rawX1 = number() ?: return null
                    val rawY1 = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x1 = if (relative) startX + rawX1 else rawX1
                    val y1 = if (relative) startY + rawY1 else rawY1
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(x1, y1)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousQuadraticControlX = x1
                    previousQuadraticControlY = y1
                    previousCommand = active
                }

                't' -> {
                    if (!hasNumbers(2)) return null
                    val startX = currentX
                    val startY = currentY
                    val reflectedX =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('q', 't'))
                            2.0 * startX - previousQuadraticControlX
                        else startX
                    val reflectedY =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('q', 't'))
                            2.0 * startY - previousQuadraticControlY
                        else startY
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(reflectedX, reflectedY)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousQuadraticControlX = reflectedX
                    previousQuadraticControlY = reflectedY
                    previousCommand = active
                }

                'z' -> {
                    include(currentX, currentY)
                    include(subpathX, subpathY)
                    currentX = subpathX
                    currentY = subpathY
                    previousCommand = active
                    command = null
                }

                // Elliptical arcs are deliberately deferred to A6.2.
                'a' -> return null

                else -> return null
            }
        }

        return if (hasPoint) Bounds(minX, minY, maxX, maxY) else null
    }

    private fun hasDrawableGeometry(pathData: String): Boolean {
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        if (tokens.isEmpty()) return false

        var command: Char? = null
        var parametersForCommand = 0

        for (token in tokens) {
            if (isCommand(token)) {
                command = token[0]
                parametersForCommand = 0
                if (command.lowercaseChar() in charArrayOf('l', 'h', 'v', 'c', 's', 'q', 't', 'a')) {
                    return true
                }
            } else {
                parametersForCommand++
                // Additional coordinate pairs following moveto are implicit lineto
                // segments, so M0,0 10,10 does draw even without an L token.
                if (command?.lowercaseChar() == 'm' && parametersForCommand > 2) {
                    return true
                }
            }
        }

        return false
    }

    private fun isDefinitelyInvisible(pathElement: String): Boolean {
        val fillInvisible = paintChannelDefinitelyInvisible(
            element = pathElement,
            colorAttribute = "android:fillColor",
            alphaAttribute = "android:fillAlpha",
            widthAttribute = null
        )
        val strokeInvisible = paintChannelDefinitelyInvisible(
            element = pathElement,
            colorAttribute = "android:strokeColor",
            alphaAttribute = "android:strokeAlpha",
            widthAttribute = "android:strokeWidth"
        )
        return fillInvisible && strokeInvisible
    }

    private fun paintChannelDefinitelyInvisible(
        element: String,
        colorAttribute: String,
        alphaAttribute: String,
        widthAttribute: String?
    ): Boolean {
        val alpha = attributeValue(element, alphaAttribute)?.toDoubleOrNull()
        if (alpha != null && alpha <= 0.0) return true

        if (widthAttribute != null) {
            val width = attributeValue(element, widthAttribute)?.toDoubleOrNull()
            if (width != null && width <= 0.0) return true
        }

        // A nested aapt gradient/color can make the channel visible even when the
        // simple attribute is absent, so preserve it unless alpha already proved
        // the whole channel invisible.
        val hasNestedPaint = Regex(
            """<aapt:attr\b[^>]*\bname\s*=\s*[\"']${Regex.escape(colorAttribute)}[\"']""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(element)
        if (hasNestedPaint) return false

        val color = attributeValue(element, colorAttribute)
        return when {
            color == null -> widthAttribute != null // absent stroke means no stroke; absent fill remains uncertain
            isTransparentColor(color) -> true
            else -> false
        }
    }

    private fun isTransparentColor(rawColor: String): Boolean {
        val color = rawColor.trim().lowercase()
        if (color == "@android:color/transparent" || color == "transparent") return true

        if (!color.startsWith('#')) return false
        val hex = color.substring(1)
        return when (hex.length) {
            4 -> hex[0] == '0'       // #ARGB
            8 -> hex.substring(0, 2) == "00" // #AARRGGBB
            else -> false
        }
    }

    private fun attributeValue(element: String, name: String): String? {
        val regex = Regex(
            """\b${Regex.escape(name)}\s*=\s*([\"'])(.*?)\1""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return regex.find(element)?.groupValues?.get(2)
    }

    private data class PathResult(
        val pathData: String,
        val repeatedCommandsRemoved: Int,
        val numbersNormalized: Int,
        val shorterCommandFormsSelected: Int = 0,
        val relativeCommandsSelected: Int = 0,
        val axisCommandsSelected: Int = 0
    )

    private fun optimizePathData(pathData: String): PathResult {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return PathResult(pathData.trim(), 0, 0, 0, 0, 0)
        }

        // If tokenization skipped anything other than legal separators, preserve the
        // original value rather than risking a malformed-path rewrite.
        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return PathResult(pathData, 0, 0, 0, 0, 0)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return PathResult(pathData, 0, 0, 0, 0, 0)
        }

        val output = StringBuilder(pathData.length)
        var activeCommand: Char? = null
        var previousWasNumber = false
        var repeatedCommandsRemoved = 0
        var numbersNormalized = 0

        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                val canUseImplicitRepeat =
                    activeCommand == command && command !in charArrayOf('M', 'm', 'Z', 'z')

                if (canUseImplicitRepeat) {
                    repeatedCommandsRemoved++
                    // Keep previousWasNumber=true so the first parameter of the
                    // implicit command is separated from the preceding parameter.
                } else {
                    output.append(command)
                    previousWasNumber = false
                }

                activeCommand = command
            } else {
                val normalized = normalizeNumber(token)
                if (normalized != token) numbersNormalized++

                if (previousWasNumber) output.append(',')
                output.append(normalized)
                previousWasNumber = true
            }
        }

        val commandOptimization = shortenPathCommands(output.toString())

        return PathResult(
            pathData = commandOptimization.pathData,
            repeatedCommandsRemoved = repeatedCommandsRemoved,
            numbersNormalized = numbersNormalized,
            shorterCommandFormsSelected = commandOptimization.shorterFormsSelected,
            relativeCommandsSelected = commandOptimization.relativeCommandsSelected,
            axisCommandsSelected = commandOptimization.axisCommandsSelected
        )
    }


    private data class CommandOptimizationResult(
        val pathData: String,
        val shorterFormsSelected: Int,
        val relativeCommandsSelected: Int,
        val axisCommandsSelected: Int
    )

    private data class ParsedSegment(
        val command: Char,
        val values: List<BigDecimal>
    )

    /**
     * Chooses a shorter, geometry-equivalent spelling for each path segment.
     *
     * This pass never rounds coordinates. Decimal arithmetic uses BigDecimal,
     * so switching between absolute and relative forms preserves the exact
     * values represented by the normalized input tokens.
     *
     * It may:
     * - switch between absolute and relative command forms;
     * - replace horizontal/vertical line segments with H/V or h/v;
     * - retain the original normalized form whenever no candidate is shorter.
     */
    private fun shortenPathCommands(pathData: String): CommandOptimizationResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return CommandOptimizationResult(pathData, 0, 0, 0)
        if (segments.isEmpty()) return CommandOptimizationResult(pathData, 0, 0, 0)

        val output = StringBuilder(pathData.length)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO
        var previousOutputCommand: Char? = null
        var previousOutputNumber: String? = null
        var shorterForms = 0
        var relativeSelected = 0
        var axisSelected = 0

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val startX = currentX
            val startY = currentY

            val candidates = mutableListOf<Pair<Char, List<BigDecimal>>>()
            candidates += upper to absoluteValuesFor(segment, startX, startY)
            if (upper != 'Z') {
                candidates += upper.lowercaseChar() to relativeValuesFor(segment, startX, startY)
            }

            if (upper == 'L') {
                val absolute = absoluteValuesFor(segment, startX, startY)
                val endX = absolute[0]
                val endY = absolute[1]
                if (endY.compareTo(startY) == 0) {
                    candidates += 'H' to listOf(endX)
                    candidates += 'h' to listOf(endX.subtract(startX))
                }
                if (endX.compareTo(startX) == 0) {
                    candidates += 'V' to listOf(endY)
                    candidates += 'v' to listOf(endY.subtract(startY))
                }
            }

            val originalCommand = segment.command
            val originalValues = segment.values
            val originalEncoded = encodeSegment(
                originalCommand,
                originalValues,
                previousOutputCommand,
                previousOutputNumber,
                forceCommand = originalCommand.uppercaseChar() == 'M'
            )

            val chosen = candidates
                .distinctBy { it.first to it.second }
                .map { candidate ->
                    val encoded = encodeSegment(
                        candidate.first,
                        candidate.second,
                        previousOutputCommand,
                        previousOutputNumber,
                        forceCommand = candidate.first.uppercaseChar() == 'M'
                    )
                    Triple(candidate, encoded, encoded.length)
                }
                .minWithOrNull(compareBy<Triple<Pair<Char, List<BigDecimal>>, String, Int>> { it.third }
                    .thenBy { if (it.first.first == originalCommand) 0 else 1 })
                ?: return CommandOptimizationResult(pathData, 0, 0, 0)

            val selectedCommand = chosen.first.first
            output.append(chosen.second)

            if (chosen.second.length < originalEncoded.length || selectedCommand != originalCommand) {
                if (chosen.second.length < originalEncoded.length) shorterForms++
                if (selectedCommand.isLowerCase() && selectedCommand.uppercaseChar() != 'Z') relativeSelected++
                if (selectedCommand.uppercaseChar() in charArrayOf('H', 'V')) axisSelected++
            }

            previousOutputCommand = selectedCommand
            previousOutputNumber = chosen.first.second.lastOrNull()?.let(::formatBigDecimal)

            val absolute = absoluteValuesFor(segment, startX, startY)
            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        val optimized = output.toString()
        return if (optimized.length <= pathData.length) {
            CommandOptimizationResult(optimized, shorterForms, relativeSelected, axisSelected)
        } else {
            CommandOptimizationResult(pathData, 0, 0, 0)
        }
    }

    private fun parseNormalizedSegments(pathData: String): List<ParsedSegment>? {
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        if (tokens.isEmpty()) return emptyList()

        val counts = mapOf(
            'M' to 2, 'L' to 2, 'H' to 1, 'V' to 1,
            'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2,
            'A' to 7, 'Z' to 0
        )
        val result = mutableListOf<ParsedSegment>()
        var index = 0
        var active: Char? = null
        var firstMovePair = true

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                active = tokens[index][0]
                index++
                firstMovePair = true
                if (active.uppercaseChar() == 'Z') {
                    result += ParsedSegment(active, emptyList())
                    active = null
                    continue
                }
            }

            val command = active ?: return null
            val count = counts[command.uppercaseChar()] ?: return null
            if (index + count > tokens.size) return null
            if ((0 until count).any { isCommand(tokens[index + it]) }) return null

            val values = (0 until count).map {
                tokens[index + it].toBigDecimalOrNull() ?: return null
            }
            index += count

            val emittedCommand = if (command.uppercaseChar() == 'M' && !firstMovePair) {
                if (command.isLowerCase()) 'l' else 'L'
            } else command
            result += ParsedSegment(emittedCommand, values)
            firstMovePair = false

            if (index < tokens.size && isCommand(tokens[index])) continue
            if (count == 0) active = null
        }
        return result
    }

    private fun absoluteValuesFor(
        segment: ParsedSegment,
        startX: BigDecimal,
        startY: BigDecimal
    ): List<BigDecimal> {
        if (segment.command.isUpperCase() || segment.command.uppercaseChar() == 'Z') return segment.values
        val v = segment.values
        return when (segment.command.uppercaseChar()) {
            'M', 'L', 'T' -> listOf(startX.add(v[0]), startY.add(v[1]))
            'H' -> listOf(startX.add(v[0]))
            'V' -> listOf(startY.add(v[0]))
            'C' -> listOf(
                startX.add(v[0]), startY.add(v[1]),
                startX.add(v[2]), startY.add(v[3]),
                startX.add(v[4]), startY.add(v[5])
            )
            'S', 'Q' -> listOf(
                startX.add(v[0]), startY.add(v[1]),
                startX.add(v[2]), startY.add(v[3])
            )
            'A' -> listOf(v[0], v[1], v[2], v[3], v[4], startX.add(v[5]), startY.add(v[6]))
            else -> v
        }
    }

    private fun relativeValuesFor(
        segment: ParsedSegment,
        startX: BigDecimal,
        startY: BigDecimal
    ): List<BigDecimal> {
        if (segment.command.isLowerCase()) return segment.values
        val v = segment.values
        return when (segment.command.uppercaseChar()) {
            'M', 'L', 'T' -> listOf(v[0].subtract(startX), v[1].subtract(startY))
            'H' -> listOf(v[0].subtract(startX))
            'V' -> listOf(v[0].subtract(startY))
            'C' -> listOf(
                v[0].subtract(startX), v[1].subtract(startY),
                v[2].subtract(startX), v[3].subtract(startY),
                v[4].subtract(startX), v[5].subtract(startY)
            )
            'S', 'Q' -> listOf(
                v[0].subtract(startX), v[1].subtract(startY),
                v[2].subtract(startX), v[3].subtract(startY)
            )
            'A' -> listOf(v[0], v[1], v[2], v[3], v[4], v[5].subtract(startX), v[6].subtract(startY))
            else -> v
        }
    }

    private fun encodeSegment(
        command: Char,
        values: List<BigDecimal>,
        previousCommand: Char?,
        previousNumber: String?,
        forceCommand: Boolean
    ): String {
        val canOmit = !forceCommand && previousCommand == command && command.uppercaseChar() != 'Z'
        val commandPrefix = if (canOmit) "" else command.toString()
        if (values.isEmpty()) return commandPrefix

        val numbers = values.map(::formatBigDecimal)
        val boundarySeparator = if (
            canOmit &&
            previousNumber != null &&
            needsNumberSeparator(previousNumber, numbers.first())
        ) "," else ""

        val body = buildString {
            numbers.forEachIndexed { index, number ->
                if (index > 0 && needsNumberSeparator(numbers[index - 1], number)) append(',')
                append(number)
            }
        }
        return commandPrefix + boundarySeparator + body
    }

    private fun needsNumberSeparator(previous: String, next: String): Boolean {
        if (next.startsWith('-')) return false
        if (next.startsWith('+')) return false
        return true
    }

    private fun formatBigDecimal(value: BigDecimal): String {
        val normalized = value.stripTrailingZeros()
        val plain = normalized.toPlainString()
        return normalizeNumber(plain)
    }

    private fun containsOnlySeparators(value: String): Boolean {
        return value.all { it.isWhitespace() || it == ',' }
    }

    private fun isCommand(token: String): Boolean {
        return token.length == 1 && token[0].isLetter()
    }

    private fun normalizeNumber(token: String): String {
        var value = token
        var exponent = ""

        val exponentIndex = value.indexOfFirst { it == 'e' || it == 'E' }
        if (exponentIndex >= 0) {
            exponent = normalizeExponent(value.substring(exponentIndex + 1))
            value = value.substring(0, exponentIndex)
        }

        var negative = false
        when {
            value.startsWith('-') -> {
                negative = true
                value = value.substring(1)
            }
            value.startsWith('+') -> value = value.substring(1)
        }

        val dotIndex = value.indexOf('.')
        var integerPart = if (dotIndex >= 0) value.substring(0, dotIndex) else value
        var fractionalPart = if (dotIndex >= 0) value.substring(dotIndex + 1) else ""

        integerPart = integerPart.trimStart('0').ifEmpty { "0" }
        fractionalPart = fractionalPart.trimEnd('0')

        val isZero = integerPart == "0" && fractionalPart.isEmpty()
        val sign = if (negative && !isZero) "-" else ""
        val mantissa = if (fractionalPart.isEmpty()) {
            integerPart
        } else {
            "$integerPart.$fractionalPart"
        }

        return if (exponent.isEmpty() || isZero) {
            sign + mantissa
        } else {
            sign + mantissa + "e" + exponent
        }
    }

    private fun normalizeExponent(rawExponent: String): String {
        if (rawExponent.isEmpty()) return ""

        var exponent = rawExponent
        var negative = false
        when {
            exponent.startsWith('-') -> {
                negative = true
                exponent = exponent.substring(1)
            }
            exponent.startsWith('+') -> exponent = exponent.substring(1)
        }

        exponent = exponent.trimStart('0').ifEmpty { "0" }
        return if (negative && exponent != "0") "-$exponent" else exponent
    }
}
