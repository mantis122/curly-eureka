package com.example.svgvectorconverter

import java.util.Locale

/**
 * H2.1 production corpus profiler.
 *
 * Diagnostic-only infrastructure. It converts real SVG inputs through the
 * normal production pipeline and aggregates the structured metrics already
 * produced by SvgConversionReporter. It does not alter optimizer behavior.
 */
object SvgProductionCorpusProfiler {

    data class CorpusInput(
        val fileName: String,
        val svg: String
    )

    data class FileResult(
        val fileName: String,
        val success: Boolean,
        val reportData: SvgConversionReportData?,
        val outputCharacters: Int,
        val elapsedNanos: Long,
        val sourcePathElementCount: Int = 0,
        val sourceHasGroup: Boolean = false,
        val sourceHasTransform: Boolean = false,
        val sourceHasStroke: Boolean = false,
        val sourceHasGradient: Boolean = false,
        val sourceHasClipPath: Boolean = false,
        val sourceHasMask: Boolean = false,
        val sourceHasUse: Boolean = false,
        val error: String? = null
    )

    data class CorpusResult(
        val files: List<FileResult>,
        val elapsedNanos: Long
    ) {
        val successCount: Int get() = files.count { it.success }
        val failureCount: Int get() = files.size - successCount

        fun toPlainTextReport(): String {
            val successful = files.mapNotNull { result ->
                result.reportData?.takeIf { result.success }?.let { result to it }
            }

            fun sumInt(block: (SvgConversionReportData) -> Int): Long =
                successful.sumOf { (_, data) -> block(data).toLong() }
            fun sumLong(block: (SvgConversionReportData) -> Long): Long =
                successful.sumOf { (_, data) -> block(data).coerceAtLeast(0L) }

            val sourceChars = sumInt { it.sourceSvgCharacters }
            val xmlBefore = sumInt { it.optimizedXmlCharactersBefore }
            val xmlAfter = sumInt { it.optimizedXmlCharactersAfter }
            val xmlDelta = xmlBefore - xmlAfter
            val pathBefore = sumInt { it.pathDataCharactersBefore }
            val pathAfter = sumInt { it.pathDataCharactersAfter }
            val pathDelta = pathBefore - pathAfter

            val successfulFiles = successful.map { it.first }
            val sourcePathElements = successfulFiles.sumOf { it.sourcePathElementCount.toLong() }
            val sourceMultiPathFiles = successfulFiles.count { it.sourcePathElementCount > 1 }
            val maxSourcePathElements = successfulFiles.maxOfOrNull { it.sourcePathElementCount } ?: 0
            val sourceGroupFiles = successfulFiles.count { it.sourceHasGroup }
            val sourceTransformFiles = successfulFiles.count { it.sourceHasTransform }
            val sourceStrokeFiles = successfulFiles.count { it.sourceHasStroke }
            val sourceGradientFiles = successfulFiles.count { it.sourceHasGradient }
            val sourceClipPathFiles = successfulFiles.count { it.sourceHasClipPath }
            val sourceMaskFiles = successfulFiles.count { it.sourceHasMask }
            val sourceUseFiles = successfulFiles.count { it.sourceHasUse }

            val stageSavings = listOf(
                "Path syntax and colors" to sumInt { it.optimizationPathSyntaxCharactersSaved },
                "Pruning and group cleanup" to sumInt { it.optimizationPruningCleanupCharactersSaved },
                "Transform optimization" to sumInt { it.optimizationTransformCharactersSaved },
                "Deduplication and merging" to sumInt { it.optimizationDeduplicationCharactersSaved },
                "Numeric cleanup" to sumInt { it.optimizationNumericCleanupCharactersSaved },
                "Final formatting" to sumInt { it.optimizationFormattingCharactersSaved }
            )

            val stageTimes = listOf(
                "Path syntax and colors" to sumLong { it.optimizationPathSyntaxNanos },
                "Pruning and group cleanup" to sumLong { it.optimizationPruningCleanupNanos },
                "Transform optimization" to sumLong { it.optimizationTransformsNanos },
                "Deduplication and merging" to sumLong { it.optimizationDeduplicationNanos },
                "Numeric cleanup" to sumLong { it.optimizationNumericCleanupNanos },
                "Final formatting" to sumLong { it.optimizationFormattingNanos }
            )

            val optimizationNanos = sumLong { it.outputOptimizationNanos }
            val productionPassNanos = sumLong { it.optimizerProductionPassNanos }
            val idempotencePassNanos = sumLong { it.optimizerIdempotencePassNanos }
            val fixedPointPassNanos = sumLong { it.optimizerFixedPointPassNanos }
            val convergenceGuardNanos = sumLong { it.g315GuardNanos }
            val finalValidationNanos = sumLong { it.finalOutputValidationNanos }
            val knownTopLevelNanos = productionPassNanos + idempotencePassNanos +
                fixedPointPassNanos + convergenceGuardNanos + finalValidationNanos
            val wrapperOtherNanos = (optimizationNanos - knownTopLevelNanos).coerceAtLeast(0L)
            val firstPassStageNanos = stageTimes.sumOf { it.second }
            val firstPassOtherNanos = (productionPassNanos - firstPassStageNanos).coerceAtLeast(0L)

            return buildString {
                appendLine("H1 production corpus profile")
                appendLine("Instrumentation level: H2.4")
                appendLine()
                appendLine("Mode: diagnostic only; normal production conversion pipeline")
                appendLine("Files selected: ${files.size}")
                appendLine("Converted successfully: $successCount")
                appendLine("Failed: $failureCount")
                appendLine("Elapsed: ${formatNanos(elapsedNanos)}")

                if (successful.isNotEmpty()) {
                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Corpus totals")
                    appendLine("────────────────────────────────")
                    appendLine("Source SVG characters: ${formatCount(sourceChars)}")
                    appendLine("Pre-optimizer VectorDrawable characters: ${formatCount(xmlBefore)}")
                    appendLine("Final VectorDrawable characters: ${formatCount(xmlAfter)}")
                    appendLine("Net optimizer character delta: ${formatSignedDelta(xmlDelta, xmlBefore)}")
                    appendLine("PathData characters before: ${formatCount(pathBefore)}")
                    appendLine("PathData characters after: ${formatCount(pathAfter)}")
                    appendLine("PathData character delta: ${formatSignedDelta(pathDelta, pathBefore)}")
                    appendLine("Final drawable paths: ${formatCount(sumInt { it.convertedPathCount })}")
                    appendLine("Final groups: ${formatCount(sumInt { it.generatedGroupCount })}")
                    appendLine("Paths optimized: ${formatCount(sumInt { it.pathDataOptimizedCount })}")
                    appendLine("Files with warnings: ${successful.count { (_, d) -> d.warningCount > 0 }}")
                    appendLine("Final validation failures: ${successful.count { (_, d) -> !d.finalOutputValidationPassed }}")
                    appendLine("Optimizer fixed-point failures: ${successful.count { (_, d) -> !d.optimizerReachedFixedPoint }}")

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Corpus diversity (source SVG)")
                    appendLine("────────────────────────────────")
                    appendLine("Source <path> elements: ${formatCount(sourcePathElements)}")
                    appendLine("Files with multiple source <path> elements: $sourceMultiPathFiles")
                    appendLine("Maximum source <path> elements in one file: $maxSourcePathElements")
                    appendLine("Files containing <g>: $sourceGroupFiles")
                    appendLine("Files containing transform attributes: $sourceTransformFiles")
                    appendLine("Files containing stroke styling: $sourceStrokeFiles")
                    appendLine("Files containing gradients: $sourceGradientFiles")
                    appendLine("Files containing <clipPath>: $sourceClipPathFiles")
                    appendLine("Files containing <mask>: $sourceMaskFiles")
                    appendLine("Files containing <use>: $sourceUseFiles")
                    appendLine("Note: diversity counters inspect source SVG markup and remain visible even when production conversion safely flattens or removes those constructs.")

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Production optimization activity")
                    appendLine("────────────────────────────────")
                    appendCounter("Redundant non-drawing segments removed", sumInt { it.redundantNonDrawingSegmentsRemoved })
                    appendCounter("Collinear segments consolidated", sumInt { it.collinearLineSegmentsConsolidated })
                    appendCounter("Straight Béziers simplified", sumInt { it.straightBezierCurvesSimplified })
                    appendCounter("Cubic curves reduced to quadratic", sumInt { it.cubicCurvesReducedToQuadratic })
                    appendCounter("Degenerate arcs simplified", sumInt { it.degenerateArcsSimplified })
                    appendCounter("Empty pathData removed", sumInt { it.emptyPathDataRemoved })
                    appendCounter("Move-only paths removed", sumInt { it.moveOnlyPathsRemoved })
                    appendCounter("Invisible paths removed", sumInt { it.invisiblePathsRemoved })
                    appendCounter("Empty groups removed", sumInt { it.emptyGroupsRemoved })
                    appendCounter("Redundant groups flattened", sumInt { it.redundantGroupsFlattened })
                    appendCounter("Adjacent groups coalesced", sumInt { it.adjacentGroupsCoalesced })
                    appendCounter("Compatible paths merged", sumInt { it.compatiblePathsMerged })
                    appendCounter("Exact duplicate paths removed", sumInt { it.exactDuplicatePathsRemoved })
                    appendCounter("Translation groups flattened", sumInt { it.translatedGroupsFlattened })
                    appendCounter("Uniform-scale groups flattened", sumInt { it.scaledGroupsFlattened })
                    appendCounter("Non-uniform-scale groups flattened", sumInt { it.nonUniformScaleGroupsFlattened })
                    appendCounter("Rotation groups flattened", sumInt { it.rotationGroupsFlattened })
                    appendCounter("Scale groups preserved for size", sumInt { it.scaleGroupsPreservedForSize })
                    appendCounter("Non-uniform scale groups preserved for size", sumInt { it.nonUniformScaleGroupsPreservedForSize })
                    appendCounter("Rotation groups preserved for size", sumInt { it.rotationGroupsPreservedForSize })
                    appendCounter("Nested transform groups composed", sumInt { it.nestedTransformGroupsComposed })
                    appendCounter("Common translation groups factored", sumInt { it.commonTranslationGroupsFactored })

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Opportunity / rejection diagnostics")
                    appendLine("────────────────────────────────")
                    appendCounter("Adjacent path pairs examined", sumInt { it.adjacentPathPairsExamined })
                    appendCounter("Adjacent path pairs with identical render attributes", sumInt { it.adjacentPathPairsSamePaint })
                    appendCounter("Rejected: nested aapt paint", sumInt { it.adjacentPathMergeRejectedNestedPaint })
                    appendCounter("Rejected: missing pathData", sumInt { it.adjacentPathMergeRejectedMissingPathData })
                    appendCounter("Rejected: render-attribute mismatch", sumInt { it.adjacentPathMergeRejectedPaintMismatch })
                    appendCounter("Rejected: unsupported geometry for exact bounds proof", sumInt { it.adjacentPathMergeRejectedUnsupportedGeometry })
                    appendCounter("Rejected: overlap / compositing safety", sumInt { it.adjacentPathMergeRejectedOverlapSafety })
                    appendCounter("Rejected: safe merge was not smaller", sumInt { it.adjacentPathMergeRejectedForSize })
                    appendCounter("Safe compatible merges accepted", sumInt { it.compatiblePathsMerged })
                    appendLine()
                    appendLine("Transform size decisions (already-safe candidates)")
                    appendCounter("Translation groups preserved because flattening was not smaller", sumInt { it.translationGroupsPreservedForSize })
                    appendCounter("Uniform-scale groups preserved because flattening was not smaller", sumInt { it.scaleGroupsPreservedForSize })
                    appendCounter("Non-uniform-scale groups preserved because flattening was not smaller", sumInt { it.nonUniformScaleGroupsPreservedForSize })
                    appendCounter("Rotation groups preserved because flattening was not smaller", sumInt { it.rotationGroupsPreservedForSize })
                    appendLine("Note: transform counters above describe candidates that already passed their existing safety eligibility rules; H2.4 does not relax those rules.")

                    val validationFailures = successful.filter { (_, d) -> !d.finalOutputValidationPassed }
                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("H1.6 validation failure classification (retained forensic diagnostics)")
                    appendLine("────────────────────────────────")
                    appendLine("Files with selected-output validation failure: ${validationFailures.size}")
                    if (validationFailures.isEmpty()) {
                        appendLine("No validation failures to classify.")
                    } else {
                        appendLine("Input invalid: ${validationFailures.count { (_, d) -> d.h16InputValidation.attempted && !d.h16InputValidation.passed }}")
                        appendLine("Pass 1 invalid: ${validationFailures.count { (_, d) -> d.h16Pass1Validation.attempted && !d.h16Pass1Validation.passed }}")
                        appendLine("Pass 2 invalid: ${validationFailures.count { (_, d) -> d.h16Pass2Validation.attempted && !d.h16Pass2Validation.passed }}")
                        appendLine("Pass 3 attempted: ${validationFailures.count { (_, d) -> d.h16Pass3Validation.attempted }}")
                        appendLine("Pass 3 invalid when attempted: ${validationFailures.count { (_, d) -> d.h16Pass3Validation.attempted && !d.h16Pass3Validation.passed }}")
                        appendLine("Selected output invalid: ${validationFailures.count { (_, d) -> d.h16SelectedValidation.attempted && !d.h16SelectedValidation.passed }}")
                        appendLine("Failures already present before optimizer: ${validationFailures.count { (_, d) -> d.h16InputValidation.attempted && !d.h16InputValidation.passed }}")
                        appendLine("Failures first appearing on pass 1: ${validationFailures.count { (_, d) -> d.h16InputValidation.passed && !d.h16Pass1Validation.passed }}")
                        appendLine("Failures first appearing on pass 2: ${validationFailures.count { (_, d) -> d.h16Pass1Validation.passed && !d.h16Pass2Validation.passed }}")
                        appendLine("Failures first appearing on selected output only: ${validationFailures.count { (_, d) -> d.h16Pass1Validation.passed && d.h16Pass2Validation.passed && (!d.h16Pass3Validation.attempted || d.h16Pass3Validation.passed) && !d.h16SelectedValidation.passed }}")
                        appendLine()
                        appendLine("Per-failure classification")
                        validationFailures.forEach { (file, data) ->
                            appendLine("⚠ ${file.fileName}")
                            appendLine("    input: ${formatValidationStage(data.h16InputValidation)}")
                            appendLine("    pass1: ${formatValidationStage(data.h16Pass1Validation)}")
                            appendLine("    pass2: ${formatValidationStage(data.h16Pass2Validation)}")
                            appendLine("    pass3: ${formatValidationStage(data.h16Pass3Validation)}")
                            appendLine("    selected: ${formatValidationStage(data.h16SelectedValidation)}")
                        }
                    }

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("H2.1 signed stage attribution (retained)")
                    appendLine("────────────────────────────────")
                    val signedStageDeltas = listOf(
                        "Path syntax and colors" to sumInt { it.h21PathSyntaxCharacterDelta },
                        "Pruning and group cleanup" to sumInt { it.h21PruningCharacterDelta },
                        "Transform optimization" to sumInt { it.h21TransformCharacterDelta },
                        "Near-integer snapping" to sumInt { it.h21NearIntegerCharacterDelta },
                        "Deduplication and merging" to sumInt { it.h21DedupMergeCharacterDelta },
                        "Decimal canonicalization" to sumInt { it.h21DecimalCanonicalizationCharacterDelta },
                        "Final formatting" to sumInt { it.h21FormattingCharacterDelta }
                    )
                    signedStageDeltas.forEach { (label, delta) ->
                        appendLine("$label: ${formatSignedStageDelta(delta)}")
                    }
                    appendLine("Note: positive means the stage removed characters; negative means it added characters.")

                    appendLine()
                    appendLine("H2.2 path-syntax/color substage attribution")
                    appendLine("────────────────────────────────")
                    appendLine(
                        "PathData syntax rewriting: " +
                            formatSignedStageDelta(sumInt { it.h22PathDataSyntaxCharacterDelta })
                    )
                    appendLine(
                        "Android color normalization: " +
                            formatSignedStageDelta(sumInt { it.h22ColorNormalizationCharacterDelta })
                    )
                    appendLine(
                        "Combined path syntax/colors: " +
                            formatSignedStageDelta(sumInt { it.h21PathSyntaxCharacterDelta })
                    )
                    appendLine("Note: these two H2.2 substages sum to the combined H2.1 path-syntax/color delta.")

                    appendLine()
                    appendLine("H2.3 PathData internal-stage attribution")
                    appendLine("────────────────────────────────")
                    val h23StageTotals = listOf(
                        "Syntax/token normalization" to sumInt { it.h23SyntaxNormalizationCharacterDelta },
                        "Redundant geometry cleanup" to sumInt { it.h23RedundantGeometryCharacterDelta },
                        "Arc cleanup" to sumInt { it.h23ArcCleanupCharacterDelta },
                        "Curve simplification" to sumInt { it.h23CurveSimplificationCharacterDelta },
                        "Collinear consolidation" to sumInt { it.h23CollinearConsolidationCharacterDelta },
                        "Local command shortening" to sumInt { it.h23LocalCommandShorteningCharacterDelta },
                        "Global command minimization" to sumInt { it.h23GlobalCommandMinimizationCharacterDelta },
                        "Global numeric serialization" to sumInt { it.h23GlobalNumericSerializationCharacterDelta }
                    )
                    h23StageTotals.forEach { (label, delta) ->
                        appendLine("$label: ${formatSignedStageDelta(delta)}")
                    }
                    appendLine("Note: H2.3 internal deltas describe attempted rewrites; H2.4 may reject a larger completed candidate.")

                    appendLine()
                    appendLine("H2.4 whole-path production size guard")
                    appendLine("────────────────────────────────")
                    appendLine(
                        "Completed PathData rewrites rejected for size: " +
                            formatCount(sumInt { it.h24PathSyntaxCandidatesRejectedForSize })
                    )
                    appendLine(
                        "Characters of path growth avoided: " +
                            formatCount(sumInt { it.h24PathSyntaxCharactersAvoided })
                    )
                    appendLine(
                        "Policy: accept the completed PathData rewrite only when its length is <= the original; " +
                            "equal-length optimized output is retained."
                    )

                    val regressionFiles = successful.filter { (_, data) ->
                        data.optimizedXmlCharactersAfter > data.optimizedXmlCharactersBefore
                    }
                    appendLine()
                    appendLine("Files with net optimization size regression: ${regressionFiles.size}")
                    if (regressionFiles.isNotEmpty()) {
                        appendLine("Per-regression attribution")
                        regressionFiles.forEach { (file, data) ->
                            val netAdded =
                                data.optimizedXmlCharactersAfter - data.optimizedXmlCharactersBefore
                            val stages = listOf(
                                "path syntax/colors" to data.h21PathSyntaxCharacterDelta,
                                "pruning/group cleanup" to data.h21PruningCharacterDelta,
                                "transform optimization" to data.h21TransformCharacterDelta,
                                "near-integer snapping" to data.h21NearIntegerCharacterDelta,
                                "deduplication/merging" to data.h21DedupMergeCharacterDelta,
                                "decimal canonicalization" to data.h21DecimalCanonicalizationCharacterDelta,
                                "final formatting" to data.h21FormattingCharacterDelta
                            )
                            val firstGrowth = stages.firstOrNull { it.second < 0 }
                            val largestGrowth = stages
                                .filter { it.second < 0 }
                                .minByOrNull { it.second }

                            appendLine("⚠ ${file.fileName}: +${formatCount(netAdded.toLong())} characters net")
                            appendLine(
                                "    H2.2 pathData syntax: " +
                                    formatSignedStageDelta(data.h22PathDataSyntaxCharacterDelta.toLong())
                            )
                            appendLine(
                                "    H2.2 color normalization: " +
                                    formatSignedStageDelta(data.h22ColorNormalizationCharacterDelta.toLong())
                            )
                            val h23Stages = listOf(
                                "syntax/token normalization" to data.h23SyntaxNormalizationCharacterDelta,
                                "redundant geometry cleanup" to data.h23RedundantGeometryCharacterDelta,
                                "arc cleanup" to data.h23ArcCleanupCharacterDelta,
                                "curve simplification" to data.h23CurveSimplificationCharacterDelta,
                                "collinear consolidation" to data.h23CollinearConsolidationCharacterDelta,
                                "local command shortening" to data.h23LocalCommandShorteningCharacterDelta,
                                "global command minimization" to data.h23GlobalCommandMinimizationCharacterDelta,
                                "global numeric serialization" to data.h23GlobalNumericSerializationCharacterDelta
                            )
                            val h23FirstGrowth = h23Stages.firstOrNull { it.second < 0 }
                            val h23LargestGrowth = h23Stages.filter { it.second < 0 }.minByOrNull { it.second }
                            appendLine(
                                "    H2.3 first internal growth: " +
                                    (h23FirstGrowth?.let { "${it.first} (${formatSignedStageDelta(it.second.toLong())})" }
                                        ?: "none recorded")
                            )
                            appendLine(
                                "    H2.3 largest internal growth: " +
                                    (h23LargestGrowth?.let { "${it.first} (${formatSignedStageDelta(it.second.toLong())})" }
                                        ?: "none recorded")
                            )
                            appendLine(
                                "    H2.3 stages: " + h23Stages.joinToString(", ") { (label, delta) ->
                                    "$label=${formatSignedStageDelta(delta.toLong())}"
                                }
                            )
                            appendLine(
                                "    first growth stage: " +
                                    (firstGrowth?.let { "${it.first} (${formatSignedStageDelta(it.second.toLong())})" }
                                        ?: "none recorded")
                            )
                            appendLine(
                                "    largest growth stage: " +
                                    (largestGrowth?.let { "${it.first} (${formatSignedStageDelta(it.second.toLong())})" }
                                        ?: "none recorded")
                            )
                            appendLine(
                                "    stages: " +
                                    stages.joinToString(", ") { (label, delta) ->
                                        "$label=${formatSignedStageDelta(delta.toLong())}"
                                    }
                            )
                        }
                    }

                    appendLine()
                    appendLine("Stage savings (legacy non-negative counters)")
                    appendLine("────────────────────────────────")
                    stageSavings.sortedByDescending { it.second }.forEach { (label, saved) ->
                        appendLine("$label: ${formatCount(saved)} characters")
                    }
                    appendLine("Note: legacy stage-savings counters clamp growth to zero; use H2.1 signed attribution above for regression analysis.")

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Optimizer pass attribution")
                    appendLine("────────────────────────────────")
                    appendLine("Total optimization wrapper time: ${formatNanos(optimizationNanos)}")
                    appendLine("Production pass 1: ${formatNanos(productionPassNanos)} (${percentNanos(productionPassNanos, optimizationNanos)})")
                    appendLine("Independent idempotence pass 2: ${formatNanos(idempotencePassNanos)} (${percentNanos(idempotencePassNanos, optimizationNanos)})")
                    appendLine("Fixed-point verification pass 3: ${formatNanos(fixedPointPassNanos)} (${percentNanos(fixedPointPassNanos, optimizationNanos)})")
                    appendLine("Guarded production convergence: ${formatNanos(convergenceGuardNanos)} (${percentNanos(convergenceGuardNanos, optimizationNanos)})")
                    appendLine("Final VectorDrawable validation: ${formatNanos(finalValidationNanos)} (${percentNanos(finalValidationNanos, optimizationNanos)})")
                    appendLine("Wrapper / clip optimization / setup / accounting remainder: ${formatNanos(wrapperOtherNanos)} (${percentNanos(wrapperOtherNanos, optimizationNanos)})")
                    appendLine("Note: pass 2 and pass 3 are correctness verification work, not missing production-pass stages.")

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Production-pass stage runtime (pass 1)")
                    appendLine("────────────────────────────────")
                    appendLine("Production pass 1 total: ${formatNanos(productionPassNanos)}")
                    stageTimes.sortedByDescending { it.second }.forEach { (label, nanos) ->
                        appendLine("$label: ${formatNanos(nanos)} (${percentNanos(nanos, productionPassNanos)})")
                    }
                    appendLine("Other pass-1 overhead: ${formatNanos(firstPassOtherNanos)} (${percentNanos(firstPassOtherNanos, productionPassNanos)})")
                    appendLine("Note: stage timers above describe pass 1 only and should not be compared directly with total multi-pass optimization time.")

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Per-file summary")
                    appendLine("────────────────────────────────")
                    successful.forEach { (file, data) ->
                        val fileDelta =
                            data.optimizedXmlCharactersBefore.toLong() -
                                data.optimizedXmlCharactersAfter.toLong()
                        appendLine(
                            "✓ ${file.fileName}: " +
                                "${data.convertedPathCount} paths, " +
                                "${data.generatedGroupCount} groups, " +
                                "${formatSignedDeltaShort(fileDelta)}, " +
                                "${formatNanos(file.elapsedNanos)}"
                        )
                        val fileKnownTopLevel =
                            data.optimizerProductionPassNanos.coerceAtLeast(0L) +
                                data.optimizerIdempotencePassNanos.coerceAtLeast(0L) +
                                data.optimizerFixedPointPassNanos.coerceAtLeast(0L) +
                                data.g315GuardNanos.coerceAtLeast(0L) +
                                data.finalOutputValidationNanos.coerceAtLeast(0L)
                        val fileWrapperOther =
                            (data.outputOptimizationNanos.coerceAtLeast(0L) - fileKnownTopLevel)
                                .coerceAtLeast(0L)
                        val fileFirstPassStages =
                            data.optimizationPathSyntaxNanos.coerceAtLeast(0L) +
                                data.optimizationNumericCleanupNanos.coerceAtLeast(0L) +
                                data.optimizationDeduplicationNanos.coerceAtLeast(0L) +
                                data.optimizationTransformsNanos.coerceAtLeast(0L) +
                                data.optimizationPruningCleanupNanos.coerceAtLeast(0L) +
                                data.optimizationFormattingNanos.coerceAtLeast(0L)
                        val fileFirstPassOther =
                            (data.optimizerProductionPassNanos.coerceAtLeast(0L) - fileFirstPassStages)
                                .coerceAtLeast(0L)
                        appendLine(
                            "    passes: p1=${formatNanos(data.optimizerProductionPassNanos)}, " +
                                "p2=${formatNanos(data.optimizerIdempotencePassNanos)}, " +
                                "p3=${formatNanos(data.optimizerFixedPointPassNanos)}, " +
                                "guard=${formatNanos(data.g315GuardNanos)}, " +
                                "validate=${formatNanos(data.finalOutputValidationNanos)}, " +
                                "wrapperOther=${formatNanos(fileWrapperOther)}"
                        )
                        appendLine(
                            "    pass1 stages: path=${formatNanos(data.optimizationPathSyntaxNanos)}, " +
                                "numeric=${formatNanos(data.optimizationNumericCleanupNanos)}, " +
                                "merge=${formatNanos(data.optimizationDeduplicationNanos)}, " +
                                "transform=${formatNanos(data.optimizationTransformsNanos)}, " +
                                "prune=${formatNanos(data.optimizationPruningCleanupNanos)}, " +
                                "format=${formatNanos(data.optimizationFormattingNanos)}, " +
                                "other=${formatNanos(fileFirstPassOther)}"
                        )
                        if (data.h24PathSyntaxCandidatesRejectedForSize > 0) {
                            appendLine(
                                "    H2.4 size guard: rejected=${data.h24PathSyntaxCandidatesRejectedForSize}, " +
                                    "growthAvoided=${data.h24PathSyntaxCharactersAvoided} chars"
                            )
                        }
                        if (data.adjacentPathPairsExamined > 0 ||
                            data.compatiblePathsMerged > 0 ||
                            data.compatiblePathMergesPreservedForSize > 0
                        ) {
                            appendLine(
                                "    merge opportunities: examined=${data.adjacentPathPairsExamined}, " +
                                    "samePaint=${data.adjacentPathPairsSamePaint}, " +
                                    "accepted=${data.compatiblePathsMerged}, " +
                                    "sizeRejected=${data.adjacentPathMergeRejectedForSize}, " +
                                    "safetyRejected=${data.adjacentPathMergeRejectedOverlapSafety}, " +
                                    "geometryRejected=${data.adjacentPathMergeRejectedUnsupportedGeometry}, " +
                                    "paintMismatch=${data.adjacentPathMergeRejectedPaintMismatch}"
                            )
                        }
                    }
                }

                files.filterNot { it.success }.forEach { file ->
                    appendLine()
                    appendLine("✕ ${file.fileName}: ${file.error ?: "conversion failed"}")
                }
            }.trimEnd()
        }
    }

