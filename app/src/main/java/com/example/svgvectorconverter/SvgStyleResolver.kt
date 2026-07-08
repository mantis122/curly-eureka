package com.example.svgvectorconverter

object SvgStyleResolver {
    fun applyStylesheets(svg: String): String {
        val stylesheet = collectStylesheetRules(svg)
        if (stylesheet.isEmpty()) return svg

        val tagPattern = Regex(
            """<\s*(/?)\s*([A-Za-z][\w:.-]*)([^<>]*?)(/?)>""",
            RegexOption.IGNORE_CASE
        )
        val ancestors = mutableListOf<ElementSnapshot>()
        val output = StringBuilder()
        var lastIndex = 0

        tagPattern.findAll(svg).forEach { match ->
            output.append(svg.substring(lastIndex, match.range.first))
            lastIndex = match.range.last + 1

            val isClosing = match.groupValues.getOrNull(1).orEmpty() == "/"
            val rawTagName = match.groupValues.getOrNull(2).orEmpty()
            val tagName = rawTagName.substringAfter(":").lowercase()
            val tagText = match.value

            if (isClosing) {
                val lastMatchingIndex = ancestors.indexOfLast { it.tagName == tagName }
                if (lastMatchingIndex >= 0) {
                    while (ancestors.size > lastMatchingIndex) ancestors.removeAt(ancestors.lastIndex)
                }
                output.append(tagText)
                return@forEach
            }

            if (tagName == "style") {
                output.append(tagText)
                return@forEach
            }

            val selfClosing = tagText.trimEnd().endsWith("/>")
            val snapshot = ElementSnapshot(
                tagName = tagName,
                id = attr(tagText, "id")?.trim().orEmpty(),
                classNames = classNames(tagText),
                attributes = attributes(tagText)
            )

            val matchedStyles = stylesheet.rules
                .asSequence()
                .filter { it.matches(ancestors, snapshot) }
                .sortedWith(
                    compareByDescending<StyleRule> { it.specificity.idCount }
                        .thenByDescending { it.specificity.classCount }
                        .thenByDescending { it.specificity.elementCount }
                        .thenByDescending { it.sourceOrder }
                )
                .map { it.declarations }
                .filter { it.isNotBlank() }
                .toList()

            val rewrittenTag = if (matchedStyles.isEmpty()) {
                tagText
            } else {
                val existingStyle = attr(tagText, "style")?.trim().orEmpty()
                // SvgPaintResolver.styleValue(...) returns the first matching declaration.
                // Keep higher-precedence declarations first:
                // inline style > CSS specificity > CSS source order.
                val mergedStyle = listOf(existingStyle)
                    .plus(matchedStyles)
                    .filter { it.isNotBlank() }
                    .joinToString("; ")
                    .trim()
                    .trimEnd(';')

                writeStyleAttribute(tagText, mergedStyle)
            }

            output.append(rewrittenTag)
            if (!selfClosing) ancestors.add(snapshot)
        }

        output.append(svg.substring(lastIndex))
        return output.toString()
    }

    private data class StylesheetRules(
        val rules: List<StyleRule>
    ) {
        fun isEmpty(): Boolean = rules.isEmpty()
    }

    private data class ElementSnapshot(
        val tagName: String,
        val id: String,
        val classNames: List<String>,
        val attributes: Map<String, String>
    )

    private data class StyleRule(
        val selector: CssSelector,
        val declarations: String,
        val specificity: Specificity,
        val sourceOrder: Int
    ) {
        fun matches(ancestors: List<ElementSnapshot>, element: ElementSnapshot): Boolean =
            selector.matches(ancestors, element)
    }

    private data class CssSelector(
        val selectors: List<SimpleSelector>,
        val combinators: List<Char>
    ) {
        fun matches(ancestors: List<ElementSnapshot>, element: ElementSnapshot): Boolean {
            if (selectors.isEmpty()) return false
            if (!selectors.last().matches(element)) return false
            if (selectors.size == 1) return true

            var ancestorIndex = ancestors.lastIndex
            for (selectorIndex in selectors.size - 2 downTo 0) {
                val combinator = combinators.getOrNull(selectorIndex) ?: ' '
                val selector = selectors[selectorIndex]

                when (combinator) {
                    '>' -> {
                        if (ancestorIndex < 0) return false
                        if (!selector.matches(ancestors[ancestorIndex])) return false
                        ancestorIndex--
                    }
                    ' ' -> {
                        var foundIndex = -1
                        while (ancestorIndex >= 0) {
                            if (selector.matches(ancestors[ancestorIndex])) {
                                foundIndex = ancestorIndex
                                break
                            }
                            ancestorIndex--
                        }
                        if (foundIndex < 0) return false
                        ancestorIndex = foundIndex - 1
                    }
                    else -> return false
                }
            }

            return true
        }

        val specificity: Specificity
            get() = selectors.fold(Specificity()) { total, selector -> total + selector.specificity }
    }

