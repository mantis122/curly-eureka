package com.example.svgvectorconverter

/**
 * E1.1 core regression runner.
 *
 * This runner is deliberately independent of the Android UI and external test
 * libraries. Fixtures are defined directly in Kotlin for now. A later phase
 * can load the same expectation model from JSON or bundled assets.
 */
object SvgRegressionRunner {

    data class Fixture(
        val name: String,
        val svg: String,
        val expectations: Expectations = Expectations(),
        val outputDpSize: Int = 24,
        val conversionProfile: String = "Default"
    )

    data class Expectations(
        val expectedDrawablePathCount: Int? = null,
        val expectedWarningCount: Int? = null,
        val requireOptimizerIdempotence: Boolean = true,
        val requireFinalOutputValidation: Boolean = true,
        val requiredXmlFragments: List<String> = emptyList(),
        val forbiddenXmlFragments: List<String> = emptyList(),
        val requiredReportFragments: List<String> = emptyList(),
        val forbiddenReportFragments: List<String> = emptyList()
    )

    enum class CheckStatus {
        PASSED,
        FAILED
    }

    data class CheckResult(
        val description: String,
        val status: CheckStatus,
        val expected: String? = null,
        val actual: String? = null
    ) {
        val passed: Boolean
            get() = status == CheckStatus.PASSED
    }

    data class TestResult(
        val fixtureName: String,
        val passed: Boolean,
        val checks: List<CheckResult>,
        val xml: String?,
        val report: String?,
        val error: String?,
        val elapsedNanos: Long
    ) {
        val elapsedMilliseconds: Double
            get() = elapsedNanos / 1_000_000.0
    }

    data class SuiteResult(
        val tests: List<TestResult>,
        val elapsedNanos: Long
    ) {
        val passedCount: Int
            get() = tests.count { it.passed }

        val failedCount: Int
            get() = tests.size - passedCount

        val passed: Boolean
            get() = failedCount == 0

        val elapsedMilliseconds: Double
            get() = elapsedNanos / 1_000_000.0

        fun toPlainTextReport(): String = buildString {
            appendLine("Regression suite")
            appendLine()
            appendLine("Tests run: ${tests.size}")
            appendLine("Passed: $passedCount")
            appendLine("Failed: $failedCount")
            appendLine("Elapsed: ${formatMilliseconds(elapsedMilliseconds)}")

            tests.forEach { test ->
                appendLine()
                appendLine(
                    if (test.passed) {
                        "✓ ${test.fixtureName}"
                    } else {
                        "✕ ${test.fixtureName}"
                    }
                )
                appendLine("  Time: ${formatMilliseconds(test.elapsedMilliseconds)}")

                test.error?.let {
                    appendLine("  • Conversion error: $it")
                }

                test.checks
                    .filterNot { test.passed && it.passed }
                    .forEach { check ->
                        val marker = if (check.passed) "✓" else "•"
                        appendLine("  $marker ${check.description}")

                        if (!check.passed) {
                            check.expected?.let {
                                appendLine("    Expected: $it")
                            }
                            check.actual?.let {
                                appendLine("    Actual: $it")
                            }
                        }
                    }
            }
        }
    }

    fun runFixture(fixture: Fixture): TestResult {
        val start = System.nanoTime()

        return try {
            val conversion = SvgToVectorConverter.convert(
                svg = fixture.svg,
                outputDpSize = fixture.outputDpSize,
                conversionProfile = fixture.conversionProfile
            )

            val checks = evaluate(
                result = conversion,
                expectations = fixture.expectations
            )

            TestResult(
                fixtureName = fixture.name,
                passed = checks.all { it.passed },
                checks = checks,
                xml = conversion.xml,
                report = conversion.report,
                error = null,
                elapsedNanos = System.nanoTime() - start
            )
        } catch (throwable: Throwable) {
            TestResult(
                fixtureName = fixture.name,
                passed = false,
                checks = listOf(
                    CheckResult(
                        description = "Conversion completes without an exception",
                        status = CheckStatus.FAILED,
                        expected = "Successful conversion",
                        actual = throwable.describeForRegressionReport()
                    )
                ),
                xml = null,
                report = null,
                error = throwable.describeForRegressionReport(),
                elapsedNanos = System.nanoTime() - start
            )
        }
    }

    fun runSuite(fixtures: List<Fixture>): SuiteResult {
        val start = System.nanoTime()
        val results = fixtures.map(::runFixture)

        return SuiteResult(
            tests = results,
            elapsedNanos = System.nanoTime() - start
        )
    }

