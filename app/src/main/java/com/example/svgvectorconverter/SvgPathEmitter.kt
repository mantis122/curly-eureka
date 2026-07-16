package com.example.svgvectorconverter

import org.w3c.dom.Element
import java.util.Locale

object SvgPathEmitter {
    private var activePaintOrderElementsApplied = 0
    val paintOrderElementsApplied: Int get() = activePaintOrderElementsApplied

    fun resetStats() {
        activePaintOrderElementsApplied = 0
    }

    private val flattenTransformStack = mutableListOf<AffineTransform>()
    private val forcedStrokeWidthStack = mutableListOf<String?>()

    internal fun pushFlattenTransform(matrix: AffineTransform) {
        flattenTransformStack.add(matrix)
    }

    internal fun popFlattenTransform() {
        if (flattenTransformStack.isNotEmpty()) {
            flattenTransformStack.removeAt(flattenTransformStack.lastIndex)
        }
    }

    internal fun currentFlattenTransform(): AffineTransform? {
        return flattenTransformStack.lastOrNull()
    }

    internal fun applyCurrentFlattenTransform(pathData: String): String {
        val matrix = currentFlattenTransform() ?: return pathData
        return SvgPathDataTransformer.applyAffineTransform(pathData, matrix) ?: pathData
    }

    internal fun pushForcedStrokeWidth(strokeWidth: String?) {
        forcedStrokeWidthStack.add(strokeWidth)
    }

    internal fun popForcedStrokeWidth() {
        if (forcedStrokeWidthStack.isNotEmpty()) {
            forcedStrokeWidthStack.removeAt(forcedStrokeWidthStack.lastIndex)
        }
    }

    private fun currentForcedStrokeWidth(): String? {
        return forcedStrokeWidthStack.lastOrNull()?.takeIf { it.isNotBlank() }
    }

