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
    val hadGradientTransform: Boolean = false,
    val tileMode: String? = null,
    val objectBoundingBoxUnits: Boolean = false
)

data class SvgObjectBounds(
    val minX: Float,
    val minY: Float,
    val width: Float,
    val height: Float
)

object SvgGradientResolver {
    private const val MAX_INHERITANCE_DEPTH = 24

    private data class ResolvedGradientSpec(
        val id: String,
        val sourceType: String,
        val attributes: Map<String, String>,
        val stops: List<SvgGradientStop>,
        val inheritedFrom: String? = null
    )

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

            val resolvedCache = mutableMapOf<String, ResolvedGradientSpec?>()

            rawGradients.keys.mapNotNull { id ->
                resolveGradientSpec(id, rawGradients, resolvedCache, emptySet())
                    ?.let { spec -> buildGradient(spec, viewportWidth, viewportHeight)?.let { id to it } }
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

    fun adaptGradientToObjectBounds(
        gradient: SvgVectorGradient?,
        bounds: SvgObjectBounds?
    ): SvgVectorGradient? {
        if (gradient == null || bounds == null || !gradient.objectBoundingBoxUnits) return gradient
        val safeWidth = bounds.width.coerceAtLeast(0.001f)
        val safeHeight = bounds.height.coerceAtLeast(0.001f)

        fun mapX(value: String?): String? = value?.toFloatOrNull()?.let { format(bounds.minX + it * safeWidth) }
        fun mapY(value: String?): String? = value?.toFloatOrNull()?.let { format(bounds.minY + it * safeHeight) }
        fun mapRadius(value: String?): String? = value?.toFloatOrNull()?.let {
            format((it * ((safeWidth + safeHeight) / 2f)).coerceAtLeast(0.001f))
        }

        return if (gradient.type == "linear") {
            gradient.copy(
                startX = mapX(gradient.startX),
                startY = mapY(gradient.startY),
                endX = mapX(gradient.endX),
                endY = mapY(gradient.endY),
                objectBoundingBoxUnits = false
            )
        } else {
            gradient.copy(
                centerX = mapX(gradient.centerX),
                centerY = mapY(gradient.centerY),
                gradientRadius = mapRadius(gradient.gradientRadius),
                objectBoundingBoxUnits = false
            )
        }
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

        gradient.tileMode?.let {
            output.appendLine("${indent}        android:tileMode=\"$it\"")
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

    private fun resolveGradientSpec(
        id: String,
        rawGradients: Map<String, Element>,
        cache: MutableMap<String, ResolvedGradientSpec?>,
        visiting: Set<String>
    ): ResolvedGradientSpec? {
        cache[id]?.let { return it }
        if (id in visiting || visiting.size > MAX_INHERITANCE_DEPTH) return null

        val element = rawGradients[id] ?: return null
        val href = gradientHrefId(element)
        val base = href?.let { resolveGradientSpec(it, rawGradients, cache, visiting + id) }

        val tag = element.tagName.substringAfter(":").lowercase(Locale.US)
        val localType = when (tag) {
            "lineargradient" -> "linear"
            "radialgradient" -> "radial"
            else -> base?.sourceType ?: "linear"
        }

        // SVG gradient references inherit all unspecified gradient attributes from the referenced
        // gradient. Stops are inherited only when the referencing gradient has no local stops.
        val mergedAttributes = base?.attributes.orEmpty().toMutableMap()
        gradientAttributeNames.forEach { name ->
            val value = rawAttribute(element, name)
            if (!value.isNullOrBlank()) mergedAttributes[name] = value
        }

        val localStops = localStopsForGradient(element)
        val mergedStops = if (localStops.isNotEmpty()) localStops else base?.stops.orEmpty()

        val resolved = ResolvedGradientSpec(
            id = id,
            sourceType = localType,
            attributes = mergedAttributes,
            stops = mergedStops,
            inheritedFrom = href
        )

        cache[id] = resolved
        return resolved
    }

    private fun buildGradient(
        spec: ResolvedGradientSpec,
        viewportWidth: Float,
        viewportHeight: Float
    ): SvgVectorGradient? {
        val stops = spec.stops
        if (stops.isEmpty()) return null

        val fallback = averageStopColor(stops.map { it.color }) ?: stops.first().color
        val transformText = spec.attributes["gradientTransform"]
        val transform = SvgTransformParser.combineTransformListToMatrix(
            SvgTransformParser.parseTransformList(transformText),
            null
        )
        val tileMode = androidTileMode(spec.attributes["spreadMethod"])
        val objectBoundingBoxUnits = !spec.attributes["gradientUnits"].equals("userSpaceOnUse", ignoreCase = true)
        val xRelativeTo = if (objectBoundingBoxUnits) 1f else viewportWidth
        val yRelativeTo = if (objectBoundingBoxUnits) 1f else viewportHeight
        val radiusRelativeTo = if (objectBoundingBoxUnits) 1f else minOf(viewportWidth, viewportHeight)

        return if (spec.sourceType == "linear") {
            val start = point(
                coordinateFloat(spec.attributes["x1"], xRelativeTo, 0f),
                coordinateFloat(spec.attributes["y1"], yRelativeTo, 0f),
                transform
            )
            val end = point(
                coordinateFloat(spec.attributes["x2"], xRelativeTo, if (objectBoundingBoxUnits) 1f else viewportWidth),
                coordinateFloat(spec.attributes["y2"], yRelativeTo, 0f),
                transform
            )

            SvgVectorGradient(
                id = spec.id,
                type = "linear",
                startX = format(start.first),
                startY = format(start.second),
                endX = format(end.first),
                endY = format(end.second),
                stops = stops,
                fallbackColor = fallback,
                hadGradientTransform = transform != null,
                tileMode = tileMode,
                objectBoundingBoxUnits = objectBoundingBoxUnits
            )
        } else {
            val cx = coordinateFloat(spec.attributes["cx"], xRelativeTo, if (objectBoundingBoxUnits) 0.5f else viewportWidth / 2f)
            val cy = coordinateFloat(spec.attributes["cy"], yRelativeTo, if (objectBoundingBoxUnits) 0.5f else viewportHeight / 2f)
            val r = coordinateFloat(
                spec.attributes["r"],
                radiusRelativeTo,
                if (objectBoundingBoxUnits) 0.5f else minOf(viewportWidth, viewportHeight) / 2f
            )
            val center = point(cx, cy, transform)
            val radius = transformedRadius(cx, cy, r, transform)

            SvgVectorGradient(
                id = spec.id,
                type = "radial",
                centerX = format(center.first),
                centerY = format(center.second),
                gradientRadius = format(radius),
                stops = stops,
                fallbackColor = fallback,
                hadGradientTransform = transform != null,
                tileMode = tileMode,
                objectBoundingBoxUnits = objectBoundingBoxUnits
            )
        }
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

    private fun localStopsForGradient(element: Element): List<SvgGradientStop> {
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

            val stopOpacityText = stop.getAttribute("stop-opacity").ifBlank {
                SvgPaintResolver.styleValue(style, "stop-opacity") ?: "1"
            }
            val opacityText = stop.getAttribute("opacity").ifBlank {
                SvgPaintResolver.styleValue(style, "opacity") ?: "1"
            }
            val stopOpacity = SvgPaintResolver.parseSvgAlpha(stopOpacityText) ?: 1f
            val localOpacity = SvgPaintResolver.parseSvgAlpha(opacityText) ?: 1f
            val color = androidColorWithAlpha(colorText, stopOpacity * localOpacity) ?: continue
            val offset = normalizeOffset(stop.getAttribute("offset"))

            stops.add(SvgGradientStop(offset = offset, color = color))
        }

        return stops.sortedBy { it.offset.toFloatOrNull() ?: 0f }
    }

    private fun gradientHrefId(element: Element): String? {
        val href = element.getAttribute("href").ifBlank {
            element.getAttribute("xlink:href").ifBlank {
                try {
                    element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                } catch (_: Exception) {
                    ""
                }
            }
        }.trim()

        return href.removePrefix("#").takeIf { it.isNotBlank() }
    }

    private fun rawAttribute(element: Element, name: String): String? {
        return element.getAttribute(name).trim().takeIf { it.isNotBlank() }
    }

    private fun androidTileMode(spreadMethod: String?): String? {
        return when (spreadMethod?.trim()?.lowercase(Locale.US)) {
            "repeat" -> "repeat"
            "reflect" -> "mirror"
            "pad" -> null
            else -> null
        }
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

    private fun androidColorWithAlpha(value: String, alphaMultiplier: Float): String? {
        val rgba = parseSourceRgba(value) ?: return null
        val finalAlpha = (rgba.alpha * alphaMultiplier).coerceIn(0f, 1f)
        val a = (finalAlpha * 255f).toInt().coerceIn(0, 255)
        return if (a >= 255) {
            "#%02X%02X%02X".format(rgba.red, rgba.green, rgba.blue)
        } else {
            "#%02X%02X%02X%02X".format(a, rgba.red, rgba.green, rgba.blue)
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
    private data class Rgba(val red: Int, val green: Int, val blue: Int, val alpha: Float = 1f)

    private fun parseRgb(value: String?): Rgb? {
        val raw = value?.trim() ?: return null
        val normalized = SvgPaintResolver.normalizedAndroidColor(raw) ?: raw
        val hex = normalized.removePrefix("#")

        return when {
            Regex("""^[0-9a-fA-F]{3}$""").matches(hex) -> Rgb(
                "${hex[0]}${hex[0]}".toInt(16),
                "${hex[1]}${hex[1]}".toInt(16),
                "${hex[2]}${hex[2]}".toInt(16)
            )
            Regex("""^[0-9a-fA-F]{4}$""").matches(hex) -> Rgb(
                "${hex[1]}${hex[1]}".toInt(16),
                "${hex[2]}${hex[2]}".toInt(16),
                "${hex[3]}${hex[3]}".toInt(16)
            )
            Regex("""^[0-9a-fA-F]{6}$""").matches(hex) -> Rgb(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
            // Generated Android colors are #AARRGGBB.
            Regex("""^[0-9a-fA-F]{8}$""").matches(hex) -> Rgb(
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16)
            )
            else -> null
        }
    }

    private fun parseSourceRgba(value: String?): Rgba? {
        val raw = value?.trim() ?: return null
        val namedOrOriginal = SvgPaintResolver.svgNamedColor(raw) ?: raw
        val v = namedOrOriginal.trim()
        val hex = v.removePrefix("#")

        return when {
            Regex("""^[0-9a-fA-F]{3}$""").matches(hex) -> Rgba(
                "${hex[0]}${hex[0]}".toInt(16),
                "${hex[1]}${hex[1]}".toInt(16),
                "${hex[2]}${hex[2]}".toInt(16),
                1f
            )
            // SVG/CSS #RGBA syntax. Android VectorDrawable expects #AARRGGBB,
            // so keep RGB and multiply this embedded alpha with stop-opacity.
            Regex("""^[0-9a-fA-F]{4}$""").matches(hex) -> Rgba(
                "${hex[0]}${hex[0]}".toInt(16),
                "${hex[1]}${hex[1]}".toInt(16),
                "${hex[2]}${hex[2]}".toInt(16),
                "${hex[3]}${hex[3]}".toInt(16) / 255f
            )
            Regex("""^[0-9a-fA-F]{6}$""").matches(hex) -> Rgba(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                1f
            )
            // SVG/CSS 8-digit color syntax is #RRGGBBAA. Convert to Android later.
            Regex("""^[0-9a-fA-F]{8}$""").matches(hex) -> Rgba(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16) / 255f
            )
            v.startsWith("rgb(", ignoreCase = true) || v.startsWith("rgba(", ignoreCase = true) -> {
                val parts = v.substringAfter("(").substringBeforeLast(")")
                    .split(",")
                    .map { it.trim() }
                if (parts.size < 3) return null

                val red = parseColorChannel(parts[0]) ?: return null
                val green = parseColorChannel(parts[1]) ?: return null
                val blue = parseColorChannel(parts[2]) ?: return null
                val alpha = parts.getOrNull(3)?.let { SvgPaintResolver.parseSvgAlpha(it) } ?: 1f

                Rgba(red, green, blue, alpha)
            }
            else -> {
                // Fall back to SvgPaintResolver for named colors and older supported formats.
                val normalized = SvgPaintResolver.normalizedAndroidColor(v) ?: return null
                if (normalized == v) return null
                parseSourceRgba(normalized)
            }
        }
    }

    private fun parseColorChannel(raw: String): Int? {
        val value = raw.trim()
        return if (value.endsWith("%")) {
            value.removeSuffix("%").trim().toFloatOrNull()
                ?.div(100f)
                ?.times(255f)
                ?.toInt()
                ?.coerceIn(0, 255)
        } else {
            value.toFloatOrNull()?.toInt()?.coerceIn(0, 255)
        }
    }

    private fun format(value: Float): String {
        return java.lang.String.format(Locale.US, "%.4f", value)
            .trimEnd('0')
            .trimEnd('.')
            .ifBlank { "0" }
    }

    private val gradientAttributeNames = listOf(
        "x1", "y1", "x2", "y2",
        "cx", "cy", "r", "fx", "fy", "fr",
        "gradientUnits",
        "gradientTransform",
        "spreadMethod"
    )
}