    private data class Specificity(
        val idCount: Int = 0,
        val classCount: Int = 0,
        val elementCount: Int = 0
    ) {
        operator fun plus(other: Specificity): Specificity = Specificity(
            idCount = idCount + other.idCount,
            classCount = classCount + other.classCount,
            elementCount = elementCount + other.elementCount
        )
    }

    private data class SimpleSelector(
        val tagName: String? = null,
        val id: String? = null,
        val classNames: List<String> = emptyList(),
        val attributeSelectors: List<AttributeSelector> = emptyList()
    ) {
        fun matches(element: ElementSnapshot): Boolean {
            tagName?.let {
                if (element.tagName != it) return false
            }
            id?.let {
                if (element.id != it) return false
            }
            classNames.forEach { className ->
                if (className !in element.classNames) return false
            }
            attributeSelectors.forEach { attributeSelector ->
                if (!attributeSelector.matches(element)) return false
            }
            return tagName != null || id != null || classNames.isNotEmpty() || attributeSelectors.isNotEmpty()
        }

        val specificity: Specificity
            get() = Specificity(
                idCount = if (id != null) 1 else 0,
                classCount = classNames.size + attributeSelectors.size,
                elementCount = if (tagName != null) 1 else 0
            )
    }

    private data class AttributeSelector(
        val name: String,
        val operator: String? = null,
        val value: String? = null
    ) {
        fun matches(element: ElementSnapshot): Boolean {
            val actualValue = element.attributes[name] ?: return false
            val expectedValue = value ?: return operator == null

            return when (operator) {
                null -> true
                "=" -> actualValue == expectedValue
                "~=" -> actualValue.split(Regex("""\s+""")).any { it == expectedValue }
                "|=" -> actualValue == expectedValue || actualValue.startsWith("$expectedValue-")
                "^=" -> actualValue.startsWith(expectedValue)
                "$=" -> actualValue.endsWith(expectedValue)
                "*=" -> actualValue.contains(expectedValue)
                else -> false
            }
        }
    }

