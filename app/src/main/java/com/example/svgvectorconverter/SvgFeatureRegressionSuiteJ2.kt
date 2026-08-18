package com.example.svgvectorconverter

/**
 * J2 extended feature regression suite.
 *
 * Separate from the permanent 12-test locked gate. J2.1 captures canonical
 * candidates for broader feature coverage before fingerprints are locked.
 */
object SvgFeatureRegressionSuiteJ2 {

    private val fixtures = listOf(
        SvgRegressionRunner.Fixture(
            name = "J2-01 Linear gradient",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <linearGradient id="g1" x1="0" y1="0" x2="1" y2="1">
                  <stop offset="0" stop-color="#00BCD4"/>
                  <stop offset="1" stop-color="#3F51B5"/>
                </linearGradient>
              </defs>
              <rect x="30" y="30" width="180" height="120" rx="18" fill="url(#g1)"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-02 Radial gradient",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <radialGradient id="g2">
                  <stop offset="0" stop-color="#FFF176"/>
                  <stop offset="1" stop-color="#F57F17"/>
                </radialGradient>
              </defs>
              <circle cx="120" cy="90" r="65" fill="url(#g2)"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-03 Gradient transform",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <linearGradient id="g3" gradientTransform="rotate(25)">
                  <stop offset="0" stop-color="#43A047"/>
                  <stop offset="1" stop-color="#1B5E20"/>
                </linearGradient>
              </defs>
              <path d="M35 145 L120 25 L205 145 Z" fill="url(#g3)"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-04 Gradient stroke",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <linearGradient id="g5">
                  <stop offset="0" stop-color="#FF5722"/>
                  <stop offset="1" stop-color="#9C27B0"/>
                </linearGradient>
              </defs>
              <path d="M35 125 C70 20 170 20 205 125" fill="none" stroke="url(#g5)" stroke-width="12" stroke-linecap="round"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-05 Complex clip path",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><clipPath id="c3"><path d="M120 20 L210 90 L120 160 L30 90 Z"/></clipPath></defs>
              <g clip-path="url(#c3)">
                <rect x="20" y="20" width="200" height="140" fill="#00838F"/>
                <circle cx="120" cy="90" r="75" fill="#B2EBF2"/>
              </g>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 2,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-06 Nested clipped group",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><clipPath id="c4"><circle cx="120" cy="90" r="65"/></clipPath></defs>
              <g clip-path="url(#c4)">
                <g transform="rotate(18 120 90)" fill="#7E57C2">
                  <rect x="30" y="50" width="180" height="80"/>
                </g>
              </g>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-07 Clipped stroke",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><clipPath id="c5"><rect x="40" y="35" width="160" height="110"/></clipPath></defs>
              <g clip-path="url(#c5)">
                <circle cx="120" cy="90" r="75" fill="none" stroke="#43A047" stroke-width="20"/>
              </g>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-08 Alpha mask",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <mask id="m1">
                  <rect width="240" height="180" fill="white"/>
                  <circle cx="120" cy="90" r="38" fill="black"/>
                </mask>
              </defs>
              <rect x="25" y="25" width="190" height="130" fill="#C62828" mask="url(#m1)"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-09 Gradient mask",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <linearGradient id="fade"><stop offset="0" stop-color="white"/><stop offset="1" stop-color="black"/></linearGradient>
                <mask id="m2"><rect width="240" height="180" fill="url(#fade)"/></mask>
              </defs>
              <circle cx="120" cy="90" r="70" fill="#3949AB" mask="url(#m2)"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-10 Basic use expansion",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><path id="tile" d="M0 0 H40 V40 H0 Z" fill="#455A64"/></defs>
              <use href="#tile" x="30" y="30"/>
              <use href="#tile" x="100" y="30"/>
              <use href="#tile" x="170" y="30"/>
              <use href="#tile" x="65" y="100"/>
              <use href="#tile" x="135" y="100"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 2,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-11 Use style override",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><circle id="dot" cx="0" cy="0" r="22"/></defs>
              <use href="#dot" x="55" y="90" fill="#F44336"/>
              <use href="#dot" x="120" y="90" fill="#4CAF50"/>
              <use href="#dot" x="185" y="90" fill="#2196F3"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 3,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-12 Symbol expansion",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs>
                <symbol id="s1" viewBox="0 0 40 40"><path d="M20 0 L40 40 H0 Z" fill="#8E24AA"/></symbol>
              </defs>
              <use href="#s1" x="35" y="35" width="60" height="60"/>
              <use href="#s1" x="145" y="85" width="60" height="60"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-13 Use transform",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><path id="bar" d="M0 -8 H80 V8 H0 Z" fill="#0097A7"/></defs>
              <use href="#bar" transform="translate(80 50) rotate(20)"/>
              <use href="#bar" transform="translate(80 120) rotate(-20)"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 2,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-14 Use opacity",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <defs><rect id="panel" x="-28" y="-22" width="56" height="44" rx="8"/></defs>
              <use href="#panel" x="65" y="90" fill="#3949AB" opacity="0.35"/>
              <use href="#panel" x="120" y="90" fill="#3949AB" opacity="0.65"/>
              <use href="#panel" x="175" y="90" fill="#3949AB"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 3,
                // Known limitation of the standalone final-validator wrapper
                // for source-level mask/use constructs.
                requireFinalOutputValidation = false,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-15 Stroke dash",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <path d="M25 90 H215" fill="none" stroke="#5E35B1" stroke-width="10" stroke-dasharray="18 9"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-16 Stroke dash offset",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <circle cx="120" cy="90" r="58" fill="none" stroke="#00838F" stroke-width="10" stroke-dasharray="24 10" stroke-dashoffset="7"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-17 Stroke line caps",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <path d="M40 45 H200" fill="none" stroke="#E53935" stroke-width="12" stroke-linecap="butt"/>
              <path d="M40 90 H200" fill="none" stroke="#43A047" stroke-width="12" stroke-linecap="round"/>
              <path d="M40 135 H200" fill="none" stroke="#1E88E5" stroke-width="12" stroke-linecap="square"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 3,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-18 Stroke line joins",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <path d="M45 130 L90 45 L135 130" fill="none" stroke="#F4511E" stroke-width="12" stroke-linejoin="miter"/>
              <path d="M125 130 L165 55 L205 130" fill="none" stroke="#3949AB" stroke-width="12" stroke-linejoin="round"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 2,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-19 CSS class exporter",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <style>
                .a { fill:#ef5350; stroke:#b71c1c; stroke-width:4; }
                .b { fill:#42a5f5; opacity:.8; }
              </style>
              <rect class="a" x="30" y="40" width="80" height="95" rx="10"/>
              <circle class="b" cx="165" cy="88" r="46"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 2,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        ),

        SvgRegressionRunner.Fixture(
            name = "J2-20 Scientific-number exporter",
            svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="240" height="180" viewBox="0 0 240 180" >
            
              <path d="M1e1 9e1 L6e1 2.5e1 L1.2e2 9e1 L1.8e2 2.5e1 L2.3e2 9e1" fill="none" stroke="#F57C00" stroke-width="6"/>
            
            </svg>
            """.trimIndent(),
            expectations = SvgRegressionRunner.Expectations(
                expectedDrawablePathCount = 1,
                requiredXmlFragments = listOf("android:pathData="),
                forbiddenXmlFragments = listOf("<svg", "NaN", "Infinity"),
                golden = SvgRegressionRunner.GoldenExpectation.CaptureCandidate
            )
        )
    )

    fun run(): SvgRegressionRunner.SuiteResult =
        SvgRegressionRunner.runSuite(fixtures)
}
