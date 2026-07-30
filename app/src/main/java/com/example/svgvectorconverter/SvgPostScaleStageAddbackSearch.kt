package com.example.svgvectorconverter

/** Android-free entry point for the G2.26 post-scale stage-addback search. */
object SvgPostScaleStageAddbackSearch {

    fun runDefault(): String = run(
        casesPerSeed = 25_000,
        seeds = listOf(
            0x6216_2026L,
            0x6216_0001L,
            0x6216_0002L,
            0x5CA1_E026L
        )
    )

    fun run(casesPerSeed: Int, seeds: List<Long>): String {
        require(casesPerSeed >= 0) { "casesPerSeed must be non-negative" }
        require(seeds.isNotEmpty()) { "At least one seed is required" }

        val results = seeds.map { seed ->
            SvgPathDataOptimizer.runPostScaleStageAddbackStressSearch(
                caseCount = casesPerSeed,
                seed = seed,
                maximumWitnessesPerPipeline = 2,
                maximumGeometrySamplesPerPipeline = 2_000
            )
        }

        val pipelineNames = results.firstOrNull()?.pipelineResults?.map { it.pipelineName }.orEmpty()
        val totalValid = results.sumOf { it.validCases }
        val totalRejected = results.sumOf { it.invalidGeneratedCases }
        val elapsedMs = results.sumOf { it.elapsedNanos } / 1_000_000.0

        return buildString {
            appendLine("G2.26 automated post-scale stage-addback differential stress search")
            appendLine()
            appendLine("Seeds: ${seeds.size}")
            appendLine("Cases per seed: $casesPerSeed")
            appendLine("Valid source cases: $totalValid")
            appendLine("Rejected generated cases: $totalRejected")
            appendLine("Geometry sampling cap: 2,000 canonical differences per pipeline per seed")
            appendLine(
                "Elapsed: " +
                    String.format(java.util.Locale.US, "%.2f ms", elapsedMs)
            )

            pipelineNames.forEachIndexed { pipelineIndex, name ->
                val compared = results.sumOf { it.pipelineResults[pipelineIndex].comparedCases }
                val identical = results.sumOf { it.pipelineResults[pipelineIndex].byteIdenticalCount }
                val differences = results.sumOf { it.pipelineResults[pipelineIndex].byteDifferenceCount }
                val canonical = results.sumOf { it.pipelineResults[pipelineIndex].canonicalDifferenceCount }
                val geometryChecks = results.sumOf { it.pipelineResults[pipelineIndex].geometrySamplesChecked }
                val geometryMismatch = results.sumOf { it.pipelineResults[pipelineIndex].sampledGeometryMismatchCount }
                val equalLength = results.sumOf { it.pipelineResults[pipelineIndex].equalLengthDifferenceCount }
                val fullShorter = results.sumOf { it.pipelineResults[pipelineIndex].fullShorterCount }
                val candidateShorter = results.sumOf { it.pipelineResults[pipelineIndex].candidateShorterCount }
                val candidateMs = results.sumOf { it.pipelineResults[pipelineIndex].candidateNanos } / 1_000_000.0

                appendLine()
                appendLine(name)
                appendLine("  Compared: $compared")
                appendLine("  Byte-identical: $identical")
                appendLine("  Byte differences: $differences")
                appendLine("  Canonical differences: $canonical")
                appendLine("  Geometry samples checked: $geometryChecks")
                appendLine("  Sampled geometry mismatches: $geometryMismatch")
                appendLine("  Equal-length differences: $equalLength")
                appendLine("  Full optimizer shorter: $fullShorter")
                appendLine("  Candidate shorter: $candidateShorter")
                appendLine(
                    "  Candidate time: " +
                        String.format(java.util.Locale.US, "%.2f ms", candidateMs)
                )
            }

            val exactIndex = pipelineNames.indices.firstOrNull { index ->
                results.all { result ->
                    val p = result.pipelineResults[index]
                    p.comparedCases == result.validCases && p.byteDifferenceCount == 0
                }
            }

            appendLine()
            if (exactIndex != null) {
                appendLine("RESULT: an exact stage-addback pipeline was found across every seed.")
                appendLine("Smallest exact pipeline: ${pipelineNames[exactIndex]}")
                appendLine(
                    "Recommendation: use this as the candidate for a guarded production " +
                        "trial with full-optimizer fallback."
                )
            } else {
                appendLine("RESULT: no reduced pipeline was byte-identical across every seed.")
                appendLine(
                    "Recommendation: keep the full post-scale optimizer authoritative and " +
                        "inspect the earliest remaining difference class."
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
