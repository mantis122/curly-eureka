package com.littlemachineworks.svgvectorconverter

/**
 * Convenience entry point for the F4.3 differential stress search.
 *
 * This has no Android UI dependency. A Developer Tools action can call:
 *
 *   val report = SvgCommandNumericDifferentialSearch.runDefault()
 *
 * and display or copy the returned string.
 */
object SvgCommandNumericDifferentialSearch {

    fun runDefault(): String =
        run(
            casesPerSeed = 25_000,
            seeds = listOf(
                0xF432_2026L,
                0x1357_2468L,
                0x5EED_0001L,
                0x5EED_0002L
            )
        )

    fun run(
        casesPerSeed: Int,
        seeds: List<Long>
    ): String {
        require(casesPerSeed >= 0) {
            "casesPerSeed must be non-negative"
        }
        require(seeds.isNotEmpty()) {
            "At least one seed is required"
        }

        val results = seeds.map { seed ->
            SvgPathDataOptimizer.runCommandNumericDifferentialStressSearch(
                caseCount = casesPerSeed,
                seed = seed,
                maximumWitnesses = 10
            )
        }

        val totalCases = results.sumOf { it.validCases }
        val totalImprovements =
            results.sumOf { it.secondRoundImprovements }
        val totalMismatches =
            results.sumOf { it.semanticMismatchCount }
        val elapsedMilliseconds =
            results.sumOf { it.elapsedNanos } / 1_000_000.0

        return buildString {
            appendLine("F4.3 automated differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Valid comparisons: $totalCases")
            appendLine("Second-round strict improvements: $totalImprovements")
            appendLine("Semantic mismatches: $totalMismatches")
            appendLine(
                "Elapsed: " +
                    String.format(
                        java.util.Locale.US,
                        "%.2f ms",
                        elapsedMilliseconds
                    )
            )

            appendLine()
            when {
                totalMismatches > 0 -> {
                    appendLine("RESULT: semantic mismatches were detected.")
                    appendLine(
                        "Do not make an F4.3 keep/remove decision until they are investigated."
                    )
                }

                totalImprovements > 0 -> {
                    appendLine("RESULT: F4.3 has demonstrated unique value.")
                    appendLine("Recommendation: keep the joint fixed-point pass.")
                }

                else -> {
                    appendLine("RESULT: no second-round reduction was found.")
                    appendLine(
                        "Recommendation: F4.3 is likely redundant. Remove it, then rerun " +
                            "the locked regression suite and this search once more."
                    )
                }
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
