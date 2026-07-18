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

        var emptyPathDataRemoved = 0
        var moveOnlyPathsRemoved = 0
        var invisiblePathsRemoved = 0

        val pathsPrunedXml = pathElementRegex.replace(syntaxOptimizedXml) { match ->
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
        val finalXml = formatVectorXml(groupCleanup.xml)
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
                xmlCharactersBefore = xml.length,
                xmlCharactersAfter = finalXml.length
            )
        )
    }


    /**
     * Applies presentation-only XML cleanup after all structural optimization.
     * This does not change element order, attributes, or rendering semantics.
     */
    private fun formatVectorXml(xml: String): String {
        var formatted = xml

        // Attribute-free groups can be emitted with the closing angle bracket
        // on a separate line. Keep those tags compact and human-readable.
        formatted = Regex(
            """(?m)^([ \t]*)<group[ \t]*\R[ \t]*>[ \t]*$""",
            RegexOption.IGNORE_CASE
        ).replace(formatted) { match ->
            "${match.groupValues[1]}<group>"
        }

        // Remove trailing whitespace without disturbing indentation.
        formatted = formatted.lines()
            .joinToString("\n") { it.trimEnd() }

        // Deleted paths/groups can leave several blank lines and orphaned
        // conversion comments. Remove comments that no longer introduce an
        // element, then collapse excessive vertical whitespace.
        formatted = removeOrphanedConversionComments(formatted)
        formatted = Regex("""\n[ \t]*\n(?:[ \t]*\n)+""")
            .replace(formatted, "\n\n")

        // Avoid blank padding immediately inside a group or before its close.
        formatted = Regex("""(<group(?:\s[^>]*)?>)\n[ \t]*\n""", RegexOption.IGNORE_CASE)
            .replace(formatted, "$1\n")
        formatted = Regex("""\n[ \t]*\n([ \t]*</group>)""", RegexOption.IGNORE_CASE)
            .replace(formatted, "\n$1")

        // Keep exactly one newline at end of the generated document.
        return formatted.trimEnd() + "\n"
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

    private fun removeEmptyGroups(xml: String): GroupCleanupResult {
        var current = xml
        var totalRemoved = 0

        while (true) {
            var removedThisPass = 0
            val next = innermostGroupRegex.replace(current) { match ->
                val body = match.groupValues[1]
                val meaningfulBody = xmlCommentRegex.replace(body, "").trim()
                if (meaningfulBody.isEmpty()) {
                    removedThisPass++
                    ""
                } else {
                    match.value
                }
            }

            totalRemoved += removedThisPass
            current = next
            if (removedThisPass == 0) break
        }

        return GroupCleanupResult(current, totalRemoved)
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
