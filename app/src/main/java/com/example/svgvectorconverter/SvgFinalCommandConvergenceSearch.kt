package com.example.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** Android-free parallel entry point for the G3.6 final-command convergence search. */
object SvgFinalCommandConvergenceSearch {

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
            executor.submit<SvgPathDataOptimizer.FinalCommandConvergenceResult> {
                SvgPathDataOptimizer.runFinalCommandConvergenceStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumWitnesses = 5,
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
        val unchanged = results.sumOf { it.unchangedByConvergence }
        val changed = results.sumOf { it.changedByConvergence }
        val matchedIndependent = results.sumOf { it.matchedIndependentSecondPass }
        val differedIndependent = results.sumOf { it.differedFromIndependentSecondPass }
        val fixed = results.sumOf { it.fixedAfterConvergence }
        val stillChanged = results.sumOf { it.stillChangedAfterVerification }
        val semanticMismatches = results.sumOf { it.semanticMismatchCount }
        val saved = results.sumOf { it.totalCharactersSaved }
        val grown = results.sumOf { it.totalCharactersGrown }
        val convergenceNanos = results.sumOf { it.convergenceNanos }
        val verificationNanos = results.sumOf { it.verificationNanos }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.6 automated final-command convergence differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("Unchanged by convergence pass: $unchanged")
            appendLine("Changed by convergence pass: $changed")
            appendLine("Matched independent full pass 2: $matchedIndependent")
            appendLine("Differed from independent full pass 2: $differedIndependent")
            appendLine("Fixed after convergence: $fixed")
            appendLine("Still changed after verification: $stillChanged")
            appendLine("Semantic mismatches: $semanticMismatches")
            appendLine("Characters saved by convergence: $saved")
            appendLine("Characters added by convergence: $grown")
            appendLine("Candidate convergence CPU time: " + String.format(java.util.Locale.US, "%.2f ms", convergenceNanos / 1_000_000.0))
            appendLine("Full verification CPU time: " + String.format(java.util.Locale.US, "%.2f ms", verificationNanos / 1_000_000.0))
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            when {
                semanticMismatches > 0 -> {
                    appendLine("RESULT: G3.6 produced semantic mismatches.")
                    appendLine("Recommendation: reject the candidate and investigate every mismatch witness.")
                }
                stillChanged > 0 -> {
                    appendLine("RESULT: the G3.6 convergence candidate did not reach a fixed point for every case.")
                    appendLine("Recommendation: keep production unchanged and inspect the remaining verification witnesses.")
                }
                differedIndependent > 0 -> {
                    appendLine("RESULT: the G3.6 candidate was fixed but did not exactly reproduce every independent second-pass result.")
                    appendLine("Recommendation: keep production unchanged until the spelling differences are classified.")
                }
                valid > 0 -> {
                    appendLine("RESULT: the G3.6 candidate exactly reproduced the independent second pass and remained fixed under full verification across every seed.")
                    appendLine("Recommendation: advance to a guarded VectorDrawable-level production trial.")
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
