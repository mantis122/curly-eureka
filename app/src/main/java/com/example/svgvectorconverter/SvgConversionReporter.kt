package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

data class BasicShapeBreakdown(
    val rectangles: Int = 0,
    val roundedRectangles: Int = 0,
    val circles: Int = 0,
    val ellipses: Int = 0,
    val polygons: Int = 0,
    val polylines: Int = 0
)

data class SvgImageStats(
    val imageElementCount: Int = 0,
    val embeddedRasterImageCount: Int = 0,
    val embeddedSvgImageCount: Int = 0,
    val externalImageCount: Int = 0,
    val missingHrefImageCount: Int = 0,
    val imageElementsWithSize: Int = 0
)

data class SvgPatternApproximationStats(
    val patternDefinitionCount: Int = 0,
    val approximatedPatternCount: Int = 0,
    val complexPatternApproximationCount: Int = 0,
    val sparsePatternApproximationCount: Int = 0
)

data class SvgConversionReportData(
    val convertedPathCount: Int,
    val convertedOriginalPathCount: Int,
    val convertedBasicShapeCount: Int,
    val basicShapeBreakdown: BasicShapeBreakdown,
    val definitionDrawableElementCount: Int,
    val visibleDrawableElementCount: Int,
    val drawableValidPathCount: Int,
    val emptyPathCount: Int,
    val generatedGroupCount: Int,
    val useCount: Int,
    val resolvedUseExpansions: Int,
    val unresolvedUseReferences: Int,
    val symbolCount: Int,
    val gradientFallbackColorCount: Int,
    val patternApproximationCount: Int,
    val patternApproximationStats: SvgPatternApproximationStats,
    val patternTileExpansionCount: Int = 0,
    val patternTilePathCount: Int = 0,
    val markerDefinitionCount: Int,
    val appliedMarkers: Int,
    val clipPathCount: Int,
    val clipPathReferenceCount: Int,
    val appliedClipPaths: Int,
    val maskPathCount: Int,
    val maskReferenceCount: Int,
    val appliedMasks: Int,
    val dashedStrokesDetected: Int,
    val dashedStrokesApproximated: Int,
    val invalidDashArrays: Int = 0,
    val dashSolidFallbacks: Int = 0,
    val oddDashListsDuplicated: Int = 0,
    val invalidDashOffsetFallbacks: Int = 0,
    val dashOffsetsNormalized: Int = 0,
    val dashTransformExactCompensations: Int = 0,
    val dashTransformApproximateCompensations: Int = 0,
    val nonScalingStrokesDetected: Int,
    val nonScalingStrokesCompensated: Int,
    val nonScalingStrokesUncertain: Int,
    val displayNoneElementsSkipped: Int = 0,
    val visibilityHiddenElementsSkipped: Int = 0,
    val nestedSvgViewportCount: Int = 0,
    val nestedSvgViewportClipCount: Int = 0,
    val nestedSvgPercentageViewportCount: Int = 0,
    val nestedSvgOverflowHiddenCount: Int = 0,
    val nestedSvgOverflowVisibleCount: Int = 0,
    val nestedSvgOverflowAutoCount: Int = 0,
    val nestedSvgOverflowScrollCount: Int = 0,
    val nestedSvgOverflowUnsupportedCount: Int = 0,
    val filterDefinitionCount: Int,
    val filterReferenceCount: Int,
    val textElementCount: Int,
    val tspanElementCount: Int,
    val textPathElementCount: Int,
    val textElementsApproximated: Int,
    val textElementsConvertedToPaths: Int = 0,
    val textGlyphPathsEmitted: Int = 0,
    val textGlyphSpecificAdvances: Int = 0,
    val textDefaultFontAdvances: Int = 0,
    val textMissingGlyphFallbacks: Int = 0,
    val textGlyphNameLookups: Int = 0,
    val textHorizontalKerningPairs: Int = 0,
    val textVerticalKerningPairs: Int = 0,
    val textHorizontalKerningPairsMatched: Int = 0,
    val textVerticalKerningPairsMatched: Int = 0,
    val textKerningAdjustmentsApplied: Int = 0,
    val textLengthSpacingAdjustments: Int = 0,
    val textLengthSpacingAndGlyphsAdjustments: Int = 0,
    val textGlyphRotationsApplied: Int = 0,
    val textLetterSpacingAdjustmentsApplied: Int = 0,
    val textWordSpacingAdjustmentsApplied: Int = 0,
    val textDecorationPathsEmitted: Int = 0,
    val textBidiRunsReordered: Int = 0,
    val textDirections: List<String> = emptyList(),
    val textUnicodeBidiModes: List<String> = emptyList(),
    val textPathsConverted: Int = 0,
    val textPathGlyphsEmitted: Int = 0,
    val textFontFamilies: List<String> = emptyList(),
    val textFontWeights: List<String> = emptyList(),
    val verticalWritingTextCount: Int = 0,
    val writingModes: List<String> = emptyList(),
    val textAnchors: List<String> = emptyList(),
    val dominantBaselines: List<String> = emptyList(),
    val alignmentBaselines: List<String> = emptyList(),
    val baselineShifts: List<String> = emptyList(),
    val lengthAdjustModes: List<String> = emptyList(),
    val textPathMethods: List<String> = emptyList(),
    val svgFontGlyphCount: Int,
    val contextPaintApproximationCount: Int,
    val cssImportRuleCount: Int,
    val cssImportedInlineRuleCount: Int,
    val cssExternalImportCount: Int,
    val imageStats: SvgImageStats,
    val styleAttributeCount: Int,
    val presentationStyleAttributeCount: Int,
    val warningCount: Int,
    val unsupportedWarnings: List<String>,
    val unsupportedMatrixTransforms: Int,
    val supportedMatrixTransforms: Int,
    val matrixCount: Int,
    val translateCount: Int,
    val scaleCount: Int,
    val rotateCount: Int,
    val conversionProfile: String,
    val outputDpSize: Int,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val pathDataOptimizedCount: Int = 0,
    val pathDataCharactersBefore: Int = 0,
    val pathDataCharactersAfter: Int = 0,
    val pathDataRepeatedCommandsRemoved: Int = 0,
    val pathDataNumbersNormalized: Int = 0,
    val emptyPathDataRemoved: Int = 0,
    val moveOnlyPathsRemoved: Int = 0,
    val invisiblePathsRemoved: Int = 0,
    val emptyGroupsRemoved: Int = 0,
    val redundantGroupsFlattened: Int = 0,
    val compatiblePathsMerged: Int = 0,
    val exactDuplicatePathsRemoved: Int = 0,
    val translatedGroupsFlattened: Int = 0,
    val translatedPaths: Int = 0,
    val scaledGroupsFlattened: Int = 0,
    val scaledPaths: Int = 0,
    val scaledStrokeWidths: Int = 0,
    val scaleGroupsPreservedForSize: Int = 0,
    val nonUniformScaleGroupsFlattened: Int = 0,
    val nonUniformScaledPaths: Int = 0,
    val nonUniformScaleGroupsPreservedForSize: Int = 0,
    val rotationGroupsFlattened: Int = 0,
    val rotatedPaths: Int = 0,
    val rotationGroupsPreservedForSize: Int = 0,
    val identityTransformAttributesRemoved: Int = 0,
    val nestedTransformGroupsComposed: Int = 0,
    val shorterCommandFormsSelected: Int = 0,
    val relativeCommandsSelected: Int = 0,
    val axisCommandsSelected: Int = 0,
    val optimizedXmlCharactersBefore: Int = 0,
    val optimizedXmlCharactersAfter: Int = 0,
    val styleResolutionMs: Long = 0,
    val definitionSetupMs: Long = 0,
    val treeConversionMs: Long = 0,
    val outputOptimizationMs: Long = 0,
    val optimizationPathSyntaxNanos: Long = 0,
    val optimizationPruningCleanupNanos: Long = 0,
    val optimizationTransformsNanos: Long = 0,
    val optimizationDeduplicationNanos: Long = 0,
    val optimizationNumericCleanupNanos: Long = 0,
    val optimizationFormattingNanos: Long = 0,
    val reportAnalysisMs: Long = 0,
    val elapsedMs: Long = 0
)

