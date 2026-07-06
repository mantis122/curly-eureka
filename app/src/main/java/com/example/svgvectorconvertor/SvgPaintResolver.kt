package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale

object SvgPaintResolver {
    private var activeGradientFallbackColors: Map<String, String> = emptyMap()
    private var activeGradientDefinitions: Map<String, SvgVectorGradient> = emptyMap()

    fun setGradientFallbackColors(colors: Map<String, String>) {
        activeGradientFallbackColors = colors
    }

    fun setGradientDefinitions(definitions: Map<String, SvgVectorGradient>) {
        activeGradientDefinitions = definitions
        activeGradientFallbackColors = SvgGradientResolver.fallbackColors(definitions)
    }

    fun isValidAndroidColor(value: String?): Boolean {
        if (value == null) return false

        val v = value.trim()

        if (v == "none") return true
        if (v == "currentColor") return true
        if (v == "@android:color/transparent") return true

        return Regex("""^#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$""")
            .matches(v)
    }

    fun isUnsupportedPaint(value: String?): Boolean {
        if (value == null) return false

        val v = value.trim()

        return v.startsWith("url(") ||
            v.startsWith("linear-gradient") ||
            v.startsWith("radial-gradient")
    }

    fun fallbackColorForPaint(value: String?): String? {
        val id = SvgGradientResolver.gradientIdFromPaint(value) ?: return null
        return activeGradientFallbackColors[id]
    }

    fun gradientForPaint(value: String?): SvgVectorGradient? {
        val id = SvgGradientResolver.gradientIdFromPaint(value) ?: return null
        return activeGradientDefinitions[id]
    }

    fun gradientForPaint(value: String?, bounds: SvgObjectBounds?): SvgVectorGradient? {
        return SvgGradientResolver.adaptGradientToObjectBounds(gradientForPaint(value), bounds)
    }

    fun normalizedAndroidColor(value: String?): String? {
        val v = value?.trim() ?: return null

        if (isValidAndroidColor(v) && v != "none" && v != "currentColor") return v

        if (v.equals("currentColor", ignoreCase = true)) return "#000000"

        svgNamedColor(v)?.let { return it }

        parseRgbColor(v)?.let { rgb ->
            return "#%02X%02X%02X".format(rgb.first, rgb.second, rgb.third)
        }

        return null
    }

    fun svgNamedColor(value: String): String? {
        return cssNamedColors[value.trim().lowercase(Locale.US)]
    }

