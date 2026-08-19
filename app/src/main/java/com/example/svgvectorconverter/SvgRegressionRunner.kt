package com.example.svgvectorconverter

import java.io.StringReader
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Core regression runner.
 *
 * UI-independent and intentionally lightweight so both the permanent locked
 * suite and the broader extended feature suite can share the same deterministic
 * expectation, golden-output, and plain-text reporting model.
 */
object SvgRegressionRunner {

    data class Fixture(
        val name: String,
        val svg: String,
        val expectations: Expectations = Expectations(),
        val outputDpSize: Int = 24,
        val conversionProfile: String = "Default"
    )

    sealed class GoldenExpectation {
        object Disabled : GoldenExpectation()

        /**
         * Records the current canonical XML and SHA-256 fingerprint in the
         * regression report so it can be reviewed and promoted to a baseline.
         */
        object CaptureCandidate : GoldenExpectation()

        /**
         * Compares canonicalized XML rather than raw formatting.
         */
        data class CanonicalXml(
            val xml: String
        ) : GoldenExpectation()

        /**
         * Compares the SHA-256 fingerprint of canonicalized XML.
         */
        data class CanonicalSha256(
            val sha256: String
        ) : GoldenExpectation()
    }

    data class Expectations(
        val expectedDrawablePathCount: Int? = null,
        val expectedWarningCount: Int? = null,
        val requireOptimizerIdempotence: Boolean = true,
        val requireFinalOutputValidation: Boolean = true,
        val requiredXmlFragments: List<String> = emptyList(),
        val forbiddenXmlFragments: List<String> = emptyList(),
        val requiredReportFragments: List<String> = emptyList(),
        val forbiddenReportFragments: List<String> = emptyList(),
        val golden: GoldenExpectation = GoldenExpectation.Disabled
    )

    enum class CheckStatus {
        PASSED,
        FAILED
    }

