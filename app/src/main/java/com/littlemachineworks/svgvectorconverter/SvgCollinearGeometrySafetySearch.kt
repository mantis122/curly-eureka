package com.littlemachineworks.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** Android-free parallel entry point for the G3.8 collinear geometry-safety investigation. */
object SvgCollinearGeometrySafetySearch {

    data class Progress(
        val completedCases: Int,
        val totalCases: Int,
        val workerCount: Int,
        val perSeedProcessed: List<Int>,
        val casesPerSeed: Int
    ) {
        val percentComplete: Double
            get() = if (totalCases <= 0) 100.0
            else completedCases.toDouble() * 100.0 / totalCases.toDouble()
    }

    fun runDefault(
        progressCallback: ((Progress) -> Unit)? = null
    ): String = run(
        casesPerSeed = 25_000,
        seeds = listOf(
            0x6316_2026L,
            0x6316_0001L,
            0x6316_0002L,
            0x1D40_2026L
        ),
        progressCallback = progressCallback
    )

    fun run(
        casesPerSeed: Int,
        seeds: List<Long>,
        progressCallback: ((Progress) -> Unit)? = null
    ): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val started = System.nanoTime()
        val totalCases = casesPerSeed * seeds.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val workerCount = minOf(4, seeds.size, availableProcessors)
        val perSeedProgress = AtomicIntegerArray(seeds.size)
        val executor = Executors.newFixedThreadPool(workerCount)

        val futures = seeds.mapIndexed { index, seed ->
            executor.submit<SvgPathDataOptimizer.CollinearGeometrySafetyResult> {
                SvgPathDataOptimizer.runCollinearGeometrySafetyStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumMismatchWitnesses = 64,
                    progressCallback = { currentSeedProcessed ->
                        perSeedProgress.set(index, currentSeedProcessed)
                        if (progressCallback != null) {
                            val snapshot = List(seeds.size) { seedIndex ->
                                perSeedProgress.get(seedIndex)
                            }
                            progressCallback.invoke(
                                Progress(
                                    completedCases = snapshot.sum(),
                                    totalCases = totalCases,
                                    workerCount = workerCount,
                                    perSeedProcessed = snapshot,
                                    casesPerSeed = casesPerSeed
                                )
                            )
                        }
                    }
                )
            }
        }

        val results = try {
            futures.map { it.get() }
        } finally {
            executor.shutdownNow()
        }

        val valid = results.sumOf { it.validCases }
        val rejected = results.sumOf { it.rejectedGeneratedCases }
        val candidateChanged = results.sumOf { it.candidateChangedCases }
        val collinearChanged = results.sumOf { it.collinearChangedCases }
        val standardMismatches = results.sumOf { it.standardMismatchCases }
        val denseMismatches = results.sumOf { it.denseMismatchCases }
        val directCollinearMismatches = results.sumOf { it.directCollinearDenseMismatchCases }
        val standardOnlyMismatches = results.sumOf { it.standardOnlyMismatchCases }
        val sourceToFirstMismatches = results.sumOf { it.sourceToFirstDenseMismatchCases }
        val sourceToSecondMismatches = results.sumOf { it.sourceToSecondDenseMismatchCases }
        val denseChecks = results.sumOf { it.denseChecks }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.8 automated collinear consolidation geometry-safety differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("G3.7 candidate changed: $candidateChanged")
            appendLine("Collinear-consolidation stage changed: $collinearChanged")
            appendLine("Standard sampler mismatches: $standardMismatches")
            appendLine("Dense sampler mismatches: $denseMismatches")
            appendLine("Direct pre/post-collinear dense mismatches: $directCollinearMismatches")
            appendLine("Standard-only mismatches (dense check passed): $standardOnlyMismatches")
            appendLine("Source → pass-1 dense mismatches: $sourceToFirstMismatches")
            appendLine("Source → pass-2 dense mismatches: $sourceToSecondMismatches")
            appendLine("Dense diagnostic checks: $denseChecks")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            when {
                denseMismatches > 0 -> {
                    appendLine("RESULT: G3.8 reproduced dense geometry mismatches.")
                    appendLine("Recommendation: use the prioritized witnesses to determine whether collinear consolidation or the geometry comparator is responsible before changing production behavior.")
                }
                standardMismatches > 0 -> {
                    appendLine("RESULT: G3.8 found only standard-sampler mismatches; all dense checks passed.")
                    appendLine("Recommendation: repair the geometry comparator, then rerun G3.7 before reconsidering convergence.")
                }
                valid > 0 -> {
                    appendLine("RESULT: G3.8 found no geometry mismatches in the repeated corpus.")
                    appendLine("Recommendation: rerun G3.7 once more before proceeding to any guarded production trial.")
                }
                else -> appendLine("RESULT: no valid comparisons were produced.")
            }

            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Seed ${index + 1}")
                append(result.toPlainTextReport())
            }
        }
    }
}