    private fun evaluate(
        result: ConversionResult,
        expectations: Expectations
    ): List<CheckResult> {
        val checks = mutableListOf<CheckResult>()

        checks += CheckResult(
            description = "Conversion completes without an exception",
            status = CheckStatus.PASSED
        )

        expectations.expectedDrawablePathCount?.let { expected ->
            val actual = parseDrawablePathCount(result.report)
            checks += equalityCheck(
                description = "Drawable path count",
                expected = expected,
                actual = actual
            )
        }

        expectations.expectedWarningCount?.let { expected ->
            val actual = parseWarningCount(result.report)
            checks += equalityCheck(
                description = "Warning count",
                expected = expected,
                actual = actual
            )
        }

        if (expectations.requireOptimizerIdempotence) {
            val verified = result.report.contains(
                "✓ Optimizer idempotence verified"
            )

            checks += CheckResult(
                description = "Optimizer idempotence verified",
                status = verified.toStatus(),
                expected = "Verified",
                actual = if (verified) "Verified" else "Not verified"
            )
        }

        if (expectations.requireFinalOutputValidation) {
            val passed = result.report.contains(
                "✓ Final VectorDrawable validation passed"
            )

            checks += CheckResult(
                description = "Final VectorDrawable validation passed",
                status = passed.toStatus(),
                expected = "Passed",
                actual = if (passed) {
                    "Passed"
                } else {
                    finalValidationSummary(result.report)
                }
            )
        }

        expectations.requiredXmlFragments.forEach { fragment ->
            val found = result.xml.contains(fragment)
            checks += CheckResult(
                description = "XML contains required fragment",
                status = found.toStatus(),
                expected = quote(fragment),
                actual = if (found) "Found" else "Not found"
            )
        }

        expectations.forbiddenXmlFragments.forEach { fragment ->
            val absent = !result.xml.contains(fragment)
            checks += CheckResult(
                description = "XML omits forbidden fragment",
                status = absent.toStatus(),
                expected = "Absent: ${quote(fragment)}",
                actual = if (absent) "Absent" else "Found"
            )
        }

        expectations.requiredReportFragments.forEach { fragment ->
            val found = result.report.contains(fragment)
            checks += CheckResult(
                description = "Report contains required fragment",
                status = found.toStatus(),
                expected = quote(fragment),
                actual = if (found) "Found" else "Not found"
            )
        }

        expectations.forbiddenReportFragments.forEach { fragment ->
            val absent = !result.report.contains(fragment)
            checks += CheckResult(
                description = "Report omits forbidden fragment",
                status = absent.toStatus(),
                expected = "Absent: ${quote(fragment)}",
                actual = if (absent) "Absent" else "Found"
            )
        }

        return checks
    }

    private fun parseDrawablePathCount(report: String): Int? {
        return Regex(
            pattern = """(?m)^\s*(\d+)\s+drawable\s+paths?\s+created\s*$""",
            option = RegexOption.IGNORE_CASE
        ).find(report)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun parseWarningCount(report: String): Int? {
        if (Regex(
                pattern = """(?m)^\s*No warnings detected\s*$""",
                option = RegexOption.IGNORE_CASE
            ).containsMatchIn(report)
        ) {
            return 0
        }

        return Regex(
            pattern = """(?m)^\s*(\d+)\s+warning\(s\)\s+detected\s*$""",
            option = RegexOption.IGNORE_CASE
        ).find(report)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun finalValidationSummary(report: String): String {
        val issueLines = report
            .lineSequence()
            .dropWhile { it.trim() != "Final output validation" }
            .drop(1)
            .takeWhile { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() &&
                    trimmed != "Optimizer validation" &&
                    !trimmed.startsWith("Largest optimization savings")
            }
            .filter { it.trim().startsWith("⚠") }
            .map { it.trim() }
            .toList()

        return when {
            issueLines.isNotEmpty() -> issueLines.joinToString("; ")
            report.contains("Final output validation") ->
                "Validation section present, but no passing status was found"
            else ->
                "Final output validation section not found"
        }
    }

    private fun equalityCheck(
        description: String,
        expected: Int,
        actual: Int?
    ): CheckResult {
        val passed = actual == expected

        return CheckResult(
            description = description,
            status = passed.toStatus(),
            expected = expected.toString(),
            actual = actual?.toString() ?: "Could not parse from report"
        )
    }

    private fun Boolean.toStatus(): CheckStatus =
        if (this) CheckStatus.PASSED else CheckStatus.FAILED

    private fun Throwable.describeForRegressionReport(): String {
        val type = this::class.simpleName ?: "Throwable"
        val detail = message?.trim().orEmpty()

        return if (detail.isEmpty()) type else "$type: $detail"
    }

    private fun quote(value: String): String =
        "\"${value.replace("\n", "\\n")}\""

    private fun formatMilliseconds(value: Double): String =
        String.format(java.util.Locale.US, "%.2f ms", value)
}
