package com.example.svgvectorconverter

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal sealed class ParsedTransform {
    data class Translate(val x: Float, val y: Float) : ParsedTransform()
    data class Scale(val x: Float, val y: Float) : ParsedTransform()
    data class Rotate(val degrees: Float, val pivotX: Float?, val pivotY: Float?) : ParsedTransform()
    data class Matrix(val value: MatrixTransform) : ParsedTransform()

    fun hasVisibleEffect(): Boolean {
        return when (this) {
            is Translate -> x != 0f || y != 0f
            is Scale -> x != 1f || y != 1f
            is Rotate -> degrees != 0f
            is Matrix -> value.hasVisibleEffect()
        }
    }
}

internal data class CombinedTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val pivotX: Float? = null,
    val pivotY: Float? = null
) {
    fun hasVisibleEffect(): Boolean {
        return translateX != 0f ||
            translateY != 0f ||
            scaleX != 1f ||
            scaleY != 1f ||
            rotation != 0f
    }
}

internal data class MatrixTransform(
    val translateX: Float,
    val translateY: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotation: Float
) {
    fun hasVisibleEffect(): Boolean {
        return translateX != 0f ||
            translateY != 0f ||
            scaleX != 1f ||
            scaleY != 1f ||
            rotation != 0f
    }
}

internal object SvgTransformParser {
    var supportedMatrixTransforms: Int = 0
        private set

    var unsupportedMatrixTransforms: Int = 0
        private set

    fun resetMatrixStats() {
        supportedMatrixTransforms = 0
        unsupportedMatrixTransforms = 0
    }

    fun parseTransformList(transform: String?): List<ParsedTransform> {
        if (transform.isNullOrBlank()) return emptyList()

        return Regex("""([A-Za-z]+)\s*\(([^)]*)\)""")
            .findAll(transform)
            .mapNotNull { match ->
                val name = match.groupValues[1].lowercase(Locale.US)
                val nums = parseTransformNumbers(match.groupValues[2])

                when (name) {
                    "translate" -> {
                        if (nums.isEmpty()) null
                        else ParsedTransform.Translate(nums[0], nums.getOrNull(1) ?: 0f)
                    }
                    "scale" -> {
                        if (nums.isEmpty()) null
                        else ParsedTransform.Scale(nums[0], nums.getOrNull(1) ?: nums[0])
                    }
                    "rotate" -> {
                        if (nums.isEmpty()) null
                        else ParsedTransform.Rotate(nums[0], nums.getOrNull(1), nums.getOrNull(2))
                    }
                    "matrix" -> parseMatrixValues(nums)?.let { ParsedTransform.Matrix(it) }
                    else -> null
                }
            }
            .toList()
    }