    fun run(
        inputs: List<CorpusInput>,
        outputDpSize: Int,
        conversionProfile: String,
        onProgress: ((completed: Int, total: Int, fileName: String) -> Unit)? = null
    ): CorpusResult {
        val start = System.nanoTime()
        val results = ArrayList<FileResult>(inputs.size)

        inputs.forEachIndexed { index, input ->
            val fileStart = System.nanoTime()
            val sourceFeatures = inspectSourceFeatures(input.svg)
            val result = try {
                val conversion = SvgToVectorConverter.convert(
                    svg = input.svg,
                    outputDpSize = outputDpSize,
                    conversionProfile = conversionProfile
                )
                FileResult(
                    fileName = input.fileName,
                    success = true,
                    reportData = conversion.reportData,
                    outputCharacters = conversion.xml.length,
                    elapsedNanos = System.nanoTime() - fileStart,
                    sourcePathElementCount = sourceFeatures.pathElementCount,
                    sourceHasGroup = sourceFeatures.hasGroup,
                    sourceHasTransform = sourceFeatures.hasTransform,
                    sourceHasStroke = sourceFeatures.hasStroke,
                    sourceHasGradient = sourceFeatures.hasGradient,
                    sourceHasClipPath = sourceFeatures.hasClipPath,
                    sourceHasMask = sourceFeatures.hasMask,
                    sourceHasUse = sourceFeatures.hasUse
                )
            } catch (throwable: Throwable) {
                FileResult(
                    fileName = input.fileName,
                    success = false,
                    reportData = null,
                    outputCharacters = 0,
                    elapsedNanos = System.nanoTime() - fileStart,
                    sourcePathElementCount = sourceFeatures.pathElementCount,
                    sourceHasGroup = sourceFeatures.hasGroup,
                    sourceHasTransform = sourceFeatures.hasTransform,
                    sourceHasStroke = sourceFeatures.hasStroke,
                    sourceHasGradient = sourceFeatures.hasGradient,
                    sourceHasClipPath = sourceFeatures.hasClipPath,
                    sourceHasMask = sourceFeatures.hasMask,
                    sourceHasUse = sourceFeatures.hasUse,
                    error = throwable.describeForProfile()
                )
            }
            results += result
            onProgress?.invoke(index + 1, inputs.size, input.fileName)
        }

        return CorpusResult(
            files = results,
            elapsedNanos = System.nanoTime() - start
        )
    }