    private val cssNamedColors = mapOf(
        "aliceblue" to "#F0F8FF",
        "antiquewhite" to "#FAEBD7",
        "aqua" to "#00FFFF",
        "aquamarine" to "#7FFFD4",
        "azure" to "#F0FFFF",
        "beige" to "#F5F5DC",
        "bisque" to "#FFE4C4",
        "black" to "#000000",
        "blanchedalmond" to "#FFEBCD",
        "blue" to "#0000FF",
        "blueviolet" to "#8A2BE2",
        "brown" to "#A52A2A",
        "burlywood" to "#DEB887",
        "cadetblue" to "#5F9EA0",
        "chartreuse" to "#7FFF00",
        "chocolate" to "#D2691E",
        "coral" to "#FF7F50",
        "cornflowerblue" to "#6495ED",
        "cornsilk" to "#FFF8DC",
        "crimson" to "#DC143C",
        "cyan" to "#00FFFF",
        "darkblue" to "#00008B",
        "darkcyan" to "#008B8B",
        "darkgoldenrod" to "#B8860B",
        "darkgray" to "#A9A9A9",
        "darkgreen" to "#006400",
        "darkgrey" to "#A9A9A9",
        "darkkhaki" to "#BDB76B",
        "darkmagenta" to "#8B008B",
        "darkolivegreen" to "#556B2F",
        "darkorange" to "#FF8C00",
        "darkorchid" to "#9932CC",
        "darkred" to "#8B0000",
        "darksalmon" to "#E9967A",
        "darkseagreen" to "#8FBC8F",
        "darkslateblue" to "#483D8B",
        "darkslategray" to "#2F4F4F",
        "darkslategrey" to "#2F4F4F",
        "darkturquoise" to "#00CED1",
        "darkviolet" to "#9400D3",
        "deeppink" to "#FF1493",
        "deepskyblue" to "#00BFFF",
        "dimgray" to "#696969",
        "dimgrey" to "#696969",
        "dodgerblue" to "#1E90FF",
        "firebrick" to "#B22222",
        "floralwhite" to "#FFFAF0",
        "forestgreen" to "#228B22",
        "fuchsia" to "#FF00FF",
        "gainsboro" to "#DCDCDC",
        "ghostwhite" to "#F8F8FF",
        "gold" to "#FFD700",
        "goldenrod" to "#DAA520",
        "gray" to "#808080",
        "green" to "#008000",
        "greenyellow" to "#ADFF2F",
        "grey" to "#808080",
        "honeydew" to "#F0FFF0",
        "hotpink" to "#FF69B4",
        "indianred" to "#CD5C5C",
        "indigo" to "#4B0082",
        "ivory" to "#FFFFF0",
        "khaki" to "#F0E68C",
        "lavender" to "#E6E6FA",
        "lavenderblush" to "#FFF0F5",
        "lawngreen" to "#7CFC00",
        "lemonchiffon" to "#FFFACD",
        "lightblue" to "#ADD8E6",
        "lightcoral" to "#F08080",
        "lightcyan" to "#E0FFFF",
        "lightgoldenrodyellow" to "#FAFAD2",
        "lightgray" to "#D3D3D3",
        "lightgreen" to "#90EE90",
        "lightgrey" to "#D3D3D3",
        "lightpink" to "#FFB6C1",
        "lightsalmon" to "#FFA07A",
        "lightseagreen" to "#20B2AA",
        "lightskyblue" to "#87CEFA",
        "lightslategray" to "#778899",
        "lightslategrey" to "#778899",
        "lightsteelblue" to "#B0C4DE",
        "lightyellow" to "#FFFFE0",
        "lime" to "#00FF00",
        "limegreen" to "#32CD32",
        "linen" to "#FAF0E6",
        "magenta" to "#FF00FF",
        "maroon" to "#800000",
        "mediumaquamarine" to "#66CDAA",
        "mediumblue" to "#0000CD",
        "mediumorchid" to "#BA55D3",
        "mediumpurple" to "#9370DB",
        "mediumseagreen" to "#3CB371",
        "mediumslateblue" to "#7B68EE",
        "mediumspringgreen" to "#00FA9A",
        "mediumturquoise" to "#48D1CC",
        "mediumvioletred" to "#C71585",
        "midnightblue" to "#191970",
        "mintcream" to "#F5FFFA",
        "mistyrose" to "#FFE4E1",
        "moccasin" to "#FFE4B5",
        "navajowhite" to "#FFDEAD",
        "navy" to "#000080",
        "oldlace" to "#FDF5E6",
        "olive" to "#808000",
        "olivedrab" to "#6B8E23",
        "orange" to "#FFA500",
        "orangered" to "#FF4500",
        "orchid" to "#DA70D6",
        "palegoldenrod" to "#EEE8AA",
        "palegreen" to "#98FB98",
        "paleturquoise" to "#AFEEEE",
        "palevioletred" to "#DB7093",
        "papayawhip" to "#FFEFD5",
        "peachpuff" to "#FFDAB9",
        "peru" to "#CD853F",
        "pink" to "#FFC0CB",
        "plum" to "#DDA0DD",
        "powderblue" to "#B0E0E6",
        "purple" to "#800080",
        "rebeccapurple" to "#663399",
        "red" to "#FF0000",
        "rosybrown" to "#BC8F8F",
        "royalblue" to "#4169E1",
        "saddlebrown" to "#8B4513",
        "salmon" to "#FA8072",
        "sandybrown" to "#F4A460",
        "seagreen" to "#2E8B57",
        "seashell" to "#FFF5EE",
        "sienna" to "#A0522D",
        "silver" to "#C0C0C0",
        "skyblue" to "#87CEEB",
        "slateblue" to "#6A5ACD",
        "slategray" to "#708090",
        "slategrey" to "#708090",
        "snow" to "#FFFAFA",
        "springgreen" to "#00FF7F",
        "steelblue" to "#4682B4",
        "tan" to "#D2B48C",
        "teal" to "#008080",
        "thistle" to "#D8BFD8",
        "tomato" to "#FF6347",
        "transparent" to "#00000000",
        "turquoise" to "#40E0D0",
        "violet" to "#EE82EE",
        "wheat" to "#F5DEB3",
        "white" to "#FFFFFF",
        "whitesmoke" to "#F5F5F5",
        "yellow" to "#FFFF00",
        "yellowgreen" to "#9ACD32"
    )

