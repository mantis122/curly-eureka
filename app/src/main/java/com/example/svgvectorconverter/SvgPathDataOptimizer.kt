// D2_v1: validate optimizer fixed-point behavior and final VectorDrawable semantics.
package com.example.svgvectorconverter

import java.math.BigDecimal
import java.math.RoundingMode
import java.io.StringReader
import java.util.concurrent.CancellationException
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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
    data class CommandLocalProfilingStats(
        val parseSetupNanos: Long = 0,
        val absoluteRelativeCandidateNanos: Long = 0,
        val axisCandidateNanos: Long = 0,
        val smoothShorthandCandidateNanos: Long = 0,
        val encodingSelectionNanos: Long = 0,
        val numericSerializationNanos: Long = 0,
        val separatorCalculationNanos: Long = 0,
        val commandOmissionNanos: Long = 0,
        val stringConstructionNanos: Long = 0,
        val winnerSelectionNanos: Long = 0,
        val stateBookkeepingNanos: Long = 0,
        val numericSerializationCalls: Int = 0,
        val numericSerializationCacheHits: Int = 0,
        val numericSerializationUniqueValues: Int = 0
    )


    data class CommandGlobalProfilingStats(
        val transitionEvaluationNanos: Long = 0,
        val separatorOmissionCostNanos: Long = 0,
        val segmentEncodingNanos: Long = 0,
        val stateCreationNanos: Long = 0,
        val bestStateComparisonNanos: Long = 0,
        val reconstructionNanos: Long = 0,
        val stateKeyCreationNanos: Long = 0,
        val stateKeyFieldPreparationNanos: Long = 0,
        val stateKeyPreviousCommandNanos: Long = 0,
        val stateKeyPreviousNumberNanos: Long = 0,
        val stateKeyAxisDirectionNanos: Long = 0,
        val stateKeyAllocationNanos: Long = 0,
        val stateStringConcatenationNanos: Long = 0,
        val stateMetadataPropagationNanos: Long = 0,
        val statePathAllocationNanos: Long = 0,
        val bestStateMapLookupNanos: Long = 0,
        val bestStateDecisionNanos: Long = 0,
        val bestStateReplacementNanos: Long = 0,
        val stateMapLookupCalls: Int = 0,
        val stateMapLookupHits: Int = 0,
        val stateMapLookupMisses: Int = 0,
        val stateMapInsertions: Int = 0,
        val stateMapReplacements: Int = 0,
        val segmentEncodingRequests: Int = 0,
        val segmentEncodingCacheHits: Int = 0,
        val segmentEncodingUniqueKeys: Int = 0
    )
    data class CurveSimplificationProfilingStats(
        val cubicToQuadraticNanos: Long = 0,
        val cubicParseSetupNanos: Long = 0,
        val cubicScanNanos: Long = 0,
        val cubicRebuildValidationNanos: Long = 0,
        val cubicRebuildNanos: Long = 0,
        val cubicValidationNanos: Long = 0,
        val straightBezierNanos: Long = 0,
        val straightParseSetupNanos: Long = 0,
        val straightScanNanos: Long = 0,
        val straightRebuildValidationNanos: Long = 0,
        val straightRebuildNanos: Long = 0,
        val straightValidationNanos: Long = 0,
        val parseCalls: Int = 0,
        val duplicateParseInputs: Int = 0,
        val secondPassReparsedUnchangedInput: Int = 0,
        val cubicChangedPaths: Int = 0,
        val straightChangedPaths: Int = 0,
        val rebuildAttempts: Int = 0,
        val rebuildNoOpResults: Int = 0,
        val validationCalls: Int = 0,
        val validationAccepted: Int = 0,
        val validationRejected: Int = 0,
        val rebuildRejectedForSize: Int = 0
    )

    data class UniformScaleProfilingStats(
        val groupDiscoveryNanos: Long = 0,
        val eligibilityChecksNanos: Long = 0,
        val pathScalingNanos: Long = 0,
        val scalePathParseTokenizeNanos: Long = 0,
        val scalePathNumericParseNanos: Long = 0,
        val scalePathCoordinateMathNanos: Long = 0,
        val scalePathArcHandlingNanos: Long = 0,
        val scalePathNumberFormattingNanos: Long = 0,
        val scalePathReconstructionNanos: Long = 0,
        val scalePathNormalizationNanos: Long = 0,
        val postScaleP6Attempts: Int = 0,
        val postScaleP6Accepted: Int = 0,
        val postScaleP6Fallbacks: Int = 0,
        val postScaleP6ParserFallbacks: Int = 0,
        val postScaleP6InternalFallbacks: Int = 0,
        val postScaleP6OptimizationNanos: Long = 0,
        val postScaleP6ParserValidationNanos: Long = 0,
        val postScaleFullFallbackNanos: Long = 0,
        val strokeAdjustmentNanos: Long = 0,
        val canonicalizationCostingNanos: Long = 0,
        val xmlReplacementNanos: Long = 0,
        val candidatesConsidered: Int = 0,
        val candidatesRejected: Int = 0,
        val proposalsAccepted: Int = 0,
    )

    data class I43ComplexSummary(
        val candidatesExamined: Int = 0,
        val predictedFixed: Int = 0,
        val truePositive: Int = 0,
        val falsePositive: Int = 0,
        val falseNegative: Int = 0,
        val trueNegative: Int = 0,
        val checkNanos: Long = 0,
        val potentialAvoidableOptimizerNanos: Long = 0,
        val falsePositiveOptimizerNanos: Long = 0,
        val rejectedReflectiveShorthand: Int = 0,
        val rejectedNumericSpelling: Int = 0,
        val rejectedExplicitRepeat: Int = 0,
        val rejectedProvenance: Int = 0,
    )

    data class I43ComplexFamilies(
        val cubicPredicted: Int = 0,
        val cubicTruePositive: Int = 0,
        val cubicFalsePositive: Int = 0,
        val quadraticPredicted: Int = 0,
        val quadraticTruePositive: Int = 0,
        val quadraticFalsePositive: Int = 0,
        val arcPredicted: Int = 0,
        val arcTruePositive: Int = 0,
        val arcFalsePositive: Int = 0,
        val mixedPredicted: Int = 0,
        val mixedTruePositive: Int = 0,
        val mixedFalsePositive: Int = 0,
    )

    data class I43ComplexChanges(
        val falsePositiveGeometryCleanup: Int = 0,
        val falsePositiveLocalShortening: Int = 0,
        val falsePositiveGlobalCommand: Int = 0,
        val falsePositiveGlobalNumeric: Int = 0,
        val falsePositiveOther: Int = 0,
    )

    data class I43ComplexStats(
        val summary: I43ComplexSummary = I43ComplexSummary(),
        val families: I43ComplexFamilies = I43ComplexFamilies(),
        val changes: I43ComplexChanges = I43ComplexChanges(),
    ) {
        val complexCandidatesExamined get() = summary.candidatesExamined
        val complexPredictedFixed get() = summary.predictedFixed
        val complexTruePositive get() = summary.truePositive
        val complexFalsePositive get() = summary.falsePositive
        val complexFalseNegative get() = summary.falseNegative
        val complexTrueNegative get() = summary.trueNegative
        val complexCheckNanos get() = summary.checkNanos
        val complexPotentialAvoidableOptimizerNanos get() = summary.potentialAvoidableOptimizerNanos
        val complexFalsePositiveOptimizerNanos get() = summary.falsePositiveOptimizerNanos
        val rejectedReflectiveShorthand get() = summary.rejectedReflectiveShorthand
        val rejectedNumericSpelling get() = summary.rejectedNumericSpelling
        val rejectedExplicitRepeat get() = summary.rejectedExplicitRepeat
        val rejectedProvenance get() = summary.rejectedProvenance
        val cubicPredicted get() = families.cubicPredicted
        val cubicTruePositive get() = families.cubicTruePositive
        val cubicFalsePositive get() = families.cubicFalsePositive
        val quadraticPredicted get() = families.quadraticPredicted
        val quadraticTruePositive get() = families.quadraticTruePositive
        val quadraticFalsePositive get() = families.quadraticFalsePositive
        val arcPredicted get() = families.arcPredicted
        val arcTruePositive get() = families.arcTruePositive
        val arcFalsePositive get() = families.arcFalsePositive
        val mixedPredicted get() = families.mixedPredicted
        val mixedTruePositive get() = families.mixedTruePositive
        val mixedFalsePositive get() = families.mixedFalsePositive
        val falsePositiveGeometryCleanup get() = changes.falsePositiveGeometryCleanup
        val falsePositiveLocalShortening get() = changes.falsePositiveLocalShortening
        val falsePositiveGlobalCommand get() = changes.falsePositiveGlobalCommand
        val falsePositiveGlobalNumeric get() = changes.falsePositiveGlobalNumeric
        val falsePositiveOther get() = changes.falsePositiveOther
    }

    data class IdempotenceProfilingStats(
        val pathSyntaxNanos: Long = 0,
        val pathTokenizationNormalizationNanos: Long = 0,
        val pathGeometryCleanupNanos: Long = 0,
        val pathCommandMinimizationNanos: Long = 0,
        val pathNumericSerializationNanos: Long = 0,
        val colorNormalizationNanos: Long = 0,
        val pruningAndGroupCleanupNanos: Long = 0,
        val transformOptimizationNanos: Long = 0,
        val deduplicationAndMergeNanos: Long = 0,
        val numericCleanupNanos: Long = 0,
        val nearIntegerSnappingNanos: Long = 0,
        val decimalCanonicalizationNanos: Long = 0,
        val decimalTokenizationNanos: Long = 0,
        val decimalRebuildNanos: Long = 0,
        val decimalReoptimizationNanos: Long = 0,
        val decimalValidationNanos: Long = 0,
        val decimalPathsExamined: Int = 0,
        val i2PathSyntaxStableInputs: Int = 0,
        val i2PathSyntaxStableInputNanos: Long = 0,
        val i2DecimalShadowPathsCompared: Int = 0,
        val i2DecimalShadowByteIdentical: Int = 0,
        val i2DecimalShadowDifferent: Int = 0,
        val i2DecimalShadowFastShorter: Int = 0,
        val i2DecimalShadowReferenceShorter: Int = 0,
        val i2DecimalShadowEqualLengthDifferent: Int = 0,
        val i2DecimalShadowFastInvalid: Int = 0,
        val i2DecimalShadowFastNonFixed: Int = 0,
        val i2DecimalShadowCharacterDeltaVsReference: Int = 0,
        val i2DecimalShadowNanos: Long = 0,
        val i3DecimalFastPathAccepted: Int = 0,
        val i3DecimalFallbackInvalid: Int = 0,
        val i3DecimalFallbackNonFixed: Int = 0,
        val i3DecimalFastPathCheckNanos: Long = 0,
        // I4.1 diagnostic-only pass-2 fixed-point certificate study.
        val i41CertificatePredictedFixed: Int = 0,
        val i41CertificateTruePositive: Int = 0,
        val i41CertificateFalsePositive: Int = 0,
        val i41CertificateFalseNegative: Int = 0,
        val i41CertificateTrueNegative: Int = 0,
        val i41CertificateCheckNanos: Long = 0,
        val i41PotentialAvoidableOptimizerNanos: Long = 0,
        val i41FalsePositiveOptimizerNanos: Long = 0,
        val i41RejectedLexical: Int = 0,
        val i41RejectedNumericSpelling: Int = 0,
        val i41RejectedWhitespace: Int = 0,
        val i41RejectedComplexCommandFamily: Int = 0,
        val i41RejectedExplicitRepeat: Int = 0,
        // I4.2 provenance-aware certificate study.
        val i42ProvenanceExcluded: Int = 0,
        val i42ProvenanceExcludedActuallyFixed: Int = 0,
        val i42ProvenancePreventedFalsePositive: Int = 0,
        val i42ProvenanceExcludedOptimizerNanos: Long = 0,
        val i42PreventedFalsePositiveOptimizerNanos: Long = 0,
        val i42PreventedChangedSyntaxNormalization: Int = 0,
        val i42PreventedChangedGeometryCleanup: Int = 0,
        val i42PreventedChangedLocalShortening: Int = 0,
        val i42PreventedChangedGlobalCommand: Int = 0,
        val i42PreventedChangedGlobalNumeric: Int = 0,
        val i42PreventedChangedOther: Int = 0,
        // I4.3 diagnostic-only complex-command certificate expansion.
        val i43: I43ComplexStats = I43ComplexStats(),
        val finalFormattingNanos: Long = 0,
        val equalityComparisonNanos: Long = 0,
        val pathsExamined: Int = 0,
        val finalPassStablePathsRegistered: Int = 0,
        val pathCacheHits: Int = 0,
        val stableOutputCacheHits: Int = 0,
        val regularCacheHits: Int = 0,
        val pathCacheMisses: Int = 0,
        val xmlCharactersBefore: Int = 0,
        val xmlCharactersAfter: Int = 0
    )

    data class G315GuardedProductionTrialStats(
        val attempted: Boolean = false,
        val candidateChanged: Boolean = false,
        val pathsExamined: Int = 0,
        val pathsChanged: Int = 0,
        val geometryComparisons: Int = 0,
        val geometryMismatchCount: Int = 0,
        val exactShortCircuitCount: Int = 0,
        val fallbackBidirectionalCount: Int = 0,
        val comparatorFailureCount: Int = 0,
        val matchedIndependentSecondPass: Boolean = false,
        val fixedPointVerified: Boolean = false,
        val finalValidationPassed: Boolean = false,
        val guardAccepted: Boolean = false,
        val guardRejected: Boolean = false,
        val charactersBefore: Int = 0,
        val charactersAfter: Int = 0,
        val charactersSaved: Int = 0,
        val charactersAdded: Int = 0,
        val candidateNanos: Long = 0,
        val comparatorNanos: Long = 0,
        val guardNanos: Long = 0,
        val rejectionReason: String = "",
        val candidateXml: String = ""
    )

    data class ValidationSnapshotStats(
        val attempted: Boolean = false,
        val passed: Boolean = false,
        val validatedPathDataCount: Int = 0,
        val invalidPathDataCount: Int = 0,
        val nonFiniteNumberCount: Int = 0,
        val malformedStructureCount: Int = 0,
        val invalidViewportCount: Int = 0,
        val unsupportedOutputConstructCount: Int = 0,
        val witness: String = ""
    )

    data class ValidationClassificationStats(
        val input: ValidationSnapshotStats = ValidationSnapshotStats(),
        val pass1: ValidationSnapshotStats = ValidationSnapshotStats(),
        val pass2: ValidationSnapshotStats = ValidationSnapshotStats(),
        val pass3: ValidationSnapshotStats = ValidationSnapshotStats(),
        val selected: ValidationSnapshotStats = ValidationSnapshotStats()
    )

    data class Stats(
        val pathCount: Int = 0,
        val charactersBefore: Int = 0,
        val charactersAfter: Int = 0,
        val repeatedCommandsRemoved: Int = 0,
        val redundantNonDrawingSegmentsRemoved: Int = 0,
        val collinearLineSegmentsConsolidated: Int = 0,
        val straightBezierCurvesSimplified: Int = 0,
        val degenerateArcsSimplified: Int = 0,
        val smoothBezierShorthandsSelected: Int = 0,
        val cubicCurvesReducedToQuadratic: Int = 0,
        val arcRotationsCanonicalized: Int = 0,
        val arcRadiiCanonicalized: Int = 0,
        val arcHalfTurnRotationsReduced: Int = 0,
        val arcAxesSwappedForSize: Int = 0,
        val arcRepresentationsGloballyMinimized: Int = 0,
        val commandSequencesGloballyMinimized: Int = 0,
        val implicitLineTosAfterMoveSelected: Int = 0,
        val repeatedShorthandCurveCommandsOmitted: Int = 0,
        val repeatedFullCurveCommandsOmitted: Int = 0,
        val repeatedArcCommandsOmitted: Int = 0,
        val scientificNotationValuesSelected: Int = 0,
        val globallyOptimizedNumericPaths: Int = 0,
        val numbersNormalized: Int = 0,
        val nearIntegerValuesSnapped: Int = 0,
        val decimalValuesCanonicalized: Int = 0,
        val translationGroupsPreservedForSize: Int = 0,
        val emptyPathDataRemoved: Int = 0,
        val moveOnlyPathsRemoved: Int = 0,
        val invisiblePathsRemoved: Int = 0,
        val emptyGroupsRemoved: Int = 0,
        val redundantGroupsFlattened: Int = 0,
        val commonTranslationGroupsFactored: Int = 0,
        val adjacentGroupsCoalesced: Int = 0,
        val compatiblePathsMerged: Int = 0,
        val compatiblePathMergesPreservedForSize: Int = 0,
        val adjacentPathPairsExamined: Int = 0,
        val adjacentPathPairsSamePaint: Int = 0,
        val adjacentPathMergeRejectedNestedPaint: Int = 0,
        val adjacentPathMergeRejectedMissingPathData: Int = 0,
        val adjacentPathMergeRejectedPaintMismatch: Int = 0,
        val adjacentPathMergeRejectedUnsupportedGeometry: Int = 0,
        val adjacentPathMergeRejectedOverlapSafety: Int = 0,
        val adjacentPathMergeRejectedForSize: Int = 0,
        val exactDuplicatePathsRemoved: Int = 0,
        val translatedGroupsFlattened: Int = 0,
        val translatedPaths: Int = 0,
        val scaledGroupsFlattened: Int = 0,
        val scaledPaths: Int = 0,
        val scaledStrokeWidths: Int = 0,
        val scaleGroupsPreservedForSize: Int = 0,
        val nonUniformScaleGroupsFlattened: Int = 0,
        val nonUniformScaledPaths: Int = 0,
        val nonUniformScaleGroupsPreservedForSize: Int = 0,
        val rotationGroupsFlattened: Int = 0,
        val rotatedPaths: Int = 0,
        val rotationGroupsPreservedForSize: Int = 0,
        val identityTransformAttributesRemoved: Int = 0,
        val nestedTransformGroupsComposed: Int = 0,
        val transformAttributesCanonicalized: Int = 0,
        val zeroPivotAttributesRemoved: Int = 0,
        val transformGroupsReordered: Int = 0,
        val optimizerIdempotenceVerified: Boolean = false,
        val optimizerReachedFixedPoint: Boolean = false,
        val optimizerStabilityPasses: Int = 0,
        val optimizerValidationNanos: Long = 0,
        val optimizerProductionPassNanos: Long = 0,
        val optimizerIdempotencePassNanos: Long = 0,
        val optimizerFixedPointPassNanos: Long = 0,
        val optimizerValidationPathCacheHits: Int = 0,
        val optimizerValidationPathCacheMisses: Int = 0,
        val idempotenceProfiling: IdempotenceProfilingStats = IdempotenceProfilingStats(),
        val optimizerValidationPasses: Int = 0,
        val optimizerFirstPassChangedXml: Boolean = false,
        val optimizerSecondPassChangedXml: Boolean = false,
        val optimizerThirdPassChangedXml: Boolean = false,
        val finalOutputValidationPassed: Boolean = false,
        val finalOutputValidationNanos: Long = 0,
        val validatedPathDataCount: Int = 0,
        val invalidPathDataCount: Int = 0,
        val nonFiniteNumberCount: Int = 0,
        val malformedStructureCount: Int = 0,
        val invalidViewportCount: Int = 0,
        val unsupportedOutputConstructCount: Int = 0,
        val shorterCommandFormsSelected: Int = 0,
        val relativeCommandsSelected: Int = 0,
        val axisCommandsSelected: Int = 0,
        val pathSyntaxOptimizationNanos: Long = 0,
        val pathTokenizationNormalizationNanos: Long = 0,
        val pathGeometryCleanupNanos: Long = 0,
        val pathRedundantSegmentCleanupNanos: Long = 0,
        val pathArcCleanupNanos: Long = 0,
        val pathCurveSimplificationNanos: Long = 0,
        val pathCurveSimplificationProfiling: CurveSimplificationProfilingStats = CurveSimplificationProfilingStats(),
        val pathCollinearConsolidationNanos: Long = 0,
        val pathCommandMinimizationNanos: Long = 0,
        val pathCommandLocalShorteningNanos: Long = 0,
        val pathCommandLocalProfiling: CommandLocalProfilingStats = CommandLocalProfilingStats(),
        val pathCommandGlobalParseSetupNanos: Long = 0,
        val pathCommandGlobalCandidateGenerationNanos: Long = 0,
        val pathCommandGlobalDynamicProgrammingNanos: Long = 0,
        val pathCommandGlobalProfiling: CommandGlobalProfilingStats = CommandGlobalProfilingStats(),
        val pathNumericSerializationNanos: Long = 0,
        val colorNormalizationNanos: Long = 0,
        val pruningAndGroupCleanupNanos: Long = 0,
        val transformOptimizationNanos: Long = 0,
        val transformIdentityCompositionNanos: Long = 0,
        val transformFactoringFlatteningNanos: Long = 0,
        val transformScaleFlatteningNanos: Long = 0,
        val transformUniformScaleFlatteningNanos: Long = 0,
        val transformUniformScaleProfiling: UniformScaleProfilingStats = UniformScaleProfilingStats(),
        val transformNonUniformScaleFlatteningNanos: Long = 0,
        val transformRotationTranslationNanos: Long = 0,
        val transformCanonicalizationNanos: Long = 0,
        val deduplicationAndMergeNanos: Long = 0,
        val numericCleanupNanos: Long = 0,
        val nearIntegerSnappingNanos: Long = 0,
        val decimalCanonicalizationNanos: Long = 0,
        val decimalTokenizationNanos: Long = 0,
        val decimalRebuildNanos: Long = 0,
        val decimalReoptimizationNanos: Long = 0,
        val decimalValidationNanos: Long = 0,
        val decimalPathsExamined: Int = 0,
        // I2 diagnostic-only redundancy study.
        val i2PathSyntaxStableInputs: Int = 0,
        val i2PathSyntaxStableInputNanos: Long = 0,
        val i2DecimalShadowPathsCompared: Int = 0,
        val i2DecimalShadowByteIdentical: Int = 0,
        val i2DecimalShadowDifferent: Int = 0,
        val i2DecimalShadowFastShorter: Int = 0,
        val i2DecimalShadowReferenceShorter: Int = 0,
        val i2DecimalShadowEqualLengthDifferent: Int = 0,
        val i2DecimalShadowFastInvalid: Int = 0,
        val i2DecimalShadowFastNonFixed: Int = 0,
        val i2DecimalShadowCharacterDeltaVsReference: Int = 0,
        val i2DecimalShadowNanos: Long = 0,
        val i3DecimalFastPathAccepted: Int = 0,
        val i3DecimalFallbackInvalid: Int = 0,
        val i3DecimalFallbackNonFixed: Int = 0,
        val i3DecimalFastPathCheckNanos: Long = 0,
        val i41CertificatePredictedFixed: Int = 0,
        val i41CertificateTruePositive: Int = 0,
        val i41CertificateFalsePositive: Int = 0,
        val i41CertificateFalseNegative: Int = 0,
        val i41CertificateTrueNegative: Int = 0,
        val i41CertificateCheckNanos: Long = 0,
        val i41PotentialAvoidableOptimizerNanos: Long = 0,
        val i41FalsePositiveOptimizerNanos: Long = 0,
        val i41RejectedLexical: Int = 0,
        val i41RejectedNumericSpelling: Int = 0,
        val i41RejectedWhitespace: Int = 0,
        val i41RejectedComplexCommandFamily: Int = 0,
        val i41RejectedExplicitRepeat: Int = 0,
        val i42ProvenanceExcluded: Int = 0,
        val i42ProvenanceExcludedActuallyFixed: Int = 0,
        val i42ProvenancePreventedFalsePositive: Int = 0,
        val i42ProvenanceExcludedOptimizerNanos: Long = 0,
        val i42PreventedFalsePositiveOptimizerNanos: Long = 0,
        val i42PreventedChangedSyntaxNormalization: Int = 0,
        val i42PreventedChangedGeometryCleanup: Int = 0,
        val i42PreventedChangedLocalShortening: Int = 0,
        val i42PreventedChangedGlobalCommand: Int = 0,
        val i42PreventedChangedGlobalNumeric: Int = 0,
        val i42PreventedChangedOther: Int = 0,
        val i43: I43ComplexStats = I43ComplexStats(),
        val pathOptimizationCacheHits: Int = 0,
        val pathOptimizationCacheMisses: Int = 0,
        val finalFormattingNanos: Long = 0,
        val pathSyntaxCharactersSaved: Int = 0,
        val pruningCleanupCharactersSaved: Int = 0,
        val transformCharactersSaved: Int = 0,
        val deduplicationCharactersSaved: Int = 0,
        val numericCleanupCharactersSaved: Int = 0,
        val formattingCharactersSaved: Int = 0,
        // Diagnostic signed stage deltas. Positive means the stage reduced XML;
        // negative means the stage increased XML. These are diagnostic-only.
        val h21PathSyntaxCharacterDelta: Int = 0,
        // Diagnostic split of the combined path-syntax/color stage.
        val h22PathDataSyntaxCharacterDelta: Int = 0,
        val h22ColorNormalizationCharacterDelta: Int = 0,
        // Forensic signed internal PathData syntax-stage deltas. Positive = shorter.
        val h23SyntaxNormalizationCharacterDelta: Int = 0,
        val h23RedundantGeometryCharacterDelta: Int = 0,
        val h23ArcCleanupCharacterDelta: Int = 0,
        val h23CurveSimplificationCharacterDelta: Int = 0,
        val h23CollinearConsolidationCharacterDelta: Int = 0,
        val h23LocalCommandShorteningCharacterDelta: Int = 0,
        val h23GlobalCommandMinimizationCharacterDelta: Int = 0,
        val h23GlobalNumericSerializationCharacterDelta: Int = 0,
        val h24PathSyntaxCandidatesRejectedForSize: Int = 0,
        val h24PathSyntaxCharactersAvoided: Int = 0,
        val h25DecimalCandidatesRejectedForSize: Int = 0,
        val h25DecimalCharactersAvoided: Int = 0,
        val h21PruningCharacterDelta: Int = 0,
        val h21TransformCharacterDelta: Int = 0,
        val h21NearIntegerCharacterDelta: Int = 0,
        val h21DedupMergeCharacterDelta: Int = 0,
        val h21DecimalCanonicalizationCharacterDelta: Int = 0,
        val h21FormattingCharacterDelta: Int = 0,
        val xmlCharactersBefore: Int = 0,
        val xmlCharactersAfter: Int = 0,
        val g315GuardedProductionTrial: G315GuardedProductionTrialStats =
            G315GuardedProductionTrialStats(),
        val validationClassification: ValidationClassificationStats =
            ValidationClassificationStats()
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
        val stats: Stats,
        // I4.2 diagnostic provenance: final/near-final PathData spellings that
        // originate from compatible-path merging in this pass.
        val mergeSynthesizedPathData: Set<String> = emptySet()
    )

    private const val MAX_PATH_DECIMAL_PLACES = 6

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
        """(<path\b[^<>]*?/\s*>)(\s*(?:(?:<!--[\s\S]*?-->)\s*)*)(<path\b[^<>]*?/\s*>)""",
        RegexOption.IGNORE_CASE
    )
    private val androidAttributeRegex = Regex(
        """\bandroid:([A-Za-z0-9_]+)\s*=\s*(["'])(.*?)\2""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private data class PathOptimizationCache(
        val values: MutableMap<String, PathResult> = linkedMapOf(),
        val stableOutputs: MutableMap<String, PathResult> = linkedMapOf(),
        var totalHits: Int = 0,
        var totalMisses: Int = 0,
        var validationHits: Int = 0,
        var validationStableOutputHits: Int = 0,
        var validationRegularHits: Int = 0,
        var validationMisses: Int = 0
    )

    fun optimizeVectorXml(xml: String): Result {
        val inputValidationSnapshot = validationSnapshot(xml)
        val pathCache = PathOptimizationCache()

        val firstPassStartTime = System.nanoTime()
        val firstPass = optimizeVectorXmlSinglePass(
            xml = xml,
            pathCache = pathCache,
            validationPass = false
        )
        val firstPassNanos = System.nanoTime() - firstPassStartTime
        val firstPassValidationSnapshot = validationSnapshot(firstPass.xml)

        // G3.5 safety rollback: production idempotence validation performs a
        // fully independent path recomputation. Stable-output reuse remains
        // available only to the G3.4 developer diagnostic so it cannot mask a
        // legitimate second-pass improvement.
        val finalPassStablePathsRegistered = 0
        val secondPassCache = PathOptimizationCache()
        val secondPassStartTime = System.nanoTime()
        val secondPass = optimizeVectorXmlSinglePass(
            xml = firstPass.xml,
            pathCache = secondPassCache,
            validationPass = true,
            certificateExcludedPathData = firstPass.mergeSynthesizedPathData
        )
        val secondPassNanos = System.nanoTime() - secondPassStartTime
        val secondPassValidationSnapshot = validationSnapshot(secondPass.xml)
        val secondPassCacheHits = secondPassCache.validationHits
        val secondPassStableHits = secondPassCache.validationStableOutputHits
        val secondPassRegularHits = secondPassCache.validationRegularHits
        val secondPassCacheMisses = secondPassCache.validationMisses
        val equalityComparisonStartTime = System.nanoTime()
        val secondPassMatchesFirst = secondPass.xml == firstPass.xml
        val equalityComparisonNanos =
            System.nanoTime() - equalityComparisonStartTime
        val idempotenceProfiling = IdempotenceProfilingStats(
            pathSyntaxNanos = secondPass.stats.pathSyntaxOptimizationNanos,
            pathTokenizationNormalizationNanos =
                secondPass.stats.pathTokenizationNormalizationNanos,
            pathGeometryCleanupNanos = secondPass.stats.pathGeometryCleanupNanos,
            pathCommandMinimizationNanos =
                secondPass.stats.pathCommandMinimizationNanos,
            pathNumericSerializationNanos =
                secondPass.stats.pathNumericSerializationNanos,
            colorNormalizationNanos = secondPass.stats.colorNormalizationNanos,
            pruningAndGroupCleanupNanos =
                secondPass.stats.pruningAndGroupCleanupNanos,
            transformOptimizationNanos =
                secondPass.stats.transformOptimizationNanos,
            deduplicationAndMergeNanos =
                secondPass.stats.deduplicationAndMergeNanos,
            numericCleanupNanos = secondPass.stats.numericCleanupNanos,
            nearIntegerSnappingNanos = secondPass.stats.nearIntegerSnappingNanos,
            decimalCanonicalizationNanos = secondPass.stats.decimalCanonicalizationNanos,
            decimalTokenizationNanos = secondPass.stats.decimalTokenizationNanos,
            decimalRebuildNanos = secondPass.stats.decimalRebuildNanos,
            decimalReoptimizationNanos = secondPass.stats.decimalReoptimizationNanos,
            decimalValidationNanos = secondPass.stats.decimalValidationNanos,
            decimalPathsExamined = secondPass.stats.decimalPathsExamined,
            i2PathSyntaxStableInputs = secondPass.stats.i2PathSyntaxStableInputs,
            i2PathSyntaxStableInputNanos = secondPass.stats.i2PathSyntaxStableInputNanos,
            i2DecimalShadowPathsCompared = secondPass.stats.i2DecimalShadowPathsCompared,
            i2DecimalShadowByteIdentical = secondPass.stats.i2DecimalShadowByteIdentical,
            i2DecimalShadowDifferent = secondPass.stats.i2DecimalShadowDifferent,
            i2DecimalShadowFastShorter = secondPass.stats.i2DecimalShadowFastShorter,
            i2DecimalShadowReferenceShorter = secondPass.stats.i2DecimalShadowReferenceShorter,
            i2DecimalShadowEqualLengthDifferent =
                secondPass.stats.i2DecimalShadowEqualLengthDifferent,
            i2DecimalShadowFastInvalid = secondPass.stats.i2DecimalShadowFastInvalid,
            i2DecimalShadowFastNonFixed = secondPass.stats.i2DecimalShadowFastNonFixed,
            i2DecimalShadowCharacterDeltaVsReference =
                secondPass.stats.i2DecimalShadowCharacterDeltaVsReference,
            i2DecimalShadowNanos = secondPass.stats.i2DecimalShadowNanos,
            i3DecimalFastPathAccepted = secondPass.stats.i3DecimalFastPathAccepted,
            i3DecimalFallbackInvalid = secondPass.stats.i3DecimalFallbackInvalid,
            i3DecimalFallbackNonFixed = secondPass.stats.i3DecimalFallbackNonFixed,
            i3DecimalFastPathCheckNanos = secondPass.stats.i3DecimalFastPathCheckNanos,
            i41CertificatePredictedFixed = secondPass.stats.i41CertificatePredictedFixed,
            i41CertificateTruePositive = secondPass.stats.i41CertificateTruePositive,
            i41CertificateFalsePositive = secondPass.stats.i41CertificateFalsePositive,
            i41CertificateFalseNegative = secondPass.stats.i41CertificateFalseNegative,
            i41CertificateTrueNegative = secondPass.stats.i41CertificateTrueNegative,
            i41CertificateCheckNanos = secondPass.stats.i41CertificateCheckNanos,
            i41PotentialAvoidableOptimizerNanos =
                secondPass.stats.i41PotentialAvoidableOptimizerNanos,
            i41FalsePositiveOptimizerNanos = secondPass.stats.i41FalsePositiveOptimizerNanos,
            i41RejectedLexical = secondPass.stats.i41RejectedLexical,
            i41RejectedNumericSpelling = secondPass.stats.i41RejectedNumericSpelling,
            i41RejectedWhitespace = secondPass.stats.i41RejectedWhitespace,
            i41RejectedComplexCommandFamily = secondPass.stats.i41RejectedComplexCommandFamily,
            i41RejectedExplicitRepeat = secondPass.stats.i41RejectedExplicitRepeat,
            i42ProvenanceExcluded = secondPass.stats.i42ProvenanceExcluded,
            i42ProvenanceExcludedActuallyFixed =
                secondPass.stats.i42ProvenanceExcludedActuallyFixed,
            i42ProvenancePreventedFalsePositive =
                secondPass.stats.i42ProvenancePreventedFalsePositive,
            i42ProvenanceExcludedOptimizerNanos =
                secondPass.stats.i42ProvenanceExcludedOptimizerNanos,
            i42PreventedFalsePositiveOptimizerNanos =
                secondPass.stats.i42PreventedFalsePositiveOptimizerNanos,
            i42PreventedChangedSyntaxNormalization =
                secondPass.stats.i42PreventedChangedSyntaxNormalization,
            i42PreventedChangedGeometryCleanup =
                secondPass.stats.i42PreventedChangedGeometryCleanup,
            i42PreventedChangedLocalShortening =
                secondPass.stats.i42PreventedChangedLocalShortening,
            i42PreventedChangedGlobalCommand =
                secondPass.stats.i42PreventedChangedGlobalCommand,
            i42PreventedChangedGlobalNumeric =
                secondPass.stats.i42PreventedChangedGlobalNumeric,
            i42PreventedChangedOther = secondPass.stats.i42PreventedChangedOther,
            i43 = I43ComplexStats(
                summary = I43ComplexSummary(
                    candidatesExamined = secondPass.stats.i43.complexCandidatesExamined,
                    predictedFixed = secondPass.stats.i43.complexPredictedFixed,
                    truePositive = secondPass.stats.i43.complexTruePositive,
                    falsePositive = secondPass.stats.i43.complexFalsePositive,
                    falseNegative = secondPass.stats.i43.complexFalseNegative,
                    trueNegative = secondPass.stats.i43.complexTrueNegative,
                    checkNanos = secondPass.stats.i43.complexCheckNanos,
                    potentialAvoidableOptimizerNanos = secondPass.stats.i43.complexPotentialAvoidableOptimizerNanos,
                    falsePositiveOptimizerNanos = secondPass.stats.i43.complexFalsePositiveOptimizerNanos,
                    rejectedReflectiveShorthand = secondPass.stats.i43.rejectedReflectiveShorthand,
                    rejectedNumericSpelling = secondPass.stats.i43.rejectedNumericSpelling,
                    rejectedExplicitRepeat = secondPass.stats.i43.rejectedExplicitRepeat,
                    rejectedProvenance = secondPass.stats.i43.rejectedProvenance,
                ),
                families = I43ComplexFamilies(
                    cubicPredicted = secondPass.stats.i43.cubicPredicted,
                    cubicTruePositive = secondPass.stats.i43.cubicTruePositive,
                    cubicFalsePositive = secondPass.stats.i43.cubicFalsePositive,
                    quadraticPredicted = secondPass.stats.i43.quadraticPredicted,
                    quadraticTruePositive = secondPass.stats.i43.quadraticTruePositive,
                    quadraticFalsePositive = secondPass.stats.i43.quadraticFalsePositive,
                    arcPredicted = secondPass.stats.i43.arcPredicted,
                    arcTruePositive = secondPass.stats.i43.arcTruePositive,
                    arcFalsePositive = secondPass.stats.i43.arcFalsePositive,
                    mixedPredicted = secondPass.stats.i43.mixedPredicted,
                    mixedTruePositive = secondPass.stats.i43.mixedTruePositive,
                    mixedFalsePositive = secondPass.stats.i43.mixedFalsePositive,
                ),
                changes = I43ComplexChanges(
                    falsePositiveGeometryCleanup = secondPass.stats.i43.falsePositiveGeometryCleanup,
                    falsePositiveLocalShortening = secondPass.stats.i43.falsePositiveLocalShortening,
                    falsePositiveGlobalCommand = secondPass.stats.i43.falsePositiveGlobalCommand,
                    falsePositiveGlobalNumeric = secondPass.stats.i43.falsePositiveGlobalNumeric,
                    falsePositiveOther = secondPass.stats.i43.falsePositiveOther,
                ),
            ),
            finalFormattingNanos = secondPass.stats.finalFormattingNanos,
            equalityComparisonNanos = equalityComparisonNanos,
            pathsExamined = secondPass.stats.pathCount,
            finalPassStablePathsRegistered = finalPassStablePathsRegistered,
            pathCacheHits = secondPassCacheHits,
            stableOutputCacheHits = secondPassStableHits,
            regularCacheHits = secondPassRegularHits,
            pathCacheMisses = secondPassCacheMisses,
            xmlCharactersBefore = secondPass.stats.xmlCharactersBefore,
            xmlCharactersAfter = secondPass.stats.xmlCharactersAfter
        )

        if (secondPassMatchesFirst) {
            val productionConvergenceTrial = runGuardedProductionConvergenceTrial(
                firstPassXml = firstPass.xml,
                independentSecondPassXml = secondPass.xml,
                independentSecondPassIsFixed = true
            )
            val selectedXml =
                if (productionConvergenceTrial.guardAccepted) productionConvergenceTrial.candidateXml else firstPass.xml
            val selectedPathCharacters = pathDataAttributeRegex.findAll(selectedXml)
                .sumOf { it.groupValues[1].length }
            val selectedValidationSnapshot = validationSnapshot(selectedXml)
            val validationClassification = ValidationClassificationStats(
                input = inputValidationSnapshot,
                pass1 = firstPassValidationSnapshot,
                pass2 = secondPassValidationSnapshot,
                pass3 = ValidationSnapshotStats(attempted = false),
                selected = selectedValidationSnapshot
            )
            return attachFinalOutputValidation(
                firstPass.copy(
                    xml = selectedXml,
                    stats = firstPass.stats.copy(
                        charactersAfter = selectedPathCharacters,
                        xmlCharactersAfter = selectedXml.length,
                        optimizerIdempotenceVerified = true,
                        optimizerReachedFixedPoint = true,
                        optimizerStabilityPasses = 1,
                        optimizerValidationNanos = secondPassNanos,
                        optimizerProductionPassNanos = firstPassNanos,
                        optimizerIdempotencePassNanos = secondPassNanos,
                        optimizerFixedPointPassNanos = 0L,
                        optimizerValidationPathCacheHits = secondPassCache.validationHits,
                        optimizerValidationPathCacheMisses = secondPassCache.validationMisses,
                        idempotenceProfiling = idempotenceProfiling,
                        optimizerValidationPasses = 2,
                        optimizerFirstPassChangedXml = firstPass.xml != xml,
                        optimizerSecondPassChangedXml = false,
                        optimizerThirdPassChangedXml = false,
                        g315GuardedProductionTrial = productionConvergenceTrial,
                        validationClassification = validationClassification
                    )
                )
            )
        }

        // A third pass distinguishes a one-pass drift from an optimizer that
        // continues changing its own output. G3.19 may promote only a candidate
        // that matches this independently verified fixed point and passes every
        // remaining fail-closed guard.
        val thirdPassStartTime = System.nanoTime()
        val thirdPassCache = PathOptimizationCache()
        val thirdPass = optimizeVectorXmlSinglePass(
            xml = secondPass.xml,
            pathCache = thirdPassCache,
            validationPass = true,
            certificateExcludedPathData = secondPass.mergeSynthesizedPathData
        )
        val thirdPassNanos = System.nanoTime() - thirdPassStartTime
        val thirdPassValidationSnapshot = validationSnapshot(thirdPass.xml)
        val reachedFixedPoint = thirdPass.xml == secondPass.xml
        val productionConvergenceTrial = runGuardedProductionConvergenceTrial(
            firstPassXml = firstPass.xml,
            independentSecondPassXml = secondPass.xml,
            independentSecondPassIsFixed = reachedFixedPoint
        )

        val selectedXml =
            if (productionConvergenceTrial.guardAccepted) productionConvergenceTrial.candidateXml else firstPass.xml
        val selectedPathCharacters = pathDataAttributeRegex.findAll(selectedXml)
            .sumOf { it.groupValues[1].length }
        val selectedValidationSnapshot = validationSnapshot(selectedXml)
        val validationClassification = ValidationClassificationStats(
            input = inputValidationSnapshot,
            pass1 = firstPassValidationSnapshot,
            pass2 = secondPassValidationSnapshot,
            pass3 = thirdPassValidationSnapshot,
            selected = selectedValidationSnapshot
        )

        return attachFinalOutputValidation(
            firstPass.copy(
                xml = selectedXml,
                stats = firstPass.stats.copy(
                    charactersAfter = selectedPathCharacters,
                    xmlCharactersAfter = selectedXml.length,
                    optimizerIdempotenceVerified = false,
                    optimizerReachedFixedPoint = reachedFixedPoint,
                    optimizerStabilityPasses = if (reachedFixedPoint) 2 else 3,
                    optimizerValidationNanos = secondPassNanos + thirdPassNanos,
                    optimizerProductionPassNanos = firstPassNanos,
                    optimizerIdempotencePassNanos = secondPassNanos,
                    optimizerFixedPointPassNanos = thirdPassNanos,
                    optimizerValidationPathCacheHits = secondPassCache.validationHits + thirdPassCache.validationHits,
                    optimizerValidationPathCacheMisses = secondPassCache.validationMisses + thirdPassCache.validationMisses,
                    idempotenceProfiling = idempotenceProfiling,
                    optimizerValidationPasses = 3,
                    optimizerFirstPassChangedXml = firstPass.xml != xml,
                    optimizerSecondPassChangedXml = secondPass.xml != firstPass.xml,
                    optimizerThirdPassChangedXml = thirdPass.xml != secondPass.xml,
                    g315GuardedProductionTrial = productionConvergenceTrial,
                    validationClassification = validationClassification
                )
            )
        )
    }

    // Guarded production convergence.
    //
    // A changed independent second pass may become authoritative only when the
    // XML structure outside pathData is unchanged, every changed path preserves
    // exact ordered traversal, the second pass is independently verified as a
    // fixed point, and final VectorDrawable validation succeeds.
    //
    // Production never invokes the expensive sampled/bidirectional geometry
    // comparator here. If exact safety cannot be proven quickly, the guard
    // fails closed and retains the established first-pass XML.
    private fun runGuardedProductionConvergenceTrial(
        firstPassXml: String,
        independentSecondPassXml: String,
        independentSecondPassIsFixed: Boolean
    ): G315GuardedProductionTrialStats {
        val guardStart = System.nanoTime()
        var pathsExamined = 0
        var pathsChanged = 0
        var geometryComparisons = 0
        var geometryMismatches = 0
        var exactShortCircuits = 0
        // Legacy telemetry field retained for reporter compatibility.
        // Interactive production never performs the bidirectional fallback.
        val fallbackBidirectional = 0
        var comparatorFailures = 0
        var comparatorNanos = 0L

        /*
         * The independent second pass is already available at this point, and a
         * third pass independently establishes whether it is a fixed point.
         * Promote that actual second-pass XML rather than trying to reconstruct
         * it with a narrower path-only candidate.
         *
         * Promotion remains fail-closed: path counts and non-path XML structure
         * must match, every changed path must preserve exact ordered traversal,
         * the second pass must be fixed, and final output validation must pass.
         */
        val firstPathMatches = pathDataAttributeRegex.findAll(firstPassXml).toList()
        val secondPathMatches = pathDataAttributeRegex.findAll(independentSecondPassXml).toList()

        pathsExamined = firstPathMatches.size

        fun pathDataSkeleton(xml: String): String =
            pathDataAttributeRegex.replace(xml) {
                "android:pathData=\"__G320B_PATHDATA__\""
            }

        val pathCountsMatch = firstPathMatches.size == secondPathMatches.size
        val nonPathStructureMatches =
            pathCountsMatch &&
                pathDataSkeleton(firstPassXml) ==
                    pathDataSkeleton(independentSecondPassXml)

        val candidateStart = System.nanoTime()

        if (pathCountsMatch) {
            for (index in firstPathMatches.indices) {
                val before = firstPathMatches[index].groupValues[1]
                val candidate = secondPathMatches[index].groupValues[1]
                if (candidate != before) {
                    pathsChanged++
                    geometryComparisons++
                    val comparatorStart = System.nanoTime()

                    // Interactive production uses only the exact ordered-
                    // traversal oracle. The adaptive bidirectional comparator is
                    // intentionally diagnostic-only because its cost can become
                    // extreme on large-coordinate paths. If exact equivalence
                    // cannot be proven, fail closed and retain pass 1.
                    val exactDiagnostic =
                        orderedTraversalPairDiagnostic(before, candidate)

                    comparatorNanos += System.nanoTime() - comparatorStart

                    if (
                        exactDiagnostic.parseable &&
                        exactDiagnostic.endpointsPreserved &&
                        exactDiagnostic.orderedTraversalPreserved
                    ) {
                        exactShortCircuits++
                    } else {
                        geometryMismatches++
                        if (!exactDiagnostic.parseable) {
                            comparatorFailures++
                        }
                    }
                }
            }
        }

        val candidateXml = independentSecondPassXml
        val candidateAndComparatorNanos = System.nanoTime() - candidateStart
        val candidateNanos =
            (candidateAndComparatorNanos - comparatorNanos).coerceAtLeast(0L)

        val candidateChanged = candidateXml != firstPassXml
        val matchedIndependent = candidateXml == independentSecondPassXml
        val validation =
            if (candidateChanged && nonPathStructureMatches) {
                validateFinalVectorXml(candidateXml)
            } else {
                validateFinalVectorXml(firstPassXml)
            }

        val rejectionReason = when {
            !candidateChanged -> ""
            !pathCountsMatch ->
                "independent full pass 2 changed path count"
            !nonPathStructureMatches ->
                "independent full pass 2 changed non-path XML structure"
            comparatorFailures > 0 ->
                "G3.13 comparator failure"
            geometryMismatches > 0 ->
                "G3.13 geometry mismatch"
            !matchedIndependent ->
                "candidate differed from independent full pass 2"
            !independentSecondPassIsFixed ->
                "independent full pass 2 was not a fixed point"
            !validation.passed ->
                "candidate failed final VectorDrawable validation"
            else -> ""
        }

        val accepted =
            candidateChanged &&
                pathCountsMatch &&
                nonPathStructureMatches &&
                comparatorFailures == 0 &&
                geometryMismatches == 0 &&
                matchedIndependent &&
                independentSecondPassIsFixed &&
                validation.passed

        val beforeLength = firstPassXml.length
        val afterLength = candidateXml.length

        return G315GuardedProductionTrialStats(
            attempted = true,
            candidateChanged = candidateChanged,
            pathsExamined = pathsExamined,
            pathsChanged = pathsChanged,
            geometryComparisons = geometryComparisons,
            geometryMismatchCount = geometryMismatches,
            exactShortCircuitCount = exactShortCircuits,
            fallbackBidirectionalCount = fallbackBidirectional,
            comparatorFailureCount = comparatorFailures,
            matchedIndependentSecondPass = matchedIndependent,
            fixedPointVerified = independentSecondPassIsFixed,
            finalValidationPassed = validation.passed,
            guardAccepted = accepted,
            guardRejected = candidateChanged && !accepted,
            charactersBefore = beforeLength,
            charactersAfter = afterLength,
            charactersSaved = (beforeLength - afterLength).coerceAtLeast(0),
            charactersAdded = (afterLength - beforeLength).coerceAtLeast(0),
            candidateNanos = candidateNanos,
            comparatorNanos = comparatorNanos,
            guardNanos = System.nanoTime() - guardStart,
            rejectionReason = rejectionReason,
            candidateXml = candidateXml
        )
    }

    private data class FinalOutputValidation(
        val passed: Boolean,
        val validatedPathDataCount: Int,
        val invalidPathDataCount: Int,
        val nonFiniteNumberCount: Int,
        val malformedStructureCount: Int,
        val invalidViewportCount: Int,
        val unsupportedOutputConstructCount: Int,
        val witness: String = ""
    )

    private fun validationSnapshot(xml: String): ValidationSnapshotStats {
        val validation = validateFinalVectorXml(xml)
        return ValidationSnapshotStats(
            attempted = true,
            passed = validation.passed,
            validatedPathDataCount = validation.validatedPathDataCount,
            invalidPathDataCount = validation.invalidPathDataCount,
            nonFiniteNumberCount = validation.nonFiniteNumberCount,
            malformedStructureCount = validation.malformedStructureCount,
            invalidViewportCount = validation.invalidViewportCount,
            unsupportedOutputConstructCount = validation.unsupportedOutputConstructCount,
            witness = validation.witness
        )
    }

    private fun attachFinalOutputValidation(result: Result): Result {
        val startTime = System.nanoTime()
        val validation = validateFinalVectorXml(result.xml)

        return result.copy(
            stats = result.stats.copy(
                finalOutputValidationPassed = validation.passed,
                finalOutputValidationNanos = System.nanoTime() - startTime,
                validatedPathDataCount = validation.validatedPathDataCount,
                invalidPathDataCount = validation.invalidPathDataCount,
                nonFiniteNumberCount = validation.nonFiniteNumberCount,
                malformedStructureCount = validation.malformedStructureCount,
                invalidViewportCount = validation.invalidViewportCount,
                unsupportedOutputConstructCount =
                    validation.unsupportedOutputConstructCount
            )
        )
    }

    private fun isStructurallyValidVectorXml(xml: String): Boolean {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false

                runCatching {
                    setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true
                    )
                }
                runCatching {
                    setFeature(
                        "http://xml.org/sax/features/external-general-entities",
                        false
                    )
                }
                runCatching {
                    setFeature(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false
                    )
                }
            }

            val document = factory.newDocumentBuilder().parse(
                InputSource(StringReader(xml))
            )
            document.documentElement?.localName.equals(
                "vector",
                ignoreCase = true
            )
        } catch (_: Throwable) {
            false
        }
    }

    private fun validateFinalVectorXml(xml: String): FinalOutputValidation {
        var validatedPathDataCount = 0
        var invalidPathDataCount = 0
        var firstInvalidPathData = ""

        pathDataAttributeRegex.findAll(xml).forEach { match ->
            validatedPathDataCount++
            val pathData = match.groupValues[1]
            if (pathData.isBlank() || parseNormalizedSegments(pathData) == null) {
                invalidPathDataCount++
                if (firstInvalidPathData.isEmpty()) {
                    firstInvalidPathData = pathData.take(240)
                }
            }
        }

        val nonFiniteRegex = Regex(
            """(?i)(?<![A-Za-z0-9_])(?:NaN|[-+]?Infinity)(?![A-Za-z0-9_])"""
        )
        val firstNonFinite = nonFiniteRegex.find(xml)?.value.orEmpty()
        val nonFiniteNumberCount = nonFiniteRegex.findAll(xml).count()

        // Use a real XML parser rather than tag-counting regular expressions.
        // This correctly handles comments, quoted attribute values, self-closing
        // elements, nested groups, aapt elements, and future VectorDrawable tags.
        val malformedStructureCount =
            if (isStructurallyValidVectorXml(xml)) 0 else 1

        fun viewportValue(name: String): Double? {
            val match = Regex(
                """android:$name\s*=\s*["']([^"']+)["']""",
                RegexOption.IGNORE_CASE
            ).find(xml) ?: return null
            return match.groupValues[1].toDoubleOrNull()
        }

        var invalidViewportCount = 0
        val viewportWidth = viewportValue("viewportWidth")
        val viewportHeight = viewportValue("viewportHeight")
        if (viewportWidth == null || !viewportWidth.isFinite() || viewportWidth <= 0.0) {
            invalidViewportCount++
        }
        if (viewportHeight == null || !viewportHeight.isFinite() || viewportHeight <= 0.0) {
            invalidViewportCount++
        }

        val unsupportedPatterns = listOf(
            "svg" to Regex("""<svg\b""", RegexOption.IGNORE_CASE),
            "defs" to Regex("""<defs\b""", RegexOption.IGNORE_CASE),
            "use" to Regex("""<use\b""", RegexOption.IGNORE_CASE),
            "mask" to Regex("""<mask\b""", RegexOption.IGNORE_CASE),
            "filter" to Regex("""<filter\b""", RegexOption.IGNORE_CASE),
            "linearGradient" to Regex("""<linearGradient\b""", RegexOption.IGNORE_CASE),
            "radialGradient" to Regex("""<radialGradient\b""", RegexOption.IGNORE_CASE),
            "transform attribute" to Regex("""\btransform\s*=""", RegexOption.IGNORE_CASE)
        )
        var firstUnsupported = ""
        var unsupportedOutputConstructCount = 0
        unsupportedPatterns.forEach { (label, regex) ->
            val matches = regex.findAll(xml).toList()
            unsupportedOutputConstructCount += matches.size
            if (firstUnsupported.isEmpty() && matches.isNotEmpty()) {
                firstUnsupported = label
            }
        }

        val passed =
            invalidPathDataCount == 0 &&
                nonFiniteNumberCount == 0 &&
                malformedStructureCount == 0 &&
                invalidViewportCount == 0 &&
                unsupportedOutputConstructCount == 0

        val witness = when {
            invalidPathDataCount > 0 -> "invalid pathData: $firstInvalidPathData"
            nonFiniteNumberCount > 0 -> "non-finite number: $firstNonFinite"
            malformedStructureCount > 0 -> "malformed VectorDrawable XML structure"
            invalidViewportCount > 0 ->
                "invalid viewport: width=${viewportWidth ?: "missing"}, height=${viewportHeight ?: "missing"}"
            unsupportedOutputConstructCount > 0 -> "unsupported final construct: $firstUnsupported"
            else -> ""
        }

        return FinalOutputValidation(
            passed = passed,
            validatedPathDataCount = validatedPathDataCount,
            invalidPathDataCount = invalidPathDataCount,
            nonFiniteNumberCount = nonFiniteNumberCount,
            malformedStructureCount = malformedStructureCount,
            invalidViewportCount = invalidViewportCount,
            unsupportedOutputConstructCount = unsupportedOutputConstructCount,
            witness = witness
        )
    }

    private fun optimizeVectorXmlSinglePass(
        xml: String,
        pathCache: PathOptimizationCache,
        validationPass: Boolean,
        certificateExcludedPathData: Set<String> = emptySet()
    ): Result {
        fun characterDelta(before: String, after: String): Int =
            before.length - after.length

        fun charactersSaved(before: String, after: String): Int =
            characterDelta(before, after).coerceAtLeast(0)

        var pathCount = 0
        var charactersBefore = 0
        var repeatedCommandsRemoved = 0
        var redundantNonDrawingSegmentsRemoved = 0
        var collinearLineSegmentsConsolidated = 0
        var straightBezierCurvesSimplified = 0
        var degenerateArcsSimplified = 0
        var smoothBezierShorthandsSelected = 0
        var cubicCurvesReducedToQuadratic = 0
        var arcRotationsCanonicalized = 0
        var arcRadiiCanonicalized = 0
        var arcHalfTurnRotationsReduced = 0
        var arcAxesSwappedForSize = 0
        var arcRepresentationsGloballyMinimized = 0
        var commandSequencesGloballyMinimized = 0
        var implicitLineTosAfterMoveSelected = 0
        var repeatedShorthandCurveCommandsOmitted = 0
        var repeatedFullCurveCommandsOmitted = 0
        var repeatedArcCommandsOmitted = 0
        var scientificNotationValuesSelected = 0
        var globallyOptimizedNumericPaths = 0
        var numbersNormalized = 0
        var shorterCommandFormsSelected = 0
        var relativeCommandsSelected = 0
        var axisCommandsSelected = 0
        val pathProfiling = PathSyntaxProfiling()
        var h23SyntaxNormalizationCharacterDelta = 0
        var h23RedundantGeometryCharacterDelta = 0
        var h23ArcCleanupCharacterDelta = 0
        var h23CurveSimplificationCharacterDelta = 0
        var h23CollinearConsolidationCharacterDelta = 0
        var h23LocalCommandShorteningCharacterDelta = 0
        var h23GlobalCommandMinimizationCharacterDelta = 0
        var h23GlobalNumericSerializationCharacterDelta = 0
        var h24PathSyntaxCandidatesRejectedForSize = 0
        var h24PathSyntaxCharactersAvoided = 0
        var i2PathSyntaxStableInputs = 0
        var i2PathSyntaxStableInputNanos = 0L
        var i41CertificatePredictedFixed = 0
        var i41CertificateTruePositive = 0
        var i41CertificateFalsePositive = 0
        var i41CertificateFalseNegative = 0
        var i41CertificateTrueNegative = 0
        var i41CertificateCheckNanos = 0L
        var i41PotentialAvoidableOptimizerNanos = 0L
        var i41FalsePositiveOptimizerNanos = 0L
        var i41RejectedLexical = 0
        var i41RejectedNumericSpelling = 0
        var i41RejectedWhitespace = 0
        var i41RejectedComplexCommandFamily = 0
        var i41RejectedExplicitRepeat = 0
        var i42ProvenanceExcluded = 0
        var i42ProvenanceExcludedActuallyFixed = 0
        var i42ProvenancePreventedFalsePositive = 0
        var i42ProvenanceExcludedOptimizerNanos = 0L
        var i42PreventedFalsePositiveOptimizerNanos = 0L
        var i42PreventedChangedSyntaxNormalization = 0
        var i42PreventedChangedGeometryCleanup = 0
        var i42PreventedChangedLocalShortening = 0
        var i42PreventedChangedGlobalCommand = 0
        var i42PreventedChangedGlobalNumeric = 0
        var i42PreventedChangedOther = 0
        var i43ComplexCandidatesExamined = 0
        var i43ComplexPredictedFixed = 0
        var i43ComplexTruePositive = 0
        var i43ComplexFalsePositive = 0
        var i43ComplexFalseNegative = 0
        var i43ComplexTrueNegative = 0
        var i43ComplexCheckNanos = 0L
        var i43ComplexPotentialAvoidableOptimizerNanos = 0L
        var i43ComplexFalsePositiveOptimizerNanos = 0L
        var i43RejectedReflectiveShorthand = 0
        var i43RejectedNumericSpelling = 0
        var i43RejectedExplicitRepeat = 0
        var i43RejectedProvenance = 0
        var i43CubicPredicted = 0
        var i43CubicTruePositive = 0
        var i43CubicFalsePositive = 0
        var i43QuadraticPredicted = 0
        var i43QuadraticTruePositive = 0
        var i43QuadraticFalsePositive = 0
        var i43ArcPredicted = 0
        var i43ArcTruePositive = 0
        var i43ArcFalsePositive = 0
        var i43MixedPredicted = 0
        var i43MixedTruePositive = 0
        var i43MixedFalsePositive = 0
        var i43FalsePositiveGeometryCleanup = 0
        var i43FalsePositiveLocalShortening = 0
        var i43FalsePositiveGlobalCommand = 0
        var i43FalsePositiveGlobalNumeric = 0
        var i43FalsePositiveOther = 0

        val pathSyntaxStartTime = System.nanoTime()
        val syntaxOptimizedXml = pathDataAttributeRegex.replace(xml) { match ->
            val original = match.groupValues[1]

            var i41Certificate: I41FixedPointCertificate? = null
            var i42ExcludedByProvenance = false
            var i42WouldPredictFixedWithoutProvenance = false
            var i43Certificate: I43ComplexCertificate? = null
            if (validationPass) {
                val certificateStart = System.nanoTime()
                val rawCertificate = i41CheapFixedPointCertificate(original)

                if (rawCertificate.rejectionReason == "complexCommand") {
                    i43ComplexCandidatesExamined++
                    val i43Start = System.nanoTime()
                    val experimental = i43ComplexFixedPointCertificate(original)
                    i43ComplexCheckNanos += System.nanoTime() - i43Start

                    i43Certificate =
                        if (experimental.predictedFixed && original in certificateExcludedPathData) {
                            i43RejectedProvenance++
                            I43ComplexCertificate(
                                false,
                                family = experimental.family,
                                rejectionReason = "mergeProvenance"
                            )
                        } else {
                            experimental
                        }

                    if (i43Certificate?.predictedFixed == true) {
                        i43ComplexPredictedFixed++
                        when (i43Certificate?.family) {
                            "cubic" -> i43CubicPredicted++
                            "quadratic" -> i43QuadraticPredicted++
                            "arc" -> i43ArcPredicted++
                            "mixed" -> i43MixedPredicted++
                        }
                    } else {
                        when (i43Certificate?.rejectionReason) {
                            "reflectiveShorthand" -> i43RejectedReflectiveShorthand++
                            "numericSpelling" -> i43RejectedNumericSpelling++
                            "explicitRepeat" -> i43RejectedExplicitRepeat++
                        }
                    }
                }

                i42WouldPredictFixedWithoutProvenance = rawCertificate.predictedFixed
                i42ExcludedByProvenance =
                    rawCertificate.predictedFixed && original in certificateExcludedPathData

                i41Certificate = if (i42ExcludedByProvenance) {
                    I41FixedPointCertificate(false, "mergeProvenance")
                } else {
                    rawCertificate
                }
                i41CertificateCheckNanos += System.nanoTime() - certificateStart

                if (i42ExcludedByProvenance) {
                    i42ProvenanceExcluded++
                }

                if (i41Certificate.predictedFixed) {
                    i41CertificatePredictedFixed++
                } else {
                    when (i41Certificate.rejectionReason) {
                        "lexical" -> i41RejectedLexical++
                        "numericSpelling" -> i41RejectedNumericSpelling++
                        "whitespace" -> i41RejectedWhitespace++
                        "complexCommand" -> i41RejectedComplexCommandFamily++
                        "explicitRepeat" -> i41RejectedExplicitRepeat++
                    }
                }
            }

            val i2PathCallStart = System.nanoTime()
            val optimized = optimizePathDataCached(
                pathData = original,
                cache = pathCache,
                validationPass = validationPass,
                profiling = pathProfiling
            )
            val i2PathCallNanos = System.nanoTime() - i2PathCallStart
            val i41ActuallyFixed = optimized.pathData == original
            if (i41ActuallyFixed) {
                i2PathSyntaxStableInputs++
                i2PathSyntaxStableInputNanos += i2PathCallNanos
            }

            if (validationPass && i43Certificate != null) {
                when {
                    i43Certificate?.predictedFixed == true && i41ActuallyFixed -> {
                        i43ComplexTruePositive++
                        i43ComplexPotentialAvoidableOptimizerNanos += i2PathCallNanos
                        when (i43Certificate?.family) {
                            "cubic" -> i43CubicTruePositive++
                            "quadratic" -> i43QuadraticTruePositive++
                            "arc" -> i43ArcTruePositive++
                            "mixed" -> i43MixedTruePositive++
                        }
                    }
                    i43Certificate?.predictedFixed == true && !i41ActuallyFixed -> {
                        i43ComplexFalsePositive++
                        i43ComplexFalsePositiveOptimizerNanos += i2PathCallNanos
                        when (i43Certificate?.family) {
                            "cubic" -> i43CubicFalsePositive++
                            "quadratic" -> i43QuadraticFalsePositive++
                            "arc" -> i43ArcFalsePositive++
                            "mixed" -> i43MixedFalsePositive++
                        }
                        when {
                            optimized.h23RedundantGeometryCharacterDelta != 0 ||
                                optimized.h23ArcCleanupCharacterDelta != 0 ||
                                optimized.h23CurveSimplificationCharacterDelta != 0 ||
                                optimized.h23CollinearConsolidationCharacterDelta != 0 ->
                                i43FalsePositiveGeometryCleanup++
                            optimized.h23LocalCommandShorteningCharacterDelta != 0 ->
                                i43FalsePositiveLocalShortening++
                            optimized.h23GlobalCommandMinimizationCharacterDelta != 0 ->
                                i43FalsePositiveGlobalCommand++
                            optimized.h23GlobalNumericSerializationCharacterDelta != 0 ->
                                i43FalsePositiveGlobalNumeric++
                            else -> i43FalsePositiveOther++
                        }
                    }
                    i43Certificate?.predictedFixed == false && i41ActuallyFixed -> {
                        i43ComplexFalseNegative++
                    }
                    else -> {
                        i43ComplexTrueNegative++
                    }
                }
            }

            if (validationPass && i42ExcludedByProvenance && i42WouldPredictFixedWithoutProvenance) {
                i42ProvenanceExcludedOptimizerNanos += i2PathCallNanos
                if (i41ActuallyFixed) {
                    i42ProvenanceExcludedActuallyFixed++
                } else {
                    i42ProvenancePreventedFalsePositive++
                    i42PreventedFalsePositiveOptimizerNanos += i2PathCallNanos
                    when {
                        optimized.h23SyntaxNormalizationCharacterDelta != 0 ->
                            i42PreventedChangedSyntaxNormalization++
                        optimized.h23RedundantGeometryCharacterDelta != 0 ||
                            optimized.h23ArcCleanupCharacterDelta != 0 ||
                            optimized.h23CurveSimplificationCharacterDelta != 0 ||
                            optimized.h23CollinearConsolidationCharacterDelta != 0 ->
                            i42PreventedChangedGeometryCleanup++
                        optimized.h23LocalCommandShorteningCharacterDelta != 0 ->
                            i42PreventedChangedLocalShortening++
                        optimized.h23GlobalCommandMinimizationCharacterDelta != 0 ->
                            i42PreventedChangedGlobalCommand++
                        optimized.h23GlobalNumericSerializationCharacterDelta != 0 ->
                            i42PreventedChangedGlobalNumeric++
                        else -> i42PreventedChangedOther++
                    }
                }
            }

            if (validationPass && i41Certificate != null) {
                when {
                    i41Certificate.predictedFixed && i41ActuallyFixed -> {
                        i41CertificateTruePositive++
                        i41PotentialAvoidableOptimizerNanos += i2PathCallNanos
                    }
                    i41Certificate.predictedFixed && !i41ActuallyFixed -> {
                        i41CertificateFalsePositive++
                        i41FalsePositiveOptimizerNanos += i2PathCallNanos
                    }
                    !i41Certificate.predictedFixed && i41ActuallyFixed -> {
                        i41CertificateFalseNegative++
                    }
                    else -> i41CertificateTrueNegative++
                }
            }

            pathCount++
            charactersBefore += original.length

            // Production size policy: the complete PathData rewrite is a candidate.
            // Reject only candidates that are strictly longer than the
            // incoming spelling. Equal-length optimized output remains canonical.
            val pathSyntaxAccepted = optimized.pathData.length <= original.length
            val selectedPathData = if (pathSyntaxAccepted) {
                optimized.pathData
            } else {
                h24PathSyntaxCandidatesRejectedForSize++
                h24PathSyntaxCharactersAvoided +=
                    optimized.pathData.length - original.length
                original
            }

            // Retain attempted-rewrite telemetry for forensic diagnostics.
            h23SyntaxNormalizationCharacterDelta += optimized.h23SyntaxNormalizationCharacterDelta
            h23RedundantGeometryCharacterDelta += optimized.h23RedundantGeometryCharacterDelta
            h23ArcCleanupCharacterDelta += optimized.h23ArcCleanupCharacterDelta
            h23CurveSimplificationCharacterDelta += optimized.h23CurveSimplificationCharacterDelta
            h23CollinearConsolidationCharacterDelta += optimized.h23CollinearConsolidationCharacterDelta
            h23LocalCommandShorteningCharacterDelta += optimized.h23LocalCommandShorteningCharacterDelta
            h23GlobalCommandMinimizationCharacterDelta += optimized.h23GlobalCommandMinimizationCharacterDelta
            h23GlobalNumericSerializationCharacterDelta += optimized.h23GlobalNumericSerializationCharacterDelta

            // Activity counters describe only rewrites actually emitted.
            if (pathSyntaxAccepted) {
                repeatedCommandsRemoved += optimized.repeatedCommandsRemoved
                redundantNonDrawingSegmentsRemoved += optimized.redundantNonDrawingSegmentsRemoved
                collinearLineSegmentsConsolidated += optimized.collinearLineSegmentsConsolidated
                straightBezierCurvesSimplified += optimized.straightBezierCurvesSimplified
                degenerateArcsSimplified += optimized.degenerateArcsSimplified
                smoothBezierShorthandsSelected += optimized.smoothBezierShorthandsSelected
                cubicCurvesReducedToQuadratic += optimized.cubicCurvesReducedToQuadratic
                arcRotationsCanonicalized += optimized.arcRotationsCanonicalized
                arcRadiiCanonicalized += optimized.arcRadiiCanonicalized
                arcHalfTurnRotationsReduced += optimized.arcHalfTurnRotationsReduced
                arcAxesSwappedForSize += optimized.arcAxesSwappedForSize
                arcRepresentationsGloballyMinimized += optimized.arcRepresentationsGloballyMinimized
                commandSequencesGloballyMinimized += optimized.commandSequencesGloballyMinimized
                implicitLineTosAfterMoveSelected += optimized.implicitLineTosAfterMoveSelected
                repeatedShorthandCurveCommandsOmitted += optimized.repeatedShorthandCurveCommandsOmitted
                repeatedFullCurveCommandsOmitted += optimized.repeatedFullCurveCommandsOmitted
                repeatedArcCommandsOmitted += optimized.repeatedArcCommandsOmitted
                scientificNotationValuesSelected += optimized.scientificNotationValuesSelected
                globallyOptimizedNumericPaths += optimized.globallyOptimizedNumericPaths
                numbersNormalized += optimized.numbersNormalized
                shorterCommandFormsSelected += optimized.shorterCommandFormsSelected
                relativeCommandsSelected += optimized.relativeCommandsSelected
                axisCommandsSelected += optimized.axisCommandsSelected
            }

            "android:pathData=\"$selectedPathData\""
        }

        val colorNormalizationStartTime = System.nanoTime()
        val colorNormalizedXml = normalizeAndroidColorAttributes(syntaxOptimizedXml)
        val colorNormalizationNanos = System.nanoTime() - colorNormalizationStartTime
        val pathSyntaxOptimizationNanos = System.nanoTime() - pathSyntaxStartTime
        val pathSyntaxCharactersSaved = charactersSaved(xml, colorNormalizedXml)
        val h22PathDataSyntaxCharacterDelta = characterDelta(xml, syntaxOptimizedXml)
        val h22ColorNormalizationCharacterDelta =
            characterDelta(syntaxOptimizedXml, colorNormalizedXml)
        val h21PathSyntaxCharacterDelta = characterDelta(xml, colorNormalizedXml)

        val pruningStartTime = System.nanoTime()
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
        val pruningAndGroupCleanupNanos = System.nanoTime() - pruningStartTime
        val pruningCleanupCharactersSaved =
            charactersSaved(colorNormalizedXml, groupCleanup.xml)
        val h21PruningCharacterDelta =
            characterDelta(colorNormalizedXml, groupCleanup.xml)

        val transformStartTime = System.nanoTime()

        val identityCompositionStartTime = System.nanoTime()
        val identityCleanup = removeIdentityGroupTransformAttributes(groupCleanup.xml)
        val translationComposition = composeNestedParentTranslationGroups(identityCleanup.xml)
        val compatibleComposition =
            composeNestedCompatibleSamePivotGroups(translationComposition.xml)
        val postCompositionIdentityCleanup =
            removeIdentityGroupTransformAttributes(compatibleComposition.xml)
        val transformIdentityCompositionNanos =
            System.nanoTime() - identityCompositionStartTime

        val factoringFlatteningStartTime = System.nanoTime()
        val commonTranslationFactoring =
            factorCommonSiblingTranslations(postCompositionIdentityCleanup.xml)
        val groupFlattening = flattenRedundantGroups(commonTranslationFactoring.xml)
        val transformFactoringFlatteningNanos =
            System.nanoTime() - factoringFlatteningStartTime

        val scaleFlatteningStartTime = System.nanoTime()
        val uniformScaleProfiling = MutableUniformScaleProfiling()
        val uniformScaleFlatteningStartTime = System.nanoTime()
        val scaleFlattening = flattenUniformPositiveScaleGroups(
            groupFlattening.xml,
            uniformScaleProfiling
        )
        val transformUniformScaleFlatteningNanos =
            System.nanoTime() - uniformScaleFlatteningStartTime
        val nonUniformScaleFlatteningStartTime = System.nanoTime()
        val nonUniformScaleFlattening =
            flattenNonUniformPositiveScaleFillOnlyGroups(scaleFlattening.xml)
        val transformNonUniformScaleFlatteningNanos =
            System.nanoTime() - nonUniformScaleFlatteningStartTime
        val transformScaleFlatteningNanos =
            System.nanoTime() - scaleFlatteningStartTime

        val rotationTranslationStartTime = System.nanoTime()
        val rotationFlattening =
            flattenRotationOnlyGroups(nonUniformScaleFlattening.xml)
        val translationFlattening =
            flattenTranslationOnlyGroups(rotationFlattening.xml)
        val transformRotationTranslationNanos =
            System.nanoTime() - rotationTranslationStartTime

        val canonicalizationStartTime = System.nanoTime()
        val adjacentGroupCoalescing =
            coalesceIdenticalAdjacentGroups(translationFlattening.xml)
        val transformCanonicalization =
            canonicalizeGroupTransformAttributes(adjacentGroupCoalescing.xml)
        val transformCanonicalizationNanos =
            System.nanoTime() - canonicalizationStartTime
        val transformOptimizationNanos = System.nanoTime() - transformStartTime
        val transformCharactersSaved =
            charactersSaved(groupCleanup.xml, transformCanonicalization.xml)
        val h21TransformCharacterDelta =
            characterDelta(groupCleanup.xml, transformCanonicalization.xml)

        val numericProfiling = NumericCleanupProfiling()
        val numericCleanupStartTime = System.nanoTime()
        val nearIntegerSnapping = snapNearIntegerPathValues(transformCanonicalization.xml)
        val nearIntegerSnappingNanos = System.nanoTime() - numericCleanupStartTime
        val h21NearIntegerCharacterDelta =
            characterDelta(transformCanonicalization.xml, nearIntegerSnapping.xml)

        val deduplicationStartTime = System.nanoTime()
        val duplicateRemoval =
            removeExactAdjacentDuplicatePaths(nearIntegerSnapping.xml)
        val pathMerging = mergeCompatibleAdjacentPaths(duplicateRemoval.xml)
        val deduplicationAndMergeNanos = System.nanoTime() - deduplicationStartTime
        val deduplicationCharactersSaved =
            charactersSaved(nearIntegerSnapping.xml, pathMerging.xml)
        val h21DedupMergeCharacterDelta =
            characterDelta(nearIntegerSnapping.xml, pathMerging.xml)

        // A11.2.1: run decimal canonicalization after every geometry-producing
        // optimization. Earlier passes may create new relative deltas, so this
        // must be the final path-data mutation before XML formatting.
        val decimalCanonicalizationStartTime = System.nanoTime()
        val decimalCanonicalization =
            canonicalizePathDecimalPrecisionCached(
                xml = pathMerging.xml,
                pathCache = pathCache,
                validationPass = validationPass,
                profiling = numericProfiling
            )
        val decimalCanonicalizationNanos =
            System.nanoTime() - decimalCanonicalizationStartTime
        val numericCleanupNanos =
            nearIntegerSnappingNanos + decimalCanonicalizationNanos
        val h21DecimalCanonicalizationCharacterDelta =
            characterDelta(pathMerging.xml, decimalCanonicalization.xml)
        val numericCleanupCharactersSaved =
            charactersSaved(transformCanonicalization.xml, nearIntegerSnapping.xml) +
                charactersSaved(pathMerging.xml, decimalCanonicalization.xml)

        val finalFormattingStartTime = System.nanoTime()
        val finalXml = formatVectorXml(decimalCanonicalization.xml)
        val finalFormattingNanos = System.nanoTime() - finalFormattingStartTime
        val formattingCharactersSaved =
            charactersSaved(decimalCanonicalization.xml, finalXml)
        val h21FormattingCharacterDelta =
            characterDelta(decimalCanonicalization.xml, finalXml)
        val charactersAfter = pathDataAttributeRegex.findAll(finalXml)
            .sumOf { it.groupValues[1].length }

        return Result(
            xml = finalXml,
            stats = Stats(
                pathCount = pathCount,
                charactersBefore = charactersBefore,
                charactersAfter = charactersAfter,
                repeatedCommandsRemoved = repeatedCommandsRemoved,
                redundantNonDrawingSegmentsRemoved =
                    redundantNonDrawingSegmentsRemoved,
                collinearLineSegmentsConsolidated =
                    collinearLineSegmentsConsolidated,
                straightBezierCurvesSimplified =
                    straightBezierCurvesSimplified,
                degenerateArcsSimplified =
                    degenerateArcsSimplified,
                smoothBezierShorthandsSelected =
                    smoothBezierShorthandsSelected,
                cubicCurvesReducedToQuadratic =
                    cubicCurvesReducedToQuadratic,
                arcRotationsCanonicalized =
                    arcRotationsCanonicalized,
                arcRadiiCanonicalized =
                    arcRadiiCanonicalized,
                arcHalfTurnRotationsReduced =
                    arcHalfTurnRotationsReduced,
                arcAxesSwappedForSize =
                    arcAxesSwappedForSize,
                arcRepresentationsGloballyMinimized =
                    arcRepresentationsGloballyMinimized,
                commandSequencesGloballyMinimized =
                    commandSequencesGloballyMinimized,
                implicitLineTosAfterMoveSelected =
                    implicitLineTosAfterMoveSelected,
                repeatedShorthandCurveCommandsOmitted =
                    repeatedShorthandCurveCommandsOmitted,
                repeatedFullCurveCommandsOmitted =
                    repeatedFullCurveCommandsOmitted,
                repeatedArcCommandsOmitted =
                    repeatedArcCommandsOmitted,
                scientificNotationValuesSelected =
                    scientificNotationValuesSelected,
                globallyOptimizedNumericPaths =
                    globallyOptimizedNumericPaths,
                numbersNormalized =
                    numbersNormalized +
                        nearIntegerSnapping.snappedValues +
                        decimalCanonicalization.changedValues,
                nearIntegerValuesSnapped = nearIntegerSnapping.snappedValues,
                decimalValuesCanonicalized = decimalCanonicalization.changedValues,
                emptyPathDataRemoved = emptyPathDataRemoved,
                moveOnlyPathsRemoved = moveOnlyPathsRemoved,
                invisiblePathsRemoved = invisiblePathsRemoved,
                emptyGroupsRemoved = groupCleanup.removedCount,
                redundantGroupsFlattened = groupFlattening.flattenedCount,
                commonTranslationGroupsFactored = commonTranslationFactoring.factoredGroups,
                adjacentGroupsCoalesced = adjacentGroupCoalescing.coalescedGroups,
                compatiblePathsMerged = pathMerging.mergedCount,
                compatiblePathMergesPreservedForSize =
                    pathMerging.preservedForSize,
                adjacentPathPairsExamined =
                    pathMerging.opportunityStats.pairsExamined,
                adjacentPathPairsSamePaint =
                    pathMerging.opportunityStats.samePaintPairs,
                adjacentPathMergeRejectedNestedPaint =
                    pathMerging.opportunityStats.rejectedNestedPaint,
                adjacentPathMergeRejectedMissingPathData =
                    pathMerging.opportunityStats.rejectedMissingPathData,
                adjacentPathMergeRejectedPaintMismatch =
                    pathMerging.opportunityStats.rejectedPaintMismatch,
                adjacentPathMergeRejectedUnsupportedGeometry =
                    pathMerging.opportunityStats.rejectedUnsupportedGeometry,
                adjacentPathMergeRejectedOverlapSafety =
                    pathMerging.opportunityStats.rejectedOverlapSafety,
                adjacentPathMergeRejectedForSize =
                    pathMerging.opportunityStats.rejectedForSize,
                exactDuplicatePathsRemoved = duplicateRemoval.removedCount,
                translatedGroupsFlattened = translationFlattening.flattenedGroups,
                translatedPaths = translationFlattening.translatedPaths,
                translationGroupsPreservedForSize =
                    translationFlattening.preservedForSize,
                scaledGroupsFlattened = scaleFlattening.flattenedGroups,
                scaledPaths = scaleFlattening.scaledPaths,
                scaledStrokeWidths = scaleFlattening.scaledStrokeWidths,
                scaleGroupsPreservedForSize =
                    scaleFlattening.preservedForSize,
                nonUniformScaleGroupsFlattened =
                    nonUniformScaleFlattening.flattenedGroups,
                nonUniformScaledPaths =
                    nonUniformScaleFlattening.scaledPaths,
                nonUniformScaleGroupsPreservedForSize =
                    nonUniformScaleFlattening.preservedForSize,
                rotationGroupsFlattened =
                    rotationFlattening.flattenedGroups,
                rotatedPaths =
                    rotationFlattening.rotatedPaths,
                rotationGroupsPreservedForSize =
                    rotationFlattening.preservedForSize,
                identityTransformAttributesRemoved =
                    identityCleanup.removedAttributes +
                        postCompositionIdentityCleanup.removedAttributes,
                nestedTransformGroupsComposed =
                    translationComposition.composedGroups +
                        compatibleComposition.composedGroups,
                transformAttributesCanonicalized =
                    transformCanonicalization.canonicalizedAttributes,
                zeroPivotAttributesRemoved =
                    transformCanonicalization.zeroPivotsRemoved,
                transformGroupsReordered =
                    transformCanonicalization.reorderedGroups,
                shorterCommandFormsSelected = shorterCommandFormsSelected,
                relativeCommandsSelected = relativeCommandsSelected,
                axisCommandsSelected = axisCommandsSelected,
                pathSyntaxOptimizationNanos = pathSyntaxOptimizationNanos,
                pathTokenizationNormalizationNanos =
                    pathProfiling.tokenizationNormalizationNanos,
                pathGeometryCleanupNanos = pathProfiling.geometryCleanupNanos,
                pathRedundantSegmentCleanupNanos =
                    pathProfiling.redundantSegmentCleanupNanos,
                pathArcCleanupNanos = pathProfiling.arcCleanupNanos,
                pathCurveSimplificationNanos =
                    pathProfiling.curveSimplificationNanos,
                pathCurveSimplificationProfiling = CurveSimplificationProfilingStats(
                    cubicToQuadraticNanos = pathProfiling.curveCubicToQuadraticNanos,
                    cubicParseSetupNanos = pathProfiling.curveCubicParseSetupNanos,
                    cubicScanNanos = pathProfiling.curveCubicScanNanos,
                    cubicRebuildValidationNanos = pathProfiling.curveCubicRebuildValidationNanos,
                    cubicRebuildNanos = pathProfiling.curveCubicRebuildNanos,
                    cubicValidationNanos = pathProfiling.curveCubicValidationNanos,
                    straightBezierNanos = pathProfiling.curveStraightBezierNanos,
                    straightParseSetupNanos = pathProfiling.curveStraightParseSetupNanos,
                    straightScanNanos = pathProfiling.curveStraightScanNanos,
                    straightRebuildValidationNanos = pathProfiling.curveStraightRebuildValidationNanos,
                    straightRebuildNanos = pathProfiling.curveStraightRebuildNanos,
                    straightValidationNanos = pathProfiling.curveStraightValidationNanos,
                    parseCalls = pathProfiling.curveParseCalls,
                    duplicateParseInputs = pathProfiling.curveDuplicateParseInputs,
                    secondPassReparsedUnchangedInput = pathProfiling.curveSecondPassReparsedUnchangedInput,
                    cubicChangedPaths = pathProfiling.curveCubicChangedPaths,
                    straightChangedPaths = pathProfiling.curveStraightChangedPaths,
                    rebuildAttempts = pathProfiling.curveRebuildAttempts,
                    rebuildNoOpResults = pathProfiling.curveRebuildNoOpResults,
                    validationCalls = pathProfiling.curveValidationCalls,
                    validationAccepted = pathProfiling.curveValidationAccepted,
                    validationRejected = pathProfiling.curveValidationRejected,
                    rebuildRejectedForSize = pathProfiling.curveRebuildRejectedForSize
                ),
                pathCollinearConsolidationNanos =
                    pathProfiling.collinearConsolidationNanos,
                pathCommandMinimizationNanos = pathProfiling.commandMinimizationNanos,
                pathCommandLocalShorteningNanos = pathProfiling.commandLocalShorteningNanos,
                pathCommandLocalProfiling = CommandLocalProfilingStats(
                    parseSetupNanos = pathProfiling.commandLocalParseSetupNanos,
                    absoluteRelativeCandidateNanos = pathProfiling.commandLocalAbsoluteRelativeCandidateNanos,
                    axisCandidateNanos = pathProfiling.commandLocalAxisCandidateNanos,
                    smoothShorthandCandidateNanos = pathProfiling.commandLocalSmoothShorthandCandidateNanos,
                    encodingSelectionNanos = pathProfiling.commandLocalEncodingSelectionNanos,
                    numericSerializationNanos = pathProfiling.commandLocalNumericSerializationNanos,
                    separatorCalculationNanos = pathProfiling.commandLocalSeparatorCalculationNanos,
                    commandOmissionNanos = pathProfiling.commandLocalCommandOmissionNanos,
                    stringConstructionNanos = pathProfiling.commandLocalStringConstructionNanos,
                    winnerSelectionNanos = pathProfiling.commandLocalWinnerSelectionNanos,
                    stateBookkeepingNanos = pathProfiling.commandLocalStateBookkeepingNanos,
                    numericSerializationCalls = pathProfiling.commandLocalNumericSerializationCalls,
                    numericSerializationCacheHits = pathProfiling.commandLocalNumericSerializationCacheHits,
                    numericSerializationUniqueValues = pathProfiling.commandLocalNumericSerializationUniqueValues
                ),
                pathCommandGlobalParseSetupNanos = pathProfiling.commandGlobalParseSetupNanos,
                pathCommandGlobalCandidateGenerationNanos =
                    pathProfiling.commandGlobalCandidateGenerationNanos,
                pathCommandGlobalDynamicProgrammingNanos =
                    pathProfiling.commandGlobalDynamicProgrammingNanos,
                pathCommandGlobalProfiling = CommandGlobalProfilingStats(
                    transitionEvaluationNanos = pathProfiling.commandGlobalTransitionEvaluationNanos,
                    separatorOmissionCostNanos = pathProfiling.commandGlobalSeparatorOmissionCostNanos,
                    segmentEncodingNanos = pathProfiling.commandGlobalSegmentEncodingNanos,
                    stateCreationNanos = pathProfiling.commandGlobalStateCreationNanos,
                    bestStateComparisonNanos = pathProfiling.commandGlobalBestStateComparisonNanos,
                    reconstructionNanos = pathProfiling.commandGlobalReconstructionNanos,
                    stateKeyCreationNanos = pathProfiling.commandGlobalStateKeyCreationNanos,
                    stateKeyFieldPreparationNanos = pathProfiling.commandGlobalStateKeyFieldPreparationNanos,
                    stateKeyPreviousCommandNanos = pathProfiling.commandGlobalStateKeyPreviousCommandNanos,
                    stateKeyPreviousNumberNanos = pathProfiling.commandGlobalStateKeyPreviousNumberNanos,
                    stateKeyAxisDirectionNanos = pathProfiling.commandGlobalStateKeyAxisDirectionNanos,
                    stateKeyAllocationNanos = pathProfiling.commandGlobalStateKeyAllocationNanos,
                    stateStringConcatenationNanos = pathProfiling.commandGlobalStateStringConcatenationNanos,
                    stateMetadataPropagationNanos = pathProfiling.commandGlobalStateMetadataPropagationNanos,
                    statePathAllocationNanos = pathProfiling.commandGlobalStatePathAllocationNanos,
                    bestStateMapLookupNanos = pathProfiling.commandGlobalBestStateMapLookupNanos,
                    bestStateDecisionNanos = pathProfiling.commandGlobalBestStateDecisionNanos,
                    bestStateReplacementNanos = pathProfiling.commandGlobalBestStateReplacementNanos,
                    stateMapLookupCalls = pathProfiling.commandGlobalStateMapLookupCalls,
                    stateMapLookupHits = pathProfiling.commandGlobalStateMapLookupHits,
                    stateMapLookupMisses = pathProfiling.commandGlobalStateMapLookupMisses,
                    stateMapInsertions = pathProfiling.commandGlobalStateMapInsertions,
                    stateMapReplacements = pathProfiling.commandGlobalStateMapReplacements,
                    segmentEncodingRequests = pathProfiling.commandGlobalSegmentEncodingRequests,
                    segmentEncodingCacheHits = pathProfiling.commandGlobalSegmentEncodingCacheHits,
                    segmentEncodingUniqueKeys = pathProfiling.commandGlobalSegmentEncodingUniqueKeys
                ),
                pathNumericSerializationNanos = pathProfiling.numericSerializationNanos,
                colorNormalizationNanos = colorNormalizationNanos,
                pruningAndGroupCleanupNanos = pruningAndGroupCleanupNanos,
                transformOptimizationNanos = transformOptimizationNanos,
                transformIdentityCompositionNanos = transformIdentityCompositionNanos,
                transformFactoringFlatteningNanos = transformFactoringFlatteningNanos,
                transformScaleFlatteningNanos = transformScaleFlatteningNanos,
                transformUniformScaleFlatteningNanos =
                    transformUniformScaleFlatteningNanos,
                transformUniformScaleProfiling = uniformScaleProfiling.snapshot(),
                transformNonUniformScaleFlatteningNanos =
                    transformNonUniformScaleFlatteningNanos,
                transformRotationTranslationNanos = transformRotationTranslationNanos,
                transformCanonicalizationNanos = transformCanonicalizationNanos,
                deduplicationAndMergeNanos = deduplicationAndMergeNanos,
                numericCleanupNanos = numericCleanupNanos,
                nearIntegerSnappingNanos = nearIntegerSnappingNanos,
                decimalCanonicalizationNanos = decimalCanonicalizationNanos,
                decimalTokenizationNanos = numericProfiling.decimalTokenizationNanos,
                decimalRebuildNanos = numericProfiling.decimalRebuildNanos,
                decimalReoptimizationNanos = numericProfiling.decimalReoptimizationNanos,
                decimalValidationNanos = numericProfiling.decimalValidationNanos,
                decimalPathsExamined = numericProfiling.decimalPathsExamined,
                i2PathSyntaxStableInputs = i2PathSyntaxStableInputs,
                i2PathSyntaxStableInputNanos = i2PathSyntaxStableInputNanos,
                i2DecimalShadowPathsCompared = numericProfiling.i2ShadowPathsCompared,
                i2DecimalShadowByteIdentical = numericProfiling.i2ShadowByteIdentical,
                i2DecimalShadowDifferent = numericProfiling.i2ShadowDifferent,
                i2DecimalShadowFastShorter = numericProfiling.i2ShadowFastShorter,
                i2DecimalShadowReferenceShorter = numericProfiling.i2ShadowReferenceShorter,
                i2DecimalShadowEqualLengthDifferent =
                    numericProfiling.i2ShadowEqualLengthDifferent,
                i2DecimalShadowFastInvalid = numericProfiling.i2ShadowFastInvalid,
                i2DecimalShadowFastNonFixed = numericProfiling.i2ShadowFastNonFixed,
                i2DecimalShadowCharacterDeltaVsReference =
                    numericProfiling.i2ShadowCharacterDeltaVsReference,
                i2DecimalShadowNanos = numericProfiling.i2ShadowNanos,
                i3DecimalFastPathAccepted = numericProfiling.i3FastPathAccepted,
                i3DecimalFallbackInvalid = numericProfiling.i3FallbackInvalid,
                i3DecimalFallbackNonFixed = numericProfiling.i3FallbackNonFixed,
                i3DecimalFastPathCheckNanos = numericProfiling.i3FastPathCheckNanos,
                i41CertificatePredictedFixed = i41CertificatePredictedFixed,
                i41CertificateTruePositive = i41CertificateTruePositive,
                i41CertificateFalsePositive = i41CertificateFalsePositive,
                i41CertificateFalseNegative = i41CertificateFalseNegative,
                i41CertificateTrueNegative = i41CertificateTrueNegative,
                i41CertificateCheckNanos = i41CertificateCheckNanos,
                i41PotentialAvoidableOptimizerNanos = i41PotentialAvoidableOptimizerNanos,
                i41FalsePositiveOptimizerNanos = i41FalsePositiveOptimizerNanos,
                i41RejectedLexical = i41RejectedLexical,
                i41RejectedNumericSpelling = i41RejectedNumericSpelling,
                i41RejectedWhitespace = i41RejectedWhitespace,
                i41RejectedComplexCommandFamily = i41RejectedComplexCommandFamily,
                i41RejectedExplicitRepeat = i41RejectedExplicitRepeat,
                i42ProvenanceExcluded = i42ProvenanceExcluded,
                i42ProvenanceExcludedActuallyFixed = i42ProvenanceExcludedActuallyFixed,
                i42ProvenancePreventedFalsePositive = i42ProvenancePreventedFalsePositive,
                i42ProvenanceExcludedOptimizerNanos = i42ProvenanceExcludedOptimizerNanos,
                i42PreventedFalsePositiveOptimizerNanos =
                    i42PreventedFalsePositiveOptimizerNanos,
                i42PreventedChangedSyntaxNormalization =
                    i42PreventedChangedSyntaxNormalization,
                i42PreventedChangedGeometryCleanup =
                    i42PreventedChangedGeometryCleanup,
                i42PreventedChangedLocalShortening =
                    i42PreventedChangedLocalShortening,
                i42PreventedChangedGlobalCommand =
                    i42PreventedChangedGlobalCommand,
                i42PreventedChangedGlobalNumeric =
                    i42PreventedChangedGlobalNumeric,
                i42PreventedChangedOther = i42PreventedChangedOther,
                i43 = I43ComplexStats(
                    summary = I43ComplexSummary(
                        candidatesExamined = i43ComplexCandidatesExamined,
                        predictedFixed = i43ComplexPredictedFixed,
                        truePositive = i43ComplexTruePositive,
                        falsePositive = i43ComplexFalsePositive,
                        falseNegative = i43ComplexFalseNegative,
                        trueNegative = i43ComplexTrueNegative,
                        checkNanos = i43ComplexCheckNanos,
                        potentialAvoidableOptimizerNanos = i43ComplexPotentialAvoidableOptimizerNanos,
                        falsePositiveOptimizerNanos = i43ComplexFalsePositiveOptimizerNanos,
                        rejectedReflectiveShorthand = i43RejectedReflectiveShorthand,
                        rejectedNumericSpelling = i43RejectedNumericSpelling,
                        rejectedExplicitRepeat = i43RejectedExplicitRepeat,
                        rejectedProvenance = i43RejectedProvenance,
                    ),
                    families = I43ComplexFamilies(
                        cubicPredicted = i43CubicPredicted,
                        cubicTruePositive = i43CubicTruePositive,
                        cubicFalsePositive = i43CubicFalsePositive,
                        quadraticPredicted = i43QuadraticPredicted,
                        quadraticTruePositive = i43QuadraticTruePositive,
                        quadraticFalsePositive = i43QuadraticFalsePositive,
                        arcPredicted = i43ArcPredicted,
                        arcTruePositive = i43ArcTruePositive,
                        arcFalsePositive = i43ArcFalsePositive,
                        mixedPredicted = i43MixedPredicted,
                        mixedTruePositive = i43MixedTruePositive,
                        mixedFalsePositive = i43MixedFalsePositive,
                    ),
                    changes = I43ComplexChanges(
                        falsePositiveGeometryCleanup = i43FalsePositiveGeometryCleanup,
                        falsePositiveLocalShortening = i43FalsePositiveLocalShortening,
                        falsePositiveGlobalCommand = i43FalsePositiveGlobalCommand,
                        falsePositiveGlobalNumeric = i43FalsePositiveGlobalNumeric,
                        falsePositiveOther = i43FalsePositiveOther,
                    ),
                ),
                pathOptimizationCacheHits = pathCache.totalHits,
                pathOptimizationCacheMisses = pathCache.totalMisses,
                finalFormattingNanos = finalFormattingNanos,
                pathSyntaxCharactersSaved = pathSyntaxCharactersSaved,
                pruningCleanupCharactersSaved = pruningCleanupCharactersSaved,
                transformCharactersSaved = transformCharactersSaved,
                deduplicationCharactersSaved = deduplicationCharactersSaved,
                numericCleanupCharactersSaved = numericCleanupCharactersSaved,
                formattingCharactersSaved = formattingCharactersSaved,
                h21PathSyntaxCharacterDelta = h21PathSyntaxCharacterDelta,
                h22PathDataSyntaxCharacterDelta = h22PathDataSyntaxCharacterDelta,
                h22ColorNormalizationCharacterDelta = h22ColorNormalizationCharacterDelta,
                h23SyntaxNormalizationCharacterDelta = h23SyntaxNormalizationCharacterDelta,
                h23RedundantGeometryCharacterDelta = h23RedundantGeometryCharacterDelta,
                h23ArcCleanupCharacterDelta = h23ArcCleanupCharacterDelta,
                h23CurveSimplificationCharacterDelta = h23CurveSimplificationCharacterDelta,
                h23CollinearConsolidationCharacterDelta = h23CollinearConsolidationCharacterDelta,
                h23LocalCommandShorteningCharacterDelta = h23LocalCommandShorteningCharacterDelta,
                h23GlobalCommandMinimizationCharacterDelta = h23GlobalCommandMinimizationCharacterDelta,
                h23GlobalNumericSerializationCharacterDelta = h23GlobalNumericSerializationCharacterDelta,
                h24PathSyntaxCandidatesRejectedForSize = h24PathSyntaxCandidatesRejectedForSize,
                h24PathSyntaxCharactersAvoided = h24PathSyntaxCharactersAvoided,
                h25DecimalCandidatesRejectedForSize =
                    decimalCanonicalization.h25CandidatesRejectedForSize,
                h25DecimalCharactersAvoided =
                    decimalCanonicalization.h25CharactersAvoided,
                h21PruningCharacterDelta = h21PruningCharacterDelta,
                h21TransformCharacterDelta = h21TransformCharacterDelta,
                h21NearIntegerCharacterDelta = h21NearIntegerCharacterDelta,
                h21DedupMergeCharacterDelta = h21DedupMergeCharacterDelta,
                h21DecimalCanonicalizationCharacterDelta =
                    h21DecimalCanonicalizationCharacterDelta,
                h21FormattingCharacterDelta = h21FormattingCharacterDelta,
                xmlCharactersBefore = xml.length,
                xmlCharactersAfter = finalXml.length
            ),
            mergeSynthesizedPathData = pathMerging.synthesizedPathData
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



    private data class NumericCleanupProfiling(
        var decimalTokenizationNanos: Long = 0,
        var decimalRebuildNanos: Long = 0,
        var decimalReoptimizationNanos: Long = 0,
        var decimalValidationNanos: Long = 0,
        var decimalPathsExamined: Int = 0,
        var i2ShadowPathsCompared: Int = 0,
        var i2ShadowByteIdentical: Int = 0,
        var i2ShadowDifferent: Int = 0,
        var i2ShadowFastShorter: Int = 0,
        var i2ShadowReferenceShorter: Int = 0,
        var i2ShadowEqualLengthDifferent: Int = 0,
        var i2ShadowFastInvalid: Int = 0,
        var i2ShadowFastNonFixed: Int = 0,
        var i2ShadowCharacterDeltaVsReference: Int = 0,
        var i2ShadowNanos: Long = 0,
        var i3FastPathAccepted: Int = 0,
        var i3FallbackInvalid: Int = 0,
        var i3FallbackNonFixed: Int = 0,
        var i3FastPathCheckNanos: Long = 0,
    )

    private data class DecimalCanonicalizationResult(
        val xml: String,
        val changedValues: Int,
        val h25CandidatesRejectedForSize: Int = 0,
        val h25CharactersAvoided: Int = 0
    )

    /**
     * A11.2: gives final path coordinates a stable decimal representation.
     *
     * Values with more than six fractional digits are rounded to six places
     * using HALF_UP. The maximum coordinate adjustment is therefore 0.0000005.
     * Scientific notation is expanded to ordinary decimal notation, trailing
     * zeroes are removed, and negative zero becomes zero.
     *
     * This runs after A11.1 so values close to integers are snapped first.
     * It only examines android:pathData and preserves the original attribute
     * whenever tokenization is uncertain.
     */
    private fun canonicalizePathDecimalPrecision(
        xml: String
    ): DecimalCanonicalizationResult {
        var changedValues = 0

        val rewritten = pathDataAttributeRegex.replace(xml) { match ->
            val original = match.groupValues[1]
            val canonicalized = canonicalizePathDecimals(original)

            changedValues += canonicalized.changedValues
            "android:pathData=\"${canonicalized.pathData}\""
        }

        return DecimalCanonicalizationResult(
            xml = rewritten,
            changedValues = changedValues
        )
    }

    private fun canonicalizePathDecimals(
        pathData: String
    ): CanonicalizedPathData {
        return canonicalizePathDecimalsCached(
            pathData = pathData,
            pathCache = PathOptimizationCache(),
            validationPass = false
        )
    }

    private fun canonicalizePathDecimalPrecisionCached(
        xml: String,
        pathCache: PathOptimizationCache,
        validationPass: Boolean,
        profiling: NumericCleanupProfiling? = null
    ): DecimalCanonicalizationResult {
        var changedValues = 0
        var h25CandidatesRejectedForSize = 0
        var h25CharactersAvoided = 0

        val rewritten = pathDataAttributeRegex.replace(xml) { match ->
            val original = match.groupValues[1]
            val canonicalized = canonicalizePathDecimalsCached(
                pathData = original,
                pathCache = pathCache,
                validationPass = validationPass,
                profiling = profiling
            )

            // Production size policy: decimal canonicalization is a candidate spelling.
            // Accept it only when it is no longer than the incoming pathData at
            // this stage. Equal-length canonical output is retained.
            val accepted = canonicalized.pathData.length <= original.length
            val selectedPathData = if (accepted) {
                changedValues += canonicalized.changedValues
                canonicalized.pathData
            } else {
                h25CandidatesRejectedForSize++
                h25CharactersAvoided +=
                    canonicalized.pathData.length - original.length
                original
            }

            "android:pathData=\"$selectedPathData\""
        }

        return DecimalCanonicalizationResult(
            xml = rewritten,
            changedValues = changedValues,
            h25CandidatesRejectedForSize = h25CandidatesRejectedForSize,
            h25CharactersAvoided = h25CharactersAvoided
        )
    }

    private data class CanonicalizedPathData(
        val pathData: String,
        val changedValues: Int,
        val i2FastPathData: String? = null,
        val i2FastValid: Boolean = true,
        val i2FastFixed: Boolean = true,
        val i2ShadowNanos: Long = 0    )

    private fun i2CanonicalizeDecimalTokensOnly(pathData: String): String? {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) return pathData

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return null
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return null
        }

        val rebuilt = StringBuilder(pathData.length)
        var lastEnd = 0
        for (match in matches) {
            rebuilt.append(pathData, lastEnd, match.range.first)
            val token = match.value
            if (isCommand(token)) {
                rebuilt.append(token)
            } else {
                val value = token.toBigDecimalOrNull() ?: return null
                val canonicalValue =
                    if (value.scale().coerceAtLeast(0) > MAX_PATH_DECIMAL_PLACES) {
                        value.setScale(MAX_PATH_DECIMAL_PLACES, RoundingMode.HALF_UP)
                    } else {
                        value
                    }
                rebuilt.append(formatPathNumber(canonicalValue))
            }
            lastEnd = match.range.last + 1
        }
        rebuilt.append(pathData, lastEnd, pathData.length)
        return rebuilt.toString()
    }

    private fun canonicalizePathDecimalsCached(
        pathData: String,
        pathCache: PathOptimizationCache,
        validationPass: Boolean,
        profiling: NumericCleanupProfiling? = null
    ): CanonicalizedPathData {
        profiling?.decimalPathsExamined = (profiling?.decimalPathsExamined ?: 0) + 1
        val tokenizationStart = System.nanoTime()
        val matches = tokenRegex.findAll(pathData).toList()
        profiling?.let { it.decimalTokenizationNanos += System.nanoTime() - tokenizationStart }
        if (matches.isEmpty()) return CanonicalizedPathData(pathData, 0)

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return CanonicalizedPathData(pathData, 0)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return CanonicalizedPathData(pathData, 0)
        }

        val rebuildStart = System.nanoTime()
        val rebuilt = StringBuilder(pathData.length)
        var lastEnd = 0
        var changedCount = 0

        for (match in matches) {
            rebuilt.append(pathData, lastEnd, match.range.first)
            val token = match.value

            if (isCommand(token)) {
                rebuilt.append(token)
            } else {
                val value = token.toBigDecimalOrNull()
                    ?: return CanonicalizedPathData(pathData, 0)

                val canonicalValue =
                    if (value.scale().coerceAtLeast(0) > MAX_PATH_DECIMAL_PLACES) {
                        value.setScale(
                            MAX_PATH_DECIMAL_PLACES,
                            RoundingMode.HALF_UP
                        )
                    } else {
                        value
                    }

                val canonical = formatPathNumber(canonicalValue)
                rebuilt.append(canonical)

                // Compare against the actual token spelling, not another
                // normalized BigDecimal representation. This catches long
                // fractional values, scientific notation, trailing zeroes,
                // leading plus signs, and negative zero reliably.
                if (canonical != token) {
                    changedCount++
                }
            }

            lastEnd = match.range.last + 1
        }
        rebuilt.append(pathData, lastEnd, pathData.length)

        val rebuiltPathData = rebuilt.toString()
        profiling?.let { it.decimalRebuildNanos += System.nanoTime() - rebuildStart }
        if (changedCount == 0 && rebuiltPathData == pathData) {
            return CanonicalizedPathData(pathData, 0)
        }

        // Production fast path: decimal-only canonicalization may bypass the
        // nested full PathData optimizer only when exact cheap checks prove the
        // rebuilt spelling is valid and already fixed under the decimal-only
        // canonicalizer. Otherwise fail closed to the established reference
        // route below.
        val fastPathCheckStart = System.nanoTime()
        val fastValid = parseNormalizedSegments(rebuiltPathData) != null
        val fastFixed = if (fastValid) {
            i2CanonicalizeDecimalTokensOnly(rebuiltPathData) == rebuiltPathData
        } else {
            false
        }
        profiling?.let {
            it.i3FastPathCheckNanos += System.nanoTime() - fastPathCheckStart
        }

        if (fastValid && fastFixed) {
            profiling?.i3FastPathAccepted =
                (profiling?.i3FastPathAccepted ?: 0) + 1
            return CanonicalizedPathData(
                pathData = rebuiltPathData,
                changedValues = changedCount
            )
        }

        profiling?.let {
            if (!fastValid) {
                it.i3FallbackInvalid++
            } else {
                it.i3FallbackNonFixed++
            }
        }

        // Fail-closed fallback: preserve the established nested full PathData
        // re-optimization whenever the fast path cannot be proven safe/fixed.
        val reoptimizationStart = System.nanoTime()
        val optimized = optimizePathDataCached(
            pathData = rebuiltPathData,
            cache = pathCache,
            validationPass = validationPass
        ).pathData
        profiling?.let {
            it.decimalReoptimizationNanos += System.nanoTime() - reoptimizationStart
        }
        val validationStart = System.nanoTime()
        val valid = parseNormalizedSegments(optimized) != null
        profiling?.let {
            it.decimalValidationNanos += System.nanoTime() - validationStart
        }
        return if (valid) {
            CanonicalizedPathData(
                pathData = optimized,
                changedValues = changedCount
            )
        } else {
            CanonicalizedPathData(
                pathData = pathData,
                changedValues = 0
            )
        }
    }

    private data class NearIntegerSnappingResult(
        val xml: String,
        val snappedValues: Int
    )

    /**
     * A11.1: removes floating-point noise from final path coordinates.
     *
     * Non-zero integers use a conservative 0.0001 tolerance. Values near zero
     * use a much tighter 0.000001 tolerance so legitimate tiny coordinates such
     * as 0.00005 are not erased.
     *
     * This pass runs after transform baking and before duplicate removal/path
     * merging. It touches android:pathData only and preserves the original path
     * unchanged whenever tokenization is uncertain.
     */
    private fun snapNearIntegerPathValues(xml: String): NearIntegerSnappingResult {
        var snappedValues = 0

        val rewritten = pathDataAttributeRegex.replace(xml) { match ->
            val original = match.groupValues[1]
            val snapped = snapNearIntegerPathData(original)

            snappedValues += snapped.snappedValues
            "android:pathData=\"${snapped.pathData}\""
        }

        return NearIntegerSnappingResult(
            xml = rewritten,
            snappedValues = snappedValues
        )
    }

    private data class SnappedPathData(
        val pathData: String,
        val snappedValues: Int
    )

    private fun snapNearIntegerPathData(pathData: String): SnappedPathData {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) return SnappedPathData(pathData, 0)

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return SnappedPathData(pathData, 0)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return SnappedPathData(pathData, 0)
        }

        val nonZeroIntegerTolerance = BigDecimal("0.0001")
        val zeroTolerance = BigDecimal("0.000001")
        val rebuilt = StringBuilder(pathData.length)
        var lastEnd = 0
        var snappedCount = 0

        for (match in matches) {
            rebuilt.append(pathData, lastEnd, match.range.first)
            val token = match.value

            if (isCommand(token)) {
                rebuilt.append(token)
            } else {
                val value = token.toBigDecimalOrNull()
                if (value == null) {
                    return SnappedPathData(pathData, 0)
                }

                val nearestInteger = value.setScale(0, RoundingMode.HALF_UP)
                val distance = value.subtract(nearestInteger).abs()
                val tolerance = if (nearestInteger.compareTo(BigDecimal.ZERO) == 0) {
                    zeroTolerance
                } else {
                    nonZeroIntegerTolerance
                }

                if (distance.compareTo(tolerance) <= 0) {
                    val canonical = formatBigDecimal(nearestInteger)
                    val normalizedOriginal = normalizeNumber(token)
                    rebuilt.append(canonical)
                    if (canonical != normalizedOriginal) snappedCount++
                } else {
                    rebuilt.append(token)
                }
            }

            lastEnd = match.range.last + 1
        }
        rebuilt.append(pathData, lastEnd, pathData.length)

        if (snappedCount == 0) return SnappedPathData(pathData, 0)

        // Re-run the existing lossless syntax optimizer so snapped values receive
        // the same compact separators and command selection as every other path.
        val optimized = optimizePathData(rebuilt.toString()).pathData
        return if (parseNormalizedSegments(optimized) != null) {
            SnappedPathData(optimized, snappedCount)
        } else {
            SnappedPathData(pathData, 0)
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

        val withoutOrphanedComments =
            removeOrphanedConversionComments(compacted.joinToString("\n"))
        return prettyPrintVectorXml(withoutOrphanedComments)
    }




    /**
     * A10.3: presentation-only VectorDrawable pretty printer.
     *
     * Every non-blank source line is emitted exactly once. The only line merge
     * performed is attaching a standalone ">" to the preceding final attribute.
     * The pass never matches or replaces complete element ranges, so it cannot
     * remove paths, groups, clip paths, gradients, or comments.
     */
    private fun prettyPrintVectorXml(xml: String): String {
        val source = splitAdjacentXmlTags(xml)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()

        val output = mutableListOf<String>()
        var depth = 0
        var openTagDepth = 0
        var openTagName: String? = null
        var insideOpeningTag = false
        var pendingBlankAfterGroupOpen = false

        fun indent(level: Int): String = "    ".repeat(level.coerceAtLeast(0))

        fun appendBlankIfNeeded() {
            if (output.isNotEmpty() && output.last().isNotBlank()) {
                output += ""
            }
        }

        fun completeOpeningTag(
            tagName: String?,
            selfClosing: Boolean
        ) {
            insideOpeningTag = false
            openTagName = null
            if (!selfClosing && tagName != null) {
                depth = openTagDepth + 1
                pendingBlankAfterGroupOpen =
                    tagName.equals("group", ignoreCase = true)
            }
        }

        for (rawLine in source) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            if (insideOpeningTag) {
                when {
                    trimmed == ">" -> {
                        if (output.isNotEmpty()) {
                            output[output.lastIndex] =
                                output.last().trimEnd() + ">"
                        } else {
                            output += indent(openTagDepth) + ">"
                        }
                        completeOpeningTag(openTagName, selfClosing = false)
                    }

                    trimmed == "/>" -> {
                        output += indent(openTagDepth) + "/>"
                        completeOpeningTag(openTagName, selfClosing = true)
                    }

                    else -> {
                        output += indent(openTagDepth + 1) + trimmed
                        if (trimmed.endsWith("/>")) {
                            completeOpeningTag(openTagName, selfClosing = true)
                        } else if (trimmed.endsWith(">")) {
                            completeOpeningTag(openTagName, selfClosing = false)
                        }
                    }
                }
                continue
            }

            if (trimmed.startsWith("</")) {
                depth = (depth - 1).coerceAtLeast(0)
                while (output.lastOrNull()?.isBlank() == true) {
                    output.removeAt(output.lastIndex)
                }
                output += indent(depth) + trimmed
                pendingBlankAfterGroupOpen = false
                continue
            }

            val isCommentOrDeclaration =
                trimmed.startsWith("<!--") ||
                    trimmed.startsWith("<?") ||
                    trimmed.startsWith("<!")

            if (pendingBlankAfterGroupOpen && !isCommentOrDeclaration) {
                appendBlankIfNeeded()
                pendingBlankAfterGroupOpen = false
            } else if (pendingBlankAfterGroupOpen && trimmed.startsWith("<!--")) {
                appendBlankIfNeeded()
                pendingBlankAfterGroupOpen = false
            }

            if (!trimmed.startsWith("<") || isCommentOrDeclaration) {
                output += indent(depth) + trimmed
                continue
            }

            val tagName = Regex("""^<([A-Za-z_][A-Za-z0-9_.:-]*)""")
                .find(trimmed)
                ?.groupValues
                ?.getOrNull(1)

            if (tagName == null) {
                output += indent(depth) + trimmed
                continue
            }

            val completesOnThisLine = trimmed.endsWith(">")
            val selfClosing = trimmed.endsWith("/>")

            output += indent(depth) + trimmed

            if (completesOnThisLine) {
                if (!selfClosing) {
                    depth++
                    pendingBlankAfterGroupOpen =
                        tagName.equals("group", ignoreCase = true)
                }
            } else {
                insideOpeningTag = true
                openTagDepth = depth
                openTagName = tagName
            }
        }

        // Keep one blank line between top-level drawable siblings while never
        // adding padding immediately before the root closing tag.
        val spaced = mutableListOf<String>()
        for (line in output) {
            val trimmed = line.trim()
            val indentLevel = line.takeWhile { it == ' ' }.length / 4
            val beginsTopLevelComment =
                indentLevel == 1 && trimmed.startsWith("<!--")
            val beginsTopLevelElement =
                indentLevel == 1 &&
                    (trimmed.startsWith("<group") ||
                        trimmed.startsWith("<path") ||
                        trimmed.startsWith("<clip-path"))

            val previousTrimmed = spaced.lastOrNull()?.trim().orEmpty()
            val previousWasComment = previousTrimmed.startsWith("<!--")

            // A top-level conversion comment belongs to the drawable that
            // immediately follows it. Add separation before the comment, but
            // never insert a blank line between that comment and its element.
            if ((beginsTopLevelComment || beginsTopLevelElement) &&
                !previousWasComment &&
                spaced.isNotEmpty() &&
                spaced.last().isNotBlank() &&
                !previousTrimmed.endsWith("<vector")
            ) {
                spaced += ""
            }
            if (trimmed.startsWith("</vector")) {
                while (spaced.lastOrNull()?.isBlank() == true) {
                    spaced.removeAt(spaced.lastIndex)
                }
            }
            spaced += line
        }

        return spaced.joinToString("\n").trimEnd() + "\n"
    }

    /**
     * Separates XML tags that structural optimizer passes may have joined onto
     * the same physical line, for example `><group` or `</group></group>`.
     *
     * VectorDrawable output contains no meaningful text nodes between drawable
     * elements, so inserting a newline at a direct `><` boundary is purely
     * presentational. Doing this before indentation lets the pretty printer
     * track each nested element independently.
     */
    private fun splitAdjacentXmlTags(xml: String): String =
        xml.replace(Regex(""">(?=<)"""), ">\n")

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



    private data class TransformCanonicalizationResult(
        val xml: String,
        val canonicalizedAttributes: Int,
        val zeroPivotsRemoved: Int,
        val reorderedGroups: Int
    )

    /**
     * C4: canonicalizes the transform attributes left on generated groups.
     *
     * This pass is semantics-neutral:
     * - numeric spellings are normalized (for example 1.000 -> 1 and -0 -> 0);
     * - explicit zero pivots are removed because VectorDrawable defaults them to 0;
     * - transform attributes are emitted in one stable order;
     * - unknown/non-transform attributes retain their original relative order.
     *
     * Resource-valued attributes are left untouched.
     */
    private fun canonicalizeGroupTransformAttributes(
        xml: String
    ): TransformCanonicalizationResult {
        val groupOpeningRegex = Regex(
            """<group\b(?:\"[^\"]*\"|'[^']*'|[^>])*?>""",
            RegexOption.IGNORE_CASE
        )
        val canonicalOrder = listOf(
            "scaleX", "scaleY",
            "pivotX", "pivotY",
            "rotation",
            "translateX", "translateY"
        )
        val transformNames = canonicalOrder.map { it.lowercase() }.toSet()

        var canonicalized = 0
        var zeroPivotsRemoved = 0
        var reorderedGroups = 0

        val updated = groupOpeningRegex.replace(xml) { match ->
            val tag = match.value
            val attrs = androidAttributeRegex.findAll(tag).toList()
            if (attrs.isEmpty()) return@replace tag

            val transformAttrs = attrs.filter {
                it.groupValues[1].lowercase() in transformNames
            }
            if (transformAttrs.isEmpty()) return@replace tag

            data class CanonicalAttr(
                val name: String,
                val value: String,
                val originalIndex: Int
            )

            val retainedTransforms = mutableListOf<CanonicalAttr>()
            transformAttrs.forEachIndexed { index, attr ->
                val rawName = attr.groupValues[1]
                val canonicalName = canonicalOrder.first {
                    it.equals(rawName, ignoreCase = true)
                }
                val rawValue = attr.groupValues[3].trim()
                val numeric = rawValue.toBigDecimalOrNull()

                if ((canonicalName == "pivotX" || canonicalName == "pivotY") &&
                    numeric != null &&
                    numeric.compareTo(BigDecimal.ZERO) == 0
                ) {
                    zeroPivotsRemoved++
                    return@forEachIndexed
                }

                val canonicalValue = if (numeric != null) {
                    val normalized = if (numeric.compareTo(BigDecimal.ZERO) == 0) {
                        BigDecimal.ZERO
                    } else {
                        numeric
                    }
                    formatBigDecimal(normalized)
                } else {
                    rawValue
                }

                if (canonicalName != rawName || canonicalValue != rawValue) {
                    canonicalized++
                }
                retainedTransforms += CanonicalAttr(
                    canonicalName,
                    canonicalValue,
                    index
                )
            }

            val sortedTransforms = retainedTransforms.sortedBy {
                canonicalOrder.indexOf(it.name)
            }
            if (retainedTransforms.map { it.name } != sortedTransforms.map { it.name }) {
                reorderedGroups++
            }

            // Remove only transform attributes from the opening tag, keeping
            // unknown attributes and their order intact.
            var baseTag = tag
            transformAttrs.sortedByDescending { it.range.first }.forEach { attr ->
                var start = attr.range.first
                val end = attr.range.last + 1
                while (start > 0 && baseTag[start - 1].isWhitespace()) start--
                baseTag = baseTag.removeRange(start, end)
            }
            baseTag = baseTag
                .replace(Regex("""\s+>"""), ">")
                .replace(Regex("""<group\s*>""", RegexOption.IGNORE_CASE), "<group>")

            if (sortedTransforms.isEmpty()) {
                return@replace baseTag
            }

            val closeIndex = baseTag.lastIndexOf('>')
            if (closeIndex < 0) return@replace tag

            val prefix = baseTag.substring(0, closeIndex).trimEnd()
            val suffix = baseTag.substring(closeIndex)
            buildString {
                append(prefix)
                sortedTransforms.forEach { attr ->
                    append("\n    android:")
                    append(attr.name)
                    append("=\"")
                    append(attr.value)
                    append('"')
                }
                append(suffix)
            }
        }

        return TransformCanonicalizationResult(
            xml = updated,
            canonicalizedAttributes = canonicalized,
            zeroPivotsRemoved = zeroPivotsRemoved,
            reorderedGroups = reorderedGroups
        )
    }

    private data class IdentityGroupCleanupResult(
        val xml: String,
        val removedAttributes: Int
    )

    /**
     * Removes identity transform attributes from VectorDrawable groups.
     *
     * Safe identity values are:
     * - translateX/translateY = 0
     * - scaleX/scaleY = 1
     * - rotation = 0
     * - pivotX/pivotY when the group has no effective scale or rotation
     *
     * Unknown attributes and non-numeric resource values are preserved. Once an
     * identity-only group becomes attribute-free, the normal redundant-group pass
     * may safely promote its children, subject to the existing clip-path guard.
     */
    private fun removeIdentityGroupTransformAttributes(xml: String): IdentityGroupCleanupResult {
        val groupOpeningRegex = Regex(
            """<group\b(?:\"[^\"]*\"|'[^']*'|[^>])*?>""",
            RegexOption.IGNORE_CASE
        )
        var removed = 0

        val cleaned = groupOpeningRegex.replace(xml) { match ->
            val tag = match.value
            val attrs = androidAttributeRegex.findAll(tag).toList()
            if (attrs.isEmpty()) return@replace tag

            fun numeric(name: String, default: BigDecimal): BigDecimal? {
                val attr = attrs.firstOrNull { it.groupValues[1].equals(name, true) }
                    ?: return default
                return attr.groupValues[3].trim().toBigDecimalOrNull()
            }

            val scaleX = numeric("scaleX", BigDecimal.ONE)
            val scaleY = numeric("scaleY", BigDecimal.ONE)
            val rotation = numeric("rotation", BigDecimal.ZERO)
            val pivotIsIrrelevant =
                scaleX != null && scaleY != null && rotation != null &&
                    scaleX.compareTo(BigDecimal.ONE) == 0 &&
                    scaleY.compareTo(BigDecimal.ONE) == 0 &&
                    rotation.compareTo(BigDecimal.ZERO) == 0

            val removable = attrs.filter { attr ->
                val name = attr.groupValues[1].lowercase()
                val value = attr.groupValues[3].trim().toBigDecimalOrNull()
                    ?: return@filter false
                when (name) {
                    "translatex", "translatey", "rotation" ->
                        value.compareTo(BigDecimal.ZERO) == 0
                    "scalex", "scaley" ->
                        value.compareTo(BigDecimal.ONE) == 0
                    "pivotx", "pivoty" -> pivotIsIrrelevant
                    else -> false
                }
            }

            if (removable.isEmpty()) return@replace tag

            val out = StringBuilder(tag)
            for (attr in removable.sortedByDescending { it.range.first }) {
                var start = attr.range.first
                var end = attr.range.last + 1
                while (start > 0 && out[start - 1].isWhitespace()) start--
                out.delete(start, end)
                removed++
            }

            out.toString()
                .replace(Regex("""\s+>"""), ">")
                .replace(Regex("""<group\s*>""", RegexOption.IGNORE_CASE), "<group>")
        }

        return IdentityGroupCleanupResult(cleaned, removed)
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


    private data class TransformCompositionResult(
        val xml: String,
        val composedGroups: Int
    )

    /**
     * A10.1: folds a translation-only parent group into its single direct child
     * group.
     *
     * This is exact for every child transform because VectorDrawable parent
     * translation is applied after the child's local transform. Adding the parent
     * translation to the child's translateX/translateY therefore preserves the
     * complete matrix, including the child's scale, rotation, pivot, and clip.
     *
     * The parent is eligible only when:
     * - its only effective attributes are numeric translateX/translateY;
     * - its body contains comments/whitespace plus exactly one direct child group;
     * - it owns no clip-path or drawable sibling.
     */
    private fun composeNestedParentTranslationGroups(xml: String): TransformCompositionResult {
        var current = xml
        var composed = 0

        while (true) {
            val groups = findMatchedGroups(current)
            val candidate = groups
                .sortedBy { it.end - it.start }
                .firstNotNullOfOrNull { outer ->
                    val outerOpening = current.substring(outer.start, outer.openingEnd)
                    val translation = translationForGroup(outerOpening)
                        ?: return@firstNotNullOfOrNull null

                    val directChildren = directChildGroups(outer, groups)
                    if (directChildren.size != 1) return@firstNotNullOfOrNull null

                    val child = directChildren.single()
                    val beforeChild = current.substring(outer.openingEnd, child.start)
                    val afterChild = current.substring(child.end, outer.closingStart)
                    if (!commentsAndWhitespaceOnly(beforeChild) ||
                        !commentsAndWhitespaceOnly(afterChild)
                    ) {
                        return@firstNotNullOfOrNull null
                    }

                    val childOpening = current.substring(child.start, child.openingEnd)
                    val updatedOpening = addTranslationToGroupOpening(
                        childOpening,
                        translation.first,
                        translation.second
                    ) ?: return@firstNotNullOfOrNull null

                    Triple(outer, child, updatedOpening)
                } ?: break

            val (outer, child, updatedChildOpening) = candidate
            val beforeChild = current.substring(outer.openingEnd, child.start)
            val childBodyAndClosing = current.substring(child.openingEnd, child.end)
            val afterChild = current.substring(child.end, outer.closingStart)
            val replacement = buildString {
                // C3: comments belong to the wrapper scope and must survive when
                // the wrapper is consolidated into its only child.
                append(beforeChild)
                append(updatedChildOpening)
                append(childBodyAndClosing)
                append(afterChild)
            }

            current = buildString(current.length) {
                append(current, 0, outer.start)
                append(replacement)
                append(current, outer.end, current.length)
            }
            composed++
        }

        return TransformCompositionResult(current, composed)
    }


    private data class RotationOnlyTransform(
        val rotation: BigDecimal,
        val pivotX: BigDecimal,
        val pivotY: BigDecimal,
        val translateX: BigDecimal,
        val translateY: BigDecimal
    )

    private data class ScaleOnlyTransform(
        val scaleX: BigDecimal,
        val scaleY: BigDecimal,
        val pivotX: BigDecimal,
        val pivotY: BigDecimal
    )

    /**
     * A10.2: composes nested transform groups when both groups represent the
     * same kind of transform around the same pivot.
     *
     * Supported exact combinations:
     * - rotation/translation parent + rotation/translation child: rigid
     *   transforms are composed and angles are added;
     * - scale-only parent + scale-only child: X/Y factors are multiplied.
     *
     * Both groups must contain only their recognized numeric transform
     * attributes, and the parent must contain comments/whitespace plus exactly
     * one direct child group. Translation is deliberately excluded here because
     * A10.1 already handles translation-only parents and mixed transform order
     * cannot in general be represented by simply combining attribute values.
     */
    private fun composeNestedCompatibleSamePivotGroups(
        xml: String
    ): TransformCompositionResult {
        var current = xml
        var composed = 0

        while (true) {
            val groups = findMatchedGroups(current)
            val candidate = groups
                .sortedBy { it.end - it.start }
                .firstNotNullOfOrNull { outer ->
                    val directChildren = directChildGroups(outer, groups)
                    if (directChildren.size != 1) return@firstNotNullOfOrNull null

                    val child = directChildren.single()
                    val beforeChild = current.substring(outer.openingEnd, child.start)
                    val afterChild = current.substring(child.end, outer.closingStart)
                    if (!commentsAndWhitespaceOnly(beforeChild) ||
                        !commentsAndWhitespaceOnly(afterChild)
                    ) {
                        return@firstNotNullOfOrNull null
                    }

                    val outerOpening = current.substring(outer.start, outer.openingEnd)
                    val childOpening = current.substring(child.start, child.openingEnd)

                    val outerRotation = rotationOnlyForGroup(outerOpening)
                    val childRotation = rotationOnlyForGroup(childOpening)
                    if (outerRotation != null && childRotation != null) {
                        val updated = composeRotationGroupOpenings(
                            childOpening = childOpening,
                            outer = outerRotation,
                            child = childRotation
                        )
                        return@firstNotNullOfOrNull Triple(outer, child, updated)
                    }

                    val outerScale = scaleOnlyForGroup(outerOpening)
                    val childScale = scaleOnlyForGroup(childOpening)
                    if (outerScale != null &&
                        childScale != null &&
                        samePivot(outerScale, childScale)
                    ) {
                        var updated = replaceNumericGroupAttribute(
                            childOpening,
                            "scaleX",
                            outerScale.scaleX.multiply(childScale.scaleX)
                        )
                        updated = replaceNumericGroupAttribute(
                            updated,
                            "scaleY",
                            outerScale.scaleY.multiply(childScale.scaleY)
                        )
                        return@firstNotNullOfOrNull Triple(outer, child, updated)
                    }

                    null
                } ?: break

            val (outer, child, updatedChildOpening) = candidate
            val beforeChild = current.substring(outer.openingEnd, child.start)
            val childBodyAndClosing = current.substring(child.openingEnd, child.end)
            val afterChild = current.substring(child.end, outer.closingStart)
            val replacement = buildString {
                // C3: preserve wrapper comments on both sides of the child.
                append(beforeChild)
                append(updatedChildOpening)
                append(childBodyAndClosing)
                append(afterChild)
            }

            current = buildString(current.length) {
                append(current, 0, outer.start)
                append(replacement)
                append(current, outer.end, current.length)
            }
            composed++
        }

        return TransformCompositionResult(current, composed)
    }

    private fun directChildGroups(
        outer: GroupRange,
        groups: List<GroupRange>
    ): List<GroupRange> =
        groups.filter { child ->
            child.start >= outer.openingEnd &&
                child.end <= outer.closingStart &&
                groups.none { between ->
                    between.start >= outer.openingEnd &&
                        between.end <= outer.closingStart &&
                        between.start < child.start &&
                        between.end > child.end
                }
        }

    private fun rotationOnlyForGroup(openingTag: String): RotationOnlyTransform? {
        val attrs = recognizedNumericGroupAttributes(
            openingTag,
            setOf("rotation", "pivotx", "pivoty", "translatex", "translatey")
        ) ?: return null

        val rotation = attrs["rotation"] ?: BigDecimal.ZERO
        if (rotation.compareTo(BigDecimal.ZERO) == 0) return null

        return RotationOnlyTransform(
            rotation = rotation,
            pivotX = attrs["pivotx"] ?: BigDecimal.ZERO,
            pivotY = attrs["pivoty"] ?: BigDecimal.ZERO,
            translateX = attrs["translatex"] ?: BigDecimal.ZERO,
            translateY = attrs["translatey"] ?: BigDecimal.ZERO
        )
    }

    private fun scaleOnlyForGroup(openingTag: String): ScaleOnlyTransform? {
        val attrs = recognizedNumericGroupAttributes(
            openingTag,
            setOf("scalex", "scaley", "pivotx", "pivoty")
        ) ?: return null

        val scaleX = attrs["scalex"] ?: BigDecimal.ONE
        val scaleY = attrs["scaley"] ?: BigDecimal.ONE
        if (scaleX.compareTo(BigDecimal.ONE) == 0 &&
            scaleY.compareTo(BigDecimal.ONE) == 0
        ) {
            return null
        }

        return ScaleOnlyTransform(
            scaleX = scaleX,
            scaleY = scaleY,
            pivotX = attrs["pivotx"] ?: BigDecimal.ZERO,
            pivotY = attrs["pivoty"] ?: BigDecimal.ZERO
        )
    }

    private fun recognizedNumericGroupAttributes(
        openingTag: String,
        allowedNames: Set<String>
    ): Map<String, BigDecimal>? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) ||
            !trimmed.endsWith('>')
        ) {
            return null
        }

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it !in allowedNames } || names.distinct().size != names.size) {
            return null
        }

        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val result = linkedMapOf<String, BigDecimal>()
        for (attribute in attributes) {
            val name = attribute.groupValues[1].lowercase()
            val value = attribute.groupValues[3].trim().toBigDecimalOrNull()
                ?: return null
            result[name] = value
        }
        return result
    }

    /**
     * Composes two emitted VectorDrawable rigid transforms exactly in emitted
     * transform order. SVG rotate(cx, cy) is commonly converted to a group with
     * pivot 0 plus a compensating translation, so checking only the emitted
     * pivot attributes misses rotations that originally shared a center.
     *
     * Each group represents T(translate) * T(pivot) * R * T(-pivot). We convert
     * both to an origin-pivot rotation plus effective translation, compose the
     * two rigid transforms, and emit the result on the child group.
     */
    private fun composeRotationGroupOpenings(
        childOpening: String,
        outer: RotationOnlyTransform,
        child: RotationOnlyTransform
    ): String {
        val outerEffective = effectiveRotationTranslation(outer)
        val childEffective = effectiveRotationTranslation(child)

        val radians = Math.toRadians(outer.rotation.toDouble())
        val cosine = cos(radians)
        val sine = sin(radians)
        val rotatedChildX =
            cosine * childEffective.first - sine * childEffective.second
        val rotatedChildY =
            sine * childEffective.first + cosine * childEffective.second

        val combinedX = outerEffective.first + rotatedChildX
        val combinedY = outerEffective.second + rotatedChildY
        val combinedRotation = outer.rotation.add(child.rotation)

        var updated = childOpening
        updated = setOrInsertGroupAttribute(updated, "rotation", combinedRotation)
        updated = setOrInsertGroupAttribute(updated, "pivotX", BigDecimal.ZERO)
        updated = setOrInsertGroupAttribute(updated, "pivotY", BigDecimal.ZERO)
        updated = setOrInsertGroupAttribute(updated, "translateX", decimalFromDouble(combinedX))
        updated = setOrInsertGroupAttribute(updated, "translateY", decimalFromDouble(combinedY))
        return updated
    }

    private fun effectiveRotationTranslation(
        transform: RotationOnlyTransform
    ): Pair<Double, Double> {
        val radians = Math.toRadians(transform.rotation.toDouble())
        val cosine = cos(radians)
        val sine = sin(radians)
        val pivotX = transform.pivotX.toDouble()
        val pivotY = transform.pivotY.toDouble()

        val rotatedPivotX = cosine * pivotX - sine * pivotY
        val rotatedPivotY = sine * pivotX + cosine * pivotY
        return Pair(
            transform.translateX.toDouble() + pivotX - rotatedPivotX,
            transform.translateY.toDouble() + pivotY - rotatedPivotY
        )
    }

    private fun decimalFromDouble(value: Double): BigDecimal {
        val normalized = if (kotlin.math.abs(value) < 1e-10) 0.0 else value
        return BigDecimal.valueOf(normalized).setScale(6, java.math.RoundingMode.HALF_UP)
            .stripTrailingZeros()
    }

    private fun samePivot(
        first: ScaleOnlyTransform,
        second: ScaleOnlyTransform
    ): Boolean =
        first.pivotX.compareTo(second.pivotX) == 0 &&
            first.pivotY.compareTo(second.pivotY) == 0

    private fun replaceNumericGroupAttribute(
        openingTag: String,
        name: String,
        value: BigDecimal
    ): String =
        setOrInsertGroupAttribute(openingTag, name, value)

    private fun commentsAndWhitespaceOnly(text: String): Boolean =
        xmlCommentRegex.replace(text, "").isBlank()

    private fun addTranslationToGroupOpening(
        openingTag: String,
        addX: BigDecimal,
        addY: BigDecimal
    ): String? {
        if (!openingTag.trimStart().startsWith("<group", ignoreCase = true)) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        if (attributes.count { it.groupValues[1].equals("translateX", true) } > 1 ||
            attributes.count { it.groupValues[1].equals("translateY", true) } > 1
        ) return null

        val existingX = attributes
            .firstOrNull { it.groupValues[1].equals("translateX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val existingY = attributes
            .firstOrNull { it.groupValues[1].equals("translateY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        var updated = openingTag
        updated = setOrInsertGroupAttribute(
            updated,
            "translateX",
            existingX.add(addX)
        )
        updated = setOrInsertGroupAttribute(
            updated,
            "translateY",
            existingY.add(addY)
        )
        return updated
    }

    private fun setOrInsertGroupAttribute(
        openingTag: String,
        name: String,
        value: BigDecimal
    ): String {
        val existing = Regex(
            """(android:${Regex.escape(name)}\s*=\s*)(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val formatted = formatBigDecimal(value)
        val match = existing.find(openingTag)
        if (match != null) {
            val replacement =
                "${match.groupValues[1]}${match.groupValues[2]}$formatted${match.groupValues[2]}"
            return openingTag.replaceRange(match.range, replacement)
        }

        if (value.compareTo(BigDecimal.ZERO) == 0) return openingTag

        val closingIndex = openingTag.lastIndexOf('>')
        if (closingIndex < 0) return openingTag

        val beforeClosing = openingTag.substring(0, closingIndex)
        val contentEnd = beforeClosing.indexOfLast { !it.isWhitespace() } + 1
        val content = beforeClosing.substring(0, contentEnd)

        val attributeIndent = Regex("""\n([ \t]*)android:""")
            .find(openingTag)
            ?.groupValues
            ?.get(1)
            ?: "    "
        val closingIndent = Regex("""\n([ \t]*)>\s*$""")
            .find(openingTag)
            ?.groupValues
            ?.get(1)
            ?: ""

        return buildString(openingTag.length + name.length + formatted.length + 16) {
            append(content)
            append('\n')
            append(attributeIndent)
            append("android:")
            append(name)
            append("=\"")
            append(formatted)
            append('"')
            append('\n')
            append(closingIndent)
            append('>')
        }
    }

    private data class CommonTranslationFactoringResult(
        val xml: String,
        val factoredGroups: Int
    )

    /**
     * C2: factors an identical final translation out of consecutive sibling
     * groups and places it on one shared parent group.
     *
     * VectorDrawable applies a parent translation after every child transform,
     * so this rewrite is exact even when the children use different rotations,
     * scales, pivots, or clip paths. Child group boundaries remain intact until
     * the ordinary redundant-group cleanup decides they can safely disappear.
     *
     * The rewrite is accepted only when:
     * - at least two direct sibling groups form a consecutive run;
     * - only comments/whitespace separate the groups;
     * - every group has the same non-zero translateX/translateY pair;
     * - all group attributes are recognized numeric VectorDrawable transforms;
     * - and the rewritten XML is strictly shorter.
     */
    private fun factorCommonSiblingTranslations(
        xml: String
    ): CommonTranslationFactoringResult {
        var current = xml
        var factoredGroups = 0

        val allowed = setOf(
            "rotation", "pivotx", "pivoty",
            "scalex", "scaley", "translatex", "translatey"
        )

        while (true) {
            val groups = findMatchedGroups(current)
            if (groups.size < 2) break

            fun parentOf(range: GroupRange): GroupRange? = groups
                .asSequence()
                .filter { it.start < range.start && it.end > range.end }
                .minByOrNull { it.end - it.start }

            var chosen: Pair<List<GroupRange>, Pair<BigDecimal, BigDecimal>>? = null

            outer@ for (siblings in groups.groupBy { parentOf(it)?.start }.values) {
                val ordered = siblings.sortedBy { it.start }
                var start = 0
                while (start < ordered.size) {
                    val first = ordered[start]
                    val firstAttrs = recognizedNumericGroupAttributes(
                        current.substring(first.start, first.openingEnd), allowed
                    )
                    if (firstAttrs == null) {
                        start++
                        continue
                    }

                    val tx = firstAttrs["translatex"] ?: BigDecimal.ZERO
                    val ty = firstAttrs["translatey"] ?: BigDecimal.ZERO
                    if (tx.compareTo(BigDecimal.ZERO) == 0 &&
                        ty.compareTo(BigDecimal.ZERO) == 0
                    ) {
                        start++
                        continue
                    }

                    val run = mutableListOf(first)
                    var next = start + 1
                    while (next < ordered.size) {
                        val previous = run.last()
                        val candidate = ordered[next]
                        if (!commentsAndWhitespaceOnly(
                                current.substring(previous.end, candidate.start)
                            )
                        ) break

                        val attrs = recognizedNumericGroupAttributes(
                            current.substring(candidate.start, candidate.openingEnd), allowed
                        ) ?: break
                        val candidateX = attrs["translatex"] ?: BigDecimal.ZERO
                        val candidateY = attrs["translatey"] ?: BigDecimal.ZERO
                        if (candidateX.compareTo(tx) != 0 || candidateY.compareTo(ty) != 0) {
                            break
                        }
                        run += candidate
                        next++
                    }

                    if (run.size >= 2) {
                        chosen = run to (tx to ty)
                        break@outer
                    }
                    start = next.coerceAtLeast(start + 1)
                }
            }

            val selection = chosen ?: break
            val run = selection.first
            val (translateX, translateY) = selection.second
            val first = run.first()
            val last = run.last()

            val original = current.substring(first.start, last.end)
            val rewrittenChildren = buildString(original.length) {
                var cursor = first.start
                for (group in run) {
                    append(current, cursor, group.start)
                    val opening = current.substring(group.start, group.openingEnd)
                    append(removeGroupTransformAttributes(
                        opening, setOf("translatex", "translatey")
                    ))
                    append(current, group.openingEnd, group.end)
                    cursor = group.end
                }
                append(current, cursor, last.end)
            }

            val replacement = buildString(rewrittenChildren.length + 96) {
                append("<group")
                if (translateX.compareTo(BigDecimal.ZERO) != 0) {
                    append(" android:translateX=\"")
                    append(formatBigDecimal(translateX))
                    append('\"')
                }
                if (translateY.compareTo(BigDecimal.ZERO) != 0) {
                    append(" android:translateY=\"")
                    append(formatBigDecimal(translateY))
                    append('\"')
                }
                append('>')
                append(rewrittenChildren)
                append("</group>")
            }

            if (replacement.length >= original.length) break

            current = buildString(current.length - original.length + replacement.length) {
                append(current, 0, first.start)
                append(replacement)
                append(current, last.end, current.length)
            }
            factoredGroups += run.size
        }

        return CommonTranslationFactoringResult(current, factoredGroups)
    }

    private fun removeGroupTransformAttributes(
        openingTag: String,
        lowercaseNames: Set<String>
    ): String {
        var updated = openingTag
        val matches = androidAttributeRegex.findAll(openingTag)
            .filter { it.groupValues[1].lowercase() in lowercaseNames }
            .toList()
            .sortedByDescending { it.range.first }
        for (match in matches) {
            updated = updated.removeRange(match.range)
        }
        return updated
            .replace(Regex("""[ \t]{2,}"""), " ")
            .replace(Regex("""[ \t]+>"""), ">")
            .replace(Regex("""\n[ \t]*\n"""), "\n")
    }

    private data class AdjacentGroupCoalescingResult(
        val xml: String,
        val coalescedGroups: Int
    )

    /**
     * C1: merges adjacent sibling groups that have identical VectorDrawable
     * group state.
     *
     * This removes one complete group wrapper without baking geometry. The
     * merge is deliberately conservative:
     * - both groups must be direct siblings under the same parent;
     * - only comments and whitespace may occur between them;
     * - their complete Android group-attribute sets must be identical; and
     * - the left group may not contain a clip-path, because merging would let
     *   that clip affect content that originally lived in the right group.
     *
     * A clip-path in the right group is safe: it still begins at the same point
     * in drawing order and the merged group ends where the right group ended.
     */
    private fun coalesceIdenticalAdjacentGroups(
        xml: String
    ): AdjacentGroupCoalescingResult {
        var current = xml
        var coalesced = 0

        while (true) {
            val groups = findMatchedGroups(current)
            if (groups.size < 2) break

            fun parentOf(range: GroupRange): GroupRange? = groups
                .asSequence()
                .filter { candidate ->
                    candidate.start < range.start && candidate.end > range.end
                }
                .minByOrNull { candidate -> candidate.end - candidate.start }

            val byParent = groups
                .groupBy { range -> parentOf(range)?.start }
                .values

            var selected: Pair<GroupRange, GroupRange>? = null

            outer@ for (siblings in byParent) {
                val ordered = siblings.sortedBy { it.start }
                for (index in 0 until ordered.lastIndex) {
                    val left = ordered[index]
                    val right = ordered[index + 1]

                    val between = current.substring(left.end, right.start)
                    if (!commentsAndWhitespaceOnly(between)) continue

                    val leftOpening = current.substring(left.start, left.openingEnd)
                    val rightOpening = current.substring(right.start, right.openingEnd)
                    val leftSignature = groupAttributeSignature(leftOpening) ?: continue
                    val rightSignature = groupAttributeSignature(rightOpening) ?: continue
                    if (leftSignature != rightSignature) continue

                    val leftBody = current.substring(left.openingEnd, left.closingStart)
                    if (Regex("""<clip-path\b""", RegexOption.IGNORE_CASE)
                            .containsMatchIn(leftBody)
                    ) {
                        continue
                    }

                    selected = left to right
                    break@outer
                }
            }

            val (left, right) = selected ?: break
            val replacement = buildString(right.end - left.start) {
                append(current, left.start, left.openingEnd)
                append(current, left.openingEnd, left.closingStart)
                append(current, left.end, right.start)
                append(current, right.openingEnd, right.end)
            }

            current = buildString(current.length) {
                append(current, 0, left.start)
                append(replacement)
                append(current, right.end, current.length)
            }
            coalesced++
        }

        return AdjacentGroupCoalescingResult(current, coalesced)
    }

    /**
     * Returns a canonical signature for a generated VectorDrawable group tag.
     * Unknown/non-Android attributes make the group ineligible rather than
     * risking a merge across semantics the optimizer does not understand.
     */
    private fun groupAttributeSignature(openingTag: String): List<Pair<String, String>>? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) || !trimmed.endsWith('>')) {
            return null
        }

        val attributes = androidAttributeRegex.findAll(trimmed).toList()
        var remainder = trimmed
            .removePrefix(trimmed.substring(0, trimmed.indexOf("group") + "group".length))
            .removeSuffix(">")

        for (attribute in attributes.sortedByDescending { it.range.first }) {
            // Attribute ranges are relative to the original opening tag. Build
            // a simpler validation string instead of attempting range deletion
            // against the trimmed/rebased text.
            remainder = remainder.replace(attribute.value, "", ignoreCase = false)
        }

        if (remainder.any { !it.isWhitespace() }) return null

        return attributes
            .map { attribute ->
                attribute.groupValues[1].lowercase() to attribute.groupValues[3].trim()
            }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
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


    private data class ScaleFlatteningResult(
        val xml: String,
        val flattenedGroups: Int,
        val scaledPaths: Int,
        val scaledStrokeWidths: Int,
        val preservedForSize: Int
    )

    private data class UniformScale(
        val factor: BigDecimal,
        val pivotX: BigDecimal,
        val pivotY: BigDecimal,
        val translateX: BigDecimal,
        val translateY: BigDecimal
    )

    /**
     * Flattens a deliberately narrow class of uniform positive-scale groups.
     *
     * A group is eligible only when:
     * - its only attributes are scaleX/scaleY plus optional pivotX/pivotY and
     *   translateX/translateY;
     * - scaleX and scaleY are equal, finite numeric values greater than zero;
     * - the effective scale is not 1;
     * - its body contains only comments, whitespace, and direct self-closing paths;
     * - the current candidate contains no nested group, clip-path, or nested aapt
     *   paint; eligible nested groups are processed from the inside out; and
     * - every explicit strokeWidth is numeric.
     *
     * Coordinates are scaled around the VectorDrawable pivot and then translated,
     * matching VectorDrawable group-transform semantics. Numeric stroke widths are
     * multiplied by the scale factor. Positive uniform scale preserves
     * arc sweep direction, stroke joins/caps, and path winding.
     */
    private class MutableUniformScaleProfiling {
        var groupDiscoveryNanos: Long = 0
        var eligibilityChecksNanos: Long = 0
        var pathScalingNanos: Long = 0
        var scalePathParseTokenizeNanos: Long = 0
        var scalePathNumericParseNanos: Long = 0
        var scalePathCoordinateMathNanos: Long = 0
        var scalePathArcHandlingNanos: Long = 0
        var scalePathNumberFormattingNanos: Long = 0
        var scalePathReconstructionNanos: Long = 0
        var scalePathNormalizationNanos: Long = 0
        var postScaleP6Attempts: Int = 0
        var postScaleP6Accepted: Int = 0
        var postScaleP6Fallbacks: Int = 0
        var postScaleP6ParserFallbacks: Int = 0
        var postScaleP6InternalFallbacks: Int = 0
        var postScaleP6OptimizationNanos: Long = 0
        var postScaleP6ParserValidationNanos: Long = 0
        var postScaleFullFallbackNanos: Long = 0
        var strokeAdjustmentNanos: Long = 0
        var canonicalizationCostingNanos: Long = 0
        var xmlReplacementNanos: Long = 0
        var candidatesConsidered: Int = 0
        var candidatesRejected: Int = 0
        var proposalsAccepted: Int = 0

        fun snapshot() = UniformScaleProfilingStats(
            groupDiscoveryNanos = groupDiscoveryNanos,
            eligibilityChecksNanos = eligibilityChecksNanos,
            pathScalingNanos = pathScalingNanos,
            scalePathParseTokenizeNanos = scalePathParseTokenizeNanos,
            scalePathNumericParseNanos = scalePathNumericParseNanos,
            scalePathCoordinateMathNanos = scalePathCoordinateMathNanos,
            scalePathArcHandlingNanos = scalePathArcHandlingNanos,
            scalePathNumberFormattingNanos = scalePathNumberFormattingNanos,
            scalePathReconstructionNanos = scalePathReconstructionNanos,
            scalePathNormalizationNanos = scalePathNormalizationNanos,
            postScaleP6Attempts = postScaleP6Attempts,
            postScaleP6Accepted = postScaleP6Accepted,
            postScaleP6Fallbacks = postScaleP6Fallbacks,
            postScaleP6ParserFallbacks = postScaleP6ParserFallbacks,
            postScaleP6InternalFallbacks = postScaleP6InternalFallbacks,
            postScaleP6OptimizationNanos = postScaleP6OptimizationNanos,
            postScaleP6ParserValidationNanos = postScaleP6ParserValidationNanos,
            postScaleFullFallbackNanos = postScaleFullFallbackNanos,
            strokeAdjustmentNanos = strokeAdjustmentNanos,
            canonicalizationCostingNanos = canonicalizationCostingNanos,
            xmlReplacementNanos = xmlReplacementNanos,
            candidatesConsidered = candidatesConsidered,
            candidatesRejected = candidatesRejected,
            proposalsAccepted = proposalsAccepted,
        )
    }

    private data class ScaleFlatteningProposal(
        val range: GroupRange,
        val replacement: String,
        val scaledPaths: Int,
        val scaledStrokeWidths: Int
    )

    /**
     * A14.1: cost-aware positive uniform-scale flattening.
     *
     * Eligible groups are transformed exactly as before, but the scale wrapper
     * is removed only when the fully canonicalized baked representation is
     * strictly smaller than retaining the group.
     *
     * The existing geometry and paint safety rules remain unchanged. The local
     * size comparison ignores indentation and blank presentation lines so that
     * the final pretty-printer cannot influence the decision.
     */
    private fun flattenUniformPositiveScaleGroups(
        xml: String,
        profiling: MutableUniformScaleProfiling? = null
    ): ScaleFlatteningResult {
        var current = xml
        var groupsFlattened = 0
        var pathsScaled = 0
        var strokeWidthsScaled = 0
        val rejectedGroupSignatures = mutableSetOf<String>()

        while (true) {
            val discoveryStart = System.nanoTime()
            val matchedGroups = findMatchedGroups(current)
                // Process eligible nested groups from the inside out. A parent
                // may become eligible after a child has been flattened.
                .sortedBy { it.end - it.start }
            profiling?.groupDiscoveryNanos =
                (profiling?.groupDiscoveryNanos ?: 0L) + (System.nanoTime() - discoveryStart)

            val proposal = matchedGroups.asSequence()
                .firstNotNullOfOrNull { range ->
                    profiling?.candidatesConsidered = (profiling?.candidatesConsidered ?: 0) + 1
                    val eligibilityStart = System.nanoTime()
                    val originalFragment = current.substring(range.start, range.end)
                    val signature = stableFragmentSignature(originalFragment)
                    if (signature in rejectedGroupSignatures) {
                        profiling?.eligibilityChecksNanos =
                            (profiling?.eligibilityChecksNanos ?: 0L) + (System.nanoTime() - eligibilityStart)
                        profiling?.candidatesRejected = (profiling?.candidatesRejected ?: 0) + 1
                        return@firstNotNullOfOrNull null
                    }

                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    val scale = uniformScaleForGroup(openingTag)
                    if (scale == null ||
                        !isDirectSimplePathBody(body) ||
                        !allExplicitStrokeWidthsAreNumeric(body)
                    ) {
                        profiling?.eligibilityChecksNanos =
                            (profiling?.eligibilityChecksNanos ?: 0L) + (System.nanoTime() - eligibilityStart)
                        profiling?.candidatesRejected = (profiling?.candidatesRejected ?: 0) + 1
                        return@firstNotNullOfOrNull null
                    }
                    profiling?.eligibilityChecksNanos =
                        (profiling?.eligibilityChecksNanos ?: 0L) + (System.nanoTime() - eligibilityStart)

                    var scaledCount = 0
                    var strokeCount = 0
                    var failed = false

                    val scaledBody = pathElementRegex.replace(body) { match ->
                        if (failed) return@replace match.value
                        val element = match.value
                        if (!element.trimEnd().endsWith("/>") ||
                            element.contains("<aapt:attr", true)
                        ) {
                            failed = true
                            return@replace element
                        }

                        val pathData = attributeValue(element, "android:pathData")
                        if (pathData == null) {
                            failed = true
                            return@replace element
                        }

                        val pathScalingStart = System.nanoTime()
                        val scaledPathData = scalePathData(
                            pathData = pathData,
                            factor = scale.factor,
                            pivotX = scale.pivotX,
                            pivotY = scale.pivotY,
                            translateX = scale.translateX,
                            translateY = scale.translateY,
                            profiling = profiling
                        )
                        profiling?.pathScalingNanos =
                            (profiling?.pathScalingNanos ?: 0L) + (System.nanoTime() - pathScalingStart)
                        if (scaledPathData == null) {
                            failed = true
                            return@replace element
                        }

                        var updated = replacePathData(element, scaledPathData)
                        val strokeStart = System.nanoTime()
                        val strokeWidth = attributeValue(updated, "android:strokeWidth")
                        if (strokeWidth != null) {
                            val numericWidth = strokeWidth.trim().toBigDecimalOrNull()
                            if (numericWidth == null) {
                                failed = true
                                return@replace element
                            }
                            updated = replaceAndroidAttribute(
                                updated,
                                "strokeWidth",
                                formatBigDecimal(numericWidth.multiply(scale.factor))
                            )
                            strokeCount++
                        }
                        profiling?.strokeAdjustmentNanos =
                            (profiling?.strokeAdjustmentNanos ?: 0L) + (System.nanoTime() - strokeStart)

                        scaledCount++
                        updated
                    }

                    if (failed || scaledCount == 0) {
                        profiling?.candidatesRejected = (profiling?.candidatesRejected ?: 0) + 1
                        rejectedGroupSignatures += signature
                        return@firstNotNullOfOrNull null
                    }

                    val replacement = removeOneIndentLevel(scaledBody)

                    // Compare the same final decimal spelling that the complete
                    // optimization pipeline will emit.
                    val costingStart = System.nanoTime()
                    val canonicalOriginal =
                        canonicalizePathDecimalPrecision(originalFragment).xml
                    val canonicalReplacement =
                        canonicalizePathDecimalPrecision(replacement).xml

                    val originalCost = stableXmlPayloadCost(canonicalOriginal)
                    val replacementCost = stableXmlPayloadCost(canonicalReplacement)

                    // Removing a group can reduce total XML while still causing
                    // pathData to balloon with repeated decimal coordinates.
                    // Permit only small path-data growth: up to eight characters
                    // or ten percent of the original path data, whichever is
                    // larger. This preserves genuinely compact flattening such
                    // as 8 -> 9 characters, while rejecting cases such as
                    // 24 -> 72 or 132 -> 200.
                    val originalPathDataCost =
                        totalPathDataCharacters(canonicalOriginal)
                    val replacementPathDataCost =
                        totalPathDataCharacters(canonicalReplacement)
                    val pathDataGrowth =
                        replacementPathDataCost - originalPathDataCost
                    val allowedPathDataGrowth =
                        maxOf(8, originalPathDataCost / 10)
                    profiling?.canonicalizationCostingNanos =
                        (profiling?.canonicalizationCostingNanos ?: 0L) + (System.nanoTime() - costingStart)

                    val acceptedByExistingGate =
                        replacementCost < originalCost &&
                            pathDataGrowth <= allowedPathDataGrowth

                    if (!acceptedByExistingGate) {
                        profiling?.candidatesRejected = (profiling?.candidatesRejected ?: 0) + 1
                        rejectedGroupSignatures += signature
                        return@firstNotNullOfOrNull null
                    }

                    profiling?.proposalsAccepted = (profiling?.proposalsAccepted ?: 0) + 1
                    ScaleFlatteningProposal(
                        range = range,
                        replacement = replacement,
                        scaledPaths = scaledCount,
                        scaledStrokeWidths = strokeCount
                    )
                }

            if (proposal == null) {
                return ScaleFlatteningResult(
                    xml = current,
                    flattenedGroups = groupsFlattened,
                    scaledPaths = pathsScaled,
                    scaledStrokeWidths = strokeWidthsScaled,
                    preservedForSize = rejectedGroupSignatures.size
                )
            }

            val replacementStart = System.nanoTime()
            current = buildString(current.length) {
                append(current, 0, proposal.range.start)
                append(proposal.replacement)
                append(current, proposal.range.end, current.length)
            }
            profiling?.xmlReplacementNanos =
                (profiling?.xmlReplacementNanos ?: 0L) + (System.nanoTime() - replacementStart)
            groupsFlattened++
            pathsScaled += proposal.scaledPaths
            strokeWidthsScaled += proposal.scaledStrokeWidths
        }
    }

    private fun uniformScaleForGroup(openingTag: String): UniformScale? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) || !trimmed.endsWith('>')) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val allowed = setOf(
            "scalex", "scaley", "pivotx", "pivoty", "translatex", "translatey"
        )
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it !in allowed }) return null
        if (names.count { it == "scalex" } > 1 || names.count { it == "scaley" } > 1 ||
            names.count { it == "pivotx" } > 1 || names.count { it == "pivoty" } > 1 ||
            names.count { it == "translatex" } > 1 || names.count { it == "translatey" } > 1
        ) return null

        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val scaleX = attributes.firstOrNull { it.groupValues[1].equals("scaleX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val scaleY = attributes.firstOrNull { it.groupValues[1].equals("scaleY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ONE
        if (scaleX.compareTo(scaleY) != 0 || scaleX.compareTo(BigDecimal.ZERO) <= 0) return null
        if (scaleX.compareTo(BigDecimal.ONE) == 0) return null

        val pivotX = attributes.firstOrNull { it.groupValues[1].equals("pivotX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val pivotY = attributes.firstOrNull { it.groupValues[1].equals("pivotY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val translateX = attributes.firstOrNull { it.groupValues[1].equals("translateX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val translateY = attributes.firstOrNull { it.groupValues[1].equals("translateY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        return UniformScale(scaleX, pivotX, pivotY, translateX, translateY)
    }

    private fun allExplicitStrokeWidthsAreNumeric(body: String): Boolean {
        return pathElementRegex.findAll(body).all { match ->
            val strokeWidth = attributeValue(match.value, "android:strokeWidth")
            strokeWidth == null || strokeWidth.trim().toBigDecimalOrNull() != null
        }
    }

    private fun replaceAndroidAttribute(element: String, name: String, value: String): String {
        val regex = Regex(
            """(android:${Regex.escape(name)}\s*=\s*)(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(element) ?: return element
        val replacement =
            "${match.groupValues[1]}${match.groupValues[2]}$value${match.groupValues[2]}"
        return element.replaceRange(match.range, replacement)
    }

    /**
     * G2.26 comparison-only post-scale stage-addback pipeline.
     *
     * The full optimizer remains production-authoritative. These candidate
     * pipelines deliberately reuse the exact production stage functions in
     * their production order, omitting only stages not enabled by [pipeline].
     */
    private enum class PostScaleAddbackPipeline(
        val displayName: String,
        val includeRedundantGeometry: Boolean,
        val includeArcCleanup: Boolean,
        val includeCurveCleanup: Boolean,
        val includeCollinearCleanup: Boolean,
        val includeGlobalNumeric: Boolean
    ) {
        P1_COMMAND_ONLY(
            "P1 Syntax + command minimization",
            false, false, false, false, false
        ),
        P2_ARC(
            "P2 P1 + arc cleanup",
            false, true, false, false, false
        ),
        P3_ARC_NUMERIC(
            "P3 P2 + global numeric serialization",
            false, true, false, false, true
        ),
        P4_REDUNDANT_ARC_NUMERIC(
            "P4 P3 + redundant geometry cleanup",
            true, true, false, false, true
        ),
        P5_CURVE(
            "P5 P4 + curve simplification",
            true, true, true, false, true
        ),
        P6_COLLINEAR(
            "P6 P5 + collinear consolidation",
            true, true, true, true, true
        )
    }

    private fun optimizePostScaleStageAddbackForComparison(
        pathData: String,
        pipeline: PostScaleAddbackPipeline
    ): String? {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) return pathData.trim()

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return null
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) return null

        val syntaxOutput = StringBuilder(pathData.length)
        var activeCommand: Char? = null
        var previousWasNumber = false
        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                val canUseImplicitRepeat =
                    activeCommand == command && command !in charArrayOf('M', 'm', 'Z', 'z')
                if (!canUseImplicitRepeat) {
                    syntaxOutput.append(command)
                    previousWasNumber = false
                }
                activeCommand = command
            } else {
                val normalized = token.toBigDecimalOrNull()
                    ?.let(::formatPathNumber)
                    ?: normalizeNumber(token)
                if (previousWasNumber) syntaxOutput.append(',')
                syntaxOutput.append(normalized)
                previousWasNumber = true
            }
        }

        var current = syntaxOutput.toString()

        if (pipeline.includeRedundantGeometry) {
            current = removeRedundantNonDrawingSegments(current).pathData
        }

        if (pipeline.includeArcCleanup) {
            val hasArcCommands = matches.any { match ->
                val token = match.value
                isCommand(token) && token[0].uppercaseChar() == 'A'
            }
            if (hasArcCommands) {
                current = canonicalizeArcRadii(current).pathData
                current = canonicalizeArcRotations(current).pathData
                current = globallyMinimizeArcRepresentations(current).pathData
                current = reduceArcRotationsByHalfTurns(current).pathData
                current = minimizeArcAxisRepresentation(current).pathData
                current = simplifyDegenerateArcs(current).pathData
            }
        }

        if (pipeline.includeCurveCleanup) {
            val cubic = reduceExactCubicCurvesToQuadratic(current, null)
            val reusableSegments = if (cubic.pathData == current) cubic.reusableSegments else null
            current = simplifyStraightBezierCurves(
                cubic.pathData,
                null,
                preParsedSegments = reusableSegments
            ).pathData
        }

        if (pipeline.includeCollinearCleanup) {
            current = consolidateConsecutiveCollinearLineRuns(current).pathData
        }

        current = shortenPathCommands(current, null).pathData
        current = globallyMinimizeCommandSequence(current, null).pathData

        if (pipeline.includeGlobalNumeric) {
            current = globallyOptimizeNumericSerialization(current).pathData
        }

        return current
    }

    /** G2.24/G2.25 baseline retained for the existing Developer Tools search. */
    private fun optimizePostScaleNarrowedForComparison(pathData: String): String? =
        optimizePostScaleStageAddbackForComparison(
            pathData,
            PostScaleAddbackPipeline.P1_COMMAND_ONLY
        )


    private fun scalePathData(
        pathData: String,
        factor: BigDecimal,
        pivotX: BigDecimal,
        pivotY: BigDecimal,
        translateX: BigDecimal,
        translateY: BigDecimal,
        profiling: MutableUniformScaleProfiling? = null
    ): String? {
        val parseStart = System.nanoTime()
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        profiling?.scalePathParseTokenizeNanos =
            (profiling?.scalePathParseTokenizeNanos ?: 0L) + (System.nanoTime() - parseStart)
        if (tokens.isEmpty()) return null

        val numericParseStart = System.nanoTime()
        val segments = parseNormalizedSegmentsFromTokens(tokens) ?: return null
        profiling?.scalePathNumericParseNanos =
            (profiling?.scalePathNumericParseNanos ?: 0L) + (System.nanoTime() - numericParseStart)
        if (segments.isEmpty()) return null

        fun scaledX(value: BigDecimal): BigDecimal =
            pivotX.add(value.subtract(pivotX).multiply(factor)).add(translateX)
        fun scaledY(value: BigDecimal): BigDecimal =
            pivotY.add(value.subtract(pivotY).multiply(factor)).add(translateY)

        val output = StringBuilder(pathData.length + 24)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val mathStart = System.nanoTime()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val scaled = when (upper) {
                'M', 'L', 'T' -> listOf(scaledX(absolute[0]), scaledY(absolute[1]))
                'H' -> listOf(scaledX(absolute[0]))
                'V' -> listOf(scaledY(absolute[0]))
                'C' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3]),
                    scaledX(absolute[4]), scaledY(absolute[5])
                )
                'S', 'Q' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3])
                )
                'A' -> {
                    val arcStart = System.nanoTime()
                    val result = listOf(
                        absolute[0].multiply(factor),
                        absolute[1].multiply(factor),
                        absolute[2], absolute[3], absolute[4],
                        scaledX(absolute[5]), scaledY(absolute[6])
                    )
                    profiling?.scalePathArcHandlingNanos =
                        (profiling?.scalePathArcHandlingNanos ?: 0L) + (System.nanoTime() - arcStart)
                    result
                }
                'Z' -> emptyList()
                else -> return null
            }
            profiling?.scalePathCoordinateMathNanos =
                (profiling?.scalePathCoordinateMathNanos ?: 0L) + (System.nanoTime() - mathStart)

            val reconstructionStart = System.nanoTime()
            output.append(upper)
            scaled.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                val formattingStart = System.nanoTime()
                val formatted = formatBigDecimal(value)
                profiling?.scalePathNumberFormattingNanos =
                    (profiling?.scalePathNumberFormattingNanos ?: 0L) +
                        (System.nanoTime() - formattingStart)
                output.append(formatted)
            }
            profiling?.scalePathReconstructionNanos =
                (profiling?.scalePathReconstructionNanos ?: 0L) +
                    (System.nanoTime() - reconstructionStart)

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        val rawScaledPath = output.toString()
        val normalizationStart = System.nanoTime()

        // G2.28: P6 is the production post-scale pipeline. Two complete G2.26
        // runs (200,000 deterministic scale-heavy cases total) reproduced the full
        // optimizer byte-for-byte. Keep a cheap parser guard here and conservatively
        // fall back to the complete optimizer on parser or internal failure.
        profiling?.postScaleP6Attempts = (profiling?.postScaleP6Attempts ?: 0) + 1

        var fallbackReason: String? = null
        val p6Start = System.nanoTime()
        val p6Candidate = try {
            optimizePostScaleStageAddbackForComparison(
                rawScaledPath,
                PostScaleAddbackPipeline.P6_COLLINEAR
            )
        } catch (_: Throwable) {
            fallbackReason = "internal"
            null
        }
        profiling?.postScaleP6OptimizationNanos =
            (profiling?.postScaleP6OptimizationNanos ?: 0L) +
                (System.nanoTime() - p6Start)

        val validationStart = System.nanoTime()
        if (fallbackReason == null &&
            (p6Candidate == null || parseNormalizedSegments(p6Candidate) == null)
        ) {
            fallbackReason = "parser"
        }
        profiling?.postScaleP6ParserValidationNanos =
            (profiling?.postScaleP6ParserValidationNanos ?: 0L) +
                (System.nanoTime() - validationStart)

        val normalized = if (fallbackReason == null && p6Candidate != null) {
            profiling?.postScaleP6Accepted = (profiling?.postScaleP6Accepted ?: 0) + 1
            p6Candidate
        } else {
            profiling?.postScaleP6Fallbacks = (profiling?.postScaleP6Fallbacks ?: 0) + 1
            when (fallbackReason) {
                "parser" -> profiling?.postScaleP6ParserFallbacks =
                    (profiling?.postScaleP6ParserFallbacks ?: 0) + 1
                else -> profiling?.postScaleP6InternalFallbacks =
                    (profiling?.postScaleP6InternalFallbacks ?: 0) + 1
            }
            val fallbackStart = System.nanoTime()
            val full = optimizePathData(rawScaledPath).pathData
            profiling?.postScaleFullFallbackNanos =
                (profiling?.postScaleFullFallbackNanos ?: 0L) +
                    (System.nanoTime() - fallbackStart)
            full
        }

        profiling?.scalePathNormalizationNanos =
            (profiling?.scalePathNormalizationNanos ?: 0L) +
                (System.nanoTime() - normalizationStart)
        return normalized
    }


    private data class NonUniformScaleFlatteningResult(
        val xml: String,
        val flattenedGroups: Int,
        val scaledPaths: Int,
        val preservedForSize: Int
    )

    private data class AxisScale(
        val scaleX: BigDecimal,
        val scaleY: BigDecimal,
        val pivotX: BigDecimal,
        val pivotY: BigDecimal,
        val translateX: BigDecimal,
        val translateY: BigDecimal
    )

    private data class NonUniformScaleFlatteningProposal(
        val range: GroupRange,
        val replacement: String,
        val scaledPaths: Int
    )

    /**
     * A14.2: cost-aware non-uniform positive-scale flattening for fill-only paths.
     *
     * This deliberately excludes any path that may have a visible stroke.
     * Android VectorDrawable has only one strokeWidth value, so baking unequal
     * X/Y scale into stroked geometry cannot preserve stroke behavior exactly.
     *
     * Axis-aligned arc commands are supported. Rotated elliptical arcs are
     * conservatively rejected because unequal axis scaling changes their
     * effective radii and rotation non-trivially.
     */
    private fun flattenNonUniformPositiveScaleFillOnlyGroups(
        xml: String
    ): NonUniformScaleFlatteningResult {
        var current = xml
        var groupsFlattened = 0
        var pathsScaled = 0
        val rejectedGroupSignatures = mutableSetOf<String>()

        while (true) {
            val proposal = findMatchedGroups(current)
                .asSequence()
                .sortedBy { it.end - it.start }
                .firstNotNullOfOrNull { range ->
                    val originalFragment = current.substring(range.start, range.end)
                    val signature = stableFragmentSignature(originalFragment)
                    if (signature in rejectedGroupSignatures) {
                        return@firstNotNullOfOrNull null
                    }

                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    val scale = nonUniformScaleForGroup(openingTag)
                        ?: return@firstNotNullOfOrNull null

                    if (!isDirectSimplePathBody(body)) {
                        return@firstNotNullOfOrNull null
                    }

                    val pathMatches = pathElementRegex.findAll(body).toList()
                    if (pathMatches.isEmpty() ||
                        pathMatches.any { !hasDefinitelyNoVisibleStroke(it.value) }
                    ) {
                        return@firstNotNullOfOrNull null
                    }

                    var scaledCount = 0
                    var failed = false
                    val scaledBody = pathElementRegex.replace(body) { match ->
                        if (failed) return@replace match.value
                        val element = match.value
                        if (!element.trimEnd().endsWith("/>") ||
                            element.contains("<aapt:attr", true)
                        ) {
                            failed = true
                            return@replace element
                        }

                        val pathData = attributeValue(element, "android:pathData")
                        if (pathData == null) {
                            failed = true
                            return@replace element
                        }

                        val scaledPathData = scalePathDataNonUniform(
                            pathData = pathData,
                            scaleX = scale.scaleX,
                            scaleY = scale.scaleY,
                            pivotX = scale.pivotX,
                            pivotY = scale.pivotY,
                            translateX = scale.translateX,
                            translateY = scale.translateY
                        )
                        if (scaledPathData == null) {
                            failed = true
                            return@replace element
                        }

                        scaledCount++
                        replacePathData(element, scaledPathData)
                    }

                    if (failed || scaledCount == 0) {
                        return@firstNotNullOfOrNull null
                    }

                    val replacement = removeOneIndentLevel(scaledBody)
                    val canonicalOriginal =
                        canonicalizePathDecimalPrecision(originalFragment).xml
                    val canonicalReplacement =
                        canonicalizePathDecimalPrecision(replacement).xml

                    val originalCost = stableXmlPayloadCost(canonicalOriginal)
                    val replacementCost = stableXmlPayloadCost(canonicalReplacement)
                    val originalPathDataCost =
                        totalPathDataCharacters(canonicalOriginal)
                    val replacementPathDataCost =
                        totalPathDataCharacters(canonicalReplacement)
                    val pathDataGrowth =
                        replacementPathDataCost - originalPathDataCost
                    val allowedPathDataGrowth =
                        maxOf(8, originalPathDataCost / 10)

                    if (replacementCost >= originalCost ||
                        pathDataGrowth > allowedPathDataGrowth
                    ) {
                        rejectedGroupSignatures += signature
                        return@firstNotNullOfOrNull null
                    }

                    NonUniformScaleFlatteningProposal(
                        range = range,
                        replacement = replacement,
                        scaledPaths = scaledCount
                    )
                }

            if (proposal == null) {
                return NonUniformScaleFlatteningResult(
                    xml = current,
                    flattenedGroups = groupsFlattened,
                    scaledPaths = pathsScaled,
                    preservedForSize = rejectedGroupSignatures.size
                )
            }

            current = buildString(current.length) {
                append(current, 0, proposal.range.start)
                append(proposal.replacement)
                append(current, proposal.range.end, current.length)
            }
            groupsFlattened++
            pathsScaled += proposal.scaledPaths
        }
    }

    private fun nonUniformScaleForGroup(openingTag: String): AxisScale? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) ||
            !trimmed.endsWith('>')
        ) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val allowed = setOf(
            "scalex", "scaley", "pivotx", "pivoty", "translatex", "translatey"
        )
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it !in allowed }) return null
        if (allowed.any { allowedName -> names.count { it == allowedName } > 1 }) {
            return null
        }

        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val scaleX = attributes.firstOrNull {
            it.groupValues[1].equals("scaleX", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ONE
        val scaleY = attributes.firstOrNull {
            it.groupValues[1].equals("scaleY", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ONE

        if (scaleX.compareTo(BigDecimal.ZERO) <= 0 ||
            scaleY.compareTo(BigDecimal.ZERO) <= 0 ||
            scaleX.compareTo(scaleY) == 0
        ) return null

        val pivotX = attributes.firstOrNull {
            it.groupValues[1].equals("pivotX", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val pivotY = attributes.firstOrNull {
            it.groupValues[1].equals("pivotY", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val translateX = attributes.firstOrNull {
            it.groupValues[1].equals("translateX", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val translateY = attributes.firstOrNull {
            it.groupValues[1].equals("translateY", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        return AxisScale(
            scaleX, scaleY, pivotX, pivotY, translateX, translateY
        )
    }

    private fun hasDefinitelyNoVisibleStroke(pathElement: String): Boolean {
        val strokeColor = attributeValue(pathElement, "android:strokeColor")
            ?: return true

        if (isTransparentColor(strokeColor)) return true

        val strokeAlpha = attributeValue(pathElement, "android:strokeAlpha")
            ?.trim()?.toBigDecimalOrNull()
        if (strokeAlpha != null && strokeAlpha.compareTo(BigDecimal.ZERO) == 0) {
            return true
        }

        val strokeWidth = attributeValue(pathElement, "android:strokeWidth")
            ?.trim()?.toBigDecimalOrNull()
        if (strokeWidth != null && strokeWidth.compareTo(BigDecimal.ZERO) == 0) {
            return true
        }

        return false
    }

    private fun scalePathDataNonUniform(
        pathData: String,
        scaleX: BigDecimal,
        scaleY: BigDecimal,
        pivotX: BigDecimal,
        pivotY: BigDecimal,
        translateX: BigDecimal,
        translateY: BigDecimal
    ): String? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        if (segments.isEmpty()) return null

        fun scaledX(value: BigDecimal): BigDecimal =
            pivotX.add(value.subtract(pivotX).multiply(scaleX)).add(translateX)
        fun scaledY(value: BigDecimal): BigDecimal =
            pivotY.add(value.subtract(pivotY).multiply(scaleY)).add(translateY)

        val output = StringBuilder(pathData.length + 24)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val scaled = when (upper) {
                'M', 'L', 'T' ->
                    listOf(scaledX(absolute[0]), scaledY(absolute[1]))
                'H' -> listOf(scaledX(absolute[0]))
                'V' -> listOf(scaledY(absolute[0]))
                'C' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3]),
                    scaledX(absolute[4]), scaledY(absolute[5])
                )
                'S', 'Q' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3])
                )
                'A' -> {
                    val rotation = absolute[2]
                        .remainder(BigDecimal("180"))
                        .stripTrailingZeros()
                    if (rotation.compareTo(BigDecimal.ZERO) != 0) return null
                    listOf(
                        absolute[0].multiply(scaleX),
                        absolute[1].multiply(scaleY),
                        absolute[2], absolute[3], absolute[4],
                        scaledX(absolute[5]), scaledY(absolute[6])
                    )
                }
                'Z' -> emptyList()
                else -> return null
            }

            output.append(upper)
            scaled.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                output.append(formatBigDecimal(value))
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        return optimizePathData(output.toString()).pathData
    }


    private data class RotationFlatteningResult(
        val xml: String,
        val flattenedGroups: Int,
        val rotatedPaths: Int,
        val preservedForSize: Int
    )

    private data class RotationTransform(
        val degrees: BigDecimal,
        val pivotX: BigDecimal,
        val pivotY: BigDecimal,
        val translateX: BigDecimal,
        val translateY: BigDecimal
    )

    private data class RotationFlatteningProposal(
        val range: GroupRange,
        val replacement: String,
        val rotatedPaths: Int
    )

    /**
     * A14.3: cost-aware flattening of rotation-only groups.
     *
     * Rotation and translation preserve stroke width, so visible strokes remain
     * eligible. Arc commands are conservatively rejected because rotating an
     * SVG elliptical arc requires updating its x-axis rotation and can interact
     * with later path canonicalization in non-obvious ways.
     */
    private fun flattenRotationOnlyGroups(xml: String): RotationFlatteningResult {
        var current = xml
        var groupsFlattened = 0
        var pathsRotated = 0
        val rejectedGroupSignatures = mutableSetOf<String>()

        while (true) {
            val proposal = findMatchedGroups(current)
                .asSequence()
                .sortedBy { it.end - it.start }
                .firstNotNullOfOrNull { range ->
                    val originalFragment = current.substring(range.start, range.end)
                    val signature = stableFragmentSignature(originalFragment)
                    if (signature in rejectedGroupSignatures) {
                        return@firstNotNullOfOrNull null
                    }

                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    val transform = rotationTransformForGroup(openingTag)
                        ?: return@firstNotNullOfOrNull null

                    if (!isDirectSimplePathBody(body)) {
                        return@firstNotNullOfOrNull null
                    }

                    val pathMatches = pathElementRegex.findAll(body).toList()
                    if (pathMatches.isEmpty()) {
                        return@firstNotNullOfOrNull null
                    }

                    var rotatedCount = 0
                    var failed = false
                    val rotatedBody = pathElementRegex.replace(body) { match ->
                        if (failed) return@replace match.value
                        val element = match.value
                        if (!element.trimEnd().endsWith("/>") ||
                            element.contains("<aapt:attr", true)
                        ) {
                            failed = true
                            return@replace element
                        }

                        val pathData = attributeValue(element, "android:pathData")
                        if (pathData == null) {
                            failed = true
                            return@replace element
                        }

                        val rotatedPathData = rotatePathData(
                            pathData = pathData,
                            transform = transform
                        )
                        if (rotatedPathData == null) {
                            failed = true
                            return@replace element
                        }

                        rotatedCount++
                        replacePathData(element, rotatedPathData)
                    }

                    if (failed || rotatedCount == 0) {
                        return@firstNotNullOfOrNull null
                    }

                    val replacement = removeOneIndentLevel(rotatedBody)
                    val canonicalOriginal =
                        canonicalizePathDecimalPrecision(originalFragment).xml
                    val canonicalReplacement =
                        canonicalizePathDecimalPrecision(replacement).xml

                    val originalCost = stableXmlPayloadCost(canonicalOriginal)
                    val replacementCost = stableXmlPayloadCost(canonicalReplacement)
                    val originalPathDataCost =
                        totalPathDataCharacters(canonicalOriginal)
                    val replacementPathDataCost =
                        totalPathDataCharacters(canonicalReplacement)
                    val pathDataGrowth =
                        replacementPathDataCost - originalPathDataCost
                    val allowedPathDataGrowth =
                        maxOf(8, originalPathDataCost / 10)

                    if (replacementCost >= originalCost ||
                        pathDataGrowth > allowedPathDataGrowth
                    ) {
                        rejectedGroupSignatures += signature
                        return@firstNotNullOfOrNull null
                    }

                    RotationFlatteningProposal(
                        range = range,
                        replacement = replacement,
                        rotatedPaths = rotatedCount
                    )
                }

            if (proposal == null) {
                return RotationFlatteningResult(
                    xml = current,
                    flattenedGroups = groupsFlattened,
                    rotatedPaths = pathsRotated,
                    preservedForSize = rejectedGroupSignatures.size
                )
            }

            current = buildString(current.length) {
                append(current, 0, proposal.range.start)
                append(proposal.replacement)
                append(current, proposal.range.end, current.length)
            }
            groupsFlattened++
            pathsRotated += proposal.rotatedPaths
        }
    }

    private fun rotationTransformForGroup(openingTag: String): RotationTransform? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) ||
            !trimmed.endsWith('>')
        ) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val allowed = setOf(
            "rotation", "pivotx", "pivoty", "translatex", "translatey"
        )
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it !in allowed }) return null
        if (allowed.any { name -> names.count { it == name } > 1 }) return null

        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val degrees = attributes.firstOrNull {
            it.groupValues[1].equals("rotation", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: return null

        val normalized = degrees.remainder(BigDecimal("360")).stripTrailingZeros()
        if (normalized.compareTo(BigDecimal.ZERO) == 0) return null

        val pivotX = attributes.firstOrNull {
            it.groupValues[1].equals("pivotX", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val pivotY = attributes.firstOrNull {
            it.groupValues[1].equals("pivotY", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val translateX = attributes.firstOrNull {
            it.groupValues[1].equals("translateX", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val translateY = attributes.firstOrNull {
            it.groupValues[1].equals("translateY", true)
        }?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO

        return RotationTransform(
            degrees = normalized,
            pivotX = pivotX,
            pivotY = pivotY,
            translateX = translateX,
            translateY = translateY
        )
    }

    private fun rotatePathData(
        pathData: String,
        transform: RotationTransform
    ): String? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        if (segments.isEmpty()) return null
        if (segments.any { it.command.uppercaseChar() == 'A' }) return null

        // Use exact quarter-turn values whenever possible. Computing sin/cos
        // through Double for 90/180/270 degrees produces tiny residuals such as
        // 6.123233995736766E-17. Those residuals make otherwise simple rotated
        // rectangles much longer and can prevent the cost-aware flattener from
        // accepting an exact rewrite. Exact values also avoid feeding numerical
        // noise into the later path command optimizer.
        val quarterTurns = transform.degrees
            .remainder(BigDecimal("360"))
            .let { if (it.signum() < 0) it.add(BigDecimal("360")) else it }
            .divideAndRemainder(BigDecimal("90"))

        val exactQuarterTurn = quarterTurns[1].compareTo(BigDecimal.ZERO) == 0
        val (cosine, sine) = if (exactQuarterTurn) {
            when (quarterTurns[0].toInt()) {
                0 -> BigDecimal.ONE to BigDecimal.ZERO
                1 -> BigDecimal.ZERO to BigDecimal.ONE
                2 -> BigDecimal.ONE.negate() to BigDecimal.ZERO
                3 -> BigDecimal.ZERO to BigDecimal.ONE.negate()
                else -> return null
            }
        } else {
            val radians = Math.toRadians(transform.degrees.toDouble())
            BigDecimal.valueOf(cos(radians)) to BigDecimal.valueOf(sin(radians))
        }

        fun rotatePoint(x: BigDecimal, y: BigDecimal): Pair<BigDecimal, BigDecimal> {
            val localX = x.subtract(transform.pivotX)
            val localY = y.subtract(transform.pivotY)
            val rotatedX = localX.multiply(cosine).subtract(localY.multiply(sine))
                .add(transform.pivotX).add(transform.translateX)
            val rotatedY = localX.multiply(sine).add(localY.multiply(cosine))
                .add(transform.pivotY).add(transform.translateY)
            return rotatedX to rotatedY
        }

        val output = StringBuilder(pathData.length + 32)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)

            fun appendPoint(x: BigDecimal, y: BigDecimal, first: Boolean) {
                val point = rotatePoint(x, y)
                if (!first) output.append(',')
                output.append(formatBigDecimal(point.first))
                output.append(',')
                output.append(formatBigDecimal(point.second))
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    output.append(upper)
                    appendPoint(absolute[0], absolute[1], true)
                }
                'H' -> {
                    output.append('L')
                    appendPoint(absolute[0], currentY, true)
                }
                'V' -> {
                    output.append('L')
                    appendPoint(currentX, absolute[0], true)
                }
                'C' -> {
                    output.append('C')
                    appendPoint(absolute[0], absolute[1], true)
                    appendPoint(absolute[2], absolute[3], false)
                    appendPoint(absolute[4], absolute[5], false)
                }
                'S', 'Q' -> {
                    output.append(upper)
                    appendPoint(absolute[0], absolute[1], true)
                    appendPoint(absolute[2], absolute[3], false)
                }
                'Z' -> output.append('Z')
                else -> return null
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        val rotatedPathData = output.toString()
        val optimizedPathData = optimizePathData(rotatedPathData).pathData

        // Rotation flattening happens after the main path-syntax pass. Validate
        // its optimized result independently so a malformed shorter spelling can
        // never enter the final VectorDrawable. The unshortened rotated path is
        // already complete and geometry-equivalent, so it is the safe fallback.
        return if (parseNormalizedSegments(optimizedPathData) != null) {
            optimizedPathData
        } else {
            rotatedPathData
        }
    }

    private data class TranslationFlatteningResult(
        val xml: String,
        val flattenedGroups: Int,
        val translatedPaths: Int,
        val preservedForSize: Int
    )

    /**
     * Flattens a deliberately narrow class of translation-only groups.
     *
     * A group is eligible only when:
     * - its only attributes are android:translateX and/or android:translateY;
     * - at least one translation component is non-zero;
     * - its body contains only comments, whitespace, and direct self-closing paths;
     * - it contains no nested group, clip-path, or nested aapt paint.
     *
     * The translation is baked into every path coordinate, then the wrapper is
     * removed. Rejecting nested paint keeps viewport-space gradient semantics
     * unchanged. This is intentionally conservative for the first A7 pass.
     */
    private data class TranslationFlatteningProposal(
        val range: GroupRange,
        val replacement: String,
        val translatedPaths: Int
    )

    /**
     * A12.1: cost-aware translation flattening.
     *
     * Eligible translation-only groups are still baked exactly as before, but
     * the wrapper is removed only when the canonicalized replacement is smaller
     * than the canonicalized group it would replace.
     *
     * Cost intentionally ignores indentation and blank-line presentation. This
     * measures stable XML payload rather than allowing nesting depth or the final
     * pretty-printer to influence the decision.
     */
    private fun flattenTranslationOnlyGroups(xml: String): TranslationFlatteningResult {
        var current = xml
        var groupsFlattened = 0
        var pathsTranslated = 0
        val rejectedGroupSignatures = mutableSetOf<String>()

        while (true) {
            val proposal = findMatchedGroups(current)
                .sortedBy { it.end - it.start }
                .firstNotNullOfOrNull { range ->
                    val originalFragment = current.substring(range.start, range.end)
                    val signature = stableFragmentSignature(originalFragment)
                    if (signature in rejectedGroupSignatures) {
                        return@firstNotNullOfOrNull null
                    }

                    val openingTag = current.substring(range.start, range.openingEnd)
                    val body = current.substring(range.openingEnd, range.closingStart)
                    val translation = translationForGroup(openingTag)
                        ?: return@firstNotNullOfOrNull null
                    if (!isDirectSimplePathBody(body)) {
                        return@firstNotNullOfOrNull null
                    }

                    val (dx, dy) = translation
                    var translatedCount = 0
                    var failed = false

                    val translatedBody = pathElementRegex.replace(body) { match ->
                        if (failed) return@replace match.value
                        val element = match.value
                        if (!element.trimEnd().endsWith("/>") ||
                            element.contains("<aapt:attr", true)
                        ) {
                            failed = true
                            return@replace element
                        }

                        val pathData = attributeValue(element, "android:pathData")
                        if (pathData == null) {
                            failed = true
                            return@replace element
                        }

                        val translated = translatePathData(pathData, dx, dy)
                        if (translated == null) {
                            failed = true
                            element
                        } else {
                            translatedCount++
                            replacePathData(element, translated)
                        }
                    }

                    if (failed || translatedCount == 0) {
                        rejectedGroupSignatures += signature
                        return@firstNotNullOfOrNull null
                    }

                    val replacement = removeOneIndentLevel(translatedBody)

                    // Apply the same final numeric canonicalization to both
                    // alternatives before measuring them.
                    val canonicalOriginal =
                        canonicalizePathDecimalPrecision(originalFragment).xml
                    val canonicalReplacement =
                        canonicalizePathDecimalPrecision(replacement).xml

                    val originalCost = stableXmlPayloadCost(canonicalOriginal)
                    val replacementCost = stableXmlPayloadCost(canonicalReplacement)

                    if (replacementCost >= originalCost) {
                        rejectedGroupSignatures += signature
                        return@firstNotNullOfOrNull null
                    }

                    TranslationFlatteningProposal(
                        range = range,
                        replacement = replacement,
                        translatedPaths = translatedCount
                    )
                }

            if (proposal == null) {
                return TranslationFlatteningResult(
                    xml = current,
                    flattenedGroups = groupsFlattened,
                    translatedPaths = pathsTranslated,
                    preservedForSize = rejectedGroupSignatures.size
                )
            }

            current = buildString(current.length) {
                append(current, 0, proposal.range.start)
                append(proposal.replacement)
                append(current, proposal.range.end, current.length)
            }
            groupsFlattened++
            pathsTranslated += proposal.translatedPaths
        }
    }

    /**
     * Stable local serialization cost used for A12 decisions.
     *
     * Leading indentation and empty presentation lines are excluded. All tags,
     * attributes, comments, path data, and non-whitespace text remain counted.
     */
    private fun stableXmlPayloadCost(fragment: String): Int =
        fragment.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .sumOf { it.length + 1 }

    /**
     * Returns the combined character count of every direct pathData value in
     * a local XML fragment. The caller supplies the canonicalized fragment so
     * the decision uses the same decimal spelling emitted by the final output.
     */
    private fun totalPathDataCharacters(fragment: String): Int =
        pathElementRegex.findAll(fragment).sumOf { match ->
            attributeValue(match.value, "android:pathData")?.length ?: 0
        }

    private fun stableFragmentSignature(fragment: String): String =
        fragment.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    private fun translationForGroup(openingTag: String): Pair<BigDecimal, BigDecimal>? {
        val trimmed = openingTag.trim()
        if (!trimmed.startsWith("<group", ignoreCase = true) || !trimmed.endsWith('>')) return null

        val attributes = androidAttributeRegex.findAll(openingTag).toList()
        val names = attributes.map { it.groupValues[1].lowercase() }
        if (names.any { it != "translatex" && it != "translatey" }) return null

        // Reject unknown/non-Android attributes as well. The text remaining after
        // removing the element name and recognized attributes must be empty.
        var remainder = openingTag
            .replace(Regex("""^\s*<group\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex(""">\s*$"""), "")
        remainder = androidAttributeRegex.replace(remainder, "")
        if (remainder.isNotBlank()) return null

        val dx = attributes.firstOrNull { it.groupValues[1].equals("translateX", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val dy = attributes.firstOrNull { it.groupValues[1].equals("translateY", true) }
            ?.groupValues?.get(3)?.trim()?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (dx.compareTo(BigDecimal.ZERO) == 0 && dy.compareTo(BigDecimal.ZERO) == 0) return null
        return dx to dy
    }

    private fun isDirectSimplePathBody(body: String): Boolean {
        if (Regex("""<(?:group|clip-path)\b""", RegexOption.IGNORE_CASE).containsMatchIn(body)) return false
        if (body.contains("<aapt:attr", ignoreCase = true)) return false

        val withoutComments = xmlCommentRegex.replace(body, "")
        val withoutPaths = pathElementRegex.replace(withoutComments) { match ->
            if (match.value.trimEnd().endsWith("/>")) "" else match.value
        }
        return withoutPaths.isBlank() && pathElementRegex.containsMatchIn(body)
    }

    private fun translatePathData(
        pathData: String,
        dx: BigDecimal,
        dy: BigDecimal
    ): String? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        if (segments.isEmpty()) return null

        val output = StringBuilder(pathData.length + 16)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val translated = when (upper) {
                'M', 'L', 'T' -> listOf(absolute[0].add(dx), absolute[1].add(dy))
                'H' -> listOf(absolute[0].add(dx))
                'V' -> listOf(absolute[0].add(dy))
                'C' -> listOf(
                    absolute[0].add(dx), absolute[1].add(dy),
                    absolute[2].add(dx), absolute[3].add(dy),
                    absolute[4].add(dx), absolute[5].add(dy)
                )
                'S', 'Q' -> listOf(
                    absolute[0].add(dx), absolute[1].add(dy),
                    absolute[2].add(dx), absolute[3].add(dy)
                )
                'A' -> listOf(
                    absolute[0], absolute[1], absolute[2], absolute[3], absolute[4],
                    absolute[5].add(dx), absolute[6].add(dy)
                )
                'Z' -> emptyList()
                else -> return null
            }

            output.append(upper)
            translated.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                output.append(formatBigDecimal(value))
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        return optimizePathData(output.toString()).pathData
    }


    private data class PathMergeOpportunityStats(
        var pairsExamined: Int = 0,
        var samePaintPairs: Int = 0,
        var rejectedNestedPaint: Int = 0,
        var rejectedMissingPathData: Int = 0,
        var rejectedPaintMismatch: Int = 0,
        var rejectedUnsupportedGeometry: Int = 0,
        var rejectedOverlapSafety: Int = 0,
        var rejectedForSize: Int = 0
    )

    private data class PathMergingResult(
        val xml: String,
        val mergedCount: Int,
        val preservedForSize: Int,
        val opportunityStats: PathMergeOpportunityStats,
        val synthesizedPathData: Set<String>
    )


    private data class DuplicateRemovalResult(
        val xml: String,
        val removedCount: Int
    )

    /**
     * Removes only adjacent path elements that are exact rendered duplicates.
     *
     * This pass is deliberately conservative:
     * - both paths must be self-closing and contain no nested aapt paint;
     * - optimized pathData must be byte-for-byte identical;
     * - every Android rendering attribute must be identical;
     * - trim-path attributes are rejected; and
     * - all paints that can draw must be fully opaque.
     *
     * Requiring adjacency keeps the paths in the same immediate XML/group
     * context and avoids crossing clip, group, or ordering boundaries.
     */
    private fun removeExactAdjacentDuplicatePaths(xml: String): DuplicateRemovalResult {
        var current = xml
        var totalRemoved = 0

        while (true) {
            var removedThisPass = false
            val replaced = adjacentSimplePathRegex.replace(current) { match ->
                if (removedThisPass) return@replace match.value

                val first = match.groupValues[1]
                val separator = match.groupValues[2]
                val second = match.groupValues[3]

                if (!areSafeExactDuplicates(first, second)) {
                    match.value
                } else {
                    removedThisPass = true
                    totalRemoved++
                    // Keep comments between the paths above the surviving path.
                    separator + first
                }
            }

            if (!removedThisPass) break
            current = replaced
        }

        return DuplicateRemovalResult(current, totalRemoved)
    }

    private fun areSafeExactDuplicates(first: String, second: String): Boolean {
        if (first.contains("<aapt:attr", ignoreCase = true) ||
            second.contains("<aapt:attr", ignoreCase = true)
        ) return false

        val firstPathData = attributeValue(first, "android:pathData")?.trim() ?: return false
        val secondPathData = attributeValue(second, "android:pathData")?.trim() ?: return false
        if (firstPathData != secondPathData) return false

        val firstAttributes = canonicalPathAttributes(first)
        val secondAttributes = canonicalPathAttributes(second)
        if (firstAttributes != secondAttributes) return false

        if (firstAttributes.keys.any { it.startsWith("trimpath") }) return false

        return hasOnlyFullyOpaquePaint(firstAttributes)
    }

    private fun hasOnlyFullyOpaquePaint(attributes: Map<String, String>): Boolean {
        fun alphaIsOne(name: String): Boolean {
            val raw = attributes[name] ?: return true
            return raw.toDoubleOrNull()?.let { kotlin.math.abs(it - 1.0) <= 1e-9 } == true
        }

        fun colorIsOpaque(raw: String?): Boolean {
            if (raw == null || isTransparentColor(raw)) return false
            val value = raw.trim()
            if (!value.startsWith("#")) return false
            val hex = value.substring(1)
            return when (hex.length) {
                6 -> true
                8 -> hex.substring(0, 2).equals("FF", ignoreCase = true)
                else -> false
            }
        }

        val fill = attributes["fillcolor"]
        val stroke = attributes["strokecolor"]
        val fillDraws = fill != null && !isTransparentColor(fill)
        val strokeDraws = stroke != null && !isTransparentColor(stroke) &&
            ((attributes["strokewidth"]?.toDoubleOrNull() ?: 0.0) > 0.0)

        if (!fillDraws && !strokeDraws) return false
        if (!alphaIsOne("fillalpha") || !alphaIsOne("strokealpha")) return false
        if (fillDraws && !colorIsOpaque(fill)) return false
        if (strokeDraws && !colorIsOpaque(stroke)) return false
        return true
    }

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
    /**
     * A13.1: cost-aware compatible adjacent-path merging.
     *
     * A geometrically safe merge is now applied only when the canonicalized
     * merged representation is strictly smaller than the two original path
     * elements plus their separator.
     *
     * The comparison uses the same stable payload metric as A12: indentation
     * and blank presentation lines are ignored, while tags, attributes,
     * comments, and path data remain counted.
     */
    private fun mergeCompatibleAdjacentPaths(xml: String): PathMergingResult {
        var current = xml
        var totalMerged = 0
        val rejectedSignatures = mutableSetOf<String>()
        val opportunityStats = PathMergeOpportunityStats()
        val synthesizedPathData = linkedSetOf<String>()

        while (true) {
            var mergedThisPass = false

            val replaced = adjacentSimplePathRegex.replace(current) { match ->
                if (mergedThisPass) return@replace match.value

                val signature = stableFragmentSignature(match.value)
                if (signature in rejectedSignatures) {
                    return@replace match.value
                }

                val first = match.groupValues[1]
                val separator = match.groupValues[2]
                val second = match.groupValues[3]
                val merged = mergePathElements(first, second, opportunityStats)

                if (merged == null) {
                    return@replace match.value
                }

                // Preserve comments associated with the second element above
                // the merged element, matching the previous behavior.
                val mergedFragment = separator + merged

                // Canonicalize final numeric spelling before comparing costs.
                val canonicalOriginal =
                    canonicalizePathDecimalPrecision(match.value).xml
                val canonicalMerged =
                    canonicalizePathDecimalPrecision(mergedFragment).xml

                val originalCost = stableXmlPayloadCost(canonicalOriginal)
                val mergedCost = stableXmlPayloadCost(canonicalMerged)

                if (mergedCost >= originalCost) {
                    opportunityStats.rejectedForSize++
                    rejectedSignatures += signature
                    return@replace match.value
                }

                mergedThisPass = true
                totalMerged++
                attributeValue(merged, "android:pathData")?.let { mergedPath ->
                    synthesizedPathData += mergedPath
                    // Include the cheap decimal-only spelling as well because
                    // decimal canonicalization is the only path-local mutation
                    // that follows merging before the next full pass.
                    i2CanonicalizeDecimalTokensOnly(mergedPath)?.let {
                        synthesizedPathData += it
                    }
                }
                mergedFragment
            }

            if (!mergedThisPass) {
                break
            }
            current = replaced
        }

        return PathMergingResult(
            xml = current,
            mergedCount = totalMerged,
            preservedForSize = rejectedSignatures.size,
            opportunityStats = opportunityStats,
            synthesizedPathData = synthesizedPathData
        )
    }

    private fun mergePathElements(
        first: String,
        second: String,
        opportunityStats: PathMergeOpportunityStats
    ): String? {
        opportunityStats.pairsExamined++

        if (first.contains("<aapt:attr", ignoreCase = true) ||
            second.contains("<aapt:attr", ignoreCase = true)
        ) {
            opportunityStats.rejectedNestedPaint++
            return null
        }

        val firstPathData = attributeValue(first, "android:pathData")
        val secondPathData = attributeValue(second, "android:pathData")
        if (firstPathData == null || secondPathData == null) {
            opportunityStats.rejectedMissingPathData++
            return null
        }

        val firstAttributes = canonicalPathAttributes(first)
        val secondAttributes = canonicalPathAttributes(second)
        if (firstAttributes != secondAttributes) {
            opportunityStats.rejectedPaintMismatch++
            return null
        }
        opportunityStats.samePaintPairs++

        val firstBounds = compatiblePathBounds(firstPathData)
        val secondBounds = compatiblePathBounds(secondPathData)
        if (firstBounds == null || secondBounds == null) {
            opportunityStats.rejectedUnsupportedGeometry++
            return null
        }
        val strokeExpansion = sharedStrokeExpansion(firstAttributes)

        val expandedBoundsAreDisjoint =
            firstBounds.expanded(strokeExpansion)
                .isStrictlyDisjointFrom(secondBounds.expanded(strokeExpansion))

        // A6.2b: adjacent stroked subpaths commonly meet at an endpoint. Their
        // stroke-expanded bounds therefore overlap even though consolidating
        // them remains visually lossless when the paint is a fully opaque,
        // stroke-only channel. Keep the original strict-disjoint rule for any
        // fill or translucent paint, where combining subpaths can alter winding
        // or compositing.
        val safeOpaqueStrokeOnlyJoin =
            isFullyOpaqueStrokeOnly(firstAttributes) &&
                arcFlagsAreMergeCompatible(firstPathData, secondPathData)

        if (!expandedBoundsAreDisjoint && !safeOpaqueStrokeOnlyJoin) {
            opportunityStats.rejectedOverlapSafety++
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


    /**
     * A6.2b permits endpoint-touching consolidation only for a single fully
     * opaque stroke channel. Fill-bearing paths retain the older disjointness
     * requirement because combining their subpaths can change winding.
     */
    private fun isFullyOpaqueStrokeOnly(attributes: Map<String, String>): Boolean {
        val fill = attributes["fillcolor"]
        val fillDraws = fill != null && !isTransparentColor(fill)
        if (fillDraws) return false

        val stroke = attributes["strokecolor"] ?: return false
        if (isTransparentColor(stroke)) return false
        val strokeWidth = attributes["strokewidth"]?.toDoubleOrNull() ?: 0.0
        if (strokeWidth <= 0.0) return false

        val strokeAlpha = attributes["strokealpha"]?.toDoubleOrNull() ?: 1.0
        if (kotlin.math.abs(strokeAlpha - 1.0) > 1e-9) return false

        val value = stroke.trim()
        if (!value.startsWith("#")) return false
        val hex = value.substring(1)
        return when (hex.length) {
            6 -> true
            8 -> hex.substring(0, 2).equals("FF", ignoreCase = true)
            else -> false
        }
    }

    /**
     * When both candidates contain arcs, require their large-arc and sweep flag
     * sequences to agree. This preserves A6.2's conservative behavior for
     * geometrically different adjacent arc constructions while still allowing
     * matching arc runs and mixed line/arc runs to consolidate.
     */
    private fun arcFlagsAreMergeCompatible(
        firstPathData: String,
        secondPathData: String
    ): Boolean {
        val firstFlags = extractArcFlagPairs(firstPathData) ?: return false
        val secondFlags = extractArcFlagPairs(secondPathData) ?: return false
        if (firstFlags.isEmpty() || secondFlags.isEmpty()) return true
        return firstFlags == secondFlags
    }

    private fun extractArcFlagPairs(pathData: String): List<Pair<Int, Int>>? {
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        val result = mutableListOf<Pair<Int, Int>>()
        var index = 0
        var command: Char? = null

        fun parameterCount(command: Char): Int = when (command.lowercaseChar()) {
            'm', 'l', 't' -> 2
            'h', 'v' -> 1
            'c' -> 6
            's', 'q' -> 4
            'a' -> 7
            'z' -> 0
            else -> -1
        }

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                command = tokens[index][0]
                index++
                if (command!!.lowercaseChar() == 'z') {
                    command = null
                    continue
                }
            }

            val active = command ?: return null
            val count = parameterCount(active)
            if (count <= 0 || index + count > tokens.size) return null

            if (active.lowercaseChar() == 'a') {
                val large = tokens[index + 3].toDoubleOrNull() ?: return null
                val sweep = tokens[index + 4].toDoubleOrNull() ?: return null
                if (!large.isArcFlag() || !sweep.isArcFlag()) return null
                result += (if (large == 0.0) 0 else 1) to
                    (if (sweep == 0.0) 0 else 1)
            }

            index += count

            // Additional moveto coordinate pairs are implicit lineto pairs.
            if (active.lowercaseChar() == 'm') {
                command = if (active.isLowerCase()) 'l' else 'L'
            }
        }

        return result
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
            """(\bandroid:pathData\s*=\s*)(["'])(.*?)\2""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val match = regex.find(element) ?: return element
        val replacement =
            "${match.groupValues[1]}${match.groupValues[2]}$newPathData${match.groupValues[2]}"
        return element.replaceRange(match.range, replacement)
    }

    /**
     * Returns conservative bounds for paths composed of:
     * M/L/H/V/Z plus cubic and quadratic Bézier commands C/S/Q/T.
     *
     * Bézier bounds use the control-point hull. A Bézier curve is always
     * contained by that hull, so these bounds may be larger than necessary but
     * are safe for proving that two rendered paths are disjoint.
     *
     * Elliptical arcs are supported with a conservative full-ellipse bound.
     * The bound is rotation-aware and always contains the rendered arc, though
     * it may be larger than the swept portion. That preserves merge safety.
     */
    private fun compatiblePathBounds(pathData: String): Bounds? {
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        if (tokens.isEmpty()) return null

        var index = 0
        var command: Char? = null
        var currentX = 0.0
        var currentY = 0.0
        var subpathX = 0.0
        var subpathY = 0.0
        var previousCommand: Char? = null
        var previousCubicControlX = 0.0
        var previousCubicControlY = 0.0
        var previousQuadraticControlX = 0.0
        var previousQuadraticControlY = 0.0

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

        fun hasNumbers(count: Int): Boolean {
            if (index + count > tokens.size) return false
            for (offset in 0 until count) {
                if (isCommand(tokens[index + offset])) return false
            }
            return true
        }

        fun number(): Double? {
            if (index >= tokens.size || isCommand(tokens[index])) return null
            return tokens[index++].toDoubleOrNull()
        }

        fun absoluteX(value: Double, relative: Boolean): Double =
            if (relative) currentX + value else value

        fun absoluteY(value: Double, relative: Boolean): Double =
            if (relative) currentY + value else value

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                command = tokens[index][0]
                index++
            }

            val active = command ?: return null
            val lower = active.lowercaseChar()
            val relative = active.isLowerCase()

            when (lower) {
                'm' -> {
                    if (!hasNumbers(2)) return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x = absoluteX(rawX, relative)
                    val y = absoluteY(rawY, relative)
                    currentX = x
                    currentY = y
                    subpathX = x
                    subpathY = y
                    include(x, y)
                    previousCommand = active
                    command = if (relative) 'l' else 'L'
                }

                'l' -> {
                    if (!hasNumbers(2)) return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    include(currentX, currentY)
                    currentX = absoluteX(rawX, relative)
                    currentY = absoluteY(rawY, relative)
                    include(currentX, currentY)
                    previousCommand = active
                }

                'h' -> {
                    if (!hasNumbers(1)) return null
                    val rawX = number() ?: return null
                    include(currentX, currentY)
                    currentX = absoluteX(rawX, relative)
                    include(currentX, currentY)
                    previousCommand = active
                }

                'v' -> {
                    if (!hasNumbers(1)) return null
                    val rawY = number() ?: return null
                    include(currentX, currentY)
                    currentY = absoluteY(rawY, relative)
                    include(currentX, currentY)
                    previousCommand = active
                }

                'c' -> {
                    if (!hasNumbers(6)) return null
                    val startX = currentX
                    val startY = currentY
                    val rawX1 = number() ?: return null
                    val rawY1 = number() ?: return null
                    val rawX2 = number() ?: return null
                    val rawY2 = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null

                    val x1 = if (relative) startX + rawX1 else rawX1
                    val y1 = if (relative) startY + rawY1 else rawY1
                    val x2 = if (relative) startX + rawX2 else rawX2
                    val y2 = if (relative) startY + rawY2 else rawY2
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(x1, y1)
                    include(x2, y2)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousCubicControlX = x2
                    previousCubicControlY = y2
                    previousCommand = active
                }

                's' -> {
                    if (!hasNumbers(4)) return null
                    val startX = currentX
                    val startY = currentY
                    val reflectedX =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('c', 's'))
                            2.0 * startX - previousCubicControlX
                        else startX
                    val reflectedY =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('c', 's'))
                            2.0 * startY - previousCubicControlY
                        else startY

                    val rawX2 = number() ?: return null
                    val rawY2 = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x2 = if (relative) startX + rawX2 else rawX2
                    val y2 = if (relative) startY + rawY2 else rawY2
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(reflectedX, reflectedY)
                    include(x2, y2)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousCubicControlX = x2
                    previousCubicControlY = y2
                    previousCommand = active
                }

                'q' -> {
                    if (!hasNumbers(4)) return null
                    val startX = currentX
                    val startY = currentY
                    val rawX1 = number() ?: return null
                    val rawY1 = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x1 = if (relative) startX + rawX1 else rawX1
                    val y1 = if (relative) startY + rawY1 else rawY1
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(x1, y1)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousQuadraticControlX = x1
                    previousQuadraticControlY = y1
                    previousCommand = active
                }

                't' -> {
                    if (!hasNumbers(2)) return null
                    val startX = currentX
                    val startY = currentY
                    val reflectedX =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('q', 't'))
                            2.0 * startX - previousQuadraticControlX
                        else startX
                    val reflectedY =
                        if (previousCommand != null && previousCommand.lowercaseChar() in charArrayOf('q', 't'))
                            2.0 * startY - previousQuadraticControlY
                        else startY
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    include(startX, startY)
                    include(reflectedX, reflectedY)
                    include(x, y)

                    currentX = x
                    currentY = y
                    previousQuadraticControlX = reflectedX
                    previousQuadraticControlY = reflectedY
                    previousCommand = active
                }

                'z' -> {
                    include(currentX, currentY)
                    include(subpathX, subpathY)
                    currentX = subpathX
                    currentY = subpathY
                    previousCommand = active
                    command = null
                }

                'a' -> {
                    if (!hasNumbers(7)) return null
                    val startX = currentX
                    val startY = currentY
                    val rawRx = number() ?: return null
                    val rawRy = number() ?: return null
                    val rotationDegrees = number() ?: return null
                    val largeArcFlag = number() ?: return null
                    val sweepFlag = number() ?: return null
                    val rawX = number() ?: return null
                    val rawY = number() ?: return null
                    val x = if (relative) startX + rawX else rawX
                    val y = if (relative) startY + rawY else rawY

                    if (!largeArcFlag.isArcFlag() || !sweepFlag.isArcFlag()) {
                        return null
                    }

                    include(startX, startY)
                    include(x, y)

                    val arcBounds = conservativeArcBounds(
                        startX = startX,
                        startY = startY,
                        endX = x,
                        endY = y,
                        radiusX = rawRx,
                        radiusY = rawRy,
                        rotationDegrees = rotationDegrees,
                        largeArc = largeArcFlag != 0.0,
                        sweep = sweepFlag != 0.0
                    ) ?: return null

                    include(arcBounds.minX, arcBounds.minY)
                    include(arcBounds.maxX, arcBounds.maxY)

                    currentX = x
                    currentY = y
                    previousCommand = active
                }

                else -> return null
            }
        }

        return if (hasPoint) Bounds(minX, minY, maxX, maxY) else null
    }


    /**
     * A6.2: returns a conservative axis-aligned bound for one SVG elliptical
     * arc. Rather than solving the swept-angle extrema, this bounds the entire
     * transformed ellipse. It can reject an otherwise-safe merge, but it can
     * never approve a merge because the arc was underestimated.
     */
    private fun conservativeArcBounds(
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        radiusX: Double,
        radiusY: Double,
        rotationDegrees: Double,
        largeArc: Boolean,
        sweep: Boolean
    ): Bounds? {
        var rx = abs(radiusX)
        var ry = abs(radiusY)

        // SVG treats a zero-radius arc as a straight line. The caller already
        // includes both endpoints, so their line bounds are sufficient.
        if (rx == 0.0 || ry == 0.0 ||
            (startX == endX && startY == endY)
        ) {
            return Bounds(
                minOf(startX, endX),
                minOf(startY, endY),
                maxOf(startX, endX),
                maxOf(startY, endY)
            )
        }

        val phi = Math.toRadians(rotationDegrees % 360.0)
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val dx = (startX - endX) / 2.0
        val dy = (startY - endY) / 2.0
        val xPrime = cosPhi * dx + sinPhi * dy
        val yPrime = -sinPhi * dx + cosPhi * dy

        // SVG 1.1 F.6.6: enlarge radii when the requested ellipse cannot span
        // the endpoints. This is essential for a bound that contains the arc.
        val radiiScale =
            (xPrime * xPrime) / (rx * rx) +
                (yPrime * yPrime) / (ry * ry)
        if (radiiScale > 1.0) {
            val scale = sqrt(radiiScale)
            rx *= scale
            ry *= scale
        }

        val rxSquared = rx * rx
        val rySquared = ry * ry
        val xPrimeSquared = xPrime * xPrime
        val yPrimeSquared = yPrime * yPrime
        val denominator =
            rxSquared * yPrimeSquared + rySquared * xPrimeSquared

        if (denominator == 0.0) return null

        val numerator =
            (rxSquared * rySquared -
                rxSquared * yPrimeSquared -
                rySquared * xPrimeSquared).coerceAtLeast(0.0)
        val sign = if (largeArc == sweep) -1.0 else 1.0
        val coefficient = sign * sqrt(numerator / denominator)
        val centerPrimeX = coefficient * (rx * yPrime / ry)
        val centerPrimeY = coefficient * (-ry * xPrime / rx)
        val centerX =
            cosPhi * centerPrimeX - sinPhi * centerPrimeY +
                (startX + endX) / 2.0
        val centerY =
            sinPhi * centerPrimeX + cosPhi * centerPrimeY +
                (startY + endY) / 2.0

        // Axis-aligned extents of a rotated ellipse. Bounding the entire ellipse
        // is conservative for either sweep direction and either arc choice.
        val extentX = sqrt(
            rxSquared * cosPhi * cosPhi +
                rySquared * sinPhi * sinPhi
        )
        val extentY = sqrt(
            rxSquared * sinPhi * sinPhi +
                rySquared * cosPhi * cosPhi
        )

        if (!centerX.isFinite() || !centerY.isFinite() ||
            !extentX.isFinite() || !extentY.isFinite()
        ) {
            return null
        }

        return Bounds(
            centerX - extentX,
            centerY - extentY,
            centerX + extentX,
            centerY + extentY
        )
    }

    private fun Double.isArcFlag(): Boolean = this == 0.0 || this == 1.0

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


    data class CommandNumericDifferentialWitness(
        val caseIndex: Int,
        val sourcePathData: String,
        val firstRoundPathData: String,
        val secondRoundPathData: String
    ) {
        val additionalCharactersSaved: Int
            get() = firstRoundPathData.length - secondRoundPathData.length
    }

    data class CommandNumericDifferentialSearchResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val invalidGeneratedCases: Int,
        val secondRoundImprovements: Int,
        val equalLengthDifferences: Int,
        val semanticMismatchCount: Int,
        val elapsedNanos: Long,
        val witnesses: List<CommandNumericDifferentialWitness>
    ) {
        val elapsedMilliseconds: Double
            get() = elapsedNanos / 1_000_000.0

        val supportsKeepingF43: Boolean
            get() = secondRoundImprovements > 0

        fun toPlainTextReport(): String = buildString {
            appendLine("F4.3 command/numeric differential stress search")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $invalidGeneratedCases")
            appendLine("Semantic mismatches: $semanticMismatchCount")
            appendLine("Equal-length spelling differences: $equalLengthDifferences")
            appendLine("Second-round strict improvements: $secondRoundImprovements")
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsedMilliseconds)
            )
            appendLine()

            when {
                semanticMismatchCount > 0 -> {
                    appendLine("RESULT: semantic mismatches were detected.")
                    appendLine(
                        "Recommendation: investigate the mismatches before making " +
                            "an F4.3 keep/remove decision."
                    )
                }

                secondRoundImprovements > 0 -> {
                    appendLine("RESULT: F4.3 found genuine additional savings.")
                    appendLine("Recommendation: keep the joint fixed-point pass.")
                }

                else -> {
                    appendLine("RESULT: no second-round strict reduction was found.")
                    appendLine(
                        "Recommendation: F4.3 appears redundant for this search space; " +
                            "consider removing it after repeating with larger case counts/seeds."
                    )
                }
            }

            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseIndex}")
                    appendLine(
                        "   Additional characters saved: " +
                            witness.additionalCharactersSaved
                    )
                    appendLine("   Source: ${witness.sourcePathData}")
                    appendLine("   F3.1 → F4.2: ${witness.firstRoundPathData}")
                    appendLine("   Second round: ${witness.secondRoundPathData}")
                }
            }
        }
    }

    /**
     * Differentially tests whether a second F3.1 → F4.2 round can produce a
     * strict reduction after the first round has completed.
     *
     * The generator is deterministic for a supplied seed. Every candidate is
     * parsed before comparison, and both outputs must preserve the same
     * normalized segment sequence as the generated source.
     */
    fun runCommandNumericDifferentialStressSearch(
        caseCount: Int = 50_000,
        seed: Long = 0xF432_2026L,
        maximumWitnesses: Int = 20
    ): CommandNumericDifferentialSearchResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) {
            "maximumWitnesses must be non-negative"
        }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<CommandNumericDifferentialWitness>()

        var generatedCases = 0
        var validCases = 0
        var invalidGeneratedCases = 0
        var secondRoundImprovements = 0
        var equalLengthDifferences = 0
        var semanticMismatchCount = 0

        repeat(caseCount) { caseIndex ->
            generatedCases++
            val source = generateDifferentialStressPath(random)
            val sourceSegments = canonicalPathSemantics(source)

            if (sourceSegments == null || sourceSegments.isEmpty()) {
                invalidGeneratedCases++
                return@repeat
            }

            val firstCommand =
                globallyMinimizeCommandSequence(source).pathData
            val firstRound =
                globallyOptimizeNumericSerialization(firstCommand).pathData
            val secondCommand =
                globallyMinimizeCommandSequence(firstRound).pathData
            val secondRound =
                globallyOptimizeNumericSerialization(secondCommand).pathData

            val firstSegments = canonicalPathSemantics(firstRound)
            val secondSegments = canonicalPathSemantics(secondRound)

            if (
                firstSegments != sourceSegments ||
                secondSegments != sourceSegments
            ) {
                semanticMismatchCount++
                return@repeat
            }

            validCases++

            when {
                secondRound.length < firstRound.length -> {
                    secondRoundImprovements++
                    if (witnesses.size < maximumWitnesses) {
                        witnesses += CommandNumericDifferentialWitness(
                            caseIndex = caseIndex + 1,
                            sourcePathData = source,
                            firstRoundPathData = firstRound,
                            secondRoundPathData = secondRound
                        )
                    }
                }

                secondRound.length == firstRound.length &&
                    secondRound != firstRound -> {
                    equalLengthDifferences++
                }
            }
        }

        return CommandNumericDifferentialSearchResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generatedCases,
            validCases = validCases,
            invalidGeneratedCases = invalidGeneratedCases,
            secondRoundImprovements = secondRoundImprovements,
            equalLengthDifferences = equalLengthDifferences,
            semanticMismatchCount = semanticMismatchCount,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }

    data class PostScaleDifferentialWitness(
        val caseIndex: Int,
        val scaleFactor: String,
        val pivotX: String,
        val pivotY: String,
        val translateX: String,
        val translateY: String,
        val sourcePathData: String,
        val rawScaledPathData: String,
        val fullPathData: String,
        val narrowedPathData: String,
        val geometryMismatch: Boolean
    )

    data class PostScaleDifferentialSearchResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val invalidGeneratedCases: Int,
        val byteIdenticalCount: Int,
        val byteDifferenceCount: Int,
        val canonicalDifferenceCount: Int,
        val geometryMismatchCount: Int,
        val equalLengthDifferenceCount: Int,
        val fullShorterCount: Int,
        val narrowedShorterCount: Int,
        val elapsedNanos: Long,
        val witnesses: List<PostScaleDifferentialWitness>
    ) {
        val elapsedMilliseconds: Double
            get() = elapsedNanos / 1_000_000.0

        fun toPlainTextReport(): String = buildString {
            appendLine("G2.25 full vs narrowed post-scale differential stress search")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $invalidGeneratedCases")
            appendLine("Byte-identical results: $byteIdenticalCount")
            appendLine("Byte differences: $byteDifferenceCount")
            appendLine("Canonical representation differences: $canonicalDifferenceCount")
            appendLine("Sampled geometry mismatches: $geometryMismatchCount")
            appendLine("Equal-length spelling differences: $equalLengthDifferenceCount")
            appendLine("Full optimizer shorter: $fullShorterCount")
            appendLine("Narrowed optimizer shorter: $narrowedShorterCount")
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsedMilliseconds)
            )
            appendLine()

            when {
                geometryMismatchCount > 0 -> {
                    appendLine("RESULT: sampled geometry mismatches were detected.")
                    appendLine(
                        "Recommendation: do not activate the narrowed production pipeline. " +
                            "Investigate every geometry mismatch first."
                    )
                }

                byteDifferenceCount > 0 -> {
                    appendLine("RESULT: geometry matched, but byte differences were found.")
                    appendLine(
                        "Recommendation: keep the full optimizer authoritative until the " +
                            "serialization differences are understood."
                    )
                }

                else -> {
                    appendLine("RESULT: full and narrowed post-scale outputs were byte-identical.")
                    appendLine(
                        "Recommendation: the narrowed pipeline is a strong candidate for a " +
                            "guarded production trial after the locked regression suite."
                    )
                }
            }

            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseIndex}")
                    appendLine("   Geometry mismatch: ${witness.geometryMismatch}")
                    appendLine("   Scale: ${witness.scaleFactor}")
                    appendLine("   Pivot: ${witness.pivotX},${witness.pivotY}")
                    appendLine("   Translate: ${witness.translateX},${witness.translateY}")
                    appendLine("   Source: ${witness.sourcePathData}")
                    appendLine("   Raw scaled: ${witness.rawScaledPathData}")
                    appendLine("   Full: ${witness.fullPathData}")
                    appendLine("   Narrowed: ${witness.narrowedPathData}")
                }
            }
        }
    }

    data class IdempotencePathReuseDifferentialWitness(
        val caseIndex: Int,
        val sourceXml: String,
        val firstPassXml: String,
        val independentSecondPassXml: String,
        val reusedSecondPassXml: String,
        val independentIdempotent: Boolean,
        val reusedIdempotent: Boolean,
        val stablePathsRegistered: Int,
        val stableOutputHits: Int
    )

    data class IdempotencePathReuseDifferentialSearchResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val byteIdenticalSecondPasses: Int,
        val byteDifferentSecondPasses: Int,
        val idempotenceDecisionMismatches: Int,
        val independentIdempotentCount: Int,
        val reuseIdempotentCount: Int,
        val stablePathsRegistered: Int,
        val stableOutputHits: Int,
        val independentSecondPassNanos: Long,
        val reusedSecondPassNanos: Long,
        val elapsedNanos: Long,
        val witnesses: List<IdempotencePathReuseDifferentialWitness>
    ) {
        val elapsedMilliseconds: Double
            get() = elapsedNanos / 1_000_000.0

        fun toPlainTextReport(): String = buildString {
            appendLine("G3.4 stable-path reuse differential stress search")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("Byte-identical second passes: $byteIdenticalSecondPasses")
            appendLine("Byte-different second passes: $byteDifferentSecondPasses")
            appendLine("Idempotence decision mismatches: $idempotenceDecisionMismatches")
            appendLine("Independent pass reported idempotent: $independentIdempotentCount")
            appendLine("Reuse pass reported idempotent: $reuseIdempotentCount")
            appendLine("Final pass-1 stable paths registered: $stablePathsRegistered")
            appendLine("Stable-output cache hits: $stableOutputHits")
            appendLine(
                "Independent second-pass time: " +
                    String.format(java.util.Locale.US, "%.2f ms", independentSecondPassNanos / 1_000_000.0)
            )
            appendLine(
                "Reuse second-pass time: " +
                    String.format(java.util.Locale.US, "%.2f ms", reusedSecondPassNanos / 1_000_000.0)
            )
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsedMilliseconds)
            )
            appendLine()

            if (byteDifferentSecondPasses == 0 && idempotenceDecisionMismatches == 0) {
                appendLine("RESULT: stable-path reuse matched independent recomputation exactly.")
                appendLine(
                    "Recommendation: G3.3 is a strong candidate for permanent retention after " +
                        "the locked regression suite remains clean."
                )
            } else {
                appendLine("RESULT: stable-path reuse diverged from independent recomputation.")
                appendLine(
                    "Recommendation: do not make G3.3 permanent. Investigate every witness first."
                )
            }

            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseIndex}")
                    appendLine("   Independent idempotent: ${witness.independentIdempotent}")
                    appendLine("   Reuse idempotent: ${witness.reusedIdempotent}")
                    appendLine("   Stable paths registered: ${witness.stablePathsRegistered}")
                    appendLine("   Stable-output hits: ${witness.stableOutputHits}")
                    appendLine("   Source XML: ${witness.sourceXml.replace('\n', ' ')}")
                    appendLine("   First pass: ${witness.firstPassXml.replace('\n', ' ')}")
                    appendLine("   Independent second pass: ${witness.independentSecondPassXml.replace('\n', ' ')}")
                    appendLine("   Reuse second pass: ${witness.reusedSecondPassXml.replace('\n', ' ')}")
                }
            }
        }
    }

    /**
     * G3.4 Developer Tools diagnostic. Production conversion never calls this.
     *
     * Each generated VectorDrawable runs one common first pass. The resulting
     * pass-1 XML is then validated two ways:
     * 1) a fresh cache forces independent path recomputation on pass 2;
     * 2) the G3.3 final-pass stable-output cache is allowed to serve pass 2.
     *
     * Exact second-pass XML and the idempotence verdict must agree.
     */
    fun runIdempotencePathReuseDifferentialStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6314_2026L,
        maximumWitnesses: Int = 12,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): IdempotencePathReuseDifferentialSearchResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<IdempotencePathReuseDifferentialWitness>()

        var generatedCases = 0
        var validCases = 0
        var rejectedGeneratedCases = 0
        var byteIdenticalSecondPasses = 0
        var byteDifferentSecondPasses = 0
        var idempotenceDecisionMismatches = 0
        var independentIdempotentCount = 0
        var reuseIdempotentCount = 0
        var stablePathsRegisteredTotal = 0
        var stableOutputHitsTotal = 0
        var independentSecondPassNanos = 0L
        var reusedSecondPassNanos = 0L

        repeat(caseCount) { caseIndex ->
            generatedCases++
            val sourceXml = generateIdempotenceDifferentialVectorXml(random)

            try {
                val reuseCache = PathOptimizationCache()
                val firstPass = optimizeVectorXmlSinglePass(
                    xml = sourceXml,
                    pathCache = reuseCache,
                    validationPass = false
                )
                val stableRegistered = registerFinalPassStablePaths(firstPass.xml, reuseCache)

                val independentStart = System.nanoTime()
                val independentSecondPass = optimizeVectorXmlSinglePass(
                    xml = firstPass.xml,
                    pathCache = PathOptimizationCache(),
                    validationPass = true
                )
                independentSecondPassNanos += System.nanoTime() - independentStart

                val stableHitsBefore = reuseCache.validationStableOutputHits
                val reuseStart = System.nanoTime()
                val reusedSecondPass = optimizeVectorXmlSinglePass(
                    xml = firstPass.xml,
                    pathCache = reuseCache,
                    validationPass = true
                )
                reusedSecondPassNanos += System.nanoTime() - reuseStart
                val stableHits = reuseCache.validationStableOutputHits - stableHitsBefore

                val independentIdempotent = independentSecondPass.xml == firstPass.xml
                val reusedIdempotent = reusedSecondPass.xml == firstPass.xml
                val byteIdentical = independentSecondPass.xml == reusedSecondPass.xml
                val decisionMismatch = independentIdempotent != reusedIdempotent

                validCases++
                stablePathsRegisteredTotal += stableRegistered
                stableOutputHitsTotal += stableHits
                if (independentIdempotent) independentIdempotentCount++
                if (reusedIdempotent) reuseIdempotentCount++
                if (byteIdentical) byteIdenticalSecondPasses++ else byteDifferentSecondPasses++
                if (decisionMismatch) idempotenceDecisionMismatches++

                if ((!byteIdentical || decisionMismatch) && witnesses.size < maximumWitnesses) {
                    witnesses += IdempotencePathReuseDifferentialWitness(
                        caseIndex = caseIndex + 1,
                        sourceXml = sourceXml,
                        firstPassXml = firstPass.xml,
                        independentSecondPassXml = independentSecondPass.xml,
                        reusedSecondPassXml = reusedSecondPass.xml,
                        independentIdempotent = independentIdempotent,
                        reusedIdempotent = reusedIdempotent,
                        stablePathsRegistered = stableRegistered,
                        stableOutputHits = stableHits
                    )
                }
            } catch (_: Throwable) {
                rejectedGeneratedCases++
            }

            val processedCases = caseIndex + 1
            if (
                progressCallback != null &&
                (processedCases % 250 == 0 || processedCases == caseCount)
            ) {
                progressCallback(processedCases)
            }
        }

        if (caseCount == 0) {
            progressCallback?.invoke(0)
        }

        return IdempotencePathReuseDifferentialSearchResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generatedCases,
            validCases = validCases,
            rejectedGeneratedCases = rejectedGeneratedCases,
            byteIdenticalSecondPasses = byteIdenticalSecondPasses,
            byteDifferentSecondPasses = byteDifferentSecondPasses,
            idempotenceDecisionMismatches = idempotenceDecisionMismatches,
            independentIdempotentCount = independentIdempotentCount,
            reuseIdempotentCount = reuseIdempotentCount,
            stablePathsRegistered = stablePathsRegisteredTotal,
            stableOutputHits = stableOutputHitsTotal,
            independentSecondPassNanos = independentSecondPassNanos,
            reusedSecondPassNanos = reusedSecondPassNanos,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }


    data class FinalCommandConvergenceWitness(
        val caseNumber: Int,
        val source: String,
        val firstPass: String,
        val convergencePass: String,
        val independentSecondPass: String,
        val verificationPass: String,
        val convergenceChangedFirstPass: Boolean,
        val convergenceMatchedIndependentSecondPass: Boolean,
        val convergenceWasFixedPoint: Boolean,
        val semanticMismatch: Boolean,
        val characterDelta: Int
    )

    data class FinalCommandConvergenceResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val unchangedByConvergence: Int,
        val changedByConvergence: Int,
        val matchedIndependentSecondPass: Int,
        val differedFromIndependentSecondPass: Int,
        val fixedAfterConvergence: Int,
        val stillChangedAfterVerification: Int,
        val semanticMismatchCount: Int,
        val totalCharactersSaved: Long,
        val totalCharactersGrown: Long,
        val convergenceNanos: Long,
        val verificationNanos: Long,
        val elapsedNanos: Long,
        val witnesses: List<FinalCommandConvergenceWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.6 final-command convergence investigation")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("Unchanged by convergence pass: $unchangedByConvergence")
            appendLine("Changed by convergence pass: $changedByConvergence")
            appendLine("Matched independent full pass 2: $matchedIndependentSecondPass")
            appendLine("Differed from independent full pass 2: $differedFromIndependentSecondPass")
            appendLine("Fixed after convergence: $fixedAfterConvergence")
            appendLine("Still changed after verification: $stillChangedAfterVerification")
            appendLine("Semantic mismatches: $semanticMismatchCount")
            appendLine("Characters saved by convergence: $totalCharactersSaved")
            appendLine("Characters added by convergence: $totalCharactersGrown")
            appendLine("Convergence time: " + String.format(java.util.Locale.US, "%.2f ms", convergenceNanos / 1_000_000.0))
            appendLine("Verification time: " + String.format(java.util.Locale.US, "%.2f ms", verificationNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            appendLine()
            when {
                semanticMismatchCount > 0 -> {
                    appendLine("RESULT: G3.6 produced semantic mismatches.")
                    appendLine("Recommendation: reject the convergence candidate and investigate every mismatch witness.")
                }
                stillChangedAfterVerification > 0 -> {
                    appendLine("RESULT: the final-command convergence candidate was not a fixed point for every case.")
                    appendLine("Recommendation: keep production unchanged and inspect the remaining verification witnesses.")
                }
                differedFromIndependentSecondPass > 0 -> {
                    appendLine("RESULT: the candidate reached a fixed point but did not always reproduce the independent second-pass optimizer output.")
                    appendLine("Recommendation: do not activate it yet; investigate the spelling differences before deciding whether they are acceptable alternatives.")
                }
                else -> {
                    appendLine("RESULT: the final-command convergence candidate exactly reproduced the independent second pass and was fixed under full verification.")
                    appendLine("Recommendation: proceed to a guarded VectorDrawable-level production trial before activation.")
                }
            }
            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   Convergence changed pass 1: ${witness.convergenceChangedFirstPass}")
                    appendLine("   Matched independent pass 2: ${witness.convergenceMatchedIndependentSecondPass}")
                    appendLine("   Fixed under verification: ${witness.convergenceWasFixedPoint}")
                    appendLine("   Semantic mismatch: ${witness.semanticMismatch}")
                    appendLine("   Character delta (pass1 - convergence): ${witness.characterDelta}")
                    appendLine("   Source: ${witness.source}")
                    appendLine("   Pass 1: ${witness.firstPass}")
                    appendLine("   G3.6 convergence: ${witness.convergencePass}")
                    appendLine("   Independent pass 2: ${witness.independentSecondPass}")
                    appendLine("   Verification pass: ${witness.verificationPass}")
                }
            }
        }
    }

    /**
     * G3.6 Developer Tools diagnostic. Production conversion never calls this.
     *
     * The candidate deliberately reruns only the syntax/command serialization tail
     * that G3.5 identified as the source of pass-2 drift. It is then compared with
     * an independent full second pass and finally verified by one full optimization
     * from the candidate output.
     */
    fun runFinalCommandConvergenceStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumWitnesses: Int = 8,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): FinalCommandConvergenceResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<FinalCommandConvergenceWitness>()
        var generated = 0
        var valid = 0
        var rejected = 0
        var unchanged = 0
        var changed = 0
        var matchedIndependent = 0
        var differedIndependent = 0
        var fixed = 0
        var stillChanged = 0
        var semanticMismatches = 0
        var charactersSaved = 0L
        var charactersGrown = 0L
        var convergenceNanos = 0L
        var verificationNanos = 0L

        repeat(caseCount) { caseIndex ->
            generated++
            val source = generateDifferentialStressPath(random)
            try {
                val first = optimizePathData(source).pathData

                val convergenceStart = System.nanoTime()
                val convergence = runFinalCommandConvergenceCandidate(first)
                convergenceNanos += System.nanoTime() - convergenceStart

                val independentSecond = optimizePathData(first).pathData

                val verificationStart = System.nanoTime()
                val verification = optimizePathData(convergence).pathData
                verificationNanos += System.nanoTime() - verificationStart

                val convergenceChanged = convergence != first
                val matched = convergence == independentSecond
                val isFixed = verification == convergence
                val firstSemantics = canonicalPathSemantics(first)
                val convergenceSemantics = canonicalPathSemantics(convergence)
                val semanticMismatch = firstSemantics == null ||
                    convergenceSemantics == null ||
                    firstSemantics != convergenceSemantics
                val delta = first.length - convergence.length

                valid++
                if (convergenceChanged) changed++ else unchanged++
                if (matched) matchedIndependent++ else differedIndependent++
                if (isFixed) fixed++ else stillChanged++
                if (semanticMismatch) semanticMismatches++
                if (delta > 0) charactersSaved += delta.toLong()
                if (delta < 0) charactersGrown += (-delta).toLong()

                val noteworthy = semanticMismatch || !isFixed || !matched || convergenceChanged
                if (noteworthy && witnesses.size < maximumWitnesses) {
                    witnesses += FinalCommandConvergenceWitness(
                        caseNumber = caseIndex + 1,
                        source = source,
                        firstPass = first,
                        convergencePass = convergence,
                        independentSecondPass = independentSecond,
                        verificationPass = verification,
                        convergenceChangedFirstPass = convergenceChanged,
                        convergenceMatchedIndependentSecondPass = matched,
                        convergenceWasFixedPoint = isFixed,
                        semanticMismatch = semanticMismatch,
                        characterDelta = delta
                    )
                }
            } catch (_: Throwable) {
                rejected++
            }

            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        return FinalCommandConvergenceResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            unchangedByConvergence = unchanged,
            changedByConvergence = changed,
            matchedIndependentSecondPass = matchedIndependent,
            differedFromIndependentSecondPass = differedIndependent,
            fixedAfterConvergence = fixed,
            stillChangedAfterVerification = stillChanged,
            semanticMismatchCount = semanticMismatches,
            totalCharactersSaved = charactersSaved,
            totalCharactersGrown = charactersGrown,
            convergenceNanos = convergenceNanos,
            verificationNanos = verificationNanos,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }

    private fun runFinalCommandConvergenceCandidate(pathData: String): String {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) return pathData.trim()

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return pathData
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) return pathData

        val normalized = StringBuilder(pathData.length)
        var activeCommand: Char? = null
        var previousWasNumber = false
        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                val implicitRepeat =
                    activeCommand == command && command !in charArrayOf('M', 'm', 'Z', 'z')
                if (!implicitRepeat) {
                    normalized.append(command)
                    previousWasNumber = false
                }
                activeCommand = command
            } else {
                val value = token.toBigDecimalOrNull()
                    ?.let(::formatPathNumber)
                    ?: normalizeNumber(token)
                if (previousWasNumber) normalized.append(',')
                normalized.append(value)
                previousWasNumber = true
            }
        }

        val local = shortenPathCommands(normalized.toString(), null).pathData
        val global = globallyMinimizeCommandSequence(local, null).pathData
        return globallyOptimizeNumericSerialization(global).pathData
    }

    data class PostSerializationGeometryConvergenceWitness(
        val caseNumber: Int,
        val source: String,
        val firstPass: String,
        val candidatePass: String,
        val independentSecondPass: String,
        val verificationPass: String,
        val redundantGeometryChanged: Boolean,
        val collinearGeometryChanged: Boolean,
        val candidateChangedFirstPass: Boolean,
        val candidateMatchedIndependentSecondPass: Boolean,
        val candidateWasFixedPoint: Boolean,
        val sampledGeometryMismatch: Boolean,
        val characterDelta: Int
    )

    data class PostSerializationGeometryConvergenceResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val unchangedByCandidate: Int,
        val changedByCandidate: Int,
        val redundantGeometryChangedCases: Int,
        val collinearGeometryChangedCases: Int,
        val matchedIndependentSecondPass: Int,
        val differedFromIndependentSecondPass: Int,
        val fixedAfterCandidate: Int,
        val stillChangedAfterVerification: Int,
        val sampledGeometryMismatchCount: Int,
        val totalCharactersSaved: Long,
        val totalCharactersGrown: Long,
        val candidateNanos: Long,
        val verificationNanos: Long,
        val elapsedNanos: Long,
        val witnesses: List<PostSerializationGeometryConvergenceWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.7 post-serialization geometry convergence investigation")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("Unchanged by G3.7 candidate: $unchangedByCandidate")
            appendLine("Changed by G3.7 candidate: $changedByCandidate")
            appendLine("Redundant-geometry stage changed: $redundantGeometryChangedCases")
            appendLine("Collinear-consolidation stage changed: $collinearGeometryChangedCases")
            appendLine("Matched independent full pass 2: $matchedIndependentSecondPass")
            appendLine("Differed from independent full pass 2: $differedFromIndependentSecondPass")
            appendLine("Fixed after G3.7 candidate: $fixedAfterCandidate")
            appendLine("Still changed after verification: $stillChangedAfterVerification")
            appendLine("Sampled geometry mismatches: $sampledGeometryMismatchCount")
            appendLine("Characters saved by G3.7 candidate: $totalCharactersSaved")
            appendLine("Characters added by G3.7 candidate: $totalCharactersGrown")
            appendLine("Candidate time: " + String.format(java.util.Locale.US, "%.2f ms", candidateNanos / 1_000_000.0))
            appendLine("Verification time: " + String.format(java.util.Locale.US, "%.2f ms", verificationNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            appendLine()
            when {
                sampledGeometryMismatchCount > 0 -> {
                    appendLine("RESULT: G3.7 produced sampled geometry mismatches.")
                    appendLine("Recommendation: reject the candidate and investigate every geometry witness.")
                }
                stillChangedAfterVerification > 0 -> {
                    appendLine("RESULT: the G3.7 candidate did not reach a fixed point for every case.")
                    appendLine("Recommendation: keep production unchanged and inspect the remaining verification witnesses.")
                }
                differedFromIndependentSecondPass > 0 -> {
                    appendLine("RESULT: the G3.7 candidate was fixed but did not exactly reproduce every independent second-pass result.")
                    appendLine("Recommendation: keep production unchanged until the remaining spelling differences are classified.")
                }
                validCases > 0 -> {
                    appendLine("RESULT: the G3.7 candidate exactly reproduced the independent second pass and remained fixed under full verification.")
                    appendLine("Recommendation: rerun the locked regression suite, then advance to a guarded VectorDrawable-level production trial.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }
            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   Redundant geometry changed: ${witness.redundantGeometryChanged}")
                    appendLine("   Collinear geometry changed: ${witness.collinearGeometryChanged}")
                    appendLine("   Candidate changed pass 1: ${witness.candidateChangedFirstPass}")
                    appendLine("   Matched independent pass 2: ${witness.candidateMatchedIndependentSecondPass}")
                    appendLine("   Fixed under verification: ${witness.candidateWasFixedPoint}")
                    appendLine("   Sampled geometry mismatch: ${witness.sampledGeometryMismatch}")
                    appendLine("   Character delta (pass1 - candidate): ${witness.characterDelta}")
                    appendLine("   Source: ${witness.source}")
                    appendLine("   Pass 1: ${witness.firstPass}")
                    appendLine("   G3.7 candidate: ${witness.candidatePass}")
                    appendLine("   Independent pass 2: ${witness.independentSecondPass}")
                    appendLine("   Verification pass: ${witness.verificationPass}")
                }
            }
        }
    }

    /**
     * G3.7 Developer Tools diagnostic. Production conversion never calls this.
     *
     * G3.6 showed that the remaining pass-2 drift was dominated by geometry that
     * becomes newly eligible only after pass-1 command serialization is reparsed
     * (for example consecutive H/V segments). G3.7 therefore reparses pass 1,
     * reruns only the proven-lossless redundant non-drawing and exact-collinear
     * geometry stages, then reruns the command/serialization tail.
     */
    fun runPostSerializationGeometryConvergenceStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumWitnesses: Int = 8,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): PostSerializationGeometryConvergenceResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<PostSerializationGeometryConvergenceWitness>()
        var generated = 0
        var valid = 0
        var rejected = 0
        var unchanged = 0
        var changed = 0
        var redundantChanged = 0
        var collinearChanged = 0
        var matchedIndependent = 0
        var differedIndependent = 0
        var fixed = 0
        var stillChanged = 0
        var sampledGeometryMismatches = 0
        var charactersSaved = 0L
        var charactersGrown = 0L
        var candidateNanos = 0L
        var verificationNanos = 0L

        repeat(caseCount) { caseIndex ->
            generated++
            val source = generateDifferentialStressPath(random)
            try {
                val first = optimizePathData(source).pathData

                val candidateStart = System.nanoTime()
                val candidate = runPostSerializationGeometryConvergenceCandidate(first)
                candidateNanos += System.nanoTime() - candidateStart

                val independentSecond = optimizePathData(first).pathData

                val verificationStart = System.nanoTime()
                val verification = optimizePathData(candidate.pathData).pathData
                verificationNanos += System.nanoTime() - verificationStart

                val candidateChanged = candidate.pathData != first
                val matched = candidate.pathData == independentSecond
                val isFixed = verification == candidate.pathData
                val sampledGeometryMismatch =
                    candidateChanged && !sampledPathGeometryEquivalent(first, candidate.pathData)
                val delta = first.length - candidate.pathData.length

                valid++
                if (candidateChanged) changed++ else unchanged++
                if (candidate.redundantGeometryChanged) redundantChanged++
                if (candidate.collinearGeometryChanged) collinearChanged++
                if (matched) matchedIndependent++ else differedIndependent++
                if (isFixed) fixed++ else stillChanged++
                if (sampledGeometryMismatch) sampledGeometryMismatches++
                if (delta > 0) charactersSaved += delta.toLong()
                if (delta < 0) charactersGrown += (-delta).toLong()

                val noteworthy = sampledGeometryMismatch || !isFixed || !matched || candidateChanged
                if (noteworthy && witnesses.size < maximumWitnesses) {
                    witnesses += PostSerializationGeometryConvergenceWitness(
                        caseNumber = caseIndex + 1,
                        source = source,
                        firstPass = first,
                        candidatePass = candidate.pathData,
                        independentSecondPass = independentSecond,
                        verificationPass = verification,
                        redundantGeometryChanged = candidate.redundantGeometryChanged,
                        collinearGeometryChanged = candidate.collinearGeometryChanged,
                        candidateChangedFirstPass = candidateChanged,
                        candidateMatchedIndependentSecondPass = matched,
                        candidateWasFixedPoint = isFixed,
                        sampledGeometryMismatch = sampledGeometryMismatch,
                        characterDelta = delta
                    )
                }
            } catch (_: Throwable) {
                rejected++
            }

            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        return PostSerializationGeometryConvergenceResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            unchangedByCandidate = unchanged,
            changedByCandidate = changed,
            redundantGeometryChangedCases = redundantChanged,
            collinearGeometryChangedCases = collinearChanged,
            matchedIndependentSecondPass = matchedIndependent,
            differedFromIndependentSecondPass = differedIndependent,
            fixedAfterCandidate = fixed,
            stillChangedAfterVerification = stillChanged,
            sampledGeometryMismatchCount = sampledGeometryMismatches,
            totalCharactersSaved = charactersSaved,
            totalCharactersGrown = charactersGrown,
            candidateNanos = candidateNanos,
            verificationNanos = verificationNanos,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }


    // G3.14 diagnostic-only rerun of the G3.7 corpus with the G3.13 comparator.
    // Production conversion never calls this.
    data class G314PostSerializationGeometryConvergenceWitness(
        val caseNumber: Int,
        val source: String,
        val firstPass: String,
        val candidatePass: String,
        val independentSecondPass: String,
        val verificationPass: String,
        val redundantGeometryChanged: Boolean,
        val collinearGeometryChanged: Boolean,
        val candidateChangedFirstPass: Boolean,
        val candidateMatchedIndependentSecondPass: Boolean,
        val candidateWasFixedPoint: Boolean,
        val geometryEquivalent: Boolean,
        val exactShortCircuitUsed: Boolean,
        val comparatorReason: String,
        val maximumFirstToSecondDeviation: Double,
        val maximumSecondToFirstDeviation: Double,
        val characterDelta: Int
    )

    data class G314PostSerializationGeometryConvergenceResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val unchangedByCandidate: Int,
        val changedByCandidate: Int,
        val redundantGeometryChangedCases: Int,
        val collinearGeometryChangedCases: Int,
        val matchedIndependentSecondPass: Int,
        val differedFromIndependentSecondPass: Int,
        val fixedAfterCandidate: Int,
        val stillChangedAfterVerification: Int,
        val geometryComparisons: Int,
        val geometryMismatchCount: Int,
        val exactShortCircuitCount: Int,
        val fallbackBidirectionalCount: Int,
        val comparatorFailureCount: Int,
        val totalCharactersSaved: Long,
        val totalCharactersGrown: Long,
        val candidateNanos: Long,
        val comparatorNanos: Long,
        val verificationNanos: Long,
        val elapsedNanos: Long,
        val witnesses: List<G314PostSerializationGeometryConvergenceWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.14 G3.7 convergence-corpus rerun with G3.13 geometry comparator")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("Unchanged by convergence candidate: $unchangedByCandidate")
            appendLine("Changed by convergence candidate: $changedByCandidate")
            appendLine("Redundant-geometry stage changed: $redundantGeometryChangedCases")
            appendLine("Collinear-consolidation stage changed: $collinearGeometryChangedCases")
            appendLine("Matched independent full pass 2: $matchedIndependentSecondPass")
            appendLine("Differed from independent full pass 2: $differedFromIndependentSecondPass")
            appendLine("Fixed after convergence candidate: $fixedAfterCandidate")
            appendLine("Still changed after verification: $stillChangedAfterVerification")
            appendLine("G3.13 geometry comparisons: $geometryComparisons")
            appendLine("G3.13 geometry mismatches: $geometryMismatchCount")
            appendLine("Exact ordered-traversal short-circuits: $exactShortCircuitCount")
            appendLine("Fallback bidirectional comparisons: $fallbackBidirectionalCount")
            appendLine("Comparator failures: $comparatorFailureCount")
            appendLine("Characters saved by convergence candidate: $totalCharactersSaved")
            appendLine("Characters added by convergence candidate: $totalCharactersGrown")
            appendLine("Candidate time: " + String.format(java.util.Locale.US, "%.2f ms", candidateNanos / 1_000_000.0))
            appendLine("Comparator time: " + String.format(java.util.Locale.US, "%.2f ms", comparatorNanos / 1_000_000.0))
            appendLine("Verification time: " + String.format(java.util.Locale.US, "%.2f ms", verificationNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            appendLine()
            when {
                comparatorFailureCount > 0 -> {
                    appendLine("RESULT: G3.14 encountered diagnostic comparator failures.")
                    appendLine("Recommendation: keep production unchanged and inspect every comparator-failure witness.")
                }
                geometryMismatchCount > 0 -> {
                    appendLine("RESULT: G3.14 found geometry mismatches under the validated G3.13 comparator.")
                    appendLine("Recommendation: keep production unchanged and inspect every geometry witness before revisiting convergence.")
                }
                stillChangedAfterVerification > 0 -> {
                    appendLine("RESULT: the G3.14 convergence candidate did not reach a fixed point for every case.")
                    appendLine("Recommendation: keep production unchanged and inspect the remaining verification witnesses.")
                }
                differedFromIndependentSecondPass > 0 -> {
                    appendLine("RESULT: G3.14 preserved geometry but did not exactly reproduce every independent second-pass result.")
                    appendLine("Recommendation: keep production unchanged until the remaining spelling differences are classified.")
                }
                validCases > 0 -> {
                    appendLine("RESULT: G3.14 exactly reproduced the independent second pass, remained fixed, and produced no G3.13 geometry mismatches.")
                    appendLine("Recommendation: rerun the locked regression suite, then consider a guarded production convergence trial.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }
            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   Redundant geometry changed: ${witness.redundantGeometryChanged}")
                    appendLine("   Collinear geometry changed: ${witness.collinearGeometryChanged}")
                    appendLine("   Candidate changed pass 1: ${witness.candidateChangedFirstPass}")
                    appendLine("   Matched independent pass 2: ${witness.candidateMatchedIndependentSecondPass}")
                    appendLine("   Fixed under verification: ${witness.candidateWasFixedPoint}")
                    appendLine("   G3.13 geometry equivalent: ${witness.geometryEquivalent}")
                    appendLine("   Exact short-circuit used: ${witness.exactShortCircuitUsed}")
                    appendLine("   Comparator reason: ${witness.comparatorReason}")
                    appendLine("   A → B max deviation: " + String.format(java.util.Locale.US, "%.9g", witness.maximumFirstToSecondDeviation))
                    appendLine("   B → A max deviation: " + String.format(java.util.Locale.US, "%.9g", witness.maximumSecondToFirstDeviation))
                    appendLine("   Character delta (pass1 - candidate): ${witness.characterDelta}")
                    appendLine("   Source: ${witness.source}")
                    appendLine("   Pass 1: ${witness.firstPass}")
                    appendLine("   G3.14 candidate: ${witness.candidatePass}")
                    appendLine("   Independent pass 2: ${witness.independentSecondPass}")
                    appendLine("   Verification pass: ${witness.verificationPass}")
                }
            }
        }
    }

    fun runG314PostSerializationGeometryConvergenceStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumWitnesses: Int = 8,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): G314PostSerializationGeometryConvergenceResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<G314PostSerializationGeometryConvergenceWitness>()
        var generated = 0
        var valid = 0
        var rejected = 0
        var unchanged = 0
        var changed = 0
        var redundantChanged = 0
        var collinearChanged = 0
        var matchedIndependent = 0
        var differedIndependent = 0
        var fixed = 0
        var stillChanged = 0
        var geometryComparisons = 0
        var geometryMismatches = 0
        var exactShortCircuits = 0
        var fallbackBidirectional = 0
        var comparatorFailures = 0
        var charactersSaved = 0L
        var charactersGrown = 0L
        var candidateNanos = 0L
        var comparatorNanos = 0L
        var verificationNanos = 0L

        repeat(caseCount) { caseIndex ->
            generated++
            val source = generateDifferentialStressPath(random)
            try {
                val first = optimizePathData(source).pathData

                val candidateStart = System.nanoTime()
                val candidate = runPostSerializationGeometryConvergenceCandidate(first)
                candidateNanos += System.nanoTime() - candidateStart

                val independentSecond = optimizePathData(first).pathData

                val verificationStart = System.nanoTime()
                val verification = optimizePathData(candidate.pathData).pathData
                verificationNanos += System.nanoTime() - verificationStart

                val candidateChanged = candidate.pathData != first
                val matched = candidate.pathData == independentSecond
                val isFixed = verification == candidate.pathData
                val delta = first.length - candidate.pathData.length

                var geometryEquivalent = true
                var exactShortCircuitUsed = false
                var comparatorReason = "candidate unchanged; comparator not required"
                var maxFirstToSecondDeviation = 0.0
                var maxSecondToFirstDeviation = 0.0

                if (candidateChanged) {
                    geometryComparisons++
                    val comparatorStart = System.nanoTime()
                    val diagnostic = SvgPathSampler.exactTraversalShortCircuitGeometryDiagnostic(
                        first,
                        candidate.pathData
                    )
                    comparatorNanos += System.nanoTime() - comparatorStart

                    geometryEquivalent = diagnostic.equivalent
                    exactShortCircuitUsed =
                        diagnostic.reason.contains("G3.13 exact ordered-traversal short-circuit")
                    if (exactShortCircuitUsed) exactShortCircuits++ else fallbackBidirectional++
                    comparatorReason = diagnostic.reason
                    maxFirstToSecondDeviation = diagnostic.maximumFirstToSecondDeviation
                    maxSecondToFirstDeviation = diagnostic.maximumSecondToFirstDeviation

                    if (!geometryEquivalent) {
                        geometryMismatches++
                        if (diagnostic.reason.contains("parse failed", ignoreCase = true) ||
                            diagnostic.reason.contains("could not be flattened", ignoreCase = true)
                        ) {
                            comparatorFailures++
                        }
                    }
                }

                valid++
                if (candidateChanged) changed++ else unchanged++
                if (candidate.redundantGeometryChanged) redundantChanged++
                if (candidate.collinearGeometryChanged) collinearChanged++
                if (matched) matchedIndependent++ else differedIndependent++
                if (isFixed) fixed++ else stillChanged++
                if (delta > 0) charactersSaved += delta.toLong()
                if (delta < 0) charactersGrown += (-delta).toLong()

                val noteworthy =
                    !geometryEquivalent || !isFixed || !matched ||
                        (candidateChanged && witnesses.size < minOf(maximumWitnesses, 2))
                if (noteworthy && witnesses.size < maximumWitnesses) {
                    witnesses += G314PostSerializationGeometryConvergenceWitness(
                        caseNumber = caseIndex + 1,
                        source = source,
                        firstPass = first,
                        candidatePass = candidate.pathData,
                        independentSecondPass = independentSecond,
                        verificationPass = verification,
                        redundantGeometryChanged = candidate.redundantGeometryChanged,
                        collinearGeometryChanged = candidate.collinearGeometryChanged,
                        candidateChangedFirstPass = candidateChanged,
                        candidateMatchedIndependentSecondPass = matched,
                        candidateWasFixedPoint = isFixed,
                        geometryEquivalent = geometryEquivalent,
                        exactShortCircuitUsed = exactShortCircuitUsed,
                        comparatorReason = comparatorReason,
                        maximumFirstToSecondDeviation = maxFirstToSecondDeviation,
                        maximumSecondToFirstDeviation = maxSecondToFirstDeviation,
                        characterDelta = delta
                    )
                }
            } catch (_: Throwable) {
                rejected++
            }

            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        return G314PostSerializationGeometryConvergenceResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            unchangedByCandidate = unchanged,
            changedByCandidate = changed,
            redundantGeometryChangedCases = redundantChanged,
            collinearGeometryChangedCases = collinearChanged,
            matchedIndependentSecondPass = matchedIndependent,
            differedFromIndependentSecondPass = differedIndependent,
            fixedAfterCandidate = fixed,
            stillChangedAfterVerification = stillChanged,
            geometryComparisons = geometryComparisons,
            geometryMismatchCount = geometryMismatches,
            exactShortCircuitCount = exactShortCircuits,
            fallbackBidirectionalCount = fallbackBidirectional,
            comparatorFailureCount = comparatorFailures,
            totalCharactersSaved = charactersSaved,
            totalCharactersGrown = charactersGrown,
            candidateNanos = candidateNanos,
            comparatorNanos = comparatorNanos,
            verificationNanos = verificationNanos,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }


    // G3.16 diagnostic-only shadow stress trial of the actual G3.15 production guard.
    // Production conversion never calls this.
    data class G316GuardedProductionTrialWitness(
        val caseNumber: Int,
        val sourcePath: String,
        val firstPassXml: String,
        val independentSecondPassXml: String,
        val verificationPassXml: String,
        val candidateChanged: Boolean,
        val guardAccepted: Boolean,
        val guardRejected: Boolean,
        val independentlySafeToAccept: Boolean,
        val unsafeAccept: Boolean,
        val falseReject: Boolean,
        val secondPassDriftOutsideCandidateCoverage: Boolean,
        val rejectionReason: String,
        val geometryComparisons: Int,
        val geometryMismatchCount: Int,
        val exactShortCircuitCount: Int,
        val fallbackBidirectionalCount: Int,
        val comparatorFailureCount: Int,
        val finalValidationPassed: Boolean,
        val fixedPointVerified: Boolean,
        val matchedIndependentSecondPass: Boolean,
        val charactersSaved: Int,
        val charactersAdded: Int
    )

    data class G316GuardedProductionTrialResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val candidateUnchanged: Int,
        val candidateChanged: Int,
        val guardAccepted: Int,
        val guardRejected: Int,
        val unsafeAccepts: Int,
        val falseRejects: Int,
        val acceptedGeometryFailures: Int,
        val acceptedComparatorFailures: Int,
        val acceptedPass2Mismatches: Int,
        val acceptedNonFixedCandidates: Int,
        val acceptedValidationFailures: Int,
        val secondPassDriftOutsideCandidateCoverage: Int,
        val geometryComparisons: Int,
        val geometryMismatchCount: Int,
        val exactShortCircuitCount: Int,
        val fallbackBidirectionalCount: Int,
        val comparatorFailureCount: Int,
        val finalValidationFailures: Int,
        val fixedPointFailures: Int,
        val pass2MismatchCount: Int,
        val totalCharactersSaved: Long,
        val totalCharactersAdded: Long,
        val guardNanos: Long,
        val elapsedNanos: Long,
        val expectedHistoricalChangedCandidates: Int?,
        val historicalCoverageMatched: Boolean,
        val rejectionReasonCounts: Map<String, Int>,
        val witnesses: List<G316GuardedProductionTrialWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.16 guarded G3.15 shadow-mode stress trial")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("G3.15 candidate unchanged: $candidateUnchanged")
            appendLine("G3.15 candidate changed: $candidateChanged")
            if (expectedHistoricalChangedCandidates != null) {
                appendLine("Historical G3.14 expected changed candidates: $expectedHistoricalChangedCandidates")
                appendLine("Historical G3.14 coverage reproduced: $historicalCoverageMatched")
            }
            appendLine("Guard accepted: $guardAccepted")
            appendLine("Guard rejected: $guardRejected")
            appendLine("Unsafe accepts: $unsafeAccepts")
            appendLine("False rejects: $falseRejects")
            appendLine("Accepted geometry failures: $acceptedGeometryFailures")
            appendLine("Accepted comparator failures: $acceptedComparatorFailures")
            appendLine("Accepted pass-2 mismatches: $acceptedPass2Mismatches")
            appendLine("Accepted non-fixed candidates: $acceptedNonFixedCandidates")
            appendLine("Accepted validation failures: $acceptedValidationFailures")
            appendLine("Second-pass drift outside G3.15 candidate coverage: $secondPassDriftOutsideCandidateCoverage")
            appendLine("G3.13 geometry comparisons: $geometryComparisons")
            appendLine("G3.13 geometry mismatches: $geometryMismatchCount")
            appendLine("Exact ordered-traversal short-circuits: $exactShortCircuitCount")
            appendLine("Fallback bidirectional comparisons: $fallbackBidirectionalCount")
            appendLine("Comparator failures: $comparatorFailureCount")
            appendLine("Final validation failures: $finalValidationFailures")
            appendLine("Fixed-point failures: $fixedPointFailures")
            appendLine("Candidate/pass-2 mismatches: $pass2MismatchCount")
            appendLine("Characters saved by accepted candidates: $totalCharactersSaved")
            appendLine("Characters added by accepted candidates: $totalCharactersAdded")
            appendLine("Guard CPU time: " + String.format(java.util.Locale.US, "%.2f ms", guardNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            if (rejectionReasonCounts.isNotEmpty()) {
                appendLine()
                appendLine("Guard rejection reasons")
                rejectionReasonCounts.toSortedMap().forEach { (reason, count) ->
                    appendLine("• $reason: $count")
                }
            }
            appendLine()
            when {
                !historicalCoverageMatched -> {
                    appendLine("RESULT: INVALID TEST — G3.16 did not reproduce the historical G3.14 candidate coverage for this seed.")
                    appendLine("Recommendation: do not use this run for production-enablement decisions; repair the harness before continuing.")
                }
                unsafeAccepts > 0 -> {
                    appendLine("RESULT: G3.16 found unsafe G3.15 guard accepts.")
                    appendLine("Recommendation: keep G3.15 shadow-only and inspect every unsafe-accept witness.")
                }
                acceptedGeometryFailures > 0 ||
                    acceptedComparatorFailures > 0 ||
                    acceptedPass2Mismatches > 0 ||
                    acceptedNonFixedCandidates > 0 ||
                    acceptedValidationFailures > 0 -> {
                    appendLine("RESULT: G3.16 found an accepted candidate that violated a required guard invariant.")
                    appendLine("Recommendation: keep G3.15 shadow-only and inspect every invariant-failure witness.")
                }
                falseRejects > 0 -> {
                    appendLine("RESULT: G3.16 found false rejects in the G3.15 guard.")
                    appendLine("Recommendation: keep G3.15 shadow-only and classify the false-reject witnesses before production enablement.")
                }
                validCases > 0 -> {
                    appendLine("RESULT: G3.16 found no unsafe accepts, false rejects, or accepted-candidate invariant failures.")
                    if (secondPassDriftOutsideCandidateCoverage > 0) {
                        appendLine("NOTE: second-pass drift existed outside the narrow G3.15 convergence-candidate coverage.")
                        appendLine("Recommendation: inspect that coverage signal before deciding whether G3.15 should become authoritative.")
                    } else {
                        appendLine("Recommendation: combine this result with the locked regression suite and consider the next guarded production-enablement step.")
                    }
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }

            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   Candidate changed: ${witness.candidateChanged}")
                    appendLine("   Guard accepted: ${witness.guardAccepted}")
                    appendLine("   Guard rejected: ${witness.guardRejected}")
                    appendLine("   Independently safe to accept: ${witness.independentlySafeToAccept}")
                    appendLine("   Unsafe accept: ${witness.unsafeAccept}")
                    appendLine("   False reject: ${witness.falseReject}")
                    appendLine("   Second-pass drift outside candidate coverage: ${witness.secondPassDriftOutsideCandidateCoverage}")
                    appendLine("   Rejection reason: ${witness.rejectionReason.ifBlank { "(none)" }}")
                    appendLine("   Geometry comparisons: ${witness.geometryComparisons}")
                    appendLine("   Geometry mismatches: ${witness.geometryMismatchCount}")
                    appendLine("   Exact short-circuits: ${witness.exactShortCircuitCount}")
                    appendLine("   Fallback bidirectional comparisons: ${witness.fallbackBidirectionalCount}")
                    appendLine("   Comparator failures: ${witness.comparatorFailureCount}")
                    appendLine("   Final validation passed: ${witness.finalValidationPassed}")
                    appendLine("   Fixed point verified: ${witness.fixedPointVerified}")
                    appendLine("   Matched independent pass 2: ${witness.matchedIndependentSecondPass}")
                    appendLine("   Characters saved: ${witness.charactersSaved}")
                    appendLine("   Characters added: ${witness.charactersAdded}")
                    appendLine("   Source path: ${witness.sourcePath}")
                    appendLine("   First pass XML: ${witness.firstPassXml}")
                    appendLine("   Independent pass 2 XML: ${witness.independentSecondPassXml}")
                    appendLine("   Verification pass XML: ${witness.verificationPassXml}")
                }
            }
        }
    }

    private fun g316HistoricalExpectedChangedCandidates(seed: Long, caseCount: Int): Int? {
        if (caseCount != 25_000) return null
        return when (seed) {
            0x6316_2026L -> 295
            0x6316_0001L -> 282
            0x6316_0002L -> 297
            0x1D40_2026L -> 281
            else -> null
        }
    }

    private fun buildG316VectorXml(pathData: String): String = buildString {
        append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" " )
        append("android:width=\"24dp\" android:height=\"24dp\" " )
        append("android:viewportWidth=\"1000\" android:viewportHeight=\"1000\">")
        append("<path android:pathData=\"")
        append(pathData)
        append("\" android:fillColor=\"#FF336699\"/></vector>")
    }

    fun runG316GuardedProductionTrialStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumWitnesses: Int = 8,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): G316GuardedProductionTrialResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<G316GuardedProductionTrialWitness>()
        val rejectionReasons = linkedMapOf<String, Int>()

        var generated = 0
        var valid = 0
        var rejected = 0
        var candidateUnchanged = 0
        var candidateChanged = 0
        var guardAccepted = 0
        var guardRejected = 0
        var unsafeAccepts = 0
        var falseRejects = 0
        var acceptedGeometryFailures = 0
        var acceptedComparatorFailures = 0
        var acceptedPass2Mismatches = 0
        var acceptedNonFixedCandidates = 0
        var acceptedValidationFailures = 0
        var coverageDrift = 0
        var geometryComparisons = 0
        var geometryMismatches = 0
        var exactShortCircuits = 0
        var fallbackBidirectional = 0
        var comparatorFailures = 0
        var validationFailures = 0
        var fixedPointFailures = 0
        var pass2Mismatches = 0
        var charactersSaved = 0L
        var charactersAdded = 0L
        var guardNanos = 0L

        repeat(caseCount) { caseIndex ->
            generated++
            val sourcePath = generateDifferentialStressPath(random)
            try {
                // G3.16 must enter the corpus at the same path-level boundary as G3.14.
                // Feeding optimizeVectorXmlSinglePass() output here pre-consumes the narrow
                // post-serialization convergence opportunity and makes the guard vacuous.
                val firstPassPath = optimizePathData(sourcePath).pathData
                val independentSecondPassPath = optimizePathData(firstPassPath).pathData
                val verificationPassPath = optimizePathData(independentSecondPassPath).pathData
                val secondPassIsFixed = verificationPassPath == independentSecondPassPath

                val firstPassXml = buildG316VectorXml(firstPassPath)
                val independentSecondPassXml = buildG316VectorXml(independentSecondPassPath)
                val verificationPassXml = buildG316VectorXml(verificationPassPath)

                val guardStart = System.nanoTime()
                val trial = runGuardedProductionConvergenceTrial(
                    firstPassXml = firstPassXml,
                    independentSecondPassXml = independentSecondPassXml,
                    independentSecondPassIsFixed = secondPassIsFixed
                )
                guardNanos += System.nanoTime() - guardStart

                val independentlySafe =
                    trial.candidateChanged &&
                        trial.comparatorFailureCount == 0 &&
                        trial.geometryMismatchCount == 0 &&
                        trial.matchedIndependentSecondPass &&
                        trial.fixedPointVerified &&
                        trial.finalValidationPassed

                val unsafeAccept = trial.guardAccepted && !independentlySafe
                val falseReject = trial.candidateChanged && independentlySafe && trial.guardRejected
                val driftOutsideCoverage =
                    !trial.candidateChanged && independentSecondPassPath != firstPassPath

                valid++
                if (trial.candidateChanged) candidateChanged++ else candidateUnchanged++
                if (trial.guardAccepted) guardAccepted++
                if (trial.guardRejected) {
                    guardRejected++
                    if (trial.rejectionReason.isNotBlank()) {
                        rejectionReasons[trial.rejectionReason] =
                            (rejectionReasons[trial.rejectionReason] ?: 0) + 1
                    }
                }
                if (unsafeAccept) unsafeAccepts++
                if (falseReject) falseRejects++
                if (trial.guardAccepted && trial.geometryMismatchCount > 0) acceptedGeometryFailures++
                if (trial.guardAccepted && trial.comparatorFailureCount > 0) acceptedComparatorFailures++
                if (trial.guardAccepted && !trial.matchedIndependentSecondPass) acceptedPass2Mismatches++
                if (trial.guardAccepted && !trial.fixedPointVerified) acceptedNonFixedCandidates++
                if (trial.guardAccepted && !trial.finalValidationPassed) acceptedValidationFailures++
                if (driftOutsideCoverage) coverageDrift++

                geometryComparisons += trial.geometryComparisons
                geometryMismatches += trial.geometryMismatchCount
                exactShortCircuits += trial.exactShortCircuitCount
                fallbackBidirectional += trial.fallbackBidirectionalCount
                comparatorFailures += trial.comparatorFailureCount
                if (!trial.finalValidationPassed) validationFailures++
                if (!trial.fixedPointVerified) fixedPointFailures++
                if (trial.candidateChanged && !trial.matchedIndependentSecondPass) pass2Mismatches++

                if (trial.guardAccepted) {
                    charactersSaved += trial.charactersSaved.toLong()
                    charactersAdded += trial.charactersAdded.toLong()
                }

                val noteworthy =
                    unsafeAccept ||
                        falseReject ||
                        driftOutsideCoverage ||
                        (trial.guardRejected && witnesses.size < minOf(maximumWitnesses, 2))
                if (noteworthy && witnesses.size < maximumWitnesses) {
                    witnesses += G316GuardedProductionTrialWitness(
                        caseNumber = caseIndex + 1,
                        sourcePath = sourcePath,
                        firstPassXml = firstPassXml,
                        independentSecondPassXml = independentSecondPassXml,
                        verificationPassXml = verificationPassXml,
                        candidateChanged = trial.candidateChanged,
                        guardAccepted = trial.guardAccepted,
                        guardRejected = trial.guardRejected,
                        independentlySafeToAccept = independentlySafe,
                        unsafeAccept = unsafeAccept,
                        falseReject = falseReject,
                        secondPassDriftOutsideCandidateCoverage = driftOutsideCoverage,
                        rejectionReason = trial.rejectionReason,
                        geometryComparisons = trial.geometryComparisons,
                        geometryMismatchCount = trial.geometryMismatchCount,
                        exactShortCircuitCount = trial.exactShortCircuitCount,
                        fallbackBidirectionalCount = trial.fallbackBidirectionalCount,
                        comparatorFailureCount = trial.comparatorFailureCount,
                        finalValidationPassed = trial.finalValidationPassed,
                        fixedPointVerified = trial.fixedPointVerified,
                        matchedIndependentSecondPass = trial.matchedIndependentSecondPass,
                        charactersSaved = trial.charactersSaved,
                        charactersAdded = trial.charactersAdded
                    )
                }
            } catch (_: Throwable) {
                rejected++
            }

            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        val expectedHistoricalChanged =
            g316HistoricalExpectedChangedCandidates(seed, caseCount)
        val historicalCoverageMatched =
            expectedHistoricalChanged == null || candidateChanged == expectedHistoricalChanged

        return G316GuardedProductionTrialResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            candidateUnchanged = candidateUnchanged,
            candidateChanged = candidateChanged,
            guardAccepted = guardAccepted,
            guardRejected = guardRejected,
            unsafeAccepts = unsafeAccepts,
            falseRejects = falseRejects,
            acceptedGeometryFailures = acceptedGeometryFailures,
            acceptedComparatorFailures = acceptedComparatorFailures,
            acceptedPass2Mismatches = acceptedPass2Mismatches,
            acceptedNonFixedCandidates = acceptedNonFixedCandidates,
            acceptedValidationFailures = acceptedValidationFailures,
            secondPassDriftOutsideCandidateCoverage = coverageDrift,
            geometryComparisons = geometryComparisons,
            geometryMismatchCount = geometryMismatches,
            exactShortCircuitCount = exactShortCircuits,
            fallbackBidirectionalCount = fallbackBidirectional,
            comparatorFailureCount = comparatorFailures,
            finalValidationFailures = validationFailures,
            fixedPointFailures = fixedPointFailures,
            pass2MismatchCount = pass2Mismatches,
            totalCharactersSaved = charactersSaved,
            totalCharactersAdded = charactersAdded,
            guardNanos = guardNanos,
            elapsedNanos = System.nanoTime() - started,
            expectedHistoricalChangedCandidates = expectedHistoricalChanged,
            historicalCoverageMatched = historicalCoverageMatched,
            rejectionReasonCounts = rejectionReasons.toMap(),
            witnesses = witnesses
        )
    }


    // G3.17 diagnostic-only classification of the 14 G3.16 final-validation failures.
    // Production conversion never calls this. The broad corpus does only the minimum
    // work needed to reproduce G3.16's validation signal; pass 2/pass 3 are computed
    // only for actual validation-failure witnesses.
    data class G317ValidationSnapshot(
        val passed: Boolean,
        val validatedPathDataCount: Int,
        val invalidPathDataCount: Int,
        val nonFiniteNumberCount: Int,
        val malformedStructureCount: Int,
        val invalidViewportCount: Int,
        val unsupportedOutputConstructCount: Int
    ) {
        fun reason(): String {
            if (passed) return "passed"
            val parts = mutableListOf<String>()
            if (invalidPathDataCount > 0) parts += "invalid pathData=$invalidPathDataCount"
            if (nonFiniteNumberCount > 0) parts += "non-finite numbers=$nonFiniteNumberCount"
            if (malformedStructureCount > 0) parts += "malformed XML structure=$malformedStructureCount"
            if (invalidViewportCount > 0) parts += "invalid viewport=$invalidViewportCount"
            if (unsupportedOutputConstructCount > 0) parts += "unsupported output constructs=$unsupportedOutputConstructCount"
            return if (parts.isEmpty()) "failed for unclassified validator reason" else parts.joinToString(", ")
        }
    }

    data class G317FinalValidationWitness(
        val caseNumber: Int,
        val sourcePath: String,
        val firstPassPath: String,
        val candidatePath: String,
        val independentSecondPassPath: String,
        val verificationPassPath: String,
        val candidateChanged: Boolean,
        val guardValidationTarget: String,
        val sourceValidation: G317ValidationSnapshot,
        val firstPassValidation: G317ValidationSnapshot,
        val candidateValidation: G317ValidationSnapshot,
        val independentSecondPassValidation: G317ValidationSnapshot,
        val verificationPassValidation: G317ValidationSnapshot,
        val firstFailureStage: String,
        val candidateMatchedIndependentSecondPass: Boolean,
        val independentSecondPassWasFixed: Boolean
    )

    data class G317FinalValidationClassificationResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val candidateChangedCases: Int,
        val candidateUnchangedCases: Int,
        val reproducedFinalValidationFailures: Int,
        val failureWithChangedCandidate: Int,
        val failureWithUnchangedCandidate: Int,
        val sourceAlreadyInvalid: Int,
        val firstPassIntroducedInvalidity: Int,
        val candidateIntroducedInvalidity: Int,
        val candidatePreservedExistingInvalidity: Int,
        val pass2RecoveredValidity: Int,
        val pass2StillInvalid: Int,
        val pass3StillInvalid: Int,
        val failureCandidatePass2Mismatches: Int,
        val failureNonFixedSecondPasses: Int,
        val expectedHistoricalFailures: Int?,
        val historicalFailureCoverageMatched: Boolean,
        val validatorReasonCounts: Map<String, Int>,
        val elapsedNanos: Long,
        val witnesses: List<G317FinalValidationWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.17 final-validation failure classification")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("G3.15 candidate changed: $candidateChangedCases")
            appendLine("G3.15 candidate unchanged: $candidateUnchangedCases")
            appendLine("Reproduced G3.16 final-validation failures: $reproducedFinalValidationFailures")
            if (expectedHistoricalFailures != null) {
                appendLine("Historical G3.16 expected validation failures: $expectedHistoricalFailures")
                appendLine("Historical validation-failure coverage reproduced: $historicalFailureCoverageMatched")
            }
            appendLine("Failures with changed candidate: $failureWithChangedCandidate")
            appendLine("Failures with unchanged candidate: $failureWithUnchangedCandidate")
            appendLine("Source already invalid: $sourceAlreadyInvalid")
            appendLine("Pass 1 introduced invalidity: $firstPassIntroducedInvalidity")
            appendLine("Candidate introduced invalidity: $candidateIntroducedInvalidity")
            appendLine("Candidate preserved existing invalidity: $candidatePreservedExistingInvalidity")
            appendLine("Pass 2 recovered validity: $pass2RecoveredValidity")
            appendLine("Pass 2 still invalid: $pass2StillInvalid")
            appendLine("Pass 3 still invalid: $pass3StillInvalid")
            appendLine("Failure candidate/pass-2 mismatches: $failureCandidatePass2Mismatches")
            appendLine("Failure non-fixed second passes: $failureNonFixedSecondPasses")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            if (validatorReasonCounts.isNotEmpty()) {
                appendLine()
                appendLine("Validator failure reasons")
                validatorReasonCounts.toSortedMap().forEach { (reason, count) ->
                    appendLine("• $reason: $count")
                }
            }
            appendLine()
            when {
                !historicalFailureCoverageMatched -> {
                    appendLine("RESULT: INVALID TEST — G3.17 did not reproduce the historical G3.16 validation-failure count for this seed.")
                    appendLine("Recommendation: repair the diagnostic harness before drawing any production conclusion.")
                }
                reproducedFinalValidationFailures == 0 -> {
                    appendLine("RESULT: G3.17 reproduced no final-validation failures.")
                    appendLine("Recommendation: treat the run as inconclusive unless this was a non-historical smoke corpus.")
                }
                failureWithChangedCandidate > 0 || candidateIntroducedInvalidity > 0 -> {
                    appendLine("RESULT: G3.17 found a validation failure that could involve the G3.15 convergence candidate.")
                    appendLine("Recommendation: keep G3.15 shadow-only and inspect every listed witness before production enablement.")
                }
                firstPassIntroducedInvalidity > 0 -> {
                    appendLine("RESULT: G3.17 found validation failures introduced before G3.15, during the existing pass-1 optimizer.")
                    appendLine("Recommendation: classify the pass-1 validator limitation separately; the failures are not convergence-induced.")
                }
                sourceAlreadyInvalid == reproducedFinalValidationFailures -> {
                    appendLine("RESULT: every reproduced validation failure was already present in the generated source path and no failure involved a changed G3.15 candidate.")
                    appendLine("Recommendation: treat the G3.16 final-validation signal as baseline corpus invalidity, then proceed to the guarded production-enablement step after the locked suite remains clean.")
                }
                else -> {
                    appendLine("RESULT: G3.17 reproduced the G3.16 validation failures without implicating a changed G3.15 candidate, but the failures have mixed baseline origins.")
                    appendLine("Recommendation: review the witnesses before production enablement.")
                }
            }

            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Validation failure witnesses")
                witnesses.forEachIndexed { index, w ->
                    appendLine()
                    appendLine("${index + 1}. Case ${w.caseNumber}")
                    appendLine("   Candidate changed: ${w.candidateChanged}")
                    appendLine("   Guard validation target: ${w.guardValidationTarget}")
                    appendLine("   First failure stage: ${w.firstFailureStage}")
                    appendLine("   Source validation: ${w.sourceValidation.reason()}")
                    appendLine("   Pass-1 validation: ${w.firstPassValidation.reason()}")
                    appendLine("   Candidate validation: ${w.candidateValidation.reason()}")
                    appendLine("   Independent pass-2 validation: ${w.independentSecondPassValidation.reason()}")
                    appendLine("   Verification pass validation: ${w.verificationPassValidation.reason()}")
                    appendLine("   Candidate matched independent pass 2: ${w.candidateMatchedIndependentSecondPass}")
                    appendLine("   Independent pass 2 fixed: ${w.independentSecondPassWasFixed}")
                    appendLine("   Source path: ${w.sourcePath}")
                    appendLine("   Pass 1: ${w.firstPassPath}")
                    appendLine("   G3.15 candidate: ${w.candidatePath}")
                    appendLine("   Independent pass 2: ${w.independentSecondPassPath}")
                    appendLine("   Verification pass: ${w.verificationPassPath}")
                }
            }
        }
    }

    private fun g317HistoricalExpectedValidationFailures(seed: Long, caseCount: Int): Int? {
        if (caseCount != 25_000) return null
        return when (seed) {
            0x6316_2026L -> 2
            0x6316_0001L -> 3
            0x6316_0002L -> 6
            0x1D40_2026L -> 3
            else -> null
        }
    }

    private fun g317Snapshot(validation: FinalOutputValidation): G317ValidationSnapshot =
        G317ValidationSnapshot(
            passed = validation.passed,
            validatedPathDataCount = validation.validatedPathDataCount,
            invalidPathDataCount = validation.invalidPathDataCount,
            nonFiniteNumberCount = validation.nonFiniteNumberCount,
            malformedStructureCount = validation.malformedStructureCount,
            invalidViewportCount = validation.invalidViewportCount,
            unsupportedOutputConstructCount = validation.unsupportedOutputConstructCount
        )

    private fun g317FastWrappedPathValidation(pathData: String): G317ValidationSnapshot {
        val invalidPathData = if (pathData.isBlank() || parseNormalizedSegments(pathData) == null) 1 else 0
        val nonFinite = Regex(
            """(?i)(?<![A-Za-z0-9_])(?:NaN|[-+]?Infinity)(?![A-Za-z0-9_])"""
        ).findAll(pathData).count()
        return G317ValidationSnapshot(
            passed = invalidPathData == 0 && nonFinite == 0,
            validatedPathDataCount = 1,
            invalidPathDataCount = invalidPathData,
            nonFiniteNumberCount = nonFinite,
            malformedStructureCount = 0,
            invalidViewportCount = 0,
            unsupportedOutputConstructCount = 0
        )
    }

    fun runG317FinalValidationClassificationStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumWitnesses: Int = 32,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): G317FinalValidationClassificationResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<G317FinalValidationWitness>()
        val validatorReasons = linkedMapOf<String, Int>()

        var generated = 0
        var valid = 0
        var rejected = 0
        var candidateChanged = 0
        var candidateUnchanged = 0
        var failures = 0
        var failuresChanged = 0
        var failuresUnchanged = 0
        var sourceAlreadyInvalid = 0
        var pass1Introduced = 0
        var candidateIntroduced = 0
        var candidatePreservedInvalid = 0
        var pass2Recovered = 0
        var pass2StillInvalid = 0
        var pass3StillInvalid = 0
        var failurePass2Mismatch = 0
        var failureNonFixed = 0

        repeat(caseCount) { caseIndex ->
            generated++
            val sourcePath = generateDifferentialStressPath(random)
            try {
                val firstPassPath = optimizePathData(sourcePath).pathData
                val candidate = runPostSerializationGeometryConvergenceCandidate(firstPassPath).pathData
                val changed = candidate != firstPassPath
                if (changed) candidateChanged++ else candidateUnchanged++

                // The G3.16 diagnostic wrapper has fixed, known-valid XML structure, viewport,
                // and output constructs. Its only variable content is pathData, so this fast
                // scan is equivalent to the full final validator for broad corpus discovery.
                val firstValidationFast = g317FastWrappedPathValidation(firstPassPath)
                val candidateValidationFast = if (changed) {
                    g317FastWrappedPathValidation(candidate)
                } else {
                    firstValidationFast
                }
                val guardValidationFast = if (changed) candidateValidationFast else firstValidationFast
                valid++

                if (!guardValidationFast.passed) {
                    // Confirm every discovered failure with the exact production validator and
                    // classify source/pass/candidate/pass2/pass3 only for this tiny population.
                    val sourceValidation = g317Snapshot(validateFinalVectorXml(buildG316VectorXml(sourcePath)))
                    val firstValidation = g317Snapshot(validateFinalVectorXml(buildG316VectorXml(firstPassPath)))
                    val candidateValidation = if (changed) {
                        g317Snapshot(validateFinalVectorXml(buildG316VectorXml(candidate)))
                    } else {
                        firstValidation
                    }
                    val guardValidation = if (changed) candidateValidation else firstValidation
                    if (guardValidation.passed) {
                        throw IllegalStateException("G3.17 fast validation disagreed with full final validation")
                    }
                    failures++
                    if (changed) failuresChanged++ else failuresUnchanged++
                    val reason = guardValidation.reason()
                    validatorReasons[reason] = (validatorReasons[reason] ?: 0) + 1

                    if (!sourceValidation.passed) sourceAlreadyInvalid++
                    if (sourceValidation.passed && !firstValidation.passed) pass1Introduced++
                    if (changed && firstValidation.passed && !candidateValidation.passed) candidateIntroduced++
                    if (changed && !firstValidation.passed && !candidateValidation.passed) candidatePreservedInvalid++

                    // Expensive follow-up work is limited to the small failure population.
                    val secondPassPath = optimizePathData(firstPassPath).pathData
                    val verificationPath = optimizePathData(secondPassPath).pathData
                    val secondValidation = g317Snapshot(validateFinalVectorXml(buildG316VectorXml(secondPassPath)))
                    val verificationValidation = g317Snapshot(validateFinalVectorXml(buildG316VectorXml(verificationPath)))
                    if (secondValidation.passed) pass2Recovered++ else pass2StillInvalid++
                    if (!verificationValidation.passed) pass3StillInvalid++
                    val matched = candidate == secondPassPath
                    val fixed = verificationPath == secondPassPath
                    if (!matched) failurePass2Mismatch++
                    if (!fixed) failureNonFixed++

                    val firstFailureStage = when {
                        !sourceValidation.passed -> "source"
                        !firstValidation.passed -> "pass 1"
                        changed && !candidateValidation.passed -> "G3.15 candidate"
                        else -> "guard validation target"
                    }

                    if (witnesses.size < maximumWitnesses) {
                        witnesses += G317FinalValidationWitness(
                            caseNumber = caseIndex + 1,
                            sourcePath = sourcePath,
                            firstPassPath = firstPassPath,
                            candidatePath = candidate,
                            independentSecondPassPath = secondPassPath,
                            verificationPassPath = verificationPath,
                            candidateChanged = changed,
                            guardValidationTarget = if (changed) "G3.15 candidate" else "pass 1 (candidate unchanged)",
                            sourceValidation = sourceValidation,
                            firstPassValidation = firstValidation,
                            candidateValidation = candidateValidation,
                            independentSecondPassValidation = secondValidation,
                            verificationPassValidation = verificationValidation,
                            firstFailureStage = firstFailureStage,
                            candidateMatchedIndependentSecondPass = matched,
                            independentSecondPassWasFixed = fixed
                        )
                    }
                }
            } catch (_: Throwable) {
                rejected++
            }

            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        val expected = g317HistoricalExpectedValidationFailures(seed, caseCount)
        val coverageMatched = expected == null || failures == expected

        return G317FinalValidationClassificationResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            candidateChangedCases = candidateChanged,
            candidateUnchangedCases = candidateUnchanged,
            reproducedFinalValidationFailures = failures,
            failureWithChangedCandidate = failuresChanged,
            failureWithUnchangedCandidate = failuresUnchanged,
            sourceAlreadyInvalid = sourceAlreadyInvalid,
            firstPassIntroducedInvalidity = pass1Introduced,
            candidateIntroducedInvalidity = candidateIntroduced,
            candidatePreservedExistingInvalidity = candidatePreservedInvalid,
            pass2RecoveredValidity = pass2Recovered,
            pass2StillInvalid = pass2StillInvalid,
            pass3StillInvalid = pass3StillInvalid,
            failureCandidatePass2Mismatches = failurePass2Mismatch,
            failureNonFixedSecondPasses = failureNonFixed,
            expectedHistoricalFailures = expected,
            historicalFailureCoverageMatched = coverageMatched,
            validatorReasonCounts = validatorReasons.toMap(),
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }

    private data class PostSerializationGeometryCandidate(
        val pathData: String,
        val redundantGeometryChanged: Boolean,
        val collinearGeometryChanged: Boolean
    )

    private fun runPostSerializationGeometryConvergenceCandidate(
        pathData: String
    ): PostSerializationGeometryCandidate {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return PostSerializationGeometryCandidate(pathData.trim(), false, false)
        }

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return PostSerializationGeometryCandidate(pathData, false, false)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return PostSerializationGeometryCandidate(pathData, false, false)
        }

        val normalized = StringBuilder(pathData.length)
        var activeCommand: Char? = null
        var previousWasNumber = false
        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                val implicitRepeat =
                    activeCommand == command && command !in charArrayOf('M', 'm', 'Z', 'z')
                if (!implicitRepeat) {
                    normalized.append(command)
                    previousWasNumber = false
                }
                activeCommand = command
            } else {
                val value = token.toBigDecimalOrNull()
                    ?.let(::formatPathNumber)
                    ?: normalizeNumber(token)
                if (previousWasNumber) normalized.append(',')
                normalized.append(value)
                previousWasNumber = true
            }
        }

        val syntaxNormalized = normalized.toString()
        val redundant = removeRedundantNonDrawingSegments(syntaxNormalized)
        val collinear = consolidateConsecutiveCollinearLineRuns(redundant.pathData)
        val local = shortenPathCommands(collinear.pathData, null).pathData
        val global = globallyMinimizeCommandSequence(local, null).pathData
        val numeric = globallyOptimizeNumericSerialization(global).pathData

        return PostSerializationGeometryCandidate(
            pathData = numeric,
            redundantGeometryChanged = redundant.pathData != syntaxNormalized,
            collinearGeometryChanged = collinear.pathData != redundant.pathData
        )
    }


    // G3.8 diagnostic-only investigation. Production conversion never calls this.
    data class CollinearGeometrySafetyWitness(
        val caseNumber: Int,
        val source: String,
        val firstPass: String,
        val syntaxNormalized: String,
        val preCollinear: String,
        val postCollinear: String,
        val finalCandidate: String,
        val independentSecondPass: String,
        val standardMismatch: Boolean,
        val denseMismatch: Boolean,
        val directCollinearDenseMismatch: Boolean,
        val sourceToFirstDenseMismatch: Boolean,
        val sourceToSecondDenseMismatch: Boolean,
        val firstDifferingTokenIndex: Int,
        val preCollinearToken: String,
        val postCollinearToken: String,
        val preCollinearTokenCount: Int,
        val postCollinearTokenCount: Int,
        val firstLength: Float,
        val candidateLength: Float,
        val lengthDelta: Float,
        val maximumSampleDeviation: Double,
        val maximumDeviationFraction: Double,
        val maximumDeviationFirstPoint: String,
        val maximumDeviationCandidatePoint: String
    )

    data class CollinearGeometrySafetyResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val candidateChangedCases: Int,
        val collinearChangedCases: Int,
        val standardMismatchCases: Int,
        val denseMismatchCases: Int,
        val directCollinearDenseMismatchCases: Int,
        val standardOnlyMismatchCases: Int,
        val sourceToFirstDenseMismatchCases: Int,
        val sourceToSecondDenseMismatchCases: Int,
        val denseChecks: Int,
        val elapsedNanos: Long,
        val mismatchWitnesses: List<CollinearGeometrySafetyWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.8 collinear consolidation geometry-safety investigation")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("G3.7 candidate changed: $candidateChangedCases")
            appendLine("Collinear-consolidation stage changed: $collinearChangedCases")
            appendLine("Standard sampler mismatches: $standardMismatchCases")
            appendLine("Dense sampler mismatches: $denseMismatchCases")
            appendLine("Direct pre/post-collinear dense mismatches: $directCollinearDenseMismatchCases")
            appendLine("Standard-only mismatches (dense check passed): $standardOnlyMismatchCases")
            appendLine("Source → pass-1 dense mismatches: $sourceToFirstDenseMismatchCases")
            appendLine("Source → pass-2 dense mismatches: $sourceToSecondDenseMismatchCases")
            appendLine("Dense diagnostic checks: $denseChecks")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            appendLine()
            when {
                denseMismatchCases > 0 -> {
                    appendLine("RESULT: dense geometry mismatches remain after rechecking the G3.7 changes.")
                    appendLine("Recommendation: inspect the mismatch witnesses before changing production collinear consolidation.")
                }
                standardMismatchCases > 0 -> {
                    appendLine("RESULT: every standard-sampler mismatch disappeared under the dense diagnostic.")
                    appendLine("Recommendation: treat the G3.7 mismatch signal as a sampler/comparator issue and repair the comparator before revisiting convergence.")
                }
                validCases > 0 -> {
                    appendLine("RESULT: no geometry mismatch was reproduced in the G3.8 diagnostic.")
                    appendLine("Recommendation: rerun G3.7 after the diagnostic comparator is updated, then continue only if it remains clean.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }
            if (mismatchWitnesses.isNotEmpty()) {
                appendLine()
                appendLine("Geometry mismatch witnesses (prioritized)")
                mismatchWitnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   Standard sampler mismatch: ${witness.standardMismatch}")
                    appendLine("   Dense sampler mismatch: ${witness.denseMismatch}")
                    appendLine("   Direct pre/post-collinear dense mismatch: ${witness.directCollinearDenseMismatch}")
                    appendLine("   Source → pass-1 dense mismatch: ${witness.sourceToFirstDenseMismatch}")
                    appendLine("   Source → pass-2 dense mismatch: ${witness.sourceToSecondDenseMismatch}")
                    appendLine("   First differing token index: ${witness.firstDifferingTokenIndex}")
                    appendLine("   Pre-collinear token: ${witness.preCollinearToken}")
                    appendLine("   Post-collinear token: ${witness.postCollinearToken}")
                    appendLine("   Token counts: ${witness.preCollinearTokenCount} → ${witness.postCollinearTokenCount}")
                    appendLine("   Flattened lengths: ${witness.firstLength} → ${witness.candidateLength}")
                    appendLine("   Length delta: ${witness.lengthDelta}")
                    appendLine("   Maximum sampled deviation: ${String.format(java.util.Locale.US, "%.9g", witness.maximumSampleDeviation)}")
                    appendLine("   Maximum-deviation fraction: ${String.format(java.util.Locale.US, "%.6f", witness.maximumDeviationFraction)}")
                    appendLine("   First point at max deviation: ${witness.maximumDeviationFirstPoint}")
                    appendLine("   Candidate point at max deviation: ${witness.maximumDeviationCandidatePoint}")
                    appendLine("   Source: ${witness.source}")
                    appendLine("   Pass 1: ${witness.firstPass}")
                    appendLine("   Syntax normalized: ${witness.syntaxNormalized}")
                    appendLine("   Before collinear consolidation: ${witness.preCollinear}")
                    appendLine("   After collinear consolidation: ${witness.postCollinear}")
                    appendLine("   Final G3.7-equivalent candidate: ${witness.finalCandidate}")
                    appendLine("   Independent pass 2: ${witness.independentSecondPass}")
                }
            }
        }
    }

    private data class DenseGeometryDiagnostic(
        val equivalent: Boolean,
        val firstLength: Float,
        val secondLength: Float,
        val maximumDeviation: Double,
        val maximumDeviationFraction: Double,
        val firstPoint: String,
        val secondPoint: String
    )

    private data class G38CandidateStages(
        val syntaxNormalized: String,
        val preCollinear: String,
        val postCollinear: String,
        val finalCandidate: String,
        val collinearChanged: Boolean
    )

    private fun buildG38CandidateStages(pathData: String): G38CandidateStages? {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) return null
        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) return null
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) return null

        val normalized = StringBuilder(pathData.length)
        var activeCommand: Char? = null
        var previousWasNumber = false
        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                val implicitRepeat = activeCommand == command && command !in charArrayOf('M', 'm', 'Z', 'z')
                if (!implicitRepeat) {
                    normalized.append(command)
                    previousWasNumber = false
                }
                activeCommand = command
            } else {
                val value = token.toBigDecimalOrNull()?.let(::formatPathNumber) ?: normalizeNumber(token)
                if (previousWasNumber) normalized.append(',')
                normalized.append(value)
                previousWasNumber = true
            }
        }
        val syntax = normalized.toString()
        val redundant = removeRedundantNonDrawingSegments(syntax).pathData
        val collinear = consolidateConsecutiveCollinearLineRuns(redundant).pathData
        val local = shortenPathCommands(collinear, null).pathData
        val global = globallyMinimizeCommandSequence(local, null).pathData
        val numeric = globallyOptimizeNumericSerialization(global).pathData
        return G38CandidateStages(
            syntaxNormalized = syntax,
            preCollinear = redundant,
            postCollinear = collinear,
            finalCandidate = numeric,
            collinearChanged = collinear != redundant
        )
    }

    private fun densePathGeometryDiagnostic(first: String, second: String): DenseGeometryDiagnostic {
        val firstMeasured = SvgPathSampler.measure(first, curveSteps = 256)
        val secondMeasured = SvgPathSampler.measure(second, curveSteps = 256)
        if (firstMeasured == null || secondMeasured == null) {
            return DenseGeometryDiagnostic(false, firstMeasured?.length ?: Float.NaN, secondMeasured?.length ?: Float.NaN,
                Double.POSITIVE_INFINITY, 0.0, "unavailable", "unavailable")
        }
        fun closeEnough(a: Float, b: Float): Boolean {
            val scale = maxOf(1.0f, kotlin.math.abs(a), kotlin.math.abs(b))
            return kotlin.math.abs(a - b) <= 0.0005f * scale
        }
        var equivalent = closeEnough(firstMeasured.length, secondMeasured.length)
        var maxDeviation = 0.0
        var maxFraction = 0.0
        var maxFirst = ""
        var maxSecond = ""
        val sampleCount = 2048
        for (index in 0..sampleCount) {
            val fraction = index.toDouble() / sampleCount.toDouble()
            val a = firstMeasured.sample(firstMeasured.length * fraction.toFloat())
            val b = secondMeasured.sample(secondMeasured.length * fraction.toFloat())
            if (a == null || b == null) {
                equivalent = false
                continue
            }
            val dx = a.x.toDouble() - b.x.toDouble()
            val dy = a.y.toDouble() - b.y.toDouble()
            val deviation = kotlin.math.sqrt(dx * dx + dy * dy)
            if (deviation > maxDeviation) {
                maxDeviation = deviation
                maxFraction = fraction
                maxFirst = "${a.x},${a.y}"
                maxSecond = "${b.x},${b.y}"
            }
            if (!closeEnough(a.x, b.x) || !closeEnough(a.y, b.y)) equivalent = false
        }
        return DenseGeometryDiagnostic(
            equivalent = equivalent,
            firstLength = firstMeasured.length,
            secondLength = secondMeasured.length,
            maximumDeviation = maxDeviation,
            maximumDeviationFraction = maxFraction,
            firstPoint = maxFirst,
            secondPoint = maxSecond
        )
    }

    private fun firstTokenDifference(first: String, second: String): Triple<Int, String, String> {
        val a = tokenRegex.findAll(first).map { it.value }.toList()
        val b = tokenRegex.findAll(second).map { it.value }.toList()
        val limit = minOf(a.size, b.size)
        for (index in 0 until limit) {
            if (a[index] != b[index]) return Triple(index, a[index], b[index])
        }
        return if (a.size != b.size) {
            Triple(limit, a.getOrNull(limit) ?: "<end>", b.getOrNull(limit) ?: "<end>")
        } else Triple(-1, "<none>", "<none>")
    }

    fun runCollinearGeometrySafetyStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumMismatchWitnesses: Int = 64,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): CollinearGeometrySafetyResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumMismatchWitnesses >= 0) { "maximumMismatchWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val mismatchWitnesses = mutableListOf<CollinearGeometrySafetyWitness>()
        var generated = 0
        var valid = 0
        var rejected = 0
        var candidateChanged = 0
        var collinearChanged = 0
        var standardMismatchCount = 0
        var denseMismatchCount = 0
        var directCollinearDenseMismatchCount = 0
        var standardOnlyMismatchCount = 0
        var sourceToFirstMismatchCount = 0
        var sourceToSecondMismatchCount = 0
        var denseChecks = 0

        repeat(caseCount) { caseIndex ->
            generated++
            val source = generateDifferentialStressPath(random)
            try {
                val first = optimizePathData(source).pathData
                val stages = buildG38CandidateStages(first) ?: throw IllegalArgumentException("candidate parse failed")
                val second = optimizePathData(first).pathData
                val changed = stages.finalCandidate != first
                if (changed) candidateChanged++
                if (stages.collinearChanged) collinearChanged++

                var standardMismatch = false
                var dense = DenseGeometryDiagnostic(true, Float.NaN, Float.NaN, 0.0, 0.0, "", "")
                var directCollinearMismatch = false
                var sourceToFirstMismatch = false
                var sourceToSecondMismatch = false

                if (changed) {
                    standardMismatch = !sampledPathGeometryEquivalent(first, stages.finalCandidate)
                    dense = densePathGeometryDiagnostic(first, stages.finalCandidate)
                    denseChecks++
                    if (standardMismatch) standardMismatchCount++
                    if (!dense.equivalent) denseMismatchCount++
                    if (standardMismatch && dense.equivalent) standardOnlyMismatchCount++

                    if (stages.collinearChanged) {
                        val direct = densePathGeometryDiagnostic(stages.preCollinear, stages.postCollinear)
                        denseChecks++
                        directCollinearMismatch = !direct.equivalent
                        if (directCollinearMismatch) directCollinearDenseMismatchCount++
                    }

                    if (standardMismatch || !dense.equivalent || directCollinearMismatch) {
                        val sourceFirst = densePathGeometryDiagnostic(source, first)
                        val sourceSecond = densePathGeometryDiagnostic(source, second)
                        denseChecks += 2
                        sourceToFirstMismatch = !sourceFirst.equivalent
                        sourceToSecondMismatch = !sourceSecond.equivalent
                        if (sourceToFirstMismatch) sourceToFirstMismatchCount++
                        if (sourceToSecondMismatch) sourceToSecondMismatchCount++

                        if (mismatchWitnesses.size < maximumMismatchWitnesses) {
                            val diff = firstTokenDifference(stages.preCollinear, stages.postCollinear)
                            val preTokens = tokenRegex.findAll(stages.preCollinear).count()
                            val postTokens = tokenRegex.findAll(stages.postCollinear).count()
                            mismatchWitnesses += CollinearGeometrySafetyWitness(
                                caseNumber = caseIndex + 1,
                                source = source,
                                firstPass = first,
                                syntaxNormalized = stages.syntaxNormalized,
                                preCollinear = stages.preCollinear,
                                postCollinear = stages.postCollinear,
                                finalCandidate = stages.finalCandidate,
                                independentSecondPass = second,
                                standardMismatch = standardMismatch,
                                denseMismatch = !dense.equivalent,
                                directCollinearDenseMismatch = directCollinearMismatch,
                                sourceToFirstDenseMismatch = sourceToFirstMismatch,
                                sourceToSecondDenseMismatch = sourceToSecondMismatch,
                                firstDifferingTokenIndex = diff.first,
                                preCollinearToken = diff.second,
                                postCollinearToken = diff.third,
                                preCollinearTokenCount = preTokens,
                                postCollinearTokenCount = postTokens,
                                firstLength = dense.firstLength,
                                candidateLength = dense.secondLength,
                                lengthDelta = dense.secondLength - dense.firstLength,
                                maximumSampleDeviation = dense.maximumDeviation,
                                maximumDeviationFraction = dense.maximumDeviationFraction,
                                maximumDeviationFirstPoint = dense.firstPoint,
                                maximumDeviationCandidatePoint = dense.secondPoint
                            )
                        }
                    }
                }
                valid++
            } catch (_: Throwable) {
                rejected++
            }
            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) progressCallback(processed)
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        return CollinearGeometrySafetyResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            candidateChangedCases = candidateChanged,
            collinearChangedCases = collinearChanged,
            standardMismatchCases = standardMismatchCount,
            denseMismatchCases = denseMismatchCount,
            directCollinearDenseMismatchCases = directCollinearDenseMismatchCount,
            standardOnlyMismatchCases = standardOnlyMismatchCount,
            sourceToFirstDenseMismatchCases = sourceToFirstMismatchCount,
            sourceToSecondDenseMismatchCases = sourceToSecondMismatchCount,
            denseChecks = denseChecks,
            elapsedNanos = System.nanoTime() - started,
            mismatchWitnesses = mismatchWitnesses
        )
    }


    // G3.9 diagnostic-only investigation. Production conversion never calls this.
    data class SubdivisionInvariantGeometryWitness(
        val caseNumber: Int,
        val source: String,
        val firstPass: String,
        val preCollinear: String,
        val postCollinear: String,
        val finalCandidate: String,
        val independentSecondPass: String,
        val denseCandidateMismatch: Boolean,
        val invariantCandidateMismatch: Boolean,
        val denseDirectCollinearMismatch: Boolean,
        val invariantDirectCollinearMismatch: Boolean,
        val sourceToFirstInvariantMismatch: Boolean,
        val sourceToSecondInvariantMismatch: Boolean,
        val candidateReason: String,
        val directReason: String,
        val firstVerticesBefore: Int,
        val candidateVerticesBefore: Int,
        val firstVerticesAfter: Int,
        val candidateVerticesAfter: Int,
        val maximumMatchedVertexDeviation: Double,
        val denseMismatchCleared: Boolean
    )

    data class SubdivisionInvariantGeometryResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val candidateChangedCases: Int,
        val collinearChangedCases: Int,
        val denseCandidateMismatchCases: Int,
        val invariantCandidateMismatchCases: Int,
        val denseCandidateMismatchesCleared: Int,
        val denseDirectCollinearMismatchCases: Int,
        val invariantDirectCollinearMismatchCases: Int,
        val denseDirectCollinearMismatchesCleared: Int,
        val sourceToFirstInvariantMismatchCases: Int,
        val sourceToSecondInvariantMismatchCases: Int,
        val invariantChecks: Int,
        val elapsedNanos: Long,
        val witnesses: List<SubdivisionInvariantGeometryWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.9 subdivision-invariant geometry comparator investigation")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("G3.7 candidate changed: $candidateChangedCases")
            appendLine("Collinear-consolidation stage changed: $collinearChangedCases")
            appendLine("Dense candidate mismatches: $denseCandidateMismatchCases")
            appendLine("Subdivision-invariant candidate mismatches: $invariantCandidateMismatchCases")
            appendLine("Dense candidate mismatches cleared: $denseCandidateMismatchesCleared")
            appendLine("Dense direct-collinear mismatches: $denseDirectCollinearMismatchCases")
            appendLine("Subdivision-invariant direct-collinear mismatches: $invariantDirectCollinearMismatchCases")
            appendLine("Dense direct-collinear mismatches cleared: $denseDirectCollinearMismatchesCleared")
            appendLine("Source → pass-1 invariant mismatches: $sourceToFirstInvariantMismatchCases")
            appendLine("Source → pass-2 invariant mismatches: $sourceToSecondInvariantMismatchCases")
            appendLine("Subdivision-invariant checks: $invariantChecks")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            appendLine()
            when {
                invariantDirectCollinearMismatchCases > 0 -> {
                    appendLine("RESULT: G3.9 found geometry differences that remain after subdivision normalization.")
                    appendLine("Recommendation: keep production unchanged and inspect every invariant direct-collinear witness before modifying the comparator or consolidator.")
                }
                invariantCandidateMismatchCases > 0 || sourceToSecondInvariantMismatchCases > 0 -> {
                    appendLine("RESULT: G3.9 cleared the collinear subdivision signal but found residual invariant geometry differences elsewhere in the candidate pipeline.")
                    appendLine("Recommendation: keep production unchanged and investigate the residual invariant witnesses.")
                }
                denseCandidateMismatchCases > 0 || denseDirectCollinearMismatchCases > 0 -> {
                    appendLine("RESULT: G3.9 classified every reproduced dense mismatch as a subdivision-sensitive comparator artifact.")
                    appendLine("Recommendation: adopt the subdivision-invariant comparator for diagnostics, then rerun G3.7 before considering the convergence candidate for production.")
                }
                validCases > 0 -> {
                    appendLine("RESULT: G3.9 found no geometry mismatch in either comparator on this corpus.")
                    appendLine("Recommendation: rerun G3.7 with the subdivision-invariant comparator before any production change.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }
            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Comparator witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   Dense candidate mismatch: ${witness.denseCandidateMismatch}")
                    appendLine("   Invariant candidate mismatch: ${witness.invariantCandidateMismatch}")
                    appendLine("   Dense direct-collinear mismatch: ${witness.denseDirectCollinearMismatch}")
                    appendLine("   Invariant direct-collinear mismatch: ${witness.invariantDirectCollinearMismatch}")
                    appendLine("   Dense mismatch cleared by invariant comparator: ${witness.denseMismatchCleared}")
                    appendLine("   Source → pass-1 invariant mismatch: ${witness.sourceToFirstInvariantMismatch}")
                    appendLine("   Source → pass-2 invariant mismatch: ${witness.sourceToSecondInvariantMismatch}")
                    appendLine("   Simplified candidate reason: ${witness.candidateReason}")
                    appendLine("   Simplified direct-collinear reason: ${witness.directReason}")
                    appendLine("   Flattened vertices before simplification: ${witness.firstVerticesBefore} → ${witness.candidateVerticesBefore}")
                    appendLine("   Flattened vertices after simplification: ${witness.firstVerticesAfter} → ${witness.candidateVerticesAfter}")
                    appendLine("   Maximum matched simplified-vertex deviation: ${String.format(java.util.Locale.US, "%.9g", witness.maximumMatchedVertexDeviation)}")
                    appendLine("   Source: ${witness.source}")
                    appendLine("   Pass 1: ${witness.firstPass}")
                    appendLine("   Before collinear consolidation: ${witness.preCollinear}")
                    appendLine("   After collinear consolidation: ${witness.postCollinear}")
                    appendLine("   Final G3.7-equivalent candidate: ${witness.finalCandidate}")
                    appendLine("   Independent pass 2: ${witness.independentSecondPass}")
                }
            }
        }
    }

    fun runSubdivisionInvariantGeometryStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6316_2026L,
        maximumWitnesses: Int = 64,
        progressCallback: ((processedCases: Int) -> Unit)? = null
    ): SubdivisionInvariantGeometryResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<SubdivisionInvariantGeometryWitness>()
        var generated = 0
        var valid = 0
        var rejected = 0
        var candidateChanged = 0
        var collinearChanged = 0
        var denseCandidateMismatch = 0
        var invariantCandidateMismatch = 0
        var denseCandidateCleared = 0
        var denseDirectMismatch = 0
        var invariantDirectMismatch = 0
        var denseDirectCleared = 0
        var sourceFirstMismatch = 0
        var sourceSecondMismatch = 0
        var invariantChecks = 0

        repeat(caseCount) { caseIndex ->
            generated++
            val source = generateDifferentialStressPath(random)
            try {
                val first = optimizePathData(source).pathData
                val stages = buildG38CandidateStages(first) ?: throw IllegalArgumentException("candidate parse failed")
                val second = optimizePathData(first).pathData
                val changed = stages.finalCandidate != first
                if (changed) candidateChanged++
                if (stages.collinearChanged) collinearChanged++

                var denseCandidate = false
                var invariantCandidate = false
                var denseDirect = false
                var invariantDirect = false
                var sourceFirst = false
                var sourceSecond = false
                var candidateDiag = SvgPathSampler.SubdivisionInvariantDiagnostic(
                    true, 0, 0, 0, 0, 0, 0, 0.0, "not checked"
                )
                var directDiag = candidateDiag

                if (changed) {
                    denseCandidate = !densePathGeometryDiagnostic(first, stages.finalCandidate).equivalent
                    candidateDiag = SvgPathSampler.subdivisionInvariantGeometryDiagnostic(first, stages.finalCandidate)
                    invariantChecks++
                    invariantCandidate = !candidateDiag.equivalent
                    if (denseCandidate) denseCandidateMismatch++
                    if (invariantCandidate) invariantCandidateMismatch++
                    if (denseCandidate && !invariantCandidate) denseCandidateCleared++

                    if (stages.collinearChanged) {
                        denseDirect = !densePathGeometryDiagnostic(stages.preCollinear, stages.postCollinear).equivalent
                        directDiag = SvgPathSampler.subdivisionInvariantGeometryDiagnostic(stages.preCollinear, stages.postCollinear)
                        invariantChecks++
                        invariantDirect = !directDiag.equivalent
                        if (denseDirect) denseDirectMismatch++
                        if (invariantDirect) invariantDirectMismatch++
                        if (denseDirect && !invariantDirect) denseDirectCleared++
                    }

                    if (denseCandidate || invariantCandidate || denseDirect || invariantDirect) {
                        val sourceFirstDiag = SvgPathSampler.subdivisionInvariantGeometryDiagnostic(source, first)
                        val sourceSecondDiag = SvgPathSampler.subdivisionInvariantGeometryDiagnostic(source, second)
                        invariantChecks += 2
                        sourceFirst = !sourceFirstDiag.equivalent
                        sourceSecond = !sourceSecondDiag.equivalent
                        if (sourceFirst) sourceFirstMismatch++
                        if (sourceSecond) sourceSecondMismatch++

                        val shouldRecord = invariantCandidate || invariantDirect || sourceFirst || sourceSecond ||
                            ((denseCandidate || denseDirect) && witnesses.size < 12)
                        if (shouldRecord && witnesses.size < maximumWitnesses) {
                            witnesses += SubdivisionInvariantGeometryWitness(
                                caseNumber = caseIndex + 1,
                                source = source,
                                firstPass = first,
                                preCollinear = stages.preCollinear,
                                postCollinear = stages.postCollinear,
                                finalCandidate = stages.finalCandidate,
                                independentSecondPass = second,
                                denseCandidateMismatch = denseCandidate,
                                invariantCandidateMismatch = invariantCandidate,
                                denseDirectCollinearMismatch = denseDirect,
                                invariantDirectCollinearMismatch = invariantDirect,
                                sourceToFirstInvariantMismatch = sourceFirst,
                                sourceToSecondInvariantMismatch = sourceSecond,
                                candidateReason = candidateDiag.reason,
                                directReason = directDiag.reason,
                                firstVerticesBefore = candidateDiag.firstVerticesBefore,
                                candidateVerticesBefore = candidateDiag.secondVerticesBefore,
                                firstVerticesAfter = candidateDiag.firstVerticesAfter,
                                candidateVerticesAfter = candidateDiag.secondVerticesAfter,
                                maximumMatchedVertexDeviation = candidateDiag.maximumMatchedVertexDeviation,
                                denseMismatchCleared = (denseCandidate && !invariantCandidate) || (denseDirect && !invariantDirect)
                            )
                        }
                    }
                }
                valid++
            } catch (_: Throwable) {
                rejected++
            }

            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        if (caseCount == 0) progressCallback?.invoke(0)

        return SubdivisionInvariantGeometryResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            candidateChangedCases = candidateChanged,
            collinearChangedCases = collinearChanged,
            denseCandidateMismatchCases = denseCandidateMismatch,
            invariantCandidateMismatchCases = invariantCandidateMismatch,
            denseCandidateMismatchesCleared = denseCandidateCleared,
            denseDirectCollinearMismatchCases = denseDirectMismatch,
            invariantDirectCollinearMismatchCases = invariantDirectMismatch,
            denseDirectCollinearMismatchesCleared = denseDirectCleared,
            sourceToFirstInvariantMismatchCases = sourceFirstMismatch,
            sourceToSecondInvariantMismatchCases = sourceSecondMismatch,
            invariantChecks = invariantChecks,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }



    // G3.10 diagnostic-only investigation. Production conversion never calls this.
    data class BidirectionalPolylineWitness(
        val caseNumber:Int,val source:String,val firstPass:String,val preCollinear:String,val postCollinear:String,
        val finalCandidate:String,val independentSecondPass:String,
        val candidateMismatch:Boolean,val directCollinearMismatch:Boolean,
        val sourceToFirstMismatch:Boolean,val sourceToSecondMismatch:Boolean,
        val candidateReason:String,val directReason:String,
        val candidateFirstToSecondDeviation:Double,val candidateSecondToFirstDeviation:Double,
        val directFirstToSecondDeviation:Double,val directSecondToFirstDeviation:Double,
        val offendingPoint:String,val nearestSegment:String
    )

    data class BidirectionalPolylineResult(
        val seed:Long,val requestedCases:Int,val generatedCases:Int,val validCases:Int,val rejectedGeneratedCases:Int,
        val candidateChangedCases:Int,val collinearChangedCases:Int,
        val candidateMismatchCases:Int,val directCollinearMismatchCases:Int,
        val sourceToFirstMismatchCases:Int,val sourceToSecondMismatchCases:Int,
        val comparisons:Int,val elapsedNanos:Long,val witnesses:List<BidirectionalPolylineWitness>
    ) {
        fun toPlainTextReport():String=buildString{
            appendLine("G3.10 bidirectional polyline geometry comparator investigation")
            appendLine();appendLine("Seed: $seed");appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases");appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("G3.7 candidate changed: $candidateChangedCases")
            appendLine("Collinear-consolidation stage changed: $collinearChangedCases")
            appendLine("Bidirectional candidate mismatches: $candidateMismatchCases")
            appendLine("Bidirectional direct-collinear mismatches: $directCollinearMismatchCases")
            appendLine("Source → pass-1 bidirectional mismatches: $sourceToFirstMismatchCases")
            appendLine("Source → pass-2 bidirectional mismatches: $sourceToSecondMismatchCases")
            appendLine("Bidirectional comparisons: $comparisons")
            appendLine("Elapsed: "+String.format(java.util.Locale.US,"%.2f ms",elapsedNanos/1_000_000.0))
            appendLine()
            if(directCollinearMismatchCases>0){
                appendLine("RESULT: G3.10 found direct collinear geometry differences under bidirectional polyline distance.")
                appendLine("Recommendation: keep production unchanged and inspect every direct-collinear witness.")
            } else if(candidateMismatchCases>0||sourceToSecondMismatchCases>0){
                appendLine("RESULT: G3.10 cleared the direct collinear signal but found residual geometry differences elsewhere.")
                appendLine("Recommendation: keep production unchanged and investigate the residual witnesses.")
            } else {
                appendLine("RESULT: G3.10 classified the G3.9 direct-collinear signal as a comparator artifact on this corpus.")
                appendLine("Recommendation: adopt the bidirectional comparator for diagnostics, then rerun G3.7 before any production change.")
            }
            if(witnesses.isNotEmpty()){
                appendLine();appendLine("Bidirectional comparator witnesses")
                witnesses.forEachIndexed{i,w->
                    appendLine();appendLine("${i+1}. Case ${w.caseNumber}")
                    appendLine("   Candidate mismatch: ${w.candidateMismatch}")
                    appendLine("   Direct-collinear mismatch: ${w.directCollinearMismatch}")
                    appendLine("   Source → pass-1 mismatch: ${w.sourceToFirstMismatch}")
                    appendLine("   Source → pass-2 mismatch: ${w.sourceToSecondMismatch}")
                    appendLine("   Candidate reason: ${w.candidateReason}")
                    appendLine("   Direct reason: ${w.directReason}")
                    appendLine("   Candidate A → B max deviation: ${String.format(java.util.Locale.US,"%.9g",w.candidateFirstToSecondDeviation)}")
                    appendLine("   Candidate B → A max deviation: ${String.format(java.util.Locale.US,"%.9g",w.candidateSecondToFirstDeviation)}")
                    appendLine("   Direct A → B max deviation: ${String.format(java.util.Locale.US,"%.9g",w.directFirstToSecondDeviation)}")
                    appendLine("   Direct B → A max deviation: ${String.format(java.util.Locale.US,"%.9g",w.directSecondToFirstDeviation)}")
                    appendLine("   Offending point: ${w.offendingPoint}")
                    appendLine("   Nearest target segment: ${w.nearestSegment}")
                    appendLine("   Source: ${w.source}");appendLine("   Pass 1: ${w.firstPass}")
                    appendLine("   Before collinear consolidation: ${w.preCollinear}")
                    appendLine("   After collinear consolidation: ${w.postCollinear}")
                    appendLine("   Final G3.7-equivalent candidate: ${w.finalCandidate}")
                    appendLine("   Independent pass 2: ${w.independentSecondPass}")
                }
            }
        }
    }

    data class BidirectionalPolylinePartialState(
        val processedCases:Int=0,
        val generatedCases:Int=0,
        val validCases:Int=0,
        val rejectedGeneratedCases:Int=0,
        val candidateChangedCases:Int=0,
        val collinearChangedCases:Int=0,
        val candidateMismatchCases:Int=0,
        val directCollinearMismatchCases:Int=0,
        val sourceToFirstMismatchCases:Int=0,
        val sourceToSecondMismatchCases:Int=0,
        val comparisons:Int=0,
        val elapsedNanos:Long=0L,
        val witnesses:List<BidirectionalPolylineWitness> = emptyList()
    )

    fun runBidirectionalPolylineGeometryStressSearch(
        caseCount:Int=25_000,seed:Long=0x6316_2026L,maximumWitnesses:Int=64,
        progressCallback:((processedCases:Int)->Unit)?=null,
        controlCheckpoint:(()->Unit)?=null,
        resumeState:BidirectionalPolylinePartialState?=null,
        checkpointCallback:((BidirectionalPolylinePartialState)->Unit)?=null
    ):BidirectionalPolylineResult{
        require(caseCount>=0);require(maximumWitnesses>=0)
        val initial=resumeState ?: BidirectionalPolylinePartialState()
        require(initial.processedCases in 0..caseCount)
        val runStarted=System.nanoTime();val random=Random(seed)
        // Replaying generation is intentionally cheap and preserves the exact original corpus.
        repeat(initial.processedCases){ generateDifferentialStressPath(random) }
        val ws=initial.witnesses.take(maximumWitnesses).toMutableList()
        var generated=initial.generatedCases;var valid=initial.validCases;var rejected=initial.rejectedGeneratedCases
        var changedCount=initial.candidateChangedCases;var colCount=initial.collinearChangedCases
        var candCount=initial.candidateMismatchCases;var directCount=initial.directCollinearMismatchCases
        var sfCount=initial.sourceToFirstMismatchCases;var ssCount=initial.sourceToSecondMismatchCases
        var checks=initial.comparisons
        fun snapshot(processed:Int)=BidirectionalPolylinePartialState(
            processed,generated,valid,rejected,changedCount,colCount,candCount,directCount,
            sfCount,ssCount,checks,initial.elapsedNanos+(System.nanoTime()-runStarted),ws.toList()
        )
        for(caseIndex in initial.processedCases until caseCount){
            controlCheckpoint?.invoke()
            generated++;val source=generateDifferentialStressPath(random)
            try{
                val first=optimizePathData(source).pathData
                val stages=buildG38CandidateStages(first)?:throw IllegalArgumentException("candidate parse failed")
                val second=optimizePathData(first).pathData
                val changed=stages.finalCandidate!=first;if(changed)changedCount++;if(stages.collinearChanged)colCount++
                if(changed){
                    controlCheckpoint?.invoke()
                    val cand=SvgPathSampler.bidirectionalPolylineGeometryDiagnostic(first,stages.finalCandidate);checks++
                    val direct=if(stages.collinearChanged){
                        controlCheckpoint?.invoke();checks++
                        SvgPathSampler.bidirectionalPolylineGeometryDiagnostic(stages.preCollinear,stages.postCollinear)
                    } else cand.copy(equivalent=true,reason="not checked",maximumFirstToSecondDeviation=0.0,maximumSecondToFirstDeviation=0.0)
                    val cm=!cand.equivalent;val dm=stages.collinearChanged&&!direct.equivalent
                    if(cm)candCount++;if(dm)directCount++
                    if(cm||dm){
                        val sf=SvgPathSampler.bidirectionalPolylineGeometryDiagnostic(source,first);checks++
                        val ss=SvgPathSampler.bidirectionalPolylineGeometryDiagnostic(source,second);checks++
                        val sfm=!sf.equivalent;val ssm=!ss.equivalent;if(sfm)sfCount++;if(ssm)ssCount++
                        if(ws.size<maximumWitnesses){
                            val use=if(dm)direct else cand
                            ws+=BidirectionalPolylineWitness(caseIndex+1,source,first,stages.preCollinear,stages.postCollinear,stages.finalCandidate,second,
                                cm,dm,sfm,ssm,cand.reason,direct.reason,cand.maximumFirstToSecondDeviation,cand.maximumSecondToFirstDeviation,
                                direct.maximumFirstToSecondDeviation,direct.maximumSecondToFirstDeviation,
                                use.firstOffendingPoint.ifBlank{use.secondOffendingPoint},use.firstNearestSegment.ifBlank{use.secondNearestSegment})
                        }
                    }
                }
                valid++
            }catch(throwable:Throwable){
                if(throwable is CancellationException || throwable is InterruptedException) throw throwable
                rejected++
            }
            val processed=caseIndex+1
            if(processed==caseCount||processed%250==0){
                progressCallback?.invoke(processed)
                checkpointCallback?.invoke(snapshot(processed))
            }
        }
        if(caseCount==0){progressCallback?.invoke(0);checkpointCallback?.invoke(snapshot(0))}
        val finalState=snapshot(caseCount)
        return BidirectionalPolylineResult(seed,caseCount,finalState.generatedCases,finalState.validCases,
            finalState.rejectedGeneratedCases,finalState.candidateChangedCases,finalState.collinearChangedCases,
            finalState.candidateMismatchCases,finalState.directCollinearMismatchCases,
            finalState.sourceToFirstMismatchCases,finalState.sourceToSecondMismatchCases,
            finalState.comparisons,finalState.elapsedNanos,finalState.witnesses)
    }

    // G3.11 diagnostic-only investigation. Production conversion never calls this.
    data class OrderedCollinearTraversalDiagnostic(
        val source: String,
        val firstPass: String,
        val preCollinear: String,
        val postCollinear: String,
        val changed: Boolean,
        val consolidatedSegments: Int,
        val parseable: Boolean,
        val endpointsPreserved: Boolean,
        val orderedTraversalPreserved: Boolean,
        val traveledLengthPreserved: Boolean,
        val reversalPairsBefore: Int,
        val zeroLengthLinesBefore: Int,
        val reason: String,
        val beforeTraversalSignature: String,
        val afterTraversalSignature: String
    ) {
        val safe: Boolean
            get() = parseable && endpointsPreserved && orderedTraversalPreserved && traveledLengthPreserved
    }

    private data class OrderedTraversalAnalysis(
        val signature: String,
        val subpathEndpoints: List<String>,
        val reversalPairs: Int,
        val zeroLengthLines: Int
    )

    /** G3.12 diagnostic-only exact bookkeeping comparison. */
    internal data class OrderedTraversalPairDiagnostic(
        val parseable: Boolean,
        val endpointsPreserved: Boolean,
        val orderedTraversalPreserved: Boolean,
        val firstEndpointSummary: String,
        val secondEndpointSummary: String,
        val firstTraversalSignature: String,
        val secondTraversalSignature: String
    )


    /**
     * G3.11: independently canonicalizes ordered straight-line traversal.
     *
     * Consecutive non-zero line segments are merged in the diagnostic signature
     * only when they are exactly collinear and travel in the same direction.
     * Reversals/backtracking, zero-length segments, curves, arcs, closes, and
     * subpath boundaries remain explicit. This gives G3.11 an analytic oracle
     * that does not depend on the expensive sampled/polyline comparators used by
     * G3.8-G3.10.
     */
    private fun analyzeOrderedTraversal(pathData: String): OrderedTraversalAnalysis? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        val signature = mutableListOf<String>()
        val subpathEndpoints = mutableListOf<String>()

        data class PendingLine(
            val startX: BigDecimal,
            val startY: BigDecimal,
            var endX: BigDecimal,
            var endY: BigDecimal,
            val dx: BigDecimal,
            val dy: BigDecimal
        )

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO
        var haveSubpath = false
        var subpathClosed = false
        var pending: PendingLine? = null
        var previousLineDx: BigDecimal? = null
        var previousLineDy: BigDecimal? = null
        var reversalPairs = 0
        var zeroLengthLines = 0

        fun number(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
        fun point(x: BigDecimal, y: BigDecimal): String = "${number(x)},${number(y)}"

        fun sameStrictDirectionAndSlope(
            firstDx: BigDecimal,
            firstDy: BigDecimal,
            secondDx: BigDecimal,
            secondDy: BigDecimal
        ): Boolean {
            if ((firstDx.signum() == 0 && firstDy.signum() == 0) ||
                (secondDx.signum() == 0 && secondDy.signum() == 0)
            ) return false
            val cross = firstDx.multiply(secondDy).subtract(firstDy.multiply(secondDx))
            if (cross.compareTo(BigDecimal.ZERO) != 0) return false
            val dot = firstDx.multiply(secondDx).add(firstDy.multiply(secondDy))
            return dot.signum() > 0
        }

        fun flushPending() {
            val line = pending ?: return
            signature += "LINE:${point(line.startX, line.startY)}>${point(line.endX, line.endY)}"
            pending = null
        }

        fun finishSubpath() {
            if (!haveSubpath) return
            flushPending()
            subpathEndpoints += "${point(subpathX, subpathY)}>${point(currentX, currentY)}:${if (subpathClosed) "closed" else "open"}"
            haveSubpath = false
            subpathClosed = false
            previousLineDx = null
            previousLineDy = null
        }

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val startX = currentX
            val startY = currentY

            val endX: BigDecimal
            val endY: BigDecimal
            when (upper) {
                'M', 'L', 'T' -> {
                    endX = absolute[0]
                    endY = absolute[1]
                }
                'H' -> {
                    endX = absolute[0]
                    endY = currentY
                }
                'V' -> {
                    endX = currentX
                    endY = absolute[0]
                }
                'C' -> {
                    endX = absolute[4]
                    endY = absolute[5]
                }
                'S', 'Q' -> {
                    endX = absolute[2]
                    endY = absolute[3]
                }
                'A' -> {
                    endX = absolute[5]
                    endY = absolute[6]
                }
                'Z' -> {
                    endX = subpathX
                    endY = subpathY
                }
                else -> return null
            }

            if (upper == 'M') {
                finishSubpath()
                currentX = endX
                currentY = endY
                subpathX = endX
                subpathY = endY
                haveSubpath = true
                signature += "M:${point(endX, endY)}"
                previousLineDx = null
                previousLineDy = null
                continue
            }

            if (!haveSubpath) {
                haveSubpath = true
                subpathX = currentX
                subpathY = currentY
            }

            val isLine = upper == 'L' || upper == 'H' || upper == 'V'
            if (isLine) {
                val dx = endX.subtract(startX)
                val dy = endY.subtract(startY)
                if (dx.signum() == 0 && dy.signum() == 0) {
                    zeroLengthLines++
                    flushPending()
                    signature += "ZERO:${point(startX, startY)}"
                    previousLineDx = null
                    previousLineDy = null
                } else {
                    val prevDx = previousLineDx
                    val prevDy = previousLineDy
                    if (prevDx != null && prevDy != null) {
                        val cross = prevDx.multiply(dy).subtract(prevDy.multiply(dx))
                        val dot = prevDx.multiply(dx).add(prevDy.multiply(dy))
                        if (cross.compareTo(BigDecimal.ZERO) == 0 && dot.signum() <= 0) {
                            reversalPairs++
                        }
                    }
                    val active = pending
                    if (active != null && sameStrictDirectionAndSlope(active.dx, active.dy, dx, dy)) {
                        active.endX = endX
                        active.endY = endY
                    } else {
                        flushPending()
                        pending = PendingLine(startX, startY, endX, endY, dx, dy)
                    }
                    previousLineDx = dx
                    previousLineDy = dy
                }
            } else {
                flushPending()
                previousLineDx = null
                previousLineDy = null
                val values = absolute.joinToString(",") { number(it) }
                signature += if (upper == 'Z') "Z" else "$upper:$values"
                if (upper == 'Z') subpathClosed = true
            }

            currentX = endX
            currentY = endY
        }
        finishSubpath()

        return OrderedTraversalAnalysis(
            signature = signature.joinToString("|"),
            subpathEndpoints = subpathEndpoints,
            reversalPairs = reversalPairs,
            zeroLengthLines = zeroLengthLines
        )
    }

    /**
     * G3.12 exact endpoint/traversal bookkeeping used by diagnostic comparators.
     * This is deliberately independent of Float flattening and therefore does
     * not accumulate subdivision-dependent endpoint or path-length error.
     */
    internal fun orderedTraversalPairDiagnostic(
        first: String,
        second: String
    ): OrderedTraversalPairDiagnostic {
        val a = analyzeOrderedTraversal(first)
        val b = analyzeOrderedTraversal(second)
        if (a == null || b == null) {
            return OrderedTraversalPairDiagnostic(
                parseable = false,
                endpointsPreserved = false,
                orderedTraversalPreserved = false,
                firstEndpointSummary = a?.subpathEndpoints?.joinToString(" | ") ?: "",
                secondEndpointSummary = b?.subpathEndpoints?.joinToString(" | ") ?: "",
                firstTraversalSignature = a?.signature ?: "",
                secondTraversalSignature = b?.signature ?: ""
            )
        }
        return OrderedTraversalPairDiagnostic(
            parseable = true,
            endpointsPreserved = a.subpathEndpoints == b.subpathEndpoints,
            orderedTraversalPreserved = a.signature == b.signature,
            firstEndpointSummary = a.subpathEndpoints.joinToString(" | "),
            secondEndpointSummary = b.subpathEndpoints.joinToString(" | "),
            firstTraversalSignature = a.signature,
            secondTraversalSignature = b.signature
        )
    }

    /**
     * G3.11 ordered-traversal safety oracle.
     *
     * When [productionReplay] is true the path is first run through the normal
     * optimizer and G3.11 inspects the exact pre/post-collinear pair used by the
     * G3.7-G3.10 investigations. When false, the supplied path is parsed and
     * encoded directly before applying only the production collinear stage.
     */
    fun diagnoseOrderedCollinearTraversal(
        pathData: String,
        productionReplay: Boolean = false
    ): OrderedCollinearTraversalDiagnostic {
        val firstPass: String
        val preCollinear: String
        val postCollinear: String
        val consolidatedCount: Int

        if (productionReplay) {
            firstPass = optimizePathData(pathData).pathData
            val stages = buildG38CandidateStages(firstPass)
                ?: return OrderedCollinearTraversalDiagnostic(
                    source = pathData,
                    firstPass = firstPass,
                    preCollinear = firstPass,
                    postCollinear = firstPass,
                    changed = false,
                    consolidatedSegments = 0,
                    parseable = false,
                    endpointsPreserved = false,
                    orderedTraversalPreserved = false,
                    traveledLengthPreserved = false,
                    reversalPairsBefore = 0,
                    zeroLengthLinesBefore = 0,
                    reason = "candidate stage parse failed",
                    beforeTraversalSignature = "",
                    afterTraversalSignature = ""
                )
            preCollinear = stages.preCollinear
            postCollinear = stages.postCollinear
            consolidatedCount = if (stages.collinearChanged) 1 else 0
        } else {
            val parsed = parseNormalizedSegments(pathData)
                ?: return OrderedCollinearTraversalDiagnostic(
                    source = pathData,
                    firstPass = pathData,
                    preCollinear = pathData,
                    postCollinear = pathData,
                    changed = false,
                    consolidatedSegments = 0,
                    parseable = false,
                    endpointsPreserved = false,
                    orderedTraversalPreserved = false,
                    traveledLengthPreserved = false,
                    reversalPairsBefore = 0,
                    zeroLengthLinesBefore = 0,
                    reason = "input parse failed",
                    beforeTraversalSignature = "",
                    afterTraversalSignature = ""
                )
            firstPass = pathData
            preCollinear = encodeParsedSegments(parsed)
            val cleanup = consolidateConsecutiveCollinearLineRuns(preCollinear)
            postCollinear = cleanup.pathData
            consolidatedCount = cleanup.consolidatedCount
        }

        val before = analyzeOrderedTraversal(preCollinear)
        val after = analyzeOrderedTraversal(postCollinear)
        if (before == null || after == null) {
            return OrderedCollinearTraversalDiagnostic(
                source = pathData,
                firstPass = firstPass,
                preCollinear = preCollinear,
                postCollinear = postCollinear,
                changed = preCollinear != postCollinear,
                consolidatedSegments = consolidatedCount,
                parseable = false,
                endpointsPreserved = false,
                orderedTraversalPreserved = false,
                traveledLengthPreserved = false,
                reversalPairsBefore = before?.reversalPairs ?: 0,
                zeroLengthLinesBefore = before?.zeroLengthLines ?: 0,
                reason = "pre/post traversal analysis failed",
                beforeTraversalSignature = before?.signature ?: "",
                afterTraversalSignature = after?.signature ?: ""
            )
        }

        val endpointsPreserved = before.subpathEndpoints == after.subpathEndpoints
        val orderedPreserved = before.signature == after.signature
        // The signature collapses only exact monotonic-collinear line runs. If
        // it is identical, straight-line travel order and exact line distance
        // are mathematically identical without floating-point sqrt comparisons.
        val traveledLengthPreserved = orderedPreserved
        val reason = when {
            !endpointsPreserved -> "open/closed subpath endpoint changed"
            !orderedPreserved -> "ordered line traversal changed"
            preCollinear == postCollinear -> "collinear stage made no change"
            else -> "exact ordered traversal preserved"
        }

        return OrderedCollinearTraversalDiagnostic(
            source = pathData,
            firstPass = firstPass,
            preCollinear = preCollinear,
            postCollinear = postCollinear,
            changed = preCollinear != postCollinear,
            consolidatedSegments = consolidatedCount,
            parseable = true,
            endpointsPreserved = endpointsPreserved,
            orderedTraversalPreserved = orderedPreserved,
            traveledLengthPreserved = traveledLengthPreserved,
            reversalPairsBefore = before.reversalPairs,
            zeroLengthLinesBefore = before.zeroLengthLines,
            reason = reason,
            beforeTraversalSignature = before.signature,
            afterTraversalSignature = after.signature
        )
    }

    data class PathFixedPointWitness(
        val caseNumber: Int,
        val firstChangingStage: String,
        val stabilizedOnThirdPass: Boolean,
        val source: String,
        val firstPass: String,
        val secondPass: String,
        val thirdPass: String,
        val stageSnapshots: List<PathFixedPointStageSnapshot>
    )

    data class PathFixedPointInvestigationResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val rejectedGeneratedCases: Int,
        val alreadyFixedAfterFirstPass: Int,
        val secondPassChangedCases: Int,
        val stabilizedOnThirdPass: Int,
        val stillChangingAfterThirdPass: Int,
        val firstChangingStageCounts: Map<String, Int>,
        val elapsedNanos: Long,
        val witnesses: List<PathFixedPointWitness>
    ) {
        fun toPlainTextReport(): String = buildString {
            appendLine("G3.5 path optimizer fixed-point investigation")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid comparisons: $validCases")
            appendLine("Rejected generated cases: $rejectedGeneratedCases")
            appendLine("Already fixed after pass 1: $alreadyFixedAfterFirstPass")
            appendLine("Changed on pass 2: $secondPassChangedCases")
            appendLine("Stabilized on pass 3: $stabilizedOnThirdPass")
            appendLine("Still changing after pass 3: $stillChangingAfterThirdPass")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsedNanos / 1_000_000.0))
            appendLine()
            appendLine("First changing stage")
            val orderedStages = listOf(
                "Syntax normalization",
                "Redundant geometry cleanup",
                "Arc cleanup",
                "Curve simplification",
                "Collinear consolidation",
                "Local command shortening",
                "Global command minimization",
                "Global numeric serialization",
                "Unclassified"
            )
            orderedStages.forEach { stage ->
                val count = firstChangingStageCounts[stage] ?: 0
                if (count > 0) appendLine("• $stage: $count")
            }
            appendLine()
            if (secondPassChangedCases == 0) {
                appendLine("RESULT: every generated path was a fixed point after one optimizer pass.")
            } else {
                appendLine("RESULT: second-pass path changes were detected.")
                appendLine("Recommendation: use the first-changing-stage distribution and witnesses to repair stage ordering rather than caching pass-1 output as stable.")
            }
            if (witnesses.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnesses.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. Case ${witness.caseNumber}")
                    appendLine("   First changing stage: ${witness.firstChangingStage}")
                    appendLine("   Stabilized on pass 3: ${witness.stabilizedOnThirdPass}")
                    appendLine("   Source: ${witness.source}")
                    appendLine("   Pass 1: ${witness.firstPass}")
                    appendLine("   Pass 2: ${witness.secondPass}")
                    appendLine("   Pass 3: ${witness.thirdPass}")
                    appendLine("   Pass-2 stage trace:")
                    var previous = witness.firstPass
                    witness.stageSnapshots.forEach { snapshot ->
                        if (snapshot.pathData != previous) {
                            appendLine("     • ${snapshot.stage}: ${snapshot.pathData}")
                        }
                        previous = snapshot.pathData
                    }
                }
            }
        }
    }

    fun runPathFixedPointInvestigationStressSearch(
        caseCount: Int,
        seed: Long,
        maximumWitnesses: Int = 10,
        progressCallback: ((Int) -> Unit)? = null
    ): PathFixedPointInvestigationResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        val started = System.nanoTime()
        val random = Random(seed)
        var generated = 0
        var valid = 0
        var rejected = 0
        var fixed = 0
        var changed = 0
        var stabilizedThird = 0
        var stillChanging = 0
        val stageCounts = linkedMapOf<String, Int>()
        val witnesses = mutableListOf<PathFixedPointWitness>()

        repeat(caseCount) { caseIndex ->
            generated++
            val source = generateDifferentialStressPath(random)
            try {
                val first = optimizePathData(source).pathData
                val trace = mutableListOf<PathFixedPointStageSnapshot>()
                val second = optimizePathData(first, stageTrace = trace).pathData
                valid++
                if (second == first) {
                    fixed++
                } else {
                    changed++
                    var previous = first
                    var firstStage = "Unclassified"
                    for (snapshot in trace) {
                        if (snapshot.pathData != previous) {
                            firstStage = snapshot.stage
                            break
                        }
                        previous = snapshot.pathData
                    }
                    stageCounts[firstStage] = (stageCounts[firstStage] ?: 0) + 1
                    val third = optimizePathData(second).pathData
                    val stabilized = third == second
                    if (stabilized) stabilizedThird++ else stillChanging++
                    if (witnesses.size < maximumWitnesses) {
                        witnesses += PathFixedPointWitness(
                            caseNumber = caseIndex + 1,
                            firstChangingStage = firstStage,
                            stabilizedOnThirdPass = stabilized,
                            source = source,
                            firstPass = first,
                            secondPass = second,
                            thirdPass = third,
                            stageSnapshots = trace.toList()
                        )
                    }
                }
            } catch (_: Throwable) {
                rejected++
            }
            val processed = caseIndex + 1
            if (progressCallback != null && (processed == caseCount || processed % 250 == 0)) {
                progressCallback(processed)
            }
        }
        return PathFixedPointInvestigationResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generated,
            validCases = valid,
            rejectedGeneratedCases = rejected,
            alreadyFixedAfterFirstPass = fixed,
            secondPassChangedCases = changed,
            stabilizedOnThirdPass = stabilizedThird,
            stillChangingAfterThirdPass = stillChanging,
            firstChangingStageCounts = stageCounts,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }

    private fun generateIdempotenceDifferentialVectorXml(random: Random): String {
        val pathCount = random.nextInt(1, 4)
        val body = buildString {
            repeat(pathCount) { index ->
                val pathData = generateDifferentialStressPath(random)
                val path = "<path android:pathData=\"$pathData\" android:fillColor=\"#FF336699\"/>"

                when (random.nextInt(5)) {
                    0 -> {
                        val factor = formatBigDecimal(randomPositiveScaleFactor(random))
                        val pivotX = formatBigDecimal(randomTransformNumber(random))
                        val pivotY = formatBigDecimal(randomTransformNumber(random))
                        append(
                            "<group android:scaleX=\"$factor\" android:scaleY=\"$factor\" " +
                                "android:pivotX=\"$pivotX\" android:pivotY=\"$pivotY\">$path</group>"
                        )
                    }
                    1 -> {
                        val tx = formatBigDecimal(randomTransformNumber(random))
                        val ty = formatBigDecimal(randomTransformNumber(random))
                        append(
                            "<group android:translateX=\"$tx\" android:translateY=\"$ty\">$path</group>"
                        )
                    }
                    else -> append(path)
                }

                if (index + 1 < pathCount) append('\n')
            }
        }

        return """<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="1000" android:viewportHeight="1000">$body</vector>"""
    }

    /**
     * G2.25 Developer Tools diagnostic: differentially stress-tests the full
     * post-scale optimizer against the G2.24 narrowed post-scale pipeline.
     * Production conversion never calls this method.
     */
    fun runPostScaleDifferentialStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6215_2026L,
        maximumWitnesses: Int = 20
    ): PostScaleDifferentialSearchResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnesses >= 0) { "maximumWitnesses must be non-negative" }

        val started = System.nanoTime()
        val random = Random(seed)
        val witnesses = mutableListOf<PostScaleDifferentialWitness>()

        var generatedCases = 0
        var validCases = 0
        var invalidGeneratedCases = 0
        var byteIdenticalCount = 0
        var byteDifferenceCount = 0
        var canonicalDifferenceCount = 0
        var geometryMismatchCount = 0
        var equalLengthDifferenceCount = 0
        var fullShorterCount = 0
        var narrowedShorterCount = 0

        repeat(caseCount) { caseIndex ->
            generatedCases++
            val source = generateDifferentialStressPath(random)
            val factor = randomPositiveScaleFactor(random)
            val pivotX = randomTransformNumber(random)
            val pivotY = randomTransformNumber(random)
            val translateX = randomTransformNumber(random)
            val translateY = randomTransformNumber(random)

            val rawScaled = scalePathDataForDifferentialSearch(
                source,
                factor,
                pivotX,
                pivotY,
                translateX,
                translateY
            )

            if (rawScaled == null) {
                invalidGeneratedCases++
                return@repeat
            }

            val full = optimizePathData(rawScaled).pathData
            val narrowed = optimizePostScaleNarrowedForComparison(rawScaled)
            if (narrowed == null) {
                invalidGeneratedCases++
                return@repeat
            }

            val fullSemantics = canonicalPathSemantics(full)
            val narrowedSemantics = canonicalPathSemantics(narrowed)
            if (fullSemantics == null || narrowedSemantics == null) {
                invalidGeneratedCases++
                return@repeat
            }

            val canonicalDifference = fullSemantics != narrowedSemantics
            if (canonicalDifference) canonicalDifferenceCount++

            val geometryMismatch =
                canonicalDifference && !sampledPathGeometryEquivalent(full, narrowed)

            validCases++
            if (geometryMismatch) geometryMismatchCount++

            if (full == narrowed) {
                byteIdenticalCount++
            } else {
                byteDifferenceCount++
                when {
                    full.length < narrowed.length -> fullShorterCount++
                    narrowed.length < full.length -> narrowedShorterCount++
                    else -> equalLengthDifferenceCount++
                }
            }

            if (geometryMismatch || full != narrowed) {
                val witness = PostScaleDifferentialWitness(
                    caseIndex = caseIndex + 1,
                    scaleFactor = formatBigDecimal(factor),
                    pivotX = formatBigDecimal(pivotX),
                    pivotY = formatBigDecimal(pivotY),
                    translateX = formatBigDecimal(translateX),
                    translateY = formatBigDecimal(translateY),
                    sourcePathData = source,
                    rawScaledPathData = rawScaled,
                    fullPathData = full,
                    narrowedPathData = narrowed,
                    geometryMismatch = geometryMismatch
                )

                if (witnesses.size < maximumWitnesses) {
                    witnesses += witness
                } else if (geometryMismatch) {
                    val replaceIndex = witnesses.indexOfFirst { !it.geometryMismatch }
                    if (replaceIndex >= 0) witnesses[replaceIndex] = witness
                }
            }
        }

        return PostScaleDifferentialSearchResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generatedCases,
            validCases = validCases,
            invalidGeneratedCases = invalidGeneratedCases,
            byteIdenticalCount = byteIdenticalCount,
            byteDifferenceCount = byteDifferenceCount,
            canonicalDifferenceCount = canonicalDifferenceCount,
            geometryMismatchCount = geometryMismatchCount,
            equalLengthDifferenceCount = equalLengthDifferenceCount,
            fullShorterCount = fullShorterCount,
            narrowedShorterCount = narrowedShorterCount,
            elapsedNanos = System.nanoTime() - started,
            witnesses = witnesses
        )
    }

    data class PostScaleStageAddbackWitness(
        val pipelineName: String,
        val caseIndex: Int,
        val scaleFactor: String,
        val sourcePathData: String,
        val rawScaledPathData: String,
        val fullPathData: String,
        val candidatePathData: String,
        val sampledGeometryMismatch: Boolean
    )

    data class PostScaleStageAddbackPipelineResult(
        val pipelineName: String,
        val comparedCases: Int,
        val byteIdenticalCount: Int,
        val byteDifferenceCount: Int,
        val canonicalDifferenceCount: Int,
        val geometrySamplesChecked: Int,
        val sampledGeometryMismatchCount: Int,
        val equalLengthDifferenceCount: Int,
        val fullShorterCount: Int,
        val candidateShorterCount: Int,
        val candidateNanos: Long,
        val witnesses: List<PostScaleStageAddbackWitness>
    ) {
        val candidateMilliseconds: Double
            get() = candidateNanos / 1_000_000.0
    }

    data class PostScaleStageAddbackSearchResult(
        val seed: Long,
        val requestedCases: Int,
        val generatedCases: Int,
        val validCases: Int,
        val invalidGeneratedCases: Int,
        val fullOptimizerNanos: Long,
        val elapsedNanos: Long,
        val pipelineResults: List<PostScaleStageAddbackPipelineResult>
    ) {
        val elapsedMilliseconds: Double
            get() = elapsedNanos / 1_000_000.0

        fun toPlainTextReport(): String = buildString {
            appendLine("G2.26 post-scale stage-addback differential stress search")
            appendLine()
            appendLine("Seed: $seed")
            appendLine("Requested cases: $requestedCases")
            appendLine("Generated cases: $generatedCases")
            appendLine("Valid source cases: $validCases")
            appendLine("Rejected generated cases: $invalidGeneratedCases")
            appendLine(
                "Full optimizer time: " +
                    String.format(java.util.Locale.US, "%.2f ms", fullOptimizerNanos / 1_000_000.0)
            )
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsedMilliseconds)
            )

            pipelineResults.forEach { result ->
                appendLine()
                appendLine(result.pipelineName)
                appendLine("  Compared: ${result.comparedCases}")
                appendLine("  Byte-identical: ${result.byteIdenticalCount}")
                appendLine("  Byte differences: ${result.byteDifferenceCount}")
                appendLine("  Canonical differences: ${result.canonicalDifferenceCount}")
                appendLine("  Geometry samples checked: ${result.geometrySamplesChecked}")
                appendLine("  Sampled geometry mismatches: ${result.sampledGeometryMismatchCount}")
                appendLine("  Equal-length differences: ${result.equalLengthDifferenceCount}")
                appendLine("  Full optimizer shorter: ${result.fullShorterCount}")
                appendLine("  Candidate shorter: ${result.candidateShorterCount}")
                appendLine(
                    "  Candidate time: " +
                        String.format(java.util.Locale.US, "%.2f ms", result.candidateMilliseconds)
                )
            }

            val exact = pipelineResults.firstOrNull {
                it.comparedCases == validCases &&
                    it.byteDifferenceCount == 0
            }
            appendLine()
            when {
                exact != null -> {
                    appendLine("RESULT: an exact stage-addback pipeline was found.")
                    appendLine("Smallest exact pipeline: ${exact.pipelineName}")
                    appendLine(
                        "Recommendation: validate this pipeline with the locked suite and a " +
                            "dedicated production-fallback trial before activation."
                    )
                }
                else -> {
                    appendLine("RESULT: no tested reduced pipeline was byte-identical on this seed.")
                    appendLine(
                        "Recommendation: keep the full post-scale optimizer authoritative and " +
                            "inspect the earliest remaining difference class."
                    )
                }
            }

            val witnessList = pipelineResults.flatMap { it.witnesses }
            if (witnessList.isNotEmpty()) {
                appendLine()
                appendLine("Witnesses")
                witnessList.forEachIndexed { index, witness ->
                    appendLine()
                    appendLine("${index + 1}. ${witness.pipelineName} — case ${witness.caseIndex}")
                    appendLine("   Sampled geometry mismatch: ${witness.sampledGeometryMismatch}")
                    appendLine("   Scale: ${witness.scaleFactor}")
                    appendLine("   Source: ${witness.sourcePathData}")
                    appendLine("   Raw scaled: ${witness.rawScaledPathData}")
                    appendLine("   Full: ${witness.fullPathData}")
                    appendLine("   Candidate: ${witness.candidatePathData}")
                }
            }
        }
    }

    /**
     * G2.26 Developer Tools diagnostic. Evaluates progressively richer
     * post-scale pipelines against the unchanged full optimizer over one
     * deterministic generated corpus.
     *
     * Geometry sampling is intentionally bounded per pipeline because exact
     * byte identity is the production-switch criterion; sampling is diagnostic
     * context for non-identical candidates, not an acceptance substitute.
     */
    fun runPostScaleStageAddbackStressSearch(
        caseCount: Int = 25_000,
        seed: Long = 0x6216_2026L,
        maximumWitnessesPerPipeline: Int = 2,
        maximumGeometrySamplesPerPipeline: Int = 2_000
    ): PostScaleStageAddbackSearchResult {
        require(caseCount >= 0) { "caseCount must be non-negative" }
        require(maximumWitnessesPerPipeline >= 0) { "maximumWitnessesPerPipeline must be non-negative" }
        require(maximumGeometrySamplesPerPipeline >= 0) { "maximumGeometrySamplesPerPipeline must be non-negative" }

        data class MutablePipelineStats(
            var compared: Int = 0,
            var identical: Int = 0,
            var differences: Int = 0,
            var canonicalDifferences: Int = 0,
            var geometrySamples: Int = 0,
            var geometryMismatches: Int = 0,
            var equalLength: Int = 0,
            var fullShorter: Int = 0,
            var candidateShorter: Int = 0,
            var nanos: Long = 0,
            val witnesses: MutableList<PostScaleStageAddbackWitness> = mutableListOf()
        )

        val started = System.nanoTime()
        val random = Random(seed)
        val pipelines = PostScaleAddbackPipeline.entries
        val stats = pipelines.associateWith { MutablePipelineStats() }

        var generatedCases = 0
        var validCases = 0
        var invalidGeneratedCases = 0
        var fullOptimizerNanos = 0L

        repeat(caseCount) { caseIndex ->
            generatedCases++
            val source = generateDifferentialStressPath(random)
            val factor = randomPositiveScaleFactor(random)
            val pivotX = randomTransformNumber(random)
            val pivotY = randomTransformNumber(random)
            val translateX = randomTransformNumber(random)
            val translateY = randomTransformNumber(random)

            val rawScaled = scalePathDataForDifferentialSearch(
                source,
                factor,
                pivotX,
                pivotY,
                translateX,
                translateY
            )
            if (rawScaled == null) {
                invalidGeneratedCases++
                return@repeat
            }

            val fullStart = System.nanoTime()
            val full = optimizePathData(rawScaled).pathData
            fullOptimizerNanos += System.nanoTime() - fullStart
            val fullSemantics = canonicalPathSemantics(full)
            if (fullSemantics == null) {
                invalidGeneratedCases++
                return@repeat
            }

            validCases++
            pipelines.forEach { pipeline ->
                val pipelineStats = stats.getValue(pipeline)
                val candidateStart = System.nanoTime()
                val candidate = optimizePostScaleStageAddbackForComparison(rawScaled, pipeline)
                pipelineStats.nanos += System.nanoTime() - candidateStart
                if (candidate == null) return@forEach

                val candidateSemantics = canonicalPathSemantics(candidate) ?: return@forEach
                pipelineStats.compared++

                val identical = candidate == full
                if (identical) {
                    pipelineStats.identical++
                } else {
                    pipelineStats.differences++
                    when {
                        full.length < candidate.length -> pipelineStats.fullShorter++
                        candidate.length < full.length -> pipelineStats.candidateShorter++
                        else -> pipelineStats.equalLength++
                    }
                }

                val canonicalDifference = candidateSemantics != fullSemantics
                if (canonicalDifference) pipelineStats.canonicalDifferences++

                var sampledMismatch = false
                if (
                    canonicalDifference &&
                    pipelineStats.geometrySamples < maximumGeometrySamplesPerPipeline
                ) {
                    pipelineStats.geometrySamples++
                    sampledMismatch = !sampledPathGeometryEquivalent(full, candidate)
                    if (sampledMismatch) pipelineStats.geometryMismatches++
                }

                if (
                    !identical &&
                    pipelineStats.witnesses.size < maximumWitnessesPerPipeline
                ) {
                    pipelineStats.witnesses += PostScaleStageAddbackWitness(
                        pipelineName = pipeline.displayName,
                        caseIndex = caseIndex + 1,
                        scaleFactor = formatBigDecimal(factor),
                        sourcePathData = source,
                        rawScaledPathData = rawScaled,
                        fullPathData = full,
                        candidatePathData = candidate,
                        sampledGeometryMismatch = sampledMismatch
                    )
                }
            }
        }

        val results = pipelines.map { pipeline ->
            val s = stats.getValue(pipeline)
            PostScaleStageAddbackPipelineResult(
                pipelineName = pipeline.displayName,
                comparedCases = s.compared,
                byteIdenticalCount = s.identical,
                byteDifferenceCount = s.differences,
                canonicalDifferenceCount = s.canonicalDifferences,
                geometrySamplesChecked = s.geometrySamples,
                sampledGeometryMismatchCount = s.geometryMismatches,
                equalLengthDifferenceCount = s.equalLength,
                fullShorterCount = s.fullShorter,
                candidateShorterCount = s.candidateShorter,
                candidateNanos = s.nanos,
                witnesses = s.witnesses.toList()
            )
        }

        return PostScaleStageAddbackSearchResult(
            seed = seed,
            requestedCases = caseCount,
            generatedCases = generatedCases,
            validCases = validCases,
            invalidGeneratedCases = invalidGeneratedCases,
            fullOptimizerNanos = fullOptimizerNanos,
            elapsedNanos = System.nanoTime() - started,
            pipelineResults = results
        )
    }

    private fun sampledPathGeometryEquivalent(first: String, second: String): Boolean {
        val firstMeasured = SvgPathSampler.measure(first, curveSteps = 64) ?: return false
        val secondMeasured = SvgPathSampler.measure(second, curveSteps = 64) ?: return false

        fun closeEnough(a: Float, b: Float): Boolean {
            val scale = maxOf(1.0f, kotlin.math.abs(a), kotlin.math.abs(b))
            return kotlin.math.abs(a - b) <= 0.0005f * scale
        }

        if (!closeEnough(firstMeasured.length, secondMeasured.length)) return false

        val sampleCount = 96
        for (index in 0..sampleCount) {
            val fraction = index.toFloat() / sampleCount.toFloat()
            val firstSample = firstMeasured.sample(firstMeasured.length * fraction) ?: return false
            val secondSample = secondMeasured.sample(secondMeasured.length * fraction) ?: return false
            if (!closeEnough(firstSample.x, secondSample.x) ||
                !closeEnough(firstSample.y, secondSample.y)
            ) return false
        }
        return true
    }

    private fun randomPositiveScaleFactor(random: Random): BigDecimal {
        val common = listOf(
            "0.1", "0.125", "0.25", "0.3333333333333333", "0.5", "0.75",
            "1", "1.25", "1.5", "2", "2.5", "3", "4", "8", "10",
            "0.000001", "100000"
        )
        return if (random.nextInt(100) < 70) {
            BigDecimal(common[random.nextInt(common.size)])
        } else {
            BigDecimal(random.nextInt(1, 10_001)).movePointLeft(random.nextInt(1, 5))
        }
    }

    private fun randomTransformNumber(random: Random): BigDecimal {
        return if (random.nextInt(100) < 45) BigDecimal.ZERO else randomStressNumber(random)
    }

    private fun scalePathDataForDifferentialSearch(
        pathData: String,
        factor: BigDecimal,
        pivotX: BigDecimal,
        pivotY: BigDecimal,
        translateX: BigDecimal,
        translateY: BigDecimal
    ): String? {
        if (factor.compareTo(BigDecimal.ZERO) <= 0) return null
        val tokens = tokenRegex.findAll(pathData).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        val segments = parseNormalizedSegmentsFromTokens(tokens) ?: return null
        if (segments.isEmpty()) return null

        fun scaledX(value: BigDecimal): BigDecimal =
            pivotX.add(value.subtract(pivotX).multiply(factor)).add(translateX)
        fun scaledY(value: BigDecimal): BigDecimal =
            pivotY.add(value.subtract(pivotY).multiply(factor)).add(translateY)

        val output = StringBuilder(pathData.length + 24)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)
            val scaled = when (upper) {
                'M', 'L', 'T' -> listOf(scaledX(absolute[0]), scaledY(absolute[1]))
                'H' -> listOf(scaledX(absolute[0]))
                'V' -> listOf(scaledY(absolute[0]))
                'C' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3]),
                    scaledX(absolute[4]), scaledY(absolute[5])
                )
                'S', 'Q' -> listOf(
                    scaledX(absolute[0]), scaledY(absolute[1]),
                    scaledX(absolute[2]), scaledY(absolute[3])
                )
                'A' -> listOf(
                    absolute[0].multiply(factor),
                    absolute[1].multiply(factor),
                    absolute[2], absolute[3], absolute[4],
                    scaledX(absolute[5]), scaledY(absolute[6])
                )
                'Z' -> emptyList()
                else -> return null
            }

            output.append(upper)
            scaled.forEachIndexed { index, value ->
                if (index > 0) output.append(',')
                output.append(formatBigDecimal(value))
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }
        return output.toString()
    }

    private fun generateDifferentialStressPath(
        random: Random
    ): String {
        val output = StringBuilder()
        var currentX = randomStressNumber(random)
        var currentY = randomStressNumber(random)
        var subpathX = currentX
        var subpathY = currentY

        output.append('M')
        appendStressNumber(output, currentX)
        output.append(',')
        appendStressNumber(output, currentY)

        val segmentCount = random.nextInt(2, 15)

        repeat(segmentCount) {
            when (random.nextInt(100)) {
                in 0..39 -> {
                    val nextX = randomStressNumber(random)
                    val nextY = randomStressNumber(random)
                    output.append('L')
                    appendStressNumber(output, nextX)
                    output.append(',')
                    appendStressNumber(output, nextY)
                    currentX = nextX
                    currentY = nextY
                }

                in 40..52 -> {
                    val nextX = randomStressNumber(random)
                    val nextY = randomStressNumber(random)
                    output.append('Q')
                    appendStressNumber(output, randomStressNumber(random))
                    output.append(',')
                    appendStressNumber(output, randomStressNumber(random))
                    output.append(',')
                    appendStressNumber(output, nextX)
                    output.append(',')
                    appendStressNumber(output, nextY)
                    currentX = nextX
                    currentY = nextY
                }

                in 53..67 -> {
                    val nextX = randomStressNumber(random)
                    val nextY = randomStressNumber(random)
                    output.append('C')
                    repeat(2) {
                        appendStressNumber(output, randomStressNumber(random))
                        output.append(',')
                        appendStressNumber(output, randomStressNumber(random))
                        output.append(',')
                    }
                    appendStressNumber(output, nextX)
                    output.append(',')
                    appendStressNumber(output, nextY)
                    currentX = nextX
                    currentY = nextY
                }

                in 68..81 -> {
                    val nextX = randomStressNumber(random)
                    val nextY = randomStressNumber(random)
                    val radiusX = randomPositiveStressNumber(random)
                    val radiusY = randomPositiveStressNumber(random)
                    val rotation = listOf(
                        BigDecimal.ZERO,
                        BigDecimal("45"),
                        BigDecimal("90"),
                        BigDecimal("180"),
                        BigDecimal("270"),
                        BigDecimal("360")
                    )[random.nextInt(6)]

                    output.append('A')
                    appendStressNumber(output, radiusX)
                    output.append(',')
                    appendStressNumber(output, radiusY)
                    output.append(',')
                    appendStressNumber(output, rotation)
                    output.append(',')
                    output.append(if (random.nextBoolean()) '1' else '0')
                    output.append(',')
                    output.append(if (random.nextBoolean()) '1' else '0')
                    output.append(',')
                    appendStressNumber(output, nextX)
                    output.append(',')
                    appendStressNumber(output, nextY)
                    currentX = nextX
                    currentY = nextY
                }

                in 82..89 -> {
                    val nextX = randomStressNumber(random)
                    output.append('H')
                    appendStressNumber(output, nextX)
                    currentX = nextX
                }

                in 90..95 -> {
                    val nextY = randomStressNumber(random)
                    output.append('V')
                    appendStressNumber(output, nextY)
                    currentY = nextY
                }

                else -> {
                    if (
                        currentX.compareTo(subpathX) != 0 ||
                        currentY.compareTo(subpathY) != 0
                    ) {
                        output.append('Z')
                        currentX = subpathX
                        currentY = subpathY
                    }

                    val nextX = randomStressNumber(random)
                    val nextY = randomStressNumber(random)
                    output.append('M')
                    appendStressNumber(output, nextX)
                    output.append(',')
                    appendStressNumber(output, nextY)
                    currentX = nextX
                    currentY = nextY
                    subpathX = nextX
                    subpathY = nextY
                }
            }
        }

        return output.toString()
    }

    private fun randomStressNumber(random: Random): BigDecimal {
        val curated = arrayOf(
            "0", "0.001", "-0.001", "0.00390625", "-0.00390625",
            "0.015625", "-0.015625", "0.03125", "-0.03125",
            "0.0625", "-0.0625", "0.125", "-0.125", "0.25", "-0.25",
            "0.5", "-0.5", "1", "-1", "2", "-2", "5", "-5",
            "10", "-10", "99", "-99", "100", "-100",
            "999", "-999", "1000", "-1000",
            "100000", "-100000", "1000000", "-1000000"
        )

        if (random.nextInt(100) < 70) {
            return BigDecimal(curated[random.nextInt(curated.size)])
        }

        val unscaled = random.nextInt(-1_000_000, 1_000_001)
        val scale = random.nextInt(0, 7)
        return BigDecimal.valueOf(unscaled.toLong(), scale)
            .stripTrailingZeros()
    }

    private fun randomPositiveStressNumber(random: Random): BigDecimal {
        var value = randomStressNumber(random).abs()
        if (value.compareTo(BigDecimal.ZERO) == 0) {
            value = BigDecimal.ONE
        }
        return value
    }

    private fun appendStressNumber(
        output: StringBuilder,
        value: BigDecimal
    ) {
        output.append(value.stripTrailingZeros().toPlainString())
    }

    private data class PathResult(
        val pathData: String,
        val repeatedCommandsRemoved: Int,
        val redundantNonDrawingSegmentsRemoved: Int,
        val collinearLineSegmentsConsolidated: Int,
        val straightBezierCurvesSimplified: Int,
        val degenerateArcsSimplified: Int,
        val smoothBezierShorthandsSelected: Int,
        val cubicCurvesReducedToQuadratic: Int,
        val arcRotationsCanonicalized: Int,
        val arcRadiiCanonicalized: Int,
        val arcHalfTurnRotationsReduced: Int,
        val arcAxesSwappedForSize: Int,
        val arcRepresentationsGloballyMinimized: Int,
        val commandSequencesGloballyMinimized: Int,
        val implicitLineTosAfterMoveSelected: Int,
        val repeatedShorthandCurveCommandsOmitted: Int,
        val repeatedFullCurveCommandsOmitted: Int,
        val repeatedArcCommandsOmitted: Int,
        val scientificNotationValuesSelected: Int,
        val numbersNormalized: Int,
        val shorterCommandFormsSelected: Int = 0,
        val relativeCommandsSelected: Int = 0,
        val axisCommandsSelected: Int = 0,
        val globallyOptimizedNumericPaths: Int = 0,
        val h23SyntaxNormalizationCharacterDelta: Int = 0,
        val h23RedundantGeometryCharacterDelta: Int = 0,
        val h23ArcCleanupCharacterDelta: Int = 0,
        val h23CurveSimplificationCharacterDelta: Int = 0,
        val h23CollinearConsolidationCharacterDelta: Int = 0,
        val h23LocalCommandShorteningCharacterDelta: Int = 0,
        val h23GlobalCommandMinimizationCharacterDelta: Int = 0,
        val h23GlobalNumericSerializationCharacterDelta: Int = 0,
    )


    private data class PathSyntaxProfiling(
        var tokenizationNormalizationNanos: Long = 0,
        var geometryCleanupNanos: Long = 0,
        var redundantSegmentCleanupNanos: Long = 0,
        var arcCleanupNanos: Long = 0,
        var curveSimplificationNanos: Long = 0,
        var curveCubicToQuadraticNanos: Long = 0,
        var curveCubicParseSetupNanos: Long = 0,
        var curveCubicScanNanos: Long = 0,
        var curveCubicRebuildValidationNanos: Long = 0,
        var curveCubicRebuildNanos: Long = 0,
        var curveCubicValidationNanos: Long = 0,
        var curveStraightBezierNanos: Long = 0,
        var curveStraightParseSetupNanos: Long = 0,
        var curveStraightScanNanos: Long = 0,
        var curveStraightRebuildValidationNanos: Long = 0,
        var curveStraightRebuildNanos: Long = 0,
        var curveStraightValidationNanos: Long = 0,
        var curveParseCalls: Int = 0,
        var curveDuplicateParseInputs: Int = 0,
        var curveSecondPassReparsedUnchangedInput: Int = 0,
        var curveCubicChangedPaths: Int = 0,
        var curveStraightChangedPaths: Int = 0,
        var curveRebuildAttempts: Int = 0,
        var curveRebuildNoOpResults: Int = 0,
        var curveValidationCalls: Int = 0,
        var curveValidationAccepted: Int = 0,
        var curveValidationRejected: Int = 0,
        var curveRebuildRejectedForSize: Int = 0,
        var collinearConsolidationNanos: Long = 0,
        var commandMinimizationNanos: Long = 0,
        var commandLocalShorteningNanos: Long = 0,
        var commandLocalParseSetupNanos: Long = 0,
        var commandLocalAbsoluteRelativeCandidateNanos: Long = 0,
        var commandLocalAxisCandidateNanos: Long = 0,
        var commandLocalSmoothShorthandCandidateNanos: Long = 0,
        var commandLocalEncodingSelectionNanos: Long = 0,
        var commandLocalNumericSerializationNanos: Long = 0,
        var commandLocalSeparatorCalculationNanos: Long = 0,
        var commandLocalCommandOmissionNanos: Long = 0,
        var commandLocalStringConstructionNanos: Long = 0,
        var commandLocalWinnerSelectionNanos: Long = 0,
        var commandLocalStateBookkeepingNanos: Long = 0,
        var commandLocalNumericSerializationCalls: Int = 0,
        var commandLocalNumericSerializationCacheHits: Int = 0,
        var commandLocalNumericSerializationUniqueValues: Int = 0,
        var commandGlobalParseSetupNanos: Long = 0,
        var commandGlobalCandidateGenerationNanos: Long = 0,
        var commandGlobalDynamicProgrammingNanos: Long = 0,
        var commandGlobalTransitionEvaluationNanos: Long = 0,
        var commandGlobalSeparatorOmissionCostNanos: Long = 0,
        var commandGlobalSegmentEncodingNanos: Long = 0,
        var commandGlobalStateCreationNanos: Long = 0,
        var commandGlobalBestStateComparisonNanos: Long = 0,
        var commandGlobalReconstructionNanos: Long = 0,
        var commandGlobalStateKeyCreationNanos: Long = 0,
        var commandGlobalStateKeyFieldPreparationNanos: Long = 0,
        var commandGlobalStateKeyPreviousCommandNanos: Long = 0,
        var commandGlobalStateKeyPreviousNumberNanos: Long = 0,
        var commandGlobalStateKeyAxisDirectionNanos: Long = 0,
        var commandGlobalStateKeyAllocationNanos: Long = 0,
        var commandGlobalStateStringConcatenationNanos: Long = 0,
        var commandGlobalStateMetadataPropagationNanos: Long = 0,
        var commandGlobalStatePathAllocationNanos: Long = 0,
        var commandGlobalBestStateMapLookupNanos: Long = 0,
        var commandGlobalBestStateDecisionNanos: Long = 0,
        var commandGlobalBestStateReplacementNanos: Long = 0,
        var commandGlobalStateMapLookupCalls: Int = 0,
        var commandGlobalStateMapLookupHits: Int = 0,
        var commandGlobalStateMapLookupMisses: Int = 0,
        var commandGlobalStateMapInsertions: Int = 0,
        var commandGlobalStateMapReplacements: Int = 0,
        var commandGlobalSegmentEncodingRequests: Int = 0,
        var commandGlobalSegmentEncodingCacheHits: Int = 0,
        var commandGlobalSegmentEncodingUniqueKeys: Int = 0,
        var numericSerializationNanos: Long = 0
    )

    private fun registerFinalPassStablePaths(
        xml: String,
        cache: PathOptimizationCache
    ): Int {
        val finalPathData = linkedSetOf<String>()
        pathDataAttributeRegex.findAll(xml).forEach { match ->
            finalPathData += match.groupValues[1]
        }
        finalPathData.forEach { pathData ->
            cache.stableOutputs[pathData] = stableReusePathResult(pathData)
        }
        return finalPathData.size
    }

    private data class I41FixedPointCertificate(
        val predictedFixed: Boolean,
        val rejectionReason: String = ""
    )

    /**
     * I4.1 diagnostic-only conservative pre-check for pass-2 PathData.
     *
     * This deliberately inspects only cheap lexical/command invariants. It
     * never replaces the real optimizer in I4.1; the full optimizer still runs
     * afterward so every prediction can be classified against ground truth.
     */
    private fun i41CheapFixedPointCertificate(pathData: String): I41FixedPointCertificate {
        if (pathData != pathData.trim() || pathData.any { it.isWhitespace() }) {
            return I41FixedPointCertificate(false, "whitespace")
        }

        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return I41FixedPointCertificate(false, "lexical")
        }

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return I41FixedPointCertificate(false, "lexical")
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return I41FixedPointCertificate(false, "lexical")
        }

        var activeCommand: Char? = null
        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                if (command.uppercaseChar() in charArrayOf('A', 'C', 'Q', 'S', 'T')) {
                    return I41FixedPointCertificate(false, "complexCommand")
                }
                if (
                    activeCommand == command &&
                    command.uppercaseChar() !in charArrayOf('M', 'Z')
                ) {
                    return I41FixedPointCertificate(false, "explicitRepeat")
                }
                activeCommand = command
            } else {
                val canonical = token.toBigDecimalOrNull()?.let(::formatPathNumber)
                    ?: normalizeNumber(token)
                if (canonical != token) {
                    return I41FixedPointCertificate(false, "numericSpelling")
                }
            }
        }

        return I41FixedPointCertificate(true)
    }

    private data class I43ComplexCertificate(
        val predictedFixed: Boolean,
        val family: String = "",
        val rejectionReason: String = ""
    )

    /**
     * I4.3 diagnostic-only expansion for paths rejected by I4.2 solely because
     * they contain complex commands. This intentionally admits only explicit
     * C/Q/A families. Reflective S/T shorthand remains out of scope because
     * its canonicality depends on preceding control-point state.
     *
     * The full optimizer still runs afterward and supplies ground truth.
     */
    private fun i43ComplexFixedPointCertificate(pathData: String): I43ComplexCertificate {
        if (pathData != pathData.trim() || pathData.any { it.isWhitespace() }) {
            return I43ComplexCertificate(false, rejectionReason = "whitespace")
        }

        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return I43ComplexCertificate(false, rejectionReason = "lexical")
        }

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return I43ComplexCertificate(false, rejectionReason = "lexical")
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return I43ComplexCertificate(false, rejectionReason = "lexical")
        }

        val complexFamilies = linkedSetOf<Char>()
        var activeCommand: Char? = null
        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                when (command.uppercaseChar()) {
                    'S', 'T' -> {
                        return I43ComplexCertificate(
                            false,
                            rejectionReason = "reflectiveShorthand"
                        )
                    }
                    'C', 'Q', 'A' -> complexFamilies += command.uppercaseChar()
                }

                if (
                    activeCommand == command &&
                    command.uppercaseChar() !in charArrayOf('M', 'Z')
                ) {
                    return I43ComplexCertificate(
                        false,
                        rejectionReason = "explicitRepeat"
                    )
                }
                activeCommand = command
            } else {
                val canonical = token.toBigDecimalOrNull()?.let(::formatPathNumber)
                    ?: normalizeNumber(token)
                if (canonical != token) {
                    return I43ComplexCertificate(
                        false,
                        rejectionReason = "numericSpelling"
                    )
                }
            }
        }

        if (complexFamilies.isEmpty()) {
            return I43ComplexCertificate(false, rejectionReason = "notComplex")
        }

        val family = when {
            complexFamilies.size > 1 -> "mixed"
            'C' in complexFamilies -> "cubic"
            'Q' in complexFamilies -> "quadratic"
            'A' in complexFamilies -> "arc"
            else -> "mixed"
        }
        return I43ComplexCertificate(true, family = family)
    }

    private fun optimizePathDataCached(
        pathData: String,
        cache: PathOptimizationCache,
        validationPass: Boolean,
        profiling: PathSyntaxProfiling? = null
    ): PathResult {
        if (validationPass) {
            val stable = cache.stableOutputs[pathData]
            if (stable != null) {
                cache.totalHits++
                cache.validationHits++
                cache.validationStableOutputHits++
                return stable
            }
        }

        val cached = cache.values[pathData]
        if (cached != null) {
            cache.totalHits++
            if (validationPass) {
                cache.validationHits++
                cache.validationRegularHits++
            }
            return cached
        }

        cache.totalMisses++
        if (validationPass) cache.validationMisses++
        return optimizePathData(pathData, profiling).also { result ->
            cache.values[pathData] = result
            if (!validationPass) {
                cache.stableOutputs.putIfAbsent(
                    result.pathData,
                    stableReusePathResult(result.pathData)
                )
            }
        }
    }

    private fun stableReusePathResult(pathData: String): PathResult =
        PathResult(
            pathData = pathData,
            repeatedCommandsRemoved = 0,
            redundantNonDrawingSegmentsRemoved = 0,
            collinearLineSegmentsConsolidated = 0,
            straightBezierCurvesSimplified = 0,
            degenerateArcsSimplified = 0,
            smoothBezierShorthandsSelected = 0,
            cubicCurvesReducedToQuadratic = 0,
            arcRotationsCanonicalized = 0,
            arcRadiiCanonicalized = 0,
            arcHalfTurnRotationsReduced = 0,
            arcAxesSwappedForSize = 0,
            arcRepresentationsGloballyMinimized = 0,
            commandSequencesGloballyMinimized = 0,
            implicitLineTosAfterMoveSelected = 0,
            repeatedShorthandCurveCommandsOmitted = 0,
            repeatedFullCurveCommandsOmitted = 0,
            repeatedArcCommandsOmitted = 0,
            scientificNotationValuesSelected = 0,
            numbersNormalized = 0,
            shorterCommandFormsSelected = 0,
            relativeCommandsSelected = 0,
            axisCommandsSelected = 0,
            globallyOptimizedNumericPaths = 0
        )

    data class PathFixedPointStageSnapshot(
        val stage: String,
        val pathData: String
    )

    private fun optimizePathData(
        pathData: String,
        profiling: PathSyntaxProfiling? = null,
        stageTrace: MutableList<PathFixedPointStageSnapshot>? = null
    ): PathResult {
        val tokenizationStartTime = System.nanoTime()
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return PathResult(pathData.trim(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        // If tokenization skipped anything other than legal separators, preserve the
        // original value rather than risking a malformed-path rewrite.
        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return PathResult(pathData, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return PathResult(pathData, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        }

        val output = StringBuilder(pathData.length)
        var activeCommand: Char? = null
        var previousWasNumber = false
        var repeatedCommandsRemoved = 0
        var repeatedShorthandCommandsOmittedInitially = 0
        var repeatedFullCurveCommandsOmittedInitially = 0
        var repeatedArcCommandsOmittedInitially = 0
        var numbersNormalized = 0

        for (match in matches) {
            val token = match.value
            if (isCommand(token)) {
                val command = token[0]
                val canUseImplicitRepeat =
                    activeCommand == command && command !in charArrayOf('M', 'm', 'Z', 'z')

                if (canUseImplicitRepeat) {
                    repeatedCommandsRemoved++
                    if (command.uppercaseChar() in charArrayOf('S', 'T')) {
                        repeatedShorthandCommandsOmittedInitially++
                    }
                    if (command.uppercaseChar() in charArrayOf('C', 'Q')) {
                        repeatedFullCurveCommandsOmittedInitially++
                    }
                    if (command.uppercaseChar() == 'A') {
                        repeatedArcCommandsOmittedInitially++
                    }
                    // Keep previousWasNumber=true so the first parameter of the
                    // implicit command is separated from the preceding parameter.
                } else {
                    output.append(command)
                    previousWasNumber = false
                }

                activeCommand = command
            } else {
                val normalized = token.toBigDecimalOrNull()
                    ?.let(::formatPathNumber)
                    ?: normalizeNumber(token)
                if (normalized != token) numbersNormalized++

                if (previousWasNumber) output.append(',')
                output.append(normalized)
                previousWasNumber = true
            }
        }

        val syntaxNormalizedPath = output.toString()
        val h23SyntaxNormalizationCharacterDelta =
            pathData.length - syntaxNormalizedPath.length
        stageTrace?.add(PathFixedPointStageSnapshot("Syntax normalization", syntaxNormalizedPath))

        profiling?.let {
            it.tokenizationNormalizationNanos +=
                System.nanoTime() - tokenizationStartTime
        }

        val geometryCleanupStartTime = System.nanoTime()

        val redundantSegmentCleanupStartTime = System.nanoTime()
        val redundantCleanup = removeRedundantNonDrawingSegments(
            syntaxNormalizedPath
        )
        val h23RedundantGeometryCharacterDelta =
            syntaxNormalizedPath.length - redundantCleanup.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Redundant geometry cleanup", redundantCleanup.pathData))
        profiling?.let {
            it.redundantSegmentCleanupNanos +=
                System.nanoTime() - redundantSegmentCleanupStartTime
        }

        // G2.5: Arc-specific cleanup is a pure no-op when the tokenized path
        // contains no elliptical-arc command. Avoid reparsing the same path
        // through six arc-only passes in that common case. The eligibility
        // decision is made from the already validated token stream above, so
        // malformed input retains the existing conservative early-return path.
        val hasArcCommands = matches.any { match ->
            val token = match.value
            isCommand(token) && token[0].uppercaseChar() == 'A'
        }

        val arcRadiusCanonicalization: ArcRadiusCanonicalizationResult
        val arcCanonicalization: ArcRotationCanonicalizationResult
        val arcGlobalMinimization: ArcGlobalMinimizationResult
        val arcHalfTurnReduction: ArcHalfTurnReductionResult
        val arcAxisMinimization: ArcAxisMinimizationResult
        val degenerateArcCleanup: DegenerateArcCleanupResult

        if (hasArcCommands) {
            val arcCleanupStartTime = System.nanoTime()
            arcRadiusCanonicalization = canonicalizeArcRadii(
                redundantCleanup.pathData
            )
            arcCanonicalization = canonicalizeArcRotations(
                arcRadiusCanonicalization.pathData
            )
            arcGlobalMinimization = globallyMinimizeArcRepresentations(
                arcCanonicalization.pathData
            )
            arcHalfTurnReduction = reduceArcRotationsByHalfTurns(
                arcGlobalMinimization.pathData
            )
            arcAxisMinimization = minimizeArcAxisRepresentation(
                arcHalfTurnReduction.pathData
            )
            degenerateArcCleanup = simplifyDegenerateArcs(
                arcAxisMinimization.pathData
            )
            profiling?.let {
                it.arcCleanupNanos += System.nanoTime() - arcCleanupStartTime
            }
        } else {
            val unchangedPathData = redundantCleanup.pathData
            arcRadiusCanonicalization =
                ArcRadiusCanonicalizationResult(unchangedPathData, 0)
            arcCanonicalization =
                ArcRotationCanonicalizationResult(unchangedPathData, 0)
            arcGlobalMinimization =
                ArcGlobalMinimizationResult(unchangedPathData, 0)
            arcHalfTurnReduction =
                ArcHalfTurnReductionResult(unchangedPathData, 0)
            arcAxisMinimization =
                ArcAxisMinimizationResult(unchangedPathData, 0)
            degenerateArcCleanup =
                DegenerateArcCleanupResult(unchangedPathData, 0)
        }

        val h23ArcCleanupCharacterDelta =
            redundantCleanup.pathData.length - degenerateArcCleanup.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Arc cleanup", degenerateArcCleanup.pathData))

        val curveSimplificationStartTime = System.nanoTime()
        val cubicStartTime = System.nanoTime()
        val cubicToQuadraticCleanup = reduceExactCubicCurvesToQuadratic(
            degenerateArcCleanup.pathData, profiling
        )
        profiling?.let { it.curveCubicToQuadraticNanos += System.nanoTime() - cubicStartTime }
        val reusableCurveSegments = if (
            cubicToQuadraticCleanup.pathData == degenerateArcCleanup.pathData
        ) {
            cubicToQuadraticCleanup.reusableSegments
        } else {
            null
        }
        if (profiling != null && reusableCurveSegments != null) {
            profiling.curveDuplicateParseInputs += 1
            profiling.curveSecondPassReparsedUnchangedInput += 1
        }
        val straightStartTime = System.nanoTime()
        val straightBezierCleanup = simplifyStraightBezierCurves(
            cubicToQuadraticCleanup.pathData,
            profiling,
            preParsedSegments = reusableCurveSegments
        )
        profiling?.let {
            it.curveStraightBezierNanos += System.nanoTime() - straightStartTime
            it.curveSimplificationNanos +=
                System.nanoTime() - curveSimplificationStartTime
        }

        val h23CurveSimplificationCharacterDelta =
            degenerateArcCleanup.pathData.length - straightBezierCleanup.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Curve simplification", straightBezierCleanup.pathData))

        val collinearConsolidationStartTime = System.nanoTime()
        val collinearCleanup = consolidateConsecutiveCollinearLineRuns(
            straightBezierCleanup.pathData
        )
        profiling?.let {
            it.collinearConsolidationNanos +=
                System.nanoTime() - collinearConsolidationStartTime
            it.geometryCleanupNanos += System.nanoTime() - geometryCleanupStartTime
        }

        val h23CollinearConsolidationCharacterDelta =
            straightBezierCleanup.pathData.length - collinearCleanup.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Collinear consolidation", collinearCleanup.pathData))

        val commandMinimizationStartTime = System.nanoTime()
        val localShorteningStartTime = System.nanoTime()
        val commandOptimization = shortenPathCommands(
            collinearCleanup.pathData,
            profiling
        )
        profiling?.let {
            it.commandLocalShorteningNanos +=
                System.nanoTime() - localShorteningStartTime
        }
        val h23LocalCommandShorteningCharacterDelta =
            collinearCleanup.pathData.length - commandOptimization.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Local command shortening", commandOptimization.pathData))
        val globalCommandOptimization = globallyMinimizeCommandSequence(
            commandOptimization.pathData,
            profiling
        )
        val h23GlobalCommandMinimizationCharacterDelta =
            commandOptimization.pathData.length - globalCommandOptimization.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Global command minimization", globalCommandOptimization.pathData))
        profiling?.let {
            it.commandMinimizationNanos +=
                System.nanoTime() - commandMinimizationStartTime
        }

        val numericSerializationStartTime = System.nanoTime()
        val globalNumericOptimization = globallyOptimizeNumericSerialization(
            globalCommandOptimization.pathData
        )
        profiling?.let {
            it.numericSerializationNanos +=
                System.nanoTime() - numericSerializationStartTime
        }
        val h23GlobalNumericSerializationCharacterDelta =
            globalCommandOptimization.pathData.length - globalNumericOptimization.pathData.length
        stageTrace?.add(PathFixedPointStageSnapshot("Global numeric serialization", globalNumericOptimization.pathData))
        return PathResult(
            pathData = globalNumericOptimization.pathData,
            repeatedCommandsRemoved = repeatedCommandsRemoved,
            redundantNonDrawingSegmentsRemoved =
                redundantCleanup.removedCount,
            collinearLineSegmentsConsolidated =
                collinearCleanup.consolidatedCount,
            straightBezierCurvesSimplified =
                straightBezierCleanup.simplifiedCount,
            degenerateArcsSimplified =
                degenerateArcCleanup.simplifiedCount,
            smoothBezierShorthandsSelected =
                commandOptimization.smoothBezierShorthandsSelected,
            cubicCurvesReducedToQuadratic =
                cubicToQuadraticCleanup.reducedCount,
            arcRotationsCanonicalized =
                arcCanonicalization.canonicalizedCount,
            arcRadiiCanonicalized =
                arcRadiusCanonicalization.canonicalizedCount,
            arcHalfTurnRotationsReduced =
                arcHalfTurnReduction.reducedCount,
            arcAxesSwappedForSize =
                arcAxisMinimization.swappedCount,
            arcRepresentationsGloballyMinimized =
                arcGlobalMinimization.minimizedCount,
            commandSequencesGloballyMinimized =
                globalCommandOptimization.minimizedCount,
            implicitLineTosAfterMoveSelected =
                globalCommandOptimization.implicitLineToCount,
            repeatedShorthandCurveCommandsOmitted =
                repeatedShorthandCommandsOmittedInitially +
                    globalCommandOptimization.repeatedShorthandCount,
            repeatedFullCurveCommandsOmitted =
                repeatedFullCurveCommandsOmittedInitially +
                    globalCommandOptimization.repeatedFullCurveCount,
            repeatedArcCommandsOmitted =
                maxOf(
                    0,
                    countImplicitRepeatedCommands(
                        globalNumericOptimization.pathData,
                        setOf('A')
                    ) -
                        countImplicitRepeatedCommands(
                            pathData,
                            setOf('A')
                        )
                ),
            scientificNotationValuesSelected =
                maxOf(
                    0,
                    countExponentNumbers(globalNumericOptimization.pathData) -
                        countExponentNumbers(pathData)
                ),
            numbersNormalized = numbersNormalized,
            shorterCommandFormsSelected = commandOptimization.shorterFormsSelected,
            relativeCommandsSelected = commandOptimization.relativeCommandsSelected,
            axisCommandsSelected = commandOptimization.axisCommandsSelected,
            globallyOptimizedNumericPaths =
                if (globalNumericOptimization.optimized) 1 else 0,
            h23SyntaxNormalizationCharacterDelta = h23SyntaxNormalizationCharacterDelta,
            h23RedundantGeometryCharacterDelta = h23RedundantGeometryCharacterDelta,
            h23ArcCleanupCharacterDelta = h23ArcCleanupCharacterDelta,
            h23CurveSimplificationCharacterDelta = h23CurveSimplificationCharacterDelta,
            h23CollinearConsolidationCharacterDelta = h23CollinearConsolidationCharacterDelta,
            h23LocalCommandShorteningCharacterDelta = h23LocalCommandShorteningCharacterDelta,
            h23GlobalCommandMinimizationCharacterDelta = h23GlobalCommandMinimizationCharacterDelta,
            h23GlobalNumericSerializationCharacterDelta = h23GlobalNumericSerializationCharacterDelta
        )
    }


    /**
     * F1.1: Removes only path commands that cannot contribute visible geometry
     * and cannot affect the coordinates of a later relative command.
     *
     * Safe cases handled here:
     * - a move command immediately superseded by an absolute M;
     * - a final trailing move command;
     * - consecutive duplicate close commands.
     *
     * Zero-length drawing commands are deliberately preserved because stroke
     * caps and joins can make them visible.
     */
    private data class RedundantNonDrawingCleanupResult(
        val pathData: String,
        val removedCount: Int
    )

    private fun removeRedundantNonDrawingSegments(
        pathData: String
    ): RedundantNonDrawingCleanupResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return RedundantNonDrawingCleanupResult(pathData, 0)
        if (segments.isEmpty()) {
            return RedundantNonDrawingCleanupResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var removed = false

        for (index in segments.indices) {
            val segment = segments[index]
            val next = segments.getOrNull(index + 1)
            val upper = segment.command.uppercaseChar()

            val supersededByAbsoluteMove =
                upper == 'M' &&
                next?.command == 'M'

            val trailingMove =
                upper == 'M' &&
                index == segments.lastIndex

            val duplicateClose =
                upper == 'Z' &&
                kept.lastOrNull()?.command?.uppercaseChar() == 'Z'

            if (
                supersededByAbsoluteMove ||
                trailingMove ||
                duplicateClose
            ) {
                removed = true
                continue
            }

            kept += segment
        }

        if (!removed) {
            return RedundantNonDrawingCleanupResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length <= pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            RedundantNonDrawingCleanupResult(
                pathData = rebuilt,
                removedCount = segments.size - kept.size
            )
        } else {
            RedundantNonDrawingCleanupResult(pathData, 0)
        }
    }

    private fun encodeParsedSegments(
        segments: List<ParsedSegment>
    ): String {
        val output = StringBuilder()
        var previousCommand: Char? = null
        var previousNumber: String? = null

        for (segment in segments) {
            output.append(
                encodeSegment(
                    command = segment.command,
                    values = segment.values,
                    previousCommand = previousCommand,
                    previousNumber = previousNumber,
                    forceCommand = segment.command.uppercaseChar() == 'M'
                )
            )
            previousCommand = segment.command
            previousNumber = segment.values
                .lastOrNull()
                ?.let(::formatPathNumber)
        }

        return output.toString()
    }

    private data class ArcGlobalMinimizationResult(
        val pathData: String,
        val minimizedCount: Int
    )

    /**
     * F2.7: Globally chooses the shortest exact arc-axis representation.
     *
     * For every non-circular, non-degenerate arc, this pass compares the full
     * exact equivalence class generated by:
     *
     * - keeping or swapping rx/ry;
     * - adding equivalent 180-degree half turns;
     * - adding the 90-degree axis-swap rotation.
     *
     * Candidate rotations are normalized into [-90, 90]. The shortest encoded
     * segment is selected, with the original retained on ties. No flags,
     * endpoints, sweep direction, or command case are changed.
     *
     * This acts as a final global safety net before the existing focused arc
     * minimizers. Those later passes should normally become no-ops.
     */
    private fun globallyMinimizeArcRepresentations(
        pathData: String
    ): ArcGlobalMinimizationResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return ArcGlobalMinimizationResult(pathData, 0)
        if (segments.isEmpty()) {
            return ArcGlobalMinimizationResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var minimized = 0
        var previousCommand: Char? = null
        var previousNumber: String? = null

        val fullTurn = BigDecimal("360")
        val halfTurn = BigDecimal("180")
        val quarterTurn = BigDecimal("90")

        fun normalize(rotation: BigDecimal): BigDecimal {
            var value = rotation.remainder(fullTurn)
            while (value.compareTo(quarterTurn) > 0) {
                value = value.subtract(halfTurn)
            }
            while (value.compareTo(quarterTurn.negate()) < 0) {
                value = value.add(halfTurn)
            }
            return if (value.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                value.stripTrailingZeros()
            }
        }

        for (segment in segments) {
            var selected = segment

            if (segment.command.uppercaseChar() == 'A') {
                val values = segment.values
                if (values.size != 7) {
                    return ArcGlobalMinimizationResult(pathData, 0)
                }

                val rx = values[0]
                val ry = values[1]

                if (
                    rx.compareTo(BigDecimal.ZERO) != 0 &&
                    ry.compareTo(BigDecimal.ZERO) != 0 &&
                    rx.compareTo(ry) != 0
                ) {
                    val candidates = mutableListOf<ParsedSegment>()

                    fun addCandidate(
                        candidateRx: BigDecimal,
                        candidateRy: BigDecimal,
                        rotation: BigDecimal
                    ) {
                        val candidateValues = values.toMutableList()
                        candidateValues[0] = candidateRx
                        candidateValues[1] = candidateRy
                        candidateValues[2] = normalize(rotation)
                        candidates += ParsedSegment(
                            command = segment.command,
                            values = candidateValues
                        )
                    }

                    addCandidate(rx, ry, values[2])
                    addCandidate(rx, ry, values[2].add(halfTurn))
                    addCandidate(rx, ry, values[2].subtract(halfTurn))

                    addCandidate(ry, rx, values[2].add(quarterTurn))
                    addCandidate(
                        ry,
                        rx,
                        values[2].add(quarterTurn).add(halfTurn)
                    )
                    addCandidate(
                        ry,
                        rx,
                        values[2].add(quarterTurn).subtract(halfTurn)
                    )

                    val originalText = encodeSegment(
                        command = segment.command,
                        values = segment.values,
                        previousCommand = previousCommand,
                        previousNumber = previousNumber,
                        forceCommand = false
                    )

                    var best = segment
                    var bestLength = originalText.length

                    for (candidate in candidates.distinctBy {
                        it.command to it.values
                    }) {
                        val encoded = encodeSegment(
                            command = candidate.command,
                            values = candidate.values,
                            previousCommand = previousCommand,
                            previousNumber = previousNumber,
                            forceCommand = false
                        )
                        if (encoded.length < bestLength) {
                            best = candidate
                            bestLength = encoded.length
                        }
                    }

                    if (best != segment) {
                        selected = best
                        minimized++
                    }
                }
            }

            kept += selected
            previousCommand = selected.command
            previousNumber = selected.values
                .lastOrNull()
                ?.let(::formatBigDecimal)
        }

        if (minimized == 0) {
            return ArcGlobalMinimizationResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length < pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            ArcGlobalMinimizationResult(
                pathData = rebuilt,
                minimizedCount = minimized
            )
        } else {
            ArcGlobalMinimizationResult(pathData, 0)
        }
    }

    private data class ArcAxisMinimizationResult(
        val pathData: String,
        val swappedCount: Int
    )

    /**
     * F2.6: Chooses the shorter exact representation of an elliptical arc's
     * axes.
     *
     * Swapping rx and ry while rotating the ellipse axes by 90 degrees
     * describes the same ellipse:
     *
     *     (rx, ry, rotation)
     *       ==
     *     (ry, rx, rotation + 90 degrees)
     *
     * The alternate rotation is reduced into [-90, 90] using the same exact
     * half-turn equivalence as F2.5. The alternate form is selected only when
     * its encoded path segment is strictly shorter.
     *
     * Flags, endpoints, sweep direction, and command case are unchanged.
     * Circular and zero-radius arcs are left alone.
     */
    private fun minimizeArcAxisRepresentation(
        pathData: String
    ): ArcAxisMinimizationResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return ArcAxisMinimizationResult(pathData, 0)
        if (segments.isEmpty()) {
            return ArcAxisMinimizationResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var swapped = 0
        var previousCommand: Char? = null
        var previousNumber: String? = null

        val halfTurn = BigDecimal("180")
        val quarterTurn = BigDecimal("90")

        fun reduceHalfTurn(rotation: BigDecimal): BigDecimal {
            var value = rotation
            while (value.compareTo(quarterTurn) > 0) {
                value = value.subtract(halfTurn)
            }
            while (value.compareTo(quarterTurn.negate()) < 0) {
                value = value.add(halfTurn)
            }
            return if (value.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                value.stripTrailingZeros()
            }
        }

        for (segment in segments) {
            var selected = segment

            if (segment.command.uppercaseChar() == 'A') {
                val values = segment.values
                if (values.size != 7) {
                    return ArcAxisMinimizationResult(pathData, 0)
                }

                val rx = values[0]
                val ry = values[1]

                if (
                    rx.compareTo(BigDecimal.ZERO) != 0 &&
                    ry.compareTo(BigDecimal.ZERO) != 0 &&
                    rx.compareTo(ry) != 0
                ) {
                    val alternateValues = values.toMutableList()
                    alternateValues[0] = ry
                    alternateValues[1] = rx
                    alternateValues[2] = reduceHalfTurn(
                        values[2].add(quarterTurn)
                    )

                    val alternate = ParsedSegment(
                        command = segment.command,
                        values = alternateValues
                    )

                    val originalText = encodeSegment(
                        command = segment.command,
                        values = segment.values,
                        previousCommand = previousCommand,
                        previousNumber = previousNumber,
                        forceCommand = false
                    )
                    val alternateText = encodeSegment(
                        command = alternate.command,
                        values = alternate.values,
                        previousCommand = previousCommand,
                        previousNumber = previousNumber,
                        forceCommand = false
                    )

                    if (alternateText.length < originalText.length) {
                        selected = alternate
                        swapped++
                    }
                }
            }

            kept += selected
            previousCommand = selected.command
            previousNumber = selected.values
                .lastOrNull()
                ?.let(::formatBigDecimal)
        }

        if (swapped == 0) {
            return ArcAxisMinimizationResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length < pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            ArcAxisMinimizationResult(
                pathData = rebuilt,
                swappedCount = swapped
            )
        } else {
            ArcAxisMinimizationResult(pathData, 0)
        }
    }

    private data class ArcHalfTurnReductionResult(
        val pathData: String,
        val reducedCount: Int
    )

    /**
     * F2.5: Reduces elliptical-arc rotations by equivalent half turns.
     *
     * An ellipse rotated by theta and theta +/- 180 degrees has the same axes
     * and therefore describes the same SVG arc when all other arc parameters
     * are unchanged. After the existing modulo-360 canonicalization, this pass
     * chooses the equivalent angle in the compact interval [-90, 90].
     *
     * Examples:
     *  137 -> -43
     * -135 -> 45
     *  180 -> 0
     *
     * Radii, flags, endpoints, sweep direction, and command case are preserved.
     */
    private fun reduceArcRotationsByHalfTurns(
        pathData: String
    ): ArcHalfTurnReductionResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return ArcHalfTurnReductionResult(pathData, 0)
        if (segments.isEmpty()) {
            return ArcHalfTurnReductionResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var reduced = 0

        val halfTurn = BigDecimal("180")
        val quarterTurn = BigDecimal("90")

        fun reduce(rotation: BigDecimal): BigDecimal {
            var value = rotation
            while (value.compareTo(quarterTurn) > 0) {
                value = value.subtract(halfTurn)
            }
            while (value.compareTo(quarterTurn.negate()) < 0) {
                value = value.add(halfTurn)
            }
            return if (value.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                value.stripTrailingZeros()
            }
        }

        for (segment in segments) {
            if (segment.command.uppercaseChar() != 'A') {
                kept += segment
                continue
            }

            val values = segment.values.toMutableList()
            if (values.size != 7) {
                return ArcHalfTurnReductionResult(pathData, 0)
            }

            val replacement = reduce(values[2])
            if (replacement.compareTo(values[2]) != 0) {
                values[2] = replacement
                reduced++
            }

            kept += ParsedSegment(segment.command, values)
        }

        if (reduced == 0) {
            return ArcHalfTurnReductionResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length <= pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            ArcHalfTurnReductionResult(
                pathData = rebuilt,
                reducedCount = reduced
            )
        } else {
            ArcHalfTurnReductionResult(pathData, 0)
        }
    }

    private data class ArcRadiusCanonicalizationResult(
        val pathData: String,
        val canonicalizedCount: Int
    )

    /**
     * F2.4: Canonicalizes elliptical-arc radii to non-negative values.
     *
     * SVG arc geometry uses the magnitudes of rx and ry. This pass therefore:
     * - replaces negative rx/ry with their absolute values;
     * - normalizes negative zero to zero;
     * - leaves flags, rotation, endpoints, and command case unchanged.
     *
     * The pass runs before rotation canonicalization and degenerate-arc
     * simplification so later stages see a stable radius representation.
     */
    private fun canonicalizeArcRadii(
        pathData: String
    ): ArcRadiusCanonicalizationResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return ArcRadiusCanonicalizationResult(pathData, 0)
        if (segments.isEmpty()) {
            return ArcRadiusCanonicalizationResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var canonicalized = 0

        for (segment in segments) {
            if (segment.command.uppercaseChar() != 'A') {
                kept += segment
                continue
            }

            val values = segment.values.toMutableList()
            if (values.size != 7) {
                return ArcRadiusCanonicalizationResult(pathData, 0)
            }

            val canonicalRx =
                if (values[0].compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal.ZERO
                } else {
                    values[0].abs().stripTrailingZeros()
                }

            val canonicalRy =
                if (values[1].compareTo(BigDecimal.ZERO) == 0) {
                    BigDecimal.ZERO
                } else {
                    values[1].abs().stripTrailingZeros()
                }

            if (canonicalRx.compareTo(values[0]) != 0) {
                values[0] = canonicalRx
                canonicalized++
            }

            if (canonicalRy.compareTo(values[1]) != 0) {
                values[1] = canonicalRy
                canonicalized++
            }

            kept += ParsedSegment(segment.command, values)
        }

        if (canonicalized == 0) {
            return ArcRadiusCanonicalizationResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length <= pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            ArcRadiusCanonicalizationResult(
                pathData = rebuilt,
                canonicalizedCount = canonicalized
            )
        } else {
            ArcRadiusCanonicalizationResult(pathData, 0)
        }
    }

    private data class ArcRotationCanonicalizationResult(
        val pathData: String,
        val canonicalizedCount: Int
    )

    /**
     * F2.3: Canonicalizes elliptical-arc rotation values without changing
     * geometry.
     *
     * Rules:
     * - rotation is reduced modulo 360;
     * - an equivalent signed value in [-180, 180] is preferred;
     * - circular arcs (rx == ry) use rotation 0 because rotation is irrelevant.
     *
     * No radii, flags, endpoints, or arc direction are changed.
     */
    private fun canonicalizeArcRotations(
        pathData: String
    ): ArcRotationCanonicalizationResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return ArcRotationCanonicalizationResult(pathData, 0)
        if (segments.isEmpty()) {
            return ArcRotationCanonicalizationResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var canonicalized = 0

        val fullTurn = BigDecimal("360")
        val halfTurn = BigDecimal("180")

        fun canonicalRotation(
            rx: BigDecimal,
            ry: BigDecimal,
            rotation: BigDecimal
        ): BigDecimal {
            if (rx.abs().compareTo(ry.abs()) == 0) {
                return BigDecimal.ZERO
            }

            var normalized = rotation.remainder(fullTurn)
            if (normalized.compareTo(halfTurn) > 0) {
                normalized = normalized.subtract(fullTurn)
            } else if (normalized.compareTo(halfTurn.negate()) <= 0) {
                normalized = normalized.add(fullTurn)
            }

            return if (normalized.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                normalized.stripTrailingZeros()
            }
        }

        for (segment in segments) {
            if (segment.command.uppercaseChar() != 'A') {
                kept += segment
                continue
            }

            val values = segment.values.toMutableList()
            if (values.size != 7) {
                return ArcRotationCanonicalizationResult(pathData, 0)
            }

            val replacement = canonicalRotation(
                rx = values[0],
                ry = values[1],
                rotation = values[2]
            )

            if (replacement.compareTo(values[2]) != 0) {
                values[2] = replacement
                canonicalized++
            }

            kept += ParsedSegment(segment.command, values)
        }

        if (canonicalized == 0) {
            return ArcRotationCanonicalizationResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length <= pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            ArcRotationCanonicalizationResult(
                pathData = rebuilt,
                canonicalizedCount = canonicalized
            )
        } else {
            ArcRotationCanonicalizationResult(pathData, 0)
        }
    }

    private data class CubicToQuadraticCleanupResult(
        val pathData: String,
        val reducedCount: Int,
        val reusableSegments: List<ParsedSegment>? = null
    )

    /**
     * F2.2: Converts an explicit cubic Bézier C/c to an exactly equivalent
     * quadratic Bézier Q when both cubic controls imply the same quadratic
     * control point.
     *
     * For a cubic from P0 to P3 to equal a quadratic with control Q:
     *
     * Q = (3*C1 - P0) / 2
     * Q = (3*C2 - P3) / 2
     *
     * Both derived controls must match exactly. No tolerance or approximation
     * is used. S/s commands are deliberately preserved because their reflected
     * first control depends on previous command state.
     */
    private fun reduceExactCubicCurvesToQuadratic(
        pathData: String,
        profiling: PathSyntaxProfiling? = null
    ): CubicToQuadraticCleanupResult {
        profiling?.let { it.curveParseCalls += 1 }
        val parseStart = System.nanoTime()
        val segments = parseNormalizedSegments(pathData)
            ?: return CubicToQuadraticCleanupResult(pathData, 0)
        if (segments.isEmpty()) {
            profiling?.let { it.curveCubicParseSetupNanos += System.nanoTime() - parseStart }
            return CubicToQuadraticCleanupResult(pathData, 0, segments)
        }
        profiling?.let { it.curveCubicParseSetupNanos += System.nanoTime() - parseStart }
        val scanStart = System.nanoTime()

        val kept = mutableListOf<ParsedSegment>()
        var reduced = 0

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        val three = BigDecimal("3")
        val two = BigDecimal("2")

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)

            val endX: BigDecimal
            val endY: BigDecimal
            when (upper) {
                'M', 'L', 'T' -> {
                    endX = absolute[0]
                    endY = absolute[1]
                }
                'H' -> {
                    endX = absolute[0]
                    endY = currentY
                }
                'V' -> {
                    endX = currentX
                    endY = absolute[0]
                }
                'C' -> {
                    endX = absolute[4]
                    endY = absolute[5]
                }
                'S', 'Q' -> {
                    endX = absolute[2]
                    endY = absolute[3]
                }
                'A' -> {
                    endX = absolute[5]
                    endY = absolute[6]
                }
                'Z' -> {
                    endX = subpathX
                    endY = subpathY
                }
                else -> {
                    return CubicToQuadraticCleanupResult(pathData, 0)
                }
            }

            val replacement = if (upper == 'C') {
                val q1x =
                    absolute[0].multiply(three)
                        .subtract(currentX)
                        .divide(two)
                val q1y =
                    absolute[1].multiply(three)
                        .subtract(currentY)
                        .divide(two)

                val q2x =
                    absolute[2].multiply(three)
                        .subtract(endX)
                        .divide(two)
                val q2y =
                    absolute[3].multiply(three)
                        .subtract(endY)
                        .divide(two)

                if (
                    q1x.compareTo(q2x) == 0 &&
                    q1y.compareTo(q2y) == 0
                ) {
                    ParsedSegment(
                        command = 'Q',
                        values = listOf(q1x, q1y, endX, endY)
                    )
                } else {
                    null
                }
            } else {
                null
            }

            if (replacement != null) {
                kept += replacement
                reduced++
            } else {
                kept += segment
            }

            currentX = endX
            currentY = endY

            if (upper == 'M') {
                subpathX = endX
                subpathY = endY
            }
        }

        profiling?.let { it.curveCubicScanNanos += System.nanoTime() - scanStart }
        if (reduced == 0) {
            return CubicToQuadraticCleanupResult(pathData, 0, segments)
        }

        profiling?.let { it.curveRebuildAttempts += 1 }
        val rebuildStart = System.nanoTime()
        val rebuilt = encodeParsedSegments(kept)
        val rebuildElapsed = System.nanoTime() - rebuildStart
        profiling?.let {
            it.curveCubicRebuildNanos += rebuildElapsed
            if (rebuilt == pathData) it.curveRebuildNoOpResults += 1
        }

        val validationStart = System.nanoTime()
        val sizeEligible = rebuilt.length <= pathData.length
        val parsedValidation = if (sizeEligible) {
            profiling?.let { it.curveValidationCalls += 1 }
            parseNormalizedSegments(rebuilt)
        } else {
            profiling?.let { it.curveRebuildRejectedForSize += 1 }
            null
        }
        val accepted = sizeEligible && parsedValidation != null
        val validationElapsed = System.nanoTime() - validationStart
        profiling?.let {
            it.curveCubicValidationNanos += validationElapsed
            it.curveCubicRebuildValidationNanos += rebuildElapsed + validationElapsed
            if (sizeEligible) {
                if (accepted) it.curveValidationAccepted += 1
                else it.curveValidationRejected += 1
            }
            if (accepted && rebuilt != pathData) it.curveCubicChangedPaths += 1
        }
        return if (accepted) {
            CubicToQuadraticCleanupResult(
                pathData = rebuilt,
                reducedCount = reduced,
                reusableSegments = null
            )
        } else {
            CubicToQuadraticCleanupResult(pathData, 0, segments)
        }
    }

    private data class DegenerateArcCleanupResult(
        val pathData: String,
        val simplifiedCount: Int
    )

    /**
     * F1.5: Converts elliptical arc commands to straight lines when either
     * radius is exactly zero, as required by SVG path semantics.
     *
     * This pass deliberately preserves:
     * - arcs with two non-zero radii;
     * - arcs whose endpoint equals the current point;
     * - all arc flags and rotations unless the radius-zero rule applies.
     *
     * Preserving same-endpoint arcs avoids changing subtle zero-length stroke
     * behavior in renderers.
     */
    private fun simplifyDegenerateArcs(
        pathData: String
    ): DegenerateArcCleanupResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return DegenerateArcCleanupResult(pathData, 0)
        if (segments.isEmpty()) {
            return DegenerateArcCleanupResult(pathData, 0)
        }

        val kept = mutableListOf<ParsedSegment>()
        var simplified = 0

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)

            val endX: BigDecimal
            val endY: BigDecimal
            when (upper) {
                'M', 'L', 'T' -> {
                    endX = absolute[0]
                    endY = absolute[1]
                }
                'H' -> {
                    endX = absolute[0]
                    endY = currentY
                }
                'V' -> {
                    endX = currentX
                    endY = absolute[0]
                }
                'C' -> {
                    endX = absolute[4]
                    endY = absolute[5]
                }
                'S', 'Q' -> {
                    endX = absolute[2]
                    endY = absolute[3]
                }
                'A' -> {
                    endX = absolute[5]
                    endY = absolute[6]
                }
                'Z' -> {
                    endX = subpathX
                    endY = subpathY
                }
                else -> {
                    return DegenerateArcCleanupResult(pathData, 0)
                }
            }

            val replacement = if (upper == 'A') {
                val radiusX = absolute[0].abs()
                val radiusY = absolute[1].abs()
                val sameEndpoint =
                    endX.compareTo(currentX) == 0 &&
                    endY.compareTo(currentY) == 0

                if (
                    !sameEndpoint &&
                    (
                        radiusX.compareTo(BigDecimal.ZERO) == 0 ||
                        radiusY.compareTo(BigDecimal.ZERO) == 0
                    )
                ) {
                    ParsedSegment('L', listOf(endX, endY))
                } else {
                    null
                }
            } else {
                null
            }

            if (replacement != null) {
                kept += replacement
                simplified++
            } else {
                kept += segment
            }

            currentX = endX
            currentY = endY

            if (upper == 'M') {
                subpathX = endX
                subpathY = endY
            }
        }

        if (simplified == 0) {
            return DegenerateArcCleanupResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length <= pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            DegenerateArcCleanupResult(
                pathData = rebuilt,
                simplifiedCount = simplified
            )
        } else {
            DegenerateArcCleanupResult(pathData, 0)
        }
    }

    private data class StraightBezierCleanupResult(
        val pathData: String,
        val simplifiedCount: Int
    )

    /**
     * F1.4: Converts explicit quadratic and cubic Bézier curves to straight
     * line segments only when the curve is exactly collinear and monotonic.
     *
     * Quadratic:
     * - control point lies on the closed start/end segment.
     *
     * Cubic:
     * - both control points lie on the closed start/end segment;
     * - their projections are ordered from start to end.
     *
     * Degenerate zero-length curves, shorthand S/T curves, backtracking
     * controls, and any non-collinear curve are preserved.
     */
    private fun simplifyStraightBezierCurves(
        pathData: String,
        profiling: PathSyntaxProfiling? = null,
        preParsedSegments: List<ParsedSegment>? = null
    ): StraightBezierCleanupResult {
        val parseStart = System.nanoTime()
        val segments = if (preParsedSegments != null) {
            preParsedSegments
        } else {
            profiling?.let { it.curveParseCalls += 1 }
            parseNormalizedSegments(pathData)
                ?: return StraightBezierCleanupResult(pathData, 0)
        }
        if (segments.isEmpty()) {
            profiling?.let { it.curveStraightParseSetupNanos += System.nanoTime() - parseStart }
            return StraightBezierCleanupResult(pathData, 0)
        }
        profiling?.let { it.curveStraightParseSetupNanos += System.nanoTime() - parseStart }
        val scanStart = System.nanoTime()

        val kept = mutableListOf<ParsedSegment>()
        var simplified = 0

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        fun cross(
            ax: BigDecimal,
            ay: BigDecimal,
            bx: BigDecimal,
            by: BigDecimal
        ): BigDecimal =
            ax.multiply(by).subtract(ay.multiply(bx))

        fun dot(
            ax: BigDecimal,
            ay: BigDecimal,
            bx: BigDecimal,
            by: BigDecimal
        ): BigDecimal =
            ax.multiply(bx).add(ay.multiply(by))

        fun projectionWithinSegment(
            pointX: BigDecimal,
            pointY: BigDecimal,
            startX: BigDecimal,
            startY: BigDecimal,
            endX: BigDecimal,
            endY: BigDecimal
        ): BigDecimal? {
            val dx = endX.subtract(startX)
            val dy = endY.subtract(startY)
            val px = pointX.subtract(startX)
            val py = pointY.subtract(startY)

            if (
                dx.compareTo(BigDecimal.ZERO) == 0 &&
                dy.compareTo(BigDecimal.ZERO) == 0
            ) {
                return null
            }

            if (cross(dx, dy, px, py).compareTo(BigDecimal.ZERO) != 0) {
                return null
            }

            val projection = dot(px, py, dx, dy)
            val lengthSquared = dot(dx, dy, dx, dy)

            return if (
                projection.compareTo(BigDecimal.ZERO) >= 0 &&
                projection.compareTo(lengthSquared) <= 0
            ) {
                projection
            } else {
                null
            }
        }

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)

            val endX: BigDecimal
            val endY: BigDecimal

            when (upper) {
                'M', 'L', 'T' -> {
                    endX = absolute[0]
                    endY = absolute[1]
                }
                'H' -> {
                    endX = absolute[0]
                    endY = currentY
                }
                'V' -> {
                    endX = currentX
                    endY = absolute[0]
                }
                'C' -> {
                    endX = absolute[4]
                    endY = absolute[5]
                }
                'S', 'Q' -> {
                    endX = absolute[2]
                    endY = absolute[3]
                }
                'A' -> {
                    endX = absolute[5]
                    endY = absolute[6]
                }
                'Z' -> {
                    endX = subpathX
                    endY = subpathY
                }
                else -> {
                    return StraightBezierCleanupResult(pathData, 0)
                }
            }

            val replacement = when (upper) {
                'Q' -> {
                    val controlProjection = projectionWithinSegment(
                        pointX = absolute[0],
                        pointY = absolute[1],
                        startX = currentX,
                        startY = currentY,
                        endX = endX,
                        endY = endY
                    )

                    if (controlProjection != null) {
                        ParsedSegment('L', listOf(endX, endY))
                    } else {
                        null
                    }
                }

                'C' -> {
                    val firstProjection = projectionWithinSegment(
                        pointX = absolute[0],
                        pointY = absolute[1],
                        startX = currentX,
                        startY = currentY,
                        endX = endX,
                        endY = endY
                    )
                    val secondProjection = projectionWithinSegment(
                        pointX = absolute[2],
                        pointY = absolute[3],
                        startX = currentX,
                        startY = currentY,
                        endX = endX,
                        endY = endY
                    )

                    if (
                        firstProjection != null &&
                        secondProjection != null &&
                        firstProjection.compareTo(secondProjection) <= 0
                    ) {
                        ParsedSegment('L', listOf(endX, endY))
                    } else {
                        null
                    }
                }

                else -> null
            }

            if (replacement != null) {
                kept += replacement
                simplified++
            } else {
                kept += segment
            }

            currentX = endX
            currentY = endY

            if (upper == 'M') {
                subpathX = endX
                subpathY = endY
            }
        }

        profiling?.let { it.curveStraightScanNanos += System.nanoTime() - scanStart }
        if (simplified == 0) {
            return StraightBezierCleanupResult(pathData, 0)
        }

        profiling?.let { it.curveRebuildAttempts += 1 }
        val rebuildStart = System.nanoTime()
        val rebuilt = encodeParsedSegments(kept)
        val rebuildElapsed = System.nanoTime() - rebuildStart
        profiling?.let {
            it.curveStraightRebuildNanos += rebuildElapsed
            if (rebuilt == pathData) it.curveRebuildNoOpResults += 1
        }

        val validationStart = System.nanoTime()
        val sizeEligible = rebuilt.length <= pathData.length
        val parsedValidation = if (sizeEligible) {
            profiling?.let { it.curveValidationCalls += 1 }
            parseNormalizedSegments(rebuilt)
        } else {
            profiling?.let { it.curveRebuildRejectedForSize += 1 }
            null
        }
        val accepted = sizeEligible && parsedValidation != null
        val validationElapsed = System.nanoTime() - validationStart
        profiling?.let {
            it.curveStraightValidationNanos += validationElapsed
            it.curveStraightRebuildValidationNanos += rebuildElapsed + validationElapsed
            if (sizeEligible) {
                if (accepted) it.curveValidationAccepted += 1
                else it.curveValidationRejected += 1
            }
            if (accepted && rebuilt != pathData) it.curveStraightChangedPaths += 1
        }
        return if (accepted) {
            StraightBezierCleanupResult(
                pathData = rebuilt,
                simplifiedCount = simplified
            )
        } else {
            StraightBezierCleanupResult(pathData, 0)
        }
    }

    private data class CollinearLineCleanupResult(
        val pathData: String,
        val consolidatedCount: Int
    )

    /**
     * F1.3: Consolidates adjacent straight-line segments when they are
     * exactly collinear and continue strictly in the same direction.
     *
     * Examples:
     * H10 H20              -> H20
     * L10,10 L20,20        -> L20,20
     * l5,5 l10,10          -> one equivalent diagonal segment
     *
     * Backtracking, zero-length segments, slope changes, curves, arcs,
     * subpath boundaries, and non-line commands are deliberately preserved.
     *
     * Exact cross-product and positive dot-product checks are used, so this
     * stage introduces no geometric tolerance or approximation.
     */
    private fun consolidateConsecutiveCollinearLineRuns(
        pathData: String
    ): CollinearLineCleanupResult {
        val segments = parseNormalizedSegments(pathData)
            ?: return CollinearLineCleanupResult(pathData, 0)
        if (segments.size < 2) {
            return CollinearLineCleanupResult(pathData, 0)
        }

        data class LineRun(
            val dx: BigDecimal,
            val dy: BigDecimal,
            val segments: MutableList<ParsedSegment>,
            var endX: BigDecimal,
            var endY: BigDecimal
        )

        val kept = mutableListOf<ParsedSegment>()
        var run: LineRun? = null
        var consolidated = 0

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        fun sameStrictDirectionAndSlope(
            firstDx: BigDecimal,
            firstDy: BigDecimal,
            secondDx: BigDecimal,
            secondDy: BigDecimal
        ): Boolean {
            if (
                (firstDx.signum() == 0 && firstDy.signum() == 0) ||
                (secondDx.signum() == 0 && secondDy.signum() == 0)
            ) {
                return false
            }

            val cross =
                firstDx.multiply(secondDy)
                    .subtract(firstDy.multiply(secondDx))
            if (cross.compareTo(BigDecimal.ZERO) != 0) {
                return false
            }

            val dot =
                firstDx.multiply(secondDx)
                    .add(firstDy.multiply(secondDy))
            return dot.signum() > 0
        }

        fun flushRun() {
            val active = run ?: return
            if (active.segments.size == 1) {
                kept += active.segments.first()
            } else {
                val command = when {
                    active.dy.compareTo(BigDecimal.ZERO) == 0 -> 'H'
                    active.dx.compareTo(BigDecimal.ZERO) == 0 -> 'V'
                    else -> 'L'
                }
                val values = when (command) {
                    'H' -> listOf(active.endX)
                    'V' -> listOf(active.endY)
                    else -> listOf(active.endX, active.endY)
                }
                kept += ParsedSegment(command, values)
                consolidated += active.segments.size - 1
            }
            run = null
        }

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)

            val endX: BigDecimal
            val endY: BigDecimal
            when (upper) {
                'M', 'L', 'T' -> {
                    endX = absolute[0]
                    endY = absolute[1]
                }
                'H' -> {
                    endX = absolute[0]
                    endY = currentY
                }
                'V' -> {
                    endX = currentX
                    endY = absolute[0]
                }
                'C' -> {
                    endX = absolute[4]
                    endY = absolute[5]
                }
                'S', 'Q' -> {
                    endX = absolute[2]
                    endY = absolute[3]
                }
                'A' -> {
                    endX = absolute[5]
                    endY = absolute[6]
                }
                'Z' -> {
                    endX = subpathX
                    endY = subpathY
                }
                else -> {
                    return CollinearLineCleanupResult(pathData, 0)
                }
            }

            val isStraightLine =
                upper == 'L' || upper == 'H' || upper == 'V'

            if (isStraightLine) {
                val dx = endX.subtract(currentX)
                val dy = endY.subtract(currentY)

                // Zero-length line segments stay explicit because round/square
                // caps and joins can make them visible.
                if (
                    dx.compareTo(BigDecimal.ZERO) == 0 &&
                    dy.compareTo(BigDecimal.ZERO) == 0
                ) {
                    flushRun()
                    kept += segment
                } else {
                    val active = run
                    if (
                        active != null &&
                        sameStrictDirectionAndSlope(
                            active.dx,
                            active.dy,
                            dx,
                            dy
                        )
                    ) {
                        active.segments += ParsedSegment(
                            command = 'L',
                            values = listOf(endX, endY)
                        )
                        active.endX = endX
                        active.endY = endY
                    } else {
                        flushRun()
                        run = LineRun(
                            dx = dx,
                            dy = dy,
                            segments = mutableListOf(
                                ParsedSegment(
                                    command = 'L',
                                    values = listOf(endX, endY)
                                )
                            ),
                            endX = endX,
                            endY = endY
                        )
                    }
                }
            } else {
                flushRun()
                kept += segment
            }

            currentX = endX
            currentY = endY

            if (upper == 'M') {
                subpathX = endX
                subpathY = endY
            }
            if (upper == 'Z') {
                run = null
            }
        }

        flushRun()

        if (consolidated == 0) {
            return CollinearLineCleanupResult(pathData, 0)
        }

        val rebuilt = encodeParsedSegments(kept)
        return if (
            rebuilt.length <= pathData.length &&
            parseNormalizedSegments(rebuilt) != null
        ) {
            CollinearLineCleanupResult(
                pathData = rebuilt,
                consolidatedCount = consolidated
            )
        } else {
            CollinearLineCleanupResult(pathData, 0)
        }
    }

    private data class GlobalCommandSequenceResult(
        val pathData: String,
        val minimizedCount: Int,
        val implicitLineToCount: Int,
        val repeatedShorthandCount: Int,
        val repeatedFullCurveCount: Int,
        val repeatedArcCount: Int
    )

    private data class CommandSequenceState(
        val previousCommand: Char?,
        val previousNumber: String?,
        val previousAxisDirection: Int?
    )

    private data class CommandSequenceCandidate(
        val command: Char,
        val values: List<BigDecimal>,
        val axisDirection: Int?,
        // G2.15: exact carry-forward of the canonical previous-number key
        // spelling. This is the same formatBigDecimal() result that was
        // previously recomputed once for every reachable DP state.
        val previousNumberState: String?
    )

    private data class GlobalSegmentEncodingCacheKey(
        val command: Char,
        val values: List<BigDecimal>,
        val commandOmitted: Boolean,
        val previousNumberWhenOmitted: String?
    )

    private data class CommandSequencePath(
        val text: String,
        val state: CommandSequenceState,
        val implicitLineToCount: Int,
        val repeatedShorthandCount: Int,
        val repeatedFullCurveCount: Int,
        val repeatedArcCount: Int
    )

    /**
     * F3.1: Globally minimizes absolute/relative command choices.
     *
     * The earlier command optimizer makes a locally shortest choice for each
     * segment. A local tie can still be globally suboptimal because choosing a
     * command's case now may let the next command letter be omitted.
     *
     * This dynamic-programming pass considers the complete path sequence and
     * keeps the shortest encoding for each reachable output state. It changes
     * only command spelling:
     *
     * - absolute versus relative command case;
     * - L versus H/V for exactly axis-aligned lines.
     *
     * Geometry, control points, arc flags, subpath boundaries, and command
     * order are unchanged. The original path is retained unless the complete
     * encoded result is strictly shorter.
     */
    private fun globallyMinimizeCommandSequence(
        pathData: String,
        profiling: PathSyntaxProfiling? = null
    ): GlobalCommandSequenceResult {
        val parseSetupStartTime = System.nanoTime()
        val segments = parseNormalizedSegments(pathData)
            ?: return GlobalCommandSequenceResult(pathData, 0, 0, 0, 0, 0)
        if (segments.isEmpty()) {
            return GlobalCommandSequenceResult(pathData, 0, 0, 0, 0, 0)
        }
        profiling?.let {
            it.commandGlobalParseSetupNanos +=
                System.nanoTime() - parseSetupStartTime
        }

        // G2.11: exact, run-local reuse of complete segment encodings.
        // The key includes every input that can affect encodeSegment output.
        val segmentEncodingCache =
            mutableMapOf<GlobalSegmentEncodingCacheKey, String>()

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        var paths = mapOf(
            CommandSequenceState(null, null, null) to
                CommandSequencePath(
                    text = "",
                    state = CommandSequenceState(null, null, null),
                    implicitLineToCount = 0,
                    repeatedShorthandCount = 0,
                    repeatedFullCurveCount = 0,
                    repeatedArcCount = 0
                )
        )

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val startX = currentX
            val startY = currentY
            val absolute = absoluteValuesFor(segment, startX, startY)

            val candidateGenerationStartTime = System.nanoTime()
            val candidates = mutableListOf<CommandSequenceCandidate>()

            fun add(command: Char, values: List<BigDecimal>) {
                val direction = when (command.uppercaseChar()) {
                    'H' -> {
                        val end = if (command.isUpperCase()) {
                            values[0]
                        } else {
                            startX.add(values[0])
                        }
                        end.subtract(startX).signum()
                    }
                    'V' -> {
                        val end = if (command.isUpperCase()) {
                            values[0]
                        } else {
                            startY.add(values[0])
                        }
                        end.subtract(startY).signum()
                    }
                    else -> null
                }
                candidates += CommandSequenceCandidate(
                    command = command,
                    values = values,
                    axisDirection = direction,
                    previousNumberState = values.lastOrNull()?.let(::formatBigDecimal)
                )
            }

            add(upper, absolute)
            if (upper != 'Z') {
                add(
                    upper.lowercaseChar(),
                    relativeValuesFor(segment, startX, startY)
                )
            }

            if (upper == 'L') {
                val endX = absolute[0]
                val endY = absolute[1]

                if (endY.compareTo(startY) == 0) {
                    add('H', listOf(endX))
                    add('h', listOf(endX.subtract(startX)))
                }
                if (endX.compareTo(startX) == 0) {
                    add('V', listOf(endY))
                    add('v', listOf(endY.subtract(startY)))
                }
            }

            val uniqueCandidates = candidates.distinctBy {
                it.command to it.values
            }
            profiling?.let {
                it.commandGlobalCandidateGenerationNanos +=
                    System.nanoTime() - candidateGenerationStartTime
            }
            val dynamicProgrammingStartTime = System.nanoTime()
            val nextPaths = mutableMapOf<
                CommandSequenceState,
                CommandSequencePath
            >()

            for ((_, path) in paths) {
                for (candidate in uniqueCandidates) {
                    val transitionEvaluationStartTime = System.nanoTime()
                    val forceAxisBoundary =
                        candidate.command.uppercaseChar() in
                            charArrayOf('H', 'V') &&
                        path.state.previousCommand == candidate.command &&
                        path.state.previousAxisDirection != null &&
                        candidate.axisDirection !=
                            path.state.previousAxisDirection

                    val implicitLineAfterMove =
                        (
                            path.state.previousCommand == 'M' &&
                                candidate.command == 'L'
                        ) || (
                            path.state.previousCommand == 'm' &&
                                candidate.command == 'l'
                        )
                    val repeatedShorthandCommand =
                        path.state.previousCommand == candidate.command &&
                            candidate.command.uppercaseChar() in
                                charArrayOf('S', 'T')
                    val repeatedFullCurveCommand =
                        path.state.previousCommand == candidate.command &&
                            candidate.command.uppercaseChar() in
                                charArrayOf('C', 'Q')
                    val repeatedArcCommand =
                        path.state.previousCommand == candidate.command &&
                            candidate.command.uppercaseChar() == 'A'
                    profiling?.let {
                        it.commandGlobalTransitionEvaluationNanos +=
                            System.nanoTime() - transitionEvaluationStartTime
                    }

                    val encoded = encodeSegment(
                        command = candidate.command,
                        values = candidate.values,
                        previousCommand = path.state.previousCommand,
                        previousNumber = path.state.previousNumber,
                        forceCommand =
                            candidate.command.uppercaseChar() == 'M' ||
                                forceAxisBoundary,
                        allowImplicitLineAfterMove = true,
                        globalProfiling = profiling,
                        globalSegmentEncodingCache = segmentEncodingCache
                    )

                    val stateCreationStartTime = System.nanoTime()

                    val stateKeyStartTime = System.nanoTime()
                    val keyFieldPreparationStartTime = System.nanoTime()

                    val previousCommandStartTime = System.nanoTime()
                    val nextPreviousCommand = candidate.command
                    profiling?.let {
                        it.commandGlobalStateKeyPreviousCommandNanos +=
                            System.nanoTime() - previousCommandStartTime
                    }

                    val previousNumberStartTime = System.nanoTime()
                    // G2.15: reuse the exact canonical spelling prepared once
                    // for this candidate instead of re-running
                    // formatBigDecimal() for every reachable source state.
                    val nextPreviousNumber = candidate.previousNumberState
                    profiling?.let {
                        it.commandGlobalStateKeyPreviousNumberNanos +=
                            System.nanoTime() - previousNumberStartTime
                    }

                    val axisDirectionStartTime = System.nanoTime()
                    val nextPreviousAxisDirection = candidate.axisDirection
                    profiling?.let {
                        it.commandGlobalStateKeyAxisDirectionNanos +=
                            System.nanoTime() - axisDirectionStartTime
                        it.commandGlobalStateKeyFieldPreparationNanos +=
                            System.nanoTime() - keyFieldPreparationStartTime
                    }

                    val keyAllocationStartTime = System.nanoTime()
                    val nextState = CommandSequenceState(
                        previousCommand = nextPreviousCommand,
                        previousNumber = nextPreviousNumber,
                        previousAxisDirection = nextPreviousAxisDirection
                    )
                    profiling?.let {
                        it.commandGlobalStateKeyAllocationNanos +=
                            System.nanoTime() - keyAllocationStartTime
                        it.commandGlobalStateKeyCreationNanos +=
                            System.nanoTime() - stateKeyStartTime
                    }

                    val stringConcatenationStartTime = System.nanoTime()
                    val nextText = path.text + encoded
                    profiling?.let {
                        it.commandGlobalStateStringConcatenationNanos +=
                            System.nanoTime() - stringConcatenationStartTime
                    }

                    val metadataStartTime = System.nanoTime()
                    val nextImplicitLineToCount =
                        path.implicitLineToCount +
                            if (implicitLineAfterMove) 1 else 0
                    val nextRepeatedShorthandCount =
                        path.repeatedShorthandCount +
                            if (repeatedShorthandCommand) 1 else 0
                    val nextRepeatedFullCurveCount =
                        path.repeatedFullCurveCount +
                            if (repeatedFullCurveCommand) 1 else 0
                    val nextRepeatedArcCount =
                        path.repeatedArcCount +
                            if (repeatedArcCommand) 1 else 0
                    profiling?.let {
                        it.commandGlobalStateMetadataPropagationNanos +=
                            System.nanoTime() - metadataStartTime
                    }

                    val pathAllocationStartTime = System.nanoTime()
                    val next = CommandSequencePath(
                        text = nextText,
                        state = nextState,
                        implicitLineToCount = nextImplicitLineToCount,
                        repeatedShorthandCount = nextRepeatedShorthandCount,
                        repeatedFullCurveCount = nextRepeatedFullCurveCount,
                        repeatedArcCount = nextRepeatedArcCount
                    )
                    profiling?.let {
                        it.commandGlobalStatePathAllocationNanos +=
                            System.nanoTime() - pathAllocationStartTime
                        it.commandGlobalStateCreationNanos +=
                            System.nanoTime() - stateCreationStartTime
                    }

                    val comparisonStartTime = System.nanoTime()
                    val lookupStartTime = System.nanoTime()
                    val existing = nextPaths[nextState]
                    profiling?.let {
                        it.commandGlobalBestStateMapLookupNanos +=
                            System.nanoTime() - lookupStartTime
                        it.commandGlobalStateMapLookupCalls += 1
                        if (existing == null) {
                            it.commandGlobalStateMapLookupMisses += 1
                        } else {
                            it.commandGlobalStateMapLookupHits += 1
                        }
                    }

                    val decisionStartTime = System.nanoTime()
                    val shouldReplace =
                        existing == null ||
                            next.text.length < existing.text.length ||
                            (
                                next.text.length == existing.text.length &&
                                next.text < existing.text
                            )
                    profiling?.let {
                        it.commandGlobalBestStateDecisionNanos +=
                            System.nanoTime() - decisionStartTime
                    }

                    if (shouldReplace) {
                        val replacementStartTime = System.nanoTime()
                        nextPaths[nextState] = next
                        profiling?.let {
                            it.commandGlobalBestStateReplacementNanos +=
                                System.nanoTime() - replacementStartTime
                            if (existing == null) {
                                it.commandGlobalStateMapInsertions += 1
                            } else {
                                it.commandGlobalStateMapReplacements += 1
                            }
                        }
                    }
                    profiling?.let {
                        it.commandGlobalBestStateComparisonNanos +=
                            System.nanoTime() - comparisonStartTime
                    }
                }
            }

            if (nextPaths.isEmpty()) {
                return GlobalCommandSequenceResult(pathData, 0, 0, 0, 0, 0)
            }
            paths = nextPaths
            profiling?.let {
                it.commandGlobalDynamicProgrammingNanos +=
                    System.nanoTime() - dynamicProgrammingStartTime
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
        }

        val reconstructionStartTime = System.nanoTime()
        val best = paths.values.minWithOrNull(
            compareBy<CommandSequencePath> { it.text.length }
                .thenBy { it.text }
        ) ?: return GlobalCommandSequenceResult(pathData, 0, 0, 0, 0, 0)

        // F3.2b: Keep an explicit, parser-validated fallback for the one case
        // where omitting L/l is guaranteed to save a character: the first
        // coordinate begins with a sign. This also protects the optimization
        // from being lost if a preceding command-choice state is collapsed by
        // the global DP before the moveto/lineto pair is considered.
        val implicitFallback = omitSignedLineCommandAfterMove(best.text)
        val selectedText = if (
            implicitFallback.pathData.length < best.text.length &&
            parseNormalizedSegments(implicitFallback.pathData) != null
        ) {
            implicitFallback.pathData
        } else {
            best.text
        }
        val selectedImplicitCount =
            best.implicitLineToCount +
                if (selectedText == implicitFallback.pathData) {
                    implicitFallback.omittedCount
                } else {
                    0
                }

        val result = if (
            selectedText.length < pathData.length &&
            parseNormalizedSegments(selectedText) != null
        ) {
            GlobalCommandSequenceResult(
                pathData = selectedText,
                minimizedCount = 1,
                implicitLineToCount = selectedImplicitCount,
                repeatedShorthandCount = best.repeatedShorthandCount,
                repeatedFullCurveCount = best.repeatedFullCurveCount,
                repeatedArcCount = best.repeatedArcCount
            )
        } else {
            GlobalCommandSequenceResult(pathData, 0, 0, 0, 0, 0)
        }
        profiling?.let {
            val reconstructionElapsedNanos =
                System.nanoTime() - reconstructionStartTime
            it.commandGlobalReconstructionNanos += reconstructionElapsedNanos
            it.commandGlobalDynamicProgrammingNanos += reconstructionElapsedNanos
        }
        return result
    }

    /**
     * Counts command letters omitted by SVG's implicit repetition syntax for
     * the requested command families.
     *
     * Parsing provides the number of logical segments, while scanning the raw
     * path string provides the number of explicitly written command letters.
     * Their difference is the number of implicit repetitions. Comparing this
     * value before and after optimization makes reporting independent of which
     * pipeline stage selected the final absolute/relative command case.
     */
    private fun countImplicitRepeatedCommands(
        pathData: String,
        commandFamilies: Set<Char>
    ): Int {
        val segments = parseNormalizedSegments(pathData) ?: return 0

        val logicalSegmentCount = segments.count {
            it.command.uppercaseChar() in commandFamilies
        }
        if (logicalSegmentCount == 0) return 0

        val explicitCommandCount = pathData.count { character ->
            character.isLetter() &&
                character.uppercaseChar() in commandFamilies
        }

        return maxOf(0, logicalSegmentCount - explicitCommandCount)
    }

    private data class ImplicitLineAfterMoveResult(
        val pathData: String,
        val omittedCount: Int
    )

    /**
     * F3.2b: Removes an explicit L/l immediately following a same-case M/m
     * when the first line coordinate starts with + or -. In that form the sign
     * is already a legal SVG number boundary, so removing the command is both
     * unambiguous and strictly shorter:
     *
     *     M10,10L-100-60 -> M10,10-100-60
     *
     * Unsigned coordinates are intentionally left to the normal encoder,
     * because they require a comma/space and therefore usually produce a tie.
     */
    private fun omitSignedLineCommandAfterMove(
        pathData: String
    ): ImplicitLineAfterMoveResult {
        val number =
            "[-+]?(?:(?:\\d+\\.\\d*)|(?:\\.\\d+)|(?:\\d+))(?:[eE][-+]?\\d+)?"
        val separator = "(?:,|\\s)*"
        val pattern = Regex(
            "([Mm]$number$separator$number)([Ll])(?=[+-])"
        )

        var omitted = 0
        val rebuilt = pattern.replace(pathData) { match ->
            val move = match.groupValues[1]
            val line = match.groupValues[2]
            val sameCase =
                (move[0] == 'M' && line == "L") ||
                    (move[0] == 'm' && line == "l")
            if (sameCase) {
                omitted++
                move
            } else {
                match.value
            }
        }

        return ImplicitLineAfterMoveResult(rebuilt, omitted)
    }

    private data class CommandOptimizationResult(
        val pathData: String,
        val shorterFormsSelected: Int,
        val relativeCommandsSelected: Int,
        val axisCommandsSelected: Int,
        val smoothBezierShorthandsSelected: Int
    )

    private data class ParsedSegment(
        val command: Char,
        val values: List<BigDecimal>
    )

    /**
     * Chooses a shorter, geometry-equivalent spelling for each path segment.
     *
     * This pass never rounds coordinates. Decimal arithmetic uses BigDecimal,
     * so switching between absolute and relative forms preserves the exact
     * values represented by the normalized input tokens.
     *
     * It may:
     * - switch between absolute and relative command forms;
     * - replace horizontal/vertical line segments with H/V or h/v;
     * - retain the original normalized form whenever no candidate is shorter.
     */
    private fun shortenPathCommands(
        pathData: String,
        profiling: PathSyntaxProfiling? = null
    ): CommandOptimizationResult {
        val parseSetupStartTime = System.nanoTime()
        val segments = parseNormalizedSegments(pathData)
            ?: return CommandOptimizationResult(pathData, 0, 0, 0, 0)
        if (segments.isEmpty()) return CommandOptimizationResult(pathData, 0, 0, 0, 0)

        val output = StringBuilder(pathData.length)
        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO
        var previousOutputCommand: Char? = null
        var previousOutputNumber: String? = null
        var previousAxisDirection: Int? = null

        var previousCubicControlX: BigDecimal? = null
        var previousCubicControlY: BigDecimal? = null
        var previousQuadraticControlX: BigDecimal? = null
        var previousQuadraticControlY: BigDecimal? = null

        var shorterForms = 0
        var relativeSelected = 0
        var axisSelected = 0
        var smoothShorthandsSelected = 0

        // G2.9: exact, scale-sensitive memoization scoped to this local-command pass.
        // formatPathNumber is pure for a given BigDecimal input, so a cache hit
        // reuses only the exact spelling that the existing serializer already produced.
        val localNumberSerializationCache = HashMap<BigDecimal, String>()

        profiling?.let {
            it.commandLocalParseSetupNanos +=
                System.nanoTime() - parseSetupStartTime
        }

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val startX = currentX
            val startY = currentY

            val candidates = mutableListOf<Pair<Char, List<BigDecimal>>>()
            val absoluteRelativeStartTime = System.nanoTime()
            candidates += upper to absoluteValuesFor(segment, startX, startY)
            if (upper != 'Z') {
                candidates += upper.lowercaseChar() to relativeValuesFor(segment, startX, startY)
            }
            profiling?.let {
                it.commandLocalAbsoluteRelativeCandidateNanos +=
                    System.nanoTime() - absoluteRelativeStartTime
            }

            val axisCandidateStartTime = System.nanoTime()
            if (upper == 'L') {
                val absolute = absoluteValuesFor(segment, startX, startY)
                val endX = absolute[0]
                val endY = absolute[1]
                if (endY.compareTo(startY) == 0) {
                    candidates += 'H' to listOf(endX)
                    candidates += 'h' to listOf(endX.subtract(startX))
                }
                if (endX.compareTo(startX) == 0) {
                    candidates += 'V' to listOf(endY)
                    candidates += 'v' to listOf(endY.subtract(startY))
                }
            }
            profiling?.let {
                it.commandLocalAxisCandidateNanos +=
                    System.nanoTime() - axisCandidateStartTime
            }

            val smoothCandidateStartTime = System.nanoTime()
            if (
                upper == 'C' &&
                previousCubicControlX != null &&
                previousCubicControlY != null
            ) {
                val absolute = absoluteValuesFor(segment, startX, startY)
                val reflectedX =
                    startX.multiply(BigDecimal("2"))
                        .subtract(previousCubicControlX)
                val reflectedY =
                    startY.multiply(BigDecimal("2"))
                        .subtract(previousCubicControlY)

                if (
                    absolute[0].compareTo(reflectedX) == 0 &&
                    absolute[1].compareTo(reflectedY) == 0
                ) {
                    candidates += 'S' to listOf(
                        absolute[2], absolute[3],
                        absolute[4], absolute[5]
                    )
                    candidates += 's' to listOf(
                        absolute[2].subtract(startX),
                        absolute[3].subtract(startY),
                        absolute[4].subtract(startX),
                        absolute[5].subtract(startY)
                    )
                }
            }

            if (
                upper == 'Q' &&
                previousQuadraticControlX != null &&
                previousQuadraticControlY != null
            ) {
                val absolute = absoluteValuesFor(segment, startX, startY)
                val reflectedX =
                    startX.multiply(BigDecimal("2"))
                        .subtract(previousQuadraticControlX)
                val reflectedY =
                    startY.multiply(BigDecimal("2"))
                        .subtract(previousQuadraticControlY)

                if (
                    absolute[0].compareTo(reflectedX) == 0 &&
                    absolute[1].compareTo(reflectedY) == 0
                ) {
                    candidates += 'T' to listOf(
                        absolute[2], absolute[3]
                    )
                    candidates += 't' to listOf(
                        absolute[2].subtract(startX),
                        absolute[3].subtract(startY)
                    )
                }
            }
            profiling?.let {
                it.commandLocalSmoothShorthandCandidateNanos +=
                    System.nanoTime() - smoothCandidateStartTime
            }

            val encodingSelectionStartTime = System.nanoTime()

            val originalCommand = segment.command
            val originalValues = segment.values
            val originalEncoded = encodeSegment(
                originalCommand,
                originalValues,
                previousOutputCommand,
                previousOutputNumber,
                forceCommand = originalCommand.uppercaseChar() == 'M',
                localProfiling = profiling,
                localNumberSerializationCache = localNumberSerializationCache
            )

            val encodedCandidates = candidates
                .distinctBy { it.first to it.second }
                .map { candidate ->
                    val candidateUpper = candidate.first.uppercaseChar()
                    val candidateDirection = when (candidateUpper) {
                        'H' -> {
                            val end = if (candidate.first.isUpperCase()) {
                                candidate.second[0]
                            } else {
                                startX.add(candidate.second[0])
                            }
                            end.subtract(startX).signum()
                        }
                        'V' -> {
                            val end = if (candidate.first.isUpperCase()) {
                                candidate.second[0]
                            } else {
                                startY.add(candidate.second[0])
                            }
                            end.subtract(startY).signum()
                        }
                        else -> null
                    }
                    val forceAxisBoundary =
                        candidateUpper in charArrayOf('H', 'V') &&
                        previousOutputCommand == candidate.first &&
                        previousAxisDirection != null &&
                        candidateDirection != previousAxisDirection

                    val encoded = encodeSegment(
                        candidate.first,
                        candidate.second,
                        previousOutputCommand,
                        previousOutputNumber,
                        forceCommand =
                            candidateUpper == 'M' ||
                                forceAxisBoundary,
                        localProfiling = profiling,
                        localNumberSerializationCache = localNumberSerializationCache
                    )
                    Triple(candidate, encoded, encoded.length)
                }

            val winnerSelectionStartTime = System.nanoTime()
            val chosen = encodedCandidates
                .minWithOrNull(compareBy<Triple<Pair<Char, List<BigDecimal>>, String, Int>> { it.third }
                    .thenBy { if (it.first.first == originalCommand) 0 else 1 })
                ?: return CommandOptimizationResult(pathData, 0, 0, 0, 0)
            profiling?.let {
                it.commandLocalWinnerSelectionNanos +=
                    System.nanoTime() - winnerSelectionStartTime
                it.commandLocalEncodingSelectionNanos +=
                    System.nanoTime() - encodingSelectionStartTime
            }

            val stateBookkeepingStartTime = System.nanoTime()
            val selectedCommand = chosen.first.first
            output.append(chosen.second)

            if (chosen.second.length < originalEncoded.length || selectedCommand != originalCommand) {
                if (chosen.second.length < originalEncoded.length) shorterForms++
                if (selectedCommand.isLowerCase() && selectedCommand.uppercaseChar() != 'Z') relativeSelected++
                if (selectedCommand.uppercaseChar() in charArrayOf('H', 'V')) axisSelected++
                if (
                    selectedCommand.uppercaseChar() in charArrayOf('S', 'T') &&
                    selectedCommand.uppercaseChar() != originalCommand.uppercaseChar()
                ) {
                    smoothShorthandsSelected++
                }
            }

            previousOutputCommand = selectedCommand
            previousOutputNumber =
                chosen.first.second.lastOrNull()?.let(::formatBigDecimal)
            previousAxisDirection = when (selectedCommand.uppercaseChar()) {
                'H' -> {
                    val end = if (selectedCommand.isUpperCase()) {
                        chosen.first.second[0]
                    } else {
                        startX.add(chosen.first.second[0])
                    }
                    end.subtract(startX).signum()
                }
                'V' -> {
                    val end = if (selectedCommand.isUpperCase()) {
                        chosen.first.second[0]
                    } else {
                        startY.add(chosen.first.second[0])
                    }
                    end.subtract(startY).signum()
                }
                else -> null
            }

            val absolute = absoluteValuesFor(segment, startX, startY)

            when (upper) {
                'C' -> {
                    previousCubicControlX = absolute[2]
                    previousCubicControlY = absolute[3]
                }
                'S' -> {
                    previousCubicControlX = absolute[0]
                    previousCubicControlY = absolute[1]
                }
                else -> {
                    previousCubicControlX = null
                    previousCubicControlY = null
                }
            }

            when (upper) {
                'Q' -> {
                    previousQuadraticControlX = absolute[0]
                    previousQuadraticControlY = absolute[1]
                }
                'T' -> {
                    val reflectedX =
                        previousQuadraticControlX?.let {
                            startX.multiply(BigDecimal("2")).subtract(it)
                        } ?: startX
                    val reflectedY =
                        previousQuadraticControlY?.let {
                            startY.multiply(BigDecimal("2")).subtract(it)
                        } ?: startY
                    previousQuadraticControlX = reflectedX
                    previousQuadraticControlY = reflectedY
                }
                else -> {
                    previousQuadraticControlX = null
                    previousQuadraticControlY = null
                }
            }

            when (upper) {
                'M', 'L', 'T' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                    if (upper == 'M') {
                        subpathX = currentX
                        subpathY = currentY
                    }
                }
                'H' -> currentX = absolute[0]
                'V' -> currentY = absolute[0]
                'C', 'S', 'Q' -> {
                    currentX = absolute[absolute.size - 2]
                    currentY = absolute[absolute.size - 1]
                }
                'A' -> {
                    currentX = absolute[5]
                    currentY = absolute[6]
                }
                'Z' -> {
                    currentX = subpathX
                    currentY = subpathY
                }
            }
            profiling?.let {
                it.commandLocalStateBookkeepingNanos +=
                    System.nanoTime() - stateBookkeepingStartTime
            }
        }

        val optimized = output.toString()

        // Never emit a shorter spelling unless it still parses as a complete SVG
        // path. This guards against incomplete implicit command parameter sets
        // such as `l0,14,-30,0,-14`, where the final line segment is missing its
        // Y coordinate. In that situation, retain the valid normalized input.
        val optimizedIsValid = parseNormalizedSegments(optimized) != null

        return if (optimizedIsValid && optimized.length <= pathData.length) {
            CommandOptimizationResult(
                optimized,
                shorterForms,
                relativeSelected,
                axisSelected,
                smoothShorthandsSelected
            )
        } else {
            CommandOptimizationResult(pathData, 0, 0, 0, 0)
        }
    }

    private fun parseNormalizedSegments(pathData: String): List<ParsedSegment>? {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return if (pathData.isBlank()) emptyList() else null
        }

        // tokenRegex.findAll() is intentionally useful in many diagnostic
        // callers, but a parser/validator must not silently skip malformed text
        // between otherwise valid tokens. Require complete lexical coverage here:
        // every gap before, between, and after recognized command/number tokens
        // must contain only legal SVG separators.
        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return null
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return null
        }

        val tokens = matches.map { it.value }
        return parseNormalizedSegmentsFromTokens(tokens)
    }

    private fun parseNormalizedSegmentsFromTokens(tokens: List<String>): List<ParsedSegment>? {
        if (tokens.isEmpty()) return emptyList()

        val counts = mapOf(
            'M' to 2, 'L' to 2, 'H' to 1, 'V' to 1,
            'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2,
            'A' to 7, 'Z' to 0
        )
        val result = mutableListOf<ParsedSegment>()
        var index = 0
        var active: Char? = null
        var firstMovePair = true

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                active = tokens[index][0]
                index++
                firstMovePair = true
                if (active.uppercaseChar() == 'Z') {
                    result += ParsedSegment(active, emptyList())
                    active = null
                    continue
                }
            }

            val command = active ?: return null
            val count = counts[command.uppercaseChar()] ?: return null
            if (index + count > tokens.size) return null
            if ((0 until count).any { isCommand(tokens[index + it]) }) return null

            val values = (0 until count).map {
                tokens[index + it].toBigDecimalOrNull() ?: return null
            }
            index += count

            val emittedCommand = if (command.uppercaseChar() == 'M' && !firstMovePair) {
                if (command.isLowerCase()) 'l' else 'L'
            } else command
            result += ParsedSegment(emittedCommand, values)
            firstMovePair = false

            if (index < tokens.size && isCommand(tokens[index])) continue
            if (count == 0) active = null
        }
        return result
    }

    private fun absoluteValuesFor(
        segment: ParsedSegment,
        startX: BigDecimal,
        startY: BigDecimal
    ): List<BigDecimal> {
        if (segment.command.isUpperCase() || segment.command.uppercaseChar() == 'Z') return segment.values
        val v = segment.values
        return when (segment.command.uppercaseChar()) {
            'M', 'L', 'T' -> listOf(startX.add(v[0]), startY.add(v[1]))
            'H' -> listOf(startX.add(v[0]))
            'V' -> listOf(startY.add(v[0]))
            'C' -> listOf(
                startX.add(v[0]), startY.add(v[1]),
                startX.add(v[2]), startY.add(v[3]),
                startX.add(v[4]), startY.add(v[5])
            )
            'S', 'Q' -> listOf(
                startX.add(v[0]), startY.add(v[1]),
                startX.add(v[2]), startY.add(v[3])
            )
            'A' -> listOf(v[0], v[1], v[2], v[3], v[4], startX.add(v[5]), startY.add(v[6]))
            else -> v
        }
    }

    private fun relativeValuesFor(
        segment: ParsedSegment,
        startX: BigDecimal,
        startY: BigDecimal
    ): List<BigDecimal> {
        if (segment.command.isLowerCase()) return segment.values
        val v = segment.values
        return when (segment.command.uppercaseChar()) {
            'M', 'L', 'T' -> listOf(v[0].subtract(startX), v[1].subtract(startY))
            'H' -> listOf(v[0].subtract(startX))
            'V' -> listOf(v[0].subtract(startY))
            'C' -> listOf(
                v[0].subtract(startX), v[1].subtract(startY),
                v[2].subtract(startX), v[3].subtract(startY),
                v[4].subtract(startX), v[5].subtract(startY)
            )
            'S', 'Q' -> listOf(
                v[0].subtract(startX), v[1].subtract(startY),
                v[2].subtract(startX), v[3].subtract(startY)
            )
            'A' -> listOf(v[0], v[1], v[2], v[3], v[4], v[5].subtract(startX), v[6].subtract(startY))
            else -> v
        }
    }

    /**
     * Geometry-level canonical representation used by F4.3 validation and its
     * differential stress search.
     *
     * Raw ParsedSegment equality is not semantic equality because F3.1 may
     * legitimately change:
     * - absolute commands to relative commands;
     * - L to H or V;
     * - C to S;
     * - Q to T;
     * - numeric scale, such as 100000 to 1e5.
     *
     * This method expands every segment to an absolute full-form command and
     * normalizes BigDecimal scale. Equivalent path spellings therefore compare
     * equal while genuine geometry changes remain detectable.
     */
    private data class CanonicalSemanticSegment(
        val command: Char,
        val values: List<BigDecimal>
    )

    private fun canonicalPathSemantics(
        pathData: String
    ): List<CanonicalSemanticSegment>? {
        val segments = parseNormalizedSegments(pathData) ?: return null
        val result = mutableListOf<CanonicalSemanticSegment>()

        var currentX = BigDecimal.ZERO
        var currentY = BigDecimal.ZERO
        var subpathX = BigDecimal.ZERO
        var subpathY = BigDecimal.ZERO

        var previousCubicControlX: BigDecimal? = null
        var previousCubicControlY: BigDecimal? = null
        var previousQuadraticControlX: BigDecimal? = null
        var previousQuadraticControlY: BigDecimal? = null

        fun normalized(value: BigDecimal): BigDecimal {
            val stripped = value.stripTrailingZeros()
            return if (stripped.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                stripped
            }
        }

        fun canonicalValues(values: List<BigDecimal>): List<BigDecimal> =
            values.map(::normalized)

        for (segment in segments) {
            val upper = segment.command.uppercaseChar()
            val absolute = absoluteValuesFor(segment, currentX, currentY)

            when (upper) {
                'M' -> {
                    result += CanonicalSemanticSegment(
                        'M',
                        canonicalValues(absolute)
                    )
                    currentX = absolute[0]
                    currentY = absolute[1]
                    subpathX = currentX
                    subpathY = currentY
                }

                'L' -> {
                    result += CanonicalSemanticSegment(
                        'L',
                        canonicalValues(absolute)
                    )
                    currentX = absolute[0]
                    currentY = absolute[1]
                }

                'H' -> {
                    val endX = absolute[0]
                    result += CanonicalSemanticSegment(
                        'L',
                        canonicalValues(listOf(endX, currentY))
                    )
                    currentX = endX
                }

                'V' -> {
                    val endY = absolute[0]
                    result += CanonicalSemanticSegment(
                        'L',
                        canonicalValues(listOf(currentX, endY))
                    )
                    currentY = endY
                }

                'C' -> {
                    result += CanonicalSemanticSegment(
                        'C',
                        canonicalValues(absolute)
                    )
                    previousCubicControlX = absolute[2]
                    previousCubicControlY = absolute[3]
                    currentX = absolute[4]
                    currentY = absolute[5]
                }

                'S' -> {
                    val reflectedX =
                        previousCubicControlX?.let {
                            currentX.multiply(BigDecimal("2")).subtract(it)
                        } ?: currentX
                    val reflectedY =
                        previousCubicControlY?.let {
                            currentY.multiply(BigDecimal("2")).subtract(it)
                        } ?: currentY

                    val full = listOf(
                        reflectedX,
                        reflectedY,
                        absolute[0],
                        absolute[1],
                        absolute[2],
                        absolute[3]
                    )
                    result += CanonicalSemanticSegment(
                        'C',
                        canonicalValues(full)
                    )
                    previousCubicControlX = absolute[0]
                    previousCubicControlY = absolute[1]
                    currentX = absolute[2]
                    currentY = absolute[3]
                }

                'Q' -> {
                    result += CanonicalSemanticSegment(
                        'Q',
                        canonicalValues(absolute)
                    )
                    previousQuadraticControlX = absolute[0]
                    previousQuadraticControlY = absolute[1]
                    currentX = absolute[2]
                    currentY = absolute[3]
                }

                'T' -> {
                    val reflectedX =
                        previousQuadraticControlX?.let {
                            currentX.multiply(BigDecimal("2")).subtract(it)
                        } ?: currentX
                    val reflectedY =
                        previousQuadraticControlY?.let {
                            currentY.multiply(BigDecimal("2")).subtract(it)
                        } ?: currentY

                    val full = listOf(
                        reflectedX,
                        reflectedY,
                        absolute[0],
                        absolute[1]
                    )
                    result += CanonicalSemanticSegment(
                        'Q',
                        canonicalValues(full)
                    )
                    previousQuadraticControlX = reflectedX
                    previousQuadraticControlY = reflectedY
                    currentX = absolute[0]
                    currentY = absolute[1]
                }

                'A' -> {
                    result += CanonicalSemanticSegment(
                        'A',
                        canonicalValues(absolute)
                    )
                    currentX = absolute[5]
                    currentY = absolute[6]
                }

                'Z' -> {
                    result += CanonicalSemanticSegment('Z', emptyList())
                    currentX = subpathX
                    currentY = subpathY
                }

                else -> return null
            }

            if (upper !in charArrayOf('C', 'S')) {
                previousCubicControlX = null
                previousCubicControlY = null
            }
            if (upper !in charArrayOf('Q', 'T')) {
                previousQuadraticControlX = null
                previousQuadraticControlY = null
            }
        }

        return result
    }

    private data class GlobalNumericSerializationResult(
        val pathData: String,
        val optimized: Boolean
    )

    private data class NumericSerializationState(
        val previousNumber: String?
    )

    /**
     * F4.2: Globally optimizes numeric spelling and separators across the
     * complete already-selected command sequence.
     *
     * F4.1 selects the shortest standalone representation of each value.
     * The shortest complete path can differ because a representation affects
     * whether the following number needs a comma. This pass uses dynamic
     * programming over the raw command/number token stream and compares:
     *
     * - canonical plain decimal;
     * - exact scientific notation;
     * - comma versus grammar-provided number boundaries.
     *
     * Commands and numeric values are unchanged. The candidate is retained
     * only when it is strictly shorter and parses back into the same normalized
     * segment sequence.
     */
    private fun globallyOptimizeNumericSerialization(
        pathData: String
    ): GlobalNumericSerializationResult {
        val matches = tokenRegex.findAll(pathData).toList()
        if (matches.isEmpty()) {
            return GlobalNumericSerializationResult(pathData, false)
        }

        var cursor = 0
        for (match in matches) {
            if (!containsOnlySeparators(pathData.substring(cursor, match.range.first))) {
                return GlobalNumericSerializationResult(pathData, false)
            }
            cursor = match.range.last + 1
        }
        if (!containsOnlySeparators(pathData.substring(cursor))) {
            return GlobalNumericSerializationResult(pathData, false)
        }

        var paths = mapOf(
            NumericSerializationState(null) to ""
        )

        for (match in matches) {
            val token = match.value
            val next = mutableMapOf<NumericSerializationState, String>()

            if (isCommand(token)) {
                for ((_, text) in paths) {
                    val candidate = text + token.lowercaseOrOriginalCommand()
                    val state = NumericSerializationState(null)
                    val existing = next[state]
                    if (
                        existing == null ||
                        candidate.length < existing.length ||
                        (
                            candidate.length == existing.length &&
                            candidate < existing
                        )
                    ) {
                        next[state] = candidate
                    }
                }
            } else {
                val value = token.toBigDecimalOrNull()
                    ?: return GlobalNumericSerializationResult(pathData, false)
                val representations = exactNumberRepresentations(value)

                for ((state, text) in paths) {
                    for (representation in representations) {
                        val separator = when {
                            state.previousNumber == null -> ""
                            canConcatenateNumbers(
                                state.previousNumber,
                                representation
                            ) -> ""
                            else -> ","
                        }
                        val candidate = text + separator + representation
                        val nextState =
                            NumericSerializationState(representation)
                        val existing = next[nextState]
                        if (
                            existing == null ||
                            candidate.length < existing.length ||
                            (
                                candidate.length == existing.length &&
                                candidate < existing
                            )
                        ) {
                            next[nextState] = candidate
                        }
                    }
                }
            }

            if (next.isEmpty()) {
                return GlobalNumericSerializationResult(pathData, false)
            }
            paths = next
        }

        val best = paths.values.minWithOrNull(
            compareBy<String> { it.length }.thenBy { it }
        ) ?: return GlobalNumericSerializationResult(pathData, false)

        if (best.length >= pathData.length) {
            return GlobalNumericSerializationResult(pathData, false)
        }

        val originalSegments = parseNormalizedSegments(pathData)
            ?: return GlobalNumericSerializationResult(pathData, false)
        val optimizedSegments = parseNormalizedSegments(best)
            ?: return GlobalNumericSerializationResult(pathData, false)

        return if (originalSegments == optimizedSegments) {
            GlobalNumericSerializationResult(best, true)
        } else {
            GlobalNumericSerializationResult(pathData, false)
        }
    }

    /**
     * Returns exact candidate spellings. The canonical plain form and the
     * integer-mantissa exponent form cover the useful shortest alternatives
     * without introducing approximation.
     */
    private fun exactNumberRepresentations(value: BigDecimal): List<String> {
        val normalized = value.stripTrailingZeros()
        if (normalized.compareTo(BigDecimal.ZERO) == 0) return listOf("0")

        val plain = normalizeNumber(normalized.toPlainString())
        val compactPlain = when {
            plain.startsWith("0.") -> plain.substring(1)
            plain.startsWith("-0.") -> "-" + plain.substring(2)
            plain.startsWith("+0.") -> "+" + plain.substring(2)
            else -> plain
        }
        val exponent =
            normalized.unscaledValue().toString() +
                "e" +
                (-normalized.scale()).toString()

        return listOf(
            plain,
            compactPlain,
            exponent
        )
            .distinct()
            .sortedWith(
                compareBy<String> { it.length }
                    .thenBy { it }
            )
    }

    /**
     * SVG permits a new number without a comma when:
     * - it begins with + or -; or
     * - it begins with a decimal point and the previous number already has a
     *   decimal point and does not use exponent notation.
     *
     * Exponent notation must be treated as a terminal numeric token before an
     * unsigned decimal-point form. Concatenating "1e5" and ".5" would produce
     * the invalid token "1e50.5", not two numbers.
     *
     * Legal examples:
     *   10-5
     *   .5.25
     *   1e5-.5
     *
     * Separator required:
     *   1e5,.5
     */
    private fun canConcatenateNumbers(
        previous: String,
        next: String
    ): Boolean {
        if (next.startsWith('-') || next.startsWith('+')) return true
        if (!next.startsWith('.')) return false

        val previousUsesExponent =
            previous.contains('e') || previous.contains('E')

        return previous.contains('.') && !previousUsesExponent
    }

    /**
     * Commands are already case-optimized by F3.1. This helper deliberately
     * returns the original spelling; its name makes the token-stream intent
     * explicit and avoids accidentally applying locale-sensitive transforms.
     */
    private fun String.lowercaseOrOriginalCommand(): String = this

    private fun encodeSegment(
        command: Char,
        values: List<BigDecimal>,
        previousCommand: Char?,
        previousNumber: String?,
        forceCommand: Boolean,
        allowImplicitLineAfterMove: Boolean = false,
        localProfiling: PathSyntaxProfiling? = null,
        localNumberSerializationCache: MutableMap<BigDecimal, String>? = null,
        globalProfiling: PathSyntaxProfiling? = null,
        globalSegmentEncodingCache: MutableMap<GlobalSegmentEncodingCacheKey, String>? = null
    ): String {
        val omissionStartTime = System.nanoTime()
        val repeatsSameCommand =
            previousCommand == command
        val isImplicitLineAfterMove =
            allowImplicitLineAfterMove && (
                (previousCommand == 'M' && command == 'L') ||
                    (previousCommand == 'm' && command == 'l')
            )
        val canOmit =
            !forceCommand &&
                command.uppercaseChar() != 'Z' &&
                (repeatsSameCommand || isImplicitLineAfterMove)
        val commandPrefix = if (canOmit) "" else command.toString()
        val omissionElapsedNanos = System.nanoTime() - omissionStartTime
        localProfiling?.let {
            it.commandLocalCommandOmissionNanos += omissionElapsedNanos
        }
        globalProfiling?.let {
            it.commandGlobalSeparatorOmissionCostNanos += omissionElapsedNanos
        }

        // G2.11: encodeSegment output depends only on the selected command,
        // exact operands, whether the command letter is omitted, and (only
        // when omitted) the previous numeric spelling used at the boundary.
        val globalCacheKey = if (globalSegmentEncodingCache != null) {
            globalProfiling?.commandGlobalSegmentEncodingRequests =
                (globalProfiling?.commandGlobalSegmentEncodingRequests ?: 0) + 1
            GlobalSegmentEncodingCacheKey(
                command = command,
                values = values.toList(),
                commandOmitted = canOmit,
                previousNumberWhenOmitted = if (canOmit) previousNumber else null
            )
        } else {
            null
        }

        if (globalCacheKey != null) {
            val cached = globalSegmentEncodingCache?.get(globalCacheKey)
            if (cached != null) {
                globalProfiling?.commandGlobalSegmentEncodingCacheHits =
                    (globalProfiling?.commandGlobalSegmentEncodingCacheHits ?: 0) + 1
                return cached
            }
        }

        if (values.isEmpty()) {
            if (globalCacheKey != null && globalSegmentEncodingCache != null) {
                globalSegmentEncodingCache[globalCacheKey] = commandPrefix
                globalProfiling?.commandGlobalSegmentEncodingUniqueKeys =
                    (globalProfiling?.commandGlobalSegmentEncodingUniqueKeys ?: 0) + 1
            }
            return commandPrefix
        }

        val numericStartTime = System.nanoTime()
        val numbers = values.map { value ->
            localProfiling?.commandLocalNumericSerializationCalls =
                (localProfiling?.commandLocalNumericSerializationCalls ?: 0) + 1

            if (localNumberSerializationCache == null) {
                formatPathNumber(value)
            } else {
                val cached = localNumberSerializationCache[value]
                if (cached != null) {
                    localProfiling?.commandLocalNumericSerializationCacheHits =
                        (localProfiling?.commandLocalNumericSerializationCacheHits ?: 0) + 1
                    cached
                } else {
                    formatPathNumber(value).also { encoded ->
                        localNumberSerializationCache[value] = encoded
                        localProfiling?.commandLocalNumericSerializationUniqueValues =
                            (localProfiling?.commandLocalNumericSerializationUniqueValues ?: 0) + 1
                    }
                }
            }
        }
        val numericElapsedNanos = System.nanoTime() - numericStartTime
        localProfiling?.let {
            it.commandLocalNumericSerializationNanos += numericElapsedNanos
        }
        globalProfiling?.let {
            it.commandGlobalSegmentEncodingNanos += numericElapsedNanos
        }

        val separatorStartTime = System.nanoTime()
        val boundarySeparator = if (
            canOmit &&
            previousNumber != null &&
            needsNumberSeparator(previousNumber, numbers.first())
        ) "," else ""
        val separatorElapsedNanos = System.nanoTime() - separatorStartTime
        localProfiling?.let {
            it.commandLocalSeparatorCalculationNanos += separatorElapsedNanos
        }
        globalProfiling?.let {
            it.commandGlobalSeparatorOmissionCostNanos += separatorElapsedNanos
        }

        val constructionStartTime = System.nanoTime()
        val body = buildString {
            numbers.forEachIndexed { index, number ->
                if (index > 0) {
                    val bodySeparatorStartTime = System.nanoTime()
                    val needsSeparator =
                        needsNumberSeparator(numbers[index - 1], number)
                    val bodySeparatorElapsedNanos =
                        System.nanoTime() - bodySeparatorStartTime
                    localProfiling?.let {
                        it.commandLocalSeparatorCalculationNanos +=
                            bodySeparatorElapsedNanos
                    }
                    globalProfiling?.let {
                        it.commandGlobalSeparatorOmissionCostNanos +=
                            bodySeparatorElapsedNanos
                    }
                    if (needsSeparator) append(',')
                }
                append(number)
            }
        }
        val result = commandPrefix + boundarySeparator + body
        val constructionElapsedNanos = System.nanoTime() - constructionStartTime
        localProfiling?.let {
            it.commandLocalStringConstructionNanos += constructionElapsedNanos
        }
        globalProfiling?.let {
            it.commandGlobalSegmentEncodingNanos += constructionElapsedNanos
        }
        if (globalCacheKey != null && globalSegmentEncodingCache != null) {
            globalSegmentEncodingCache[globalCacheKey] = result
            globalProfiling?.commandGlobalSegmentEncodingUniqueKeys =
                (globalProfiling?.commandGlobalSegmentEncodingUniqueKeys ?: 0) + 1
        }
        return result
    }

    private fun needsNumberSeparator(previous: String, next: String): Boolean {
        if (next.startsWith('-')) return false
        if (next.startsWith('+')) return false
        return true
    }

    /**
     * F4.1: Chooses the shortest exact SVG path-number spelling.
     *
     * BigDecimal's stripped unscaled value gives a compact exponent candidate
     * without changing numeric value. Scientific notation is selected only
     * when it is strictly shorter than the canonical plain-decimal spelling.
     * Ties retain plain notation for stability and readability.
     */
    private fun formatPathNumber(value: BigDecimal): String {
        val normalized = value.stripTrailingZeros()
        if (normalized.compareTo(BigDecimal.ZERO) == 0) return "0"

        val plain = normalizeNumber(normalized.toPlainString())
        val exponent = -normalized.scale()
        val mantissa = normalized.unscaledValue().toString()
        val scientific = mantissa + "e" + exponent.toString()

        return if (scientific.length < plain.length) {
            scientific
        } else {
            plain
        }
    }

    private fun countExponentNumbers(pathData: String): Int {
        return tokenRegex.findAll(pathData).count { match ->
            val token = match.value
            !isCommand(token) && (token.contains('e') || token.contains('E'))
        }
    }

    private fun formatBigDecimal(value: BigDecimal): String {
        val normalized = value.stripTrailingZeros()
        val plain = normalized.toPlainString()
        return normalizeNumber(plain)
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
