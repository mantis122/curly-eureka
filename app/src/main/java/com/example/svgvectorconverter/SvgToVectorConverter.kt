package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

object SvgToVectorConverter {
    fun convert(
        svg: String,
        outputDpSize: Int,
        conversionProfile: String
    ): ConversionResult {
        val startTime = System.nanoTime()

        SvgTransformParser.resetMatrixStats()

        val svgWithCssClassStyles = SvgStyleResolver.applyStylesheets(svg)

        val svgForTransformStats = stripSvgComments(svgWithCssClassStyles)
        val drawableSvgForStats = stripDefs(svgWithCssClassStyles)

        val clipPathData = SvgTreeConverter.collectClipPathData(
            svg = svgWithCssClassStyles,
            basicShapeToPathData = SvgPathEmitter::basicShapeToPathData
        )
        val maskPathData = SvgTreeConverter.collectMaskPathData(
            svg = svgWithCssClassStyles,
            basicShapeToPathData = SvgPathEmitter::basicShapeToPathData
        )
        val markerDefinitions = SvgTreeConverter.collectMarkerDefinitions(
            svg = svgWithCssClassStyles,
            basicShapeToPathData = SvgPathEmitter::basicShapeToPathData
        )
        val patternDefinitions = SvgTreeConverter.collectPatternDefinitions(
            svg = svgWithCssClassStyles,
            basicShapeToPathData = SvgPathEmitter::basicShapeToPathData
        )
        val svgFontDefinitions = SvgFontResolver.collectDefinitions(svgWithCssClassStyles)
        SvgTreeConverter.resetStats(clipPathData, maskPathData, markerDefinitions, patternDefinitions, svgFontDefinitions)

        val viewBoxValues = getViewBox(svgWithCssClassStyles)
        val widthFromSvg = getNumberAttr(svgWithCssClassStyles, "width")
        val heightFromSvg = getNumberAttr(svgWithCssClassStyles, "height")

        val viewportWidth = viewBoxValues?.getOrNull(2)
            ?: widthFromSvg
            ?: 24f
        val viewportHeight = viewBoxValues?.getOrNull(3)
            ?: heightFromSvg
            ?: 24f

        SvgTransformParser.setTransformOriginReferenceBox(viewportWidth, viewportHeight)

        val gradientDefinitions = SvgGradientResolver.collectGradientDefinitions(svgWithCssClassStyles, viewportWidth, viewportHeight)
        SvgPaintResolver.setGradientDefinitions(gradientDefinitions)
        val gradientFallbackColors = SvgGradientResolver.fallbackColors(gradientDefinitions)

        val patternFallbackColors = SvgPaintResolver.collectPatternFallbackColors(svgWithCssClassStyles)
        val patternApproximationStats = SvgPaintResolver.collectPatternApproximationStats(svgWithCssClassStyles, patternFallbackColors)
        SvgPaintResolver.setPatternFallbackColors(patternFallbackColors)

        val vectorWidthDp = if (outputDpSize > 0) outputDpSize else viewportWidth.toInt()
        val vectorHeightDp = if (outputDpSize > 0) outputDpSize else viewportHeight.toInt()

        val output = StringBuilder()
        val usesVectorGradients = gradientDefinitions.isNotEmpty()
        if (usesVectorGradients) {
            output.appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
            output.appendLine("""    xmlns:aapt="http://schemas.android.com/aapt"""")
        } else {
            output.appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
        }
        output.appendLine("""    android:width="${vectorWidthDp}dp"""")
        output.appendLine("""    android:height="${vectorHeightDp}dp"""")
        output.appendLine("""    android:viewportWidth="$viewportWidth"""")
        output.appendLine("""    android:viewportHeight="$viewportHeight">""")
        output.appendLine()

        SvgTreeConverter.appendConvertedSvgTree(
            output = output,
            svg = svgWithCssClassStyles,
            appendElementPath = SvgPathEmitter::appendElementPath,
            appendBasicShapePath = SvgPathEmitter::appendBasicShapePath,
            appendFlatPathsFallback = SvgPathEmitter::appendFlatPathsFallback,
            basicShapeToPathData = SvgPathEmitter::basicShapeToPathData,
            floatAttr = SvgPathEmitter::floatAttr,
            escapeXml = SvgPathEmitter::escapeXml
        )

        output.appendLine("</vector>")

        val rawXml = output.toString().trim().substringBeforeLast("</vector>") + "</vector>"
        val clipOptimizedXml = optimizeDuplicateClipPathGroups(rawXml)
        val pathOptimizationResult = SvgPathDataOptimizer.optimizeVectorXml(clipOptimizedXml)
        val finalXml = pathOptimizationResult.xml
        val pathOptimizationStats = pathOptimizationResult.stats
        val finalXmlForStats = stripSvgComments(finalXml)

        val convertedBasicShapeCount = countConvertedBasicShapes(finalXml)
        val convertedOriginalPathCount = countConvertedOriginalSvgPaths(finalXml)
        val convertedPathCount = Regex("""<path\b""").findAll(finalXmlForStats).count()
        val generatedGroupCount = Regex("""<group\b""").findAll(finalXmlForStats).count()

        val generatedGroups = Regex("""<group[\s\S]*?>""")
            .findAll(finalXmlForStats)
            .map { it.value }
            .toList()

        val generatedTranslateCount = generatedGroups.count {
            it.contains("android:translateX=") || it.contains("android:translateY=")
        }
        val generatedScaleCount = generatedGroups.count {
            it.contains("android:scaleX=") || it.contains("android:scaleY=")
        }
        val generatedRotateCount = generatedGroups.count {
            it.contains("android:rotation=")
        }

        val basicShapeBreakdown = countDrawableBasicShapeBreakdown(drawableSvgForStats)
        val visibleLineCount = countDrawableLines(drawableSvgForStats)
        val visibleBasicShapeCount = basicShapeBreakdown.rectangles +
            basicShapeBreakdown.roundedRectangles +
            basicShapeBreakdown.circles +
            basicShapeBreakdown.ellipses +
            basicShapeBreakdown.polygons +
            basicShapeBreakdown.polylines +
            visibleLineCount
        val definitionDrawableElementCount = countDefinitionDrawableElements(svgWithCssClassStyles)
        val drawableValidPathCount = countDrawableValidPaths(drawableSvgForStats)
        val unresolvedUseReferences = SvgTreeConverter.unresolvedUseReferences
        val visibleUseReferenceCount = countVisibleUseReferences(drawableSvgForStats)
        val resolvedVisibleUseReferenceCount = maxOf(
            0,
            visibleUseReferenceCount - unresolvedUseReferences
        )
        val visibleDrawableElementCount = maxOf(
            0,
            drawableValidPathCount + visibleBasicShapeCount + resolvedVisibleUseReferenceCount -
                SvgTreeConverter.hiddenDrawableElementsSkipped
        )
        val emptyPathCount = countAllPaths(svgWithCssClassStyles) - countValidPaths(svgWithCssClassStyles)

        val filterDefinitionCount = countFilterDefinitions(svgForTransformStats)
        val filterReferenceCount = countFilterReferences(svgForTransformStats)
        val textElementCount = countTags(svgForTransformStats, "text")
        val tspanElementCount = countTags(svgForTransformStats, "tspan")
        val textPathElementCount = countTags(svgForTransformStats, "textPath")
        val textLayoutStats = collectTextLayoutStats(svgWithCssClassStyles)
        val svgFontGlyphCount = countSvgFontGlyphs(svgForTransformStats)
        val contextPaintApproximationCount = countContextPaintReferences(svgForTransformStats)
        val cssImportRuleCount = SvgStyleResolver.cssImportRuleCount
        val cssImportedInlineRuleCount = SvgStyleResolver.cssImportedInlineRuleCount
        val cssExternalImportCount = SvgStyleResolver.cssExternalImportCount
        val imageStats = countImageStats(svgForTransformStats)
        val unsupported = buildUnsupportedWarnings(svgWithCssClassStyles, gradientFallbackColors, patternFallbackColors, clipPathData, maskPathData, filterReferenceCount)
        val matrixCount = Regex("""matrix\(""").findAll(svgForTransformStats).count()
        val useCount = Regex("""<\s*use\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svgWithCssClassStyles).count()
        val symbolCount = Regex("""<\s*symbol\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svgWithCssClassStyles).count()
        val clipPathReferenceCount = Regex("""clip-path\s*[:=]""", RegexOption.IGNORE_CASE)
            .findAll(svgForTransformStats)
            .count()
        val maskReferenceCount = Regex("""\bmask\s*[:=]""", RegexOption.IGNORE_CASE)
            .findAll(svgForTransformStats)
            .count()
        val styleAttributeCount = Regex("""\bstyle\s*=""", RegexOption.IGNORE_CASE)
            .findAll(svgForTransformStats)
            .count()
        val presentationStyleAttributeCount = countPresentationStyleAttributes(svgForTransformStats)

        val unapproximatedDashedStrokes = maxOf(
            0,
            SvgTreeConverter.dashedStrokesDetected - SvgTreeConverter.dashedStrokesApproximated
        )

        val handledTextCount = SvgTreeConverter.textElementsApproximated + SvgTreeConverter.textElementsConvertedToPaths
        val unapproximatedTextCount = maxOf(0, textElementCount - handledTextCount)

        val unconvertedTextPathCount = maxOf(0, textPathElementCount - SvgTreeConverter.textPathsConverted)
        val warningCount = unsupported.size +
            (if (unapproximatedTextCount > 0 || unconvertedTextPathCount > 0) 1 else 0) +
            (if (SvgTransformParser.unsupportedMatrixTransforms > 0) 1 else 0) +
            (if (unresolvedUseReferences > 0) 1 else 0) +
            (if (unapproximatedDashedStrokes > 0) 1 else 0) +
            (if (SvgTreeConverter.invalidDashArrays > 0) 1 else 0) +
            (if (SvgTreeConverter.invalidDashOffsetFallbacks > 0) 1 else 0) +
            (if (SvgTreeConverter.dashTransformApproximateCompensations > 0) 1 else 0) +
            (if (SvgTreeConverter.nonScalingStrokesUncertain > 0) 1 else 0) +
            (if (cssExternalImportCount > 0) 1 else 0) +
            (if (imageStats.imageElementCount > 0) 1 else 0) +
            (if (SvgTreeConverter.nestedSvgOverflowApproximated > 0) 1 else 0) +
            (if (SvgTreeConverter.nestedSvgOverflowUnsupported > 0) 1 else 0)

        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        val report = SvgConversionReporter.buildReport(
            SvgConversionReportData(
                convertedPathCount = convertedPathCount,
                convertedOriginalPathCount = convertedOriginalPathCount,
                convertedBasicShapeCount = convertedBasicShapeCount,
                basicShapeBreakdown = basicShapeBreakdown,
                definitionDrawableElementCount = definitionDrawableElementCount,
                visibleDrawableElementCount = visibleDrawableElementCount,
                drawableValidPathCount = drawableValidPathCount,
                emptyPathCount = emptyPathCount,
                generatedGroupCount = generatedGroupCount,
                useCount = useCount,
                resolvedUseExpansions = SvgTreeConverter.resolvedUseExpansions,
                unresolvedUseReferences = unresolvedUseReferences,
                symbolCount = symbolCount,
                gradientFallbackColorCount = gradientFallbackColors.size,
                patternApproximationCount = patternFallbackColors.size,
                patternApproximationStats = patternApproximationStats,
                patternTileExpansionCount = SvgTreeConverter.patternTileExpansions,
                patternTilePathCount = SvgTreeConverter.patternTilePathsEmitted,
                markerDefinitionCount = markerDefinitions.size,
                appliedMarkers = SvgTreeConverter.appliedMarkers,
                clipPathCount = clipPathData.size,
                clipPathReferenceCount = clipPathReferenceCount,
                appliedClipPaths = SvgTreeConverter.appliedClipPaths,
                maskPathCount = maskPathData.size,
                maskReferenceCount = maskReferenceCount,
                appliedMasks = SvgTreeConverter.appliedMasks,
                dashedStrokesDetected = SvgTreeConverter.dashedStrokesDetected,
                dashedStrokesApproximated = SvgTreeConverter.dashedStrokesApproximated,
                invalidDashArrays = SvgTreeConverter.invalidDashArrays,
                dashSolidFallbacks = SvgTreeConverter.dashSolidFallbacks,
                oddDashListsDuplicated = SvgTreeConverter.oddDashListsDuplicated,
                invalidDashOffsetFallbacks = SvgTreeConverter.invalidDashOffsetFallbacks,
                dashOffsetsNormalized = SvgTreeConverter.dashOffsetsNormalized,
                dashTransformExactCompensations = SvgTreeConverter.dashTransformExactCompensations,
                dashTransformApproximateCompensations = SvgTreeConverter.dashTransformApproximateCompensations,
                nonScalingStrokesDetected = SvgTreeConverter.nonScalingStrokesDetected,
                nonScalingStrokesCompensated = SvgTreeConverter.nonScalingStrokesCompensated,
                nonScalingStrokesUncertain = SvgTreeConverter.nonScalingStrokesUncertain,
                displayNoneElementsSkipped = SvgTreeConverter.displayNoneElementsSkipped,
                visibilityHiddenElementsSkipped = SvgTreeConverter.visibilityHiddenElementsSkipped,
                nestedSvgViewportCount = SvgTreeConverter.nestedSvgViewports,
                nestedSvgViewportClipCount = SvgTreeConverter.nestedSvgViewportClips,
                nestedSvgPercentageViewportCount = SvgTreeConverter.nestedSvgPercentageViewports,
                nestedSvgOverflowHiddenCount = SvgTreeConverter.nestedSvgOverflowHidden,
                nestedSvgOverflowVisibleCount = SvgTreeConverter.nestedSvgOverflowVisible,
                nestedSvgOverflowAutoCount = SvgTreeConverter.nestedSvgOverflowAuto,
                nestedSvgOverflowScrollCount = SvgTreeConverter.nestedSvgOverflowScroll,
                nestedSvgOverflowUnsupportedCount = SvgTreeConverter.nestedSvgOverflowUnsupported,
                filterDefinitionCount = filterDefinitionCount,
                filterReferenceCount = filterReferenceCount,
                textElementCount = textElementCount,
                tspanElementCount = tspanElementCount,
                textPathElementCount = textPathElementCount,
                textElementsApproximated = SvgTreeConverter.textElementsApproximated,
                textElementsConvertedToPaths = SvgTreeConverter.textElementsConvertedToPaths,
                textGlyphPathsEmitted = SvgTreeConverter.textGlyphPathsEmitted,
                textGlyphSpecificAdvances = SvgTreeConverter.textGlyphSpecificAdvances,
                textDefaultFontAdvances = SvgTreeConverter.textDefaultFontAdvances,
                textMissingGlyphFallbacks = SvgTreeConverter.textMissingGlyphFallbacks,
                textGlyphNameLookups = SvgTreeConverter.textGlyphNameLookups,
                textHorizontalKerningPairs = SvgTreeConverter.textHorizontalKerningPairs,
                textVerticalKerningPairs = SvgTreeConverter.textVerticalKerningPairs,
                textHorizontalKerningPairsMatched = SvgTreeConverter.textHorizontalKerningPairsMatched,
                textVerticalKerningPairsMatched = SvgTreeConverter.textVerticalKerningPairsMatched,
                textKerningAdjustmentsApplied = SvgTreeConverter.textKerningAdjustmentsApplied,
                textLengthSpacingAdjustments = SvgTreeConverter.textLengthSpacingAdjustments,
                textLengthSpacingAndGlyphsAdjustments = SvgTreeConverter.textLengthSpacingAndGlyphsAdjustments,
                textGlyphRotationsApplied = SvgTreeConverter.textGlyphRotationsApplied,
                textLetterSpacingAdjustmentsApplied = SvgTreeConverter.textLetterSpacingAdjustmentsApplied,
                textWordSpacingAdjustmentsApplied = SvgTreeConverter.textWordSpacingAdjustmentsApplied,
                textDecorationPathsEmitted = SvgTreeConverter.textDecorationPathsEmitted,
                textBidiRunsReordered = SvgTreeConverter.textBidiRunsReordered,
                textDirections = SvgTreeConverter.textDirections,
                textUnicodeBidiModes = SvgTreeConverter.textUnicodeBidiModes,
                textPathsConverted = SvgTreeConverter.textPathsConverted,
                textPathGlyphsEmitted = SvgTreeConverter.textPathGlyphsEmitted,
                textFontFamilies = SvgTreeConverter.textFontFamilies,
                textFontWeights = SvgTreeConverter.textFontWeights,
                verticalWritingTextCount = textLayoutStats.verticalTextCount,
                writingModes = textLayoutStats.writingModes,
                textAnchors = textLayoutStats.textAnchors,
                dominantBaselines = textLayoutStats.dominantBaselines,
                alignmentBaselines = textLayoutStats.alignmentBaselines,
                baselineShifts = textLayoutStats.baselineShifts,
                lengthAdjustModes = textLayoutStats.lengthAdjustModes,
                textPathMethods = textLayoutStats.textPathMethods,
                svgFontGlyphCount = svgFontGlyphCount,
                contextPaintApproximationCount = contextPaintApproximationCount,
                cssImportRuleCount = cssImportRuleCount,
                cssImportedInlineRuleCount = cssImportedInlineRuleCount,
                cssExternalImportCount = cssExternalImportCount,
                imageStats = imageStats,
                styleAttributeCount = styleAttributeCount,
                presentationStyleAttributeCount = presentationStyleAttributeCount,
                warningCount = warningCount,
                unsupportedWarnings = unsupported,
                unsupportedMatrixTransforms = SvgTransformParser.unsupportedMatrixTransforms,
                supportedMatrixTransforms = SvgTransformParser.supportedMatrixTransforms,
                matrixCount = matrixCount,
                translateCount = generatedTranslateCount,
                scaleCount = generatedScaleCount,
                rotateCount = generatedRotateCount,
                conversionProfile = conversionProfile,
                outputDpSize = outputDpSize,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                pathDataOptimizedCount = pathOptimizationStats.pathCount,
                pathDataCharactersBefore = pathOptimizationStats.charactersBefore,
                pathDataCharactersAfter = pathOptimizationStats.charactersAfter,
                pathDataRepeatedCommandsRemoved = pathOptimizationStats.repeatedCommandsRemoved,
                pathDataNumbersNormalized = pathOptimizationStats.numbersNormalized,
                emptyPathDataRemoved = pathOptimizationStats.emptyPathDataRemoved,
                moveOnlyPathsRemoved = pathOptimizationStats.moveOnlyPathsRemoved,
                invisiblePathsRemoved = pathOptimizationStats.invisiblePathsRemoved,
                emptyGroupsRemoved = pathOptimizationStats.emptyGroupsRemoved,
                optimizedXmlCharactersBefore = pathOptimizationStats.xmlCharactersBefore,
                optimizedXmlCharactersAfter = pathOptimizationStats.xmlCharactersAfter,
                elapsedMs = elapsedMs
            )
        )

        return ConversionResult(finalXml, report)
    }


    private data class TextLayoutStats(
        val verticalTextCount: Int = 0,
        val writingModes: List<String> = emptyList(),
        val textAnchors: List<String> = emptyList(),
        val dominantBaselines: List<String> = emptyList(),
        val alignmentBaselines: List<String> = emptyList(),
        val baselineShifts: List<String> = emptyList(),
        val lengthAdjustModes: List<String> = emptyList(),
        val textPathMethods: List<String> = emptyList()
    )

    private fun collectTextLayoutStats(svg: String): TextLayoutStats {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
            }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(svg)))
            val textElements = document.getElementsByTagNameNS("*", "text")

            val writingModes = linkedSetOf<String>()
            val textAnchors = linkedSetOf<String>()
            val dominantBaselines = linkedSetOf<String>()
            val alignmentBaselines = linkedSetOf<String>()
            val baselineShifts = linkedSetOf<String>()
            val lengthAdjustModes = linkedSetOf<String>()
            val textPathMethods = linkedSetOf<String>()
            var verticalTextCount = 0

            fun localName(element: Element): String =
                (element.localName ?: element.tagName.substringAfter(':')).lowercase()

            fun inlineStyleValue(element: Element, property: String): String? {
                val style = element.getAttribute("style")
                if (style.isBlank()) return null
                return style.split(';')
                    .asSequence()
                    .mapNotNull { declaration ->
                        val index = declaration.indexOf(':')
                        if (index <= 0) null
                        else declaration.substring(0, index).trim().lowercase() to
                            declaration.substring(index + 1).trim()
                    }
                    .firstOrNull { it.first == property }
                    ?.second
            }

            fun directValue(element: Element, property: String): String? {
                return inlineStyleValue(element, property)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: element.getAttribute(property).trim().takeIf { it.isNotBlank() }
            }

            fun inheritedValue(element: Element, property: String): String? {
                var current: Node? = element
                while (current is Element) {
                    directValue(current, property)?.let { return it }
                    current = current.parentNode
                }
                return null
            }

            fun normalizeMode(raw: String?): String? {
                val value = raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
                return when (value) {
                    "horizontal-tb", "lr", "lr-tb", "rl", "rl-tb" -> "horizontal-tb"
                    "vertical-rl", "tb-rl" -> "vertical-rl"
                    "vertical-lr", "tb", "tb-lr" -> "vertical-lr"
                    else -> value
                }
            }

            fun normalized(raw: String?): String? =
                raw?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

            fun collectElement(element: Element) {
                val tag = localName(element)
                if (tag == "text" || tag == "tspan" || tag == "textpath") {
                    normalizeMode(inheritedValue(element, "writing-mode"))
                        ?.let(writingModes::add)
                    normalized(inheritedValue(element, "text-anchor"))
                        ?.let(textAnchors::add)
                    normalized(inheritedValue(element, "dominant-baseline"))
                        ?.let(dominantBaselines::add)
                    normalized(inheritedValue(element, "alignment-baseline"))
                        ?.let(alignmentBaselines::add)
                    normalized(inheritedValue(element, "baseline-shift"))
                        ?.let(baselineShifts::add)
                    normalized(inheritedValue(element, "lengthAdjust"))
                        ?.let { value ->
                            lengthAdjustModes.add(
                                when (value) {
                                    "spacingandglyphs" -> "spacingAndGlyphs"
                                    else -> value
                                }
                            )
                        }
                }

                if (tag == "textpath") {
                    val method = normalized(directValue(element, "method")) ?: "align"
                    textPathMethods.add(method)
                }

                val children = element.childNodes
                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child is Element) collectElement(child)
                }
            }

