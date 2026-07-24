package com.example.svgvectorconverter

/**
 * E1.2 bundled smoke-regression suite.
 *
 * This is intentionally UI-independent. It exercises the E1.1 runner with
 * five representative SVG fixtures and returns the same plain-text report
 * format that can later be shown in a developer screen or exported to a file.
 */
object SvgRegressionSuiteE2 {

    fun fixtures(): List<SvgRegressionRunner.Fixture> = listOf(
        simpleRectangle(),
        translatedSiblings(),
        rotationFlattening(),
        arcPreservation(),
        mixedLayering()
    )

    fun run(): SvgRegressionRunner.SuiteResult =
        SvgRegressionRunner.runSuite(fixtures())

    fun runAndFormat(): String =
        run().toPlainTextReport()

    private fun simpleRectangle() = SvgRegressionRunner.Fixture(
        name = "E1.2-01 Simple rectangle",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="24"
                height="24"
                viewBox="0 0 24 24">
                <rect x="3" y="4" width="18" height="16" fill="#2196F3"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "<vector",
                "android:pathData=",
                "android:fillColor=\"#2196F3\""
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.SIMPLE_RECTANGLE
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "<rect",
                "NaN",
                "Infinity"
            )
        )
    )

    private fun translatedSiblings() = SvgRegressionRunner.Fixture(
        name = "E1.2-02 Translated sibling paths",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="40"
                height="24"
                viewBox="0 0 40 24">
                <g transform="translate(4 3)">
                    <rect x="0" y="0" width="10" height="10" fill="#03A9F4"/>
                    <rect x="14" y="2" width="10" height="10" fill="#FF5722"/>
                </g>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 2,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "#03A9F4",
                "#FF5722"
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.TRANSLATED_SIBLINGS
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "<rect",
                "NaN",
                "Infinity"
            )
        )
    )

    private fun rotationFlattening() = SvgRegressionRunner.Fixture(
        name = "E1.2-03 Right-angle rotation",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="32"
                height="32"
                viewBox="0 0 32 32">
                <g transform="rotate(90 16 16)">
                    <path d="M8 10 L24 10 L16 24 Z" fill="#8BC34A"/>
                </g>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "android:fillColor=\"#8BC34A\""
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.RIGHT_ANGLE_ROTATION
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "NaN",
                "Infinity"
            )
        )
    )

    private fun arcPreservation() = SvgRegressionRunner.Fixture(
        name = "E1.2-04 Arc-heavy path",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="48"
                height="48"
                viewBox="0 0 48 48">
                <!-- converted from <path> is intentionally tag-like comment text -->
                <path
                    d="M24 5 A19 19 0 1 1 23.999 5 Z"
                    fill="#607D8B"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "android:fillColor=\"#607D8B\""
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.ARC_HEAVY_PATH
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "NaN",
                "Infinity"
            )
        )
    )

    private fun mixedLayering() = SvgRegressionRunner.Fixture(
        name = "E1.2-05 Mixed layering and transforms",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="64"
                height="64"
                viewBox="0 0 64 64">
                <rect x="10" y="10" width="44" height="44"
                    rx="10" fill="#90CAF9"/>
                <circle cx="22" cy="24" r="10" fill="#E91E63"/>
                <rect x="27" y="27" width="16" height="16"
                    fill="#FFC107"
                    transform="rotate(45 35 35)"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 3,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "#90CAF9",
                "#E91E63",
                "#FFC107",
                "android:pathData="
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.MIXED_LAYERING
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "<circle",
                "<rect",
                "NaN",
                "Infinity"
            )
        )
    )
}


/**
 * Compatibility wrapper so the existing Developer Tools UI continues to work.
 */
object SvgRegressionSuiteE1_2 {
    fun fixtures(): List<SvgRegressionRunner.Fixture> =
        SvgRegressionSuiteE2.fixtures()

    fun run(): SvgRegressionRunner.SuiteResult =
        SvgRegressionSuiteE2.run()

    fun runAndFormat(): String =
        SvgRegressionSuiteE2.runAndFormat()
}

/**
 * E2.1 locked canonical golden-output fingerprints.
 *
 * Update a fingerprint only after reviewing and approving the corresponding
 * canonical XML change produced by the regression runner.
 */
object SvgGoldenBaselinesE2_1 {
    const val SIMPLE_RECTANGLE =
        "238d0a6c5aa99ae4dbd76c94ed5330e38f825e908c61e917f48d846a584b2691"

    const val TRANSLATED_SIBLINGS =
        "7fc897278da200ffa35eb86afe362898579cdb3d8cb860b73809f06173ae1e76"

    const val RIGHT_ANGLE_ROTATION =
        "4a5a6f5b313e188a2d60253591381e5e22ffbee89998cc24c1eb6ca44bc0b604"

    const val ARC_HEAVY_PATH =
        "cda1d53f2bd9536c3229d638c601206013bc6ca1acd26585276b2d2aee6b5c64"

    const val MIXED_LAYERING =
        "0bc5bf17f8e8a2052b2802caeb97f188473190fa6511f596c40dc079920f91ca"
}