    fun safeFillColor(value: String?): String {
        val v = value?.trim()

        return when {
            v.isNullOrBlank() -> "#000000"
            v.equals("none", ignoreCase = true) -> "@android:color/transparent"
            fallbackColorForPaint(v) != null -> fallbackColorForPaint(v)!!
            isUnsupportedPaint(v) -> "@android:color/transparent"
            normalizedAndroidColor(v) != null -> normalizedAndroidColor(v)!!
            else -> "@android:color/transparent"
        }
    }

    fun safeStrokeColor(value: String?): String? {
        val v = value?.trim()

        return when {
            v.isNullOrBlank() -> null
            v.equals("none", ignoreCase = true) -> null
            fallbackColorForPaint(v) != null -> fallbackColorForPaint(v)
            isUnsupportedPaint(v) -> null
            normalizedAndroidColor(v) != null -> normalizedAndroidColor(v)
            else -> null
        }
    }

    fun collectGradientFallbackColors(svg: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isIgnoringComments = true
            }

            val document = factory
                .newDocumentBuilder()
                .parse(InputSource(StringReader(svg)))

            val gradients = mutableMapOf<String, Element>()

            fun visit(node: Node) {
                if (node.nodeType != Node.ELEMENT_NODE) return

                val element = node as Element
                val tag = element.tagName.substringAfter(":").lowercase()
                val id = element.getAttribute("id").trim()

                if ((tag == "lineargradient" || tag == "radialgradient") && id.isNotBlank()) {
                    gradients[id] = element
                }

                val children = element.childNodes
                for (i in 0 until children.length) {
                    visit(children.item(i))
                }
            }

            visit(document.documentElement)

            fun gradientHrefId(element: Element): String? {
                val href = element.getAttribute("href").ifBlank {
                    element.getAttribute("xlink:href").ifBlank {
                        element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                    }
                }.trim()

                return href.removePrefix("#").takeIf { it.isNotBlank() }
            }

            fun stopsForGradient(element: Element, depth: Int = 0): List<Pair<String, Float>> {
                if (depth > 10) return emptyList()

                val stops = mutableListOf<Pair<String, Float>>()
                val children = element.childNodes

                for (i in 0 until children.length) {
                    val child = children.item(i)
                    if (child.nodeType != Node.ELEMENT_NODE) continue

                    val stop = child as Element
                    val tag = stop.tagName.substringAfter(":").lowercase()
                    if (tag != "stop") continue

                    val style = stop.getAttribute("style").ifBlank { null }
                    val color = stop.getAttribute("stop-color").ifBlank {
                        styleValue(style, "stop-color") ?: ""
                    }.trim()

                    if (color.isBlank() || color == "none") continue

                    val opacityText = stop.getAttribute("stop-opacity").ifBlank {
                        styleValue(style, "stop-opacity") ?: "1"
                    }
                    val opacity = parseSvgAlpha(opacityText) ?: 1f

                    stops.add(color to opacity)
                }

                if (stops.isNotEmpty()) return stops

                val hrefId = gradientHrefId(element) ?: return emptyList()
                val referenced = gradients[hrefId] ?: return emptyList()
                return stopsForGradient(referenced, depth + 1)
            }

            gradients.forEach { (id, element) ->
                val stops = stopsForGradient(element)
                val fallback = averageStopColor(stops)
                if (fallback != null) {
                    result[id] = fallback
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }

        return result
    }



    private fun averageStopColor(stops: List<Pair<String, Float>>): String? {
        if (stops.isEmpty()) return null

        var totalWeight = 0f
        var totalR = 0f
        var totalG = 0f
        var totalB = 0f

        stops.forEach { (colorText, opacity) ->
            val rgb = parseRgbColor(colorText) ?: return@forEach
            val weight = opacity.coerceIn(0f, 1f).takeIf { it > 0f } ?: 0.05f

            totalR += rgb.first * weight
            totalG += rgb.second * weight
            totalB += rgb.third * weight
            totalWeight += weight
        }

        if (totalWeight <= 0f) return null

        val r = (totalR / totalWeight).toInt().coerceIn(0, 255)
        val g = (totalG / totalWeight).toInt().coerceIn(0, 255)
        val b = (totalB / totalWeight).toInt().coerceIn(0, 255)

        return "#%02X%02X%02X".format(r, g, b)
    }