            for (i in 0 until textElements.length) {
                val element = textElements.item(i) as? Element ?: continue
                val mode = normalizeMode(inheritedValue(element, "writing-mode")) ?: "horizontal-tb"
                writingModes.add(mode)
                if (mode == "vertical-rl" || mode == "vertical-lr") verticalTextCount++
                collectElement(element)
            }

            TextLayoutStats(
                verticalTextCount = verticalTextCount,
                writingModes = writingModes.toList(),
                textAnchors = textAnchors.toList(),
                dominantBaselines = dominantBaselines.toList(),
                alignmentBaselines = alignmentBaselines.toList(),
                baselineShifts = baselineShifts.toList(),
                lengthAdjustModes = lengthAdjustModes.toList(),
                textPathMethods = textPathMethods.toList()
            )
        } catch (_: Exception) {
            TextLayoutStats()
        }
    }

    private fun buildUnsupportedWarnings(
        svg: String,
        gradientFallbackColors: Map<String, String>,
        patternFallbackColors: Map<String, String>,
        clipPathData: Map<String, String>,
        maskPathData: Map<String, String>,
        filterReferenceCount: Int
    ): List<String> {
        val unsupported = mutableListOf<String>()

        if (hasTag(svg, "linearGradient") && gradientFallbackColors.isEmpty()) {
            unsupported.add("Linear gradients")
        }
        if (hasTag(svg, "radialGradient") && gradientFallbackColors.isEmpty()) {
            unsupported.add("Radial gradients")
        }
        if (hasTag(svg, "mask") && maskPathData.isEmpty()) unsupported.add("Masks")
        if (filterReferenceCount > 0) unsupported.add("Filter effects ignored: $filterReferenceCount")
        if (hasTag(svg, "clipPath") && clipPathData.isEmpty()) unsupported.add("Clip paths")
        if (hasTag(svg, "pattern") && patternFallbackColors.isEmpty()) {
    unsupported.add("Patterns")
}

val paintUrlRefs = Regex("""\b(?:fill|stroke)\s*=\s*["']url\(#([^)]+)\)["']""", RegexOption.IGNORE_CASE)
    .findAll(svg)
    .mapNotNull { it.groupValues.getOrNull(1) }
    .toSet()

val knownPaintIds =
    gradientFallbackColors.keys +
    patternFallbackColors.keys +
    clipPathData.keys +
    maskPathData.keys

paintUrlRefs
    .filter { it !in knownPaintIds }
    .forEach { unsupported.add("Missing paint reference: #$it") }
        return unsupported
    }

    private fun countTags(svg: String, tagName: String): Int {
        return Regex("""<\s*$tagName\b""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
    }

    private fun countSvgFontGlyphs(svg: String): Int {
        return Regex("""<\s*(?:glyph|missing-glyph)\b[^>]*\bd\s*=""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
    }

    private fun countContextPaintReferences(svg: String): Int {
        return Regex("""\b(?:fill|stroke)\s*=\s*["']context-(?:fill|stroke)["']|(?:fill|stroke)\s*:\s*context-(?:fill|stroke)\b""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
    }

    private fun countImageStats(svg: String): SvgImageStats {
        val imageTags = Regex("""<\s*image\b[^>]*(?:/>|>)""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .map { it.value }
            .toList()

        var embeddedRaster = 0
        var embeddedSvg = 0
        var external = 0
        var missingHref = 0
        var withSize = 0

        imageTags.forEach { tag ->
            val href = attrExact(tag, "href")
                ?: attrExact(tag, "xlink:href")
                ?: attrExact(tag, "src")

            if ((attrExact(tag, "width")?.trim().orEmpty()).isNotBlank() &&
                (attrExact(tag, "height")?.trim().orEmpty()).isNotBlank()
            ) {
                withSize++
            }

            when {
                href.isNullOrBlank() -> missingHref++
                href.trim().startsWith("data:image/svg+xml", ignoreCase = true) -> embeddedSvg++
                href.trim().startsWith("data:image/", ignoreCase = true) -> embeddedRaster++
                else -> external++
            }
        }

        return SvgImageStats(
            imageElementCount = imageTags.size,
            embeddedRasterImageCount = embeddedRaster,
            embeddedSvgImageCount = embeddedSvg,
            externalImageCount = external,
            missingHrefImageCount = missingHref,
            imageElementsWithSize = withSize
        )
    }

    private fun countFilterDefinitions(svg: String): Int {
        return Regex("""<\s*filter\b""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
    }

    private fun countFilterReferences(svg: String): Int {
        val attrRefs = Regex("""\bfilter\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
        val styleRefs = Regex("""filter\s*:\s*url\(""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
        return attrRefs + styleRefs
    }

    private fun countPresentationStyleAttributes(svg: String): Int {
        return listOf(
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
                .findAll(svg)
                .count()
        }
    }

    private fun countConvertedBasicShapes(xml: String): Int {
        return Regex("""<!-- converted from <(rect|circle|ellipse|line|polyline|polygon)> -->""")
            .findAll(xml)
            .count()
    }

    private fun countConvertedOriginalSvgPaths(xml: String): Int {
        return Regex("""<!-- converted from <path> -->\s*<path\b""")
            .findAll(xml)
            .count()
    }

    private fun countDrawableBasicShapeBreakdown(svg: String): BasicShapeBreakdown {
        var rectangles = 0
        var roundedRectangles = 0
        var circles = 0
        var ellipses = 0
        var polygons = 0
        var polylines = 0

        fun countElement(element: Element) {
            val tag = element.tagName.substringAfter(":").lowercase()

            when (tag) {
                "rect" -> {
                    if (SvgShapeConverters.basicShapeToPathData(element, tag) != null) {
                        val rx = SvgPathEmitter.floatAttr(element, "rx") ?: 0f
                        val ry = SvgPathEmitter.floatAttr(element, "ry") ?: 0f
                        if (rx > 0f || ry > 0f) roundedRectangles++ else rectangles++
                    }
                }
                "circle" -> if (SvgShapeConverters.basicShapeToPathData(element, tag) != null) circles++
                "ellipse" -> if (SvgShapeConverters.basicShapeToPathData(element, tag) != null) ellipses++
                "polygon" -> if (SvgShapeConverters.basicShapeToPathData(element, tag) != null) polygons++
                "polyline" -> if (SvgShapeConverters.basicShapeToPathData(element, tag) != null) polylines++
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) countElement(child as Element)
            }
        }

        return try {
            val document = newDocument(svg)
            countElement(document.documentElement)
            BasicShapeBreakdown(rectangles, roundedRectangles, circles, ellipses, polygons, polylines)
        } catch (_: Exception) {
            val rectTagMatches = Regex("""<\s*rect\b[^>]*>""", RegexOption.IGNORE_CASE)
                .findAll(svg)
                .map { it.value }
                .toList()
            val roundedRectFallbackCount = rectTagMatches.count { tag ->
                Regex("""\b(rx|ry)\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(tag)
            }
            BasicShapeBreakdown(
                rectangles = rectTagMatches.size - roundedRectFallbackCount,
                roundedRectangles = roundedRectFallbackCount,
                circles = Regex("""<\s*circle\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
                ellipses = Regex("""<\s*ellipse\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
                polygons = Regex("""<\s*polygon\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
                polylines = Regex("""<\s*polyline\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
            )
        }
    }

    private fun countDrawableLines(svg: String): Int {
        var lines = 0

        fun countElement(element: Element) {
            val tag = element.tagName.substringAfter(":").lowercase()

            if (tag == "line" && SvgShapeConverters.basicShapeToPathData(element, tag) != null) {
                lines++
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) countElement(child as Element)
            }
        }

        return try {
            val document = newDocument(svg)
            countElement(document.documentElement)
            lines
        } catch (_: Exception) {
            Regex("""<\s*line\b[^>]*>""", RegexOption.IGNORE_CASE)
                .findAll(svg)
                .count()
        }
    }

    private fun countDefinitionDrawableElements(svg: String): Int {
        val basicShapeTags = setOf("rect", "circle", "ellipse", "line", "polyline", "polygon")
        var count = 0

        fun countDrawableElement(element: Element) {
            val tag = element.tagName.substringAfter(":").lowercase()

            when (tag) {
                "path" -> if (element.getAttribute("d").trim().isNotBlank()) count++
                in basicShapeTags -> if (SvgShapeConverters.basicShapeToPathData(element, tag) != null) count++
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) countDrawableElement(child as Element)
            }
        }

        return try {
            val document = newDocument(svg)
            val defsNodes = document.getElementsByTagName("defs")

            for (i in 0 until defsNodes.length) {
                val defs = defsNodes.item(i)
                val children = defs.childNodes

                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeType == Node.ELEMENT_NODE) countDrawableElement(child as Element)
                }
            }

            count
        } catch (_: Exception) {
            val defsBlocks = Regex(
                """<defs\b[^>]*>.*?</defs>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).findAll(svg)

            defsBlocks.sumOf { block ->
                val value = block.value
                val pathCount = Regex("""<path\b[^>]*\bd\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE)
                    .findAll(value)
                    .count()
                val shapeCount = basicShapeTags.sumOf { tag ->
                    Regex("""<\s*$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
                        .findAll(value)
                        .count()
                }
                pathCount + shapeCount
            }
        }
    }

    private fun countAllPaths(svg: String): Int {
        return Regex("""<path\b[^>]*>""").findAll(svg).count()
    }

    private fun countValidPaths(svg: String): Int {
        return Regex("""<path\b[^>]*>""")
            .findAll(svg)
            .count { match -> attr(match.value, "d")?.trim().isNullOrBlank().not() }
    }

    private fun countDrawableValidPaths(svg: String): Int {
        return Regex("""<path\b[^>]*>""")
            .findAll(svg)
            .count { match -> attr(match.value, "d")?.trim().isNullOrBlank().not() }
    }

    private fun countVisibleUseReferences(svg: String): Int {
        return Regex("""<\s*use\b[^>]*>""", RegexOption.IGNORE_CASE)
            .findAll(svg)
            .count()
    }

    private fun optimizeDuplicateClipPathGroups(xml: String): String {
        fun reindentBlock(block: String, indent: String): String {
            return block.lines().joinToString("\n") { line ->
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
                    appendLine("${indent}<group>")
                    appendLine(reindentBlock(clipPath, "$indent    "))
                    if (firstBody.isNotBlank()) appendLine(firstBody)
                    if (secondBody.isNotBlank()) appendLine(secondBody)
                    append("${indent}</group>")
                }
            }

            if (updated == current) return current
            current = updated
        }
    }


    private fun newDocument(svg: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isIgnoringComments = true
    }.newDocumentBuilder().parse(InputSource(StringReader(svg)))

    private fun hasTag(svg: String, tagName: String): Boolean {
        return Regex("""<\s*$tagName\b""", RegexOption.IGNORE_CASE).containsMatchIn(svg)
    }

    private fun stripSvgComments(xml: String): String {
        return Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL).replace(xml, "")
    }

    private fun stripDefs(xml: String): String {
        return Regex("""<defs\b[^>]*>.*?</defs>""", RegexOption.DOT_MATCHES_ALL).replace(xml, "")
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

    private fun attrExact(tag: String, name: String): String? {
        val pattern = Regex("""(?:^|\s)$name\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
        return pattern.find(tag)?.groupValues?.getOrNull(2)
    }

    private fun attr(tag: String, name: String): String? {
        val pattern = Regex("""\b$name\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
        return pattern.find(tag)?.groupValues?.getOrNull(2)
    }
}
