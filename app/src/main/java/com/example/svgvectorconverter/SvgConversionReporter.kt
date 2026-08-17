package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

data class BasicShapeBreakdown(
    val rectangles: Int = 0,
    val roundedRectangles: Int = 0,
    val circles: Int = 0,
    val ellipses: Int = 0,
    val polygons: Int = 0,
    val polylines: Int = 0
)

data class SvgImageStats(
    val imageElementCount: Int = 0,
    val embeddedRasterImageCount: Int = 0,
    val embeddedSvgImageCount: Int = 0,
    val externalImageCount: Int = 0,
    val missingHrefImageCount: Int = 0,
    val imageElementsWithSize: Int = 0
)

data class SvgPatternApproximationStats(
    val patternDefinitionCount: Int = 0,
    val approximatedPatternCount: Int = 0,
    val complexPatternApproximationCount: Int = 0,
    val sparsePatternApproximationCount: Int = 0
)

data class SvgValidationStageReport(
    var attempted: Boolean = false,
    var passed: Boolean = false,
    var validatedPathDataCount: Int = 0,
    var invalidPathDataCount: Int = 0,
    var nonFiniteNumberCount: Int = 0,
    var malformedStructureCount: Int = 0,
    var invalidViewportCount: Int = 0,
    var unsupportedOutputConstructCount: Int = 0,
    var witness: String = ""
)

class SvgConversionReportData {
    var convertedPathCount: Int = 0
    var convertedOriginalPathCount: Int = 0
    var convertedBasicShapeCount: Int = 0
    var basicShapeBreakdown: BasicShapeBreakdown = BasicShapeBreakdown()
    var definitionDrawableElementCount: Int = 0
    var visibleDrawableElementCount: Int = 0
    var drawableValidPathCount: Int = 0
    var emptyPathCount: Int = 0
    var generatedGroupCount: Int = 0
    var useCount: Int = 0
    var resolvedUseExpansions: Int = 0
    var unresolvedUseReferences: Int = 0
    var symbolCount: Int = 0
    var gradientFallbackColorCount: Int = 0
    var patternApproximationCount: Int = 0
    var patternApproximationStats: SvgPatternApproximationStats = SvgPatternApproximationStats()
    var patternTileExpansionCount: Int = 0
    var patternTilePathCount: Int = 0
    var markerDefinitionCount: Int = 0
    var appliedMarkers: Int = 0
    var clipPathCount: Int = 0
    var clipPathReferenceCount: Int = 0
    var appliedClipPaths: Int = 0
    var maskPathCount: Int = 0
    var maskReferenceCount: Int = 0
    var appliedMasks: Int = 0
    var dashedStrokesDetected: Int = 0
    var dashedStrokesApproximated: Int = 0
    var invalidDashArrays: Int = 0
    var dashSolidFallbacks: Int = 0
    var oddDashListsDuplicated: Int = 0
    var invalidDashOffsetFallbacks: Int = 0
    var dashOffsetsNormalized: Int = 0
    var dashTransformExactCompensations: Int = 0
    var dashTransformApproximateCompensations: Int = 0
    var nonScalingStrokesDetected: Int = 0
    var nonScalingStrokesCompensated: Int = 0
    var nonScalingStrokesUncertain: Int = 0
    var displayNoneElementsSkipped: Int = 0
    var visibilityHiddenElementsSkipped: Int = 0
    var nestedSvgViewportCount: Int = 0
    var nestedSvgViewportClipCount: Int = 0
    var nestedSvgPercentageViewportCount: Int = 0
    var nestedSvgOverflowHiddenCount: Int = 0
    var nestedSvgOverflowVisibleCount: Int = 0
    var nestedSvgOverflowAutoCount: Int = 0
    var nestedSvgOverflowScrollCount: Int = 0
    var nestedSvgOverflowUnsupportedCount: Int = 0
    var filterDefinitionCount: Int = 0
    var filterReferenceCount: Int = 0
    var textElementCount: Int = 0
    var tspanElementCount: Int = 0
    var textPathElementCount: Int = 0
    var textElementsApproximated: Int = 0
    var textElementsConvertedToPaths: Int = 0
    var textGlyphPathsEmitted: Int = 0
    var textGlyphSpecificAdvances: Int = 0
    var textDefaultFontAdvances: Int = 0
    var textMissingGlyphFallbacks: Int = 0
    var textGlyphNameLookups: Int = 0
    var textHorizontalKerningPairs: Int = 0
    var textVerticalKerningPairs: Int = 0
    var textHorizontalKerningPairsMatched: Int = 0
    var textVerticalKerningPairsMatched: Int = 0
    var textKerningAdjustmentsApplied: Int = 0
    var textLengthSpacingAdjustments: Int = 0
    var textLengthSpacingAndGlyphsAdjustments: Int = 0
    var textGlyphRotationsApplied: Int = 0
    var textLetterSpacingAdjustmentsApplied: Int = 0
    var textWordSpacingAdjustmentsApplied: Int = 0
    var textDecorationPathsEmitted: Int = 0
    var textBidiRunsReordered: Int = 0
    var textDirections: List<String> = emptyList()
    var textUnicodeBidiModes: List<String> = emptyList()
    var textPathsConverted: Int = 0
    var textPathGlyphsEmitted: Int = 0
    var textFontFamilies: List<String> = emptyList()
    var textFontWeights: List<String> = emptyList()
    var verticalWritingTextCount: Int = 0
    var writingModes: List<String> = emptyList()
    var textAnchors: List<String> = emptyList()
    var dominantBaselines: List<String> = emptyList()
    var alignmentBaselines: List<String> = emptyList()
    var baselineShifts: List<String> = emptyList()
    var lengthAdjustModes: List<String> = emptyList()
    var textPathMethods: List<String> = emptyList()
    var svgFontGlyphCount: Int = 0
    var contextPaintApproximationCount: Int = 0
    var cssImportRuleCount: Int = 0
    var cssImportedInlineRuleCount: Int = 0
    var cssExternalImportCount: Int = 0
    var imageStats: SvgImageStats = SvgImageStats()
    var styleAttributeCount: Int = 0
    var presentationStyleAttributeCount: Int = 0
    var warningCount: Int = 0
    var unsupportedWarnings: List<String> = emptyList()
    var unsupportedMatrixTransforms: Int = 0
    var supportedMatrixTransforms: Int = 0
    var matrixCount: Int = 0
    var translateCount: Int = 0
    var scaleCount: Int = 0
    var rotateCount: Int = 0
    var conversionProfile: String = ""
    var outputDpSize: Int = 0
    var viewportWidth: Float = 0f
    var viewportHeight: Float = 0f
    var pathDataOptimizedCount: Int = 0
    var pathDataCharactersBefore: Int = 0
    var pathDataCharactersAfter: Int = 0
    var pathDataRepeatedCommandsRemoved: Int = 0
    var redundantNonDrawingSegmentsRemoved: Int = 0
    var collinearLineSegmentsConsolidated: Int = 0
    var straightBezierCurvesSimplified: Int = 0
    var degenerateArcsSimplified: Int = 0
    var smoothBezierShorthandsSelected: Int = 0
    var cubicCurvesReducedToQuadratic: Int = 0
    var arcRotationsCanonicalized: Int = 0
    var arcRadiiCanonicalized: Int = 0
    var arcHalfTurnRotationsReduced: Int = 0
    var arcAxesSwappedForSize: Int = 0
    var arcRepresentationsGloballyMinimized: Int = 0
    var commandSequencesGloballyMinimized: Int = 0
    var implicitLineTosAfterMoveSelected: Int = 0
    var repeatedShorthandCurveCommandsOmitted: Int = 0
    var repeatedFullCurveCommandsOmitted: Int = 0
    var repeatedArcCommandsOmitted: Int = 0
    var scientificNotationValuesSelected: Int = 0
    var globallyOptimizedNumericPaths: Int = 0
    var pathDataNumbersNormalized: Int = 0
    var emptyPathDataRemoved: Int = 0
    var moveOnlyPathsRemoved: Int = 0
    var invisiblePathsRemoved: Int = 0
    var emptyGroupsRemoved: Int = 0
    var redundantGroupsFlattened: Int = 0
    var commonTranslationGroupsFactored: Int = 0
    var adjacentGroupsCoalesced: Int = 0
    var compatiblePathsMerged: Int = 0
    var compatiblePathMergesPreservedForSize: Int = 0
    var adjacentPathPairsExamined: Int = 0
    var adjacentPathPairsSamePaint: Int = 0
    var adjacentPathMergeRejectedNestedPaint: Int = 0
    var adjacentPathMergeRejectedMissingPathData: Int = 0
    var adjacentPathMergeRejectedPaintMismatch: Int = 0
    var adjacentPathMergeRejectedUnsupportedGeometry: Int = 0
    var adjacentPathMergeRejectedOverlapSafety: Int = 0
    var adjacentPathMergeRejectedForSize: Int = 0
    var exactDuplicatePathsRemoved: Int = 0
    var translatedGroupsFlattened: Int = 0
    var translatedPaths: Int = 0
    var translationGroupsPreservedForSize: Int = 0
    var scaledGroupsFlattened: Int = 0
    var scaledPaths: Int = 0
    var scaledStrokeWidths: Int = 0
    var scaleGroupsPreservedForSize: Int = 0
    var nonUniformScaleGroupsFlattened: Int = 0
    var nonUniformScaledPaths: Int = 0
    var nonUniformScaleGroupsPreservedForSize: Int = 0
    var rotationGroupsFlattened: Int = 0
    var rotatedPaths: Int = 0
    var rotationGroupsPreservedForSize: Int = 0
    var identityTransformAttributesRemoved: Int = 0
    var nestedTransformGroupsComposed: Int = 0
    var transformAttributesCanonicalized: Int = 0
    var zeroPivotAttributesRemoved: Int = 0
    var transformGroupsReordered: Int = 0
    var optimizerIdempotenceVerified: Boolean = false
    var optimizerReachedFixedPoint: Boolean = false
    var optimizerStabilityPasses: Int = 0
    var optimizerValidationNanos: Long = 0
    var optimizerProductionPassNanos: Long = 0
    var optimizerIdempotencePassNanos: Long = 0
    var optimizerFixedPointPassNanos: Long = 0
    var optimizerValidationPathCacheHits: Int = 0
    var optimizerValidationPathCacheMisses: Int = 0
    var optimizerIdempotencePathSyntaxNanos: Long = 0
    var optimizerIdempotencePathTokenizationNormalizationNanos: Long = 0
    var optimizerIdempotencePathGeometryCleanupNanos: Long = 0
    var optimizerIdempotencePathCommandMinimizationNanos: Long = 0
    var optimizerIdempotencePathNumericSerializationNanos: Long = 0
    var optimizerIdempotenceColorNormalizationNanos: Long = 0
    var optimizerIdempotencePruningGroupCleanupNanos: Long = 0
    var optimizerIdempotenceTransformOptimizationNanos: Long = 0
    var optimizerIdempotenceDeduplicationMergeNanos: Long = 0
    var optimizerIdempotenceNumericCleanupNanos: Long = 0
    var optimizerIdempotenceNearIntegerSnappingNanos: Long = 0
    var optimizerIdempotenceDecimalCanonicalizationNanos: Long = 0
    var optimizerIdempotenceDecimalTokenizationNanos: Long = 0
    var optimizerIdempotenceDecimalRebuildNanos: Long = 0
    var optimizerIdempotenceDecimalReoptimizationNanos: Long = 0
    var optimizerIdempotenceDecimalValidationNanos: Long = 0
    var optimizerIdempotenceDecimalPathsExamined: Int = 0
    var optimizerIdempotenceFinalFormattingNanos: Long = 0
    var optimizerIdempotenceEqualityComparisonNanos: Long = 0
    var optimizerIdempotencePathsExamined: Int = 0
    var optimizerIdempotenceFinalPassStablePathsRegistered: Int = 0
    var optimizerIdempotencePathCacheHits: Int = 0
    var optimizerIdempotenceStableOutputCacheHits: Int = 0
    var optimizerIdempotenceRegularCacheHits: Int = 0
    var optimizerIdempotencePathCacheMisses: Int = 0
    var optimizerIdempotenceXmlCharactersBefore: Int = 0
    var optimizerIdempotenceXmlCharactersAfter: Int = 0
    var optimizerValidationPasses: Int = 0
    var optimizerFirstPassChangedXml: Boolean = false
    var optimizerSecondPassChangedXml: Boolean = false
    var optimizerThirdPassChangedXml: Boolean = false
    var g315TrialAttempted: Boolean = false
    var g315CandidateChanged: Boolean = false
    var g315PathsExamined: Int = 0
    var g315PathsChanged: Int = 0
    var g315GeometryComparisons: Int = 0
    var g315GeometryMismatchCount: Int = 0
    var g315ExactShortCircuitCount: Int = 0
    var g315FallbackBidirectionalCount: Int = 0
    var g315ComparatorFailureCount: Int = 0
    var g315MatchedIndependentSecondPass: Boolean = false
    var g315FixedPointVerified: Boolean = false
    var g315FinalValidationPassed: Boolean = false
    var g315GuardAccepted: Boolean = false
    var g315GuardRejected: Boolean = false
    var g315CharactersBefore: Int = 0
    var g315CharactersAfter: Int = 0
    var g315CharactersSaved: Int = 0
    var g315CharactersAdded: Int = 0
    var g315CandidateNanos: Long = 0
    var g315ComparatorNanos: Long = 0
    var g315GuardNanos: Long = 0
    var g315RejectionReason: String = ""
    var finalOutputValidationPassed: Boolean = false
    var finalOutputValidationNanos: Long = 0
    var validatedPathDataCount: Int = 0
    var invalidPathDataCount: Int = 0
    var nonFiniteNumberCount: Int = 0
    var malformedStructureCount: Int = 0
    var invalidViewportCount: Int = 0
    var unsupportedOutputConstructCount: Int = 0
    var h16InputValidation: SvgValidationStageReport = SvgValidationStageReport()
    var h16Pass1Validation: SvgValidationStageReport = SvgValidationStageReport()
    var h16Pass2Validation: SvgValidationStageReport = SvgValidationStageReport()
    var h16Pass3Validation: SvgValidationStageReport = SvgValidationStageReport()
    var h16SelectedValidation: SvgValidationStageReport = SvgValidationStageReport()
    var shorterCommandFormsSelected: Int = 0
    var relativeCommandsSelected: Int = 0
    var axisCommandsSelected: Int = 0
    var sourceSvgCharacters: Int = 0
    var optimizedXmlCharactersBefore: Int = 0
    var optimizedXmlCharactersAfter: Int = 0
    var styleResolutionNanos: Long = 0
    var svgParsingNanos: Long = 0
    var treeConversionNanos: Long = 0
    var outputOptimizationNanos: Long = 0
    var optimizationPathSyntaxNanos: Long = 0
    var optimizationPathTokenizationNanos: Long = 0
    var optimizationPathGeometryCleanupNanos: Long = 0
    var optimizationPathRedundantSegmentCleanupNanos: Long = 0
    var optimizationPathArcCleanupNanos: Long = 0
    var optimizationPathCurveSimplificationNanos: Long = 0
    var optimizationCurveCubicToQuadraticNanos: Long = 0
    var optimizationCurveCubicParseSetupNanos: Long = 0
    var optimizationCurveCubicScanNanos: Long = 0
    var optimizationCurveCubicRebuildValidationNanos: Long = 0
    var optimizationCurveCubicRebuildNanos: Long = 0
    var optimizationCurveCubicValidationNanos: Long = 0
    var optimizationCurveStraightBezierNanos: Long = 0
    var optimizationCurveStraightParseSetupNanos: Long = 0
    var optimizationCurveStraightScanNanos: Long = 0
    var optimizationCurveStraightRebuildValidationNanos: Long = 0
    var optimizationCurveStraightRebuildNanos: Long = 0
    var optimizationCurveStraightValidationNanos: Long = 0
    var optimizationCurveParseCalls: Int = 0
    var optimizationCurveDuplicateParseInputs: Int = 0
    var optimizationCurveSecondPassReparsedUnchangedInput: Int = 0
    var optimizationCurveCubicChangedPaths: Int = 0
    var optimizationCurveStraightChangedPaths: Int = 0
    var optimizationCurveRebuildAttempts: Int = 0
    var optimizationCurveRebuildNoOpResults: Int = 0
    var optimizationCurveValidationCalls: Int = 0
    var optimizationCurveValidationAccepted: Int = 0
    var optimizationCurveValidationRejected: Int = 0
    var optimizationCurveRebuildRejectedForSize: Int = 0
    var optimizationPathCollinearConsolidationNanos: Long = 0
    var optimizationPathCommandMinimizationNanos: Long = 0
    var optimizationPathCommandLocalShorteningNanos: Long = 0
    var optimizationPathCommandLocalParseSetupNanos: Long = 0
    var optimizationPathCommandLocalAbsoluteRelativeCandidateNanos: Long = 0
    var optimizationPathCommandLocalAxisCandidateNanos: Long = 0
    var optimizationPathCommandLocalSmoothShorthandCandidateNanos: Long = 0
    var optimizationPathCommandLocalEncodingSelectionNanos: Long = 0
    var optimizationPathCommandLocalNumericSerializationNanos: Long = 0
    var optimizationPathCommandLocalSeparatorCalculationNanos: Long = 0
    var optimizationPathCommandLocalCommandOmissionNanos: Long = 0
    var optimizationPathCommandLocalStringConstructionNanos: Long = 0
    var optimizationPathCommandLocalWinnerSelectionNanos: Long = 0
    var optimizationPathCommandLocalStateBookkeepingNanos: Long = 0
    var optimizationPathCommandLocalNumericSerializationCalls: Int = 0
    var optimizationPathCommandLocalNumericSerializationCacheHits: Int = 0
    var optimizationPathCommandLocalNumericSerializationUniqueValues: Int = 0
    var optimizationPathCommandGlobalParseSetupNanos: Long = 0
    var optimizationPathCommandGlobalCandidateGenerationNanos: Long = 0
    var optimizationPathCommandGlobalDynamicProgrammingNanos: Long = 0
    var optimizationPathCommandGlobalTransitionEvaluationNanos: Long = 0
    var optimizationPathCommandGlobalSeparatorOmissionCostNanos: Long = 0
    var optimizationPathCommandGlobalSegmentEncodingNanos: Long = 0
    var optimizationPathCommandGlobalStateCreationNanos: Long = 0
    var optimizationPathCommandGlobalBestStateComparisonNanos: Long = 0
    var optimizationPathCommandGlobalReconstructionNanos: Long = 0
    var optimizationPathCommandGlobalStateKeyCreationNanos: Long = 0
    var optimizationPathCommandGlobalStateKeyFieldPreparationNanos: Long = 0
    var optimizationPathCommandGlobalStateKeyPreviousCommandNanos: Long = 0
    var optimizationPathCommandGlobalStateKeyPreviousNumberNanos: Long = 0
    var optimizationPathCommandGlobalStateKeyAxisDirectionNanos: Long = 0
    var optimizationPathCommandGlobalStateKeyAllocationNanos: Long = 0
    var optimizationPathCommandGlobalStateStringConcatenationNanos: Long = 0
    var optimizationPathCommandGlobalStateMetadataPropagationNanos: Long = 0
    var optimizationPathCommandGlobalStatePathAllocationNanos: Long = 0
    var optimizationPathCommandGlobalBestStateMapLookupNanos: Long = 0
    var optimizationPathCommandGlobalBestStateDecisionNanos: Long = 0
    var optimizationPathCommandGlobalBestStateReplacementNanos: Long = 0
    var optimizationPathCommandGlobalStateMapLookupCalls: Int = 0
    var optimizationPathCommandGlobalStateMapLookupHits: Int = 0
    var optimizationPathCommandGlobalStateMapLookupMisses: Int = 0
    var optimizationPathCommandGlobalStateMapInsertions: Int = 0
    var optimizationPathCommandGlobalStateMapReplacements: Int = 0
    var optimizationPathCommandGlobalSegmentEncodingRequests: Int = 0
    var optimizationPathCommandGlobalSegmentEncodingCacheHits: Int = 0
    var optimizationPathCommandGlobalSegmentEncodingUniqueKeys: Int = 0
    var optimizationPathNumericSerializationNanos: Long = 0
    var optimizationColorNormalizationNanos: Long = 0
    var optimizationPruningCleanupNanos: Long = 0
    var optimizationTransformsNanos: Long = 0
    var optimizationTransformIdentityCompositionNanos: Long = 0
    var optimizationTransformFactoringFlatteningNanos: Long = 0
    var optimizationTransformScaleFlatteningNanos: Long = 0
    var optimizationTransformUniformScaleFlatteningNanos: Long = 0
    var optimizationTransformUniformScaleGroupDiscoveryNanos: Long = 0
    var optimizationTransformUniformScaleEligibilityChecksNanos: Long = 0
    var optimizationTransformUniformScalePathScalingNanos: Long = 0
    var optimizationTransformUniformScalePathParseTokenizeNanos: Long = 0
    var optimizationTransformUniformScalePathNumericParseNanos: Long = 0
    var optimizationTransformUniformScalePathCoordinateMathNanos: Long = 0
    var optimizationTransformUniformScalePathArcHandlingNanos: Long = 0
    var optimizationTransformUniformScalePathNumberFormattingNanos: Long = 0
    var optimizationTransformUniformScalePathReconstructionNanos: Long = 0
    var optimizationTransformUniformScalePathNormalizationNanos: Long = 0
    var optimizationTransformUniformScalePostScaleP6Attempts: Int = 0
    var optimizationTransformUniformScalePostScaleP6Accepted: Int = 0
    var optimizationTransformUniformScalePostScaleP6Fallbacks: Int = 0
    var optimizationTransformUniformScalePostScaleP6ParserFallbacks: Int = 0
    var optimizationTransformUniformScalePostScaleP6InternalFallbacks: Int = 0
    var optimizationTransformUniformScalePostScaleP6OptimizationNanos: Long = 0
    var optimizationTransformUniformScalePostScaleP6ParserValidationNanos: Long = 0
    var optimizationTransformUniformScalePostScaleFullFallbackNanos: Long = 0
    var optimizationTransformUniformScaleStrokeAdjustmentNanos: Long = 0
    var optimizationTransformUniformScaleCanonicalizationCostingNanos: Long = 0
    var optimizationTransformUniformScaleXmlReplacementNanos: Long = 0
    var optimizationTransformUniformScaleCandidatesConsidered: Int = 0
    var optimizationTransformUniformScaleCandidatesRejected: Int = 0
    var optimizationTransformUniformScaleProposalsAccepted: Int = 0
    var optimizationTransformNonUniformScaleFlatteningNanos: Long = 0
    var optimizationTransformRotationTranslationNanos: Long = 0
    var optimizationTransformCanonicalizationNanos: Long = 0
    var optimizationDeduplicationNanos: Long = 0
    var optimizationNumericCleanupNanos: Long = 0
    var optimizationNearIntegerSnappingNanos: Long = 0
    var optimizationDecimalCanonicalizationNanos: Long = 0
    var optimizationDecimalTokenizationNanos: Long = 0
    var optimizationDecimalRebuildNanos: Long = 0
    var optimizationDecimalReoptimizationNanos: Long = 0
    var optimizationDecimalValidationNanos: Long = 0
    var optimizationDecimalPathsExamined: Int = 0

