package com.example.svgvectorconverter

object SvgStyleResolver {
    fun applyStylesheets(svg: String): String {
        val stylesheet = collectStylesheetRules(svg)
        if (stylesheet.isEmpty()) return svg

        val tagPattern = Regex("""<\s*([A-Za-z][\w:.-]*)([^<>]*?)>""", RegexOption.IGNORE_CASE)

        return tagPattern.replace(svg) { match ->
            val tagName = match.groupValues.getOrNull(1).orEmpty().substringAfter(":").lowercase()
            if (tagName == "style") return@replace match.value

            val tagText = match.value
            val elementStyle = stylesheet.elementStyles[tagName].orEmpty()
            val idStyle = attr(tagText, "id")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { stylesheet.idStyles[it] }
                .orEmpty()
            val classStyle = classNames(tagText)
                .mapNotNull { stylesheet.classStyles[it] }
                .joinToString("; ")
                .trim()

            if (elementStyle.isBlank() && idStyle.isBlank() && classStyle.isBlank()) return@replace tagText

            val existingStyle = attr(tagText, "style")?.trim().orEmpty()
            // SvgPaintResolver.styleValue(...) returns the first matching declaration.
            // Keep higher-precedence declarations first:
            // inline style > id selector > class selector > element selector.
            val mergedStyle = listOf(existingStyle, idStyle, classStyle, elementStyle)
                .filter { it.isNotBlank() }
                .joinToString("; ")
                .trim()
                .trimEnd(';')

            writeStyleAttribute(tagText, mergedStyle)
        }
    }

    private data class StylesheetRules(
        val idStyles: Map<String, String>,
        val classStyles: Map<String, String>,
        val elementStyles: Map<String, String>
    ) {
        fun isEmpty(): Boolean = idStyles.isEmpty() && classStyles.isEmpty() && elementStyles.isEmpty()
    }

    private fun collectStylesheetRules(svg: String): StylesheetRules {
        val idStyles = linkedMapOf<String, String>()
        val classStyles = linkedMapOf<String, String>()
        val elementStyles = linkedMapOf<String, String>()
        val styleBlocks = Regex(
            """<\s*style\b[^>]*>(.*?)</\s*style\s*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(svg)

        val rulePattern = Regex("""([^{}]+)\{([^{}]+)\}""", RegexOption.DOT_MATCHES_ALL)
        val idSelectorPattern = Regex("""^#([A-Za-z_][\w:.-]*)$""")
        val classSelectorPattern = Regex("""^\.([A-Za-z_][\w-]*)$""")
        val elementSelectorPattern = Regex("""^[A-Za-z][\w:.-]*$""")

        styleBlocks.forEach { block ->
            val css = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
                .replace(block.groupValues.getOrNull(1).orEmpty(), "")

            rulePattern.findAll(css).forEach { rule ->
                val declarations = normalizeCssDeclarations(rule.groupValues.getOrNull(2).orEmpty())
                if (declarations.isBlank()) return@forEach

                rule.groupValues.getOrNull(1).orEmpty()
                    .split(',')
                    .map { it.trim() }
                    .forEach { selector ->
                        val idName = idSelectorPattern.matchEntire(selector)
                            ?.groupValues
                            ?.getOrNull(1)
                        if (!idName.isNullOrBlank()) {
                            idStyles[idName] = mergeDeclarations(idStyles[idName], declarations)
                            return@forEach
                        }

                        val className = classSelectorPattern.matchEntire(selector)
                            ?.groupValues
                            ?.getOrNull(1)
                        if (!className.isNullOrBlank()) {
                            classStyles[className] = mergeDeclarations(classStyles[className], declarations)
                            return@forEach
                        }

                        if (elementSelectorPattern.matches(selector) && !selector.contains(':')) {
                            val elementName = selector.substringAfter(":").lowercase()
                            elementStyles[elementName] = mergeDeclarations(elementStyles[elementName], declarations)
                        }
                    }
            }
        }

        return StylesheetRules(idStyles, classStyles, elementStyles)
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
