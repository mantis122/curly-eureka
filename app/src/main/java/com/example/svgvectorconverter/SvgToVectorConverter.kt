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

        val styleStartTime = System.nanoTime()
        val svgWithCssClassStyles = SvgStyleResolver.applyStylesheets(svg)
        val styleResolutionNanos = elapsedNanoseconds(styleStartTime)

        val svgParsingStartTime = System.nanoTime()
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
        val svgParsingNanos = elapsedNanoseconds(svgParsingStartTime)

        val treeConversionStartTime = System.nanoTime()
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
        val treeConversionNanos = elapsedNanoseconds(treeConversionStartTime)

        val optimizationStartTime = System.nanoTime()
        val rawXml = output.toString().trim().substringBeforeLast("</vector>") + "</vector>"
        val clipOptimizedXml = optimizeDuplicateClipPathGroups(rawXml)
        val pathOptimizationResult = SvgPathDataOptimizer.optimizeVectorXml(clipOptimizedXml)
        val finalXml = pathOptimizationResult.xml
        val pathOptimizationStats = pathOptimizationResult.stats
        val outputOptimizationNanos = elapsedNanoseconds(optimizationStartTime)

        val analysisStartTime = System.nanoTime()
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

        val reportAnalysisNanos = elapsedNanoseconds(analysisStartTime)
        val elapsedNanos = elapsedNanoseconds(startTime)
        val elapsedMs = elapsedNanos / 1_000_000L

        val reportData = SvgConversionReportData()
        reportData.convertedPathCount = convertedPathCount
        reportData.convertedOriginalPathCount = convertedOriginalPathCount
        reportData.convertedBasicShapeCount = convertedBasicShapeCount
        reportData.basicShapeBreakdown = basicShapeBreakdown
        reportData.definitionDrawableElementCount = definitionDrawableElementCount
        reportData.visibleDrawableElementCount = visibleDrawableElementCount
        reportData.drawableValidPathCount = drawableValidPathCount
        reportData.emptyPathCount = emptyPathCount
        reportData.generatedGroupCount = generatedGroupCount
        reportData.useCount = useCount
        reportData.resolvedUseExpansions = SvgTreeConverter.resolvedUseExpansions
        reportData.unresolvedUseReferences = unresolvedUseReferences
        reportData.symbolCount = symbolCount
        reportData.gradientFallbackColorCount = gradientFallbackColors.size
        reportData.patternApproximationCount = patternFallbackColors.size
        reportData.patternApproximationStats = patternApproximationStats
        reportData.patternTileExpansionCount = SvgTreeConverter.patternTileExpansions
        reportData.patternTilePathCount = SvgTreeConverter.patternTilePathsEmitted
        reportData.markerDefinitionCount = markerDefinitions.size
        reportData.appliedMarkers = SvgTreeConverter.appliedMarkers
        reportData.clipPathCount = clipPathData.size
        reportData.clipPathReferenceCount = clipPathReferenceCount
        reportData.appliedClipPaths = SvgTreeConverter.appliedClipPaths
        reportData.maskPathCount = maskPathData.size
        reportData.maskReferenceCount = maskReferenceCount
        reportData.appliedMasks = SvgTreeConverter.appliedMasks
        reportData.dashedStrokesDetected = SvgTreeConverter.dashedStrokesDetected
        reportData.dashedStrokesApproximated = SvgTreeConverter.dashedStrokesApproximated
        reportData.invalidDashArrays = SvgTreeConverter.invalidDashArrays
        reportData.dashSolidFallbacks = SvgTreeConverter.dashSolidFallbacks
        reportData.oddDashListsDuplicated = SvgTreeConverter.oddDashListsDuplicated
        reportData.invalidDashOffsetFallbacks = SvgTreeConverter.invalidDashOffsetFallbacks
        reportData.dashOffsetsNormalized = SvgTreeConverter.dashOffsetsNormalized
        reportData.dashTransformExactCompensations = SvgTreeConverter.dashTransformExactCompensations
        reportData.dashTransformApproximateCompensations = SvgTreeConverter.dashTransformApproximateCompensations
        reportData.nonScalingStrokesDetected = SvgTreeConverter.nonScalingStrokesDetected
        reportData.nonScalingStrokesCompensated = SvgTreeConverter.nonScalingStrokesCompensated
        reportData.nonScalingStrokesUncertain = SvgTreeConverter.nonScalingStrokesUncertain
        reportData.displayNoneElementsSkipped = SvgTreeConverter.displayNoneElementsSkipped
        reportData.visibilityHiddenElementsSkipped = SvgTreeConverter.visibilityHiddenElementsSkipped
        reportData.nestedSvgViewportCount = SvgTreeConverter.nestedSvgViewports
        reportData.nestedSvgViewportClipCount = SvgTreeConverter.nestedSvgViewportClips
        reportData.nestedSvgPercentageViewportCount = SvgTreeConverter.nestedSvgPercentageViewports
        reportData.nestedSvgOverflowHiddenCount = SvgTreeConverter.nestedSvgOverflowHidden
        reportData.nestedSvgOverflowVisibleCount = SvgTreeConverter.nestedSvgOverflowVisible
        reportData.nestedSvgOverflowAutoCount = SvgTreeConverter.nestedSvgOverflowAuto
        reportData.nestedSvgOverflowScrollCount = SvgTreeConverter.nestedSvgOverflowScroll
        reportData.nestedSvgOverflowUnsupportedCount = SvgTreeConverter.nestedSvgOverflowUnsupported
        reportData.filterDefinitionCount = filterDefinitionCount
        reportData.filterReferenceCount = filterReferenceCount
        reportData.textElementCount = textElementCount
        reportData.tspanElementCount = tspanElementCount
        reportData.textPathElementCount = textPathElementCount
        reportData.textElementsApproximated = SvgTreeConverter.textElementsApproximated
        reportData.textElementsConvertedToPaths = SvgTreeConverter.textElementsConvertedToPaths
        reportData.textGlyphPathsEmitted = SvgTreeConverter.textGlyphPathsEmitted
        reportData.textGlyphSpecificAdvances = SvgTreeConverter.textGlyphSpecificAdvances
        reportData.textDefaultFontAdvances = SvgTreeConverter.textDefaultFontAdvances
        reportData.textMissingGlyphFallbacks = SvgTreeConverter.textMissingGlyphFallbacks
        reportData.textGlyphNameLookups = SvgTreeConverter.textGlyphNameLookups
        reportData.textHorizontalKerningPairs = SvgTreeConverter.textHorizontalKerningPairs
        reportData.textVerticalKerningPairs = SvgTreeConverter.textVerticalKerningPairs
        reportData.textHorizontalKerningPairsMatched = SvgTreeConverter.textHorizontalKerningPairsMatched
        reportData.textVerticalKerningPairsMatched = SvgTreeConverter.textVerticalKerningPairsMatched
        reportData.textKerningAdjustmentsApplied = SvgTreeConverter.textKerningAdjustmentsApplied
        reportData.textLengthSpacingAdjustments = SvgTreeConverter.textLengthSpacingAdjustments
        reportData.textLengthSpacingAndGlyphsAdjustments = SvgTreeConverter.textLengthSpacingAndGlyphsAdjustments
        reportData.textGlyphRotationsApplied = SvgTreeConverter.textGlyphRotationsApplied
        reportData.textLetterSpacingAdjustmentsApplied = SvgTreeConverter.textLetterSpacingAdjustmentsApplied
        reportData.textWordSpacingAdjustmentsApplied = SvgTreeConverter.textWordSpacingAdjustmentsApplied
        reportData.textDecorationPathsEmitted = SvgTreeConverter.textDecorationPathsEmitted
        reportData.textBidiRunsReordered = SvgTreeConverter.textBidiRunsReordered
        reportData.textDirections = SvgTreeConverter.textDirections
        reportData.textUnicodeBidiModes = SvgTreeConverter.textUnicodeBidiModes
        reportData.textPathsConverted = SvgTreeConverter.textPathsConverted
        reportData.textPathGlyphsEmitted = SvgTreeConverter.textPathGlyphsEmitted
        reportData.textFontFamilies = SvgTreeConverter.textFontFamilies
        reportData.textFontWeights = SvgTreeConverter.textFontWeights
        reportData.verticalWritingTextCount = textLayoutStats.verticalTextCount
        reportData.writingModes = textLayoutStats.writingModes
        reportData.textAnchors = textLayoutStats.textAnchors
        reportData.dominantBaselines = textLayoutStats.dominantBaselines
        reportData.alignmentBaselines = textLayoutStats.alignmentBaselines
        reportData.baselineShifts = textLayoutStats.baselineShifts
        reportData.lengthAdjustModes = textLayoutStats.lengthAdjustModes
        reportData.textPathMethods = textLayoutStats.textPathMethods
        reportData.svgFontGlyphCount = svgFontGlyphCount
        reportData.contextPaintApproximationCount = contextPaintApproximationCount
        reportData.cssImportRuleCount = cssImportRuleCount
        reportData.cssImportedInlineRuleCount = cssImportedInlineRuleCount
        reportData.cssExternalImportCount = cssExternalImportCount
        reportData.imageStats = imageStats
        reportData.styleAttributeCount = styleAttributeCount
        reportData.presentationStyleAttributeCount = presentationStyleAttributeCount
        reportData.warningCount = warningCount
        reportData.unsupportedWarnings = unsupported
        reportData.unsupportedMatrixTransforms = SvgTransformParser.unsupportedMatrixTransforms
        reportData.supportedMatrixTransforms = SvgTransformParser.supportedMatrixTransforms
        reportData.matrixCount = matrixCount
        reportData.translateCount = generatedTranslateCount
        reportData.scaleCount = generatedScaleCount
        reportData.rotateCount = generatedRotateCount
        reportData.conversionProfile = conversionProfile
        reportData.outputDpSize = outputDpSize
        reportData.viewportWidth = viewportWidth
        reportData.viewportHeight = viewportHeight
        reportData.pathDataOptimizedCount = pathOptimizationStats.pathCount
        reportData.pathDataCharactersBefore = pathOptimizationStats.charactersBefore
        reportData.pathDataCharactersAfter = pathOptimizationStats.charactersAfter
        reportData.pathDataRepeatedCommandsRemoved = pathOptimizationStats.repeatedCommandsRemoved
        reportData.redundantNonDrawingSegmentsRemoved = pathOptimizationStats.redundantNonDrawingSegmentsRemoved
        reportData.collinearLineSegmentsConsolidated = pathOptimizationStats.collinearLineSegmentsConsolidated
        reportData.straightBezierCurvesSimplified = pathOptimizationStats.straightBezierCurvesSimplified
        reportData.degenerateArcsSimplified = pathOptimizationStats.degenerateArcsSimplified
        reportData.smoothBezierShorthandsSelected = pathOptimizationStats.smoothBezierShorthandsSelected
        reportData.cubicCurvesReducedToQuadratic = pathOptimizationStats.cubicCurvesReducedToQuadratic
        reportData.arcRotationsCanonicalized = pathOptimizationStats.arcRotationsCanonicalized
        reportData.arcRadiiCanonicalized = pathOptimizationStats.arcRadiiCanonicalized
        reportData.arcHalfTurnRotationsReduced = pathOptimizationStats.arcHalfTurnRotationsReduced
        reportData.arcAxesSwappedForSize = pathOptimizationStats.arcAxesSwappedForSize
        reportData.arcRepresentationsGloballyMinimized = pathOptimizationStats.arcRepresentationsGloballyMinimized
        reportData.commandSequencesGloballyMinimized = pathOptimizationStats.commandSequencesGloballyMinimized
        reportData.implicitLineTosAfterMoveSelected = pathOptimizationStats.implicitLineTosAfterMoveSelected
        reportData.repeatedShorthandCurveCommandsOmitted = pathOptimizationStats.repeatedShorthandCurveCommandsOmitted
        reportData.repeatedFullCurveCommandsOmitted = pathOptimizationStats.repeatedFullCurveCommandsOmitted
        reportData.repeatedArcCommandsOmitted = pathOptimizationStats.repeatedArcCommandsOmitted
        reportData.scientificNotationValuesSelected = pathOptimizationStats.scientificNotationValuesSelected
        reportData.globallyOptimizedNumericPaths = pathOptimizationStats.globallyOptimizedNumericPaths
        reportData.pathDataNumbersNormalized = pathOptimizationStats.numbersNormalized
        reportData.emptyPathDataRemoved = pathOptimizationStats.emptyPathDataRemoved
        reportData.moveOnlyPathsRemoved = pathOptimizationStats.moveOnlyPathsRemoved
        reportData.invisiblePathsRemoved = pathOptimizationStats.invisiblePathsRemoved
        reportData.emptyGroupsRemoved = pathOptimizationStats.emptyGroupsRemoved
        reportData.redundantGroupsFlattened = pathOptimizationStats.redundantGroupsFlattened
        reportData.commonTranslationGroupsFactored = pathOptimizationStats.commonTranslationGroupsFactored
        reportData.adjacentGroupsCoalesced = pathOptimizationStats.adjacentGroupsCoalesced
        reportData.compatiblePathsMerged = pathOptimizationStats.compatiblePathsMerged
        reportData.compatiblePathMergesPreservedForSize = pathOptimizationStats.compatiblePathMergesPreservedForSize
        reportData.adjacentPathPairsExamined = pathOptimizationStats.adjacentPathPairsExamined
        reportData.adjacentPathPairsSamePaint = pathOptimizationStats.adjacentPathPairsSamePaint
        reportData.adjacentPathMergeRejectedNestedPaint = pathOptimizationStats.adjacentPathMergeRejectedNestedPaint
        reportData.adjacentPathMergeRejectedMissingPathData = pathOptimizationStats.adjacentPathMergeRejectedMissingPathData
        reportData.adjacentPathMergeRejectedPaintMismatch = pathOptimizationStats.adjacentPathMergeRejectedPaintMismatch
        reportData.adjacentPathMergeRejectedUnsupportedGeometry = pathOptimizationStats.adjacentPathMergeRejectedUnsupportedGeometry
        reportData.adjacentPathMergeRejectedOverlapSafety = pathOptimizationStats.adjacentPathMergeRejectedOverlapSafety
        reportData.adjacentPathMergeRejectedForSize = pathOptimizationStats.adjacentPathMergeRejectedForSize
        reportData.exactDuplicatePathsRemoved = pathOptimizationStats.exactDuplicatePathsRemoved
        reportData.translatedGroupsFlattened = pathOptimizationStats.translatedGroupsFlattened
        reportData.translatedPaths = pathOptimizationStats.translatedPaths
        reportData.translationGroupsPreservedForSize = pathOptimizationStats.translationGroupsPreservedForSize
        reportData.scaledGroupsFlattened = pathOptimizationStats.scaledGroupsFlattened
        reportData.scaledPaths = pathOptimizationStats.scaledPaths
        reportData.scaledStrokeWidths = pathOptimizationStats.scaledStrokeWidths
        reportData.scaleGroupsPreservedForSize = pathOptimizationStats.scaleGroupsPreservedForSize
        reportData.nonUniformScaleGroupsFlattened = pathOptimizationStats.nonUniformScaleGroupsFlattened
        reportData.nonUniformScaledPaths = pathOptimizationStats.nonUniformScaledPaths
        reportData.nonUniformScaleGroupsPreservedForSize = pathOptimizationStats.nonUniformScaleGroupsPreservedForSize
        reportData.rotationGroupsFlattened = pathOptimizationStats.rotationGroupsFlattened
        reportData.rotatedPaths = pathOptimizationStats.rotatedPaths
        reportData.rotationGroupsPreservedForSize = pathOptimizationStats.rotationGroupsPreservedForSize
        reportData.identityTransformAttributesRemoved = pathOptimizationStats.identityTransformAttributesRemoved
        reportData.nestedTransformGroupsComposed = pathOptimizationStats.nestedTransformGroupsComposed
        reportData.transformAttributesCanonicalized = pathOptimizationStats.transformAttributesCanonicalized
        reportData.zeroPivotAttributesRemoved = pathOptimizationStats.zeroPivotAttributesRemoved
        reportData.transformGroupsReordered = pathOptimizationStats.transformGroupsReordered
        reportData.optimizerIdempotenceVerified = pathOptimizationStats.optimizerIdempotenceVerified
        reportData.optimizerReachedFixedPoint = pathOptimizationStats.optimizerReachedFixedPoint
        reportData.optimizerStabilityPasses = pathOptimizationStats.optimizerStabilityPasses
        reportData.optimizerValidationNanos = pathOptimizationStats.optimizerValidationNanos
        reportData.optimizerProductionPassNanos = pathOptimizationStats.optimizerProductionPassNanos
        reportData.optimizerIdempotencePassNanos = pathOptimizationStats.optimizerIdempotencePassNanos
        reportData.optimizerFixedPointPassNanos = pathOptimizationStats.optimizerFixedPointPassNanos
        reportData.optimizerValidationPathCacheHits = pathOptimizationStats.optimizerValidationPathCacheHits
        reportData.optimizerValidationPathCacheMisses = pathOptimizationStats.optimizerValidationPathCacheMisses
        reportData.optimizerIdempotencePathSyntaxNanos =
            pathOptimizationStats.idempotenceProfiling.pathSyntaxNanos
        reportData.optimizerIdempotencePathTokenizationNormalizationNanos =
            pathOptimizationStats.idempotenceProfiling.pathTokenizationNormalizationNanos
        reportData.optimizerIdempotencePathGeometryCleanupNanos =
            pathOptimizationStats.idempotenceProfiling.pathGeometryCleanupNanos
        reportData.optimizerIdempotencePathCommandMinimizationNanos =
            pathOptimizationStats.idempotenceProfiling.pathCommandMinimizationNanos
        reportData.optimizerIdempotencePathNumericSerializationNanos =
            pathOptimizationStats.idempotenceProfiling.pathNumericSerializationNanos
        reportData.optimizerIdempotenceColorNormalizationNanos =
            pathOptimizationStats.idempotenceProfiling.colorNormalizationNanos
        reportData.optimizerIdempotencePruningGroupCleanupNanos =
            pathOptimizationStats.idempotenceProfiling.pruningAndGroupCleanupNanos
        reportData.optimizerIdempotenceTransformOptimizationNanos =
            pathOptimizationStats.idempotenceProfiling.transformOptimizationNanos
        reportData.optimizerIdempotenceDeduplicationMergeNanos =
            pathOptimizationStats.idempotenceProfiling.deduplicationAndMergeNanos
        reportData.optimizerIdempotenceNumericCleanupNanos =
            pathOptimizationStats.idempotenceProfiling.numericCleanupNanos
        reportData.optimizerIdempotenceNearIntegerSnappingNanos =
            pathOptimizationStats.idempotenceProfiling.nearIntegerSnappingNanos
        reportData.optimizerIdempotenceDecimalCanonicalizationNanos =
            pathOptimizationStats.idempotenceProfiling.decimalCanonicalizationNanos
        reportData.optimizerIdempotenceDecimalTokenizationNanos =
            pathOptimizationStats.idempotenceProfiling.decimalTokenizationNanos
        reportData.optimizerIdempotenceDecimalRebuildNanos =
            pathOptimizationStats.idempotenceProfiling.decimalRebuildNanos
        reportData.optimizerIdempotenceDecimalReoptimizationNanos =
            pathOptimizationStats.idempotenceProfiling.decimalReoptimizationNanos
        reportData.optimizerIdempotenceDecimalValidationNanos =
            pathOptimizationStats.idempotenceProfiling.decimalValidationNanos
        reportData.optimizerIdempotenceDecimalPathsExamined =
            pathOptimizationStats.idempotenceProfiling.decimalPathsExamined
        reportData.optimizerIdempotenceFinalFormattingNanos =
            pathOptimizationStats.idempotenceProfiling.finalFormattingNanos
        reportData.optimizerIdempotenceEqualityComparisonNanos =
            pathOptimizationStats.idempotenceProfiling.equalityComparisonNanos
        reportData.optimizerIdempotencePathsExamined =
            pathOptimizationStats.idempotenceProfiling.pathsExamined
        reportData.optimizerIdempotenceFinalPassStablePathsRegistered =
            pathOptimizationStats.idempotenceProfiling.finalPassStablePathsRegistered
        reportData.optimizerIdempotencePathCacheHits =
            pathOptimizationStats.idempotenceProfiling.pathCacheHits
        reportData.optimizerIdempotenceStableOutputCacheHits =
            pathOptimizationStats.idempotenceProfiling.stableOutputCacheHits
        reportData.optimizerIdempotenceRegularCacheHits =
            pathOptimizationStats.idempotenceProfiling.regularCacheHits
        reportData.optimizerIdempotencePathCacheMisses =
            pathOptimizationStats.idempotenceProfiling.pathCacheMisses
        reportData.optimizerIdempotenceXmlCharactersBefore =
            pathOptimizationStats.idempotenceProfiling.xmlCharactersBefore
        reportData.optimizerIdempotenceXmlCharactersAfter =
            pathOptimizationStats.idempotenceProfiling.xmlCharactersAfter
        reportData.optimizerValidationPasses = pathOptimizationStats.optimizerValidationPasses
        reportData.optimizerFirstPassChangedXml = pathOptimizationStats.optimizerFirstPassChangedXml
        reportData.optimizerSecondPassChangedXml = pathOptimizationStats.optimizerSecondPassChangedXml
        reportData.optimizerThirdPassChangedXml = pathOptimizationStats.optimizerThirdPassChangedXml
        val g315Trial = pathOptimizationStats.g315GuardedProductionTrial
        reportData.g315TrialAttempted = g315Trial.attempted
        reportData.g315CandidateChanged = g315Trial.candidateChanged
        reportData.g315PathsExamined = g315Trial.pathsExamined
        reportData.g315PathsChanged = g315Trial.pathsChanged
        reportData.g315GeometryComparisons = g315Trial.geometryComparisons
        reportData.g315GeometryMismatchCount = g315Trial.geometryMismatchCount
        reportData.g315ExactShortCircuitCount = g315Trial.exactShortCircuitCount
        reportData.g315FallbackBidirectionalCount = g315Trial.fallbackBidirectionalCount
        reportData.g315ComparatorFailureCount = g315Trial.comparatorFailureCount
        reportData.g315MatchedIndependentSecondPass = g315Trial.matchedIndependentSecondPass
        reportData.g315FixedPointVerified = g315Trial.fixedPointVerified
        reportData.g315FinalValidationPassed = g315Trial.finalValidationPassed
        reportData.g315GuardAccepted = g315Trial.guardAccepted
        reportData.g315GuardRejected = g315Trial.guardRejected
        reportData.g315CharactersBefore = g315Trial.charactersBefore
        reportData.g315CharactersAfter = g315Trial.charactersAfter
        reportData.g315CharactersSaved = g315Trial.charactersSaved
        reportData.g315CharactersAdded = g315Trial.charactersAdded
        reportData.g315CandidateNanos = g315Trial.candidateNanos
        reportData.g315ComparatorNanos = g315Trial.comparatorNanos
        reportData.g315GuardNanos = g315Trial.guardNanos
        reportData.g315RejectionReason = g315Trial.rejectionReason
        reportData.finalOutputValidationPassed = pathOptimizationStats.finalOutputValidationPassed
        reportData.finalOutputValidationNanos = pathOptimizationStats.finalOutputValidationNanos
        reportData.validatedPathDataCount = pathOptimizationStats.validatedPathDataCount
        reportData.invalidPathDataCount = pathOptimizationStats.invalidPathDataCount
        reportData.nonFiniteNumberCount = pathOptimizationStats.nonFiniteNumberCount
        reportData.malformedStructureCount = pathOptimizationStats.malformedStructureCount
        reportData.invalidViewportCount = pathOptimizationStats.invalidViewportCount
        reportData.unsupportedOutputConstructCount = pathOptimizationStats.unsupportedOutputConstructCount
        fun validationStageReport(
            stage: SvgPathDataOptimizer.ValidationSnapshotStats
        ): SvgValidationStageReport = SvgValidationStageReport(
            attempted = stage.attempted,
            passed = stage.passed,
            validatedPathDataCount = stage.validatedPathDataCount,
            invalidPathDataCount = stage.invalidPathDataCount,
            nonFiniteNumberCount = stage.nonFiniteNumberCount,
            malformedStructureCount = stage.malformedStructureCount,
            invalidViewportCount = stage.invalidViewportCount,
            unsupportedOutputConstructCount = stage.unsupportedOutputConstructCount,
            witness = stage.witness
        )
        val validationClassification = pathOptimizationStats.validationClassification
        reportData.h16InputValidation = validationStageReport(validationClassification.input)
        reportData.h16Pass1Validation = validationStageReport(validationClassification.pass1)
        reportData.h16Pass2Validation = validationStageReport(validationClassification.pass2)
        reportData.h16Pass3Validation = validationStageReport(validationClassification.pass3)
        reportData.h16SelectedValidation = validationStageReport(validationClassification.selected)
        reportData.shorterCommandFormsSelected = pathOptimizationStats.shorterCommandFormsSelected
        reportData.relativeCommandsSelected = pathOptimizationStats.relativeCommandsSelected
        reportData.axisCommandsSelected = pathOptimizationStats.axisCommandsSelected
        reportData.sourceSvgCharacters = svg.length
        reportData.optimizedXmlCharactersBefore = pathOptimizationStats.xmlCharactersBefore
        reportData.optimizedXmlCharactersAfter = pathOptimizationStats.xmlCharactersAfter
        reportData.styleResolutionNanos = styleResolutionNanos
        reportData.svgParsingNanos = svgParsingNanos
        reportData.treeConversionNanos = treeConversionNanos
        reportData.outputOptimizationNanos = outputOptimizationNanos
        reportData.optimizationPathSyntaxNanos = pathOptimizationStats.pathSyntaxOptimizationNanos
        reportData.optimizationPathTokenizationNanos = pathOptimizationStats.pathTokenizationNormalizationNanos
        reportData.optimizationPathGeometryCleanupNanos = pathOptimizationStats.pathGeometryCleanupNanos
        reportData.optimizationPathRedundantSegmentCleanupNanos = pathOptimizationStats.pathRedundantSegmentCleanupNanos
        reportData.optimizationPathArcCleanupNanos = pathOptimizationStats.pathArcCleanupNanos
        reportData.optimizationPathCurveSimplificationNanos = pathOptimizationStats.pathCurveSimplificationNanos
        reportData.optimizationCurveCubicToQuadraticNanos = pathOptimizationStats.pathCurveSimplificationProfiling.cubicToQuadraticNanos
        reportData.optimizationCurveCubicParseSetupNanos = pathOptimizationStats.pathCurveSimplificationProfiling.cubicParseSetupNanos
        reportData.optimizationCurveCubicScanNanos = pathOptimizationStats.pathCurveSimplificationProfiling.cubicScanNanos
        reportData.optimizationCurveCubicRebuildValidationNanos = pathOptimizationStats.pathCurveSimplificationProfiling.cubicRebuildValidationNanos
        reportData.optimizationCurveCubicRebuildNanos = pathOptimizationStats.pathCurveSimplificationProfiling.cubicRebuildNanos
        reportData.optimizationCurveCubicValidationNanos = pathOptimizationStats.pathCurveSimplificationProfiling.cubicValidationNanos
        reportData.optimizationCurveStraightBezierNanos = pathOptimizationStats.pathCurveSimplificationProfiling.straightBezierNanos
        reportData.optimizationCurveStraightParseSetupNanos = pathOptimizationStats.pathCurveSimplificationProfiling.straightParseSetupNanos
        reportData.optimizationCurveStraightScanNanos = pathOptimizationStats.pathCurveSimplificationProfiling.straightScanNanos
        reportData.optimizationCurveStraightRebuildValidationNanos = pathOptimizationStats.pathCurveSimplificationProfiling.straightRebuildValidationNanos
        reportData.optimizationCurveStraightRebuildNanos = pathOptimizationStats.pathCurveSimplificationProfiling.straightRebuildNanos
        reportData.optimizationCurveStraightValidationNanos = pathOptimizationStats.pathCurveSimplificationProfiling.straightValidationNanos
        reportData.optimizationCurveParseCalls = pathOptimizationStats.pathCurveSimplificationProfiling.parseCalls
        reportData.optimizationCurveDuplicateParseInputs = pathOptimizationStats.pathCurveSimplificationProfiling.duplicateParseInputs
        reportData.optimizationCurveSecondPassReparsedUnchangedInput = pathOptimizationStats.pathCurveSimplificationProfiling.secondPassReparsedUnchangedInput
        reportData.optimizationCurveCubicChangedPaths = pathOptimizationStats.pathCurveSimplificationProfiling.cubicChangedPaths
        reportData.optimizationCurveStraightChangedPaths = pathOptimizationStats.pathCurveSimplificationProfiling.straightChangedPaths
        reportData.optimizationCurveRebuildAttempts = pathOptimizationStats.pathCurveSimplificationProfiling.rebuildAttempts
        reportData.optimizationCurveRebuildNoOpResults = pathOptimizationStats.pathCurveSimplificationProfiling.rebuildNoOpResults
        reportData.optimizationCurveValidationCalls = pathOptimizationStats.pathCurveSimplificationProfiling.validationCalls
        reportData.optimizationCurveValidationAccepted = pathOptimizationStats.pathCurveSimplificationProfiling.validationAccepted
        reportData.optimizationCurveValidationRejected = pathOptimizationStats.pathCurveSimplificationProfiling.validationRejected
        reportData.optimizationCurveRebuildRejectedForSize = pathOptimizationStats.pathCurveSimplificationProfiling.rebuildRejectedForSize
        reportData.optimizationPathCollinearConsolidationNanos = pathOptimizationStats.pathCollinearConsolidationNanos
        reportData.optimizationPathCommandMinimizationNanos = pathOptimizationStats.pathCommandMinimizationNanos
        reportData.optimizationPathCommandLocalShorteningNanos = pathOptimizationStats.pathCommandLocalShorteningNanos
        reportData.optimizationPathCommandLocalParseSetupNanos = pathOptimizationStats.pathCommandLocalProfiling.parseSetupNanos
        reportData.optimizationPathCommandLocalAbsoluteRelativeCandidateNanos = pathOptimizationStats.pathCommandLocalProfiling.absoluteRelativeCandidateNanos
        reportData.optimizationPathCommandLocalAxisCandidateNanos = pathOptimizationStats.pathCommandLocalProfiling.axisCandidateNanos
        reportData.optimizationPathCommandLocalSmoothShorthandCandidateNanos = pathOptimizationStats.pathCommandLocalProfiling.smoothShorthandCandidateNanos
        reportData.optimizationPathCommandLocalEncodingSelectionNanos = pathOptimizationStats.pathCommandLocalProfiling.encodingSelectionNanos
        reportData.optimizationPathCommandLocalNumericSerializationNanos = pathOptimizationStats.pathCommandLocalProfiling.numericSerializationNanos
        reportData.optimizationPathCommandLocalSeparatorCalculationNanos = pathOptimizationStats.pathCommandLocalProfiling.separatorCalculationNanos
        reportData.optimizationPathCommandLocalCommandOmissionNanos = pathOptimizationStats.pathCommandLocalProfiling.commandOmissionNanos
        reportData.optimizationPathCommandLocalStringConstructionNanos = pathOptimizationStats.pathCommandLocalProfiling.stringConstructionNanos
        reportData.optimizationPathCommandLocalWinnerSelectionNanos = pathOptimizationStats.pathCommandLocalProfiling.winnerSelectionNanos
        reportData.optimizationPathCommandLocalStateBookkeepingNanos = pathOptimizationStats.pathCommandLocalProfiling.stateBookkeepingNanos
        reportData.optimizationPathCommandLocalNumericSerializationCalls = pathOptimizationStats.pathCommandLocalProfiling.numericSerializationCalls
        reportData.optimizationPathCommandLocalNumericSerializationCacheHits = pathOptimizationStats.pathCommandLocalProfiling.numericSerializationCacheHits
        reportData.optimizationPathCommandLocalNumericSerializationUniqueValues = pathOptimizationStats.pathCommandLocalProfiling.numericSerializationUniqueValues
        reportData.optimizationPathCommandGlobalParseSetupNanos = pathOptimizationStats.pathCommandGlobalParseSetupNanos
        reportData.optimizationPathCommandGlobalCandidateGenerationNanos = pathOptimizationStats.pathCommandGlobalCandidateGenerationNanos
        reportData.optimizationPathCommandGlobalDynamicProgrammingNanos = pathOptimizationStats.pathCommandGlobalDynamicProgrammingNanos
        reportData.optimizationPathCommandGlobalTransitionEvaluationNanos = pathOptimizationStats.pathCommandGlobalProfiling.transitionEvaluationNanos
        reportData.optimizationPathCommandGlobalSeparatorOmissionCostNanos = pathOptimizationStats.pathCommandGlobalProfiling.separatorOmissionCostNanos
        reportData.optimizationPathCommandGlobalSegmentEncodingNanos = pathOptimizationStats.pathCommandGlobalProfiling.segmentEncodingNanos
        reportData.optimizationPathCommandGlobalStateCreationNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateCreationNanos
        reportData.optimizationPathCommandGlobalBestStateComparisonNanos = pathOptimizationStats.pathCommandGlobalProfiling.bestStateComparisonNanos
        reportData.optimizationPathCommandGlobalReconstructionNanos = pathOptimizationStats.pathCommandGlobalProfiling.reconstructionNanos
        reportData.optimizationPathCommandGlobalStateKeyCreationNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateKeyCreationNanos
        reportData.optimizationPathCommandGlobalStateKeyFieldPreparationNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateKeyFieldPreparationNanos
        reportData.optimizationPathCommandGlobalStateKeyPreviousCommandNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateKeyPreviousCommandNanos
        reportData.optimizationPathCommandGlobalStateKeyPreviousNumberNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateKeyPreviousNumberNanos
        reportData.optimizationPathCommandGlobalStateKeyAxisDirectionNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateKeyAxisDirectionNanos
        reportData.optimizationPathCommandGlobalStateKeyAllocationNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateKeyAllocationNanos
        reportData.optimizationPathCommandGlobalStateStringConcatenationNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateStringConcatenationNanos
        reportData.optimizationPathCommandGlobalStateMetadataPropagationNanos = pathOptimizationStats.pathCommandGlobalProfiling.stateMetadataPropagationNanos
        reportData.optimizationPathCommandGlobalStatePathAllocationNanos = pathOptimizationStats.pathCommandGlobalProfiling.statePathAllocationNanos
        reportData.optimizationPathCommandGlobalBestStateMapLookupNanos = pathOptimizationStats.pathCommandGlobalProfiling.bestStateMapLookupNanos
        reportData.optimizationPathCommandGlobalBestStateDecisionNanos = pathOptimizationStats.pathCommandGlobalProfiling.bestStateDecisionNanos
        reportData.optimizationPathCommandGlobalBestStateReplacementNanos = pathOptimizationStats.pathCommandGlobalProfiling.bestStateReplacementNanos
        reportData.optimizationPathCommandGlobalStateMapLookupCalls = pathOptimizationStats.pathCommandGlobalProfiling.stateMapLookupCalls
        reportData.optimizationPathCommandGlobalStateMapLookupHits = pathOptimizationStats.pathCommandGlobalProfiling.stateMapLookupHits
        reportData.optimizationPathCommandGlobalStateMapLookupMisses = pathOptimizationStats.pathCommandGlobalProfiling.stateMapLookupMisses
        reportData.optimizationPathCommandGlobalStateMapInsertions = pathOptimizationStats.pathCommandGlobalProfiling.stateMapInsertions
        reportData.optimizationPathCommandGlobalStateMapReplacements = pathOptimizationStats.pathCommandGlobalProfiling.stateMapReplacements
        reportData.optimizationPathCommandGlobalSegmentEncodingRequests = pathOptimizationStats.pathCommandGlobalProfiling.segmentEncodingRequests
        reportData.optimizationPathCommandGlobalSegmentEncodingCacheHits = pathOptimizationStats.pathCommandGlobalProfiling.segmentEncodingCacheHits
        reportData.optimizationPathCommandGlobalSegmentEncodingUniqueKeys = pathOptimizationStats.pathCommandGlobalProfiling.segmentEncodingUniqueKeys
        reportData.optimizationPathNumericSerializationNanos = pathOptimizationStats.pathNumericSerializationNanos
        reportData.optimizationColorNormalizationNanos = pathOptimizationStats.colorNormalizationNanos
        reportData.optimizationPruningCleanupNanos = pathOptimizationStats.pruningAndGroupCleanupNanos
        reportData.optimizationTransformsNanos = pathOptimizationStats.transformOptimizationNanos
        reportData.optimizationTransformIdentityCompositionNanos = pathOptimizationStats.transformIdentityCompositionNanos
        reportData.optimizationTransformFactoringFlatteningNanos = pathOptimizationStats.transformFactoringFlatteningNanos
        reportData.optimizationTransformScaleFlatteningNanos = pathOptimizationStats.transformScaleFlatteningNanos
        reportData.optimizationTransformUniformScaleFlatteningNanos = pathOptimizationStats.transformUniformScaleFlatteningNanos
        reportData.optimizationTransformUniformScaleGroupDiscoveryNanos = pathOptimizationStats.transformUniformScaleProfiling.groupDiscoveryNanos
        reportData.optimizationTransformUniformScaleEligibilityChecksNanos = pathOptimizationStats.transformUniformScaleProfiling.eligibilityChecksNanos
        reportData.optimizationTransformUniformScalePathScalingNanos = pathOptimizationStats.transformUniformScaleProfiling.pathScalingNanos
        reportData.optimizationTransformUniformScalePathParseTokenizeNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathParseTokenizeNanos
        reportData.optimizationTransformUniformScalePathNumericParseNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathNumericParseNanos
        reportData.optimizationTransformUniformScalePathCoordinateMathNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathCoordinateMathNanos
        reportData.optimizationTransformUniformScalePathArcHandlingNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathArcHandlingNanos
        reportData.optimizationTransformUniformScalePathNumberFormattingNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathNumberFormattingNanos
        reportData.optimizationTransformUniformScalePathReconstructionNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathReconstructionNanos
        reportData.optimizationTransformUniformScalePathNormalizationNanos = pathOptimizationStats.transformUniformScaleProfiling.scalePathNormalizationNanos
        reportData.optimizationTransformUniformScalePostScaleP6Attempts = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6Attempts
        reportData.optimizationTransformUniformScalePostScaleP6Accepted = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6Accepted
        reportData.optimizationTransformUniformScalePostScaleP6Fallbacks = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6Fallbacks
        reportData.optimizationTransformUniformScalePostScaleP6ParserFallbacks = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6ParserFallbacks
        reportData.optimizationTransformUniformScalePostScaleP6InternalFallbacks = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6InternalFallbacks
        reportData.optimizationTransformUniformScalePostScaleP6OptimizationNanos = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6OptimizationNanos
        reportData.optimizationTransformUniformScalePostScaleP6ParserValidationNanos = pathOptimizationStats.transformUniformScaleProfiling.postScaleP6ParserValidationNanos
        reportData.optimizationTransformUniformScalePostScaleFullFallbackNanos = pathOptimizationStats.transformUniformScaleProfiling.postScaleFullFallbackNanos
        reportData.optimizationTransformUniformScaleStrokeAdjustmentNanos = pathOptimizationStats.transformUniformScaleProfiling.strokeAdjustmentNanos
        reportData.optimizationTransformUniformScaleCanonicalizationCostingNanos = pathOptimizationStats.transformUniformScaleProfiling.canonicalizationCostingNanos
        reportData.optimizationTransformUniformScaleXmlReplacementNanos = pathOptimizationStats.transformUniformScaleProfiling.xmlReplacementNanos
        reportData.optimizationTransformUniformScaleCandidatesConsidered = pathOptimizationStats.transformUniformScaleProfiling.candidatesConsidered
        reportData.optimizationTransformUniformScaleCandidatesRejected = pathOptimizationStats.transformUniformScaleProfiling.candidatesRejected
        reportData.optimizationTransformUniformScaleProposalsAccepted = pathOptimizationStats.transformUniformScaleProfiling.proposalsAccepted
        reportData.optimizationTransformNonUniformScaleFlatteningNanos = pathOptimizationStats.transformNonUniformScaleFlatteningNanos
        reportData.optimizationTransformRotationTranslationNanos = pathOptimizationStats.transformRotationTranslationNanos
        reportData.optimizationTransformCanonicalizationNanos = pathOptimizationStats.transformCanonicalizationNanos
        reportData.optimizationDeduplicationNanos = pathOptimizationStats.deduplicationAndMergeNanos
        reportData.optimizationNumericCleanupNanos = pathOptimizationStats.numericCleanupNanos
        reportData.optimizationNearIntegerSnappingNanos = pathOptimizationStats.nearIntegerSnappingNanos
        reportData.optimizationDecimalCanonicalizationNanos = pathOptimizationStats.decimalCanonicalizationNanos
        reportData.optimizationDecimalTokenizationNanos = pathOptimizationStats.decimalTokenizationNanos
        reportData.optimizationDecimalRebuildNanos = pathOptimizationStats.decimalRebuildNanos
        reportData.optimizationDecimalReoptimizationNanos = pathOptimizationStats.decimalReoptimizationNanos
        reportData.optimizationDecimalValidationNanos = pathOptimizationStats.decimalValidationNanos
        reportData.optimizationDecimalPathsExamined = pathOptimizationStats.decimalPathsExamined

        reportData.i2Pass1PathSyntaxStableInputs = pathOptimizationStats.i2PathSyntaxStableInputs
        reportData.i2Pass1PathSyntaxStableInputNanos =
            pathOptimizationStats.i2PathSyntaxStableInputNanos
        reportData.i2Pass2PathSyntaxStableInputs =
            pathOptimizationStats.idempotenceProfiling.i2PathSyntaxStableInputs
        reportData.i2Pass2PathSyntaxStableInputNanos =
            pathOptimizationStats.idempotenceProfiling.i2PathSyntaxStableInputNanos

        reportData.i2Pass1DecimalShadowPathsCompared =
            pathOptimizationStats.i2DecimalShadowPathsCompared
        reportData.i2Pass1DecimalShadowByteIdentical =
            pathOptimizationStats.i2DecimalShadowByteIdentical
        reportData.i2Pass1DecimalShadowDifferent =
            pathOptimizationStats.i2DecimalShadowDifferent
        reportData.i2Pass1DecimalShadowFastShorter =
            pathOptimizationStats.i2DecimalShadowFastShorter
        reportData.i2Pass1DecimalShadowReferenceShorter =
            pathOptimizationStats.i2DecimalShadowReferenceShorter
        reportData.i2Pass1DecimalShadowEqualLengthDifferent =
            pathOptimizationStats.i2DecimalShadowEqualLengthDifferent
        reportData.i2Pass1DecimalShadowFastInvalid =
            pathOptimizationStats.i2DecimalShadowFastInvalid
        reportData.i2Pass1DecimalShadowFastNonFixed =
            pathOptimizationStats.i2DecimalShadowFastNonFixed
        reportData.i2Pass1DecimalShadowCharacterDeltaVsReference =
            pathOptimizationStats.i2DecimalShadowCharacterDeltaVsReference
        reportData.i2Pass1DecimalShadowNanos =
            pathOptimizationStats.i2DecimalShadowNanos

        reportData.i2Pass2DecimalShadowPathsCompared =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowPathsCompared
        reportData.i2Pass2DecimalShadowByteIdentical =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowByteIdentical
        reportData.i2Pass2DecimalShadowDifferent =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowDifferent
        reportData.i2Pass2DecimalShadowFastShorter =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowFastShorter
        reportData.i2Pass2DecimalShadowReferenceShorter =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowReferenceShorter
        reportData.i2Pass2DecimalShadowEqualLengthDifferent =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowEqualLengthDifferent
        reportData.i2Pass2DecimalShadowFastInvalid =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowFastInvalid
        reportData.i2Pass2DecimalShadowFastNonFixed =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowFastNonFixed
        reportData.i2Pass2DecimalShadowCharacterDeltaVsReference =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowCharacterDeltaVsReference
        reportData.i2Pass2DecimalShadowNanos =
            pathOptimizationStats.idempotenceProfiling.i2DecimalShadowNanos

        reportData.i3Pass1DecimalFastPathAccepted =
            pathOptimizationStats.i3DecimalFastPathAccepted
        reportData.i3Pass1DecimalFallbackInvalid =
            pathOptimizationStats.i3DecimalFallbackInvalid
        reportData.i3Pass1DecimalFallbackNonFixed =
            pathOptimizationStats.i3DecimalFallbackNonFixed
        reportData.i3Pass1DecimalFastPathCheckNanos =
            pathOptimizationStats.i3DecimalFastPathCheckNanos

        reportData.i3Pass2DecimalFastPathAccepted =
            pathOptimizationStats.idempotenceProfiling.i3DecimalFastPathAccepted
        reportData.i3Pass2DecimalFallbackInvalid =
            pathOptimizationStats.idempotenceProfiling.i3DecimalFallbackInvalid
        reportData.i3Pass2DecimalFallbackNonFixed =
            pathOptimizationStats.idempotenceProfiling.i3DecimalFallbackNonFixed
        reportData.i3Pass2DecimalFastPathCheckNanos =
            pathOptimizationStats.idempotenceProfiling.i3DecimalFastPathCheckNanos

        reportData.i41Pass2CertificatePredictedFixed =
            pathOptimizationStats.idempotenceProfiling.i41CertificatePredictedFixed
        reportData.i41Pass2CertificateTruePositive =
            pathOptimizationStats.idempotenceProfiling.i41CertificateTruePositive
        reportData.i41Pass2CertificateFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i41CertificateFalsePositive
        reportData.i41Pass2CertificateFalseNegative =
            pathOptimizationStats.idempotenceProfiling.i41CertificateFalseNegative
        reportData.i41Pass2CertificateTrueNegative =
            pathOptimizationStats.idempotenceProfiling.i41CertificateTrueNegative
        reportData.i41Pass2CertificateCheckNanos =
            pathOptimizationStats.idempotenceProfiling.i41CertificateCheckNanos
        reportData.i41Pass2PotentialAvoidableOptimizerNanos =
            pathOptimizationStats.idempotenceProfiling.i41PotentialAvoidableOptimizerNanos
        reportData.i41Pass2FalsePositiveOptimizerNanos =
            pathOptimizationStats.idempotenceProfiling.i41FalsePositiveOptimizerNanos
        reportData.i41Pass2RejectedLexical =
            pathOptimizationStats.idempotenceProfiling.i41RejectedLexical
        reportData.i41Pass2RejectedNumericSpelling =
            pathOptimizationStats.idempotenceProfiling.i41RejectedNumericSpelling
        reportData.i41Pass2RejectedWhitespace =
            pathOptimizationStats.idempotenceProfiling.i41RejectedWhitespace
        reportData.i41Pass2RejectedComplexCommandFamily =
            pathOptimizationStats.idempotenceProfiling.i41RejectedComplexCommandFamily
        reportData.i41Pass2RejectedExplicitRepeat =
            pathOptimizationStats.idempotenceProfiling.i41RejectedExplicitRepeat

        reportData.i42Pass2ProvenanceExcluded =
            pathOptimizationStats.idempotenceProfiling.i42ProvenanceExcluded
        reportData.i42Pass2ProvenanceExcludedActuallyFixed =
            pathOptimizationStats.idempotenceProfiling.i42ProvenanceExcludedActuallyFixed
        reportData.i42Pass2ProvenancePreventedFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i42ProvenancePreventedFalsePositive
        reportData.i42Pass2ProvenanceExcludedOptimizerNanos =
            pathOptimizationStats.idempotenceProfiling.i42ProvenanceExcludedOptimizerNanos
        reportData.i42Pass2PreventedFalsePositiveOptimizerNanos =
            pathOptimizationStats.idempotenceProfiling.i42PreventedFalsePositiveOptimizerNanos
        reportData.i42Pass2PreventedChangedSyntaxNormalization =
            pathOptimizationStats.idempotenceProfiling.i42PreventedChangedSyntaxNormalization
        reportData.i42Pass2PreventedChangedGeometryCleanup =
            pathOptimizationStats.idempotenceProfiling.i42PreventedChangedGeometryCleanup
        reportData.i42Pass2PreventedChangedLocalShortening =
            pathOptimizationStats.idempotenceProfiling.i42PreventedChangedLocalShortening
        reportData.i42Pass2PreventedChangedGlobalCommand =
            pathOptimizationStats.idempotenceProfiling.i42PreventedChangedGlobalCommand
        reportData.i42Pass2PreventedChangedGlobalNumeric =
            pathOptimizationStats.idempotenceProfiling.i42PreventedChangedGlobalNumeric
        reportData.i42Pass2PreventedChangedOther =
            pathOptimizationStats.idempotenceProfiling.i42PreventedChangedOther

        reportData.i43Pass2ComplexCandidatesExamined =
            pathOptimizationStats.idempotenceProfiling.i43ComplexCandidatesExamined
        reportData.i43Pass2ComplexPredictedFixed =
            pathOptimizationStats.idempotenceProfiling.i43ComplexPredictedFixed
        reportData.i43Pass2ComplexTruePositive =
            pathOptimizationStats.idempotenceProfiling.i43ComplexTruePositive
        reportData.i43Pass2ComplexFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i43ComplexFalsePositive
        reportData.i43Pass2ComplexFalseNegative =
            pathOptimizationStats.idempotenceProfiling.i43ComplexFalseNegative
        reportData.i43Pass2ComplexTrueNegative =
            pathOptimizationStats.idempotenceProfiling.i43ComplexTrueNegative
        reportData.i43Pass2ComplexCheckNanos =
            pathOptimizationStats.idempotenceProfiling.i43ComplexCheckNanos
        reportData.i43Pass2ComplexPotentialAvoidableOptimizerNanos =
            pathOptimizationStats.idempotenceProfiling.i43ComplexPotentialAvoidableOptimizerNanos
        reportData.i43Pass2ComplexFalsePositiveOptimizerNanos =
            pathOptimizationStats.idempotenceProfiling.i43ComplexFalsePositiveOptimizerNanos
        reportData.i43Pass2RejectedReflectiveShorthand =
            pathOptimizationStats.idempotenceProfiling.i43RejectedReflectiveShorthand
        reportData.i43Pass2RejectedNumericSpelling =
            pathOptimizationStats.idempotenceProfiling.i43RejectedNumericSpelling
        reportData.i43Pass2RejectedExplicitRepeat =
            pathOptimizationStats.idempotenceProfiling.i43RejectedExplicitRepeat
        reportData.i43Pass2RejectedProvenance =
            pathOptimizationStats.idempotenceProfiling.i43RejectedProvenance
        reportData.i43Pass2CubicPredicted =
            pathOptimizationStats.idempotenceProfiling.i43CubicPredicted
        reportData.i43Pass2CubicTruePositive =
            pathOptimizationStats.idempotenceProfiling.i43CubicTruePositive
        reportData.i43Pass2CubicFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i43CubicFalsePositive
        reportData.i43Pass2QuadraticPredicted =
            pathOptimizationStats.idempotenceProfiling.i43QuadraticPredicted
        reportData.i43Pass2QuadraticTruePositive =
            pathOptimizationStats.idempotenceProfiling.i43QuadraticTruePositive
        reportData.i43Pass2QuadraticFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i43QuadraticFalsePositive
        reportData.i43Pass2ArcPredicted =
            pathOptimizationStats.idempotenceProfiling.i43ArcPredicted
        reportData.i43Pass2ArcTruePositive =
            pathOptimizationStats.idempotenceProfiling.i43ArcTruePositive
        reportData.i43Pass2ArcFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i43ArcFalsePositive
        reportData.i43Pass2MixedPredicted =
            pathOptimizationStats.idempotenceProfiling.i43MixedPredicted
        reportData.i43Pass2MixedTruePositive =
            pathOptimizationStats.idempotenceProfiling.i43MixedTruePositive
        reportData.i43Pass2MixedFalsePositive =
            pathOptimizationStats.idempotenceProfiling.i43MixedFalsePositive
        reportData.i43Pass2FalsePositiveGeometryCleanup =
            pathOptimizationStats.idempotenceProfiling.i43FalsePositiveGeometryCleanup
        reportData.i43Pass2FalsePositiveLocalShortening =
            pathOptimizationStats.idempotenceProfiling.i43FalsePositiveLocalShortening
        reportData.i43Pass2FalsePositiveGlobalCommand =
            pathOptimizationStats.idempotenceProfiling.i43FalsePositiveGlobalCommand
        reportData.i43Pass2FalsePositiveGlobalNumeric =
            pathOptimizationStats.idempotenceProfiling.i43FalsePositiveGlobalNumeric
        reportData.i43Pass2FalsePositiveOther =
            pathOptimizationStats.idempotenceProfiling.i43FalsePositiveOther

        reportData.optimizationPathCacheHits = pathOptimizationStats.pathOptimizationCacheHits
        reportData.optimizationPathCacheMisses = pathOptimizationStats.pathOptimizationCacheMisses
        reportData.optimizationFormattingNanos = pathOptimizationStats.finalFormattingNanos
        reportData.optimizationPathSyntaxCharactersSaved = pathOptimizationStats.pathSyntaxCharactersSaved
        reportData.optimizationPruningCleanupCharactersSaved = pathOptimizationStats.pruningCleanupCharactersSaved
        reportData.optimizationTransformCharactersSaved = pathOptimizationStats.transformCharactersSaved
        reportData.optimizationDeduplicationCharactersSaved = pathOptimizationStats.deduplicationCharactersSaved
        reportData.optimizationNumericCleanupCharactersSaved = pathOptimizationStats.numericCleanupCharactersSaved
        reportData.optimizationFormattingCharactersSaved = pathOptimizationStats.formattingCharactersSaved

        reportData.h21PathSyntaxCharacterDelta = pathOptimizationStats.h21PathSyntaxCharacterDelta
        reportData.h22PathDataSyntaxCharacterDelta = pathOptimizationStats.h22PathDataSyntaxCharacterDelta
        reportData.h22ColorNormalizationCharacterDelta =
            pathOptimizationStats.h22ColorNormalizationCharacterDelta
        reportData.h23SyntaxNormalizationCharacterDelta = pathOptimizationStats.h23SyntaxNormalizationCharacterDelta
        reportData.h23RedundantGeometryCharacterDelta = pathOptimizationStats.h23RedundantGeometryCharacterDelta
        reportData.h23ArcCleanupCharacterDelta = pathOptimizationStats.h23ArcCleanupCharacterDelta
        reportData.h23CurveSimplificationCharacterDelta = pathOptimizationStats.h23CurveSimplificationCharacterDelta
        reportData.h23CollinearConsolidationCharacterDelta = pathOptimizationStats.h23CollinearConsolidationCharacterDelta
        reportData.h23LocalCommandShorteningCharacterDelta = pathOptimizationStats.h23LocalCommandShorteningCharacterDelta
        reportData.h23GlobalCommandMinimizationCharacterDelta = pathOptimizationStats.h23GlobalCommandMinimizationCharacterDelta
        reportData.h23GlobalNumericSerializationCharacterDelta = pathOptimizationStats.h23GlobalNumericSerializationCharacterDelta
        reportData.h24PathSyntaxCandidatesRejectedForSize =
            pathOptimizationStats.h24PathSyntaxCandidatesRejectedForSize
        reportData.h24PathSyntaxCharactersAvoided =
            pathOptimizationStats.h24PathSyntaxCharactersAvoided
        reportData.h25DecimalCandidatesRejectedForSize =
            pathOptimizationStats.h25DecimalCandidatesRejectedForSize
        reportData.h25DecimalCharactersAvoided =
            pathOptimizationStats.h25DecimalCharactersAvoided
        reportData.h21PruningCharacterDelta = pathOptimizationStats.h21PruningCharacterDelta
        reportData.h21TransformCharacterDelta = pathOptimizationStats.h21TransformCharacterDelta
        reportData.h21NearIntegerCharacterDelta = pathOptimizationStats.h21NearIntegerCharacterDelta
        reportData.h21DedupMergeCharacterDelta = pathOptimizationStats.h21DedupMergeCharacterDelta
        reportData.h21DecimalCanonicalizationCharacterDelta =
            pathOptimizationStats.h21DecimalCanonicalizationCharacterDelta
        reportData.h21FormattingCharacterDelta = pathOptimizationStats.h21FormattingCharacterDelta

        reportData.reportAnalysisNanos = reportAnalysisNanos
        reportData.reportGenerationNanos = 0
        reportData.elapsedNanos = elapsedNanos
        reportData.elapsedMs = elapsedMs

        val report = SvgConversionReporter.buildReport(
            data = reportData,
            conversionStartNanos = startTime
        )

        return ConversionResult(
            xml = finalXml,
            report = report,
            reportData = reportData
        )
    }


    private fun elapsedNanoseconds(startTimeNanos: Long): Long =
        (System.nanoTime() - startTimeNanos).coerceAtLeast(0L)

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
