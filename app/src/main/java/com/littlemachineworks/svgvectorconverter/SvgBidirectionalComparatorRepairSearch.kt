package com.littlemachineworks.svgvectorconverter

/**
 * G3.12 diagnostic-only validation for the repaired bidirectional geometry
 * comparator. Production conversion/optimization behavior is not changed.
 */
internal object SvgBidirectionalComparatorRepairSearch {
    data class Progress(
        val completedChecks: Int,
        val totalChecks: Int,
        val label: String
    ) {
        val percentComplete: Double
            get() = if (totalChecks <= 0) 100.0
            else completedChecks.toDouble() * 100.0 / totalChecks.toDouble()
    }

    private data class MutationCase(
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

    private val mutationCases = listOf(
        MutationCase(
            "horizontal subdivision control",
            "M0,0H5H10",
            "M0,0H10",
            true
        ),
        MutationCase(
            "vertical subdivision control",
            "M3,-10V-2V8",
            "M3,-10V8",
            true
        ),
        MutationCase(
            "zero-length line control",
            "M0,0L5,0L5,0L10,0",
            "M0,0L5,0L10,0",
            true
        ),
        MutationCase(
            "endpoint mutation",
            "M0,0L10,0",
            "M0,0L11,0",
            false
        ),
        MutationCase(
            "skipped non-collinear traversal",
            "M0,0L5,2L10,0",
            "M0,0L10,0",
            false
        ),
        MutationCase(
            "reversal/backtracking mutation",
            "M0,0L10,0L5,0L10,0",
            "M0,0L10,0",
            false
        ),
        MutationCase(
            "perpendicular offset mutation",
            "M0,0L10,0",
            "M0,0.5L10,0.5",
            false
        ),
        MutationCase(
            "closure-state mutation",
            "M0,0L10,0L0,0Z",
            "M0,0L10,0L0,0",
            false
        )
    )

    fun run(progressCallback: ((Progress) -> Unit)? = null): String {
        val started = System.nanoTime()
        val total = survivorSources.size + mutationCases.size
        var completed = 0

        data class SurvivorResult(
            val index: Int,
            val orderedSafe: Boolean,
            val repairedEquivalent: Boolean,
            val reason: String,
            val pre: String,
            val post: String
        )

        val survivorResults = survivorSources.mapIndexed { index, source ->
            val ordered = SvgPathDataOptimizer.diagnoseOrderedCollinearTraversal(
                source,
                productionReplay = true
            )
            val repaired = SvgPathSampler.bidirectionalPolylineGeometryDiagnostic(
                ordered.preCollinear,
                ordered.postCollinear
            )
            completed++
            progressCallback?.invoke(Progress(completed, total, "G3.10 survivor ${index + 1}"))
            SurvivorResult(
                index = index + 1,
                orderedSafe = ordered.safe,
                repairedEquivalent = repaired.equivalent,
                reason = repaired.reason,
                pre = ordered.preCollinear,
                post = ordered.postCollinear
            )
        }

        data class MutationResult(
            val label: String,
            val expectedEquivalent: Boolean,
            val actualEquivalent: Boolean,
            val passed: Boolean,
            val reason: String
        )

        val mutationResults = mutationCases.map { case ->
            val diagnostic = SvgPathSampler.bidirectionalPolylineGeometryDiagnostic(
                case.first,
                case.second
            )
            completed++
            progressCallback?.invoke(Progress(completed, total, case.label))
            MutationResult(
                label = case.label,
                expectedEquivalent = case.expectedEquivalent,
                actualEquivalent = diagnostic.equivalent,
                passed = diagnostic.equivalent == case.expectedEquivalent,
                reason = diagnostic.reason
            )
        }

        val survivorsSafe = survivorResults.count { it.orderedSafe }
        val survivorsAccepted = survivorResults.count { it.repairedEquivalent }
        val mutationPassed = mutationResults.count { it.passed }
        val elapsed = System.nanoTime() - started

        val success = survivorsSafe == survivorResults.size &&
            survivorsAccepted == survivorResults.size &&
            mutationPassed == mutationResults.size

        return buildString {
            appendLine("G3.12 repaired bidirectional comparator validation")
            appendLine()
            appendLine("G3.10 survivor replays: ${survivorResults.size}")
            appendLine("Survivors exact-safe under G3.11 oracle: $survivorsSafe")
            appendLine("Survivors accepted by repaired comparator: $survivorsAccepted")
            appendLine("Targeted mutation/control checks: ${mutationResults.size}")
            appendLine("Mutation/control checks passed: $mutationPassed")
            appendLine("Elapsed: ${String.format(java.util.Locale.US, "%.2f", elapsed / 1_000_000.0)} ms")
            appendLine()
            if (success) {
                appendLine("RESULT: G3.12 repaired the G3.10 endpoint/length bookkeeping artifacts without weakening targeted mutation detection.")
                appendLine("Recommendation: adopt the repaired comparator for diagnostics and rerun the G3.7 convergence corpus before any production convergence change.")
            } else {
                appendLine("RESULT: G3.12 comparator validation failed.")
                appendLine("Recommendation: keep production unchanged and inspect the failed survivor or mutation checks before rerunning G3.7.")
            }

            appendLine()
            appendLine("────────────────────────────────")
            appendLine("G3.10 survivor replay results")
            survivorResults.forEach { result ->
                appendLine()
                appendLine("${result.index}. Exact ordered safe: ${result.orderedSafe}")
                appendLine("   Repaired comparator equivalent: ${result.repairedEquivalent}")
                appendLine("   Comparator reason: ${result.reason}")
                if (!result.repairedEquivalent || !result.orderedSafe) {
                    appendLine("   Before collinear consolidation: ${result.pre}")
                    appendLine("   After collinear consolidation: ${result.post}")
                }
            }

            appendLine()
            appendLine("────────────────────────────────")
            appendLine("Targeted comparator controls / mutations")
            mutationResults.forEachIndexed { index, result ->
                appendLine()
                appendLine("${index + 1}. ${result.label}")
                appendLine("   Expected equivalent: ${result.expectedEquivalent}")
                appendLine("   Actual equivalent: ${result.actualEquivalent}")
                appendLine("   Passed: ${result.passed}")
                appendLine("   Reason: ${result.reason}")
            }
        }
    }
}