    private fun collectStylesheetRules(svg: String): StylesheetRules {
        val rules = mutableListOf<StyleRule>()
        var sourceOrder = 0
        val styleBlocks = Regex(
            """<\s*style\b[^>]*>(.*?)</\s*style\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(svg)

        val rulePattern = Regex("""([^{}]+)\{([^{}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        styleBlocks.forEach { block ->
            val css = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
                .replace(block.groupValues.getOrNull(1).orEmpty(), "")

            rulePattern.findAll(css).forEach ruleLoop@{ rule ->
                val declarations = normalizeCssDeclarations(rule.groupValues.getOrNull(2).orEmpty())
                if (declarations.isBlank()) return@ruleLoop

                splitSelectorList(rule.groupValues.getOrNull(1).orEmpty())
                    .forEach selectorLoop@{ selectorText ->
                        val selector = parseCssSelector(selectorText) ?: return@selectorLoop
                        rules.add(
                            StyleRule(
                                selector = selector,
                                declarations = declarations,
                                specificity = selector.specificity,
                                sourceOrder = sourceOrder++
                            )
                        )
                    }
            }
        }

        return StylesheetRules(rules)
    }

    private fun splitSelectorList(selectorList: String): List<String> {
        val selectors = mutableListOf<String>()
        val current = StringBuilder()
        var bracketDepth = 0
        var parenDepth = 0
        var quote: Char? = null

        selectorList.forEach { char ->
            when {
                quote != null -> {
                    current.append(char)
                    if (char == quote) quote = null
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    current.append(char)
                }
                char == '[' -> {
                    bracketDepth++
                    current.append(char)
                }
                char == ']' -> {
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    current.append(char)
                }
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth = (parenDepth - 1).coerceAtLeast(0)
                    current.append(char)
                }
                char == ',' && bracketDepth == 0 && parenDepth == 0 -> {
                    current.toString().trim().takeIf { it.isNotBlank() }?.let { selectors.add(it) }
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        current.toString().trim().takeIf { it.isNotBlank() }?.let { selectors.add(it) }
        return selectors
    }

    private fun parseCssSelector(selector: String): CssSelector? {
        if (selector.isBlank() || selector.contains(':')) return null
        if (containsOutsideAttributeSelector(selector, '+')) return null
        if (containsOutsideAttributeSelector(selector, '~')) return null

        val tokens = tokenizeCssSelector(selector)

        if (tokens.isEmpty()) return null

        val selectors = mutableListOf<SimpleSelector>()
        val combinators = mutableListOf<Char>()
        var pendingCombinator: Char? = null

        tokens.forEach { token ->
            if (token == ">") {
                if (selectors.isEmpty() || pendingCombinator != null) return null
                pendingCombinator = '>'
                return@forEach
            }

            val simpleSelector = parseSimpleSelector(token) ?: return null
            if (selectors.isNotEmpty()) {
                combinators.add(pendingCombinator ?: ' ')
            }
            selectors.add(simpleSelector)
            pendingCombinator = null
        }

        if (pendingCombinator != null) return null
        if (combinators.size != selectors.size - 1) return null

        return CssSelector(selectors, combinators)
    }

    private fun parseSimpleSelector(selector: String): SimpleSelector? {
        if (selector.isBlank() || selector.contains(':')) return null
        if (containsOutsideAttributeSelector(selector, '>')) return null
        if (containsOutsideAttributeSelector(selector, '+')) return null
        if (containsOutsideAttributeSelector(selector, '~')) return null
        if (containsOutsideAttributeSelector(selector, '*')) return null

        var remaining = selector.trim()
        var tagName: String? = null
        var id: String? = null
        val classNames = mutableListOf<String>()
        val attributeSelectors = mutableListOf<AttributeSelector>()

        // CSS uses `.` to introduce class selectors, so keep dots out of the
        // optional tag-name prefix. This lets selectors such as `circle.dot`
        // parse as tag `circle` + class `dot` instead of tag `circle.dot`.
        val tagMatch = Regex("""^[A-Za-z][\w:-]*""").find(remaining)
        if (tagMatch != null) {
            val rawTagName = tagMatch.value
            if (rawTagName.contains(':')) return null
            tagName = rawTagName.lowercase()
            remaining = remaining.substring(tagMatch.range.last + 1)
        }

        while (remaining.isNotBlank()) {
            when {
                remaining.startsWith("#") -> {
                    if (id != null) return null
                    val match = Regex("""^#([A-Za-z_][\w:.-]*)""").find(remaining) ?: return null
                    id = match.groupValues.getOrNull(1).orEmpty()
                    remaining = remaining.substring(match.range.last + 1)
                }
                remaining.startsWith(".") -> {
                    val match = Regex("""^\.([A-Za-z_][\w-]*)""").find(remaining) ?: return null
                    classNames.add(match.groupValues.getOrNull(1).orEmpty())
                    remaining = remaining.substring(match.range.last + 1)
                }
                remaining.startsWith("[") -> {
                    val endIndex = findAttributeSelectorEnd(remaining)
                    if (endIndex <= 0) return null
                    val attributeSelector = parseAttributeSelector(remaining.substring(1, endIndex)) ?: return null
                    attributeSelectors.add(attributeSelector)
                    remaining = remaining.substring(endIndex + 1)
                }
                else -> return null
            }
        }

        return SimpleSelector(
            tagName = tagName,
            id = id,
            classNames = classNames.distinct(),
            attributeSelectors = attributeSelectors
        ).takeIf { it.tagName != null || it.id != null || it.classNames.isNotEmpty() || it.attributeSelectors.isNotEmpty() }
    }


    private fun containsOutsideAttributeSelector(selector: String, target: Char): Boolean {
        var bracketDepth = 0
        var quote: Char? = null

        selector.forEach { char ->
            when {
                quote != null -> {
                    if (char == quote) quote = null
                }
                char == '\'' || char == '"' -> quote = char
                char == '[' -> bracketDepth++
                char == ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                char == target && bracketDepth == 0 -> return true
            }
        }

        return false
    }

    private fun tokenizeCssSelector(selector: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var bracketDepth = 0
        var quote: Char? = null

        fun flushCurrent() {
            current.toString().trim().takeIf { it.isNotBlank() }?.let { tokens.add(it) }
            current.clear()
        }

        selector.trim().forEach { char ->
            when {
                quote != null -> {
                    current.append(char)
                    if (char == quote) quote = null
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    current.append(char)
                }
                char == '[' -> {
                    bracketDepth++
                    current.append(char)
                }
                char == ']' -> {
                    bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                    current.append(char)
                }
                char == '>' && bracketDepth == 0 -> {
                    flushCurrent()
                    tokens.add(">")
                }
                char.isWhitespace() && bracketDepth == 0 -> flushCurrent()
                else -> current.append(char)
            }
        }

        flushCurrent()
        return tokens
    }

    private fun findAttributeSelectorEnd(selector: String): Int {
        var quote: Char? = null
        selector.forEachIndexed { index, char ->
            when {
                quote != null -> {
                    if (char == quote) quote = null
                }
                char == '\'' || char == '"' -> quote = char
                char == ']' -> return index
            }
        }
        return -1
    }

    private fun parseAttributeSelector(selectorBody: String): AttributeSelector? {
        val trimmed = selectorBody.trim()
        if (trimmed.isBlank()) return null

        val match = Regex(
            """^([A-Za-z_][\w:.-]*)(?:\s*([~|^$*]?=)\s*(.+))?$"""
        ).find(trimmed) ?: return null

        val rawName = match.groupValues.getOrNull(1).orEmpty()
        if (rawName.contains(':')) return null
        val operator = match.groupValues.getOrNull(2).orEmpty().takeIf { it.isNotBlank() }
        val rawValue = match.groupValues.getOrNull(3).orEmpty().trim().takeIf { it.isNotBlank() }

        val value = rawValue?.let { stripCssQuotes(it) }
        if (operator != null && value == null) return null

        return AttributeSelector(
            name = rawName.lowercase(),
            operator = operator,
            value = value
        )
    }

    private fun stripCssQuotes(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            val first = trimmed.first()
            val last = trimmed.last()
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return trimmed.substring(1, trimmed.length - 1)
            }
        }
        return trimmed
    }

    private fun normalizeCssDeclarations(css: String): String {
        return css
            .split(';')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(':') }
            .joinToString("; ")
    }

    private fun classNames(tagText: String): List<String> {
        return attr(tagText, "class")
            ?.split(Regex("""\s+"""))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun attributes(tagText: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        Regex("""\b([A-Za-z_][\w:.-]*)\s*=\s*(['\"])(.*?)\2""", RegexOption.IGNORE_CASE)
            .findAll(tagText)
            .forEach { match ->
                val rawName = match.groupValues.getOrNull(1).orEmpty()
                if (!rawName.contains(':')) {
                    result[rawName.lowercase()] = match.groupValues.getOrNull(3).orEmpty()
                }
            }
        return result
    }

    private fun writeStyleAttribute(tagText: String, mergedStyle: String): String {
        val styleAttrPattern = Regex("""\sstyle\s*=\s*(['\"])(.*?)\1""", RegexOption.IGNORE_CASE)
        return if (styleAttrPattern.containsMatchIn(tagText)) {
            styleAttrPattern.replace(tagText, " style=\"${SvgPathEmitter.escapeXml(mergedStyle)}\"")
        } else {
            val insertAt = tagText.lastIndexOf('>')
            if (insertAt <= 0) tagText else {
                val beforeEnd = tagText.substring(0, insertAt).trimEnd()
                val close = tagText.substring(insertAt)
                val slash = if (beforeEnd.endsWith("/")) "/" else ""
                val tagWithoutSlash = if (slash.isNotEmpty()) beforeEnd.dropLast(1).trimEnd() else beforeEnd
                "$tagWithoutSlash style=\"${SvgPathEmitter.escapeXml(mergedStyle)}\"$slash$close"
            }
        }
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name\s*=\s*(['\"])(.*?)\1""", RegexOption.IGNORE_CASE)
            .find(tag)
            ?.groupValues
            ?.getOrNull(2)
    }
}
