package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.hypot

data class SvgGradientStop(
    val offset: String,
    val color: String
)

data class SvgVectorGradient(
    val id: String,
    val type: String,
    val startX: String? = null,
    val startY: String? = null,
    val endX: String? = null,
    val endY: String? = null,
    val centerX: String? = null,
    val centerY: String? = null,
    val gradientRadius: String? = null,
    val stops: List<SvgGradientStop> = emptyList(),
    val fallbackColor: String,
    val hadGradientTransform: Boolean = false
)

object SvgGradientResolver {
    fun collectGradientDefinitions(
        svg: String,
        viewportWidth: Float,
        viewportHeight: Float
    ): Map<String, SvgVectorGradient> {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }
            val document = factory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(svg)))

            val rawGradients = mutableMapOf<String, Element>()

            fun visit(node: Node) {
                if (node.nodeType != Node.ELEMENT_NODE) return
                val element = node as Element
                val tag = element.tagName.substringAfter(":").lowercase(Locale.US)
                val id = element.getAttribute("id").trim()
                if ((tag == "lineargradient" || tag == "radialgradient") && id.isNotBlank()) {
                    rawGradients[id] = element
                }

                val children = element.childNodes
                for (i in 0 until children.length) {
                    visit(children.item(i))
                }
            }

            visit(document.documentElement)

            rawGradients.mapNotNull { (id, element) ->
                buildGradient(id, element, rawGradients, viewportWidth, viewportHeight)
                    ?.let { id to it }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun gradientIdFromPaint(value: String?): String? {
        val raw = value?.trim() ?: return null
        return Regex("""url\(\s*#([^)'\"\s]+)\s*\)""")
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun fallbackColors(definitions: Map<String, SvgVectorGradient>): Map<String, String> {
        return definitions.mapValues { it.value.fallbackColor }
    }

    fun emitGradientAttr(
        output: StringBuilder,
        gradient: SvgVectorGradient,
        attrName: String,
        indent: String
    ) {
        output.appendLine("${indent}<aapt:attr name=\"$attrName\">")
        output.appendLine("${indent}    <gradient")
        output.appendLine("${indent}        android:type=\"${gradient.type}\"")

        if (gradient.type == "linear") {
            output.appendLine("${indent}        android:startX=\"${gradient.startX ?: "0"}\"")
            output.appendLine("${indent}        android:startY=\"${gradient.startY ?: "0"}\"")
            output.appendLine("${indent}        android:endX=\"${gradient.endX ?: "0"}\"")
            output.appendLine("${indent}        android:endY=\"${gradient.endY ?: "0"}\"")
        } else {
            output.appendLine("${indent}        android:centerX=\"${gradient.centerX ?: "0"}\"")
            output.appendLine("${indent}        android:centerY=\"${gradient.centerY ?: "0"}\"")
            output.appendLine("${indent}        android:gradientRadius=\"${gradient.gradientRadius ?: "1"}\"")
        }

        if (gradient.stops.size <= 1) {
            val color = gradient.stops.firstOrNull()?.color ?: gradient.fallbackColor
            output.appendLine("${indent}        android:startColor=\"$color\"")
            output.appendLine("${indent}        android:endColor=\"$color\" />")
        } else if (gradient.stops.size == 2) {
            output.appendLine("${indent}        android:startColor=\"${gradient.stops[0].color}\"")
            output.appendLine("${indent}        android:endColor=\"${gradient.stops[1].color}\" />")
        } else {
            output.appendLine("${indent}        >")
            gradient.stops.forEach { stop ->
                output.appendLine("${indent}        <item")
                output.appendLine("${indent}            android:offset=\"${stop.offset}\"")
                output.appendLine("${indent}            android:color=\"${stop.color}\" />")
            }
            output.appendLine("${indent}    </gradient>")
        }

        output.appendLine("${indent}</aapt:attr>")
    }

    fun transformedGradientCount(definitions: Map<String, SvgVectorGradient>): Int {
        return definitions.values.count { it.hadGradientTransform }
    }

    private fun buildGradient(
        id: String,
        element: Element,
        rawGradients: Map<String, Element>,
        viewportWidth: Float,
        viewportHeight: Float
    ): SvgVectorGradient? {
        val tag = element.tagName.substringAfter(":").lowercase(Locale.US)
        val stops = stopsForGradient(element, rawGradients)
        if (stops.isEmpty()) return null

        val fallback = averageStopColor(stops.map { it.color }) ?: stops.first().color
        val transformText = inheritedGradientAttribute(element, rawGradients, "gradientTransform")
        val transform = SvgTransformParser.combineTransformListToMatrix(
            SvgTransformParser.parseTransformList(transformText),
            null
        )

        return if (tag == "lineargradient") {
            val start = point(
                coordinateFloat(inheritedGradientAttribute(element, rawGradients, "x1"), viewportWidth, 0f),
                coordinateFloat(inheritedGradientAttribute(element, rawGradients, "y1"), viewportHeight, 0f),
                transform
            )
            val end = point(
                coordinateFloat(inheritedGradientAttribute(element, rawGradients, "x2"), viewportWidth, viewportWidth),
                coordinateFloat(inheritedGradientAttribute(element, rawGradients, "y2"), viewportHeight, 0f),
                transform
            )

            SvgVectorGradient(
                id = id,
                type = "linear",
                startX = format(start.first),
                startY = format(start.second),
                endX = format(end.first),
                endY = format(end.second),
                stops = stops,
                fallbackColor = fallback,
                hadGradientTransform = transform != null
            )
        } else {
            val cx = coordinateFloat(inheritedGradientAttribute(element, rawGradients, "cx"), viewportWidth, viewportWidth / 2f)
            val cy = coordinateFloat(inheritedGradientAttribute(element, rawGradients, "cy"), viewportHeight, viewportHeight / 2f)
            val r = coordinateFloat(
                inheritedGradientAttribute(element, rawGradients, "r"),
                minOf(viewportWidth, viewportHeight),
                minOf(viewportWidth, viewportHeight) / 2f
            )
            val center = point(cx, cy, transform)
            val radius = transformedRadius(cx, cy, r, transform)

            SvgVectorGradient(
                id = id,
                type = "radial",
                centerX = format(center.first),
                centerY = format(center.second),
                gradientRadius = format(radius),
                stops = stops,
                fallbackColor = fallback,
                hadGradientTransform = transform != null
            )
        }
    }

    private fun inheritedGradientAttribute(
        element: Element,
        rawGradients: Map<String, Element>,
        name: String,
        depth: Int = 0
    ): String? {
        if (depth > 10) return null
        val local = element.getAttribute(name).trim()
        if (local.isNotBlank()) return local

        val href = gradientHrefId(element) ?: return null
        val referenced = rawGradients[href] ?: return null
        return inheritedGradientAttribute(referenced, rawGradients, name, depth + 1)
    }

    private fun point(x: Float, y: Float, transform: AffineTransform?): Pair<Float, Float> {
        return transform?.mapPoint(x, y) ?: Pair(x, y)
    }

    private fun transformedRadius(cx: Float, cy: Float, r: Float, transform: AffineTransform?): Float {
        if (transform == null) return r
        val center = transform.mapPoint(cx, cy)
        val px = transform.mapPoint(cx + r, cy)
        val py = transform.mapPoint(cx, cy + r)
        val rx = hypot(px.first - center.first, px.second - center.second)
        val ry = hypot(py.first - center.first, py.second - center.second)
        return ((rx + ry) / 2f).coerceAtLeast(0.001f)
    }

    private fun stopsForGradient(
        element: Element,
        rawGradients: Map<String, Element>,
        depth: Int = 0
    ): List<SvgGradientStop> {
        if (depth > 10) return emptyList()

        val stops = mutableListOf<SvgGradientStop>()
        val children = element.childNodes

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue

            val stop = child as Element
            val tag = stop.tagName.substringAfter(":").lowercase(Locale.US)
            if (tag != "stop") continue

            val style = stop.getAttribute("style").ifBlank { null }
            val colorText = stop.getAttribute("stop-color").ifBlank {
                SvgPaintResolver.styleValue(style, "stop-color") ?: ""
            }.trim()

            if (colorText.isBlank() || colorText.equals("none", ignoreCase = true)) continue

            val opacityText = stop.getAttribute("stop-opacity").ifBlank {
                SvgPaintResolver.styleValue(style, "stop-opacity") ?: "1"
            }
            val opacity = SvgPaintResolver.parseSvgAlpha(opacityText) ?: 1f
            val color = androidColorWithAlpha(colorText, opacity) ?: continue
            val offset = normalizeOffset(stop.getAttribute("offset"))

            stops.add(SvgGradientStop(offset = offset, color = color))
        }

        if (stops.isNotEmpty()) return stops.sortedBy { it.offset.toFloatOrNull() ?: 0f }

        val href = gradientHrefId(element) ?: return emptyList()
        val referenced = rawGradients[href] ?: return emptyList()
        return stopsForGradient(referenced, rawGradients, depth + 1)
    }

    private fun gradientHrefId(element: Element): String? {
        val href = element.getAttribute("href").ifBlank {
            element.getAttribute("xlink:href").ifBlank {
                element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
            }
        }.trim()

        return href.removePrefix("#").takeIf { it.isNotBlank() }
    }

    private fun normalizeOffset(value: String?): String {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return "0"
        val number = if (raw.endsWith("%")) {
            raw.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
        } else {
            raw.toFloatOrNull()
        } ?: 0f
        return format(number.coerceIn(0f, 1f))
    }

    private fun coordinateFloat(value: String?, relativeTo: Float, default: Float): Float {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return default
        return if (raw.endsWith("%")) {
            raw.removeSuffix("%").trim().toFloatOrNull()?.div(100f)?.times(relativeTo)
        } else {
            raw.removeSuffix("px").trim().toFloatOrNull()
        } ?: default
    }

    private fun androidColorWithAlpha(value: String, alpha: Float): String? {
        val rgb = parseRgb(value) ?: return null
        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return if (a >= 255) {
            "#%02X%02X%02X".format(rgb.first, rgb.second, rgb.third)
        } else {
            "#%02X%02X%02X%02X".format(a, rgb.first, rgb.second, rgb.third)
        }
    }

    private fun averageStopColor(colors: List<String>): String? {
        val rgbs = colors.mapNotNull { parseRgb(it) }
        if (rgbs.isEmpty()) return null
        val r = rgbs.map { it.first }.average().toInt().coerceIn(0, 255)
        val g = rgbs.map { it.second }.average().toInt().coerceIn(0, 255)
        val b = rgbs.map { it.third }.average().toInt().coerceIn(0, 255)
        return "#%02X%02X%02X".format(r, g, b)
    }

    private data class Rgb(val first: Int, val second: Int, val third: Int)

    private fun parseRgb(value: String?): Rgb? {
        val raw = value?.trim() ?: return null
        val v = SvgPaintResolver.normalizedAndroidColor(raw) ?: raw
        val hex = v.removePrefix("#")

        return when {
            Regex("""^[0-9a-fA-F]{3}$""").matches(hex) -> Rgb(
                "${hex[0]}${hex[0]}".toInt(16),
                "${hex[1]}${hex[1]}".toInt(16),
                "${hex[2]}${hex[2]}".toInt(16)
            )
            Regex("""^[0-9a-fA-F]{6}$""").matches(hex) -> Rgb(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
            Regex("""^[0-9a-fA-F]{8}$""").matches(hex) -> Rgb(
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16)
            )
            else -> null
        }
    }

    private fun format(value: Float): String {
        return java.lang.String.format(Locale.US, "%.4f", value)
            .trimEnd('0')
            .trimEnd('.')
            .ifBlank { "0" }
    }
}
