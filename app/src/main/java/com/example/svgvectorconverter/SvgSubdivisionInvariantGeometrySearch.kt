package com.example.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** Android-free parallel entry point for the G3.9 subdivision-invariant comparator investigation. */
object SvgSubdivisionInvariantGeometrySearch {

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

    fun runDefault(progressCallback: ((Progress) -> Unit)? = null): String = run(
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
            executor.submit<SvgPathDataOptimizer.SubdivisionInvariantGeometryResult> {
                SvgPathDataOptimizer.runSubdivisionInvariantGeometryStressSearch(
                    caseCount = casesPerSeed,
                    seed = seed,
                    maximumWitnesses = 64,
                    progressCallback = { processed ->
                        perSeedProgress.set(index, processed)
                        if (progressCallback != null) {
                            val snapshot = List(seeds.size) { seedIndex -> perSeedProgress.get(seedIndex) }
                            progressCallback(
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
        val denseCandidate = results.sumOf { it.denseCandidateMismatchCases }
        val invariantCandidate = results.sumOf { it.invariantCandidateMismatchCases }
        val denseCandidateCleared = results.sumOf { it.denseCandidateMismatchesCleared }
        val denseDirect = results.sumOf { it.denseDirectCollinearMismatchCases }
        val invariantDirect = results.sumOf { it.invariantDirectCollinearMismatchCases }
        val denseDirectCleared = results.sumOf { it.denseDirectCollinearMismatchesCleared }
        val sourceFirst = results.sumOf { it.sourceToFirstInvariantMismatchCases }
        val sourceSecond = results.sumOf { it.sourceToSecondInvariantMismatchCases }
        val checks = results.sumOf { it.invariantChecks }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.9 automated subdivision-invariant geometry comparator differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("G3.7 candidate changed: $candidateChanged")
            appendLine("Collinear-consolidation stage changed: $collinearChanged")
            appendLine("Dense candidate mismatches: $denseCandidate")
            appendLine("Subdivision-invariant candidate mismatches: $invariantCandidate")
            appendLine("Dense candidate mismatches cleared: $denseCandidateCleared")
            appendLine("Dense direct-collinear mismatches: $denseDirect")
            appendLine("Subdivision-invariant direct-collinear mismatches: $invariantDirect")
            appendLine("Dense direct-collinear mismatches cleared: $denseDirectCleared")
            appendLine("Source → pass-1 invariant mismatches: $sourceFirst")
            appendLine("Source → pass-2 invariant mismatches: $sourceSecond")
            appendLine("Subdivision-invariant checks: $checks")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            when {
                invariantDirect > 0 -> {
                    appendLine("RESULT: G3.9 found direct collinear geometry differences that survive subdivision normalization.")
                    appendLine("Recommendation: keep production unchanged and inspect every invariant direct-collinear witness.")
                }
                invariantCandidate > 0 || sourceSecond > 0 -> {
                    appendLine("RESULT: G3.9 cleared the direct collinear signal but found residual invariant geometry differences elsewhere.")
                    appendLine("Recommendation: keep production unchanged and investigate the residual witnesses.")
                }
                denseCandidate > 0 || denseDirect > 0 -> {
                    appendLine("RESULT: G3.9 classified every reproduced dense mismatch as a subdivision-sensitive comparator artifact.")
                    appendLine("Recommendation: adopt the subdivision-invariant comparator for diagnostics and rerun G3.7 before any production convergence change.")
                }
                valid > 0 -> {
                    appendLine("RESULT: G3.9 found no geometry mismatch in either comparator on the repeated corpus.")
                    appendLine("Recommendation: rerun G3.7 with the subdivision-invariant comparator before any production change.")
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
