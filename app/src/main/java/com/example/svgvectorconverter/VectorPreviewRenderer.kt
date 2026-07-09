package com.example.svgvectorconverter

import android.graphics.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import kotlin.math.max

object VectorPreviewRenderer {
    private const val PREVIEW_SUPERSAMPLE = 2
    private const val MAX_SUPERSAMPLED_EDGE = 2048

    fun render(xml: String, width: Int, height: Int): Bitmap {
        val viewportWidth = attr(xml, "android:viewportWidth")?.toFloatOrNull() ?: 24f
        val viewportHeight = attr(xml, "android:viewportHeight")?.toFloatOrNull() ?: 24f

        val sampleScale = if (max(width, height) * PREVIEW_SUPERSAMPLE <= MAX_SUPERSAMPLED_EDGE) {
            PREVIEW_SUPERSAMPLE
        } else {
            1
        }

        val renderWidth = (width * sampleScale).coerceAtLeast(1)
        val renderHeight = (height * sampleScale).coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val canvasScaleX = renderWidth / viewportWidth
        val canvasScaleY = renderHeight / viewportHeight
        val strokeScale = minOf(canvasScaleX, canvasScaleY)

        canvas.scale(canvasScaleX, canvasScaleY)

        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml)))

        walkVectorNode(canvas, document.documentElement, strokeScale)

        if (sampleScale == 1) return bitmap

        val finalBitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val finalCanvas = Canvas(finalBitmap)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
        finalCanvas.drawBitmap(
            bitmap,
            null,
            Rect(0, 0, finalBitmap.width, finalBitmap.height),
            bitmapPaint
        )
        bitmap.recycle()
        return finalBitmap
    }

    private fun walkVectorNode(canvas: Canvas, node: Node, strokeScale: Float) {
        if (node.nodeType != Node.ELEMENT_NODE) return

        val element = node as Element
        val tagName = element.tagName.substringAfter(":")

        when (tagName) {
            "group" -> {
                canvas.save()

                val translateX = element.getAttribute("android:translateX").toFloatOrNull() ?: 0f
                val translateY = element.getAttribute("android:translateY").toFloatOrNull() ?: 0f
                val scaleX = element.getAttribute("android:scaleX").toFloatOrNull() ?: 1f
                val scaleY = element.getAttribute("android:scaleY").toFloatOrNull() ?: 1f
                val rotation = element.getAttribute("android:rotation").toFloatOrNull() ?: 0f
                val pivotX = element.getAttribute("android:pivotX").toFloatOrNull() ?: 0f
                val pivotY = element.getAttribute("android:pivotY").toFloatOrNull() ?: 0f

                val matrix = Matrix().apply {
                    postTranslate(-pivotX, -pivotY)
                    postScale(scaleX, scaleY)
                    postRotate(rotation)
                    postTranslate(translateX + pivotX, translateY + pivotY)
                }

                canvas.concat(matrix)

                val children = element.childNodes
                for (i in 0 until children.length) {
                    walkVectorNode(canvas, children.item(i), strokeScale)
                }

                canvas.restore()
            }

            "clip-path" -> {
                applyClipPathElement(canvas, element)
            }

            "path" -> {
                drawPathElement(canvas, element, strokeScale)
            }

            else -> {
                val children = element.childNodes
                for (i in 0 until children.length) {
                    walkVectorNode(canvas, children.item(i), strokeScale)
                }
            }
        }
    }

    private fun applyClipPathElement(canvas: Canvas, element: Element) {
        val pathData = element.getAttribute("android:pathData")
        if (pathData.isBlank()) return

        val path = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
        path.fillType = parsePathFillType(element.getAttribute("android:fillType"))
        canvas.clipPath(path)
    }

    private fun drawPathElement(
        canvas: Canvas,
        element: Element,
        strokeScale: Float
    ) {
        val pathData = element.getAttribute("android:pathData")
        if (pathData.isBlank()) return

        val path = androidx.core.graphics.PathParser.createPathFromPathData(pathData)
        path.fillType = parsePathFillType(element.getAttribute("android:fillType"))

        val fillColor = element.getAttribute("android:fillColor")
        val strokeColor = element.getAttribute("android:strokeColor")
        val fillGradient = previewGradientShader(element, "android:fillColor")
        val strokeGradient = previewGradientShader(element, "android:strokeColor")
        val strokeWidth = element.getAttribute("android:strokeWidth").toFloatOrNull() ?: 1f
        val fillAlpha = parsePreviewAlpha(element.getAttribute("android:fillAlpha"))
        val strokeAlpha = parsePreviewAlpha(element.getAttribute("android:strokeAlpha"))

        if (fillGradient != null || isDrawablePreviewColor(fillColor)) {
            val fillPaint = previewPaint().apply {
                style = Paint.Style.FILL
                if (fillGradient != null) {
                    shader = fillGradient
                    alpha = (255f * fillAlpha).toInt().coerceIn(0, 255)
                } else {
                    val parsedColor = parsePreviewColor(fillColor)
                    color = parsedColor
                    alpha = (Color.alpha(parsedColor) * fillAlpha).toInt().coerceIn(0, 255)
                }
            }

            canvas.drawPath(path, fillPaint)
        }

        if (strokeGradient != null || isDrawablePreviewColor(strokeColor)) {
            val strokePaint = previewPaint().apply {
                style = Paint.Style.STROKE
                if (strokeGradient != null) {
                    shader = strokeGradient
                    alpha = (255f * strokeAlpha).toInt().coerceIn(0, 255)
                } else {
                    val parsedColor = parsePreviewColor(strokeColor)
                    color = parsedColor
                    alpha = (Color.alpha(parsedColor) * strokeAlpha).toInt().coerceIn(0, 255)
                }

                this.strokeWidth = strokeWidth
                strokeCap = parseStrokeCap(element.getAttribute("android:strokeLineCap"))
                strokeJoin = parseStrokeJoin(element.getAttribute("android:strokeLineJoin"))
                strokeMiter = element.getAttribute("android:strokeMiterLimit")
                    .toFloatOrNull()
                    ?.takeIf { it > 0f }
                    ?: strokeMiter
            }

            canvas.drawPath(path, strokePaint)
        }
    }

    private fun previewPaint(): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isAntiAlias = true
            isDither = true
            isFilterBitmap = true
        }
    }

    private fun previewGradientShader(element: Element, attrName: String): Shader? {
        val attrElement = aaptAttrElement(element, attrName) ?: return null
        val gradient = firstChildElement(attrElement, "gradient") ?: return null
        val type = gradient.androidAttr("type").ifBlank { "linear" }.lowercase(Locale.US)
        val colorsAndOffsets = gradientColorsAndOffsets(gradient)
        val colors = colorsAndOffsets.first
        val offsets = colorsAndOffsets.second
        if (colors.isEmpty()) return null

        val tileMode = parseTileMode(gradient.androidAttr("tileMode"))
        return if (type == "radial") {
            val cx = gradient.androidAttr("centerX").toFloatOrNull() ?: 0f
            val cy = gradient.androidAttr("centerY").toFloatOrNull() ?: 0f
            val radius = gradient.androidAttr("gradientRadius").toFloatOrNull()?.coerceAtLeast(0.001f) ?: 1f
            RadialGradient(cx, cy, radius, colors.toIntArray(), offsets, tileMode)
        } else {
            val startX = gradient.androidAttr("startX").toFloatOrNull() ?: 0f
            val startY = gradient.androidAttr("startY").toFloatOrNull() ?: 0f
            val endX = gradient.androidAttr("endX").toFloatOrNull() ?: startX
            val endY = gradient.androidAttr("endY").toFloatOrNull() ?: startY
            LinearGradient(startX, startY, endX, endY, colors.toIntArray(), offsets, tileMode)
        }
    }

    private fun aaptAttrElement(element: Element, attrName: String): Element? {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val attrElement = child as Element
            if (attrElement.tagName.substringAfter(":") != "attr") continue
            if (attrElement.getAttribute("name") == attrName) return attrElement
        }
        return null
    }

    private fun gradientColorsAndOffsets(gradient: Element): Pair<List<Int>, FloatArray?> {
        val itemColors = mutableListOf<Int>()
        val itemOffsets = mutableListOf<Float?>()
        val gradientChildren = gradient.childNodes

        for (j in 0 until gradientChildren.length) {
            val itemNode = gradientChildren.item(j)
            if (itemNode.nodeType != Node.ELEMENT_NODE) continue
            val item = itemNode as Element
            if (item.tagName.substringAfter(":") != "item") continue

            val color = item.androidAttr("color")
            val parsedColor = parsePreviewColorOrNull(color) ?: continue
            val offset = item.androidAttr("offset").toFloatOrNull()?.coerceIn(0f, 1f)

            itemColors.add(parsedColor)
            itemOffsets.add(offset)
        }

        if (itemColors.isNotEmpty()) {
            if (itemColors.size == 1) {
                return listOf(itemColors[0], itemColors[0]) to floatArrayOf(0f, 1f)
            }
            return normalizeGradientStops(itemColors, itemOffsets)
        }

        val start = parsePreviewColorOrNull(gradient.androidAttr("startColor"))
        val center = parsePreviewColorOrNull(gradient.androidAttr("centerColor"))
        val end = parsePreviewColorOrNull(gradient.androidAttr("endColor"))

        return when {
            start != null && center != null && end != null -> {
                listOf(start, center, end) to floatArrayOf(0f, 0.5f, 1f)
            }
            start != null && end != null -> {
                listOf(start, end) to floatArrayOf(0f, 1f)
            }
            start != null -> listOf(start, start) to floatArrayOf(0f, 1f)
            end != null -> listOf(end, end) to floatArrayOf(0f, 1f)
            else -> emptyList<Int>() to null
        }
    }

    private fun normalizeGradientStops(
        colors: List<Int>,
        nullableOffsets: List<Float?>
    ): Pair<List<Int>, FloatArray> {
        val offsets = MutableList(colors.size) { index ->
            nullableOffsets.getOrNull(index)
        }

        if (offsets.first() == null) offsets[0] = 0f
        if (offsets.last() == null) offsets[offsets.lastIndex] = 1f

        var index = 0
        while (index < offsets.size) {
            if (offsets[index] != null) {
                index++
                continue
            }

            val startIndex = index - 1
            var endIndex = index
            while (endIndex < offsets.size && offsets[endIndex] == null) {
                endIndex++
            }

            val startOffset = offsets[startIndex] ?: 0f
            val endOffset = offsets.getOrNull(endIndex) ?: 1f
            val gap = endIndex - startIndex
            for (fillIndex in index until endIndex) {
                val fraction = (fillIndex - startIndex).toFloat() / gap.toFloat()
                offsets[fillIndex] = startOffset + ((endOffset - startOffset) * fraction)
            }
            index = endIndex
        }

        val sortedStops = colors
            .zip(offsets.map { it ?: 0f })
            .map { it.first to it.second.coerceIn(0f, 1f) }
            .sortedBy { it.second }

        val sortedColors = sortedStops.map { it.first }
        val sortedOffsets = sortedStops.map { it.second }.toFloatArray()
        return sortedColors to sortedOffsets
    }

    private fun firstChildElement(element: Element, localName: String): Element? {
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val childElement = child as Element
            if (childElement.tagName.substringAfter(":") == localName) return childElement
        }
        return null
    }

    private fun isDrawablePreviewColor(value: String?): Boolean {
        val raw = value?.trim().orEmpty()
        return raw.isNotBlank() &&
            !raw.equals("none", ignoreCase = true) &&
            raw != "@android:color/transparent" &&
            parsePreviewColorOrNull(raw) != null
    }

    private fun parsePreviewColor(value: String?): Int {
        return parsePreviewColorOrNull(value) ?: Color.TRANSPARENT
    }

    private fun parsePreviewColorOrNull(value: String?): Int? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return try {
            Color.parseColor(raw)
        } catch (_: Exception) {
            null
        }
    }

    private fun Element.androidAttr(localName: String): String {
        return getAttribute("android:$localName").ifBlank { getAttribute(localName) }.trim()
    }

    private fun parseTileMode(value: String?): Shader.TileMode {
        return when (value?.trim()?.lowercase(Locale.US)) {
            "repeat" -> Shader.TileMode.REPEAT
            "mirror" -> Shader.TileMode.MIRROR
            else -> Shader.TileMode.CLAMP
        }
    }

    private fun parsePreviewAlpha(value: String?): Float {
        return value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
            ?.coerceIn(0f, 1f)
            ?: 1f
    }

    private fun parsePathFillType(value: String?): Path.FillType {
        return when (value?.trim()?.lowercase(Locale.US)) {
            "evenodd" -> Path.FillType.EVEN_ODD
            else -> Path.FillType.WINDING
        }
    }

    private fun parseStrokeCap(value: String?): Paint.Cap {
        return when (value?.trim()?.lowercase(Locale.US)) {
            "round" -> Paint.Cap.ROUND
            "square" -> Paint.Cap.SQUARE
            else -> Paint.Cap.BUTT
        }
    }

    private fun parseStrokeJoin(value: String?): Paint.Join {
        return when (value?.trim()?.lowercase(Locale.US)) {
            "round" -> Paint.Join.ROUND
            "bevel" -> Paint.Join.BEVEL
            else -> Paint.Join.MITER
        }
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=[\"']([^\"']*)[\"']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }
}
