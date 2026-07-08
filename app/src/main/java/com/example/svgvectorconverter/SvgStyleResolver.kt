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
                classNames = classNames(tagText)
            )
            val elementStyle = stylesheet.elementStyles[tagName].orEmpty()
            val idStyle = snapshot.id
                .takeIf { it.isNotBlank() }
                ?.let { stylesheet.idStyles[it] }
                .orEmpty()
            val classStyle = snapshot.classNames
                .mapNotNull { stylesheet.classStyles[it] }
                .joinToString("; ")
                .trim()
            val compoundStyle = stylesheet.compoundStyles
                .filter { it.matches(snapshot) }
                .fold("") { existing, rule -> mergeDeclarations(existing, rule.declarations) }
                .trim()
            val descendantStyle = stylesheet.descendantStyles
                .filter { it.matches(ancestors, snapshot) }
                .fold("") { existing, rule -> mergeDeclarations(existing, rule.declarations) }
                .trim()

            val rewrittenTag = if (elementStyle.isBlank() && idStyle.isBlank() && classStyle.isBlank() && compoundStyle.isBlank() && descendantStyle.isBlank()) {
                tagText
            } else {
                val existingStyle = attr(tagText, "style")?.trim().orEmpty()
                // SvgPaintResolver.styleValue(...) returns the first matching declaration.
                // Keep higher-precedence declarations first:
                // inline style > id selector > descendant selector > class selector > element selector.
                val mergedStyle = listOf(existingStyle, idStyle, compoundStyle, descendantStyle, classStyle, elementStyle)
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
        val idStyles: Map<String, String>,
        val classStyles: Map<String, String>,
        val elementStyles: Map<String, String>,
        val compoundStyles: List<CompoundStyleRule>,
        val descendantStyles: List<DescendantStyleRule>
    ) {
        fun isEmpty(): Boolean =
            idStyles.isEmpty() &&
                classStyles.isEmpty() &&
                elementStyles.isEmpty() &&
                compoundStyles.isEmpty() &&
                descendantStyles.isEmpty()
    }

    private data class ElementSnapshot(
        val tagName: String,
        val id: String,
        val classNames: List<String>
    )

    private data class CompoundStyleRule(
        val selector: SimpleSelector,
        val declarations: String
    ) {
        fun matches(element: ElementSnapshot): Boolean = selector.matches(element)
    }

    private data class DescendantStyleRule(
        val selectors: List<SimpleSelector>,
        val declarations: String
    ) {
        fun matches(ancestors: List<ElementSnapshot>, element: ElementSnapshot): Boolean {
            if (selectors.isEmpty()) return false
            if (!selectors.last().matches(element)) return false

            var ancestorIndex = ancestors.lastIndex
            for (selectorIndex in selectors.size - 2 downTo 0) {
                val selector = selectors[selectorIndex]
                var found = false
                while (ancestorIndex >= 0) {
                    if (selector.matches(ancestors[ancestorIndex])) {
                        found = true
                        ancestorIndex--
                        break
                    }
                    ancestorIndex--
                }
                if (!found) return false
            }
            return true
        }
    }

    private data class SimpleSelector(
        val tagName: String? = null,
        val id: String? = null,
        val classNames: List<String> = emptyList()
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
            return tagName != null || id != null || classNames.isNotEmpty()
        }

        fun isElementOnly(): Boolean = tagName != null && id == null && classNames.isEmpty()
        fun isIdOnly(): Boolean = tagName == null && id != null && classNames.isEmpty()
        fun isClassOnly(): Boolean = tagName == null && id == null && classNames.size == 1
    }

    private fun collectStylesheetRules(svg: String): StylesheetRules {
        val idStyles = linkedMapOf<String, String>()
        val classStyles = linkedMapOf<String, String>()
        val elementStyles = linkedMapOf<String, String>()
        val compoundStyles = mutableListOf<CompoundStyleRule>()
        val descendantStyles = mutableListOf<DescendantStyleRule>()
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
                    .forEach selectorLoop@{ selector ->
                        parseDescendantSelector(selector)?.let { selectors ->
                            descendantStyles.add(DescendantStyleRule(selectors, declarations))
                            return@selectorLoop
                        }

                        val simpleSelector = parseSimpleSelector(selector) ?: return@selectorLoop
                        when {
                            simpleSelector.isIdOnly() -> {
                                val idName = simpleSelector.id.orEmpty()
                                idStyles[idName] = mergeDeclarations(idStyles[idName], declarations)
                            }
                            simpleSelector.isClassOnly() -> {
                                val className = simpleSelector.classNames.first()
                                classStyles[className] = mergeDeclarations(classStyles[className], declarations)
                            }
                            simpleSelector.isElementOnly() -> {
                                val elementName = simpleSelector.tagName.orEmpty()
                                elementStyles[elementName] = mergeDeclarations(elementStyles[elementName], declarations)
                            }
                            else -> {
                                compoundStyles.add(CompoundStyleRule(simpleSelector, declarations))
                            }
                        }
                    }
            }
        }

        return StylesheetRules(idStyles, classStyles, elementStyles, compoundStyles, descendantStyles)
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

    private fun parseDescendantSelector(selector: String): List<SimpleSelector>? {
        if (!selector.contains(Regex("""\s+"""))) return null
        if (selector.contains('>') || selector.contains('+') || selector.contains('~')) return null

        val selectors = selector
            .split(Regex("""\s+"""))
            .map { parseSimpleSelector(it.trim()) ?: return null }

        return selectors.takeIf { it.size >= 2 }
    }

    private fun parseSimpleSelector(selector: String): SimpleSelector? {
        if (selector.isBlank() || selector.contains(':')) return null
        if (selector.contains('>') || selector.contains('+') || selector.contains('~')) return null
        if (selector.contains('[') || selector.contains(']') || selector.contains('*')) return null

        var remaining = selector.trim()
        var tagName: String? = null
        var id: String? = null
        val classNames = mutableListOf<String>()

        val tagMatch = Regex("""^[A-Za-z][\w:.-]*""").find(remaining)
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
                else -> return null
            }
        }

        return SimpleSelector(
            tagName = tagName,
            id = id,
            classNames = classNames.distinct()
        ).takeIf { it.tagName != null || it.id != null || it.classNames.isNotEmpty() }
    }

    private fun mergeDeclarations(existing: String?, declarations: String): String {
        return listOfNotNull(
            existing?.takeIf { it.isNotBlank() },
            declarations
        ).joinToString("; ")
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
