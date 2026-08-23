package com.littlemachineworks.svgvectorconverter

import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicIntegerArray

/** Android-free entry point for the G3.5 path optimizer fixed-point search. */
object SvgPathFixedPointInvestigationSearch {

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
            0x6315_2026L,
            0x6315_0001L,
            0x6315_0002L,
            0x1D3F_2026L
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
            executor.submit<SvgPathDataOptimizer.PathFixedPointInvestigationResult> {
                SvgPathDataOptimizer.runPathFixedPointInvestigationStressSearch(
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
        val fixed = results.sumOf { it.alreadyFixedAfterFirstPass }
        val changed = results.sumOf { it.secondPassChangedCases }
        val stabilizedThird = results.sumOf { it.stabilizedOnThirdPass }
        val stillChanging = results.sumOf { it.stillChangingAfterThirdPass }
        val stageCounts = linkedMapOf<String, Int>()
        results.forEach { result ->
            result.firstChangingStageCounts.forEach { (stage, count) ->
                stageCounts[stage] = (stageCounts[stage] ?: 0) + count
            }
        }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.5 automated path optimizer fixed-point investigation")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Parallel workers: $workerCount")
            appendLine("Available processors: $availableProcessors")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("Already fixed after pass 1: $fixed")
            appendLine("Changed on pass 2: $changed")
            appendLine("Stabilized on pass 3: $stabilizedThird")
            appendLine("Still changing after pass 3: $stillChanging")
            appendLine("Elapsed: " + String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0))
            appendLine()
            appendLine("First changing stage")
            val stages = listOf(
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
            stages.forEach { stage ->
                val count = stageCounts[stage] ?: 0
                if (count > 0) appendLine("• $stage: $count")
            }
            appendLine()
            if (changed == 0 && valid > 0) {
                appendLine("RESULT: every generated path was a fixed point after one optimizer pass.")
            } else {
                appendLine("RESULT: path optimizer fixed-point drift was detected.")
                appendLine("Recommendation: repair the dominant first-changing stage interaction, then rerun G3.5 before revisiting idempotence reuse.")
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