    data class CheckResult(
        val description: String,
        val status: CheckStatus,
        val expected: String? = null,
        val actual: String? = null,
        val details: String? = null
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

    data class SuiteMetadata(
        val title: String = "Regression suite",
        val baselineLabel: String? = null
    )

    data class GoldenCoverage(
        val locked: Int,
        val captureCandidates: Int,
        val disabled: Int
    ) {
        val total: Int
            get() = locked + captureCandidates + disabled
    }

    data class SuiteResult(
        val tests: List<TestResult>,
        val elapsedNanos: Long,
        val metadata: SuiteMetadata = SuiteMetadata(),
        val goldenCoverage: GoldenCoverage = GoldenCoverage(
            locked = 0,
            captureCandidates = 0,
            disabled = 0
        )
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
            appendLine(metadata.title)
            metadata.baselineLabel?.let {
                appendLine("Baseline: $it")
            }
            appendLine(
                "Golden coverage: ${goldenCoverage.locked} locked, " +
                    "${goldenCoverage.captureCandidates} capture, " +
                    "${goldenCoverage.disabled} disabled"
            )
            appendLine()
            appendLine("Tests run: ${tests.size}")
            appendLine("Passed: $passedCount")
            appendLine("Failed: $failedCount")
            appendLine("Elapsed: ${formatMilliseconds(elapsedMilliseconds)}")

            if (failedCount > 0) {
                appendLine()
                appendLine("────────────────────────────────")
                appendLine("Failure summary")
                appendLine("────────────────────────────────")
                tests.filterNot { it.passed }.forEach { test ->
                    appendLine("✕ ${test.fixtureName}")

                    val failedChecks = test.checks.filterNot { it.passed }
                    if (failedChecks.isEmpty()) {
                        test.error?.let { appendLine("  $it") }
                    } else {
                        failedChecks.forEach { check ->
                            appendLine("  • ${check.description}")
                            check.actual?.let { actual ->
                                if (
                                    check.description.contains(
                                        "Golden output",
                                        ignoreCase = true
                                    )
                                ) {
                                    appendLine("    Actual: $actual")
                                }
                            }
                        }
                    }
                }
            }

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
                    .filterNot {
                        test.passed &&
                            it.passed &&
                            it.details == null
                    }
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

                        check.details?.let { details ->
                            details.lineSequence().forEach { line ->
                                appendLine("    $line")
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

    fun runSuite(
        fixtures: List<Fixture>,
        metadata: SuiteMetadata = SuiteMetadata()
    ): SuiteResult {
        val start = System.nanoTime()
        val results = fixtures.map(::runFixture)

        var lockedGoldens = 0
        var captureCandidates = 0
        var disabledGoldens = 0

        fixtures.forEach { fixture ->
            when (fixture.expectations.golden) {
                GoldenExpectation.Disabled -> disabledGoldens++
                GoldenExpectation.CaptureCandidate -> captureCandidates++
                is GoldenExpectation.CanonicalXml,
                is GoldenExpectation.CanonicalSha256 -> lockedGoldens++
            }
        }

        return SuiteResult(
            tests = results,
            elapsedNanos = System.nanoTime() - start,
            metadata = metadata,
            goldenCoverage = GoldenCoverage(
                locked = lockedGoldens,
                captureCandidates = captureCandidates,
                disabled = disabledGoldens
            )
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

        val assertionXml = stripXmlComments(result.xml)

        expectations.requiredXmlFragments.forEach { fragment ->
            val found = assertionXml.contains(fragment)
            checks += CheckResult(
                description = "XML contains required fragment",
                status = found.toStatus(),
                expected = quote(fragment),
                actual = if (found) "Found" else "Not found"
            )
        }

        expectations.forbiddenXmlFragments.forEach { fragment ->
            val absent = !assertionXml.contains(fragment)
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

        checks += evaluateGoldenOutput(
            actualXml = result.xml,
            expectation = expectations.golden
        )

        return checks
    }

    private fun evaluateGoldenOutput(
        actualXml: String,
        expectation: GoldenExpectation
    ): List<CheckResult> {
        if (expectation is GoldenExpectation.Disabled) {
            return emptyList()
        }

        val canonicalActual = canonicalizeXml(actualXml)
            ?: return listOf(
                CheckResult(
                    description = "Golden output canonicalization",
                    status = CheckStatus.FAILED,
                    expected = "Canonicalizable VectorDrawable XML",
                    actual = "Could not canonicalize generated XML"
                )
            )

        val actualHash = sha256(canonicalActual)

        return when (expectation) {
            GoldenExpectation.Disabled -> emptyList()

            GoldenExpectation.CaptureCandidate -> listOf(
                CheckResult(
                    description = "Golden output candidate captured",
                    status = CheckStatus.PASSED,
                    actual = actualHash,
                    details = buildString {
                        appendLine("Canonical SHA-256: $actualHash")
                        appendLine("BEGIN GOLDEN XML")
                        canonicalActual.lineSequence().forEach(::appendLine)
                        append("END GOLDEN XML")
                    }
                )
            )

            is GoldenExpectation.CanonicalXml -> {
                val canonicalExpected = canonicalizeXml(expectation.xml)
                if (canonicalExpected == null) {
                    listOf(
                        CheckResult(
                            description = "Golden output comparison",
                            status = CheckStatus.FAILED,
                            expected = "Canonicalizable expected XML",
                            actual = "Expected golden XML could not be canonicalized"
                        )
                    )
                } else {
                    val passed = canonicalExpected == canonicalActual
                    listOf(
                        CheckResult(
                            description = "Golden output matches canonical XML",
                            status = passed.toStatus(),
                            expected = sha256(canonicalExpected),
                            actual = actualHash,
                            details = if (passed) {
                                null
                            } else {
                                compactXmlDiff(
                                    expected = canonicalExpected,
                                    actual = canonicalActual
                                )
                            }
                        )
                    )
                }
            }

            is GoldenExpectation.CanonicalSha256 -> {
                val expected = expectation.sha256
                    .trim()
                    .lowercase()
                val passed = expected == actualHash
                listOf(
                    CheckResult(
                        description = "Golden output fingerprint",
                        status = passed.toStatus(),
                        expected = expected,
                        actual = actualHash,
                        details = if (passed) {
                            null
                        } else {
                            buildString {
                                appendLine("Canonical XML changed.")
                                appendLine("Replacement candidate SHA-256: $actualHash")
                                appendLine("BEGIN ACTUAL CANONICAL XML")
                                canonicalActual.lineSequence().forEach(::appendLine)
                                append("END ACTUAL CANONICAL XML")
                            }
                        }
                    )
                )
            }
        }
    }

    /**
     * Produces a stable semantic representation:
     * - removes comments
     * - ignores indentation-only text nodes
     * - sorts attributes by namespace/name
     * - preserves child order and meaningful text
     */
    private fun canonicalizeXml(xml: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false

                runCatching {
                    setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl",
                        true
                    )
                }
                runCatching {
                    setFeature(
                        "http://xml.org/sax/features/external-general-entities",
                        false
                    )
                }
                runCatching {
                    setFeature(
                        "http://xml.org/sax/features/external-parameter-entities",
                        false
                    )
                }
            }

            val document = factory.newDocumentBuilder().parse(
                InputSource(StringReader(xml))
            )

            buildString {
                appendCanonicalNode(document.documentElement, this, 0)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun appendCanonicalNode(
        node: Node,
        output: StringBuilder,
        depth: Int
    ) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val name = element.tagName
                val indent = "  ".repeat(depth)

                output.append(indent)
                output.append('<')
                output.append(name)

                val attributes = (0 until element.attributes.length)
                    .map { index -> element.attributes.item(index) }
                    .sortedWith(
                        compareBy<Node>(
                            { it.namespaceURI.orEmpty() },
                            { it.nodeName }
                        )
                    )

                attributes.forEach { attribute ->
                    output.append(' ')
                    output.append(attribute.nodeName)
                    output.append("=\"")
                    output.append(escapeXml(attribute.nodeValue.orEmpty()))
                    output.append('"')
                }

                val meaningfulChildren = (0 until element.childNodes.length)
                    .map { index -> element.childNodes.item(index) }
                    .filter { child ->
                        when (child.nodeType) {
                            Node.COMMENT_NODE -> false
                            Node.TEXT_NODE,
                            Node.CDATA_SECTION_NODE ->
                                child.nodeValue?.trim()?.isNotEmpty() == true
                            else -> true
                        }
                    }

                if (meaningfulChildren.isEmpty()) {
                    output.append("/>")
                    return
                }

                val onlyText = meaningfulChildren.all {
                    it.nodeType == Node.TEXT_NODE ||
                        it.nodeType == Node.CDATA_SECTION_NODE
                }

                output.append('>')

                if (onlyText) {
                    meaningfulChildren.forEach { child ->
                        output.append(escapeXml(child.nodeValue.orEmpty().trim()))
                    }
                    output.append("</")
                    output.append(name)
                    output.append('>')
                } else {
                    output.append('\n')
                    meaningfulChildren.forEachIndexed { index, child ->
                        appendCanonicalNode(child, output, depth + 1)
                        if (index != meaningfulChildren.lastIndex) {
                            output.append('\n')
                        }
                    }
                    output.append('\n')
                    output.append(indent)
                    output.append("</")
                    output.append(name)
                    output.append('>')
                }
            }

            Node.TEXT_NODE,
            Node.CDATA_SECTION_NODE -> {
                val text = node.nodeValue?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    output.append("  ".repeat(depth))
                    output.append(escapeXml(text))
                }
            }
        }
    }

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun compactXmlDiff(
        expected: String,
        actual: String
    ): String {
        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val maxLines = maxOf(expectedLines.size, actualLines.size)
        val firstDifference = (0 until maxLines).firstOrNull { index ->
            expectedLines.getOrNull(index) != actualLines.getOrNull(index)
        } ?: return "Canonical XML differs."

        val start = maxOf(0, firstDifference - 2)
        val end = minOf(maxLines, firstDifference + 3)

        return buildString {
            appendLine("First difference near canonical line ${firstDifference + 1}:")
            for (index in start until end) {
                appendLine(
                    "E ${index + 1}: " +
                        (expectedLines.getOrNull(index) ?: "<missing>")
                )
                appendLine(
                    "A ${index + 1}: " +
                        (actualLines.getOrNull(index) ?: "<missing>")
                )
            }
        }.trimEnd()
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

    private fun stripXmlComments(xml: String): String =
        Regex(
            """<!--.*?-->""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).replace(xml, "")

    private fun quote(value: String): String =
        "\"${value.replace("\n", "\\n")}\""

    private fun formatMilliseconds(value: Double): String =
        String.format(java.util.Locale.US, "%.2f ms", value)
}
