package com.example.svgvectorconverter

import android.graphics.*
import android.graphics.Color
import org.w3c.dom.Element
import org.w3c.dom.Node
import android.graphics.drawable.BitmapDrawable
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

object VectorPreviewRenderer {
    fun render(xml: String, width: Int, height: Int): Bitmap {
        val viewportWidth = attr(xml, "android:viewportWidth")?.toFloatOrNull() ?: 24f
        val viewportHeight = attr(xml, "android:viewportHeight")?.toFloatOrNull() ?: 24f

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

       val canvasScaleX = width / viewportWidth
val canvasScaleY = height / viewportHeight
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

        return bitmap
    }

private fun walkVectorNode(canvas: Canvas, node: Node, strokeScale: Float) {
        if (node.nodeType != Node.ELEMENT_NODE) return

        val element = node as Element
        val tagName = element.tagName.substringAfter(":")

        when (tagName) {
            "group" -> {
                canvas.save()

                val translateX =
                    element.getAttribute("android:translateX").toFloatOrNull() ?: 0f
                val translateY =
                    element.getAttribute("android:translateY").toFloatOrNull() ?: 0f
                val scaleX =
                    element.getAttribute("android:scaleX").toFloatOrNull() ?: 1f
                val scaleY =
                    element.getAttribute("android:scaleY").toFloatOrNull() ?: 1f
                val rotation =
                    element.getAttribute("android:rotation").toFloatOrNull() ?: 0f
                val pivotX =
                    element.getAttribute("android:pivotX").toFloatOrNull() ?: 0f
                val pivotY =
                    element.getAttribute("android:pivotY").toFloatOrNull() ?: 0f

                canvas.translate(translateX, translateY)
                if (rotation != 0f) {
                    canvas.rotate(rotation, pivotX, pivotY)
                }
                canvas.scale(scaleX, scaleY)

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

    val path = androidx.core.graphics.PathParser
        .createPathFromPathData(pathData)

    canvas.clipPath(path)
}

private fun drawPathElement(
    canvas: Canvas,
    element: Element,
    strokeScale: Float
) {
        val pathData = element.getAttribute("android:pathData")
        if (pathData.isBlank()) return

        val path = androidx.core.graphics.PathParser
            .createPathFromPathData(pathData)

        path.fillType = parsePathFillType(element.getAttribute("android:fillType"))

        val fillColor = element.getAttribute("android:fillColor")
        val strokeColor = element.getAttribute("android:strokeColor")
        val strokeWidth = element.getAttribute("android:strokeWidth")
            .toFloatOrNull()
            ?: 1f
        val fillAlpha = parsePreviewAlpha(element.getAttribute("android:fillAlpha"))
        val strokeAlpha = parsePreviewAlpha(element.getAttribute("android:strokeAlpha"))

        if (
            fillColor.isNotBlank() &&
            fillColor != "@android:color/transparent" &&
            fillColor != "none"
        ) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                val parsedColor = try {
                    Color.parseColor(fillColor)
                } catch (e: Exception) {
                    Color.TRANSPARENT
                }
                color = parsedColor
                alpha = (Color.alpha(parsedColor) * fillAlpha).toInt().coerceIn(0, 255)
            }

            canvas.drawPath(path, fillPaint)
        }

        if (
            strokeColor.isNotBlank() &&
            strokeColor != "@android:color/transparent" &&
            strokeColor != "none"
        ) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                val parsedColor = try {
                    Color.parseColor(strokeColor)
                } catch (e: Exception) {
                    Color.TRANSPARENT
                }
                color = parsedColor
                alpha = (Color.alpha(parsedColor) * strokeAlpha).toInt().coerceIn(0, 255)

this.strokeWidth = strokeWidth
                strokeCap = parseStrokeCap(element.getAttribute("android:strokeLineCap"))
                strokeJoin = parseStrokeJoin(element.getAttribute("android:strokeLineJoin"))
            }

            canvas.drawPath(path, strokePaint)
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
        return when (value?.trim()?.lowercase()) {
            "evenodd" -> Path.FillType.EVEN_ODD
            else -> Path.FillType.WINDING
        }
    }

    private fun parseStrokeCap(value: String?): Paint.Cap {
        return when (value?.trim()?.lowercase()) {
            "round" -> Paint.Cap.ROUND
            "square" -> Paint.Cap.SQUARE
            else -> Paint.Cap.BUTT
        }
    }

    private fun parseStrokeJoin(value: String?): Paint.Join {
        return when (value?.trim()?.lowercase()) {
            "round" -> Paint.Join.ROUND
            "bevel" -> Paint.Join.BEVEL
            else -> Paint.Join.MITER
        }
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }
}
