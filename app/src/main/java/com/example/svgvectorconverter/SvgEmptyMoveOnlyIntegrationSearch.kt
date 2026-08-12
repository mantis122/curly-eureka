package com.example.svgvectorconverter

/**
 * G3.18 — targeted production-optimizer integration check for move-only paths.
 *
 * Diagnostic-only. Exercises the real VectorDrawable optimizer entry point
 * (optimizeVectorXml), including its normal idempotence and final-validation
 * path, and verifies that paths optimized to blank/move-only data are pruned
 * instead of emitted as android:pathData="".
 */
internal object SvgEmptyMoveOnlyIntegrationSearch {

    private data class Case(
        val name: String,
        val xml: String,
        val expectAtLeastOnePath: Boolean,
        val expectMoveOnlyRemoved: Boolean,
        val note: String
    )

    private data class CaseResult(
        val case: Case,
        val output: String,
        val inputPathCount: Int,
        val outputPathCount: Int,
        val emptyPathDataCount: Int,
        val moveOnlyPathDataCount: Int,
        val finalValidationPassed: Boolean,
        val emptyRemoved: Int,
        val moveOnlyRemoved: Int,
        val emptyGroupsRemoved: Int,
        val passed: Boolean,
        val failureReason: String
    )

    private val pathElementRegex = Regex(
        """<path\b(?:\"[^\"]*\"|'[^']*'|[^>])*(?:/\s*>|>[\s\S]*?</path\s*>)""",
        RegexOption.IGNORE_CASE
    )
    private val pathDataRegex = Regex(
        """android:pathData\s*=\s*([\"'])(.*?)\1""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val commandRegex = Regex("""[AaCcHhLlMmQqSsTtVvZz]""")

    fun run(): String {
        val started = System.nanoTime()
        val cases = buildCases()
        val results = cases.map(::runCase)

        val passed = results.count { it.passed }
        val failed = results.size - passed
        val emptyOutputs = results.sumOf { it.emptyPathDataCount }
        val moveOnlyOutputs = results.sumOf { it.moveOnlyPathDataCount }
        val validationFailures = results.count { !it.finalValidationPassed }
        val historical = results.filter { it.case.name.startsWith("Historical") }
        val historicalPassed = historical.count { it.passed }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0

        return buildString {
            appendLine("G3.18 move-only / empty-path production integration check")
            appendLine()
            appendLine("Cases run: ${results.size}")
            appendLine("Historical G3.17 witnesses replayed: ${historical.size}")
            appendLine("Historical witnesses passed: $historicalPassed / ${historical.size}")
            appendLine("Passed: $passed")
            appendLine("Failed: $failed")
            appendLine("Output empty pathData attributes: $emptyOutputs")
            appendLine("Output move-only pathData values: $moveOnlyOutputs")
            appendLine("Final validation failures: $validationFailures")
            appendLine("Elapsed: ${"%.2f".format(java.util.Locale.US, elapsedMs)} ms")
            appendLine()

            when {
                failed == 0 && emptyOutputs == 0 && moveOnlyOutputs == 0 && validationFailures == 0 -> {
                    appendLine("RESULT: G3.18 confirmed that production optimization prunes move-only/empty paths without emitting invalid empty pathData.")
                    appendLine("Recommendation: classify the 14 G3.16/G3.17 failures as diagnostic-wrapper artifacts and proceed to guarded G3.15 production enablement.")
                }
                else -> {
                    appendLine("RESULT: G3.18 found a production integration failure involving move-only/empty paths.")
                    appendLine("Recommendation: keep G3.15 shadow-only and fix the failing production-path case before enablement.")
                }
            }

            appendLine()
            appendLine("────────────────────────────────")
            appendLine("Case results")
            results.forEachIndexed { index, result ->
                appendLine()
                appendLine("${index + 1}. ${result.case.name}")
                appendLine("   Passed: ${result.passed}")
                appendLine("   Note: ${result.case.note}")
                appendLine("   Input paths: ${result.inputPathCount}")
                appendLine("   Output paths: ${result.outputPathCount}")
                appendLine("   Empty paths removed counter: ${result.emptyRemoved}")
                appendLine("   Move-only paths removed counter: ${result.moveOnlyRemoved}")
                appendLine("   Empty groups removed counter: ${result.emptyGroupsRemoved}")
                appendLine("   Output empty pathData count: ${result.emptyPathDataCount}")
                appendLine("   Output move-only pathData count: ${result.moveOnlyPathDataCount}")
                appendLine("   Final VectorDrawable validation: ${if (result.finalValidationPassed) "passed" else "FAILED"}")
                if (!result.passed) {
                    appendLine("   Failure: ${result.failureReason}")
                }
                appendLine("   Output XML: ${result.output}")
            }
        }
    }

    private fun runCase(case: Case): CaseResult {
        return try {
            val result = SvgPathDataOptimizer.optimizeVectorXml(case.xml)
            val output = result.xml
            val inputPaths = pathElementRegex.findAll(case.xml).count()
            val outputElements = pathElementRegex.findAll(output).map { it.value }.toList()
            val outputPathData = outputElements.mapNotNull { element ->
                pathDataRegex.find(element)?.groupValues?.getOrNull(2)
            }
            val emptyCount = outputPathData.count { it.isBlank() }
            val moveOnlyCount = outputPathData.count { !it.isBlank() && !hasDrawableGeometry(it) }
            val pathExpectationPassed = if (case.expectAtLeastOnePath) {
                outputElements.isNotEmpty()
            } else {
                outputElements.isEmpty()
            }
            val removalCounterPassed = if (case.expectMoveOnlyRemoved) {
                result.stats.moveOnlyPathsRemoved > 0 || result.stats.emptyPathDataRemoved > 0
            } else true

            val passed =
                emptyCount == 0 &&
                    moveOnlyCount == 0 &&
                    result.stats.finalOutputValidationPassed &&
                    pathExpectationPassed &&
                    removalCounterPassed

            val failure = buildList {
                if (emptyCount != 0) add("empty android:pathData survived production pruning")
                if (moveOnlyCount != 0) add("move-only path survived production pruning")
                if (!result.stats.finalOutputValidationPassed) add("final VectorDrawable validation failed")
                if (!pathExpectationPassed) {
                    add(if (case.expectAtLeastOnePath) "expected drawable path was removed" else "expected all paths to be removed")
                }
                if (!removalCounterPassed) add("move-only/empty removal counter did not record pruning")
            }.joinToString("; ")

            CaseResult(
                case = case,
                output = output.replace(Regex("\\s+"), " ").trim(),
                inputPathCount = inputPaths,
                outputPathCount = outputElements.size,
                emptyPathDataCount = emptyCount,
                moveOnlyPathDataCount = moveOnlyCount,
                finalValidationPassed = result.stats.finalOutputValidationPassed,
                emptyRemoved = result.stats.emptyPathDataRemoved,
                moveOnlyRemoved = result.stats.moveOnlyPathsRemoved,
                emptyGroupsRemoved = result.stats.emptyGroupsRemoved,
                passed = passed,
                failureReason = failure
            )
        } catch (t: Throwable) {
            CaseResult(
                case = case,
                output = "",
                inputPathCount = pathElementRegex.findAll(case.xml).count(),
                outputPathCount = 0,
                emptyPathDataCount = 0,
                moveOnlyPathDataCount = 0,
                finalValidationPassed = false,
                emptyRemoved = 0,
                moveOnlyRemoved = 0,
                emptyGroupsRemoved = 0,
                passed = false,
                failureReason = "exception: ${t.message ?: t::class.java.simpleName}"
            )
        }
    }

    private fun hasDrawableGeometry(pathData: String): Boolean {
        return commandRegex.findAll(pathData).any { match ->
            when (match.value[0].uppercaseChar()) {
                'L', 'H', 'V', 'C', 'S', 'Q', 'T', 'A', 'Z' -> true
                else -> false
            }
        }
    }

    private fun vector(vararg body: String): String = buildString {
        append("<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" ")
        append("android:width=\"24dp\" android:height=\"24dp\" ")
        append("android:viewportWidth=\"1000\" android:viewportHeight=\"1000\">")
        body.forEach(::append)
        append("</vector>")
    }

    private fun path(data: String, extra: String = ""): String =
        "<path android:pathData=\"$data\" android:fillColor=\"#FF336699\" $extra/>"

    private fun buildCases(): List<Case> {
        val historical = listOf(
            "M999,1000M-0.00390625,100M100000,1",
            "M0.0625,-999M0.125,-100000M0.0625,-1000",
            "M-543.297,999M-999,919976M-10,4364.1",
            "M-1000000,-744.393M-0.25,-5M-5,-0.001",
            "M-1000,-1533.66M-0.001,0.25M-1,2",
            "M-99,0.015625M0.00390625,0.001M-2,-995.019",
            "M-0.001,-5M-0.015625,0.5M155.65,1000",
            "M6949.53,0.00390625M-132895,-1000M10,-10",
            "M2,573.666M-10,0.03125M-0.817202,3.86133M0.25,58.7641",
            "M0.001,99M-99,0.00390625M0.125,-10",
            "M-0.312345,0.125M-5,-0.0625M-2,0.0625",
            "M157.11,2M0.00390625,99M0.00390625,0.001",
            "M-1000000,1M-0.03125,-1000M0,0.0625",
            "M0.959139,-16.8643M-0.5,-0.0625M0.03125,10"
        ).mapIndexed { index, data ->
            Case(
                name = "Historical G3.17 witness ${index + 1}",
                xml = vector(path(data)),
                expectAtLeastOnePath = false,
                expectMoveOnlyRemoved = true,
                note = "Move-only path should disappear completely."
            )
        }

        val controls = listOf(
            Case(
                "Single move-only path",
                vector(path("M1,1")),
                false,
                true,
                "Simplest move-only path."
            ),
            Case(
                "Multiple move commands",
                vector(path("M1,1M2,2M3,3")),
                false,
                true,
                "Multiple subpath moves with no drawing."
            ),
            Case(
                "Move-only stroked path",
                vector(path("M4,4M5,5", "android:strokeColor=\"#FFFF0000\" android:strokeWidth=\"3\"")),
                false,
                true,
                "Paint attributes must not make a move-only path drawable."
            ),
            Case(
                "Move-only path inside group",
                vector("<group android:translateX=\"10\">${path("M1,1M2,2")}</group>"),
                false,
                true,
                "Path should be removed and the now-empty group pruned."
            ),
            Case(
                "Drawable sibling before move-only path",
                vector(path("M0,0L10,10"), path("M1,1M2,2")),
                true,
                true,
                "Drawable sibling must remain while move-only sibling disappears."
            ),
            Case(
                "Move-only sibling before drawable path",
                vector(path("M1,1M2,2"), path("M0,0L10,10")),
                true,
                true,
                "Ordering around a removed move-only sibling must remain safe."
            ),
            Case(
                "Drawable and move-only paths in group",
                vector("<group android:scaleX=\"2\" android:scaleY=\"2\">${path("M1,1M2,2")}${path("M0,0H10")}</group>"),
                true,
                true,
                "Only the drawable child should survive."
            ),
            Case(
                "Zero-length line command remains valid",
                vector(path("M1,1L1,1")),
                true,
                false,
                "A zero-length L/H/V command is not a move-only path; production may retain it, but output must remain valid and non-empty."
            )
        )

        return historical + controls
    }
}