    // I2 diagnostic-only redundancy / fast-path shadow study.
    var i2Pass1PathSyntaxStableInputs: Int = 0
    var i2Pass1PathSyntaxStableInputNanos: Long = 0
    var i2Pass2PathSyntaxStableInputs: Int = 0
    var i2Pass2PathSyntaxStableInputNanos: Long = 0

    var i2Pass1DecimalShadowPathsCompared: Int = 0
    var i2Pass1DecimalShadowByteIdentical: Int = 0
    var i2Pass1DecimalShadowDifferent: Int = 0
    var i2Pass1DecimalShadowFastShorter: Int = 0
    var i2Pass1DecimalShadowReferenceShorter: Int = 0
    var i2Pass1DecimalShadowEqualLengthDifferent: Int = 0
    var i2Pass1DecimalShadowFastInvalid: Int = 0
    var i2Pass1DecimalShadowFastNonFixed: Int = 0
    var i2Pass1DecimalShadowCharacterDeltaVsReference: Int = 0
    var i2Pass1DecimalShadowNanos: Long = 0

    var i2Pass2DecimalShadowPathsCompared: Int = 0
    var i2Pass2DecimalShadowByteIdentical: Int = 0
    var i2Pass2DecimalShadowDifferent: Int = 0
    var i2Pass2DecimalShadowFastShorter: Int = 0
    var i2Pass2DecimalShadowReferenceShorter: Int = 0
    var i2Pass2DecimalShadowEqualLengthDifferent: Int = 0
    var i2Pass2DecimalShadowFastInvalid: Int = 0
    var i2Pass2DecimalShadowFastNonFixed: Int = 0
    var i2Pass2DecimalShadowCharacterDeltaVsReference: Int = 0
    var i2Pass2DecimalShadowNanos: Long = 0

    // I3 guarded production decimal fast path.
    var i3Pass1DecimalFastPathAccepted: Int = 0
    var i3Pass1DecimalFallbackInvalid: Int = 0
    var i3Pass1DecimalFallbackNonFixed: Int = 0
    var i3Pass1DecimalFastPathCheckNanos: Long = 0
    var i3Pass2DecimalFastPathAccepted: Int = 0
    var i3Pass2DecimalFallbackInvalid: Int = 0
    var i3Pass2DecimalFallbackNonFixed: Int = 0
    var i3Pass2DecimalFastPathCheckNanos: Long = 0

    // I4.1 diagnostic-only pass-2 fixed-point certificate study.
    var i41Pass2CertificatePredictedFixed: Int = 0
    var i41Pass2CertificateTruePositive: Int = 0
    var i41Pass2CertificateFalsePositive: Int = 0
    var i41Pass2CertificateFalseNegative: Int = 0
    var i41Pass2CertificateTrueNegative: Int = 0
    var i41Pass2CertificateCheckNanos: Long = 0
    var i41Pass2PotentialAvoidableOptimizerNanos: Long = 0
    var i41Pass2FalsePositiveOptimizerNanos: Long = 0
    var i41Pass2RejectedLexical: Int = 0
    var i41Pass2RejectedNumericSpelling: Int = 0
    var i41Pass2RejectedWhitespace: Int = 0
    var i41Pass2RejectedComplexCommandFamily: Int = 0
    var i41Pass2RejectedExplicitRepeat: Int = 0

    // I4.2 provenance-aware pass-2 certificate diagnostics.
    var i42Pass2ProvenanceExcluded: Int = 0
    var i42Pass2ProvenanceExcludedActuallyFixed: Int = 0
    var i42Pass2ProvenancePreventedFalsePositive: Int = 0
    var i42Pass2ProvenanceExcludedOptimizerNanos: Long = 0
    var i42Pass2PreventedFalsePositiveOptimizerNanos: Long = 0
    var i42Pass2PreventedChangedSyntaxNormalization: Int = 0
    var i42Pass2PreventedChangedGeometryCleanup: Int = 0
    var i42Pass2PreventedChangedLocalShortening: Int = 0
    var i42Pass2PreventedChangedGlobalCommand: Int = 0
    var i42Pass2PreventedChangedGlobalNumeric: Int = 0
    var i42Pass2PreventedChangedOther: Int = 0