object SvgConversionReporter {
    fun hasTag(svg: String, tagName: String): Boolean {
        return Regex("""<\s*$tagName\b""", RegexOption.IGNORE_CASE)
            .containsMatchIn(svg)
    }

    fun countConvertedBasicShapes(xml: String): Int {
        return Regex("""<!-- converted from <(rect|circle|ellipse|line|polyline|polygon)> -->""")
            .findAll(xml)
            .count()
    }

    fun countConvertedOriginalSvgPaths(xml: String): Int {
        return Regex("""<!-- converted from <path> -->""")
            .findAll(xml)
            .count()
    }

    fun countDrawableBasicShapeBreakdown(svg: String): BasicShapeBreakdown {
        var rectangles = 0
        var roundedRectangles = 0
        var circles = 0
        var ellipses = 0
        var polygons = 0
        var polylines = 0

        fun countElement(element: Element) {
            val tag = element.tagName.substringAfter(":").lowercase()

            when (tag) {
                "rect" -> {
                    if (basicShapeToPathData(element, tag) != null) {
                        val rx = floatAttr(element, "rx") ?: 0f
                        val ry = floatAttr(element, "ry") ?: 0f
                        if (rx > 0f || ry > 0f) {
                            roundedRectangles++
                        } else {
                            rectangles++
                        }
                    }
                }
                "circle" -> {
                    if (basicShapeToPathData(element, tag) != null) circles++
                }
                "ellipse" -> {
                    if (basicShapeToPathData(element, tag) != null) ellipses++
                }
                "polygon" -> {
                    if (basicShapeToPathData(element, tag) != null) polygons++
                }
                "polyline" -> {
                    if (basicShapeToPathData(element, tag) != null) polylines++
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    countElement(child as Element)
                }
            }
        }

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }

            val document = factory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(svg)))

            countElement(document.documentElement)

            BasicShapeBreakdown(
                rectangles = rectangles,
                roundedRectangles = roundedRectangles,
                circles = circles,
                ellipses = ellipses,
                polygons = polygons,
                polylines = polylines
            )
        } catch (e: Exception) {
            val rectTagMatches = Regex("""<\s*rect\b[^>]*>""", RegexOption.IGNORE_CASE)
                .findAll(svg)
                .map { it.value }
                .toList()

            val roundedRectFallbackCount = rectTagMatches.count { tag ->
                Regex("""\b(rx|ry)\s*=""", RegexOption.IGNORE_CASE).containsMatchIn(tag)
            }

            BasicShapeBreakdown(
                rectangles = rectTagMatches.size - roundedRectFallbackCount,
                roundedRectangles = roundedRectFallbackCount,
                circles = Regex("""<\s*circle\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
                ellipses = Regex("""<\s*ellipse\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
                polygons = Regex("""<\s*polygon\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count(),
                polylines = Regex("""<\s*polyline\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(svg).count()
            )
        }
    }

    fun countDefinitionDrawableElements(svg: String): Int {
        val basicShapeTags = setOf("rect", "circle", "ellipse", "line", "polyline", "polygon")
        var count = 0

        fun countDrawableElement(element: Element) {
            val tag = element.tagName.substringAfter(":").lowercase()

            when (tag) {
                "path" -> {
                    if (element.getAttribute("d").trim().isNotBlank()) {
                        count++
                    }
                }
                in basicShapeTags -> {
                    if (basicShapeToPathData(element, tag) != null) {
                        count++
                    }
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    countDrawableElement(child as Element)
                }
            }
        }

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }

            val document = factory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(svg)))

            val defsNodes = document.getElementsByTagName("defs")

            for (i in 0 until defsNodes.length) {
                val defs = defsNodes.item(i)
                val children = defs.childNodes

                for (j in 0 until children.length) {
                    val child = children.item(j)
                    if (child.nodeType == Node.ELEMENT_NODE) {
                        countDrawableElement(child as Element)
                    }
                }
            }

            count
        } catch (e: Exception) {
            val defsBlocks = Regex(
                """<defs\b[^>]*>.*?</defs>""",
                setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
            ).findAll(svg)

            defsBlocks.sumOf { block ->
                val value = block.value
                val pathCount = Regex("""<path\b[^>]*\bd\s*=\s*["'][^"']+["']""", RegexOption.IGNORE_CASE)
                    .findAll(value)
                    .count()

                val shapeCount = basicShapeTags.sumOf { tag ->
                    Regex("""<\s*$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
                        .findAll(value)
                        .count()
                }

                pathCount + shapeCount
            }
        }
    }


    fun buildReport(data: SvgConversionReportData): String {
        val aggregateWarningCount = aggregateWarningCount(data)

        val summaryTitle =
            if (aggregateWarningCount == 0)
                "🟢 Conversion Successful"
            else
                "🟡 Conversion Completed With Warnings"

        val drawablePathWord =
            if (data.convertedPathCount == 1) "path" else "paths"

        return buildString {
            appendLine(summaryTitle)
            appendLine("${data.convertedPathCount} drawable $drawablePathWord created")

            if (aggregateWarningCount == 0)
                appendLine("No warnings detected")
            else
                appendLine("$aggregateWarningCount warning(s) detected")

            appendLine()
            appendLine("Converted in ${data.elapsedMs} ms")
            appendPerformanceBreakdown(data)

            appendLine()
            appendLine("────────────────────")
            appendLine("Compatibility")
            appendLine("────────────────────")
            appendLine()
            appendCompatibilitySummary(data)

            appendLine("────────────────────")
            appendLine("Drawable Elements Processed")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Generated VectorDrawable paths: ${data.convertedPathCount}")
            appendLine("✓ SVG path elements converted: ${data.convertedOriginalPathCount}")
            appendLine("✓ Basic shapes converted: ${data.convertedBasicShapeCount}")
            appendBasicShapeBreakdown(data.basicShapeBreakdown)

            appendLine("✓ Expanded <use> references: ${data.resolvedUseExpansions}")
            if (data.unresolvedUseReferences > 0)
                appendLine("⚠ Unresolved <use> references: ${data.unresolvedUseReferences}")
            appendLine("✓ Definition drawable elements: ${data.definitionDrawableElementCount}")
            appendLine("✓ Groups created: ${data.generatedGroupCount}")

            appendLine()
            appendLine("────────────────────")
            appendLine("Output Optimization")
            appendLine("────────────────────")
            appendLine()
            appendLine("✓ Path data optimized: ${data.pathDataOptimizedCount}")
            appendLine("✓ Path-data characters: ${data.pathDataCharactersBefore} → ${data.pathDataCharactersAfter}")
            val pathCharacterDelta =
                data.pathDataCharactersAfter - data.pathDataCharactersBefore
            val pathChangePercent = if (data.pathDataCharactersBefore > 0) {
                kotlin.math.abs(pathCharacterDelta) * 100.0 /
                    data.pathDataCharactersBefore.toDouble()
            } else {
                0.0
            }
            when {
                pathCharacterDelta < 0 ->
                    appendLine(
                        "✓ Path data reduced by ${-pathCharacterDelta} characters " +
                            "(${String.format(java.util.Locale.US, "%.1f", pathChangePercent)}%)"
                    )
                pathCharacterDelta > 0 ->
                    appendLine(
                        "• Path data increased by $pathCharacterDelta characters " +
                            "(${String.format(java.util.Locale.US, "%.1f", pathChangePercent)}%)"
                    )
                else ->
                    appendLine("✓ Path data size unchanged")
            }

            val xmlCharactersSaved = (data.optimizedXmlCharactersBefore - data.optimizedXmlCharactersAfter).coerceAtLeast(0)
            val xmlReductionPercent = if (data.optimizedXmlCharactersBefore > 0) {
                xmlCharactersSaved * 100.0 / data.optimizedXmlCharactersBefore.toDouble()
            } else {
                0.0
            }
            appendLine("✓ XML reduction: ${String.format(java.util.Locale.US, "%.1f", xmlReductionPercent)}% ($xmlCharactersSaved characters)")

            if (data.pathDataNumbersNormalized > 0)
                appendLine("✓ Numeric values normalized: ${data.pathDataNumbersNormalized}")
            if (data.pathDataRepeatedCommandsRemoved > 0)
                appendLine("✓ Repeated commands removed: ${data.pathDataRepeatedCommandsRemoved}")
            if (data.emptyPathDataRemoved > 0)
                appendLine("✓ Empty path-data elements removed: ${data.emptyPathDataRemoved}")
            if (data.moveOnlyPathsRemoved > 0)
                appendLine("✓ Move-only paths removed: ${data.moveOnlyPathsRemoved}")
            if (data.invisiblePathsRemoved > 0)
                appendLine("✓ Fully transparent paths removed: ${data.invisiblePathsRemoved}")
            if (data.emptyGroupsRemoved > 0)
                appendLine("✓ Empty groups removed: ${data.emptyGroupsRemoved}")
            if (data.redundantGroupsFlattened > 0)
                appendLine("✓ Redundant groups flattened: ${data.redundantGroupsFlattened}")
            if (data.exactDuplicatePathsRemoved > 0)
                appendLine("✓ Exact duplicate paths removed: ${data.exactDuplicatePathsRemoved}")
            if (data.translatedGroupsFlattened > 0)
                appendLine("✓ Translation groups flattened: ${data.translatedGroupsFlattened}")
            if (data.translatedPaths > 0)
                appendLine("✓ Paths translated into coordinates: ${data.translatedPaths}")
            if (data.scaledGroupsFlattened > 0)
                appendLine("✓ Uniform scale groups flattened: ${data.scaledGroupsFlattened}")
            if (data.scaledPaths > 0)
                appendLine("✓ Paths scaled into coordinates: ${data.scaledPaths}")
            if (data.scaledStrokeWidths > 0)
                appendLine("✓ Stroke widths scaled: ${data.scaledStrokeWidths}")
            if (data.scaleGroupsPreservedForSize > 0)
                appendLine(
                    "✓ Uniform scale groups preserved for smaller output: " +
                        data.scaleGroupsPreservedForSize
                )
            if (data.nonUniformScaleGroupsFlattened > 0)
                appendLine(
                    "✓ Non-uniform scale groups flattened: " +
                        data.nonUniformScaleGroupsFlattened
                )
            if (data.nonUniformScaledPaths > 0)
                appendLine(
                    "✓ Fill-only paths non-uniformly scaled into coordinates: " +
                        data.nonUniformScaledPaths
                )
            if (data.nonUniformScaleGroupsPreservedForSize > 0)
                appendLine(
                    "✓ Non-uniform scale groups preserved for smaller output: " +
                        data.nonUniformScaleGroupsPreservedForSize
                )
            if (data.rotationGroupsFlattened > 0)
                appendLine(
                    "✓ Rotation groups flattened: " +
                        data.rotationGroupsFlattened
                )
            if (data.rotatedPaths > 0)
                appendLine(
                    "✓ Paths rotated into coordinates: " +
                        data.rotatedPaths
                )
            if (data.rotationGroupsPreservedForSize > 0)
                appendLine(
                    "✓ Rotation groups preserved for smaller output: " +
                        data.rotationGroupsPreservedForSize
                )
            if (data.identityTransformAttributesRemoved > 0)
                appendLine("✓ Identity transform attributes removed: ${data.identityTransformAttributesRemoved}")
            if (data.nestedTransformGroupsComposed > 0)
                appendLine("✓ Nested transform groups composed: ${data.nestedTransformGroupsComposed}")
            if (data.compatiblePathsMerged > 0)
                appendLine("✓ Compatible adjacent paths merged: ${data.compatiblePathsMerged}")
            if (data.shorterCommandFormsSelected > 0)
                appendLine("✓ Shorter command forms selected: ${data.shorterCommandFormsSelected}")
            if (data.relativeCommandsSelected > 0)
                appendLine("✓ Relative commands selected: ${data.relativeCommandsSelected}")
            if (data.axisCommandsSelected > 0)
                appendLine("✓ Horizontal/vertical commands selected: ${data.axisCommandsSelected}")
            appendLine()

            appendLine("────────────────────")
            appendLine("Transforms")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Translate: ${data.translateCount}")
            appendLine("✓ Scale: ${data.scaleCount}")
            appendLine("✓ Rotate: ${data.rotateCount}")

            if (data.matrixCount > 0) {
                appendLine("✓ Matrix supported: ${data.supportedMatrixTransforms}")
                appendLine("⚠ Matrix unsupported: ${data.unsupportedMatrixTransforms}")
            } else {
                appendLine("✓ Matrix: 0")
            }

            if (data.textElementCount > 0 || data.tspanElementCount > 0 || data.textPathElementCount > 0 || data.svgFontGlyphCount > 0) {
                appendLine()
                appendLine("────────────────────")
                appendLine("Text")
                appendLine("────────────────────")
                appendLine()
                appendLine("✓ Text elements found: ${data.textElementCount}")
                if (data.textElementsConvertedToPaths > 0) {
                    appendLine("✓ Text converted to paths: ${data.textElementsConvertedToPaths}")
                    appendLine("✓ Glyphs rendered: ${data.textGlyphPathsEmitted}")
                    appendLine("✓ Glyph-specific advances: ${data.textGlyphSpecificAdvances}")
                    appendLine("✓ Default font advances: ${data.textDefaultFontAdvances}")
                    if (data.textGlyphNameLookups > 0) {
                        appendLine("✓ Glyph-name lookups rendered: ${data.textGlyphNameLookups}")
                    }
                    if (data.textMissingGlyphFallbacks > 0) {
                        appendLine("✓ Missing-glyph fallbacks rendered: ${data.textMissingGlyphFallbacks}")
                    }
                    if (data.textHorizontalKerningPairs > 0 || data.textVerticalKerningPairs > 0 || data.textKerningAdjustmentsApplied > 0) {
                        appendLine("✓ Kerning rules parsed: ${data.textHorizontalKerningPairs + data.textVerticalKerningPairs}")
                        appendLine("✓ <hkern> entries parsed: ${data.textHorizontalKerningPairs}")
                        appendLine("✓ <vkern> entries parsed: ${data.textVerticalKerningPairs}")
                        appendLine("✓ Kerning rules matched: ${data.textHorizontalKerningPairsMatched + data.textVerticalKerningPairsMatched}")
                        appendLine("✓ <hkern> rules matched: ${data.textHorizontalKerningPairsMatched}")
                        appendLine("✓ <vkern> rules matched: ${data.textVerticalKerningPairsMatched}")
                        appendLine("✓ Kerning adjustments applied: ${data.textKerningAdjustmentsApplied}")
                    }
                }
                if (data.textElementsApproximated > 0) {
                    appendLine("✓ Bounding-box approximations: ${data.textElementsApproximated}")
                }
                if (data.textFontFamilies.isNotEmpty()) {
                    appendLine("✓ Font families: ${data.textFontFamilies.size}")
                    data.textFontFamilies.forEach { family ->
                        appendLine(" • $family")
                    }
                }
                if (data.textFontWeights.isNotEmpty()) {
                    appendLine("✓ Font weights: ${data.textFontWeights.size}")
                    data.textFontWeights
                        .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
                        .forEach { weight ->
                            appendLine(" • $weight")
                        }
                }
                if (data.tspanElementCount > 0) {
                    appendLine("✓ Text spans processed: ${data.tspanElementCount}")
                }
                if (data.textPathsConverted > 0) {
                    appendLine("✓ Text paths converted: ${data.textPathsConverted}")
                    appendLine("✓ Glyphs placed on paths: ${data.textPathGlyphsEmitted}")
                }
                val unconvertedTextPaths = maxOf(0, data.textPathElementCount - data.textPathsConverted)
                if (unconvertedTextPaths > 0) {
                    appendLine("⚠ Text-on-path elements not converted: $unconvertedTextPaths")
                }
                if (data.svgFontGlyphCount > 0) {
                    appendLine("ℹ Embedded SVG font glyph outlines found: ${data.svgFontGlyphCount}")
                }

                val textLengthAdjustmentCount =
                    data.textLengthSpacingAdjustments + data.textLengthSpacingAndGlyphsAdjustments
                val hasTextLayout =
                    data.writingModes.isNotEmpty() ||
                    data.textAnchors.isNotEmpty() ||
                    data.dominantBaselines.isNotEmpty() ||
                    data.alignmentBaselines.isNotEmpty() ||
                    data.baselineShifts.isNotEmpty() ||
                    data.lengthAdjustModes.isNotEmpty() ||
                    data.textPathMethods.isNotEmpty() ||
                    data.textGlyphRotationsApplied > 0 ||
                    data.textLetterSpacingAdjustmentsApplied > 0 ||
                    data.textBidiRunsReordered > 0 ||
                    data.textDirections.isNotEmpty() ||
                    data.textUnicodeBidiModes.isNotEmpty() ||
                    data.textWordSpacingAdjustmentsApplied > 0 ||
                    data.textDecorationPathsEmitted > 0 ||
                    textLengthAdjustmentCount > 0

                if (hasTextLayout) {
                    appendLine()
                    appendLine("────────────────────")
                    appendLine("Text Layout")
                    appendLine("────────────────────")
                    appendLine()

                    fun appendValues(label: String, values: List<String>) {
                        if (values.isEmpty()) return
                        appendLine("✓ $label:")
                        values.forEach { value -> appendLine(" • $value") }
                    }

                    appendValues("Writing modes", data.writingModes)
                    appendValues("Text directions", data.textDirections)
                    appendValues("unicode-bidi modes", data.textUnicodeBidiModes)
                    if (data.textBidiRunsReordered > 0) {
                        appendLine("✓ Bidirectional text runs reordered: ${data.textBidiRunsReordered}")
                    }
                    appendValues("Text anchors", data.textAnchors)
                    appendValues("Dominant baselines", data.dominantBaselines)
                    appendValues("Alignment baselines", data.alignmentBaselines)
                    appendValues("Baseline shifts", data.baselineShifts)
                    appendValues("lengthAdjust modes", data.lengthAdjustModes)
                    appendValues("textPath methods", data.textPathMethods)

                    if (data.textGlyphRotationsApplied > 0) {
                        appendLine("✓ Rotated glyphs: ${data.textGlyphRotationsApplied}")
                    }
                    if (data.textLetterSpacingAdjustmentsApplied > 0) {
                        appendLine("✓ Letter-spacing gaps applied: ${data.textLetterSpacingAdjustmentsApplied}")
                    }
                    if (data.textWordSpacingAdjustmentsApplied > 0) {
                        appendLine("✓ Word-spacing whitespace advances applied: ${data.textWordSpacingAdjustmentsApplied}")
                    }
                    if (data.textDecorationPathsEmitted > 0) {
                        appendLine("✓ Text-decoration paths emitted: ${data.textDecorationPathsEmitted}")
                    }
                    if (textLengthAdjustmentCount > 0) {
                        appendLine("✓ Text-length adjustments applied: $textLengthAdjustmentCount")
                        if (data.textLengthSpacingAdjustments > 0) {
                            appendLine(" • spacing: ${data.textLengthSpacingAdjustments}")
                        }
                        if (data.textLengthSpacingAndGlyphsAdjustments > 0) {
                            appendLine(" • spacingAndGlyphs: ${data.textLengthSpacingAndGlyphsAdjustments}")
                        }
                    }
                }
            }

            appendLine()
            appendLine("────────────────────")
            appendLine("SVG Analysis")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Viewport: ${data.viewportWidth} × ${data.viewportHeight}")
            appendLine("✓ Visible SVG drawable elements: ${data.visibleDrawableElementCount}")
            appendLine("✓ Visible SVG path elements: ${data.drawableValidPathCount}")
            appendLine("✓ Empty path elements skipped: ${data.emptyPathCount}")

            if (data.useCount > 0)
                appendLine("✓ <use> references found: ${data.useCount}")

            if (data.symbolCount > 0)
                appendLine("✓ Symbol definitions: ${data.symbolCount}")

            if (data.gradientFallbackColorCount > 0)
                appendLine("✓ Gradients converted: ${data.gradientFallbackColorCount}")

            if (data.patternApproximationCount > 0)
                appendLine("✓ Patterns approximated: ${data.patternApproximationCount}")
            if (data.patternTileExpansionCount > 0) {
                appendLine("✓ Pattern tiles expanded: ${data.patternTileExpansionCount}")
                appendLine("✓ Pattern tile paths emitted: ${data.patternTilePathCount}")
            }
            if (data.patternApproximationStats.complexPatternApproximationCount > 0) {
                val label = if (data.patternTileExpansionCount > 0) "Complex pattern tile definitions" else "Complex pattern fallback fills"
                appendLine("ℹ $label: ${data.patternApproximationStats.complexPatternApproximationCount}")
            }
            if (data.patternApproximationStats.sparsePatternApproximationCount > 0) {
                val label = if (data.patternTileExpansionCount > 0) "Sparse/transparent pattern tile definitions" else "Sparse/transparent pattern fallback fills"
                appendLine("ℹ $label: ${data.patternApproximationStats.sparsePatternApproximationCount}")
            }

            if (data.markerDefinitionCount > 0 || data.appliedMarkers > 0) {
                appendLine("✓ Marker definitions: ${data.markerDefinitionCount}")
                appendLine("✓ Markers approximated: ${data.appliedMarkers}")
            }

            if (data.clipPathCount > 0) {
                appendLine("✓ Clip paths: ${data.clipPathCount}")
                appendLine("✓ Clip path references: ${data.clipPathReferenceCount}")
                appendLine("✓ Clip paths applied: ${data.appliedClipPaths}")
            }

            if (data.maskPathCount > 0 || data.maskReferenceCount > 0) {
                appendLine("✓ Masks approximated: ${data.maskPathCount}")
                appendLine("✓ Mask references: ${data.maskReferenceCount}")
                appendLine("✓ Masks applied as clip paths: ${data.appliedMasks}")
            }

            if (data.dashedStrokesDetected > 0) {
                appendLine("✓ Dashed strokes detected: ${data.dashedStrokesDetected}")
                appendLine("✓ Dashed strokes approximated: ${data.dashedStrokesApproximated}")
                if (data.oddDashListsDuplicated > 0) {
                    appendLine("✓ Odd dash lists duplicated per SVG rules: ${data.oddDashListsDuplicated}")
                }
                if (data.invalidDashOffsetFallbacks > 0) {
                    appendLine("⚠ Invalid dash offsets replaced with 0: ${data.invalidDashOffsetFallbacks}")
                }
                if (data.dashOffsetsNormalized > 0) {
                    appendLine("✓ Negative/large dash offsets normalized: ${data.dashOffsetsNormalized}")
                }
                if (data.dashTransformExactCompensations > 0) {
                    appendLine("✓ Non-scaling dash transforms compensated exactly: ${data.dashTransformExactCompensations}")
                }
                if (data.dashTransformApproximateCompensations > 0) {
                    appendLine("⚠ Non-scaling dash transforms approximated: ${data.dashTransformApproximateCompensations}")
                }
                if (data.invalidDashArrays > 0) {
                    appendLine("⚠ Invalid dash arrays detected: ${data.invalidDashArrays}")
                    appendLine("✓ Solid-stroke fallbacks used: ${data.dashSolidFallbacks}")
                }
            }

            if (data.displayNoneElementsSkipped > 0 || data.visibilityHiddenElementsSkipped > 0) {
                appendLine("✓ Hidden drawable elements skipped: ${data.displayNoneElementsSkipped + data.visibilityHiddenElementsSkipped}")
                appendLine("  • display=\"none\": ${data.displayNoneElementsSkipped}")
                appendLine("  • visibility=\"hidden/collapse\": ${data.visibilityHiddenElementsSkipped}")
            }

            if (data.nestedSvgViewportCount > 0) {
                appendLine("✓ Nested SVG viewports processed: ${data.nestedSvgViewportCount}")
                appendLine("✓ Percentage-based nested viewports: ${data.nestedSvgPercentageViewportCount}")
                appendLine("✓ Nested viewport clips applied: ${data.nestedSvgViewportClipCount}")
                appendLine("✓ Nested viewport overflow=\"hidden\": ${data.nestedSvgOverflowHiddenCount}")
                appendLine("✓ Nested viewport overflow=\"visible\": ${data.nestedSvgOverflowVisibleCount}")
                if (data.nestedSvgOverflowAutoCount > 0 || data.nestedSvgOverflowScrollCount > 0) {
                    appendLine("⚠ Nested viewport overflow auto/scroll approximated by clipping: ${data.nestedSvgOverflowAutoCount + data.nestedSvgOverflowScrollCount}")
                    appendLine("  • overflow=\"auto\": ${data.nestedSvgOverflowAutoCount}")
                    appendLine("  • overflow=\"scroll\": ${data.nestedSvgOverflowScrollCount}")
                }
                if (data.nestedSvgOverflowUnsupportedCount > 0) {
                    appendLine("⚠ Unsupported nested viewport overflow values treated as visible: ${data.nestedSvgOverflowUnsupportedCount}")
                }
            }

            if (data.nonScalingStrokesDetected > 0) {
                val exactNonScalingStrokes = maxOf(
                    0,
                    data.nonScalingStrokesCompensated - data.nonScalingStrokesUncertain
                )
                appendLine("✓ Non-scaling strokes detected: ${data.nonScalingStrokesDetected}")
                appendLine("✓ Non-scaling strokes compensated exactly: $exactNonScalingStrokes")
                if (data.nonScalingStrokesUncertain > 0) {
                    appendLine("⚠ Non-scaling strokes approximated for non-uniform scaling: ${data.nonScalingStrokesUncertain}")
                }
            }

            if (data.filterDefinitionCount > 0) {
                appendLine("✓ Filter definitions found: ${data.filterDefinitionCount}")
            }
            if (data.filterReferenceCount > 0) {
                appendLine("⚠ Filter references ignored: ${data.filterReferenceCount}")
            }

            if (data.contextPaintApproximationCount > 0) {
                appendLine("ℹ context-fill/context-stroke approximated using inherited paint.")
            }

            if (data.cssImportRuleCount > 0) {
                appendLine("✓ CSS @import rules found: ${data.cssImportRuleCount}")
                appendLine("✓ Inline CSS imports applied: ${data.cssImportedInlineRuleCount}")
                if (data.cssExternalImportCount > 0) {
                    appendLine("⚠ External CSS imports ignored: ${data.cssExternalImportCount}")
                }
            }

            if (data.imageStats.imageElementCount > 0) {
                appendLine("⚠ Image elements found: ${data.imageStats.imageElementCount}")
                if (data.imageStats.embeddedRasterImageCount > 0) {
                    appendLine("ℹ Embedded raster images: ${data.imageStats.embeddedRasterImageCount}")
                }
                if (data.imageStats.embeddedSvgImageCount > 0) {
                    appendLine("ℹ Embedded SVG image references: ${data.imageStats.embeddedSvgImageCount}")
                }
                if (data.imageStats.externalImageCount > 0) {
                    appendLine("⚠ External image references: ${data.imageStats.externalImageCount}")
                }
                if (data.imageStats.missingHrefImageCount > 0) {
                    appendLine("⚠ Image elements without href: ${data.imageStats.missingHrefImageCount}")
                }
                appendLine("ℹ Images with explicit width/height: ${data.imageStats.imageElementsWithSize}")
            }

            appendLine("✓ Style attributes: ${data.styleAttributeCount}")
            appendLine("✓ Presentation attributes: ${data.presentationStyleAttributeCount}")

            appendLine()
            appendLine("────────────────────")
            appendLine("Output")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ Profile: ${data.conversionProfile}")

            appendLine(
                if (data.outputDpSize > 0)
                    "✓ Output size: ${data.outputDpSize}dp"
                else
                    "✓ Output size: Keep SVG size"
            )

            appendLine()
            appendLine("────────────────────")
            appendLine("Conversion Status")
            appendLine("────────────────────")
            appendLine()

            appendLine("✓ VectorDrawable generated")
            appendLine("✓ XML validated")
            appendLine("✓ Ready to save")

            val unapproximatedDashedStrokes = maxOf(0, data.dashedStrokesDetected - data.dashedStrokesApproximated)

            if (
                data.unsupportedWarnings.isNotEmpty() ||
                data.unsupportedMatrixTransforms > 0 ||
                data.unresolvedUseReferences > 0 ||
                unapproximatedDashedStrokes > 0 ||
                data.dashTransformApproximateCompensations > 0 ||
                data.nonScalingStrokesUncertain > 0 ||
                data.cssExternalImportCount > 0 ||
                data.imageStats.imageElementCount > 0 ||
                maxOf(0, data.textElementCount - data.textElementsApproximated - data.textElementsConvertedToPaths) > 0 ||
                data.tspanElementCount > 0 ||
                maxOf(0, data.textPathElementCount - data.textPathsConverted) > 0 ||
                data.nestedSvgOverflowAutoCount > 0 ||
                data.nestedSvgOverflowScrollCount > 0 ||
                data.nestedSvgOverflowUnsupportedCount > 0
            ) {
                appendLine()
                appendLine("────────────────────")
                appendLine("Warnings")
                appendLine("────────────────────")
                appendLine()

                if (data.unsupportedMatrixTransforms > 0) {
                    appendLine("⚠ Matrix transforms not flattened: ${data.unsupportedMatrixTransforms}")
                }

                if (data.unresolvedUseReferences > 0) {
                    appendLine("⚠ Unresolved <use> references: ${data.unresolvedUseReferences}")
                }

                if (unapproximatedDashedStrokes > 0) {
                    appendLine("⚠ Dashed strokes could not be approximated: $unapproximatedDashedStrokes")
                }
                if (data.dashTransformApproximateCompensations > 0) {
                    appendLine("⚠ Non-scaling dashed strokes under non-uniform transforms use geometric-mean compensation: ${data.dashTransformApproximateCompensations}")
                }

                if (data.nonScalingStrokesUncertain > 0) {
                    appendLine("⚠ Non-scaling stroke compensation used average scale for non-uniform transforms: ${data.nonScalingStrokesUncertain}")
                }

                if (data.nestedSvgOverflowAutoCount > 0 || data.nestedSvgOverflowScrollCount > 0) {
                    appendLine("⚠ Nested <svg> overflow=\"auto/scroll\" approximated using viewport clipping: ${data.nestedSvgOverflowAutoCount + data.nestedSvgOverflowScrollCount}")
                }

                if (data.nestedSvgOverflowUnsupportedCount > 0) {
                    appendLine("⚠ Unsupported nested <svg> overflow values treated as visible: ${data.nestedSvgOverflowUnsupportedCount}")
                }

                if (data.cssExternalImportCount > 0) {
                    appendLine("⚠ External CSS @import ignored: ${data.cssExternalImportCount}. Inline data:text/css imports are supported, but external stylesheets cannot be fetched from a standalone SVG file.")
                }

                if (data.imageStats.imageElementCount > 0) {
                    appendLine(imageConversionWarning(data.imageStats))
                }

                val handledTextCount = data.textElementsApproximated + data.textElementsConvertedToPaths
                val unapproximatedTextCount = maxOf(0, data.textElementCount - handledTextCount)
                val unconvertedTextPaths = maxOf(0, data.textPathElementCount - data.textPathsConverted)
                if (unapproximatedTextCount > 0 || unconvertedTextPaths > 0) {
                    appendLine(textConversionWarning(data))
                }

                data.unsupportedWarnings.forEach {
                    appendLine("⚠ $it")
                }
            }
        }
    }


    /**
     * Returns the number shown in the report summary.
     *
     * An invalid dash array produces one user-facing conversion warning:
     * the dashed stroke could not be approximated and a solid fallback was
     * used. The converter's raw warning count may also include the internal
     * invalid-array diagnostic, so remove that duplicate from the aggregate.
     */
    private fun StringBuilder.appendPerformanceBreakdown(data: SvgConversionReportData) {
        val measuredStages = listOf(
            "Style resolution" to data.styleResolutionMs,
            "Preparation" to data.definitionSetupMs,
            "Tree conversion" to data.treeConversionMs,
            "Optimization" to data.outputOptimizationMs,
            "Analysis" to data.reportAnalysisMs
        )

        val measuredTimeMs = measuredStages.sumOf { (_, durationMs) ->
            durationMs.coerceAtLeast(0L)
        }
        val otherFrameworkMs = (data.elapsedMs - measuredTimeMs).coerceAtLeast(0L)

        val visibleStages = buildList {
            addAll(measuredStages.filter { (_, durationMs) -> durationMs > 0L })
            if (otherFrameworkMs > 0L) {
                add("Other / framework" to otherFrameworkMs)
            }
        }

        if (visibleStages.isEmpty()) return

        appendLine()
        appendLine("Performance")

        visibleStages.forEach { (label, durationMs) ->
            val percentage = performancePercentage(durationMs, data.elapsedMs)
            appendLine("• $label: $durationMs ms ($percentage%)")

            if (label == "Optimization") {
                appendOptimizationBreakdown(data)
            }
        }
    }

    private fun StringBuilder.appendOptimizationBreakdown(data: SvgConversionReportData) {
        val stages = listOf(
            "Path syntax and colors" to data.optimizationPathSyntaxNanos,
            "Pruning and group cleanup" to data.optimizationPruningCleanupNanos,
            "Transform optimization" to data.optimizationTransformsNanos,
            "Deduplication and merging" to data.optimizationDeduplicationNanos,
            "Numeric cleanup" to data.optimizationNumericCleanupNanos,
            "Final formatting" to data.optimizationFormattingNanos
        ).filter { (_, durationNanos) -> durationNanos > 0L }

        if (stages.isEmpty()) return

        val totalNanos = stages.sumOf { (_, durationNanos) -> durationNanos }
        stages.forEach { (label, durationNanos) ->
            val percentage = nanosPercentageLabel(durationNanos, totalNanos)
            appendLine("  ◦ $label: ${formatNanosAsMilliseconds(durationNanos)} ($percentage)")
        }
    }

    private fun formatNanosAsMilliseconds(durationNanos: Long): String {
        val milliseconds = durationNanos / 1_000_000.0
        return if (milliseconds >= 10.0) {
            "${milliseconds.toLong()} ms"
        } else {
            String.format(java.util.Locale.US, "%.1f ms", milliseconds)
        }
    }

    private fun nanosPercentageLabel(durationNanos: Long, totalNanos: Long): String {
        if (durationNanos <= 0L || totalNanos <= 0L) return "0%"

        val exactPercentage = durationNanos * 100.0 / totalNanos.toDouble()
        if (exactPercentage < 1.0) return "<1%"

        val roundedPercentage = (exactPercentage + 0.5).toInt().coerceIn(1, 100)
        return "$roundedPercentage%"
    }

    private fun performancePercentage(durationMs: Long, totalMs: Long): Int {
        if (durationMs <= 0L || totalMs <= 0L) return 0

        return (((durationMs * 100.0) / totalMs) + 0.5)
            .toInt()
            .coerceIn(0, 100)
    }

    private fun aggregateWarningCount(data: SvgConversionReportData): Int {
        val unapproximatedDashedStrokes =
            maxOf(0, data.dashedStrokesDetected - data.dashedStrokesApproximated)

        val duplicatedInvalidDashWarnings =
            minOf(data.invalidDashArrays, unapproximatedDashedStrokes)

        return maxOf(0, data.warningCount - duplicatedInvalidDashWarnings)
    }


    private data class CompatibilitySummary(
        val stars: String,
        val label: String,
        val fidelityPercent: Int,
        val converted: List<String>,
        val approximated: List<String>,
        val ignored: List<String>,
        val unsupported: List<String>
    )

    private fun StringBuilder.appendCompatibilitySummary(data: SvgConversionReportData) {
        val summary = compatibilitySummary(data)

        appendLine("${summary.stars} ${summary.label}")
        appendLine("Estimated visual fidelity: ~${summary.fidelityPercent}%")

        if (summary.converted.isNotEmpty()) {
            appendLine()
            appendLine("Converted")
            summary.converted.forEach { appendLine("✓ $it") }
        }

        if (summary.approximated.isNotEmpty()) {
            appendLine()
            appendLine("Approximated")
            summary.approximated.forEach { appendLine("✓ $it") }
        }

        if (summary.ignored.isNotEmpty()) {
            appendLine()
            appendLine("Ignored")
            summary.ignored.forEach { appendLine("⚠ $it") }
        }

        if (summary.unsupported.isNotEmpty()) {
            appendLine()
            appendLine("Unsupported")
            summary.unsupported.forEach { appendLine("⚠ $it") }
        }

        appendLine()
    }

    private fun compatibilitySummary(data: SvgConversionReportData): CompatibilitySummary {
        val converted = linkedSetOf<String>()
        val approximated = linkedSetOf<String>()
        val ignored = linkedSetOf<String>()
        val unsupported = linkedSetOf<String>()

        if (data.patternApproximationCount > 0) {
            val patternLabel = when {
                data.patternTileExpansionCount > 0 -> "Pattern fills (tile approximation)"
                data.patternApproximationStats.sparsePatternApproximationCount > 0 -> "Pattern fills (fallback color)"
                data.patternApproximationStats.complexPatternApproximationCount > 0 -> "Pattern fills (complex fallback)"
                else -> "Pattern fills"
            }
            approximated.add(patternLabel)
        }
        if (data.maskPathCount > 0 || data.appliedMasks > 0) approximated.add("Masks as clip paths")
        if (data.appliedMarkers > 0) approximated.add("Markers")
        if (data.contextPaintApproximationCount > 0) approximated.add("context-fill/context-stroke")
        if (data.textElementsConvertedToPaths > 0) converted.add("Text converted to vector paths")
        if (data.textElementsApproximated > 0) approximated.add("Text (${data.textElementsApproximated} bounding box approximation${if (data.textElementsApproximated == 1) "" else "s"})")

        val unapproximatedDashedStrokes = maxOf(0, data.dashedStrokesDetected - data.dashedStrokesApproximated)
        when {
            data.dashedStrokesApproximated > 0 && unapproximatedDashedStrokes > 0 -> {
                approximated.add("Dashed strokes (${data.dashedStrokesApproximated} approximated)")
                unsupported.add("Some dashed strokes ($unapproximatedDashedStrokes not approximated)")
            }
            data.dashedStrokesApproximated > 0 -> approximated.add("Dashed strokes")
            unapproximatedDashedStrokes > 0 -> unsupported.add("Dashed strokes")
        }

        if (data.dashTransformApproximateCompensations > 0) approximated.add("Non-scaling dash transforms")
        if (data.nonScalingStrokesUncertain > 0) approximated.add("Non-scaling strokes under non-uniform transforms")

        if (data.cssExternalImportCount > 0) ignored.add("External CSS @import")
        if (data.filterReferenceCount > 0) ignored.add("Filter effects")

        val handledTextCount = data.textElementsApproximated + data.textElementsConvertedToPaths
        val unapproximatedTextCount = maxOf(0, data.textElementCount - handledTextCount)
        val unconvertedTextPaths = maxOf(0, data.textPathElementCount - data.textPathsConverted)
        if (unapproximatedTextCount > 0 || unconvertedTextPaths > 0) {
            unsupported.add("Text")
        }
        if (data.imageStats.imageElementCount > 0) {
            unsupported.add("Raster/external images")
        }
        if (data.unsupportedMatrixTransforms > 0) {
            unsupported.add("Unsupported matrix transforms")
        }
        if (data.unresolvedUseReferences > 0) {
            unsupported.add("Unresolved <use> references")
        }

        data.unsupportedWarnings.forEach { warning ->
            val normalized = warning.trim()
            when {
                normalized.contains("Filter effects ignored", ignoreCase = true) -> ignored.add("Filter effects")
                normalized.contains("Missing paint reference", ignoreCase = true) -> unsupported.add("Missing paint references")
                normalized.contains("Linear gradients", ignoreCase = true) || normalized.contains("Radial gradients", ignoreCase = true) -> unsupported.add("Unsupported gradients")
                normalized.contains("Patterns", ignoreCase = true) -> unsupported.add("Unsupported patterns")
                normalized.contains("Masks", ignoreCase = true) -> unsupported.add("Unsupported masks")
                normalized.contains("Clip paths", ignoreCase = true) -> unsupported.add("Unsupported clip paths")
                normalized.isNotBlank() -> unsupported.add(normalized.removeSuffix("."))
            }
        }

        val unsupportedCount = unsupported.size
        val approximationCount = approximated.size
        val ignoredCount = ignored.size
        val noConvertedVisibleVectorContent =
            data.convertedPathCount == 0 &&
                (data.visibleDrawableElementCount > 0 || data.imageStats.imageElementCount > 0 || data.textElementCount > 0)

        val fidelity = when {
            noConvertedVisibleVectorContent -> 25
            unsupportedCount >= 4 -> 25
            unsupportedCount >= 2 -> 55
            unsupportedCount == 1 -> when {
                approximationCount >= 3 || ignoredCount >= 2 -> 75
                else -> 80
            }
            approximationCount > 0 || ignoredCount > 0 -> when {
                data.patternTileExpansionCount > 0 -> 95
                data.patternApproximationStats.sparsePatternApproximationCount > 0 -> 80
                data.patternApproximationStats.complexPatternApproximationCount > 0 -> 85
                approximationCount + ignoredCount >= 4 -> 90
                else -> 95
            }
            else -> 100
        }

        val stars = when {
            fidelity >= 100 -> "★★★★★"
            fidelity >= 95 -> "★★★★☆"
            fidelity >= 80 -> "★★★☆☆"
            fidelity >= 55 -> "★★☆☆☆"
            else -> "★☆☆☆☆"
        }

        val label = when {
            fidelity >= 100 -> "Fully compatible"
            fidelity >= 95 -> "Mostly compatible"
            fidelity >= 80 -> "Partially compatible"
            fidelity >= 55 -> "Limited compatibility"
            else -> "Poor compatibility"
        }

        return CompatibilitySummary(
            stars = stars,
            label = label,
            fidelityPercent = fidelity,
            converted = converted.toList(),
            approximated = approximated.toList(),
            ignored = ignored.toList(),
            unsupported = unsupported.toList()
        )
    }

    private fun imageConversionWarning(stats: SvgImageStats): String {
        val parts = mutableListOf<String>()

        if (stats.embeddedRasterImageCount > 0) {
            parts.add("embedded raster images were found")
        }
        if (stats.embeddedSvgImageCount > 0) {
            parts.add("embedded SVG image references were found")
        }
        if (stats.externalImageCount > 0) {
            parts.add("external image references were found")
        }
        if (stats.missingHrefImageCount > 0) {
            parts.add("some image elements have no href")
        }

        val detail = if (parts.isEmpty()) {
            "image elements were found"
        } else {
            parts.joinToString("; ")
        }

        return "⚠ <image> elements are raster or external resources and cannot be represented in VectorDrawable path XML. $detail. Convert images to vector paths/outlines, or keep the source as a raster asset if pixel accuracy is required."
    }

    private fun textConversionWarning(data: SvgConversionReportData): String {
        return if (data.svgFontGlyphCount > 0) {
            "⚠ Some text could not be converted exactly. Embedded SVG font glyphs were found, but unsupported characters or advanced text layout may still need manual outlining."
        } else {
            "⚠ Some text could not be approximated accurately. Text is best converted to paths/outlines before importing for exact VectorDrawable output."
        }
    }

    private fun basicShapeToPathData(element: Element, tagName: String): String? {
        return SvgShapeConverters.basicShapeToPathData(element, tagName)
    }

    private fun StringBuilder.appendBasicShapeBreakdown(breakdown: BasicShapeBreakdown) {
        appendLine("    • Rectangles: ${breakdown.rectangles}")
        appendLine("    • Rounded rectangles: ${breakdown.roundedRectangles}")
        appendLine("    • Circles: ${breakdown.circles}")
        appendLine("    • Ellipses: ${breakdown.ellipses}")
        appendLine("    • Polygons: ${breakdown.polygons}")
        appendLine("    • Polylines: ${breakdown.polylines}")
    }

    private fun floatAttr(element: Element, name: String): Float? {
        return element.getAttribute(name)
            .replace("px", "")
            .replace("dp", "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
    }
}