    private data class SourceFeatures(
        val pathElementCount: Int,
        val hasGroup: Boolean,
        val hasTransform: Boolean,
        val hasStroke: Boolean,
        val hasGradient: Boolean,
        val hasClipPath: Boolean,
        val hasMask: Boolean,
        val hasUse: Boolean
    )

    private fun inspectSourceFeatures(svg: String): SourceFeatures =
        SourceFeatures(
            pathElementCount = SOURCE_PATH_TAG.findAll(svg).count(),
            hasGroup = SOURCE_GROUP_TAG.containsMatchIn(svg),
            hasTransform = SOURCE_TRANSFORM_ATTRIBUTE.containsMatchIn(svg),
            hasStroke = SOURCE_STROKE_ATTRIBUTE.containsMatchIn(svg) ||
                SOURCE_STROKE_STYLE.containsMatchIn(svg),
            hasGradient = SOURCE_GRADIENT_TAG.containsMatchIn(svg),
            hasClipPath = SOURCE_CLIP_PATH_TAG.containsMatchIn(svg),
            hasMask = SOURCE_MASK_TAG.containsMatchIn(svg),
            hasUse = SOURCE_USE_TAG.containsMatchIn(svg)
        )

    private fun StringBuilder.appendCounter(label: String, value: Long) {
        appendLine("$label: ${formatCount(value)}")
    }

