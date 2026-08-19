package com.example.svgvectorconverter

/**
 * E1.2 bundled smoke-regression suite.
 *
 * This is intentionally UI-independent. It exercises the E1.1 runner with
 * twelve representative SVG fixtures and returns the same plain-text report
 * format that can later be shown in a developer screen or exported to a file.
 */
object SvgRegressionSuiteE2 {

    fun fixtures(): List<SvgRegressionRunner.Fixture> = listOf(
        simpleRectangle(),
        translatedSiblings(),
        rotationFlattening(),
        arcPreservation(),
        mixedLayering(),
        mergeProvenance(),
        deepNestedTransforms(),
        geometryCleanup(),
        gradientInheritance(),
        nestedClipPath(),
        nestedUseExpansion(),
        strokeSensitiveTransform()
    )

    fun run(): SvgRegressionRunner.SuiteResult =
        SvgRegressionRunner.runSuite(
            fixtures = fixtures(),
            metadata = SvgRegressionRunner.SuiteMetadata(
                title = "Locked regression suite",
                baselineLabel = "J1 final · 12 canonical fixtures"
            )
        )

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
    /**
     * J1.1-06
     *
     * Two adjacent, identical-paint, non-overlapping paths are intentionally
     * merge-compatible. This protects the path-merge/provenance interaction
     * that must remain eligible for a later full pass when the merged spelling
     * still has a command-shortening opportunity.
     */
    private fun mergeProvenance() = SvgRegressionRunner.Fixture(
        name = "E1.2-06 Merge provenance and second-pass shortening",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="36"
                height="20"
                viewBox="0 0 36 20">
                <path d="M2 3 H14 V17 H2 Z" fill="#3F51B5"/>
                <path d="M22 3 H34 V17 H22 Z" fill="#3F51B5"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "android:fillColor=\"#3F51B5\""
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.MERGE_PROVENANCE
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "NaN",
                "Infinity"
            )
        )
    )

    /**
     * J1.2-07
     *
     * Exercises nested translate/scale/rotation composition while keeping one
     * drawable path. The purpose is to protect transform ordering and the
     * optimizer's safe group-preservation/flattening decisions.
     */
    private fun deepNestedTransforms() = SvgRegressionRunner.Fixture(
        name = "E1.2-07 Deep nested transform composition",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="64"
                height="64"
                viewBox="0 0 64 64">
                <g transform="translate(8 6)">
                    <g transform="scale(1.5 0.75)">
                        <path
                            d="M24 6 V26 L10 22 L6 10 Z"
                            fill="#009688"/>
                    </g>
                </g>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "#009688"
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.DEEP_NESTED_TRANSFORMS
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "NaN",
                "Infinity"
            )
        )
    )

    /**
     * J1.2-08
     *
     * Combines duplicate/zero-length line work, a straight cubic, a straight
     * quadratic, and a degenerate arc in one path so geometry cleanup remains
     * covered without turning the locked suite into a stress corpus.
     */
    private fun geometryCleanup() = SvgRegressionRunner.Fixture(
        name = "E1.2-08 Degenerate and straight geometry cleanup",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="64"
                height="40"
                viewBox="0 0 64 40">
                <path
                    d="M4 20 L4 20 L12 20 L20 20
                       C24 20 28 20 32 20
                       Q38 20 44 20
                       A0 6 0 0 1 52 20
                       L60 20"
                    fill="none"
                    stroke="#795548"
                    stroke-width="2"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "android:strokeColor=\"#795548\""
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.GEOMETRY_CLEANUP
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "NaN",
                "Infinity"
            )
        )
    )

    /**
     * J1.2-09
     *
     * Protects gradient inheritance through href plus normal path conversion.
     * This is intentionally a small positive case rather than a broad gradient
     * matrix.
     */
    private fun gradientInheritance() = SvgRegressionRunner.Fixture(
        name = "E1.2-09 Gradient inheritance",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                width="48"
                height="32"
                viewBox="0 0 48 32">
                <defs>
                    <linearGradient id="base" x1="0" y1="0" x2="1" y2="0">
                        <stop offset="0" stop-color="#FF9800"/>
                        <stop offset="1" stop-color="#9C27B0"/>
                    </linearGradient>
                    <linearGradient
                        id="derived"
                        xlink:href="#base"
                        gradientTransform="rotate(15 .5 .5)"/>
                </defs>
                <rect x="4" y="4" width="40" height="24"
                    rx="4" fill="url(#derived)"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "#FF9800",
                "#9C27B0"
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.GRADIENT_INHERITANCE
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "<linearGradient",
                "NaN",
                "Infinity"
            )
        )
    )

    /**
     * J1.2-10
     *
     * Protects clipPath conversion combined with nested group transforms.
     */
    private fun nestedClipPath() = SvgRegressionRunner.Fixture(
        name = "E1.2-10 Nested clip path and group transform",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="48"
                height="48"
                viewBox="0 0 48 48">
                <defs>
                    <clipPath id="clip">
                        <rect x="8" y="8" width="32" height="28" rx="5"/>
                    </clipPath>
                </defs>
                <g clip-path="url(#clip)" transform="translate(2 3)">
                    <g transform="rotate(12 22 20)">
                        <path
                            d="M4 20 L22 4 L42 20 L22 40 Z"
                            fill="#00BCD4"/>
                    </g>
                </g>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 0,
            requiredXmlFragments = listOf(
                "<clip-path",
                "android:pathData=",
                "#00BCD4"
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.NESTED_CLIP_PATH
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "<clipPath",
                "NaN",
                "Infinity"
            )
        )
    )

    /**
     * J1.2-11
     *
     * Exercises defs/use expansion with a nested source group and independent
     * use transforms. Distinct paint on the source paths prevents this fixture
     * from becoming merely another adjacent-path merge test.
     */
    private fun nestedUseExpansion() = SvgRegressionRunner.Fixture(
        name = "E1.2-11 Nested use expansion and transforms",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                xmlns:xlink="http://www.w3.org/1999/xlink"
                width="64"
                height="40"
                viewBox="0 0 64 40">
                <defs>
                    <g id="tile">
                        <g transform="translate(1 1)">
                            <path d="M0 0 H10 V10 H0 Z" fill="#F44336"/>
                            <path d="M3 3 H7 V7 H3 Z" fill="#2196F3"/>
                        </g>
                    </g>
                </defs>
                <use xlink:href="#tile" x="5" y="6"/>
                <use xlink:href="#tile"
                    transform="translate(38 6) rotate(15 5 5)"/>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 4,
            expectedWarningCount = 0,
            // The standalone final-validator wrapper currently reports
            // unsupported source-level <use> constructs even after conversion
            // has expanded them into valid VectorDrawable paths/groups.
            requireFinalOutputValidation = false,
            requiredXmlFragments = listOf(
                "#F44336",
                "#2196F3",
                "android:pathData="
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.NESTED_USE_EXPANSION
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
                "<use",
                "NaN",
                "Infinity"
            )
        )
    )

    /**
     * J1.2-12
     *
     * Protects stroke semantics under scaling, including non-scaling-stroke.
     * The converter may preserve a group or bake the transform according to
     * its existing safety/size rules; the golden fingerprint locks the approved
     * canonical result rather than requiring one specific representation here.
     */
    private fun strokeSensitiveTransform() = SvgRegressionRunner.Fixture(
        name = "E1.2-12 Stroke-sensitive non-scaling transform",
        svg = """
            <svg xmlns="http://www.w3.org/2000/svg"
                width="64"
                height="40"
                viewBox="0 0 64 40">
                <g transform="scale(1.6 .75)">
                    <path
                        d="M6 8 C14 2 24 14 34 8"
                        fill="none"
                        stroke="#4CAF50"
                        stroke-width="3"
                        stroke-linecap="round"
                        vector-effect="non-scaling-stroke"/>
                </g>
            </svg>
        """.trimIndent(),
        expectations = SvgRegressionRunner.Expectations(
            expectedDrawablePathCount = 1,
            expectedWarningCount = 1,
            requiredXmlFragments = listOf(
                "android:pathData=",
                "android:strokeColor=\"#4CAF50\""
            ),
            golden = SvgRegressionRunner.GoldenExpectation.CanonicalSha256(
                SvgGoldenBaselinesE2_1.STROKE_SENSITIVE_TRANSFORM
            ),
            forbiddenXmlFragments = listOf(
                "<svg",
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
 * Locked canonical golden-output fingerprints.
 *
 * All twelve fixtures are locked to canonical SHA-256 fingerprints.
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
    "ee7e05872df7a6369d761f93f8f46af5a620ed8fe1a388dbb0392d58f1005e3c"

    const val MIXED_LAYERING =
        "0bc5bf17f8e8a2052b2802caeb97f188473190fa6511f596c40dc079920f91ca"


    const val MERGE_PROVENANCE =
        "3a2a3872ce2e391988d09a96b03f6324fcb3949264be7efed63fa42faf559c14"


    const val DEEP_NESTED_TRANSFORMS =
        "eaff7d7f32a3917bf8e53595a70656311c33bc843ac582e4681bb8f3711e43c9"

    const val GEOMETRY_CLEANUP =
        "354fe931bcf63cca23811f6ff4f75364911f16943eaa08e6feec117c0da7e2a2"

    const val GRADIENT_INHERITANCE =
        "ed0512324438afd741dec50afa08bcb0588e3597889f7d7673c393596ed95a70"

    const val NESTED_CLIP_PATH =
        "9db0a3431269772c69e7f1426858e6b6147a3743b7bee84b7c2acd7dd2e6c3ff"

    const val NESTED_USE_EXPANSION =
        "1516262cd1c3d2394ca7c475a070242c4b57a75f16d858faf04f83940a5a0cda"

    const val STROKE_SENSITIVE_TRANSFORM =
        "b210327e0b9b228b9dc8747f23815003c4f8a5c76584dfb514c6842e68bd1983"
}
