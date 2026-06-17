package com.example.svgvectorconverter

import android.os.*
import android.content.*
import android.graphics.Color
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var outputBox: EditText
    private var convertedXml = ""

    private val openSvg = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val svg = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: ""

            convertedXml = SvgToVectorConverter.convert(svg)
            outputBox.setText(convertedXml)
        }
    }

    private val saveXml = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use {
                it.write(convertedXml.toByteArray())
            }
            toast("Saved")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(250, 248, 240))
        }

        val title = TextView(this).apply {
            text = "SVG to Android Vector"
            textSize = 24f
            setTextColor(Color.BLACK)
        }

        val openButton = Button(this).apply {
            text = "Open SVG"
            setOnClickListener {
                openSvg.launch(arrayOf("image/svg+xml", "text/xml", "text/plain"))
            }
        }

        val copyButton = Button(this).apply {
            text = "Copy XML"
            setOnClickListener {
                if (convertedXml.isBlank()) {
                    toast("Nothing to copy yet")
                } else {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("vector.xml", convertedXml))
                    toast("Copied")
                }
            }
        }

        val saveButton = Button(this).apply {
            text = "Save XML"
            setOnClickListener {
                if (convertedXml.isBlank()) {
                    toast("Nothing to save yet")
                } else {
                    saveXml.launch("converted_vector.xml")
                }
            }
        }

        outputBox = EditText(this).apply {
            hint = "Converted VectorDrawable XML will appear here"
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
            textSize = 12f
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            setHorizontallyScrolling(true)
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(openButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(copyButton, LinearLayout.LayoutParams(0, -2, 1f))
            addView(saveButton, LinearLayout.LayoutParams(0, -2, 1f))
        }

        root.addView(title)
        root.addView(buttonRow)
        root.addView(outputBox, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

object SvgToVectorConverter {
    fun convert(svg: String): String {
        val viewBox = Regex("""viewBox=["']([^"']+)["']""")
            .find(svg)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.split(Regex("[,\\s]+"))
            ?.mapNotNull { it.toFloatOrNull() }

        val viewportWidth = viewBox?.getOrNull(2) ?: 24f
        val viewportHeight = viewBox?.getOrNull(3) ?: 24f

        val paths = Regex("""<path\b[^>]*>""")
            .findAll(svg)
            .mapNotNull { match ->
                val tag = match.value
                val d = attr(tag, "d") ?: return@mapNotNull null
                val fill = attr(tag, "fill") ?: "#000000"
                val stroke = attr(tag, "stroke")
                val strokeWidth = attr(tag, "stroke-width")

                buildString {
                    appendLine("    <path")
                    appendLine("""        android:pathData="${escapeXml(d)}"""")
                    if (fill != "none") {
                        appendLine("""        android:fillColor="$fill"""")
                    } else {
                        appendLine("""        android:fillColor="@android:color/transparent"""")
                    }
                    if (stroke != null && stroke != "none") {
                        appendLine("""        android:strokeColor="$stroke"""")
                        appendLine("""        android:strokeWidth="${strokeWidth ?: "1"}"""")
                    }
                    appendLine("    />")
                }
            }
            .joinToString("\n")

        return """
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${viewportWidth.toInt()}dp"
    android:height="${viewportHeight.toInt()}dp"
    android:viewportWidth="$viewportWidth"
    android:viewportHeight="$viewportHeight">

$paths
</vector>
        """.trim()
    }

    private fun attr(tag: String, name: String): String? {
        return Regex("""\b$name=["']([^"']*)["']""")
            .find(tag)
            ?.groupValues
            ?.get(1)
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
