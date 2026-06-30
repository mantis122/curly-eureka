package com.example.svgvectorconverter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object BatchGalleryRenderer {
    fun render(
        context: Context,
        container: LinearLayout,
        results: List<BatchResult>
    ) {
        container.removeAllViews()

        container.addView(makeHeading(context))

        results.forEach { result ->
            container.addView(makeResultLabel(context, result))

            val xml = result.xml ?: return@forEach
            container.addView(
                makePreviewImage(context, xml),
                LinearLayout.LayoutParams(-1, 220)
            )
        }
    }

    private fun makeHeading(context: Context): TextView {
        return TextView(context).apply {
            text = "Batch Results"
            textSize = 20f
            setTextColor(Color.BLACK)
            setPadding(0, 24, 0, 12)
        }
    }

    private fun makeResultLabel(
        context: Context,
        result: BatchResult
    ): TextView {
        return TextView(context).apply {
            text = buildString {
                append(
                    when {
                        !result.success -> "✕ "
                        result.warningCount > 0 -> "⚠ "
                        else -> "✓ "
                    }
                )

                append(result.fileName)

                if (result.definitionPathCount > 0) {
                    append("  (${result.definitionPathCount} defs available)")
                }
            }

            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 16, 0, 6)
        }
    }

    private fun makePreviewImage(
        context: Context,
        xml: String
    ): ImageView {
        return ImageView(context).apply {
            setBackgroundColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            scaleType = ImageView.ScaleType.FIT_CENTER

            try {
                val bitmap = VectorPreviewRenderer.render(xml, 256, 256)
                setImageDrawable(BitmapDrawable(context.resources, bitmap))
            } catch (e: Exception) {
                setImageDrawable(null)
            }
        }
    }
}
