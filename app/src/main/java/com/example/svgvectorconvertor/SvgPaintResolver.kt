package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale

object SvgPaintResolver {
    private var activeGradientFallbackColors: Map<String, String> = emptyMap()

    fun setGradientFallbackColors(colors: Map<String, String>) {
        activeGradientFallbackColors = colors
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
        val v = value?.trim() ?: return null
        val id = Regex("""url\(\s*#([^)'\"\s]+)\s*\)""")
            .find(v)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: return null

        return activeGradientFallbackColors[id]
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
        return when (value.trim().lowercase()) {
            "black" -> "#000000"
            "white" -> "#FFFFFF"
            "red" -> "#FF0000"
            "green" -> "#008000"
            "blue" -> "#0000FF"
            "yellow" -> "#FFFF00"
            "cyan", "aqua" -> "#00FFFF"
            "magenta", "fuchsia" -> "#FF00FF"
            "gray", "grey" -> "#808080"
            "darkgray", "darkgrey" -> "#A9A9A9"
            "lightgray", "lightgrey" -> "#D3D3D3"
            "orange" -> "#FFA500"
            "purple" -> "#800080"
            "brown" -> "#A52A2A"
            "pink" -> "#FFC0CB"
            "lime" -> "#00FF00"
            "navy" -> "#000080"
            "teal" -> "#008080"
            "olive" -> "#808000"
            "maroon" -> "#800000"
            "silver" -> "#C0C0C0"
            "transparent" -> "#00000000"
            else -> null
        }
    }

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

        return java.lang.String.format(java.util.Locale.US, "%.3f", combined)
            .trimEnd('0')
            .trimEnd('.')
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
