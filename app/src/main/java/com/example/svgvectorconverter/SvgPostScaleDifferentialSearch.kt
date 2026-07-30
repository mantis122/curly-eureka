package com.example.svgvectorconverter

/**
 * Android-free entry point for the G2.25 full-vs-narrowed post-scale
 * differential stress search.
 *
 * Developer Tools can call [runDefault] and display/save the returned report.
 */
object SvgPostScaleDifferentialSearch {

    fun runDefault(): String =
        run(
            casesPerSeed = 25_000,
            seeds = listOf(
                0x6215_2026L,
                0x6215_0001L,
                0x6215_0002L,
                0x5CA1_E001L
            )
        )

    fun run(
        casesPerSeed: Int,
        seeds: List<Long>
    ): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val results = seeds.map { seed ->
            SvgPathDataOptimizer.runPostScaleDifferentialStressSearch(
                caseCount = casesPerSeed,
                seed = seed,
                maximumWitnesses = 10
            )
        }

        val totalValid = results.sumOf { it.validCases }
        val totalRejected = results.sumOf { it.invalidGeneratedCases }
        val totalIdentical = results.sumOf { it.byteIdenticalCount }
        val totalDifferences = results.sumOf { it.byteDifferenceCount }
        val totalCanonicalDifferences = results.sumOf { it.canonicalDifferenceCount }
        val totalGeometryMismatches = results.sumOf { it.geometryMismatchCount }
        val totalEqualLengthDifferences = results.sumOf { it.equalLengthDifferenceCount }
        val totalFullShorter = results.sumOf { it.fullShorterCount }
        val totalNarrowedShorter = results.sumOf { it.narrowedShorterCount }
        val elapsedMilliseconds = results.sumOf { it.elapsedNanos } / 1_000_000.0

        return buildString {
            appendLine("G2.25 automated post-scale differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Valid comparisons: $totalValid")
            appendLine("Rejected generated cases: $totalRejected")
            appendLine("Byte-identical results: $totalIdentical")
            appendLine("Byte differences: $totalDifferences")
            appendLine("Canonical representation differences: $totalCanonicalDifferences")
            appendLine("Sampled geometry mismatches: $totalGeometryMismatches")
            appendLine("Equal-length spelling differences: $totalEqualLengthDifferences")
            appendLine("Full optimizer shorter: $totalFullShorter")
            appendLine("Narrowed optimizer shorter: $totalNarrowedShorter")
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsedMilliseconds)
            )
            appendLine()

            when {
                totalGeometryMismatches > 0 -> {
                    appendLine("RESULT: sampled geometry mismatches were detected.")
                    appendLine(
                        "Recommendation: do not activate the narrowed production pipeline. " +
                            "Investigate the mismatch witnesses first."
                    )
                }

                totalDifferences > 0 -> {
                    appendLine("RESULT: geometry matched, but byte differences were found.")
                    appendLine(
                        "Recommendation: keep the full post-scale optimizer authoritative " +
                            "until every serialization difference is understood."
                    )
                }

                else -> {
                    appendLine("RESULT: all valid full/narrowed comparisons were byte-identical.")
                    appendLine(
                        "Recommendation: the narrowed post-scale pipeline is a strong candidate " +
                            "for a guarded production trial, subject to the locked regression suite."
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