    private fun formatCount(value: Long): String =
        String.format(Locale.US, "%,d", value)

    private fun formatValidationStage(stage: SvgValidationStageReport): String {
        if (!stage.attempted) return "not attempted"
        if (stage.passed) return "PASS"
        val details = mutableListOf<String>()
        if (stage.invalidPathDataCount > 0) details += "invalidPathData=${stage.invalidPathDataCount}"
        if (stage.nonFiniteNumberCount > 0) details += "nonFinite=${stage.nonFiniteNumberCount}"
        if (stage.malformedStructureCount > 0) details += "malformedXml=${stage.malformedStructureCount}"
        if (stage.invalidViewportCount > 0) details += "invalidViewport=${stage.invalidViewportCount}"
        if (stage.unsupportedOutputConstructCount > 0) details += "unsupported=${stage.unsupportedOutputConstructCount}"
        val witness = stage.witness.takeIf { it.isNotBlank() }?.let { "; witness=${it.replace("\n", " ")}" }.orEmpty()
        return "FAIL (${details.joinToString(", ")})$witness"
    }

    private fun formatNanos(nanos: Long): String =
        String.format(Locale.US, "%.2f ms", nanos.coerceAtLeast(0L) / 1_000_000.0)

    private fun formatSignedDelta(delta: Long, before: Long): String {
        val signed = if (delta > 0L) "+${formatCount(delta)}" else formatCount(delta)
        val percent = if (before <= 0L) 0.0 else delta * 100.0 / before.toDouble()
        val meaning = when {
            delta > 0L -> "${formatCount(delta)} characters saved"
            delta < 0L -> "${formatCount(-delta)} characters added"
            else -> "no size change"
        }
        return "$signed (${String.format(Locale.US, "%+.1f%%", percent)}; $meaning)"
    }

