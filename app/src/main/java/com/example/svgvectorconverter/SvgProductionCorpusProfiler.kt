package com.example.svgvectorconverter

import java.util.Locale

/**
 * H1.1 production corpus profiler.
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
            val xmlSaved = (xmlBefore - xmlAfter).coerceAtLeast(0L)
            val pathBefore = sumInt { it.pathDataCharactersBefore }
            val pathAfter = sumInt { it.pathDataCharactersAfter }
            val pathSaved = (pathBefore - pathAfter).coerceAtLeast(0L)

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

            return buildString {
                appendLine("H1.1 production corpus profile")
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
                    appendLine("Net optimizer characters saved: ${formatCount(xmlSaved)} (${percent(xmlSaved, xmlBefore)})")
                    appendLine("PathData characters before: ${formatCount(pathBefore)}")
                    appendLine("PathData characters after: ${formatCount(pathAfter)}")
                    appendLine("PathData characters saved: ${formatCount(pathSaved)} (${percent(pathSaved, pathBefore)})")
                    appendLine("Final drawable paths: ${formatCount(sumInt { it.convertedPathCount })}")
                    appendLine("Final groups: ${formatCount(sumInt { it.generatedGroupCount })}")
                    appendLine("Paths optimized: ${formatCount(sumInt { it.pathDataOptimizedCount })}")
                    appendLine("Files with warnings: ${successful.count { (_, d) -> d.warningCount > 0 }}")
                    appendLine("Final validation failures: ${successful.count { (_, d) -> !d.finalOutputValidationPassed }}")
                    appendLine("Optimizer fixed-point failures: ${successful.count { (_, d) -> !d.optimizerReachedFixedPoint }}")

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
                    appendLine("Stage savings")
                    appendLine("────────────────────────────────")
                    stageSavings.sortedByDescending { it.second }.forEach { (label, saved) ->
                        appendLine("$label: ${formatCount(saved)} characters")
                    }
                    appendLine("Note: stage savings are instrumentation totals and are not necessarily additive.")

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Stage runtime")
                    appendLine("────────────────────────────────")
                    val optimizationNanos = sumLong { it.outputOptimizationNanos }
                    appendLine("Total optimization time: ${formatNanos(optimizationNanos)}")
                    stageTimes.sortedByDescending { it.second }.forEach { (label, nanos) ->
                        appendLine("$label: ${formatNanos(nanos)} (${percentNanos(nanos, optimizationNanos)})")
                    }

                    appendLine()
                    appendLine("────────────────────────────────")
                    appendLine("Per-file summary")
                    appendLine("────────────────────────────────")
                    successful.forEach { (file, data) ->
                        val saved = (data.optimizedXmlCharactersBefore - data.optimizedXmlCharactersAfter).coerceAtLeast(0)
                        appendLine(
                            "✓ ${file.fileName}: " +
                                "${data.convertedPathCount} paths, " +
                                "${data.generatedGroupCount} groups, " +
                                "${formatCount(saved.toLong())} chars saved, " +
                                "${formatNanos(file.elapsedNanos)}"
                        )
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
                    elapsedNanos = System.nanoTime() - fileStart
                )
            } catch (throwable: Throwable) {
                FileResult(
                    fileName = input.fileName,
                    success = false,
                    reportData = null,
                    outputCharacters = 0,
                    elapsedNanos = System.nanoTime() - fileStart,
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

    private fun StringBuilder.appendCounter(label: String, value: Long) {
        appendLine("$label: ${formatCount(value)}")
    }

    private fun formatCount(value: Long): String =
        String.format(Locale.US, "%,d", value)

    private fun formatNanos(nanos: Long): String =
        String.format(Locale.US, "%.2f ms", nanos.coerceAtLeast(0L) / 1_000_000.0)

    private fun percent(part: Long, whole: Long): String =
        if (whole <= 0L) "0.0%" else
            String.format(Locale.US, "%.1f%%", part * 100.0 / whole.toDouble())

    private fun percentNanos(part: Long, whole: Long): String =
        if (whole <= 0L) "0.0%" else
            String.format(Locale.US, "%.1f%%", part * 100.0 / whole.toDouble())

    private fun Throwable.describeForProfile(): String {
        val type = this::class.java.simpleName.ifBlank { "Throwable" }
        val detail = message?.trim().orEmpty()
        return if (detail.isBlank()) type else "$type: $detail"
    }
}
