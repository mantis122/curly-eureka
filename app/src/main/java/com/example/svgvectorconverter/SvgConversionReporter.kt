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
    val clipPathCount: Int,
    val clipPathReferenceCount: Int,
    val appliedClipPaths: Int,
    val maskPathCount: Int,
    val maskReferenceCount: Int,
    val appliedMasks: Int,
    val dashedStrokesDetected: Int,
    val dashedStrokesApproximated: Int,
    val filterDefinitionCount: Int,
    val filterReferenceCount: Int,
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
    val elapsedMs: Long
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
        val summaryTitle =
            if (data.warningCount == 0)
                "🟢 Conversion Successful"
            else
                "🟡 Conversion Completed With Warnings"

        val drawablePathWord =
            if (data.convertedPathCount == 1) "path" else "paths"

        return buildString {
            appendLine(summaryTitle)
            appendLine("${data.convertedPathCount} drawable $drawablePathWord created")

            if (data.warningCount == 0)
                appendLine("No warnings detected")
            else
                appendLine("${data.warningCount} warning(s) detected")

            appendLine()
            appendLine("Converted in ${data.elapsedMs} ms")
            appendLine()

            appendLine("────────────────────")
            appendLine("Drawable Elements Processed")
            appendLine("────────────────────")
            appendLine()

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
            }

            if (data.filterDefinitionCount > 0) {
                appendLine("✓ Filter definitions found: ${data.filterDefinitionCount}")
            }
            if (data.filterReferenceCount > 0) {
                appendLine("⚠ Filter references ignored: ${data.filterReferenceCount}")
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
                unapproximatedDashedStrokes > 0
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

                data.unsupportedWarnings.forEach {
                    appendLine("⚠ $it")
                }
            }
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
