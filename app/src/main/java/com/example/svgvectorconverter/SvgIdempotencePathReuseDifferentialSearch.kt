package com.example.svgvectorconverter

/** Android-free entry point for the G3.4 idempotence stable-path reuse search. */
object SvgIdempotencePathReuseDifferentialSearch {

    data class Progress(
        val completedCases: Int,
        val totalCases: Int,
        val seedIndex: Int,
        val seedCount: Int,
        val currentSeedProcessed: Int,
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
            0x6314_2026L,
            0x6314_0001L,
            0x6314_0002L,
            0x1D3E_2026L
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
        val results = mutableListOf<SvgPathDataOptimizer.IdempotencePathReuseDifferentialSearchResult>()

        if (totalCases == 0) {
            progressCallback?.invoke(
                Progress(
                    completedCases = 0,
                    totalCases = 0,
                    seedIndex = 1,
                    seedCount = seeds.size,
                    currentSeedProcessed = 0,
                    casesPerSeed = casesPerSeed
                )
            )
        }

        seeds.forEachIndexed { index, seed ->
            val completedBeforeSeed = index * casesPerSeed
            val result = SvgPathDataOptimizer.runIdempotencePathReuseDifferentialStressSearch(
                caseCount = casesPerSeed,
                seed = seed,
                maximumWitnesses = 4,
                progressCallback = { currentSeedProcessed ->
                    progressCallback?.invoke(
                        Progress(
                            completedCases = completedBeforeSeed + currentSeedProcessed,
                            totalCases = totalCases,
                            seedIndex = index + 1,
                            seedCount = seeds.size,
                            currentSeedProcessed = currentSeedProcessed,
                            casesPerSeed = casesPerSeed
                        )
                    )
                }
            )
            results += result
        }

        val valid = results.sumOf { it.validCases }
        val rejected = results.sumOf { it.rejectedGeneratedCases }
        val byteIdentical = results.sumOf { it.byteIdenticalSecondPasses }
        val byteDifferent = results.sumOf { it.byteDifferentSecondPasses }
        val decisionMismatches = results.sumOf { it.idempotenceDecisionMismatches }
        val independentIdempotent = results.sumOf { it.independentIdempotentCount }
        val reuseIdempotent = results.sumOf { it.reuseIdempotentCount }
        val registered = results.sumOf { it.stablePathsRegistered }
        val hits = results.sumOf { it.stableOutputHits }
        val independentNanos = results.sumOf { it.independentSecondPassNanos }
        val reuseNanos = results.sumOf { it.reusedSecondPassNanos }
        val elapsed = System.nanoTime() - started

        return buildString {
            appendLine("G3.4 automated stable-path reuse differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Valid comparisons: $valid")
            appendLine("Rejected generated cases: $rejected")
            appendLine("Byte-identical second passes: $byteIdentical")
            appendLine("Byte-different second passes: $byteDifferent")
            appendLine("Idempotence decision mismatches: $decisionMismatches")
            appendLine("Independent pass reported idempotent: $independentIdempotent")
            appendLine("Reuse pass reported idempotent: $reuseIdempotent")
            appendLine("Final pass-1 stable paths registered: $registered")
            appendLine("Stable-output cache hits: $hits")
            appendLine(
                "Independent second-pass time: " +
                    String.format(java.util.Locale.US, "%.2f ms", independentNanos / 1_000_000.0)
            )
            appendLine(
                "Reuse second-pass time: " +
                    String.format(java.util.Locale.US, "%.2f ms", reuseNanos / 1_000_000.0)
            )
            if (independentNanos > 0L) {
                val reduction = (1.0 - reuseNanos.toDouble() / independentNanos.toDouble()) * 100.0
                appendLine(
                    "Second-pass time reduction: " +
                        String.format(java.util.Locale.US, "%.1f%%", reduction)
                )
            }
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsed / 1_000_000.0)
            )
            appendLine()

            if (byteDifferent == 0 && decisionMismatches == 0 && valid > 0) {
                appendLine("RESULT: stable-path reuse matched independent recomputation across every seed.")
                appendLine(
                    "Recommendation: G3.3 can be retained permanently, subject to the locked regression suite."
                )
            } else {
                appendLine("RESULT: stable-path reuse diverged from independent recomputation.")
                appendLine(
                    "Recommendation: keep G3.3 as experimental and investigate the mismatch witnesses."
                )
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