    // I4.3 diagnostic-only complex-command certificate expansion.
    var i43Pass2ComplexCandidatesExamined: Int = 0
    var i43Pass2ComplexPredictedFixed: Int = 0
    var i43Pass2ComplexTruePositive: Int = 0
    var i43Pass2ComplexFalsePositive: Int = 0
    var i43Pass2ComplexFalseNegative: Int = 0
    var i43Pass2ComplexTrueNegative: Int = 0
    var i43Pass2ComplexCheckNanos: Long = 0
    var i43Pass2ComplexPotentialAvoidableOptimizerNanos: Long = 0
    var i43Pass2ComplexFalsePositiveOptimizerNanos: Long = 0
    var i43Pass2RejectedReflectiveShorthand: Int = 0
    var i43Pass2RejectedNumericSpelling: Int = 0
    var i43Pass2RejectedExplicitRepeat: Int = 0
    var i43Pass2RejectedProvenance: Int = 0
    var i43Pass2CubicPredicted: Int = 0
    var i43Pass2CubicTruePositive: Int = 0
    var i43Pass2CubicFalsePositive: Int = 0
    var i43Pass2QuadraticPredicted: Int = 0
    var i43Pass2QuadraticTruePositive: Int = 0
    var i43Pass2QuadraticFalsePositive: Int = 0
    var i43Pass2ArcPredicted: Int = 0
    var i43Pass2ArcTruePositive: Int = 0
    var i43Pass2ArcFalsePositive: Int = 0
    var i43Pass2MixedPredicted: Int = 0
    var i43Pass2MixedTruePositive: Int = 0
    var i43Pass2MixedFalsePositive: Int = 0
    var i43Pass2FalsePositiveGeometryCleanup: Int = 0
    var i43Pass2FalsePositiveLocalShortening: Int = 0
    var i43Pass2FalsePositiveGlobalCommand: Int = 0
    var i43Pass2FalsePositiveGlobalNumeric: Int = 0
    var i43Pass2FalsePositiveOther: Int = 0

    // I4.6 production-active guarded pass-2 fixed-point skips.
    var i46Pass2CertifiedSkips: Int = 0
    var i46Pass2CertifiedByBasic: Int = 0
    var i46Pass2CertifiedByComplex: Int = 0
    var i46Pass2NonCertifiedFallbacks: Int = 0
    var i46Pass2ProvenanceBlocked: Int = 0
    var i46Pass2CertificateNanos: Long = 0
    var i46Pass2FullOptimizerNanosOnFallbacks: Long = 0

    var optimizationPathCacheHits: Int = 0
    var optimizationPathCacheMisses: Int = 0
    var optimizationFormattingNanos: Long = 0
    var optimizationPathSyntaxCharactersSaved: Int = 0
    var optimizationPruningCleanupCharactersSaved: Int = 0
    var optimizationTransformCharactersSaved: Int = 0
    var optimizationDeduplicationCharactersSaved: Int = 0
    var optimizationNumericCleanupCharactersSaved: Int = 0
    var optimizationFormattingCharactersSaved: Int = 0

    // Diagnostic signed stage deltas. Positive = characters removed; negative = added.
    var h21PathSyntaxCharacterDelta: Int = 0
    var h22PathDataSyntaxCharacterDelta: Int = 0
    var h22ColorNormalizationCharacterDelta: Int = 0
    var h23SyntaxNormalizationCharacterDelta: Int = 0
    var h23RedundantGeometryCharacterDelta: Int = 0
    var h23ArcCleanupCharacterDelta: Int = 0
    var h23CurveSimplificationCharacterDelta: Int = 0
    var h23CollinearConsolidationCharacterDelta: Int = 0
    var h23LocalCommandShorteningCharacterDelta: Int = 0
    var h23GlobalCommandMinimizationCharacterDelta: Int = 0
    var h23GlobalNumericSerializationCharacterDelta: Int = 0
    var h24PathSyntaxCandidatesRejectedForSize: Int = 0
    var h24PathSyntaxCharactersAvoided: Int = 0
    var h25DecimalCandidatesRejectedForSize: Int = 0
    var h25DecimalCharactersAvoided: Int = 0
    var h21PruningCharacterDelta: Int = 0
    var h21TransformCharacterDelta: Int = 0
    var h21NearIntegerCharacterDelta: Int = 0
    var h21DedupMergeCharacterDelta: Int = 0
    var h21DecimalCanonicalizationCharacterDelta: Int = 0
    var h21FormattingCharacterDelta: Int = 0

    var reportAnalysisNanos: Long = 0
    var reportGenerationNanos: Long = 0
    var elapsedNanos: Long = 0
    var elapsedMs: Long = 0
}