    private data class RgbColor(val first: Int, val second: Int, val third: Int)

    private fun parseRgbColor(value: String?): RgbColor? {
        val v = value?.trim() ?: return null

        if (v == "currentColor") return RgbColor(0, 0, 0)

        svgNamedColor(v)?.let { named ->
            return parseRgbColor(named)
        }

        val hex = v.removePrefix("#")
        return when {
            Regex("""^[0-9a-fA-F]{3}$""").matches(hex) -> {
                val r = "${hex[0]}${hex[0]}".toInt(16)
                val g = "${hex[1]}${hex[1]}".toInt(16)
                val b = "${hex[2]}${hex[2]}".toInt(16)
                RgbColor(r, g, b)
            }
            Regex("""^[0-9a-fA-F]{6}$""").matches(hex) -> {
                RgbColor(
                    hex.substring(0, 2).toInt(16),
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16)
                )
            }
            Regex("""^[0-9a-fA-F]{8}$""").matches(hex) -> {
                RgbColor(
                    hex.substring(2, 4).toInt(16),
                    hex.substring(4, 6).toInt(16),
                    hex.substring(6, 8).toInt(16)
                )
            }
            v.startsWith("rgb(", ignoreCase = true) -> {
                val parts = v.substringAfter("(").substringBeforeLast(")")
                    .split(",")
                    .map { it.trim().removeSuffix("%").toFloatOrNull() }
                if (parts.size < 3 || parts.any { it == null }) return null
                RgbColor(
                    parts[0]!!.toInt().coerceIn(0, 255),
                    parts[1]!!.toInt().coerceIn(0, 255),
                    parts[2]!!.toInt().coerceIn(0, 255)
                )
            }
            else -> null
        }
    }

    fun styleValue(style: String?, name: String): String? {
        if (style == null) return null

        return style
            .split(";")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { declaration ->
                val parts = declaration.split(":", limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val propertyName = parts[0].trim()
                val propertyValue = parts[1].trim()

                if (propertyName.equals(name, ignoreCase = true) && propertyValue.isNotBlank()) {
                    propertyValue
                } else {
                    null
                }
            }
            .firstOrNull()
    }


    fun resolveDrawableAlpha(opacity: String?, channelOpacity: String?): String? {
        return combineAlpha(opacity, channelOpacity)
    }

    /**
     * SVG group/object opacity is composited with ancestor opacity. Android VectorDrawable
     * has no group-alpha, so the converter pushes the product down to each emitted path.
     */
    fun inheritedOpacity(parentOpacity: String?, localOpacity: String?): String? {
        return combineAlpha(parentOpacity, localOpacity)
    }

    /**
     * fill-opacity and stroke-opacity are inherited paint properties. A local value
     * replaces the inherited value; it does not multiply it. The resulting value is
     * later multiplied by inherited object/group opacity when path alpha is emitted.
     */
    fun inheritedPaintOpacity(parentPaintOpacity: String?, localPaintOpacity: String?): String? {
        val local = normalizeAlpha(localPaintOpacity)
        if (local != null) return local
        return normalizeAlpha(parentPaintOpacity)
    }

    fun normalizeAlpha(value: String?): String? {
        val alpha = parseSvgAlpha(value)?.coerceIn(0f, 1f) ?: return null
        if (alpha >= 0.999f) return null
        return formatAlpha(alpha)
    }

    fun formatAlpha(alpha: Float): String {
        return java.lang.String.format(java.util.Locale.US, "%.3f", alpha.coerceIn(0f, 1f))
            .trimEnd('0')
            .trimEnd('.')
    }

    fun combineAlpha(baseAlpha: String?, localAlpha: String?): String? {
        val base = parseSvgAlpha(baseAlpha)
        val local = parseSvgAlpha(localAlpha)

        val combined = when {
            base != null && local != null -> base * local
            base != null -> base
            local != null -> local
            else -> return null
        }.coerceIn(0f, 1f)

        if (combined >= 0.999f) return null

        return formatAlpha(combined)
    }

    fun parseSvgAlpha(value: String?): Float? {
        val raw = value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parsed = if (raw.endsWith("%")) {
            raw.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
        } else {
            raw.toFloatOrNull()
        }

        return parsed?.coerceIn(0f, 1f)
    }

}