    fun appendBasicShapePath(
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
            output = output,
            element = element,
            d = d,
            indent = indent,
            inheritedFill = inheritedFill,
            inheritedStroke = inheritedStroke,
            inheritedStrokeWidth = inheritedStrokeWidth,
            inheritedStrokeLineCap = inheritedStrokeLineCap,
            inheritedStrokeLineJoin = inheritedStrokeLineJoin,
            inheritedStrokeMiterLimit = inheritedStrokeMiterLimit,
            inheritedFillRule = inheritedFillRule,
            inheritedOpacity = inheritedOpacity,
            inheritedFillOpacity = inheritedFillOpacity,
            inheritedStrokeOpacity = inheritedStrokeOpacity,
            inheritedClipPath = inheritedClipPath,
            activeClipPathId = activeClipPathId,
            sourceTag = tagName
        )
    }

    fun appendElementPath(
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
            output = output,
            element = element,
            d = d,
            indent = indent,
            inheritedFill = inheritedFill,
            inheritedStroke = inheritedStroke,
            inheritedStrokeWidth = inheritedStrokeWidth,
            inheritedStrokeLineCap = inheritedStrokeLineCap,
            inheritedStrokeLineJoin = inheritedStrokeLineJoin,
            inheritedStrokeMiterLimit = inheritedStrokeMiterLimit,
            inheritedFillRule = inheritedFillRule,
            inheritedOpacity = inheritedOpacity,
            inheritedFillOpacity = inheritedFillOpacity,
            inheritedStrokeOpacity = inheritedStrokeOpacity,
            inheritedClipPath = inheritedClipPath,
            activeClipPathId = activeClipPathId,
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

        val currentColor = SvgPaintResolver.resolvedCurrentColor(
            SvgPaintResolver.styleValue(style, "color")
                ?: element.getAttribute("color").ifBlank { null }
        )
        val resolvedRawFill = SvgPaintResolver.resolveSpecialPaint(
            rawFill,
            currentColor,
            inheritedFill,
            inheritedStroke
        )
        val resolvedRawStroke = SvgPaintResolver.resolveSpecialPaint(
            rawStroke,
            currentColor,
            inheritedFill,
            inheritedStroke
        )

        val strokeWidth = currentForcedStrokeWidth()
            ?: SvgPaintResolver.styleValue(style, "stroke-width")
            ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

        val strokeLineCap = SvgPaintResolver.styleValue(style, "stroke-linecap")
            ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

        val strokeLineJoin = SvgPaintResolver.styleValue(style, "stroke-linejoin")
            ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

        val strokeMiterLimit = SvgPaintResolver.styleValue(style, "stroke-miterlimit")
            ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

        val fillRule = SvgPaintResolver.styleValue(style, "fill-rule")
            ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

        val fillOpacity = SvgPaintResolver.inheritedPaintOpacity(
            inheritedFillOpacity,
            SvgPaintResolver.styleValue(style, "fill-opacity")
                ?: element.getAttribute("fill-opacity").ifBlank { "" }
        )

        val strokeOpacity = SvgPaintResolver.inheritedPaintOpacity(
            inheritedStrokeOpacity,
            SvgPaintResolver.styleValue(style, "stroke-opacity")
                ?: element.getAttribute("stroke-opacity").ifBlank { "" }
        )

        val directClipPathValue = SvgPaintResolver.styleValue(style, "clip-path")
            ?: element.getAttribute("clip-path").ifBlank { "" }
        val directMaskValue = SvgPaintResolver.styleValue(style, "mask")
            ?: element.getAttribute("mask").ifBlank { "" }
        val clipPathValue = when {
            directClipPathValue.isNotBlank() -> directClipPathValue
            directMaskValue.isNotBlank() -> directMaskValue
            else -> inheritedClipPath ?: ""
        }

        val clipPathId = SvgTreeConverter.clipPathIdFromValue(clipPathValue)
        val hasClipPath = clipPathId != null &&
            clipPathId != activeClipPathId &&
            SvgTreeConverter.hasClipPathData(clipPathId)

        val fillAlpha = SvgPaintResolver.combineAlpha(inheritedOpacity, fillOpacity)
        val strokeAlpha = SvgPaintResolver.combineAlpha(inheritedOpacity, strokeOpacity)

        val fill = if (sourceTag == "line") {
            "@android:color/transparent"
        } else {
            SvgPaintResolver.safeFillColor(resolvedRawFill)
        }
        val stroke = SvgPaintResolver.safeStrokeColor(resolvedRawStroke)

        val pathTransform = element.getAttribute("transform")
            .ifBlank { SvgPaintResolver.styleValue(style, "transform") ?: "" }
        val transformOrigin = SvgTransformParser.parseTransformOrigin(
            element.getAttribute("transform-origin")
                .ifBlank { SvgPaintResolver.styleValue(style, "transform-origin") ?: "" }
        )
        val transforms = SvgTransformParser.parseTransformList(pathTransform)
        val localMatrix = SvgTransformParser.combineTransformListToMatrix(transforms, transformOrigin)
        val inheritedMatrix = currentFlattenTransform()
        val matrixTransform = when {
            inheritedMatrix != null && localMatrix != null -> inheritedMatrix.multiply(localMatrix)
            inheritedMatrix != null -> inheritedMatrix
            else -> localMatrix
        }
        val mustFlattenToPath = inheritedMatrix != null
        val combinedTransform = if (mustFlattenToPath) {
            null
        } else {
            matrixTransform?.toAndroidGroupTransform(
                preferredPivotX = transformOrigin?.x,
                preferredPivotY = transformOrigin?.y
            )
        }

        val hasMatrixLikeTransform = transforms.any {
            it is ParsedTransform.Matrix || it is ParsedTransform.SkewX || it is ParsedTransform.SkewY
        } || inheritedMatrix != null
        val flattenedPathData = if (matrixTransform != null && (mustFlattenToPath || combinedTransform == null)) {
            SvgPathDataTransformer.applyAffineTransform(d, matrixTransform)
        } else {
            null
        }

        if (matrixTransform != null && (mustFlattenToPath || combinedTransform == null)) {
            if (flattenedPathData != null) {
                SvgTransformParser.recordPathAppliedMatrixTransform()
            } else {
                SvgTransformParser.recordUnsupportedMatrixTransform()
            }
        } else if (hasMatrixLikeTransform && combinedTransform != null) {
            SvgTransformParser.recordPathAppliedMatrixTransform()
        }

        val transformedPathData = flattenedPathData ?: d
        val dashedPathData = dashedStrokePathData(
            element = element,
            style = style,
            sourceTag = sourceTag,
            pathData = transformedPathData,
            stroke = stroke
        )
        val effectivePathData = dashedPathData ?: transformedPathData
        val markerPathData = transformedPathData
        val effectiveTransform = if (flattenedPathData != null) null else combinedTransform
        val objectBounds = approximatePathBounds(effectivePathData)
        val fillGradient = if (sourceTag == "line") {
            null
        } else {
            SvgGradientResolver.resolveContextPaints(
                gradient = SvgPaintResolver.gradientForPaint(resolvedRawFill, objectBounds),
                currentColor = currentColor,
                contextFill = resolvedRawFill,
                contextStroke = resolvedRawStroke
            )
        }
        val strokeGradient = SvgGradientResolver.resolveContextPaints(
            gradient = SvgPaintResolver.gradientForPaint(resolvedRawStroke, objectBounds),
            currentColor = currentColor,
            contextFill = resolvedRawFill,
            contextStroke = resolvedRawStroke
        )
        val pathNeedsGroup = effectiveTransform != null || hasClipPath

        val directFilterValue = SvgPaintResolver.styleValue(style, "filter")
            ?: element.getAttribute("filter").ifBlank { "" }

        if (pathNeedsGroup) {
            var currentIndent = indent
            var openedGroupCount = 0

            if (hasClipPath) {
                output.appendLine("${currentIndent}<group>")
                SvgTreeConverter.appendClipPath(output, clipPathId, currentIndent + "    ")
                currentIndent += "    "
                openedGroupCount++
            }

            if (effectiveTransform != null) {
                SvgTransformParser.appendCombinedTransformGroupStart(output, effectiveTransform, currentIndent)
                currentIndent += "    "
                openedGroupCount++
            }

            if (directFilterValue.isNotBlank()) {
                output.appendLine("${currentIndent}<!-- filter ignored: ${escapeXml(directFilterValue)} -->")
            }
            if (sourceTag != null) {
                output.appendLine("${currentIndent}<!-- converted from <$sourceTag> -->")
            }
            appendPaintOrderedElement(
                output = output,
                element = element,
                style = style,
                fillPathData = transformedPathData,
                strokePathData = effectivePathData,
                markerPathData = markerPathData,
                fill = fill,
                stroke = stroke,
                strokeWidth = strokeWidth.ifBlank { null },
                strokeLineCap = strokeLineCap.ifBlank { null },
                strokeLineJoin = strokeLineJoin.ifBlank { null },
                strokeMiterLimit = strokeMiterLimit.ifBlank { null },
                fillRule = fillRule.ifBlank { null },
                fillAlpha = fillAlpha,
                strokeAlpha = strokeAlpha,
                fillGradient = fillGradient,
                strokeGradient = strokeGradient,
                indent = currentIndent
            )

            repeat(openedGroupCount) {
                currentIndent = currentIndent.dropLast(4)
                output.appendLine("${currentIndent}</group>")
            }
        } else {
            if (directFilterValue.isNotBlank()) {
                output.appendLine("${indent}<!-- filter ignored: ${escapeXml(directFilterValue)} -->")
            }
            if (sourceTag != null) {
                output.appendLine("${indent}<!-- converted from <$sourceTag> -->")
            }
            appendPaintOrderedElement(
                output = output,
                element = element,
                style = style,
                fillPathData = transformedPathData,
                strokePathData = effectivePathData,
                markerPathData = markerPathData,
                fill = fill,
                stroke = stroke,
                strokeWidth = strokeWidth.ifBlank { null },
                strokeLineCap = strokeLineCap.ifBlank { null },
                strokeLineJoin = strokeLineJoin.ifBlank { null },
                strokeMiterLimit = strokeMiterLimit.ifBlank { null },
                fillRule = fillRule.ifBlank { null },
                fillAlpha = fillAlpha,
                strokeAlpha = strokeAlpha,
                fillGradient = fillGradient,
                strokeGradient = strokeGradient,
                indent = indent
            )
        }

        output.appendLine()
    }




    private fun appendPaintOrderedElement(
        output: StringBuilder,
        element: Element,
        style: String?,
        fillPathData: String,
        strokePathData: String,
        markerPathData: String,
        fill: String,
        stroke: String?,
        strokeWidth: String?,
        strokeLineCap: String?,
        strokeLineJoin: String?,
        strokeMiterLimit: String?,
        fillRule: String?,
        fillAlpha: String?,
        strokeAlpha: String?,
        fillGradient: SvgVectorGradient?,
        strokeGradient: SvgVectorGradient?,
        indent: String
    ) {
        val paintOrder = SvgPaintResolver.resolvedPaintOrder(element)

        if (paintOrder == null) {
            appendPath(
                output = output,
                d = strokePathData,
                fill = fill,
                stroke = stroke,
                strokeWidth = strokeWidth,
                strokeLineCap = strokeLineCap,
                strokeLineJoin = strokeLineJoin,
                strokeMiterLimit = strokeMiterLimit,
                fillRule = fillRule,
                fillAlpha = fillAlpha,
                strokeAlpha = strokeAlpha,
                fillGradient = fillGradient,
                strokeGradient = strokeGradient,
                indent = indent
            )
            appendMarkersForPath(
                output = output,
                element = element,
                style = style,
                pathData = markerPathData,
                inheritedStroke = stroke,
                inheritedStrokeWidth = strokeWidth,
                inheritedFillAlpha = fillAlpha,
                inheritedStrokeAlpha = strokeAlpha,
                indent = indent
            )
            return
        }

        activePaintOrderElementsApplied++
        output.appendLine("${indent}<!-- paint-order applied: ${paintOrder.joinToString(" ") { it.name.lowercase(Locale.US) }} -->")

        paintOrder.forEach { layer ->
            when (layer) {
                SvgPaintResolver.PaintOrderLayer.FILL -> {
                    if (fill != "@android:color/transparent") {
                        appendPath(
                            output = output,
                            d = fillPathData,
                            fill = fill,
                            stroke = null,
                            strokeWidth = null,
                            strokeLineCap = null,
                            strokeLineJoin = null,
                            strokeMiterLimit = null,
                            fillRule = fillRule,
                            fillAlpha = fillAlpha,
                            strokeAlpha = null,
                            fillGradient = fillGradient,
                            strokeGradient = null,
                            indent = indent
                        )
                    }
                }

                SvgPaintResolver.PaintOrderLayer.STROKE -> {
                    if (!stroke.isNullOrBlank()) {
                        appendPath(
                            output = output,
                            d = strokePathData,
                            fill = "@android:color/transparent",
                            stroke = stroke,
                            strokeWidth = strokeWidth,
                            strokeLineCap = strokeLineCap,
                            strokeLineJoin = strokeLineJoin,
                            strokeMiterLimit = strokeMiterLimit,
                            fillRule = null,
                            fillAlpha = null,
                            strokeAlpha = strokeAlpha,
                            fillGradient = null,
                            strokeGradient = strokeGradient,
                            indent = indent
                        )
                    }
                }

                SvgPaintResolver.PaintOrderLayer.MARKERS -> {
                    appendMarkersForPath(
                        output = output,
                        element = element,
                        style = style,
                        pathData = markerPathData,
                        inheritedStroke = stroke,
                        inheritedStrokeWidth = strokeWidth,
                        inheritedFillAlpha = fillAlpha,
                        inheritedStrokeAlpha = strokeAlpha,
                        indent = indent
                    )
                }
            }
        }
    }

    internal fun appendRawPathForPatternTile(
        output: StringBuilder,
        d: String,
        fill: String?,
        stroke: String?,
        strokeWidth: String?,
        fillAlpha: String?,
        strokeAlpha: String?,
        indent: String
    ) {
        appendPath(
            output = output,
            d = d,
            fill = fill ?: "@android:color/transparent",
            stroke = stroke,
            strokeWidth = strokeWidth,
            strokeLineCap = null,
            strokeLineJoin = null,
            strokeMiterLimit = null,
            fillRule = null,
            fillAlpha = fillAlpha,
            strokeAlpha = strokeAlpha,
            fillGradient = null,
            strokeGradient = null,
            indent = indent
        )
    }

    fun appendFlatPathsFallback(
        output: StringBuilder,
        xml: String,
        indent: String
    ) {
        val drawableTags = Regex("""<(path|rect|circle|ellipse|line|polyline|polygon)\b[^>]*>""", RegexOption.IGNORE_CASE)

        drawableTags.findAll(xml).forEach { match ->
            val tag = match.value
            val tagName = match.groupValues[1].lowercase(Locale.US)
            val style = attr(tag, "style")

            val d = if (tagName == "path") {
                attr(tag, "d")?.trim().orEmpty()
            } else {
                SvgShapeConverters.basicShapeTagToPathData(tag, tagName)
            }

            if (d.isBlank()) return@forEach

            val rawFill = SvgPaintResolver.styleValue(style, "fill")
                ?: attr(tag, "fill")
                ?: "#000000"

            val rawStroke = SvgPaintResolver.styleValue(style, "stroke")
                ?: attr(tag, "stroke")

            val currentColor = SvgPaintResolver.resolvedCurrentColor(
                SvgPaintResolver.styleValue(style, "color")
                    ?: attr(tag, "color")
            )
            val resolvedRawFill = SvgPaintResolver.resolveSpecialPaint(
                rawFill,
                currentColor,
                null,
                null
            )
            val resolvedRawStroke = SvgPaintResolver.resolveSpecialPaint(
                rawStroke,
                currentColor,
                null,
                null
            )

            val strokeWidth = SvgPaintResolver.styleValue(style, "stroke-width")
                ?: attr(tag, "stroke-width")

            val strokeLineCap = SvgPaintResolver.styleValue(style, "stroke-linecap")
                ?: attr(tag, "stroke-linecap")

            val strokeLineJoin = SvgPaintResolver.styleValue(style, "stroke-linejoin")
                ?: attr(tag, "stroke-linejoin")

            val strokeMiterLimit = SvgPaintResolver.styleValue(style, "stroke-miterlimit")
                ?: attr(tag, "stroke-miterlimit")

            val opacity = SvgPaintResolver.styleValue(style, "opacity")
                ?: attr(tag, "opacity")

            val fillOpacity = SvgPaintResolver.styleValue(style, "fill-opacity")
                ?: attr(tag, "fill-opacity")

            val strokeOpacity = SvgPaintResolver.styleValue(style, "stroke-opacity")
                ?: attr(tag, "stroke-opacity")

            val fillRule = SvgPaintResolver.styleValue(style, "fill-rule")
                ?: attr(tag, "fill-rule")

            val filterValue = SvgPaintResolver.styleValue(style, "filter")
                ?: attr(tag, "filter")
                ?: ""

            val directClipPathValue = SvgPaintResolver.styleValue(style, "clip-path")
                ?: attr(tag, "clip-path")
                ?: ""
            val directMaskValue = SvgPaintResolver.styleValue(style, "mask")
                ?: attr(tag, "mask")
                ?: ""
            val clipPathValue = when {
                directClipPathValue.isNotBlank() -> directClipPathValue
                directMaskValue.isNotBlank() -> directMaskValue
                else -> ""
            }
            val clipPathId = SvgTreeConverter.clipPathIdFromValue(clipPathValue)
            val hasClipPath = clipPathId != null && SvgTreeConverter.hasClipPathData(clipPathId)

            val transform = attr(tag, "transform")
                ?: SvgPaintResolver.styleValue(style, "transform")
                ?: ""

            val transformOrigin = SvgTransformParser.parseTransformOrigin(
                attr(tag, "transform-origin")
                    ?: SvgPaintResolver.styleValue(style, "transform-origin")
                    ?: ""
            )

            val transforms = SvgTransformParser.parseTransformList(transform)
            val localMatrix = SvgTransformParser.combineTransformListToMatrix(transforms, transformOrigin)
            val inheritedMatrix = currentFlattenTransform()
            val matrixTransform = when {
                inheritedMatrix != null && localMatrix != null -> inheritedMatrix.multiply(localMatrix)
                inheritedMatrix != null -> inheritedMatrix
                else -> localMatrix
            }
            val mustFlattenToPath = inheritedMatrix != null
            val combinedTransform = if (mustFlattenToPath) {
                null
            } else {
                matrixTransform?.toAndroidGroupTransform(
                    preferredPivotX = transformOrigin?.x,
                    preferredPivotY = transformOrigin?.y
                )
            }
            val hasMatrixLikeTransform = transforms.any {
                it is ParsedTransform.Matrix || it is ParsedTransform.SkewX || it is ParsedTransform.SkewY
            } || inheritedMatrix != null
            val flattenedPathData = if (matrixTransform != null && (mustFlattenToPath || combinedTransform == null)) {
                SvgPathDataTransformer.applyAffineTransform(d, matrixTransform)
            } else {
                null
            }

            if (matrixTransform != null && (mustFlattenToPath || combinedTransform == null)) {
                if (flattenedPathData != null) {
                    SvgTransformParser.recordPathAppliedMatrixTransform()
                } else {
                    SvgTransformParser.recordUnsupportedMatrixTransform()
                }
            } else if (hasMatrixLikeTransform && combinedTransform != null) {
                SvgTransformParser.recordPathAppliedMatrixTransform()
            }

            val effectivePathData = flattenedPathData ?: d
            val effectiveTransform = if (flattenedPathData != null) null else combinedTransform

            val objectBounds = approximatePathBounds(effectivePathData)
            val fillGradient = if (tagName == "line") {
                null
            } else {
                SvgGradientResolver.resolveContextPaints(
                    gradient = SvgPaintResolver.gradientForPaint(resolvedRawFill, objectBounds),
                    currentColor = currentColor,
                    contextFill = resolvedRawFill,
                    contextStroke = resolvedRawStroke
                )
            }
            val strokeGradient = SvgGradientResolver.resolveContextPaints(
                gradient = SvgPaintResolver.gradientForPaint(resolvedRawStroke, objectBounds),
                currentColor = currentColor,
                contextFill = resolvedRawFill,
                contextStroke = resolvedRawStroke
            )

            val fillColor = if (tagName == "line") {
                "@android:color/transparent"
            } else {
                SvgPaintResolver.safeFillColor(resolvedRawFill)
            }

            var currentIndent = indent
            var openedGroupCount = 0

            if (hasClipPath) {
                output.appendLine("${currentIndent}<group>")
                SvgTreeConverter.appendClipPath(output, clipPathId, currentIndent + "    ")
                currentIndent += "    "
                openedGroupCount++
            }

            if (effectiveTransform != null) {
                SvgTransformParser.appendCombinedTransformGroupStart(output, effectiveTransform, currentIndent)
                currentIndent += "    "
                openedGroupCount++
            }

            if (filterValue.isNotBlank()) {
                output.appendLine("${currentIndent}<!-- filter ignored: ${escapeXml(filterValue)} -->")
            }
            if (tagName != "path") {
                output.appendLine("${currentIndent}<!-- converted from <$tagName> -->")
            }

            appendPath(
                output = output,
                d = effectivePathData,
                fill = fillColor,
                stroke = SvgPaintResolver.safeStrokeColor(resolvedRawStroke),
                strokeWidth = strokeWidth,
                strokeLineCap = strokeLineCap,
                strokeLineJoin = strokeLineJoin,
                strokeMiterLimit = strokeMiterLimit,
                fillRule = fillRule,
                fillAlpha = SvgPaintResolver.combineAlpha(opacity, fillOpacity),
                strokeAlpha = SvgPaintResolver.combineAlpha(opacity, strokeOpacity),
                fillGradient = fillGradient,
                strokeGradient = strokeGradient,
                indent = currentIndent
            )

            repeat(openedGroupCount) {
                currentIndent = currentIndent.dropLast(4)
                output.appendLine("${currentIndent}</group>")
            }

            output.appendLine()
        }
    }

    fun appendPath(
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
        fillGradient: SvgVectorGradient? = null,
        strokeGradient: SvgVectorGradient? = null,
        indent: String
    ) {
        val normalizedStroke = stroke?.trim()?.takeIf { it.isNotBlank() }
        val hasFillGradient = fillGradient != null && fill != "@android:color/transparent"
        val hasStrokeGradient = strokeGradient != null && normalizedStroke != null
        val needsChildren = hasFillGradient || hasStrokeGradient

        output.appendLine("${indent}<path")
        output.appendLine("""${indent}    android:pathData="${escapeXml(d)}"""")

        if (!hasFillGradient) {
            if (fill != "@android:color/transparent") {
                output.appendLine("""${indent}    android:fillColor="$fill"""")
                if (fillAlpha != null) {
                    output.appendLine("""${indent}    android:fillAlpha="$fillAlpha"""")
                }
            } else {
                output.appendLine("""${indent}    android:fillColor="@android:color/transparent"""")
            }
        } else if (fillAlpha != null) {
            output.appendLine("""${indent}    android:fillAlpha="$fillAlpha"""")
        }

        normalizeFillRuleToVectorFillType(fillRule)?.let { fillType ->
            output.appendLine("""${indent}    android:fillType="$fillType"""")
        }

        if (!hasStrokeGradient && normalizedStroke != null) {
            output.appendLine("""${indent}    android:strokeColor="$normalizedStroke"""")
        }

        if (normalizedStroke != null) {
            output.appendLine("""${indent}    android:strokeWidth="${normalizeNumber(strokeWidth) ?: "1"}"""")
            if (strokeAlpha != null) {
                output.appendLine("""${indent}    android:strokeAlpha="$strokeAlpha"""")
            }

            when (strokeLineCap?.trim()?.lowercase(Locale.US)) {
                "square" -> output.appendLine("""${indent}    android:strokeLineCap="square"""")
                "round" -> output.appendLine("""${indent}    android:strokeLineCap="round"""")
            }

            when (strokeLineJoin?.trim()?.lowercase(Locale.US)) {
                "miter" -> output.appendLine("""${indent}    android:strokeLineJoin="miter"""")
                "round" -> output.appendLine("""${indent}    android:strokeLineJoin="round"""")
                "bevel" -> output.appendLine("""${indent}    android:strokeLineJoin="bevel"""")
            }

            normalizeNumber(strokeMiterLimit)
                ?.takeIf { it != "4" && it != "4.0" }
                ?.let { miterLimit ->
                    output.appendLine("""${indent}    android:strokeMiterLimit="$miterLimit"""")
                }
        }

        if (!needsChildren) {
            output.appendLine("${indent}/>")
            return
        }

        output.appendLine("${indent}>")
        if (hasFillGradient && fillGradient != null) {
            SvgGradientResolver.emitGradientAttr(
                output = output,
                gradient = fillGradient,
                attrName = "android:fillColor",
                indent = indent + "    "
            )
        }
        if (hasStrokeGradient && strokeGradient != null) {
            SvgGradientResolver.emitGradientAttr(
                output = output,
                gradient = strokeGradient,
                attrName = "android:strokeColor",
                indent = indent + "    "
            )
        }
        output.appendLine("${indent}</path>")
    }

    fun basicShapeToPathData(element: Element, tagName: String): String? {
        return SvgShapeConverters.basicShapeToPathData(element, tagName)
    }

    fun floatAttr(element: Element, name: String): Float? {
        return element.getAttribute(name)
            .replace("px", "")
            .replace("dp", "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
    }

    fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }


    private data class DashPoint(val x: Float, val y: Float, val startsNewSubpath: Boolean = false)

    private sealed class DashArrayParseResult {
        data object None : DashArrayParseResult()
        data class Valid(val pattern: List<Float>, val duplicatedOddList: Boolean) : DashArrayParseResult()
        data object Invalid : DashArrayParseResult()
    }

    private fun dashedStrokePathData(
        element: Element,
        style: String?,
        sourceTag: String?,
        pathData: String,
        stroke: String?
    ): String? {
        if (sourceTag !in setOf("line", "polyline", "polygon", "rect", "circle", "ellipse", "path")) return null
        if (stroke.isNullOrBlank() || stroke.equals("none", ignoreCase = true)) return null

        val dashArrayValue = inheritedStyleOrAttribute(element, style, "stroke-dasharray") ?: return null
        when (val parsed = parseDashArrayStrict(dashArrayValue)) {
            DashArrayParseResult.None -> return null
            DashArrayParseResult.Invalid -> {
                SvgTreeConverter.recordDashedStrokeInvalid(solidFallback = true)
                return null
            }
            is DashArrayParseResult.Valid -> {
                if (parsed.duplicatedOddList) SvgTreeConverter.recordOddDashListDuplicated()

                val dashOffsetValue = inheritedStyleOrAttribute(element, style, "stroke-dashoffset")
                val dashOffset = when (val parsedOffset = parseSingleLengthStrict(dashOffsetValue ?: "0")) {
                    null -> {
                        SvgTreeConverter.recordInvalidDashOffsetFallback()
                        0f
                    }
                    else -> parsedOffset
                }

                val dashPoints = dashApproximationPoints(element, sourceTag, pathData)
                if (dashPoints.size < 2) {
                    SvgTreeConverter.recordDashedStroke(didApproximate = false)
                    return null
                }

                val dashed = buildDashedPath(dashPoints, parsed.pattern, dashOffset)
                if (dashed.isBlank()) {
                    SvgTreeConverter.recordDashedStroke(didApproximate = false)
                    return null
                }

                SvgTreeConverter.recordDashedStroke(didApproximate = true)
                return dashed
            }
        }
    }

    private fun inheritedStyleOrAttribute(element: Element, directStyle: String?, property: String): String? {
        SvgPaintResolver.styleValue(directStyle, property)?.let { return it }
        element.getAttribute(property).trim().takeIf { it.isNotBlank() }?.let { return it }

        var parent = element.parentNode
        while (parent is Element) {
            val parentStyle = parent.getAttribute("style").ifBlank { null }
            SvgPaintResolver.styleValue(parentStyle, property)?.let { return it }
            parent.getAttribute(property).trim().takeIf { it.isNotBlank() }?.let { return it }
            parent = parent.parentNode
        }
        return null
    }

    private fun parseDashArrayStrict(value: String?): DashArrayParseResult {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("none", ignoreCase = true)) return DashArrayParseResult.None

        val tokens = raw.replace(",", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return DashArrayParseResult.Invalid

        val values = mutableListOf<Float>()
        for (token in tokens) {
            val number = parseSupportedLengthToken(token) ?: return DashArrayParseResult.Invalid
            if (!number.isFinite() || number < 0f) return DashArrayParseResult.Invalid
            values += number
        }

        if (values.all { it == 0f }) return DashArrayParseResult.None
        val odd = values.size % 2 != 0
        return DashArrayParseResult.Valid(if (odd) values + values else values, odd)
    }

    private fun parseSingleLengthStrict(value: String?): Float? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return 0f
        if (raw.contains(',') || raw.split(Regex("\\s+")).size != 1) return null
        return parseSupportedLengthToken(raw)?.takeIf { it.isFinite() }
    }

    private fun parseSupportedLengthToken(token: String): Float? {
        val trimmed = token.trim()
        if (trimmed.endsWith("%")) return null
        val numeric = when {
            trimmed.endsWith("px", ignoreCase = true) -> trimmed.dropLast(2)
            trimmed.endsWith("dp", ignoreCase = true) -> trimmed.dropLast(2)
            else -> trimmed
        }
        return numeric.toFloatOrNull()
    }

    private fun dashApproximationPoints(element: Element, sourceTag: String?, pathData: String): List<DashPoint> {
        return when (sourceTag) {
            "line", "polyline" -> extractLinePoints(pathData)
            "polygon" -> closeDashPolygon(extractLinePoints(pathData))
            "rect" -> rectDashPoints(element) ?: closeDashPolygon(extractLinePoints(pathData))
            "circle" -> circleDashPoints(element) ?: closeDashPolygon(extractLinePoints(pathData))
            "ellipse" -> ellipseDashPoints(element) ?: closeDashPolygon(extractLinePoints(pathData))
            "path" -> straightPathDashPoints(pathData) ?: emptyList()
            else -> emptyList()
        }
    }

    private fun rectDashPoints(element: Element): List<DashPoint>? {
        val x = floatAttr(element, "x") ?: 0f
        val y = floatAttr(element, "y") ?: 0f
        val width = floatAttr(element, "width") ?: return null
        val height = floatAttr(element, "height") ?: return null
        if (width <= 0f || height <= 0f) return null

        val rawRx = floatAttr(element, "rx")
        val rawRy = floatAttr(element, "ry")
        val rx = when {
            rawRx != null -> rawRx
            rawRy != null -> rawRy
            else -> 0f
        }.coerceAtLeast(0f).coerceAtMost(width / 2f)
        val ry = when {
            rawRy != null -> rawRy
            rawRx != null -> rawRx
            else -> 0f
        }.coerceAtLeast(0f).coerceAtMost(height / 2f)

        if (rx <= 0.0001f || ry <= 0.0001f) {
            return listOf(
                DashPoint(x, y),
                DashPoint(x + width, y),
                DashPoint(x + width, y + height),
                DashPoint(x, y + height),
                DashPoint(x, y)
            )
        }

        val points = mutableListOf<DashPoint>()
        points.add(DashPoint(x + rx, y))
        points.add(DashPoint(x + width - rx, y))
        appendArcPoints(points, x + width - rx, y + ry, rx, ry, -90f, 0f, includeFirst = false)
        points.add(DashPoint(x + width, y + height - ry))
        appendArcPoints(points, x + width - rx, y + height - ry, rx, ry, 0f, 90f, includeFirst = false)
        points.add(DashPoint(x + rx, y + height))
        appendArcPoints(points, x + rx, y + height - ry, rx, ry, 90f, 180f, includeFirst = false)
        points.add(DashPoint(x, y + ry))
        appendArcPoints(points, x + rx, y + ry, rx, ry, 180f, 270f, includeFirst = false)
        points.add(points.first())
        return points
    }

    private fun circleDashPoints(element: Element): List<DashPoint>? {
        val cx = floatAttr(element, "cx") ?: 0f
        val cy = floatAttr(element, "cy") ?: 0f
        val r = floatAttr(element, "r") ?: return null
        if (r <= 0f) return null
        return ellipsePerimeterPoints(cx, cy, r, r)
    }

    private fun ellipseDashPoints(element: Element): List<DashPoint>? {
        val cx = floatAttr(element, "cx") ?: 0f
        val cy = floatAttr(element, "cy") ?: 0f
        val rx = floatAttr(element, "rx") ?: return null
        val ry = floatAttr(element, "ry") ?: return null
        if (rx <= 0f || ry <= 0f) return null
        return ellipsePerimeterPoints(cx, cy, rx, ry)
    }

    private fun ellipsePerimeterPoints(cx: Float, cy: Float, rx: Float, ry: Float): List<DashPoint> {
        val points = mutableListOf<DashPoint>()
        val steps = 96
        for (i in 0..steps) {
            val angle = (Math.PI * 2.0 * i.toDouble()) / steps.toDouble()
            points.add(
                DashPoint(
                    x = cx + (kotlin.math.cos(angle) * rx).toFloat(),
                    y = cy + (kotlin.math.sin(angle) * ry).toFloat()
                )
            )
        }
        return points
    }

    private fun appendArcPoints(
        points: MutableList<DashPoint>,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        startDegrees: Float,
        endDegrees: Float,
        includeFirst: Boolean
    ) {
        val steps = 12
        val startIndex = if (includeFirst) 0 else 1
        for (i in startIndex..steps) {
            val t = i.toFloat() / steps.toFloat()
            val degrees = startDegrees + (endDegrees - startDegrees) * t
            val radians = Math.toRadians(degrees.toDouble())
            points.add(
                DashPoint(
                    x = cx + (kotlin.math.cos(radians) * rx).toFloat(),
                    y = cy + (kotlin.math.sin(radians) * ry).toFloat()
                )
            )
        }
    }

    private fun extractLinePoints(pathData: String): List<DashPoint> {
        val points = mutableListOf<DashPoint>()
        val commandRegex = Regex("""([MmLl])\s*([-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)[,\s]+([-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)""")
        commandRegex.findAll(pathData).forEach { match ->
            val x = match.groupValues.getOrNull(2)?.toFloatOrNull()
            val y = match.groupValues.getOrNull(3)?.toFloatOrNull()
            if (x != null && y != null) points.add(DashPoint(x, y))
        }
        return points
    }

    private fun closeDashPolygon(points: List<DashPoint>): List<DashPoint> {
        if (points.size < 2) return points
        val first = points.first()
        val last = points.last()
        val alreadyClosed = kotlin.math.abs(first.x - last.x) < 0.0001f &&
            kotlin.math.abs(first.y - last.y) < 0.0001f
        return if (alreadyClosed) points else points + first
    }

    private fun straightPathDashPoints(pathData: String): List<DashPoint>? {
        val tokens = tokenizePathData(pathData)
        if (tokens.isEmpty()) return null

        val points = mutableListOf<DashPoint>()
        var i = 0
        var command: Char? = null
        var currentX = 0f
        var currentY = 0f
        var subpathStartX = 0f
        var subpathStartY = 0f
        var hasCurrentPoint = false

        fun isCommandToken(index: Int): Boolean = index < tokens.size && tokens[index].length == 1 && tokens[index][0].isLetter()
        fun readFloat(): Float? = tokens.getOrNull(i)?.toFloatOrNull()?.also { i++ }
        fun addPoint(x: Float, y: Float, startsNewSubpath: Boolean = false) {
            points.add(DashPoint(x, y, startsNewSubpath))
            currentX = x
            currentY = y
            hasCurrentPoint = true
        }

        while (i < tokens.size) {
            if (isCommandToken(i)) {
                command = tokens[i][0]
                i++
            } else if (command == null) {
                return null
            }

            when (command) {
                'M', 'm' -> {
                    val relative = command == 'm'
                    val x = readFloat() ?: return null
                    val y = readFloat() ?: return null
                    val moveX = if (relative) currentX + x else x
                    val moveY = if (relative) currentY + y else y
                    addPoint(moveX, moveY, startsNewSubpath = points.isNotEmpty())
                    subpathStartX = moveX
                    subpathStartY = moveY

                    // Additional coordinate pairs after M/m are implicit L/l commands.
                    command = if (relative) 'l' else 'L'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val lx = readFloat() ?: return null
                        val ly = readFloat() ?: return null
                        addPoint(if (relative) currentX + lx else lx, if (relative) currentY + ly else ly)
                    }
                }
                'L', 'l' -> {
                    if (!hasCurrentPoint) return null
                    val relative = command == 'l'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return null
                        val y = readFloat() ?: return null
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'H', 'h' -> {
                    if (!hasCurrentPoint) return null
                    val relative = command == 'h'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return null
                        addPoint(if (relative) currentX + x else x, currentY)
                    }
                }
                'V', 'v' -> {
                    if (!hasCurrentPoint) return null
                    val relative = command == 'v'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val y = readFloat() ?: return null
                        addPoint(currentX, if (relative) currentY + y else y)
                    }
                }
                'Z', 'z' -> {
                    if (!hasCurrentPoint) return null
                    addPoint(subpathStartX, subpathStartY)
                    command = null
                }
                else -> return null
            }
        }

        return points.takeIf { it.size >= 2 }
    }

    private fun tokenizePathData(pathData: String): List<String> {
        val tokenRegex = Regex("""[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        return tokenRegex.findAll(pathData).map { it.value }.toList()
    }

    private fun buildDashedPath(points: List<DashPoint>, pattern: List<Float>, dashOffset: Float): String {
        if (points.size < 2 || pattern.isEmpty()) return ""

        val totalPatternLength = pattern.sum()
        if (totalPatternLength <= 0f) return ""

        var patternIndex = 0
        var distanceIntoPattern = positiveModulo(dashOffset, totalPatternLength)

        var guard = 0
        while ((pattern[patternIndex] <= 0.000001f || distanceIntoPattern >= pattern[patternIndex]) && guard < pattern.size * 2) {
            if (pattern[patternIndex] > 0.000001f) distanceIntoPattern -= pattern[patternIndex]
            patternIndex = (patternIndex + 1) % pattern.size
            guard++
        }

        var drawDash = patternIndex % 2 == 0
        var remainingInPattern = pattern[patternIndex] - distanceIntoPattern
        val segments = mutableListOf<String>()

        for (i in 0 until points.lastIndex) {
            val from = points[i]
            val to = points[i + 1]
            if (to.startsNewSubpath) continue
            val dx = to.x - from.x
            val dy = to.y - from.y
            val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (length <= 0.0001f) continue

            var walked = 0f
            while (walked < length - 0.0001f) {
                val step = minOf(remainingInPattern, length - walked)
                if (drawDash && step > 0.0001f) {
                    val startRatio = walked / length
                    val endRatio = (walked + step) / length
                    val x1 = from.x + dx * startRatio
                    val y1 = from.y + dy * startRatio
                    val x2 = from.x + dx * endRatio
                    val y2 = from.y + dy * endRatio
                    segments.add("M ${formatDashNumber(x1)},${formatDashNumber(y1)} L ${formatDashNumber(x2)},${formatDashNumber(y2)}")
                }

                walked += step
                remainingInPattern -= step
                if (remainingInPattern <= 0.0001f) {
                    var advanceGuard = 0
                    do {
                        patternIndex = (patternIndex + 1) % pattern.size
                        drawDash = patternIndex % 2 == 0
                        remainingInPattern = pattern[patternIndex]
                        advanceGuard++
                    } while (remainingInPattern <= 0.000001f && advanceGuard < pattern.size * 2)
                }
            }
        }

        return segments.joinToString(" ")
    }

    private fun positiveModulo(value: Float, modulo: Float): Float {
        if (modulo <= 0f) return 0f
        val result = value % modulo
        return if (result < 0f) result + modulo else result
    }

    private fun formatDashNumber(value: Float): String {
        return java.lang.String.format(Locale.US, "%.3f", value)
            .trimEnd('0')
            .trimEnd('.')
    }


    private data class MarkerPlacement(
        val x: Float,
        val y: Float,
        val angle: Float,
        val isStartMarker: Boolean = false
    )

    private fun appendMarkersForPath(
        output: StringBuilder,
        element: Element,
        style: String?,
        pathData: String,
        inheritedStroke: String?,
        inheritedStrokeWidth: String?,
        inheritedFillAlpha: String?,
        inheritedStrokeAlpha: String?,
        indent: String
    ) {
        val markerAll = markerIdFromValue(
            SvgPaintResolver.styleValue(style, "marker")
                ?: element.getAttribute("marker").ifBlank { "" }
        )
        val markerStart = markerIdFromValue(
            SvgPaintResolver.styleValue(style, "marker-start")
                ?: element.getAttribute("marker-start").ifBlank { "" }
        ) ?: markerAll
        val markerMid = markerIdFromValue(
            SvgPaintResolver.styleValue(style, "marker-mid")
                ?: element.getAttribute("marker-mid").ifBlank { "" }
        ) ?: markerAll
        val markerEnd = markerIdFromValue(
            SvgPaintResolver.styleValue(style, "marker-end")
                ?: element.getAttribute("marker-end").ifBlank { "" }
        ) ?: markerAll

        if (markerStart == null && markerMid == null && markerEnd == null) return

        val points = extractMarkerPoints(pathData)
        if (points.size < 2) return

        markerStart?.let { id ->
            appendMarkerDefinitionAt(
                output = output,
                markerId = id,
                placement = MarkerPlacement(
                    x = points.first().first,
                    y = points.first().second,
                    angle = angleBetween(points[0], points[1]),
                    isStartMarker = true
                ),
                inheritedStroke = inheritedStroke,
                inheritedStrokeWidth = inheritedStrokeWidth,
                inheritedFillAlpha = inheritedFillAlpha,
                inheritedStrokeAlpha = inheritedStrokeAlpha,
                indent = indent
            )
        }

        if (markerMid != null && points.size > 2) {
            for (i in 1 until points.lastIndex) {
                val previousAngle = angleBetween(points[i - 1], points[i])
                val nextAngle = angleBetween(points[i], points[i + 1])
                appendMarkerDefinitionAt(
                    output = output,
                    markerId = markerMid,
                    placement = MarkerPlacement(
                        x = points[i].first,
                        y = points[i].second,
                        angle = averageAngle(previousAngle, nextAngle)
                    ),
                    inheritedStroke = inheritedStroke,
                    inheritedStrokeWidth = inheritedStrokeWidth,
                    inheritedFillAlpha = inheritedFillAlpha,
                    inheritedStrokeAlpha = inheritedStrokeAlpha,
                    indent = indent
                )
            }
        }

        markerEnd?.let { id ->
            appendMarkerDefinitionAt(
                output = output,
                markerId = id,
                placement = MarkerPlacement(
                    x = points.last().first,
                    y = points.last().second,
                    angle = angleBetween(points[points.lastIndex - 1], points.last())
                ),
                inheritedStroke = inheritedStroke,
                inheritedStrokeWidth = inheritedStrokeWidth,
                inheritedFillAlpha = inheritedFillAlpha,
                inheritedStrokeAlpha = inheritedStrokeAlpha,
                indent = indent
            )
        }
    }

    private fun appendMarkerDefinitionAt(
        output: StringBuilder,
        markerId: String,
        placement: MarkerPlacement,
        inheritedStroke: String?,
        inheritedStrokeWidth: String?,
        inheritedFillAlpha: String?,
        inheritedStrokeAlpha: String?,
        indent: String
    ) {
        val marker = SvgTreeConverter.markerDefinition(markerId) ?: return
        val strokeScale = if (marker.markerUnits.equals("strokeWidth", ignoreCase = true)) {
            inheritedStrokeWidth?.toFloatOrNull() ?: 1f
        } else {
            1f
        }
        val scaleX = marker.markerWidth / marker.viewBoxWidth.coerceAtLeast(0.001f) * strokeScale
        val scaleY = marker.markerHeight / marker.viewBoxHeight.coerceAtLeast(0.001f) * strokeScale
        val rotation = marker.orient.trim().let { orient ->
            when {
                orient.equals("auto-start-reverse", ignoreCase = true) -> {
                    if (placement.isStartMarker) placement.angle + 180f else placement.angle
                }
                orient.equals("auto", ignoreCase = true) -> placement.angle
                else -> orient.removeSuffix("deg").trim().toFloatOrNull() ?: 0f
            }
        }
        val transform = markerTransform(
            placement.x,
            placement.y,
            rotation,
            scaleX,
            scaleY,
            marker.refX,
            marker.refY
        )

        output.appendLine("${indent}<!-- approximated marker #${escapeXml(marker.id)} -->")
        marker.paths.forEach { markerPath ->
            val transformedPathData = SvgPathDataTransformer.applyAffineTransform(markerPath.pathData, transform)
                ?: markerPath.pathData
            appendPath(
                output = output,
                d = transformedPathData,
                fill = markerPaintColor(markerPath.fill, inheritedStroke, "#000000"),
                stroke = markerPaintColor(markerPath.stroke, inheritedStroke, "").takeIf { it.isNotBlank() },
                strokeWidth = markerPath.strokeWidth,
                strokeLineCap = null,
                strokeLineJoin = null,
                strokeMiterLimit = null,
                fillRule = null,
                fillAlpha = SvgPaintResolver.combineAlpha(inheritedFillAlpha, markerPath.fillOpacity),
                strokeAlpha = SvgPaintResolver.combineAlpha(inheritedStrokeAlpha, markerPath.strokeOpacity),
                fillGradient = null,
                strokeGradient = null,
                indent = indent
            )
            SvgTreeConverter.recordAppliedMarker()
        }
    }

    private fun markerPaintColor(value: String?, inheritedStroke: String?, fallback: String): String {
        val raw = value?.trim().orEmpty()
        val resolved = when {
            raw.equals("context-stroke", ignoreCase = true) -> inheritedStroke.orEmpty()
            raw.equals("context-fill", ignoreCase = true) -> fallback
            raw.isNotBlank() -> raw
            else -> fallback
        }
        return if (fallback.isBlank()) SvgPaintResolver.safeStrokeColor(resolved).orEmpty() else SvgPaintResolver.safeFillColor(resolved)
    }

    private fun markerTransform(
        x: Float,
        y: Float,
        rotationDegrees: Float,
        scaleX: Float,
        scaleY: Float,
        refX: Float,
        refY: Float
    ): AffineTransform {
        val radians = Math.toRadians(rotationDegrees.toDouble())
        val cosValue = kotlin.math.cos(radians).toFloat()
        val sinValue = kotlin.math.sin(radians).toFloat()
        val translateToPoint = AffineTransform(e = x, f = y)
        val rotate = AffineTransform(a = cosValue, b = sinValue, c = -sinValue, d = cosValue)
        val scale = AffineTransform(a = scaleX, d = scaleY)
        val translateRef = AffineTransform(e = -refX, f = -refY)
        return translateToPoint.multiply(rotate).multiply(scale).multiply(translateRef)
    }

    private fun markerIdFromValue(value: String?): String? {
        val v = value?.trim().orEmpty()
        if (v.isBlank() || v.equals("none", ignoreCase = true)) return null
        return Regex("""url\(\s*#([^)'"\s]+)\s*\)""")
            .find(v)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractMarkerPoints(pathData: String): List<Pair<Float, Float>> {
        val tokens = tokenizePathData(pathData)
        if (tokens.isEmpty()) return emptyList()

        val points = mutableListOf<Pair<Float, Float>>()
        var i = 0
        var command: Char? = null
        var currentX = 0f
        var currentY = 0f
        var subpathStartX = 0f
        var subpathStartY = 0f
        var hasCurrentPoint = false

        fun isCommandToken(index: Int): Boolean =
            index < tokens.size && tokens[index].length == 1 && tokens[index][0].isLetter()

        fun readFloat(): Float? = tokens.getOrNull(i)?.toFloatOrNull()?.also { i++ }

        fun addPoint(x: Float, y: Float) {
            points.add(Pair(x, y))
            currentX = x
            currentY = y
            hasCurrentPoint = true
        }

        while (i < tokens.size) {
            if (isCommandToken(i)) {
                command = tokens[i][0]
                i++
            } else if (command == null) {
                return points
            }

            when (command) {
                'M', 'm' -> {
                    val relative = command == 'm'
                    val x = readFloat() ?: return points
                    val y = readFloat() ?: return points
                    val moveX = if (relative) currentX + x else x
                    val moveY = if (relative) currentY + y else y
                    addPoint(moveX, moveY)
                    subpathStartX = moveX
                    subpathStartY = moveY
                    command = if (relative) 'l' else 'L'

                    while (i < tokens.size && !isCommandToken(i)) {
                        val lx = readFloat() ?: return points
                        val ly = readFloat() ?: return points
                        addPoint(if (relative) currentX + lx else lx, if (relative) currentY + ly else ly)
                    }
                }
                'L', 'l' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 'l'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return points
                        val y = readFloat() ?: return points
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'H', 'h' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 'h'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return points
                        addPoint(if (relative) currentX + x else x, currentY)
                    }
                }
                'V', 'v' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 'v'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val y = readFloat() ?: return points
                        addPoint(currentX, if (relative) currentY + y else y)
                    }
                }
                'C', 'c' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 'c'
                    while (i < tokens.size && !isCommandToken(i)) {
                        repeat(4) { readFloat() ?: return points }
                        val x = readFloat() ?: return points
                        val y = readFloat() ?: return points
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'S', 's', 'Q', 'q' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 's' || command == 'q'
                    while (i < tokens.size && !isCommandToken(i)) {
                        repeat(2) { readFloat() ?: return points }
                        val x = readFloat() ?: return points
                        val y = readFloat() ?: return points
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'T', 't' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 't'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return points
                        val y = readFloat() ?: return points
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'A', 'a' -> {
                    if (!hasCurrentPoint) return points
                    val relative = command == 'a'
                    while (i < tokens.size && !isCommandToken(i)) {
                        repeat(5) { readFloat() ?: return points }
                        val x = readFloat() ?: return points
                        val y = readFloat() ?: return points
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'Z', 'z' -> {
                    if (!hasCurrentPoint) return points
                    addPoint(subpathStartX, subpathStartY)
                    command = null
                }
                else -> return points
            }
        }

        return points
    }

    private fun angleBetween(from: Pair<Float, Float>, to: Pair<Float, Float>): Float {
        return Math.toDegrees(kotlin.math.atan2((to.second - from.second).toDouble(), (to.first - from.first).toDouble())).toFloat()
    }

    private fun averageAngle(a: Float, b: Float): Float {
        val ar = Math.toRadians(a.toDouble())
        val br = Math.toRadians(b.toDouble())
        val x = kotlin.math.cos(ar) + kotlin.math.cos(br)
        val y = kotlin.math.sin(ar) + kotlin.math.sin(br)
        return Math.toDegrees(kotlin.math.atan2(y, x)).toFloat()
    }

    private fun normalizeNumber(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cleaned = raw.removeSuffix("px").trim()
        val number = cleaned.toFloatOrNull() ?: return null
        return java.lang.String.format(Locale.US, "%.3f", number)
            .trimEnd('0')
            .trimEnd('.')
    }

    private fun normalizeFillRuleToVectorFillType(fillRule: String?): String? {
        return when (fillRule?.trim()?.lowercase(Locale.US)?.replace("-", "")) {
            "evenodd" -> "evenOdd"
            "nonzero" -> "nonZero"
            else -> null
        }
    }

    private fun approximatePathBounds(pathData: String): SvgObjectBounds? {
        val numberRegex = Regex("""[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        val nums = numberRegex.findAll(pathData).mapNotNull { it.value.toFloatOrNull() }.toList()
        if (nums.size < 2) return null

        val xs = mutableListOf<Float>()
        val ys = mutableListOf<Float>()

        // This intentionally approximates the bounds. For generated shape paths and most
        // converted pathData, coordinate values appear as x,y pairs. Arc flags/radii may add
        // extra numbers, but objectBoundingBox gradients are still substantially closer than
        // viewport-relative output when based on this local drawable extent.
        var i = 0
        while (i + 1 < nums.size) {
            xs.add(nums[i])
            ys.add(nums[i + 1])
            i += 2
        }

        if (xs.isEmpty() || ys.isEmpty()) return null
        val minX = xs.minOrNull() ?: return null
        val maxX = xs.maxOrNull() ?: return null
        val minY = ys.minOrNull() ?: return null
        val maxY = ys.maxOrNull() ?: return null
        val width = (maxX - minX).coerceAtLeast(0.001f)
        val height = (maxY - minY).coerceAtLeast(0.001f)
        return SvgObjectBounds(minX, minY, width, height)
    }

    private fun attr(tag: String, name: String): String? {
        val pattern = Regex("""\b$name\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
        return pattern.find(tag)?.groupValues?.getOrNull(2)
    }
}