    fun appendTransformGroupsStart(
        output: StringBuilder,
        transforms: List<ParsedTransform>,
        indent: String
    ): Pair<String, Int> {
        var currentIndent = indent
        var openedGroupCount = 0

        transforms.filter { it.hasVisibleEffect() }.forEach { transform ->
            when (transform) {
                is ParsedTransform.Translate -> {
                    output.appendLine("${currentIndent}<group")
                    if (transform.x != 0f) {
                        output.appendLine("""${currentIndent}    android:translateX="${transform.x}"""")
                    }
                    if (transform.y != 0f) {
                        output.appendLine("""${currentIndent}    android:translateY="${transform.y}"""")
                    }
                    output.appendLine("${currentIndent}>")
                }
                is ParsedTransform.Scale -> {
                    output.appendLine("${currentIndent}<group")
                    output.appendLine("""${currentIndent}    android:scaleX="${transform.x}"""")
                    output.appendLine("""${currentIndent}    android:scaleY="${transform.y}"""")
                    output.appendLine("${currentIndent}>")
                }
                is ParsedTransform.Rotate -> {
                    output.appendLine("${currentIndent}<group")
                    output.appendLine("""${currentIndent}    android:rotation="${transform.degrees}"""")
                    if (transform.pivotX != null && transform.pivotY != null) {
                        output.appendLine("""${currentIndent}    android:pivotX="${transform.pivotX}"""")
                        output.appendLine("""${currentIndent}    android:pivotY="${transform.pivotY}"""")
                    }
                    output.appendLine("${currentIndent}>")
                }
                is ParsedTransform.Matrix -> {
                    appendCombinedTransformGroupStart(
                        output,
                        CombinedTransform(
                            translateX = transform.value.translateX,
                            translateY = transform.value.translateY,
                            scaleX = transform.value.scaleX,
                            scaleY = transform.value.scaleY,
                            rotation = transform.value.rotation
                        ),
                        currentIndent
                    )
                }
            }

            currentIndent += "    "
            openedGroupCount++
        }

        return Pair(currentIndent, openedGroupCount)
    }

    fun closeGroups(output: StringBuilder, childIndent: String, groupCount: Int) {
        var currentIndent = childIndent
        repeat(groupCount) {
            currentIndent = currentIndent.dropLast(4)
            output.appendLine("${currentIndent}</group>")
        }
    }

    fun combineTransformList(transforms: List<ParsedTransform>): CombinedTransform? {
        if (transforms.isEmpty()) return null

        var translateX = 0f
        var translateY = 0f
        var scaleX = 1f
        var scaleY = 1f
        var rotation = 0f
        var pivotX: Float? = null
        var pivotY: Float? = null

        transforms.forEach { transform ->
            when (transform) {
                is ParsedTransform.Translate -> {
                    translateX += transform.x
                    translateY += transform.y
                }
                is ParsedTransform.Scale -> {
                    scaleX *= transform.x
                    scaleY *= transform.y
                }
                is ParsedTransform.Rotate -> {
                    rotation += transform.degrees
                    if (transform.pivotX != null && transform.pivotY != null) {
                        pivotX = transform.pivotX
                        pivotY = transform.pivotY
                    }
                }
                is ParsedTransform.Matrix -> {
                    val matrix = transform.value
                    translateX += matrix.translateX
                    translateY += matrix.translateY
                    scaleX *= matrix.scaleX
                    scaleY *= matrix.scaleY
                    rotation += matrix.rotation
                }
            }
        }

        return CombinedTransform(
            translateX = translateX,
            translateY = translateY,
            scaleX = scaleX,
            scaleY = scaleY,
            rotation = rotation,
            pivotX = pivotX,
            pivotY = pivotY
        ).takeIf { it.hasVisibleEffect() }
    }

    fun appendCombinedTransformGroupStart(
        output: StringBuilder,
        transform: CombinedTransform,
        indent: String
    ) {
        output.appendLine("${indent}<group")

        if (transform.translateX != 0f) {
            output.appendLine("""${indent}    android:translateX="${transform.translateX}"""")
        }

        if (transform.translateY != 0f) {
            output.appendLine("""${indent}    android:translateY="${transform.translateY}"""")
        }

        if (transform.scaleX != 1f || transform.scaleY != 1f) {
            output.appendLine("""${indent}    android:scaleX="${transform.scaleX}"""")
            output.appendLine("""${indent}    android:scaleY="${transform.scaleY}"""")
        }

        if (transform.rotation != 0f) {
            output.appendLine("""${indent}    android:rotation="${transform.rotation}"""")
            if (transform.pivotX != null && transform.pivotY != null) {
                output.appendLine("""${indent}    android:pivotX="${transform.pivotX}"""")
                output.appendLine("""${indent}    android:pivotY="${transform.pivotY}"""")
            }
        }

        output.appendLine("${indent}>")
    }

    private fun parseTransformNumbers(value: String): List<Float> {
        return value
            .trim()
            .split(Regex("[,\\s]+"))
            .filter { it.isNotBlank() }
            .mapNotNull { it.toFloatOrNull() }
    }

    private fun parseMatrixValues(nums: List<Float>): MatrixTransform? {
        if (nums.size != 6) {
            unsupportedMatrixTransforms++
            return null
        }

        val a = nums[0]
        val b = nums[1]
        val c = nums[2]
        val d = nums[3]
        val e = nums[4]
        val f = nums[5]

        val scaleX = sqrt(a * a + b * b)
        val scaleY = sqrt(c * c + d * d)

        if (scaleX == 0f || scaleY == 0f) {
            unsupportedMatrixTransforms++
            return null
        }

        val dot = a * c + b * d
        val hasSkew = abs(dot) > 0.0001f

        if (hasSkew) {
            unsupportedMatrixTransforms++
            return null
        }

        val rotation = atan2(b, a) * 180f / PI.toFloat()

        supportedMatrixTransforms++

        return MatrixTransform(
            translateX = e,
            translateY = f,
            scaleX = scaleX,
            scaleY = scaleY,
            rotation = rotation
        )
    }
}
