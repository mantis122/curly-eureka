package com.example.svgvectorconverter

import org.w3c.dom.Element

object SvgToVectorConverter {
fun convert(
    svg: String,
    outputDpSize: Int,
    conversionProfile: String
): ConversionResult {

val startTime = System.nanoTime()

SvgTransformParser.resetMatrixStats()
val svgForTransformStats = stripSvgComments(svg)
val gradientFallbackColors = SvgPaintResolver.collectGradientFallbackColors(svg)
SvgPaintResolver.setGradientFallbackColors(gradientFallbackColors)
val clipPathData = SvgTreeConverter.collectClipPathData(svg, ::basicShapeToPathData)
SvgTreeConverter.resetStats(clipPathData)


val translateCount = Regex("""translate\(""").findAll(svgForTransformStats).count()
val scaleCount = Regex("""scale\(""").findAll(svgForTransformStats).count()
val rotateCount = Regex("""rotate\(""").findAll(svgForTransformStats).count()
val matrixCount = Regex("""matrix\(""").findAll(svgForTransformStats).count()

val pathCount = Regex("""<path\b[^>]*>""").findAll(svg).count()
val validPathCount = Regex("""<path\b[^>]*>""")
    .findAll(svg)
    .count { match ->
        val d = attr(match.value, "d")?.trim()
        !d.isNullOrBlank()
    }
val drawableSvgForStats = stripDefs(svg)

val drawableValidPathCount = Regex("""<path\b[^>]*>""")
    .findAll(drawableSvgForStats)
    .count { match ->
        val d = attr(match.value, "d")?.trim()
        !d.isNullOrBlank()
    }

val definitionDrawableElementCount = SvgConversionReporter.countDefinitionDrawableElements(svg)
val emptyPathCount = pathCount - validPathCount
val useCount = Regex("""<\s*use\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
val symbolCount = Regex("""<\s*symbol\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
val clipPathReferenceCount = Regex("""clip-path\s*[:=]""", RegexOption.IGNORE_CASE)
    .findAll(svgForTransformStats)
    .count()
val styleAttributeCount = Regex("""\bstyle\s*=""", RegexOption.IGNORE_CASE)
    .findAll(svgForTransformStats)
    .count()
val presentationStyleAttributeCount = listOf(
    "fill",
    "stroke",
    "stroke-width",
    "stroke-linecap",
    "stroke-linejoin",
    "stroke-miterlimit",
    "fill-rule",
    "opacity",
    "fill-opacity",
    "stroke-opacity"
).sumOf { name ->
    Regex("""\b$name\s*=""", RegexOption.IGNORE_CASE)
        .findAll(svgForTransformStats)
        .count()
}


val unsupported = mutableListOf<String>()

if (SvgConversionReporter.hasTag(svg, "linearGradient")) {
    unsupported.add(
        if (gradientFallbackColors.isNotEmpty()) "Linear gradients converted to fallback colors" else "Linear gradients"
    )
}
if (SvgConversionReporter.hasTag(svg, "radialGradient")) {
    unsupported.add(
        if (gradientFallbackColors.isNotEmpty()) "Radial gradients converted to fallback colors" else "Radial gradients"
    )
}
if (SvgConversionReporter.hasTag(svg, "mask")) unsupported.add("Masks")
if (SvgConversionReporter.hasTag(svg, "filter")) unsupported.add("Filters")
if (SvgConversionReporter.hasTag(svg, "text")) unsupported.add("Text elements")
if (SvgConversionReporter.hasTag(svg, "clipPath") && clipPathData.isEmpty()) unsupported.add("Clip paths")
if (SvgConversionReporter.hasTag(svg, "pattern")) unsupported.add("Patterns")
if (SvgConversionReporter.hasTag(svg, "image")) unsupported.add("Embedded images")

        val viewBoxValues = getViewBox(svg)

        val widthFromSvg = getNumberAttr(svg, "width")
        val heightFromSvg = getNumberAttr(svg, "height")

        val viewportWidth = viewBoxValues?.getOrNull(2)
            ?: widthFromSvg
            ?: 24f

        val viewportHeight = viewBoxValues?.getOrNull(3)
            ?: heightFromSvg
            ?: 24f

val vectorWidthDp =
    if (outputDpSize > 0) outputDpSize else viewportWidth.toInt()

val vectorHeightDp =
    if (outputDpSize > 0) outputDpSize else viewportHeight.toInt()        

        val output = StringBuilder()

        output.appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
        output.appendLine("""    android:width="${vectorWidthDp}dp"""")
        output.appendLine("""    android:height="${vectorHeightDp}dp"""")
        output.appendLine("""    android:viewportWidth="$viewportWidth"""")
        output.appendLine("""    android:viewportHeight="$viewportHeight">""")
        output.appendLine()

        SvgTreeConverter.appendConvertedSvgTree(
            output = output,
            svg = svg,
            appendElementPath = ::appendElementPath,
            appendBasicShapePath = ::appendBasicShapePath,
            appendFlatPathsFallback = ::appendFlatPathsFallback,
            basicShapeToPathData = ::basicShapeToPathData,
            floatAttr = ::floatAttr,
            escapeXml = ::escapeXml
        )

        output.appendLine("</vector>")

        val result = output.toString().trim()
        val endTag = "</vector>"
        val endIndex = result.indexOf(endTag)

val rawFinalXml = if (endIndex >= 0) {
    result.substring(0, endIndex + endTag.length)
} else {
    result
}

val finalXml = optimizeDuplicateClipPathGroups(rawFinalXml)

val convertedPathCount = Regex("""<path\b""").findAll(finalXml).count()
val convertedBasicShapeCount = SvgConversionReporter.countConvertedBasicShapes(finalXml)
val basicShapeBreakdown = SvgConversionReporter.countDrawableBasicShapeBreakdown(drawableSvgForStats)
val convertedOriginalPathCount = convertedPathCount - convertedBasicShapeCount
val generatedGroupCount = Regex("""<group\b""").findAll(finalXml).count()

val elapsedMs =
    (System.nanoTime() - startTime) / 1_000_000

val warningCount =
    unsupported.size +
    if (SvgTransformParser.unsupportedMatrixTransforms > 0) 1 else 0


val report = SvgConversionReporter.buildReport(
    SvgConversionReportData(
        convertedPathCount = convertedPathCount,
        convertedOriginalPathCount = convertedOriginalPathCount,
        convertedBasicShapeCount = convertedBasicShapeCount,
        basicShapeBreakdown = basicShapeBreakdown,
        definitionDrawableElementCount = definitionDrawableElementCount,
        drawableValidPathCount = drawableValidPathCount,
        emptyPathCount = emptyPathCount,
        generatedGroupCount = generatedGroupCount,
        useCount = useCount,
        resolvedUseExpansions = SvgTreeConverter.resolvedUseExpansions,
        symbolCount = symbolCount,
        gradientFallbackColorCount = gradientFallbackColors.size,
        clipPathCount = clipPathData.size,
        clipPathReferenceCount = clipPathReferenceCount,
        appliedClipPaths = SvgTreeConverter.appliedClipPaths,
        styleAttributeCount = styleAttributeCount,
        presentationStyleAttributeCount = presentationStyleAttributeCount,
        warningCount = warningCount,
        unsupportedWarnings = unsupported,
        unsupportedMatrixTransforms = SvgTransformParser.unsupportedMatrixTransforms,
        supportedMatrixTransforms = SvgTransformParser.supportedMatrixTransforms,
        matrixCount = matrixCount,
        translateCount = translateCount,
        scaleCount = scaleCount,
        rotateCount = rotateCount,
        conversionProfile = conversionProfile,
        outputDpSize = outputDpSize,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        elapsedMs = elapsedMs
    )
)

return ConversionResult(finalXml, report)


    }

private fun optimizeDuplicateClipPathGroups(xml: String): String {
    fun reindentBlock(block: String, indent: String): String {
        return block
            .lines()
            .joinToString("\n") { line ->
                if (line.isBlank()) line else indent + line.trimStart()
            }
    }

    val pattern = Regex(
        """(?s)([ \t]*)<group\s*>\s*(<clip-path\s+android:pathData="([^"]+)"\s*/>)\s*(.*?)\s*\1</group>\s*\1<group\s*>\s*<clip-path\s+android:pathData="\3"\s*/>\s*(.*?)\s*\1</group>"""
    )

    var current = xml

    while (true) {
        val updated = pattern.replace(current) { match ->
            val indent = match.groupValues[1]
            val clipPath = match.groupValues[2]
            val firstBody = match.groupValues[4].trimEnd()
            val secondBody = match.groupValues[5].trim()

            buildString {
                appendLine("${indent}<group")
                appendLine("${indent}>")
                appendLine(reindentBlock(clipPath, "$indent    "))

                if (firstBody.isNotBlank()) {
                    appendLine(firstBody)
                }

                if (secondBody.isNotBlank()) {
                    appendLine(secondBody)
                }

                append("${indent}</group>")
            }
        }

        if (updated == current) return current
        current = updated
    }
}

private fun stripSvgComments(xml: String): String {
    return Regex(
        """<!--.*?-->""",
        RegexOption.DOT_MATCHES_ALL
    ).replace(xml, "")
}


private fun stripDefs(xml: String): String {
    return Regex(
        """<defs\b[^>]*>.*?</defs>""",
        RegexOption.DOT_MATCHES_ALL
    ).replace(xml, "")
}


private fun appendBasicShapePath(
    output: StringBuilder,
    element: Element,
    tagName: String,
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
    activeClipPathId: String? = null
) {
    val d = basicShapeToPathData(element, tagName) ?: return

    appendElementPathData(
        output,
        element,
        d,
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
        activeClipPathId,
        sourceTag = tagName
    )
}

private fun appendElementPath(
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
    activeClipPathId: String? = null
) {
    val d = element.getAttribute("d").trim()
    if (d.isBlank()) return

    appendElementPathData(
        output,
        element,
        d,
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
        activeClipPathId,
        sourceTag = "path"
    )
}

private fun appendElementPathData(
    output: StringBuilder,
    element: Element,
    d: String,
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
    activeClipPathId: String? = null,
    sourceTag: String?
) {
    val style = element.getAttribute("style").ifBlank { null }

val rawFill = SvgPaintResolver.styleValue(style, "fill")
    ?: element.getAttribute("fill").ifBlank { inheritedFill ?: "#000000" }

val rawStroke = SvgPaintResolver.styleValue(style, "stroke")
    ?: element.getAttribute("stroke").ifBlank { inheritedStroke ?: "" }

val strokeWidth = SvgPaintResolver.styleValue(style, "stroke-width")
    ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

val strokeLineCap = SvgPaintResolver.styleValue(style, "stroke-linecap")
    ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

val strokeLineJoin = SvgPaintResolver.styleValue(style, "stroke-linejoin")
    ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

val strokeMiterLimit = SvgPaintResolver.styleValue(style, "stroke-miterlimit")
    ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

val fillRule = SvgPaintResolver.styleValue(style, "fill-rule")
    ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

val fillOpacity = SvgPaintResolver.styleValue(style, "fill-opacity")
    ?: element.getAttribute("fill-opacity").ifBlank { inheritedFillOpacity ?: "" }

val strokeOpacity = SvgPaintResolver.styleValue(style, "stroke-opacity")
    ?: element.getAttribute("stroke-opacity").ifBlank { inheritedStrokeOpacity ?: "" }

val clipPathValue = SvgPaintResolver.styleValue(style, "clip-path")
    ?: element.getAttribute("clip-path").ifBlank { inheritedClipPath ?: "" }
val clipPathId = SvgTreeConverter.clipPathIdFromValue(clipPathValue)
val hasClipPath = clipPathId != null && clipPathId != activeClipPathId && SvgTreeConverter.hasClipPathData(clipPathId)

val fillAlpha = SvgPaintResolver.resolveDrawableAlpha(inheritedOpacity, fillOpacity)
val strokeAlpha = SvgPaintResolver.resolveDrawableAlpha(inheritedOpacity, strokeOpacity)

    val fill =
        if (sourceTag == "line") {
            "@android:color/transparent"
        } else {
            SvgPaintResolver.safeFillColor(rawFill)
        }
    val stroke = SvgPaintResolver.safeStrokeColor(rawStroke)

    val pathTransform = element.getAttribute("transform")
    val transforms = SvgTransformParser.parseTransformList(pathTransform)

    val pathNeedsGroup = transforms.any { it.hasVisibleEffect() } || hasClipPath

    if (pathNeedsGroup) {
        var currentIndent = indent
        var openedGroupCount = 0

        if (hasClipPath) {
            output.appendLine("${currentIndent}<group>")
            SvgTreeConverter.appendClipPath(output, clipPathId, currentIndent + "    ")
            currentIndent += "    "
            openedGroupCount++
        }

        val openedTransformGroups = SvgTransformParser.appendTransformGroupsStart(output, transforms, currentIndent)
        currentIndent = openedTransformGroups.first
        openedGroupCount += openedTransformGroups.second

        if (sourceTag != null) {
            output.appendLine("${currentIndent}<!-- converted from <$sourceTag> -->")
        }
        appendPath(
            output,
            d,
            fill,
            stroke,
            strokeWidth.ifBlank { null },
            strokeLineCap.ifBlank { null },
            strokeLineJoin.ifBlank { null },
            strokeMiterLimit.ifBlank { null },
            fillRule.ifBlank { null },
            fillAlpha,
            strokeAlpha,
            currentIndent
        )

        repeat(openedGroupCount) {
            currentIndent = currentIndent.dropLast(4)
            output.appendLine("${currentIndent}</group>")
        }
    } else {
        if (sourceTag != null) {
            output.appendLine("${indent}<!-- converted from <$sourceTag> -->")
        }
        appendPath(
            output,
            d,
            fill,
            stroke,
            strokeWidth.ifBlank { null },
            strokeLineCap.ifBlank { null },
            strokeLineJoin.ifBlank { null },
            strokeMiterLimit.ifBlank { null },
            fillRule.ifBlank { null },
            fillAlpha,
            strokeAlpha,
            indent
        )
    }

    output.appendLine()
}


private fun basicShapeToPathData(element: Element, tagName: String): String? {
    return SvgShapeConverters.basicShapeToPathData(element, tagName)
}

private fun floatAttr(element: Element, name: String): Float? {
    return element.getAttribute(name)
        .replace("px", "")
        .replace("dp", "")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.toFloatOrNull()
}

private fun appendFlatPathsFallback(
    output: StringBuilder,
    xml: String,
    indent: String
) {
    val drawableTags = Regex("""<(path|rect|circle|ellipse|line|polyline|polygon)\b[^>]*>""", RegexOption.IGNORE_CASE)

    drawableTags
        .findAll(xml)
        .forEach { match ->
            val tag = match.value
            val tagName = match.groupValues[1].lowercase()

            val d = if (tagName == "path") {
                attr(tag, "d")?.trim().orEmpty()
            } else {
                basicShapeTagToPathData(tag, tagName)
            }

            if (d.isBlank()) return@forEach

            val rawFill = SvgPaintResolver.styleValue(attr(tag, "style"), "fill")
                ?: attr(tag, "fill")

            val rawStroke = SvgPaintResolver.styleValue(attr(tag, "style"), "stroke")
                ?: attr(tag, "stroke")

            val strokeWidth = SvgPaintResolver.styleValue(attr(tag, "style"), "stroke-width")
                ?: attr(tag, "stroke-width")

            val strokeLineCap = SvgPaintResolver.styleValue(attr(tag, "style"), "stroke-linecap")
                ?: attr(tag, "stroke-linecap")

            val strokeLineJoin = SvgPaintResolver.styleValue(attr(tag, "style"), "stroke-linejoin")
                ?: attr(tag, "stroke-linejoin")

            val strokeMiterLimit = SvgPaintResolver.styleValue(attr(tag, "style"), "stroke-miterlimit")
                ?: attr(tag, "stroke-miterlimit")

            val opacity = SvgPaintResolver.styleValue(attr(tag, "style"), "opacity")
                ?: attr(tag, "opacity")

            val fillOpacity = SvgPaintResolver.styleValue(attr(tag, "style"), "fill-opacity")
                ?: attr(tag, "fill-opacity")

            val strokeOpacity = SvgPaintResolver.styleValue(attr(tag, "style"), "stroke-opacity")
                ?: attr(tag, "stroke-opacity")

            val fillRule = SvgPaintResolver.styleValue(attr(tag, "style"), "fill-rule")
                ?: attr(tag, "fill-rule")

            if (tagName != "path") {
                output.appendLine("${indent}<!-- converted from <$tagName> -->")
            }

            val fillColor =
                if (tagName == "line") {
                    "@android:color/transparent"
                } else {
                    SvgPaintResolver.safeFillColor(rawFill)
                }

            appendPath(
                output,
                d,
                fillColor,
                SvgPaintResolver.safeStrokeColor(rawStroke),
                strokeWidth,
                strokeLineCap,
                strokeLineJoin,
                strokeMiterLimit,
                fillRule,
                SvgPaintResolver.resolveDrawableAlpha(opacity, fillOpacity),
                SvgPaintResolver.resolveDrawableAlpha(opacity, strokeOpacity),
                indent
            )

            output.appendLine()
        }
}

private fun basicShapeTagToPathData(tag: String, tagName: String): String {
    return SvgShapeConverters.basicShapeTagToPathData(tag, tagName).orEmpty()
}

private fun appendPath(
    output: StringBuilder,
    d: String,
    fill: String,
    stroke: String?,
    strokeWidth: String?,
    strokeLineCap: String?,
    strokeLineJoin: String?,
    strokeMiterLimit: String?,
    fillRule: String?,
    fillAlpha: String?,
    strokeAlpha: String?,
    indent: String
) {
    output.appendLine("${indent}<path")
    output.appendLine("""${indent}    android:pathData="${escapeXml(d)}"""")

    if (fill != "@android:color/transparent") {
        output.appendLine("""${indent}    android:fillColor="$fill"""")
        if (fillAlpha != null) {
            output.appendLine("""${indent}    android:fillAlpha="$fillAlpha"""")
        }
    } else {
        output.appendLine("""${indent}    android:fillColor="@android:color/transparent"""")
    }

    when (fillRule?.trim()?.lowercase()) {
        "evenodd" -> output.appendLine("""${indent}    android:fillType="evenOdd"""")
        "nonzero" -> output.appendLine("""${indent}    android:fillType="nonZero"""")
    }

    if (stroke != null) {
        output.appendLine("""${indent}    android:strokeColor="$stroke"""")
        output.appendLine("""${indent}    android:strokeWidth="${strokeWidth ?: "1"}"""")
        if (strokeAlpha != null) {
            output.appendLine("""${indent}    android:strokeAlpha="$strokeAlpha"""")
        }

when (strokeLineCap?.trim()?.lowercase()) {
    "square" -> output.appendLine("""${indent}    android:strokeLineCap="square"""")
    "round" -> output.appendLine("""${indent}    android:strokeLineCap="round"""")
}

        when (strokeLineJoin?.trim()?.lowercase()) {
            "round" -> output.appendLine("""${indent}    android:strokeLineJoin="round"""")
            "bevel" -> output.appendLine("""${indent}    android:strokeLineJoin="bevel"""")
            // Omit null and "miter" because Android's default matches SVG's default.
        }

        normalizeNumber(strokeMiterLimit)?.takeIf { it != "4" && it != "4.0" }?.let { miterLimit ->
            output.appendLine("""${indent}    android:strokeMiterLimit="$miterLimit"""")
        }
    }

    output.appendLine("${indent}/>")
}


private fun normalizeNumber(value: String?): String? {
    val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val cleaned = raw.removeSuffix("px").trim()
    val number = cleaned.toFloatOrNull() ?: return null
    return java.lang.String.format(java.util.Locale.US, "%.3f", number)
        .trimEnd('0')
        .trimEnd('.')
}

    private fun getViewBox(svg: String): List<Float>? {
        return Regex("""viewBox=["']([^"']+)["']""")
            .find(svg)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.split(Regex("[,\\s]+"))
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size >= 4 }
    }

    private fun getNumberAttr(tag: String, name: String): Float? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.replace("px", "")
            ?.replace("dp", "")
            ?.trim()
            ?.toFloatOrNull()
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