    private fun formatSignedDeltaShort(delta: Long): String =
        when {
            delta > 0L -> "+${formatCount(delta)} chars saved"
            delta < 0L -> "${formatCount(delta)} chars (${formatCount(-delta)} added)"
            else -> "0 chars changed"
        }

    private fun formatSignedStageDelta(delta: Long): String =
        when {
            delta > 0L -> "+${formatCount(delta)} saved"
            delta < 0L -> "${formatCount(delta)} (${formatCount(-delta)} added)"
            else -> "0"
        }

    private fun percent(part: Long, whole: Long): String =
        if (whole <= 0L) "0.0%" else
            String.format(Locale.US, "%.1f%%", part * 100.0 / whole.toDouble())

    private fun percentNanos(part: Long, whole: Long): String =
        if (whole <= 0L) "0.0%" else
            String.format(Locale.US, "%.1f%%", part * 100.0 / whole.toDouble())

    private val SOURCE_PATH_TAG =
        Regex("""<\s*path(?:\s|/?>)""", RegexOption.IGNORE_CASE)
    private val SOURCE_GROUP_TAG =
        Regex("""<\s*g(?:\s|/?>)""", RegexOption.IGNORE_CASE)
    private val SOURCE_TRANSFORM_ATTRIBUTE =
        Regex("""\btransform\s*=""", RegexOption.IGNORE_CASE)
    private val SOURCE_STROKE_ATTRIBUTE =
        Regex("""\bstroke(?:-[A-Za-z]+)?\s*=""", RegexOption.IGNORE_CASE)
    private val SOURCE_STROKE_STYLE =
        Regex("""(?:^|[;"'])\s*stroke(?:-[A-Za-z]+)?\s*:""", RegexOption.IGNORE_CASE)
    private val SOURCE_GRADIENT_TAG =
        Regex("""<\s*(?:linearGradient|radialGradient)(?:\s|/?>)""", RegexOption.IGNORE_CASE)
    private val SOURCE_CLIP_PATH_TAG =
        Regex("""<\s*clipPath(?:\s|/?>)""", RegexOption.IGNORE_CASE)
    private val SOURCE_MASK_TAG =
        Regex("""<\s*mask(?:\s|/?>)""", RegexOption.IGNORE_CASE)
    private val SOURCE_USE_TAG =
        Regex("""<\s*use(?:\s|/?>)""", RegexOption.IGNORE_CASE)

    private fun Throwable.describeForProfile(): String {
        val type = this::class.java.simpleName.ifBlank { "Throwable" }
        val detail = message?.trim().orEmpty()
        return if (detail.isBlank()) type else "$type: $detail"
    }
}
