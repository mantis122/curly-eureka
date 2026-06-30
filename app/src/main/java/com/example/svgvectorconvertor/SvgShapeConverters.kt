package com.example.svgvectorconverter

import org.w3c.dom.Element

object SvgShapeConverters {
    fun basicShapeToPathData(element: Element, tagName: String): String? {
        return when (tagName) {
            "rect" -> rectToPathData(element)
            "circle" -> circleToPathData(element)
            "ellipse" -> ellipseToPathData(element)
            "line" -> lineToPathData(element)
            "polyline" -> pointsToPathData(element.getAttribute("points"), close = false)
            "polygon" -> pointsToPathData(element.getAttribute("points"), close = true)
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    fun basicShapeTagToPathData(tag: String, tagName: String): String {
        return when (tagName) {
            "rect" -> {
                val x = floatAttrFromTag(tag, "x") ?: 0f
                val y = floatAttrFromTag(tag, "y") ?: 0f
                val w = floatAttrFromTag(tag, "width") ?: return ""
                val h = floatAttrFromTag(tag, "height") ?: return ""
                rectPathData(x, y, w, h, floatAttrFromTag(tag, "rx"), floatAttrFromTag(tag, "ry"))
            }

            "circle" -> {
                val cx = floatAttrFromTag(tag, "cx") ?: 0f
                val cy = floatAttrFromTag(tag, "cy") ?: 0f
                val r = floatAttrFromTag(tag, "r") ?: return ""
                circlePathData(cx, cy, r)
            }

            "ellipse" -> {
                val cx = floatAttrFromTag(tag, "cx") ?: 0f
                val cy = floatAttrFromTag(tag, "cy") ?: 0f
                val rx = floatAttrFromTag(tag, "rx") ?: return ""
                val ry = floatAttrFromTag(tag, "ry") ?: return ""
                ellipsePathData(cx, cy, rx, ry)
            }

            "line" -> {
                val x1 = floatAttrFromTag(tag, "x1") ?: 0f
                val y1 = floatAttrFromTag(tag, "y1") ?: 0f
                val x2 = floatAttrFromTag(tag, "x2") ?: 0f
                val y2 = floatAttrFromTag(tag, "y2") ?: 0f
                linePathData(x1, y1, x2, y2)
            }

            "polyline" -> pointsToPathData(attr(tag, "points").orEmpty(), close = false)
            "polygon" -> pointsToPathData(attr(tag, "points").orEmpty(), close = true)
            else -> ""
        }
    }

    private fun rectToPathData(element: Element): String {
        val x = floatAttr(element, "x") ?: 0f
        val y = floatAttr(element, "y") ?: 0f
        val w = floatAttr(element, "width") ?: return ""
        val h = floatAttr(element, "height") ?: return ""
        return rectPathData(x, y, w, h, floatAttr(element, "rx"), floatAttr(element, "ry"))
    }

    private fun rectPathData(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        rawRx: Float?,
        rawRy: Float?
    ): String {
        if (w <= 0f || h <= 0f) return ""

        var rx = rawRx ?: 0f
        var ry = rawRy ?: 0f

        // SVG rule: if only rx or ry is provided, the missing one uses the same value.
        if (rx > 0f && ry == 0f) ry = rx
        if (ry > 0f && rx == 0f) rx = ry

        // Clamp radii so they cannot exceed half the rectangle size.
        rx = rx.coerceAtMost(w / 2f)
        ry = ry.coerceAtMost(h / 2f)

        if (rx <= 0f || ry <= 0f) {
            return "M $x,$y L ${x + w},$y L ${x + w},${y + h} L $x,${y + h} Z"
        }

        val right = x + w
        val bottom = y + h

        return "M ${x + rx},$y " +
            "L ${right - rx},$y " +
            "A $rx,$ry 0,0,1 $right,${y + ry} " +
            "L $right,${bottom - ry} " +
            "A $rx,$ry 0,0,1 ${right - rx},$bottom " +
            "L ${x + rx},$bottom " +
            "A $rx,$ry 0,0,1 $x,${bottom - ry} " +
            "L $x,${y + ry} " +
            "A $rx,$ry 0,0,1 ${x + rx},$y Z"
    }

    private fun circleToPathData(element: Element): String {
        val cx = floatAttr(element, "cx") ?: 0f
        val cy = floatAttr(element, "cy") ?: 0f
        val r = floatAttr(element, "r") ?: return ""
        return circlePathData(cx, cy, r)
    }

    private fun circlePathData(cx: Float, cy: Float, r: Float): String {
        if (r <= 0f) return ""

        return "M ${cx - r},$cy " +
            "A $r,$r 0,1,0 ${cx + r},$cy " +
            "A $r,$r 0,1,0 ${cx - r},$cy Z"
    }

    private fun ellipseToPathData(element: Element): String {
        val cx = floatAttr(element, "cx") ?: 0f
        val cy = floatAttr(element, "cy") ?: 0f
        val rx = floatAttr(element, "rx") ?: return ""
        val ry = floatAttr(element, "ry") ?: return ""
        return ellipsePathData(cx, cy, rx, ry)
    }

    private fun ellipsePathData(cx: Float, cy: Float, rx: Float, ry: Float): String {
        if (rx <= 0f || ry <= 0f) return ""

        // Approximate the ellipse with four cubic Bézier curves.
        // This avoids relying on SVG arc commands in VectorDrawable output.
        val k = 0.55228475f

        val left = cx - rx
        val right = cx + rx
        val top = cy - ry
        val bottom = cy + ry
        val ox = rx * k
        val oy = ry * k

        return "M $cx,$top " +
            "C ${cx + ox},$top $right,${cy - oy} $right,$cy " +
            "C $right,${cy + oy} ${cx + ox},$bottom $cx,$bottom " +
            "C ${cx - ox},$bottom $left,${cy + oy} $left,$cy " +
            "C $left,${cy - oy} ${cx - ox},$top $cx,$top Z"
    }

    private fun lineToPathData(element: Element): String {
        val x1 = floatAttr(element, "x1") ?: 0f
        val y1 = floatAttr(element, "y1") ?: 0f
        val x2 = floatAttr(element, "x2") ?: 0f
        val y2 = floatAttr(element, "y2") ?: 0f

        return linePathData(x1, y1, x2, y2)
    }

    private fun linePathData(x1: Float, y1: Float, x2: Float, y2: Float): String {
        return "M $x1,$y1 L $x2,$y2"
    }

    private fun pointsToPathData(points: String, close: Boolean): String {
        val values = points
            .trim()
            .replace(",", " ")
            .split(Regex("\\s+"))
            .mapNotNull { it.toFloatOrNull() }

        if (values.size < 4) return ""

        val output = StringBuilder("M ${values[0]},${values[1]}")

        var i = 2
        while (i + 1 < values.size) {
            output.append(" L ${values[i]},${values[i + 1]}")
            i += 2
        }

        if (close) output.append(" Z")

        return output.toString()
    }

    private fun floatAttr(element: Element, name: String): Float? {
        return element.getAttribute(name)
            .replace("px", "")
            .replace("dp", "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
    }

    private fun floatAttrFromTag(tag: String, name: String): Float? {
        return attr(tag, name)
            ?.replace("px", "")
            ?.replace("dp", "")
            ?.trim()
            ?.toFloatOrNull()
    }

    private fun attr(tag: String, name: String): String? {
        val pattern = Regex("""\b$name\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
        return pattern.find(tag)?.groupValues?.getOrNull(2)
    }
}
