package com.example.svgvectorconverter

/**
 * G3.13 diagnostic-only validation for an exact ordered-traversal short-circuit.
 *
 * If the exact BigDecimal traversal oracle proves that two paths have identical
 * ordered geometry, the expensive Float/adaptive-polyline comparator is skipped.
 * Otherwise comparison falls through to the existing G3.12 repaired comparator.
 * Production conversion/optimization behavior is unchanged.
 */
internal object SvgExactTraversalShortCircuitSearch {
    data class Progress(
        val completedChecks: Int,
        val totalChecks: Int,
        val label: String
    ) {
        val percentComplete: Double
            get() = if (totalChecks <= 0) 100.0
            else completedChecks.toDouble() * 100.0 / totalChecks.toDouble()
    }

    private data class ComparisonCase(
        val label: String,
        val first: String,
        val second: String,
        val expectedEquivalent: Boolean
    )

    private val survivorSources = listOf(
        "M921359,-100000V0.001L-0.5,0.5L0.015625,-0.015625L-5603.68,0.00390625C0.0625,-3865.8,0.015625,-4.29581,5,0.43125Q-100000,5.44538,346409,-0.863562V-0.0625V1A100000,0.00390625,180,0,1,-0.25,100000V667.398",
        "M-99,-0.125L0.25,34916.6Q4236.92,0.932262,-100,-0.5A0.0625,0.03125,90,0,1,865822,-0.412029H-4678.91A100,0.0625,180,0,0,99,-10H0.5H0",
        "M-1,-0.5L-0.015625,-10C10,-10,-99,-99,0.001,0.5A999,916.159,0,1,0,5,0.125L0.25,-0.5C-90429.8,-767.512,2,-142.946,-299.795,-570334A0.125,0.015625,360,1,1,-99,0.03125V999V99V-1",
        "M0.0625,100L999,128456H-10H-3198.81Q63482.6,0.125,0.0625,100000Q-2,399.738,784.595,-0.001A100,0.5,360,0,0,0.125,1000000V0.015625",
        "M5168,-427752V-3.5468L-1000000,-5Q-205.28,-5,9.08038,0.00390625ZM-253.962,0L0.00390625,2Q-357.629,0.0625,4784.1,2V-999L0.0625,-100C-999,-1,268.2,-0.015625,-5,0.00390625H0.03125H1000000A0.316118,5,45,1,0,-10,-10V-1A1,4.34564,270,1,1,-1000000,0.0625"
    )

    private val comparisonCases = listOf(
        ComparisonCase("horizontal subdivision control", "M0,0H5H10", "M0,0H10", true),
        ComparisonCase("vertical subdivision control", "M3,-10V-2V8", "M3,-10V8", true),
        ComparisonCase("mixed L/H subdivision control", "M0,0L5,0H10", "M0,0L10,0", true),
        ComparisonCase("zero-length line control", "M0,0L5,0L5,0L10,0", "M0,0L5,0L10,0", true),
        ComparisonCase("identical cubic control", "M0,0C1,2,3,4,5,6", "M0,0C1,2,3,4,5,6", true),
        ComparisonCase("endpoint mutation", "M0,0L10,0", "M0,0L11,0", false),
        ComparisonCase("skipped non-collinear traversal", "M0,0L5,2L10,0", "M0,0L10,0", false),
        ComparisonCase("reversal/backtracking mutation", "M0,0L10,0L5,0L10,0", "M0,0L10,0", false),
        ComparisonCase("perpendicular offset mutation", "M0,0L10,0", "M0,0.5L10,0.5", false),
        ComparisonCase("closure-state mutation", "M0,0L10,0L0,0Z", "M0,0L10,0L0,0", false),
        ComparisonCase("equal-length bent mutation", "M0,0L5,1L10,0", "M0,0L5,-1L10,0", false),
        ComparisonCase("same-endpoint cubic mutation", "M0,0C2,0,8,0,10,0", "M0,0C2,4,8,4,10,0", false)
    )

