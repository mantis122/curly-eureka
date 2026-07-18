package com.example.svgvectorconverter

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
        """(<path(?:"[^"]*"|'[^']*'|[^>])*?/\s*>)([\s
]*(?:<!--[\s\S]*?-->[\s
]*)*)(<path(?:"[^"]*"|'[^']*'|[^>])*?/\s*>)""",
        RegexOption.IGNORE_CASE
    )
    private val androidAttributeRegex = Regex(
        """android:([A-Za-z0-9_]+)\s*=\s*(["'])(.*?)""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun optimizeVectorXml(xml: String): Result {
        var pathCount = 0
        var charactersBefore = 0
        var repeatedCommandsRemoved = 0
        var numbersNormalized = 0

        val syntaxOptimizedXml = pathDataAttributeRegex.replace(xml) { match ->
            val original = match.groupValues[1]
            val optimized = optimizePathData(original)

            pathCount++
            charactersBefore += original.length
            repeatedCommandsRemoved += optimized.repeatedCommandsRemoved
            numbersNormalized += optimized.numbersNormalized

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
        val groupFlattening = flattenRedundantGroups(groupCleanup.xml)
        val pathMerging = mergeCompatibleAdjacentPaths(groupFlattening.xml)
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


    private data class PathMergingResult(
        val xml: String,
        val mergedCount: Int
    )

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

        val firstBounds = simpleLinearPathBounds(firstPathData) ?: return null
        val secondBounds = simpleLinearPathBounds(secondPathData) ?: return null
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
            """(android:pathData\s*=\s*)(["'])(.*?)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(element) ?: return element
        val replacement =
            "${match.groupValues[1]}${match.groupValues[2]}$newPathData${match.groupValues[2]}"
        return element.replaceRange(match.range, replacement)
    }

    /**
     * Returns conservative bounds for absolute or relative line-only path data.
     * Curves and arcs are rejected in this first pass.
     */
    private fun simpleLinearPathBounds(pathData: String): Bounds? {
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        if (tokens.isEmpty()) return null

        var index = 0
        var command: Char? = null
        var currentX = 0.0
        var currentY = 0.0
        var subpathX = 0.0
        var subpathY = 0.0
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

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                command = tokens[index][0]
                index++
            }

            val active = command ?: return null
            when (active.lowercaseChar()) {
                'm', 'l' -> {
                    if (index + 1 >= tokens.size || isCommand(tokens[index]) || isCommand(tokens[index + 1])) {
                        return null
                    }
                    var x = tokens[index].toDoubleOrNull() ?: return null
                    var y = tokens[index + 1].toDoubleOrNull() ?: return null
                    index += 2
                    if (active.isLowerCase()) {
                        x += currentX
                        y += currentY
                    }
                    currentX = x
                    currentY = y
                    include(x, y)
                    if (active.lowercaseChar() == 'm') {
                        subpathX = x
                        subpathY = y
                        command = if (active.isLowerCase()) 'l' else 'L'
                    }
                }
                'h' -> {
                    if (index >= tokens.size || isCommand(tokens[index])) return null
                    var x = tokens[index].toDoubleOrNull() ?: return null
                    index++
                    if (active.isLowerCase()) x += currentX
                    currentX = x
                    include(currentX, currentY)
                }
                'v' -> {
                    if (index >= tokens.size || isCommand(tokens[index])) return null
                    var y = tokens[index].toDoubleOrNull() ?: return null
                    index++
                    if (active.isLowerCase()) y += currentY
                    currentY = y
                    include(currentX, currentY)
                }
                'z' -> {
                    currentX = subpathX
                    currentY = subpathY
                    include(currentX, currentY)
                    command = null
                }
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
        val numbersNormalized: Int
    )

    private fun optimizePathData(pathData: String): PathResult {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return PathResult(pathData.trim(), 0, 0)
        }

        // If tokenization skipped anything other than legal separators, preserve the
        // original value rather than risking a malformed-path rewrite.
        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return PathResult(pathData, 0, 0)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return PathResult(pathData, 0, 0)
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

        return PathResult(
            pathData = output.toString(),
            repeatedCommandsRemoved = repeatedCommandsRemoved,
            numbersNormalized = numbersNormalized
        )
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