object SvgConversionReporter {
    fun hasTag(svg: String, tagName: String): Boolean {
        return Regex("""<\s*$tagName\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(svg)
    }

    fun countConvertedBasicShapes(xml: String): Int {
        return Regex("""<!-- converted from <(rect|circle|ellipse|line|polyline|polygon)> -->""")
            .findAll(xml)
            .count()
    }

    fun countConvertedOriginalSvgPaths(xml: String): Int {
        return Regex("""<!-- converted from <path> -->""")
            .findAll(xml)
            .count()
    }

    fun countDrawableBasicShapeBreakdown(svg: String): BasicShapeBreakdown {
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
                    if (basicShapeToPathData(element, tag) != null) {
                        val rx = floatAttr(element, "rx") ?: 0f
                        val ry = floatAttr(element, "ry") ?: 0f
                        if (rx > 0f || ry > 0f) {
                            roundedRectangles++
                        } else {
                            rectangles++
                        }
                    }
                }
                "circle" -> {
                    if (basicShapeToPathData(element, tag) != null) circles++
                }
                "ellipse" -> {
                    if (basicShapeToPathData(element, tag) != null) ellipses++
                }
                "polygon" -> {
                    if (basicShapeToPathData(element, tag) != null) polygons++
                }
                "polyline" -> {
                    if (basicShapeToPathData(element, tag) != null) polylines++
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    countElement(child as Element)
                }
            }
        }

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }

            val document = factory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(svg)))

            countElement(document.documentElement)

            BasicShapeBreakdown(
                rectangles = rectangles,
                roundedRectangles = roundedRectangles,
                circles = circles,
                ellipses = ellipses,
                polygons = polygons,
                polylines = polylines
            )
        } catch (e: Exception) {
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

    fun countDefinitionDrawableElements(svg: String): Int {
        val basicShapeTags = setOf("rect", "circle", "ellipse", "line", "polyline", "polygon")
        var count = 0

        fun countDrawableElement(element: Element) {
            val tag = element.tagName.substringAfter(":").lowercase()

            when (tag) {
                "path" -> {
                    if (element.getAttribute("d").trim().isNotBlank()) {
                        count++
                    }
                }
                in basicShapeTags -> {
                    if (basicShapeToPathData(element, tag) != null) {
                        count++
                    }
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    countDrawableElement(child as Element)
                }
            }
        }

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }

            val document = factory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(svg)))

            val defsNodes = document.getElementsByTagName("defs")

            for (i in 0 until defsNodes.length) {
                val defs = defsNodes.item(i)
                val children = defs.childNodes

                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        countDrawableElement(child as Element)
                    }
                }
            }

            count
        } catch (e: Exception) {
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


    fun buildReport(
        data: SvgConversionReportData,
        conversionStartNanos: Long? = null
    ): String {
        val reportStartNanos = System.nanoTime()
        val performanceMarker = "__SVG_CONVERTER_PERFORMANCE__"
        val reportWithoutPerformance = buildString {
            appendReportBody(data, performanceMarker)
        }
    

        val reportGenerationNanos =
            (System.nanoTime() - reportStartNanos).coerceAtLeast(0L)
        val elapsedNanos = conversionStartNanos
            ?.let { (System.nanoTime() - it).coerceAtLeast(0L) }
            ?: data.elapsedNanos
        val performance = buildString {
            appendPerformanceBreakdown(
                data = data,
                elapsedNanosOverride = elapsedNanos,
                reportGenerationNanosOverride = reportGenerationNanos
            )
        }.trimEnd()
        return reportWithoutPerformance.replace(performanceMarker, performance)
    }

    /**
     * Appends the main report body outside buildReport so Android's DEX
     * verifier does not have to verify one extremely large register-heavy
     * method. This is a reporting-only extraction; output ordering and text
     * remain unchanged.
     */
    private fun StringBuilder.appendReportBody(
        data: SvgConversionReportData,
        performanceMarker: String
    ) {
        val aggregateWarningCount = aggregateWarningCount(data)
        val summaryTitle =
            if (aggregateWarningCount == 0)
                "🟢 Conversion Successful"
            else
                "🟡 Conversion Completed With Warnings"
        val drawablePathWord =
            if (data.convertedPathCount == 1) "path" else "paths"

            appendLine(summaryTitle)
            appendLine("${data.convertedPathCount} drawable $drawablePathWord created")

            if (aggregateWarningCount == 0)
                appendLine("No warnings detected")
            else
                appendLine("$aggregateWarningCount warning(s) detected")

            appendLine(performanceMarker)

            appendLine()
            appendLine("────────────────────")
            appendLine("Compatibility")
            appendLine("────────────────────")
            appendLine()
            appendCompatibilitySummary(data)

            appendLine("────────────────────")
            appendLine("Drawable Elements Processed")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Generated VectorDrawable paths: ${data.convertedPathCount}")
            appendLine("✓ SVG path elements converted: ${data.convertedOriginalPathCount}")
            appendLine("✓ Basic shapes converted: ${data.convertedBasicShapeCount}")
            appendBasicShapeBreakdown(data.basicShapeBreakdown)

            appendLine("✓ Expanded <use> references: ${data.resolvedUseExpansions}")
            if (data.unresolvedUseReferences > 0)
                appendLine("⚠ Unresolved <use> references: ${data.unresolvedUseReferences}")
            appendLine("✓ Definition drawable elements: ${data.definitionDrawableElementCount}")
            appendLine("✓ Groups created: ${data.generatedGroupCount}")

            appendLine()
            appendLine("────────────────────")
            appendLine("Output Optimization")
            appendLine("────────────────────")
            appendLine()
            appendLine("✓ Path data optimized: ${data.pathDataOptimizedCount}")
            appendLine("✓ Path-data characters: ${data.pathDataCharactersBefore} → ${data.pathDataCharactersAfter}")
            val pathCharacterDelta =
                data.pathDataCharactersAfter - data.pathDataCharactersBefore
            val pathChangePercent = if (data.pathDataCharactersBefore > 0) {
                kotlin.math.abs(pathCharacterDelta) * 100.0 /
                    data.pathDataCharactersBefore.toDouble()
            } else {
                0.0
            }
            when {
                pathCharacterDelta < 0 ->
                    appendLine(
                        "✓ Path data reduced by ${-pathCharacterDelta} characters " +
                            "(${String.format(java.util.Locale.US, "%.1f", pathChangePercent)}%)"
                    )
                pathCharacterDelta > 0 ->
                    appendLine(
                        "• Path data increased by $pathCharacterDelta characters " +
                            "(${String.format(java.util.Locale.US, "%.1f", pathChangePercent)}%)"
                    )
                else ->
                    appendLine("✓ Path data size unchanged")
            }

            val xmlCharactersSaved = (data.optimizedXmlCharactersBefore - data.optimizedXmlCharactersAfter).coerceAtLeast(0)
            val xmlReductionPercent = if (data.optimizedXmlCharactersBefore > 0) {
                xmlCharactersSaved * 100.0 / data.optimizedXmlCharactersBefore.toDouble()
            } else {
                0.0
            }
            appendLine("✓ XML reduction: ${String.format(java.util.Locale.US, "%.1f", xmlReductionPercent)}% ($xmlCharactersSaved characters)")

            if (data.pathDataNumbersNormalized > 0)
                appendLine("✓ Numeric values normalized: ${data.pathDataNumbersNormalized}")
            if (data.pathDataRepeatedCommandsRemoved > 0)
                appendLine("✓ Repeated commands removed: ${data.pathDataRepeatedCommandsRemoved}")
            if (data.redundantNonDrawingSegmentsRemoved > 0)
                appendLine(
                    "✓ Redundant non-drawing segments removed: " +
                        data.redundantNonDrawingSegmentsRemoved
                )
            if (data.collinearLineSegmentsConsolidated > 0)
                appendLine(
                    "✓ Collinear line segments consolidated: " +
                        data.collinearLineSegmentsConsolidated
                )
            if (data.straightBezierCurvesSimplified > 0)
                appendLine(
                    "✓ Straight Bézier curves simplified: " +
                        data.straightBezierCurvesSimplified
                )
            if (data.degenerateArcsSimplified > 0)
                appendLine(
                    "✓ Degenerate arcs simplified: " +
                        data.degenerateArcsSimplified
                )
            if (data.smoothBezierShorthandsSelected > 0)
                appendLine(
                    "✓ Smooth Bézier shorthands selected: " +
                        data.smoothBezierShorthandsSelected
                )
            if (data.cubicCurvesReducedToQuadratic > 0)
                appendLine(
                    "✓ Cubic curves reduced to quadratic: " +
                        data.cubicCurvesReducedToQuadratic
                )
            if (data.arcRotationsCanonicalized > 0)
                appendLine(
                    "✓ Arc rotations canonicalized: " +
                        data.arcRotationsCanonicalized
                )
            if (data.arcRadiiCanonicalized > 0)
                appendLine(
                    "✓ Arc radii canonicalized: " +
                        data.arcRadiiCanonicalized
                )
            if (data.arcHalfTurnRotationsReduced > 0)
                appendLine(
                    "✓ Arc half-turn rotations reduced: " +
                        data.arcHalfTurnRotationsReduced
                )
            if (data.arcAxesSwappedForSize > 0)
                appendLine(
                    "✓ Arc axes swapped for shorter encoding: " +
                        data.arcAxesSwappedForSize
                )
            if (data.arcRepresentationsGloballyMinimized > 0)
                appendLine(
                    "✓ Arc representations globally minimized: " +
                        data.arcRepresentationsGloballyMinimized
                )
            if (data.commandSequencesGloballyMinimized > 0)
                appendLine(
                    "✓ Path command sequences globally minimized: " +
                        data.commandSequencesGloballyMinimized
                )
            if (data.implicitLineTosAfterMoveSelected > 0)
                appendLine(
                    "✓ Implicit line commands after move selected: " +
                        data.implicitLineTosAfterMoveSelected
                )
            if (data.repeatedShorthandCurveCommandsOmitted > 0)
                appendLine(
                    "✓ Repeated shorthand curve commands omitted: " +
                        data.repeatedShorthandCurveCommandsOmitted
                )
            if (data.repeatedFullCurveCommandsOmitted > 0)
                appendLine(
                    "✓ Repeated full curve commands omitted: " +
                        data.repeatedFullCurveCommandsOmitted
                )
            if (data.repeatedArcCommandsOmitted > 0)
                appendLine(
                    "✓ Repeated arc commands omitted: " +
                        data.repeatedArcCommandsOmitted
                )
            if (data.scientificNotationValuesSelected > 0)
                appendLine(
                    "✓ Scientific notation values selected: " +
                        data.scientificNotationValuesSelected
                )
            if (data.globallyOptimizedNumericPaths > 0)
                appendLine(
                    "✓ Paths with globally optimized numeric serialization: " +
                        data.globallyOptimizedNumericPaths
                )
            if (data.emptyPathDataRemoved > 0)
                appendLine("✓ Empty path-data elements removed: ${data.emptyPathDataRemoved}")
            if (data.moveOnlyPathsRemoved > 0)
                appendLine("✓ Move-only paths removed: ${data.moveOnlyPathsRemoved}")
            if (data.invisiblePathsRemoved > 0)
                appendLine("✓ Fully transparent paths removed: ${data.invisiblePathsRemoved}")
            if (data.emptyGroupsRemoved > 0)
                appendLine("✓ Empty groups removed: ${data.emptyGroupsRemoved}")
            if (data.redundantGroupsFlattened > 0)
                appendLine("✓ Redundant groups flattened: ${data.redundantGroupsFlattened}")
            if (data.commonTranslationGroupsFactored > 0)
                appendLine("✓ Groups sharing translations factored: ${data.commonTranslationGroupsFactored}")
            if (data.adjacentGroupsCoalesced > 0)
                appendLine("✓ Identical adjacent groups coalesced: ${data.adjacentGroupsCoalesced}")
            if (data.exactDuplicatePathsRemoved > 0)
                appendLine("✓ Exact duplicate paths removed: ${data.exactDuplicatePathsRemoved}")
            if (data.translatedGroupsFlattened > 0)
                appendLine("✓ Translation groups flattened: ${data.translatedGroupsFlattened}")
            if (data.translatedPaths > 0)
                appendLine("✓ Paths translated into coordinates: ${data.translatedPaths}")
            if (data.scaledGroupsFlattened > 0)
                appendLine("✓ Uniform scale groups flattened: ${data.scaledGroupsFlattened}")
            if (data.scaledPaths > 0)
                appendLine("✓ Paths scaled into coordinates: ${data.scaledPaths}")
            if (data.scaledStrokeWidths > 0)
                appendLine("✓ Stroke widths scaled: ${data.scaledStrokeWidths}")
            if (data.scaleGroupsPreservedForSize > 0)
                appendLine(
                    "✓ Uniform scale groups preserved for smaller output: " +
                        data.scaleGroupsPreservedForSize
                )
            if (data.nonUniformScaleGroupsFlattened > 0)
                appendLine(
                    "✓ Non-uniform scale groups flattened: " +
                        data.nonUniformScaleGroupsFlattened
                )
            if (data.nonUniformScaledPaths > 0)
                appendLine(
                    "✓ Fill-only paths non-uniformly scaled into coordinates: " +
                        data.nonUniformScaledPaths
                )
            if (data.nonUniformScaleGroupsPreservedForSize > 0)
                appendLine(
                    "✓ Non-uniform scale groups preserved for smaller output: " +
                        data.nonUniformScaleGroupsPreservedForSize
                )
            if (data.rotationGroupsFlattened > 0)
                appendLine(
                    "✓ Rotation groups flattened: " +
                        data.rotationGroupsFlattened
                )
            if (data.rotatedPaths > 0)
                appendLine(
                    "✓ Paths rotated into coordinates: " +
                        data.rotatedPaths
                )
            if (data.rotationGroupsPreservedForSize > 0)
                appendLine(
                    "✓ Rotation groups preserved for smaller output: " +
                        data.rotationGroupsPreservedForSize
                )
            if (data.identityTransformAttributesRemoved > 0)
                appendLine("✓ Identity transform attributes removed: ${data.identityTransformAttributesRemoved}")
            if (data.nestedTransformGroupsComposed > 0)
                appendLine("✓ Nested transform groups consolidated: ${data.nestedTransformGroupsComposed}")
            if (data.transformAttributesCanonicalized > 0)
                appendLine("✓ Transform attributes canonicalized: ${data.transformAttributesCanonicalized}")
            if (data.zeroPivotAttributesRemoved > 0)
                appendLine("✓ Redundant zero pivots removed: ${data.zeroPivotAttributesRemoved}")
            if (data.transformGroupsReordered > 0)
                appendLine("✓ Transform groups put in canonical order: ${data.transformGroupsReordered}")
            if (data.compatiblePathsMerged > 0)
                appendLine("✓ Compatible adjacent paths merged: ${data.compatiblePathsMerged}")
            if (data.shorterCommandFormsSelected > 0)
                appendLine("✓ Shorter command forms selected: ${data.shorterCommandFormsSelected}")
            if (data.relativeCommandsSelected > 0)
                appendLine("✓ Relative commands selected: ${data.relativeCommandsSelected}")
            if (data.axisCommandsSelected > 0)
                appendLine("✓ Horizontal/vertical commands selected: ${data.axisCommandsSelected}")

            appendOptimizerValidation(data)
            appendG315GuardedProductionTrial(data)
            appendFinalOutputValidation(data)
            appendOptimizationImpact(data)
            appendOptimizationQualityMetrics(data)
            appendLine()

            appendLine("────────────────────")
            appendLine("Transforms")
            appendLine("────────────────────")
            appendLine()

            var transformLinesAdded = 0

            if (data.translateCount > 0) {
                appendLine("✓ Translate: ${data.translateCount}")
                transformLinesAdded++
            }
            if (data.scaleCount > 0) {
                appendLine("✓ Scale: ${data.scaleCount}")
                transformLinesAdded++
            }
            if (data.rotateCount > 0) {
                appendLine("✓ Rotate: ${data.rotateCount}")
                transformLinesAdded++
            }
            if (data.matrixCount > 0) {
                if (data.supportedMatrixTransforms > 0) {
                    appendLine("✓ Matrix supported: ${data.supportedMatrixTransforms}")
                    transformLinesAdded++
                }
                if (data.unsupportedMatrixTransforms > 0) {
                    appendLine("⚠ Matrix unsupported: ${data.unsupportedMatrixTransforms}")
                    transformLinesAdded++
                }
            }

            if (transformLinesAdded == 0) {
                appendLine("✓ None")
            }

            if (data.textElementCount > 0 || data.tspanElementCount > 0 || data.textPathElementCount > 0 || data.svgFontGlyphCount > 0) {
                appendLine()
                appendLine("────────────────────")
                appendLine("Text")
                appendLine("────────────────────")
                appendLine()
                appendLine("✓ Text elements found: ${data.textElementCount}")
                if (data.textElementsConvertedToPaths > 0) {
                    appendLine("✓ Text converted to paths: ${data.textElementsConvertedToPaths}")
                    appendLine("✓ Glyphs rendered: ${data.textGlyphPathsEmitted}")
                    appendLine("✓ Glyph-specific advances: ${data.textGlyphSpecificAdvances}")
                    appendLine("✓ Default font advances: ${data.textDefaultFontAdvances}")
                    if (data.textGlyphNameLookups > 0) {
                        appendLine("✓ Glyph-name lookups rendered: ${data.textGlyphNameLookups}")
                    }
                    if (data.textMissingGlyphFallbacks > 0) {
                        appendLine("✓ Missing-glyph fallbacks rendered: ${data.textMissingGlyphFallbacks}")
                    }
                    if (data.textHorizontalKerningPairs > 0 || data.textVerticalKerningPairs > 0 || data.textKerningAdjustmentsApplied > 0) {
                        appendLine("✓ Kerning rules parsed: ${data.textHorizontalKerningPairs + data.textVerticalKerningPairs}")
                        appendLine("✓ <hkern> entries parsed: ${data.textHorizontalKerningPairs}")
                        appendLine("✓ <vkern> entries parsed: ${data.textVerticalKerningPairs}")
                        appendLine("✓ Kerning rules matched: ${data.textHorizontalKerningPairsMatched + data.textVerticalKerningPairsMatched}")
                        appendLine("✓ <hkern> rules matched: ${data.textHorizontalKerningPairsMatched}")
                        appendLine("✓ <vkern> rules matched: ${data.textVerticalKerningPairsMatched}")
                        appendLine("✓ Kerning adjustments applied: ${data.textKerningAdjustmentsApplied}")
                    }
                }
                if (data.textElementsApproximated > 0) {
                    appendLine("✓ Bounding-box approximations: ${data.textElementsApproximated}")
                }
                if (data.textFontFamilies.isNotEmpty()) {
                    appendLine("✓ Font families: ${data.textFontFamilies.size}")
                    data.textFontFamilies.forEach { family ->
                        appendLine(" • $family")
                    }
                }
                if (data.textFontWeights.isNotEmpty()) {
                    appendLine("✓ Font weights: ${data.textFontWeights.size}")
                    data.textFontWeights
                        .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
                        .forEach { weight ->
                            appendLine(" • $weight")
                        }
                }
                if (data.tspanElementCount > 0) {
                    appendLine("✓ Text spans processed: ${data.tspanElementCount}")
                }
                if (data.textPathsConverted > 0) {
                    appendLine("✓ Text paths converted: ${data.textPathsConverted}")
                    appendLine("✓ Glyphs placed on paths: ${data.textPathGlyphsEmitted}")
                }
                val unconvertedTextPaths = maxOf(0, data.textPathElementCount - data.textPathsConverted)
                if (unconvertedTextPaths > 0) {
                    appendLine("⚠ Text-on-path elements not converted: $unconvertedTextPaths")
                }
                if (data.svgFontGlyphCount > 0) {
                    appendLine("ℹ Embedded SVG font glyph outlines found: ${data.svgFontGlyphCount}")
                }

                val textLengthAdjustmentCount =
                    data.textLengthSpacingAdjustments + data.textLengthSpacingAndGlyphsAdjustments
                val hasTextLayout =
                    data.writingModes.isNotEmpty() ||
                    data.textAnchors.isNotEmpty() ||
                    data.dominantBaselines.isNotEmpty() ||
                    data.alignmentBaselines.isNotEmpty() ||
                    data.baselineShifts.isNotEmpty() ||
                    data.lengthAdjustModes.isNotEmpty() ||
                    data.textPathMethods.isNotEmpty() ||
                    data.textGlyphRotationsApplied > 0 ||
                    data.textLetterSpacingAdjustmentsApplied > 0 ||
                    data.textBidiRunsReordered > 0 ||
                    data.textDirections.isNotEmpty() ||
                    data.textUnicodeBidiModes.isNotEmpty() ||
                    data.textWordSpacingAdjustmentsApplied > 0 ||
                    data.textDecorationPathsEmitted > 0 ||
                    textLengthAdjustmentCount > 0

                if (hasTextLayout) {
                    appendLine()
                    appendLine("────────────────────")
                    appendLine("Text Layout")
                    appendLine("────────────────────")
                    appendLine()

                    fun appendValues(label: String, values: List<String>) {
                        if (values.isEmpty()) return
                        appendLine("✓ $label:")
                        values.forEach { value -> appendLine(" • $value") }
                    }

                    appendValues("Writing modes", data.writingModes)
                    appendValues("Text directions", data.textDirections)
                    appendValues("unicode-bidi modes", data.textUnicodeBidiModes)
                    if (data.textBidiRunsReordered > 0) {
                        appendLine("✓ Bidirectional text runs reordered: ${data.textBidiRunsReordered}")
                    }
                    appendValues("Text anchors", data.textAnchors)
                    appendValues("Dominant baselines", data.dominantBaselines)
                    appendValues("Alignment baselines", data.alignmentBaselines)
                    appendValues("Baseline shifts", data.baselineShifts)
                    appendValues("lengthAdjust modes", data.lengthAdjustModes)
                    appendValues("textPath methods", data.textPathMethods)

                    if (data.textGlyphRotationsApplied > 0) {
                        appendLine("✓ Rotated glyphs: ${data.textGlyphRotationsApplied}")
                    }
                    if (data.textLetterSpacingAdjustmentsApplied > 0) {
                        appendLine("✓ Letter-spacing gaps applied: ${data.textLetterSpacingAdjustmentsApplied}")
                    }
                    if (data.textWordSpacingAdjustmentsApplied > 0) {
                        appendLine("✓ Word-spacing whitespace advances applied: ${data.textWordSpacingAdjustmentsApplied}")
                    }
                    if (data.textDecorationPathsEmitted > 0) {
                        appendLine("✓ Text-decoration paths emitted: ${data.textDecorationPathsEmitted}")
                    }
                    if (textLengthAdjustmentCount > 0) {
                        appendLine("✓ Text-length adjustments applied: $textLengthAdjustmentCount")
                        if (data.textLengthSpacingAdjustments > 0) {
                            appendLine(" • spacing: ${data.textLengthSpacingAdjustments}")
                        }
                        if (data.textLengthSpacingAndGlyphsAdjustments > 0) {
                            appendLine(" • spacingAndGlyphs: ${data.textLengthSpacingAndGlyphsAdjustments}")
                        }
                    }
                }
            }

            appendLine()
            appendLine("────────────────────")
            appendLine("SVG Analysis")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Viewport: ${data.viewportWidth} × ${data.viewportHeight}")
            appendLine("✓ Visible SVG drawable elements: ${data.visibleDrawableElementCount}")
            appendLine("✓ Visible SVG path elements: ${data.drawableValidPathCount}")
            appendLine("✓ Empty path elements skipped: ${data.emptyPathCount}")

            if (data.useCount > 0)
                appendLine("✓ <use> references found: ${data.useCount}")

            if (data.symbolCount > 0)
                appendLine("✓ Symbol definitions: ${data.symbolCount}")

            if (data.gradientFallbackColorCount > 0)
                appendLine("✓ Gradients converted: ${data.gradientFallbackColorCount}")

            if (data.patternApproximationCount > 0)
                appendLine("✓ Patterns approximated: ${data.patternApproximationCount}")
            if (data.patternTileExpansionCount > 0) {
                appendLine("✓ Pattern tiles expanded: ${data.patternTileExpansionCount}")
                appendLine("✓ Pattern tile paths emitted: ${data.patternTilePathCount}")
            }
            if (data.patternApproximationStats.complexPatternApproximationCount > 0) {
                val label = if (data.patternTileExpansionCount > 0) "Complex pattern tile definitions" else "Complex pattern fallback fills"
                appendLine("ℹ $label: ${data.patternApproximationStats.complexPatternApproximationCount}")
            }
            if (data.patternApproximationStats.sparsePatternApproximationCount > 0) {
                val label = if (data.patternTileExpansionCount > 0) "Sparse/transparent pattern tile definitions" else "Sparse/transparent pattern fallback fills"
                appendLine("ℹ $label: ${data.patternApproximationStats.sparsePatternApproximationCount}")
            }

            if (data.markerDefinitionCount > 0 || data.appliedMarkers > 0) {
                appendLine("✓ Marker definitions: ${data.markerDefinitionCount}")
                appendLine("✓ Markers approximated: ${data.appliedMarkers}")
            }

            if (data.clipPathCount > 0) {
                appendLine("✓ Clip paths: ${data.clipPathCount}")
                appendLine("✓ Clip path references: ${data.clipPathReferenceCount}")
                appendLine("✓ Clip paths applied: ${data.appliedClipPaths}")
            }

            if (data.maskPathCount > 0 || data.maskReferenceCount > 0) {
                appendLine("✓ Masks approximated: ${data.maskPathCount}")
                appendLine("✓ Mask references: ${data.maskReferenceCount}")
                appendLine("✓ Masks applied as clip paths: ${data.appliedMasks}")
            }

            if (data.dashedStrokesDetected > 0) {
                appendLine("✓ Dashed strokes detected: ${data.dashedStrokesDetected}")
                appendLine("✓ Dashed strokes approximated: ${data.dashedStrokesApproximated}")
                if (data.oddDashListsDuplicated > 0) {
                    appendLine("✓ Odd dash lists duplicated per SVG rules: ${data.oddDashListsDuplicated}")
                }
                if (data.invalidDashOffsetFallbacks > 0) {
                    appendLine("⚠ Invalid dash offsets replaced with 0: ${data.invalidDashOffsetFallbacks}")
                }
                if (data.dashOffsetsNormalized > 0) {
                    appendLine("✓ Negative/large dash offsets normalized: ${data.dashOffsetsNormalized}")
                }
                if (data.dashTransformExactCompensations > 0) {
                    appendLine("✓ Non-scaling dash transforms compensated exactly: ${data.dashTransformExactCompensations}")
                }
                if (data.dashTransformApproximateCompensations > 0) {
                    appendLine("⚠ Non-scaling dash transforms approximated: ${data.dashTransformApproximateCompensations}")
                }
                if (data.invalidDashArrays > 0) {
                    appendLine("⚠ Invalid dash arrays detected: ${data.invalidDashArrays}")
                    appendLine("✓ Solid-stroke fallbacks used: ${data.dashSolidFallbacks}")
                }
            }

            if (data.displayNoneElementsSkipped > 0 || data.visibilityHiddenElementsSkipped > 0) {
                appendLine("✓ Hidden drawable elements skipped: ${data.displayNoneElementsSkipped + data.visibilityHiddenElementsSkipped}")
                appendLine("  • display=\"none\": ${data.displayNoneElementsSkipped}")
                appendLine("  • visibility=\"hidden/collapse\": ${data.visibilityHiddenElementsSkipped}")
            }

            if (data.nestedSvgViewportCount > 0) {
                appendLine("✓ Nested SVG viewports processed: ${data.nestedSvgViewportCount}")
                appendLine("✓ Percentage-based nested viewports: ${data.nestedSvgPercentageViewportCount}")
                appendLine("✓ Nested viewport clips applied: ${data.nestedSvgViewportClipCount}")
                appendLine("✓ Nested viewport overflow=\"hidden\": ${data.nestedSvgOverflowHiddenCount}")
                appendLine("✓ Nested viewport overflow=\"visible\": ${data.nestedSvgOverflowVisibleCount}")
                if (data.nestedSvgOverflowAutoCount > 0 || data.nestedSvgOverflowScrollCount > 0) {
                    appendLine("⚠ Nested viewport overflow auto/scroll approximated by clipping: ${data.nestedSvgOverflowAutoCount + data.nestedSvgOverflowScrollCount}")
                    appendLine("  • overflow=\"auto\": ${data.nestedSvgOverflowAutoCount}")
                    appendLine("  • overflow=\"scroll\": ${data.nestedSvgOverflowScrollCount}")
                }
                if (data.nestedSvgOverflowUnsupportedCount > 0) {
                    appendLine("⚠ Unsupported nested viewport overflow values treated as visible: ${data.nestedSvgOverflowUnsupportedCount}")
                }
            }

            if (data.nonScalingStrokesDetected > 0) {
                val exactNonScalingStrokes = maxOf(
                    0,
                    data.nonScalingStrokesCompensated - data.nonScalingStrokesUncertain
                )
                appendLine("✓ Non-scaling strokes detected: ${data.nonScalingStrokesDetected}")
                appendLine("✓ Non-scaling strokes compensated exactly: $exactNonScalingStrokes")
                if (data.nonScalingStrokesUncertain > 0) {
                    appendLine("⚠ Non-scaling strokes approximated for non-uniform scaling: ${data.nonScalingStrokesUncertain}")
                }
            }

            if (data.filterDefinitionCount > 0) {
                appendLine("✓ Filter definitions found: ${data.filterDefinitionCount}")
            }
            if (data.filterReferenceCount > 0) {
                appendLine("⚠ Filter references ignored: ${data.filterReferenceCount}")
            }

            if (data.contextPaintApproximationCount > 0) {
                appendLine("ℹ context-fill/context-stroke approximated using inherited paint.")
            }

            if (data.cssImportRuleCount > 0) {
                appendLine("✓ CSS @import rules found: ${data.cssImportRuleCount}")
                appendLine("✓ Inline CSS imports applied: ${data.cssImportedInlineRuleCount}")
                if (data.cssExternalImportCount > 0) {
                    appendLine("⚠ External CSS imports ignored: ${data.cssExternalImportCount}")
                }
            }

            if (data.imageStats.imageElementCount > 0) {
                appendLine("⚠ Image elements found: ${data.imageStats.imageElementCount}")
                if (data.imageStats.embeddedRasterImageCount > 0) {
                    appendLine("ℹ Embedded raster images: ${data.imageStats.embeddedRasterImageCount}")
                }
                if (data.imageStats.embeddedSvgImageCount > 0) {
                    appendLine("ℹ Embedded SVG image references: ${data.imageStats.embeddedSvgImageCount}")
                }
                if (data.imageStats.externalImageCount > 0) {
                    appendLine("⚠ External image references: ${data.imageStats.externalImageCount}")
                }
                if (data.imageStats.missingHrefImageCount > 0) {
                    appendLine("⚠ Image elements without href: ${data.imageStats.missingHrefImageCount}")
                }
                appendLine("ℹ Images with explicit width/height: ${data.imageStats.imageElementsWithSize}")
            }

            appendLine("✓ Style attributes: ${data.styleAttributeCount}")
            appendLine("✓ Presentation attributes: ${data.presentationStyleAttributeCount}")

            appendLine()
            appendLine("────────────────────")
            appendLine("Output")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Profile: ${data.conversionProfile}")

            appendLine(
                if (data.outputDpSize > 0)
                    "✓ Output size: ${data.outputDpSize}dp"
                else
                    "✓ Output size: Keep SVG size"
            )

            appendLine()
            appendLine("────────────────────")
            appendLine("Conversion Status")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ VectorDrawable generated")
            appendLine("✓ XML validated")
            appendLine("✓ Ready to save")

            val unapproximatedDashedStrokes = maxOf(0, data.dashedStrokesDetected - data.dashedStrokesApproximated)

            if (
                data.unsupportedWarnings.isNotEmpty() ||
                data.unsupportedMatrixTransforms > 0 ||
                data.unresolvedUseReferences > 0 ||
                unapproximatedDashedStrokes > 0 ||
                data.dashTransformApproximateCompensations > 0 ||
                data.nonScalingStrokesUncertain > 0 ||
                data.cssExternalImportCount > 0 ||
                data.imageStats.imageElementCount > 0 ||
                maxOf(0, data.textElementCount - data.textElementsApproximated - data.textElementsConvertedToPaths) > 0 ||
                data.tspanElementCount > 0 ||
                maxOf(0, data.textPathElementCount - data.textPathsConverted) > 0 ||
                data.nestedSvgOverflowAutoCount > 0 ||
                data.nestedSvgOverflowScrollCount > 0 ||
                data.nestedSvgOverflowUnsupportedCount > 0
            ) {
                appendLine()
                appendLine("────────────────────")
                appendLine("Warnings")
                appendLine("────────────────────")
                appendLine()

                if (data.unsupportedMatrixTransforms > 0) {
                    appendLine("⚠ Matrix transforms not flattened: ${data.unsupportedMatrixTransforms}")
                }

                if (data.unresolvedUseReferences > 0) {
                    appendLine("⚠ Unresolved <use> references: ${data.unresolvedUseReferences}")
                }

                if (unapproximatedDashedStrokes > 0) {
                    appendLine("⚠ Dashed strokes could not be approximated: $unapproximatedDashedStrokes")
                }
                if (data.dashTransformApproximateCompensations > 0) {
                    appendLine("⚠ Non-scaling dashed strokes under non-uniform transforms use geometric-mean compensation: ${data.dashTransformApproximateCompensations}")
                }

                if (data.nonScalingStrokesUncertain > 0) {
                    appendLine("⚠ Non-scaling stroke compensation used average scale for non-uniform transforms: ${data.nonScalingStrokesUncertain}")
                }

                if (data.nestedSvgOverflowAutoCount > 0 || data.nestedSvgOverflowScrollCount > 0) {
                    appendLine("⚠ Nested <svg> overflow=\"auto/scroll\" approximated using viewport clipping: ${data.nestedSvgOverflowAutoCount + data.nestedSvgOverflowScrollCount}")
                }

                if (data.nestedSvgOverflowUnsupportedCount > 0) {
                    appendLine("⚠ Unsupported nested <svg> overflow values treated as visible: ${data.nestedSvgOverflowUnsupportedCount}")
                }

                if (data.cssExternalImportCount > 0) {
                    appendLine("⚠ External CSS @import ignored: ${data.cssExternalImportCount}. Inline data:text/css imports are supported, but external stylesheets cannot be fetched from a standalone SVG file.")
                }

                if (data.imageStats.imageElementCount > 0) {
                    appendLine(imageConversionWarning(data.imageStats))
                }

                val handledTextCount = data.textElementsApproximated + data.textElementsConvertedToPaths
                val unapproximatedTextCount = maxOf(0, data.textElementCount - handledTextCount)
                val unconvertedTextPaths = maxOf(0, data.textPathElementCount - data.textPathsConverted)
                if (unapproximatedTextCount > 0 || unconvertedTextPaths > 0) {
                    appendLine(textConversionWarning(data))
                }

                data.unsupportedWarnings.forEach {
                    appendLine("⚠ $it")
                }
            }
        
    }


    /**
     * Returns the number shown in the report summary.
     *
     * An invalid dash array produces one user-facing conversion warning:
     * the dashed stroke could not be approximated and a solid fallback was
     * used. The converter's raw warning count may also include the internal
     * invalid-array diagnostic, so remove that duplicate from the aggregate.
     */
    private fun StringBuilder.appendG315GuardedProductionTrial(
        data: SvgConversionReportData
    ) {
        if (!data.g315TrialAttempted) return

        appendLine()
        appendLine("G3.19 guarded production convergence")
        appendLine("• Mode: production-enabled, fail-closed")

        when {
            !data.g315CandidateChanged ->
                appendLine("✓ Candidate unchanged; pass-1 production XML retained")
            data.g315GuardAccepted ->
                appendLine("✓ Candidate passed every guard and was applied to production XML")
            data.g315GuardRejected -> {
                appendLine("⚠ Candidate rejected; pass-1 production XML retained")
                if (data.g315RejectionReason.isNotBlank()) {
                    appendLine("• Rejection reason: ${data.g315RejectionReason}")
                }
            }
            else ->
                appendLine("⚠ Trial completed without an accepted/rejected classification")
        }

        appendLine("• Paths examined: ${data.g315PathsExamined}")
        appendLine("• Paths changed by candidate: ${data.g315PathsChanged}")
        if (data.g315CandidateChanged) {
            appendLine(
                "• Matched independent full pass 2: " +
                    if (data.g315MatchedIndependentSecondPass) "Yes" else "No"
            )
            appendLine(
                "• Independent pass 2 fixed point verified: " +
                    if (data.g315FixedPointVerified) "Yes" else "No"
            )
            appendLine(
                "• Candidate final validation passed: " +
                    if (data.g315FinalValidationPassed) "Yes" else "No"
            )
            appendLine("• G3.13 geometry comparisons: ${data.g315GeometryComparisons}")
            appendLine("• G3.13 geometry mismatches: ${data.g315GeometryMismatchCount}")
            appendLine(
                "• Exact ordered-traversal short-circuits: " +
                    data.g315ExactShortCircuitCount
            )
            appendLine(
                "• Fallback bidirectional comparisons: " +
                    data.g315FallbackBidirectionalCount
            )
            appendLine("• Comparator failures: ${data.g315ComparatorFailureCount}")
            appendLine(
                "• XML characters: ${data.g315CharactersBefore} → " +
                    data.g315CharactersAfter
            )
            appendLine("• Characters saved: ${data.g315CharactersSaved}")
            appendLine("• Characters added: ${data.g315CharactersAdded}")
        }
        appendLine(
            "• Candidate time: " +
                formatNanosAsMilliseconds(data.g315CandidateNanos)
        )
        appendLine(
            "• Comparator time: " +
                formatNanosAsMilliseconds(data.g315ComparatorNanos)
        )
        appendLine(
            "• Total guarded convergence time: " +
                formatNanosAsMilliseconds(data.g315GuardNanos)
        )
    }

    private fun StringBuilder.appendFinalOutputValidation(
        data: SvgConversionReportData
    ) {
        if (data.validatedPathDataCount <= 0 &&
            data.finalOutputValidationNanos <= 0L
        ) return

        appendLine()
        appendLine("Final output validation")

        if (data.finalOutputValidationPassed) {
            appendLine("✓ Final VectorDrawable validation passed")
        } else {
            appendLine("⚠ Final VectorDrawable validation found issues")
        }

        appendLine("• Path data values validated: ${data.validatedPathDataCount}")

        if (data.invalidPathDataCount > 0)
            appendLine("⚠ Invalid pathData values: ${data.invalidPathDataCount}")
        if (data.nonFiniteNumberCount > 0)
            appendLine("⚠ Non-finite numeric values: ${data.nonFiniteNumberCount}")
        if (data.malformedStructureCount > 0)
            appendLine("⚠ Structural XML issues: ${data.malformedStructureCount}")
        if (data.invalidViewportCount > 0)
            appendLine("⚠ Invalid viewport dimensions: ${data.invalidViewportCount}")
        if (data.unsupportedOutputConstructCount > 0)
            appendLine(
                "⚠ Unsupported output constructs: " +
                    data.unsupportedOutputConstructCount
            )

        appendLine(
            "• Validation time: " +
                formatNanosAsMilliseconds(data.finalOutputValidationNanos)
        )
    }

    private fun StringBuilder.appendOptimizerValidation(data: SvgConversionReportData) {
        if (data.optimizerValidationPasses <= 0) return

        appendLine()
        appendLine("Optimizer validation")
        when {
            data.optimizerIdempotenceVerified ->
                appendLine("✓ Optimizer idempotence verified")
            data.optimizerReachedFixedPoint ->
                appendLine(
                    "⚠ Optimizer required ${data.optimizerStabilityPasses} passes to stabilize"
                )
            else ->
                appendLine(
                    "⚠ Optimizer did not reach a fixed point after " +
                        "${data.optimizerStabilityPasses} passes"
                )
        }
        appendLine("• Validation passes: ${data.optimizerValidationPasses}")
        appendLine(
            "• Production pass: " +
                formatNanosAsMilliseconds(data.optimizerProductionPassNanos)
        )
        appendLine(
            "• Idempotence pass: " +
                formatNanosAsMilliseconds(data.optimizerIdempotencePassNanos)
        )
        if (data.optimizerValidationPasses >= 3) {
            appendLine(
                "• Fixed-point pass: " +
                    formatNanosAsMilliseconds(data.optimizerFixedPointPassNanos)
            )
        }
        appendLine(
            "• XML changed after pass 1: " +
                if (data.optimizerFirstPassChangedXml) "Yes" else "No"
        )
        appendLine(
            "• XML changed after pass 2: " +
                if (data.optimizerSecondPassChangedXml) "Yes" else "No"
        )
        if (data.optimizerValidationPasses >= 3) {
            appendLine(
                "• XML changed after pass 3: " +
                    if (data.optimizerThirdPassChangedXml) "Yes" else "No"
            )
        }
        val cacheLookups =
            data.optimizerValidationPathCacheHits +
                data.optimizerValidationPathCacheMisses
        if (cacheLookups > 0) {
            appendLine(
                "• Unchanged path inputs reused: " +
                    data.optimizerValidationPathCacheHits
            )
            if (data.optimizerValidationPathCacheMisses > 0) {
                appendLine(
                    "• Changed path inputs recomputed: " +
                        data.optimizerValidationPathCacheMisses
                )
            }
        }
        val idempotenceStages = listOf(
            "Path syntax and colors" to data.optimizerIdempotencePathSyntaxNanos,
            "Pruning and group cleanup" to data.optimizerIdempotencePruningGroupCleanupNanos,
            "Transform optimization" to data.optimizerIdempotenceTransformOptimizationNanos,
            "Deduplication and merging" to data.optimizerIdempotenceDeduplicationMergeNanos,
            "Numeric cleanup" to data.optimizerIdempotenceNumericCleanupNanos,
            "Final XML formatting" to data.optimizerIdempotenceFinalFormattingNanos,
            "Final equality comparison" to data.optimizerIdempotenceEqualityComparisonNanos
        ).filter { (_, durationNanos) -> durationNanos > 0L }

        if (idempotenceStages.isNotEmpty()) {
            appendLine()
            appendLine("Idempotence pass breakdown")
            val measuredNanos = idempotenceStages.sumOf { it.second }
            idempotenceStages.forEach { (label, durationNanos) ->
                val percentage = nanosPercentageLabel(durationNanos, measuredNanos)
                appendLine(
                    "• $label: ${formatNanosAsMilliseconds(durationNanos)} ($percentage)"
                )
                if (label == "Path syntax and colors") {
                    appendNestedTimingBreakdown(
                        parentNanos = durationNanos,
                        stages = listOf(
                            "Tokenization and normalization" to
                                data.optimizerIdempotencePathTokenizationNormalizationNanos,
                            "Geometry cleanup" to
                                data.optimizerIdempotencePathGeometryCleanupNanos,
                            "Command minimization" to
                                data.optimizerIdempotencePathCommandMinimizationNanos,
                            "Global numeric serialization" to
                                data.optimizerIdempotencePathNumericSerializationNanos,
                            "Color normalization" to
                                data.optimizerIdempotenceColorNormalizationNanos
                        )
                    )
                }
            }
            appendLine("• Paths examined: ${data.optimizerIdempotencePathsExamined}")
            if (data.optimizerIdempotenceFinalPassStablePathsRegistered > 0) {
                appendLine(
                    "• Final pass-1 stable paths registered: " +
                        data.optimizerIdempotenceFinalPassStablePathsRegistered
                )
            }
            val secondPassLookups =
                data.optimizerIdempotencePathCacheHits +
                    data.optimizerIdempotencePathCacheMisses
            if (secondPassLookups > 0) {
                appendLine(
                    "• Second-pass path cache: " +
                        "${data.optimizerIdempotencePathCacheHits} reused, " +
                        "${data.optimizerIdempotencePathCacheMisses} recomputed"
                )
                if (data.optimizerIdempotenceStableOutputCacheHits > 0) {
                    appendLine(
                        "  ◦ Stable pass-1 outputs reused: " +
                            data.optimizerIdempotenceStableOutputCacheHits
                    )
                }
                if (data.optimizerIdempotenceRegularCacheHits > 0) {
                    appendLine(
                        "  ◦ Ordinary duplicate-input cache hits: " +
                            data.optimizerIdempotenceRegularCacheHits
                    )
                }
            }
            if (data.optimizerIdempotenceXmlCharactersBefore > 0) {
                appendLine(
                    "• Idempotence XML size: " +
                        "${data.optimizerIdempotenceXmlCharactersBefore} → " +
                        "${data.optimizerIdempotenceXmlCharactersAfter} characters"
                )
            }
        }

        appendLine(
            "• Validation time: " +
                formatNanosAsMilliseconds(data.optimizerValidationNanos)
        )
    }

    private fun StringBuilder.appendOptimizationImpact(data: SvgConversionReportData) {
        val savings = listOf(
            "Path syntax and colors" to data.optimizationPathSyntaxCharactersSaved,
            "Pruning and group cleanup" to data.optimizationPruningCleanupCharactersSaved,
            "Transform optimization" to data.optimizationTransformCharactersSaved,
            "Deduplication and merging" to data.optimizationDeduplicationCharactersSaved,
            "Numeric cleanup" to data.optimizationNumericCleanupCharactersSaved,
            "Final formatting" to data.optimizationFormattingCharactersSaved
        )
            .filter { (_, charactersSaved) -> charactersSaved > 0 }
            .sortedByDescending { (_, charactersSaved) -> charactersSaved }

        if (savings.isEmpty()) return

        appendLine()
        appendLine("Largest optimization savings")
        savings.forEach { (label, charactersSaved) ->
            appendLine("• $label: ${formatCharacterCount(charactersSaved)} saved")
        }
        appendLine("Stage savings are not additive.")
    }

    private fun StringBuilder.appendOptimizationQualityMetrics(data: SvgConversionReportData) {
        val xmlCharactersBefore = data.optimizedXmlCharactersBefore.coerceAtLeast(0)
        val xmlCharactersAfter = data.optimizedXmlCharactersAfter.coerceAtLeast(0)
        val netCharactersSaved = (xmlCharactersBefore - xmlCharactersAfter).coerceAtLeast(0)

        val hasNetReduction = netCharactersSaved > 0 && xmlCharactersBefore > 0
        val hasPerPathReduction = netCharactersSaved > 0 && data.pathDataOptimizedCount > 0
        val hasThroughput = netCharactersSaved > 0 && data.outputOptimizationNanos > 0L

        if (!hasNetReduction && !hasPerPathReduction && !hasThroughput) return

        appendLine()
        appendLine("Optimization quality")

        if (hasNetReduction) {
            val reductionPercent = netCharactersSaved * 100.0 / xmlCharactersBefore.toDouble()
            appendLine(
                "• Net XML reduction: ${formatCharacterCount(netCharactersSaved)} (" +
                    String.format(java.util.Locale.US, "%.1f%%", reductionPercent) +
                    ")"
            )
        }

        if (hasPerPathReduction) {
            val averageReduction = netCharactersSaved.toDouble() / data.pathDataOptimizedCount
            appendLine(
                "• Average reduction per optimized path: " +
                    String.format(java.util.Locale.US, "%.1f characters", averageReduction)
            )
        }

        if (hasThroughput) {
            val throughput = netCharactersSaved.toDouble() / (data.outputOptimizationNanos / 1_000_000.0)
            appendLine(
                "• Optimization throughput: " +
                    String.format(java.util.Locale.US, "%.1f characters/ms", throughput)
            )
        }
    }

    private fun formatCharacterCount(count: Int): String =
        if (count == 1) "1 character" else "$count characters"

    private fun StringBuilder.appendPerformanceBreakdown(
        data: SvgConversionReportData,
        elapsedNanosOverride: Long? = null,
        reportGenerationNanosOverride: Long? = null
    ) {
        val reportGenerationNanos =
            reportGenerationNanosOverride ?: data.reportGenerationNanos
        val measuredStages = listOf(
            "SVG parsing" to data.svgParsingNanos,
            "Style resolution" to data.styleResolutionNanos,
            "Tree conversion" to data.treeConversionNanos,
            "Optimization" to data.outputOptimizationNanos,
            "Analysis" to data.reportAnalysisNanos,
            "Report generation" to reportGenerationNanos
        )

        val totalNanos = when {
            elapsedNanosOverride != null -> elapsedNanosOverride
            data.elapsedNanos > 0L -> data.elapsedNanos
            data.elapsedMs > 0L -> data.elapsedMs * 1_000_000L
            else -> measuredStages.sumOf { (_, durationNanos) -> durationNanos.coerceAtLeast(0L) }
        }
        val measuredNanos = measuredStages.sumOf { (_, durationNanos) ->
            durationNanos.coerceAtLeast(0L)
        }
        val otherFrameworkNanos = (totalNanos - measuredNanos).coerceAtLeast(0L)

        appendLine()
        appendLine("Performance")
        appendLine()
        appendLine("Conversion time")
        appendLine(formatPerformanceDuration(totalNanos))
        appendLine()
        appendLine("Pipeline")
        measuredStages.forEach { (label, durationNanos) ->
            val percentage = performancePercentageNanos(durationNanos, totalNanos)
            appendLine("• $label: ${formatPerformanceDuration(durationNanos)} ($percentage)")
        }
        if (otherFrameworkNanos >= 1_000_000L) {
            val percentage = performancePercentageNanos(otherFrameworkNanos, totalNanos)
            appendLine("• Other / framework: ${formatPerformanceDuration(otherFrameworkNanos)} ($percentage)")
        }

        val hasOptimizationBreakdown = listOf(
            data.optimizationPathSyntaxNanos,
            data.optimizationPruningCleanupNanos,
            data.optimizationTransformsNanos,
            data.optimizationDeduplicationNanos,
            data.optimizationNumericCleanupNanos,
            data.optimizationFormattingNanos
        ).any { it > 0L }

        if (hasOptimizationBreakdown) {
            appendLine()
            appendLine("Production pass breakdown")
            appendOptimizationBreakdown(data)
        }
    }

    private fun StringBuilder.appendOptimizationBreakdown(data: SvgConversionReportData) {
        val stages = listOf(
            "Path syntax and colors" to data.optimizationPathSyntaxNanos,
            "Pruning and group cleanup" to data.optimizationPruningCleanupNanos,
            "Transform optimization" to data.optimizationTransformsNanos,
            "Deduplication and merging" to data.optimizationDeduplicationNanos,
            "Numeric cleanup" to data.optimizationNumericCleanupNanos,
            "Final formatting" to data.optimizationFormattingNanos
        ).filter { (_, durationNanos) -> durationNanos > 0L }

        if (stages.isEmpty()) return

        val totalNanos = stages.sumOf { (_, durationNanos) -> durationNanos }
        stages.forEach { (label, durationNanos) ->
            val percentage = nanosPercentageLabel(durationNanos, totalNanos)
            appendLine("• $label: ${formatNanosAsMilliseconds(durationNanos)} ($percentage)")

            when (label) {
                "Path syntax and colors" -> {
                    appendNestedTimingBreakdown(
                        parentNanos = durationNanos,
                        stages = listOf(
                            "Tokenization and normalization" to
                                data.optimizationPathTokenizationNanos,
                            "Geometry cleanup" to
                                data.optimizationPathGeometryCleanupNanos,
                            "Command minimization" to
                                data.optimizationPathCommandMinimizationNanos,
                            "Global numeric serialization" to
                                data.optimizationPathNumericSerializationNanos,
                            "Color normalization" to
                                data.optimizationColorNormalizationNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Geometry cleanup",
                        parentNanos = data.optimizationPathGeometryCleanupNanos,
                        stages = listOf(
                            "Redundant-segment cleanup" to
                                data.optimizationPathRedundantSegmentCleanupNanos,
                            "Arc normalization and minimization" to
                                data.optimizationPathArcCleanupNanos,
                            "Curve simplification" to
                                data.optimizationPathCurveSimplificationNanos,
                            "Collinear-line consolidation" to
                                data.optimizationPathCollinearConsolidationNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Curve simplification",
                        parentNanos = data.optimizationPathCurveSimplificationNanos,
                        stages = listOf(
                            "Exact cubic → quadratic" to data.optimizationCurveCubicToQuadraticNanos,
                            "Straight Bézier → line" to data.optimizationCurveStraightBezierNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Exact cubic → quadratic",
                        parentNanos = data.optimizationCurveCubicToQuadraticNanos,
                        stages = listOf(
                            "Parse and setup" to data.optimizationCurveCubicParseSetupNanos,
                            "Segment scan / exact-equivalence checks" to data.optimizationCurveCubicScanNanos,
                            "Rebuild" to data.optimizationCurveCubicRebuildNanos,
                            "Validation / fallback" to data.optimizationCurveCubicValidationNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Straight Bézier → line",
                        parentNanos = data.optimizationCurveStraightBezierNanos,
                        stages = listOf(
                            "Parse and setup" to data.optimizationCurveStraightParseSetupNanos,
                            "Segment scan / collinearity checks" to data.optimizationCurveStraightScanNanos,
                            "Rebuild" to data.optimizationCurveStraightRebuildNanos,
                            "Validation / fallback" to data.optimizationCurveStraightValidationNanos
                        )
                    )
                    appendCurveReuseProfiling(data)
                    appendDeepTimingBreakdown(
                        parentLabel = "Command minimization",
                        parentNanos = data.optimizationPathCommandMinimizationNanos,
                        stages = listOf(
                            "Local command shortening" to
                                data.optimizationPathCommandLocalShorteningNanos,
                            "Global parse and setup" to
                                data.optimizationPathCommandGlobalParseSetupNanos,
                            "Global candidate generation" to
                                data.optimizationPathCommandGlobalCandidateGenerationNanos,
                            "Global dynamic programming" to
                                data.optimizationPathCommandGlobalDynamicProgrammingNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Global dynamic programming",
                        parentNanos = data.optimizationPathCommandGlobalDynamicProgrammingNanos,
                        stages = listOf(
                            "Transition evaluation" to
                                data.optimizationPathCommandGlobalTransitionEvaluationNanos,
                            "Separator / command-omission costs" to
                                data.optimizationPathCommandGlobalSeparatorOmissionCostNanos,
                            "Segment number/string encoding" to
                                data.optimizationPathCommandGlobalSegmentEncodingNanos,
                            "State creation and path extension" to
                                data.optimizationPathCommandGlobalStateCreationNanos,
                            "Best-state comparison" to
                                data.optimizationPathCommandGlobalBestStateComparisonNanos,
                            "Final selection and reconstruction" to
                                data.optimizationPathCommandGlobalReconstructionNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "State creation and path extension",
                        parentNanos = data.optimizationPathCommandGlobalStateCreationNanos,
                        stages = listOf(
                            "State-key creation" to
                                data.optimizationPathCommandGlobalStateKeyCreationNanos,
                            "Partial-string concatenation" to
                                data.optimizationPathCommandGlobalStateStringConcatenationNanos,
                            "Metadata propagation" to
                                data.optimizationPathCommandGlobalStateMetadataPropagationNanos,
                            "Path-state object allocation" to
                                data.optimizationPathCommandGlobalStatePathAllocationNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "State-key creation",
                        parentNanos = data.optimizationPathCommandGlobalStateKeyCreationNanos,
                        stages = listOf(
                            "Key-field preparation" to
                                data.optimizationPathCommandGlobalStateKeyFieldPreparationNanos,
                            "Composite key allocation" to
                                data.optimizationPathCommandGlobalStateKeyAllocationNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Key-field preparation",
                        parentNanos = data.optimizationPathCommandGlobalStateKeyFieldPreparationNanos,
                        stages = listOf(
                            "Previous-command state" to
                                data.optimizationPathCommandGlobalStateKeyPreviousCommandNanos,
                            "Previous-number state reuse" to
                                data.optimizationPathCommandGlobalStateKeyPreviousNumberNanos,
                            "Previous-axis-direction state" to
                                data.optimizationPathCommandGlobalStateKeyAxisDirectionNanos
                        ),
                        showZeroStages = true
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Best-state comparison",
                        parentNanos = data.optimizationPathCommandGlobalBestStateComparisonNanos,
                        stages = listOf(
                            "State-map lookup" to
                                data.optimizationPathCommandGlobalBestStateMapLookupNanos,
                            "Best-state decision" to
                                data.optimizationPathCommandGlobalBestStateDecisionNanos,
                            "Best-state replacement" to
                                data.optimizationPathCommandGlobalBestStateReplacementNanos
                        )
                    )

                    if (data.optimizationPathCommandGlobalStateMapLookupCalls > 0) {
                        val lookups = data.optimizationPathCommandGlobalStateMapLookupCalls
                        val hits = data.optimizationPathCommandGlobalStateMapLookupHits
                        val misses = data.optimizationPathCommandGlobalStateMapLookupMisses
                        val hitRate = hits.toDouble() * 100.0 / lookups.toDouble()
                        append("      ▫ DP state-map access\n")
                        append("        · Lookup calls: ").append(lookups).append('\n')
                        append("        · Existing-state hits: ").append(hits)
                            .append(" (").append(String.format(java.util.Locale.US, "%.1f%%", hitRate)).append(")\n")
                        append("        · New-state misses: ").append(misses).append('\n')
                        append("        · New-state inserts: ")
                            .append(data.optimizationPathCommandGlobalStateMapInsertions).append('\n')
                        append("        · Existing-state replacements: ")
                            .append(data.optimizationPathCommandGlobalStateMapReplacements).append('\n')
                    }

                    if (data.optimizationPathCommandGlobalSegmentEncodingRequests > 0) {
                        val requests = data.optimizationPathCommandGlobalSegmentEncodingRequests
                        val hits = data.optimizationPathCommandGlobalSegmentEncodingCacheHits
                        val unique = data.optimizationPathCommandGlobalSegmentEncodingUniqueKeys
                        val hitRate = hits.toDouble() * 100.0 / requests.toDouble()
                        append("      ▫ Global segment encoding reuse\n")
                        append("        · Encoding requests: ").append(requests).append('\n')
                        append("        · Cache hits: ").append(hits)
                            .append(" (").append(String.format(java.util.Locale.US, "%.1f%%", hitRate)).append(")\n")
                        append("        · Unique encodings: ").append(unique).append('\n')
                    }

                    appendDeepTimingBreakdown(
                        parentLabel = "Local command shortening",
                        parentNanos = data.optimizationPathCommandLocalShorteningNanos,
                        stages = listOf(
                            "Local parse and setup" to
                                data.optimizationPathCommandLocalParseSetupNanos,
                            "Absolute/relative candidates" to
                                data.optimizationPathCommandLocalAbsoluteRelativeCandidateNanos,
                            "H/V axis candidates" to
                                data.optimizationPathCommandLocalAxisCandidateNanos,
                            "S/T shorthand candidates" to
                                data.optimizationPathCommandLocalSmoothShorthandCandidateNanos,
                            "Candidate encoding and selection" to
                                data.optimizationPathCommandLocalEncodingSelectionNanos,
                            "State bookkeeping" to
                                data.optimizationPathCommandLocalStateBookkeepingNanos
                        )
                    )

                    appendDeepTimingBreakdown(
                        parentLabel = "Candidate encoding and selection",
                        parentNanos = data.optimizationPathCommandLocalEncodingSelectionNanos,
                        stages = listOf(
                            "Numeric serialization" to
                                data.optimizationPathCommandLocalNumericSerializationNanos,
                            "Separator calculation" to
                                data.optimizationPathCommandLocalSeparatorCalculationNanos,
                            "Command-letter omission checks" to
                                data.optimizationPathCommandLocalCommandOmissionNanos,
                            "Candidate string construction" to
                                data.optimizationPathCommandLocalStringConstructionNanos,
                            "Length comparison / winner selection" to
                                data.optimizationPathCommandLocalWinnerSelectionNanos
                        )
                    )

                    if (data.optimizationPathCommandLocalNumericSerializationCalls > 0) {
                        val calls = data.optimizationPathCommandLocalNumericSerializationCalls
                        val hits = data.optimizationPathCommandLocalNumericSerializationCacheHits
                        val unique = data.optimizationPathCommandLocalNumericSerializationUniqueValues
                        val hitRate = hits.toDouble() * 100.0 / calls.toDouble()
                        append("      ▫ Numeric serialization reuse\n")
                        append("        · Calls: ").append(calls).append('\n')
                        append("        · Cache hits: ").append(hits)
                            .append(" (").append(String.format(java.util.Locale.US, "%.1f%%", hitRate)).append(")\n")
                        append("        · Unique exact values: ").append(unique).append('\n')
                    }
                }
                "Transform optimization" -> {
                    appendNestedTimingBreakdown(
                        parentNanos = durationNanos,
                        stages = listOf(
                            "Identity cleanup and composition" to
                                data.optimizationTransformIdentityCompositionNanos,
                            "Factoring and group flattening" to
                                data.optimizationTransformFactoringFlatteningNanos,
                            "Scale flattening" to
                                data.optimizationTransformScaleFlatteningNanos,
                            "Rotation and translation flattening" to
                                data.optimizationTransformRotationTranslationNanos,
                            "Coalescing and canonicalization" to
                                data.optimizationTransformCanonicalizationNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Scale flattening",
                        parentNanos = data.optimizationTransformScaleFlatteningNanos,
                        stages = listOf(
                            "Uniform positive scale" to
                                data.optimizationTransformUniformScaleFlatteningNanos,
                            "Non-uniform fill-only scale" to
                                data.optimizationTransformNonUniformScaleFlatteningNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Uniform positive scale",
                        parentNanos = data.optimizationTransformUniformScaleFlatteningNanos,
                        stages = listOf(
                            "Group discovery" to
                                data.optimizationTransformUniformScaleGroupDiscoveryNanos,
                            "Eligibility and safety checks" to
                                data.optimizationTransformUniformScaleEligibilityChecksNanos,
                            "Path scaling and normalization" to
                                data.optimizationTransformUniformScalePathScalingNanos,
                            "Stroke-width adjustment" to
                                data.optimizationTransformUniformScaleStrokeAdjustmentNanos,
                            "Canonicalization and size gating" to
                                data.optimizationTransformUniformScaleCanonicalizationCostingNanos,
                            "XML replacement" to
                                data.optimizationTransformUniformScaleXmlReplacementNanos
                        )
                    )
                    appendDeepTimingBreakdown(
                        parentLabel = "Path scaling and normalization",
                        parentNanos = data.optimizationTransformUniformScalePathScalingNanos,
                        stages = listOf(
                            "Tokenization" to
                                data.optimizationTransformUniformScalePathParseTokenizeNanos,
                            "Numeric parsing / segment construction" to
                                data.optimizationTransformUniformScalePathNumericParseNanos,
                            "Coordinate transformation" to
                                data.optimizationTransformUniformScalePathCoordinateMathNanos,
                            "Number formatting" to
                                data.optimizationTransformUniformScalePathNumberFormattingNanos,
                            "Path reconstruction" to
                                data.optimizationTransformUniformScalePathReconstructionNanos,
                            "Final path normalization / optimization" to
                                data.optimizationTransformUniformScalePathNormalizationNanos
                        )
                    )
                    if (data.optimizationTransformUniformScalePathArcHandlingNanos > 0L) {
                        append("      ▫ Arc-specific scaling subset\n")
                        append("        · Arc parameter handling: ")
                            .append(formatNanosAsMilliseconds(data.optimizationTransformUniformScalePathArcHandlingNanos))
                            .append('\n')
                    }
                    appendP6ProductionProfiling(data)
                    if (data.optimizationTransformUniformScaleCandidatesConsidered > 0) {
                        append("      ▫ Uniform scale candidate flow\n")
                        append("        · Candidates considered: ")
                            .append(data.optimizationTransformUniformScaleCandidatesConsidered).append('\n')
                        append("        · Candidates rejected: ")
                            .append(data.optimizationTransformUniformScaleCandidatesRejected).append('\n')
                        append("        · Proposals accepted: ")
                            .append(data.optimizationTransformUniformScaleProposalsAccepted).append('\n')
                    }
                }
            }
        }
    }

    private fun StringBuilder.appendP6ProductionProfiling(
        data: SvgConversionReportData
    ) {
        val attempts = data.optimizationTransformUniformScalePostScaleP6Attempts
        if (attempts <= 0) return

        append("      ▫ P6 post-scale production\n")
        append("        · P6 attempts: ").append(attempts).append('\n')
        append("        · P6 accepted: ")
            .append(data.optimizationTransformUniformScalePostScaleP6Accepted)
            .append('\n')
        append("        · Full-optimizer fallbacks: ")
            .append(data.optimizationTransformUniformScalePostScaleP6Fallbacks)
            .append('\n')
        if (data.optimizationTransformUniformScalePostScaleP6ParserFallbacks > 0) {
            append("          ◦ Parser validation: ")
                .append(data.optimizationTransformUniformScalePostScaleP6ParserFallbacks)
                .append('\n')
        }
        if (data.optimizationTransformUniformScalePostScaleP6InternalFallbacks > 0) {
            append("          ◦ Internal failure: ")
                .append(data.optimizationTransformUniformScalePostScaleP6InternalFallbacks)
                .append('\n')
        }
        append("        · P6 optimization time: ")
            .append(formatPerformanceDuration(
                data.optimizationTransformUniformScalePostScaleP6OptimizationNanos
            ))
            .append('\n')
        append("        · Parser validation time: ")
            .append(formatPerformanceDuration(
                data.optimizationTransformUniformScalePostScaleP6ParserValidationNanos
            ))
            .append('\n')
        if (data.optimizationTransformUniformScalePostScaleFullFallbackNanos > 0L) {
            append("        · Full fallback time: ")
                .append(formatPerformanceDuration(
                    data.optimizationTransformUniformScalePostScaleFullFallbackNanos
                ))
                .append('\n')
        }
    }

    private fun StringBuilder.appendCurveReuseProfiling(
        data: SvgConversionReportData
    ) {
        if (data.optimizationCurveParseCalls <= 0) return

        append("\nCurve parse / rebuild reuse\n")
        append("• Curve parse calls: ${data.optimizationCurveParseCalls}\n")
        append("• Duplicate second-pass parses avoided: ${data.optimizationCurveDuplicateParseInputs}\n")
        append("• Parsed representations reused by second pass: ${data.optimizationCurveSecondPassReparsedUnchangedInput}\n")
        append("• Paths changed by cubic → quadratic: ${data.optimizationCurveCubicChangedPaths}\n")
        append("• Paths changed by straight Bézier → line: ${data.optimizationCurveStraightChangedPaths}\n")
        append("• Rebuild attempts: ${data.optimizationCurveRebuildAttempts}\n")
        append("• Rebuilds identical to input: ${data.optimizationCurveRebuildNoOpResults}\n")
        append("• Validation calls: ${data.optimizationCurveValidationCalls}\n")
        append("  ◦ Accepted: ${data.optimizationCurveValidationAccepted}\n")
        append("  ◦ Rejected: ${data.optimizationCurveValidationRejected}\n")
        append("• Rebuilds rejected for size before validation: ${data.optimizationCurveRebuildRejectedForSize}\n")
    }

    private fun StringBuilder.appendDeepTimingBreakdown(
        parentLabel: String,
        parentNanos: Long,
        stages: List<Pair<String, Long>>,
        showZeroStages: Boolean = false
    ) {
        if (parentNanos <= 0L) return
        val measuredStages = if (showZeroStages) {
            stages
        } else {
            stages.filter { (_, durationNanos) -> durationNanos > 0L }
        }
        if (measuredStages.isEmpty()) return

        appendLine("    ▫ $parentLabel detail")
        measuredStages.forEach { (label, durationNanos) ->
            val percentage = nanosPercentageLabel(durationNanos, parentNanos)
            appendLine(
                "      · $label: ${formatDeepProfileDuration(durationNanos)} " +
                    "($percentage of $parentLabel)"
            )
        }
    }

    private fun formatDeepProfileDuration(durationNanos: Long): String {
        if (durationNanos <= 0L) return "0 ms"
        if (durationNanos < 1_000L) return "<0.001 ms"
        if (durationNanos < 100_000L) {
            return String.format(
                java.util.Locale.US,
                "%.3f ms",
                durationNanos / 1_000_000.0
            )
        }
        return formatNanosAsMilliseconds(durationNanos)
    }

    private fun StringBuilder.appendNestedTimingBreakdown(
        parentNanos: Long,
        stages: List<Pair<String, Long>>
    ) {
        stages.filter { (_, durationNanos) -> durationNanos > 0L }
            .forEach { (label, durationNanos) ->
                val percentage = nanosPercentageLabel(durationNanos, parentNanos)
                appendLine(
                    "  ◦ $label: ${formatNanosAsMilliseconds(durationNanos)} " +
                        "($percentage of stage)"
                )
            }
    }

    private fun formatNanosAsMilliseconds(durationNanos: Long): String {
        val milliseconds = durationNanos / 1_000_000.0
        return if (milliseconds >= 10.0) {
            "${milliseconds.toLong()} ms"
        } else {
            String.format(java.util.Locale.US, "%.1f ms", milliseconds)
        }
    }

    private fun nanosPercentageLabel(durationNanos: Long, totalNanos: Long): String {
        if (durationNanos <= 0L || totalNanos <= 0L) return "0%"

        val exactPercentage = durationNanos * 100.0 / totalNanos.toDouble()
        if (exactPercentage < 1.0) return "<1%"

        val roundedPercentage = (exactPercentage + 0.5).toInt().coerceIn(1, 100)
        return "$roundedPercentage%"
    }

    private fun formatPerformanceDuration(durationNanos: Long): String {
        val safeNanos = durationNanos.coerceAtLeast(0L)
        return if (safeNanos < 1_000_000L) {
            String.format(java.util.Locale.US, "%.3f ms", safeNanos / 1_000_000.0)
        } else {
            val milliseconds = safeNanos / 1_000_000.0
            if (milliseconds < 10.0) {
                String.format(java.util.Locale.US, "%.2f ms", milliseconds)
            } else if (milliseconds < 100.0) {
                String.format(java.util.Locale.US, "%.1f ms", milliseconds)
            } else {
                String.format(java.util.Locale.US, "%.0f ms", milliseconds)
            }
        }
    }

    private fun performancePercentageNanos(durationNanos: Long, totalNanos: Long): String {
        if (durationNanos <= 0L || totalNanos <= 0L) return "<1%"
        val percent = durationNanos.toDouble() * 100.0 / totalNanos.toDouble()
        return if (percent < 1.0) "<1%" else "${percent.toInt().coerceAtMost(100)}%"
    }

    private fun performancePercentage(durationMs: Long, totalMs: Long): Int {
        if (durationMs <= 0L || totalMs <= 0L) return 0

        return (((durationMs * 100.0) / totalMs) + 0.5)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun aggregateWarningCount(data: SvgConversionReportData): Int {
        val unapproximatedDashedStrokes =
            maxOf(0, data.dashedStrokesDetected - data.dashedStrokesApproximated)

        val duplicatedInvalidDashWarnings =
            minOf(data.invalidDashArrays, unapproximatedDashedStrokes)

        return maxOf(0, data.warningCount - duplicatedInvalidDashWarnings)
    }


    private data class CompatibilitySummary(
        val stars: String,
        val label: String,
        val fidelityPercent: Int,
        val converted: List<String>,
        val approximated: List<String>,
        val ignored: List<String>,
        val unsupported: List<String>
    )

    private fun StringBuilder.appendCompatibilitySummary(data: SvgConversionReportData) {
        val summary = compatibilitySummary(data)

        appendLine("${summary.stars} ${summary.label}")
        appendLine("Estimated visual fidelity: ~${summary.fidelityPercent}%")

        if (summary.converted.isNotEmpty()) {
            appendLine()
            appendLine("Converted")
            summary.converted.forEach { appendLine("✓ $it") }
        }

        if (summary.approximated.isNotEmpty()) {
            appendLine()
            appendLine("Approximated")
            summary.approximated.forEach { appendLine("✓ $it") }
        }

        if (summary.ignored.isNotEmpty()) {
            appendLine()
            appendLine("Ignored")
            summary.ignored.forEach { appendLine("⚠ $it") }
        }

        if (summary.unsupported.isNotEmpty()) {
            appendLine()
            appendLine("Unsupported")
            summary.unsupported.forEach { appendLine("⚠ $it") }
        }

        appendLine()
    }

    private fun compatibilitySummary(data: SvgConversionReportData): CompatibilitySummary {
        val converted = linkedSetOf<String>()
        val approximated = linkedSetOf<String>()
        val ignored = linkedSetOf<String>()
        val unsupported = linkedSetOf<String>()

        if (data.patternApproximationCount > 0) {
            val patternLabel = when {
                data.patternTileExpansionCount > 0 -> "Pattern fills (tile approximation)"
                data.patternApproximationStats.sparsePatternApproximationCount > 0 -> "Pattern fills (fallback color)"
                data.patternApproximationStats.complexPatternApproximationCount > 0 -> "Pattern fills (complex fallback)"
                else -> "Pattern fills"
            }
            approximated.add(patternLabel)
        }
        if (data.maskPathCount > 0 || data.appliedMasks > 0) approximated.add("Masks as clip paths")
        if (data.appliedMarkers > 0) approximated.add("Markers")
        if (data.contextPaintApproximationCount > 0) approximated.add("context-fill/context-stroke")
        if (data.textElementsConvertedToPaths > 0) converted.add("Text converted to vector paths")
        if (data.textElementsApproximated > 0) approximated.add("Text (${data.textElementsApproximated} bounding box approximation${if (data.textElementsApproximated == 1) "" else "s"})")

        val unapproximatedDashedStrokes = maxOf(0, data.dashedStrokesDetected - data.dashedStrokesApproximated)
        when {
            data.dashedStrokesApproximated > 0 && unapproximatedDashedStrokes > 0 -> {
                approximated.add("Dashed strokes (${data.dashedStrokesApproximated} approximated)")
                unsupported.add("Some dashed strokes ($unapproximatedDashedStrokes not approximated)")
            }
            data.dashedStrokesApproximated > 0 -> approximated.add("Dashed strokes")
            unapproximatedDashedStrokes > 0 -> unsupported.add("Dashed strokes")
        }

        if (data.dashTransformApproximateCompensations > 0) approximated.add("Non-scaling dash transforms")
        if (data.nonScalingStrokesUncertain > 0) approximated.add("Non-scaling strokes under non-uniform transforms")

        if (data.cssExternalImportCount > 0) ignored.add("External CSS @import")
        if (data.filterReferenceCount > 0) ignored.add("Filter effects")

        val handledTextCount = data.textElementsApproximated + data.textElementsConvertedToPaths
        val unapproximatedTextCount = maxOf(0, data.textElementCount - handledTextCount)
        val unconvertedTextPaths = maxOf(0, data.textPathElementCount - data.textPathsConverted)
        if (unapproximatedTextCount > 0 || unconvertedTextPaths > 0) {
            unsupported.add("Text")
        }
        if (data.imageStats.imageElementCount > 0) {
            unsupported.add("Raster/external images")
        }
        if (data.unsupportedMatrixTransforms > 0) {
            unsupported.add("Unsupported matrix transforms")
        }
        if (data.unresolvedUseReferences > 0) {
            unsupported.add("Unresolved <use> references")
        }

        data.unsupportedWarnings.forEach { warning ->
            val normalized = warning.trim()
            when {
                normalized.contains("Filter effects ignored", ignoreCase = true) -> ignored.add("Filter effects")
                normalized.contains("Missing paint reference", ignoreCase = true) -> unsupported.add("Missing paint references")
                normalized.contains("Linear gradients", ignoreCase = true) || normalized.contains("Radial gradients", ignoreCase = true) -> unsupported.add("Unsupported gradients")
                normalized.contains("Patterns", ignoreCase = true) -> unsupported.add("Unsupported patterns")
                normalized.contains("Masks", ignoreCase = true) -> unsupported.add("Unsupported masks")
                normalized.contains("Clip paths", ignoreCase = true) -> unsupported.add("Unsupported clip paths")
                normalized.isNotBlank() -> unsupported.add(normalized.removeSuffix("."))
            }
        }

        val unsupportedCount = unsupported.size
        val approximationCount = approximated.size
        val ignoredCount = ignored.size
        val noConvertedVisibleVectorContent =
            data.convertedPathCount == 0 &&
                (data.visibleDrawableElementCount > 0 || data.imageStats.imageElementCount > 0 || data.textElementCount > 0)

        val fidelity = when {
            noConvertedVisibleVectorContent -> 25
            unsupportedCount >= 4 -> 25
            unsupportedCount >= 2 -> 55
            unsupportedCount == 1 -> when {
                approximationCount >= 3 || ignoredCount >= 2 -> 75
                else -> 80
            }
            approximationCount > 0 || ignoredCount > 0 -> when {
                data.patternTileExpansionCount > 0 -> 95
                data.patternApproximationStats.sparsePatternApproximationCount > 0 -> 80
                data.patternApproximationStats.complexPatternApproximationCount > 0 -> 85
                approximationCount + ignoredCount >= 4 -> 90
                else -> 95
            }
            else -> 100
        }

        val stars = when {
            fidelity >= 100 -> "★★★★★"
            fidelity >= 95 -> "★★★★☆"
            fidelity >= 80 -> "★★★☆☆"
            fidelity >= 55 -> "★★☆☆☆"
            else -> "★☆☆☆☆"
        }

        val label = when {
            fidelity >= 100 -> "Fully compatible"
            fidelity >= 95 -> "Mostly compatible"
            fidelity >= 80 -> "Partially compatible"
            fidelity >= 55 -> "Limited compatibility"
            else -> "Poor compatibility"
        }

        return CompatibilitySummary(
            stars = stars,
            label = label,
            fidelityPercent = fidelity,
            converted = converted.toList(),
            approximated = approximated.toList(),
            ignored = ignored.toList(),
            unsupported = unsupported.toList()
        )
    }

    private fun imageConversionWarning(stats: SvgImageStats): String {
        val parts = mutableListOf<String>()

        if (stats.embeddedRasterImageCount > 0) {
            parts.add("embedded raster images were found")
        }
        if (stats.embeddedSvgImageCount > 0) {
            parts.add("embedded SVG image references were found")
        }
        if (stats.externalImageCount > 0) {
            parts.add("external image references were found")
        }
        if (stats.missingHrefImageCount > 0) {
            parts.add("some image elements have no href")
        }

        val detail = if (parts.isEmpty()) {
            "image elements were found"
        } else {
            parts.joinToString("; ")
        }

        return "⚠ <image> elements are raster or external resources and cannot be represented in VectorDrawable path XML. $detail. Convert images to vector paths/outlines, or keep the source as a raster asset if pixel accuracy is required."
    }

    private fun textConversionWarning(data: SvgConversionReportData): String {
        return if (data.svgFontGlyphCount > 0) {
            "⚠ Some text could not be converted exactly. Embedded SVG font glyphs were found, but unsupported characters or advanced text layout may still need manual outlining."
        } else {
            "⚠ Some text could not be approximated accurately. Text is best converted to paths/outlines before importing for exact VectorDrawable output."
        }
    }

    private fun basicShapeToPathData(element: Element, tagName: String): String? {
        return SvgShapeConverters.basicShapeToPathData(element, tagName)
    }

    private fun StringBuilder.appendBasicShapeBreakdown(breakdown: BasicShapeBreakdown) {
        appendLine("    • Rectangles: ${breakdown.rectangles}")
        appendLine("    • Rounded rectangles: ${breakdown.roundedRectangles}")
        appendLine("    • Circles: ${breakdown.circles}")
        appendLine("    • Ellipses: ${breakdown.ellipses}")
        appendLine("    • Polygons: ${breakdown.polygons}")
        appendLine("    • Polylines: ${breakdown.polylines}")
    }

    private fun floatAttr(element: Element, name: String): Float? {
        return element.getAttribute(name)
            .replace("px", "")
            .replace("dp", "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
    }
}