    fun run(progressCallback: ((Progress) -> Unit)? = null): String {
        val started = System.nanoTime()
        val total = survivorSources.size + comparisonCases.size
        var completed = 0

        data class SurvivorResult(
            val index: Int,
            val exactSafe: Boolean,
            val equivalent: Boolean,
            val shortCircuited: Boolean,
            val reason: String,
            val pre: String,
            val post: String
        )

        val survivorResults = survivorSources.mapIndexed { index, source ->
            val ordered = SvgPathDataOptimizer.diagnoseOrderedCollinearTraversal(
                source,
                productionReplay = true
            )
            val diagnostic = SvgPathSampler.exactTraversalShortCircuitGeometryDiagnostic(
                ordered.preCollinear,
                ordered.postCollinear
            )
            completed++
            progressCallback?.invoke(Progress(completed, total, "G3.10 survivor ${index + 1}"))
            SurvivorResult(
                index = index + 1,
                exactSafe = ordered.safe,
                equivalent = diagnostic.equivalent,
                shortCircuited = diagnostic.reason.contains("G3.13 exact ordered-traversal short-circuit"),
                reason = diagnostic.reason,
                pre = ordered.preCollinear,
                post = ordered.postCollinear
            )
        }

        data class CaseResult(
            val label: String,
            val expectedEquivalent: Boolean,
            val actualEquivalent: Boolean,
            val shortCircuited: Boolean,
            val passed: Boolean,
            val reason: String
        )

        val caseResults = comparisonCases.map { case ->
            val diagnostic = SvgPathSampler.exactTraversalShortCircuitGeometryDiagnostic(
                case.first,
                case.second
            )
            completed++
            progressCallback?.invoke(Progress(completed, total, case.label))
            CaseResult(
                label = case.label,
                expectedEquivalent = case.expectedEquivalent,
                actualEquivalent = diagnostic.equivalent,
                shortCircuited = diagnostic.reason.contains("G3.13 exact ordered-traversal short-circuit"),
                passed = diagnostic.equivalent == case.expectedEquivalent,
                reason = diagnostic.reason
            )
        }

        val survivorsSafe = survivorResults.count { it.exactSafe }
        val survivorsAccepted = survivorResults.count { it.equivalent }
        val survivorShortCircuits = survivorResults.count { it.shortCircuited }
        val checksPassed = caseResults.count { it.passed }
        val totalShortCircuits = survivorShortCircuits + caseResults.count { it.shortCircuited }
        val fallbacks = total - totalShortCircuits
        val elapsed = System.nanoTime() - started

        val success = survivorsSafe == survivorResults.size &&
            survivorsAccepted == survivorResults.size &&
            checksPassed == caseResults.size

        return buildString {
            appendLine("G3.13 exact ordered-traversal short-circuit validation")
            appendLine()
            appendLine("G3.10 survivor replays: ${survivorResults.size}")
            appendLine("Survivors exact-safe under G3.11 oracle: $survivorsSafe")
            appendLine("Survivors accepted by G3.13 comparator: $survivorsAccepted")
            appendLine("Survivors accepted by exact short-circuit: $survivorShortCircuits")
            appendLine("Targeted control/mutation checks: ${caseResults.size}")
            appendLine("Control/mutation checks passed: $checksPassed")
            appendLine("Total exact short-circuits: $totalShortCircuits")
            appendLine("Fallback bidirectional comparisons: $fallbacks")
            appendLine("Elapsed: ${String.format(java.util.Locale.US, "%.2f", elapsed / 1_000_000.0)} ms")
            appendLine()
            if (success) {
                appendLine("RESULT: G3.13 validated the exact ordered-traversal short-circuit on all historical survivors and targeted controls/mutations.")
                appendLine("Recommendation: use exact ordered traversal as the diagnostic comparator's first equivalence path, retain G3.12 bidirectional comparison as the fallback, then rerun the G3.7 convergence corpus before any production convergence change.")
            } else {
                appendLine("RESULT: G3.13 short-circuit validation failed.")
                appendLine("Recommendation: keep production unchanged and inspect every failed survivor or targeted control before rerunning G3.7.")
            }

            appendLine()
            appendLine("────────────────────────────────")
            appendLine("G3.10 survivor replay results")
            survivorResults.forEach { result ->
                appendLine()
                appendLine("${result.index}. Exact ordered safe: ${result.exactSafe}")
                appendLine("   G3.13 equivalent: ${result.equivalent}")
                appendLine("   Exact short-circuit used: ${result.shortCircuited}")
                appendLine("   Comparator reason: ${result.reason}")
                if (!result.equivalent || !result.exactSafe) {
                    appendLine("   Before collinear consolidation: ${result.pre}")
                    appendLine("   After collinear consolidation: ${result.post}")
                }
            }

            appendLine()
            appendLine("────────────────────────────────")
            appendLine("Targeted comparator controls / mutations")
            caseResults.forEachIndexed { index, result ->
                appendLine()
                appendLine("${index + 1}. ${result.label}")
                appendLine("   Expected equivalent: ${result.expectedEquivalent}")
                appendLine("   Actual equivalent: ${result.actualEquivalent}")
                appendLine("   Exact short-circuit used: ${result.shortCircuited}")
                appendLine("   Passed: ${result.passed}")
                appendLine("   Reason: ${result.reason}")
            }
        }
    }
}
