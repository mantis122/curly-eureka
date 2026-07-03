package com.example.svgvectorconverter

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal sealed class ParsedTransform {
    data class Translate(val x: Float, val y: Float) : ParsedTransform()
    data class Scale(val x: Float, val y: Float) : ParsedTransform()
    data class Rotate(val degrees: Float, val pivotX: Float?, val pivotY: Float?) : ParsedTransform()
    data class Matrix(val value: AffineTransform) : ParsedTransform()

    fun hasVisibleEffect(): Boolean {
        return when (this) {
            is Translate -> x != 0f || y != 0f
            is Scale -> x != 1f || y != 1f
            is Rotate -> degrees != 0f
            is Matrix -> value.hasVisibleEffect()
        }
    }
}

/**
 * SVG 2D affine matrix:
 *
 *     | a c e |
 *     | b d f |
 *     | 0 0 1 |
 *
 * Android VectorDrawable does not expose a raw matrix on <group>, so this file keeps
 * the full SVG matrix internally, then decomposes it into the group attributes Android
 * can represent: translate, scale, rotation, and optional pivot.
 */
internal data class AffineTransform(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val e: Float = 0f,
    val f: Float = 0f
) {
    fun hasVisibleEffect(): Boolean {
        return !nearlyEqual(a, 1f) ||
            !nearlyEqual(b, 0f) ||
            !nearlyEqual(c, 0f) ||
            !nearlyEqual(d, 1f) ||
            !nearlyEqual(e, 0f) ||
            !nearlyEqual(f, 0f)
    }

    /**
     * Returns this * other.
     *
     * This lets a transform list like:
     * translate(...) rotate(...) scale(...)
     *
     * compose into the same matrix as equivalent nested SVG groups in that order.
     */
    fun multiply(other: AffineTransform): AffineTransform {
        return AffineTransform(
            a = a * other.a + c * other.b,
            b = b * other.a + d * other.b,
            c = a * other.c + c * other.d,
            d = b * other.c + d * other.d,
            e = a * other.e + c * other.f + e,
            f = b * other.e + d * other.f + f
        )
    }

    fun toAndroidGroupTransform(preferredPivotX: Float? = null, preferredPivotY: Float? = null): AndroidGroupTransform? {
        val scaleX = sqrt(a * a + b * b)
        if (nearlyEqual(scaleX, 0f)) return null

        val rotation = atan2(b, a) * 180f / PI.toFloat()

        // Remove the rotation from the matrix. If Android can represent the result,
        // it should be diagonal scale only. Non-zero off-diagonal values mean skew.
        val cosValue = a / scaleX
        val sinValue = b / scaleX

        val unrotatedC = cosValue * c + sinValue * d
        val unrotatedD = -sinValue * c + cosValue * d

        if (!nearlyEqual(unrotatedC, 0f)) {
            return null
        }

        val determinant = a * d - b * c
        val scaleY = determinant / scaleX

        val hasRotationOrScale = !nearlyEqual(rotation, 0f) ||
            !nearlyEqual(scaleX, 1f) ||
            !nearlyEqual(scaleY, 1f)

        val pivotX = if (hasRotationOrScale) preferredPivotX ?: 0f else null
        val pivotY = if (hasRotationOrScale) preferredPivotY ?: 0f else null

        // Android VectorDrawable groups apply rotation/scale around pivot, then translate.
        // For a matrix with linear part A and translation (e, f), the translate needed
        // for a chosen pivot p is: t = e - p + A*p.
        val adjustedTranslateX = if (pivotX != null && pivotY != null) {
            e - pivotX + (a * pivotX + c * pivotY)
        } else {
            e
        }
        val adjustedTranslateY = if (pivotX != null && pivotY != null) {
            f - pivotY + (b * pivotX + d * pivotY)
        } else {
            f
        }

        return AndroidGroupTransform(
            translateX = normalizeZero(adjustedTranslateX),
            translateY = normalizeZero(adjustedTranslateY),
            scaleX = normalizeZero(scaleX),
            scaleY = normalizeZero(scaleY),
            rotation = normalizeZero(rotation),
            pivotX = pivotX?.let { normalizeZero(it) },
            pivotY = pivotY?.let { normalizeZero(it) }
        ).takeIf { it.hasVisibleEffect() }
    }

    companion object {
        fun identity(): AffineTransform = AffineTransform()

        fun translation(x: Float, y: Float): AffineTransform {
            return AffineTransform(e = x, f = y)
        }

        fun scale(x: Float, y: Float): AffineTransform {
            return AffineTransform(a = x, d = y)
        }

        fun rotation(degrees: Float): AffineTransform {
            val radians = degrees * PI.toFloat() / 180f
            val cosValue = cos(radians)
            val sinValue = sin(radians)

            return AffineTransform(
                a = cosValue,
                b = sinValue,
                c = -sinValue,
                d = cosValue
            )
        }

        fun rotation(degrees: Float, pivotX: Float, pivotY: Float): AffineTransform {
            return translation(pivotX, pivotY)
                .multiply(rotation(degrees))
                .multiply(translation(-pivotX, -pivotY))
        }
    }
}

internal data class AndroidGroupTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val pivotX: Float? = null,
    val pivotY: Float? = null
) {
    fun hasVisibleEffect(): Boolean {
        return !nearlyEqual(translateX, 0f) ||
            !nearlyEqual(translateY, 0f) ||
            !nearlyEqual(scaleX, 1f) ||
            !nearlyEqual(scaleY, 1f) ||
            !nearlyEqual(rotation, 0f)
    }
}

// Compatibility alias so SvgTreeConverter does not need to change everywhere at once.
internal typealias CombinedTransform = AndroidGroupTransform

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
        val combinedTransform = combineTransformList(transforms) ?: return Pair(indent, 0)

        appendCombinedTransformGroupStart(output, combinedTransform, indent)
        return Pair(indent + "    ", 1)
    }

    fun closeGroups(output: StringBuilder, childIndent: String, groupCount: Int) {
        var currentIndent = childIndent
        repeat(groupCount) {
            currentIndent = currentIndent.dropLast(4)
            output.appendLine("${currentIndent}</group>")
        }
    }

    fun combineTransformList(transforms: List<ParsedTransform>): CombinedTransform? {
        val matrix = combineTransformListToMatrix(transforms) ?: return null
        val preferredPivot = transforms
            .filterIsInstance<ParsedTransform.Rotate>()
            .lastOrNull { it.pivotX != null && it.pivotY != null }
        val androidTransform = matrix.toAndroidGroupTransform(
            preferredPivotX = preferredPivot?.pivotX,
            preferredPivotY = preferredPivot?.pivotY
        )

        if (androidTransform == null) {
            unsupportedMatrixTransforms++
            return null
        }

        if (transforms.any { it is ParsedTransform.Matrix }) {
            supportedMatrixTransforms++
        }

        return androidTransform
    }

    fun combineTransformListToMatrix(transforms: List<ParsedTransform>): AffineTransform? {
        if (transforms.isEmpty()) return null

        var current = AffineTransform.identity()

        transforms.forEach { transform ->
            val next = when (transform) {
                is ParsedTransform.Translate -> AffineTransform.translation(transform.x, transform.y)
                is ParsedTransform.Scale -> AffineTransform.scale(transform.x, transform.y)
                is ParsedTransform.Rotate -> {
                    if (transform.pivotX != null && transform.pivotY != null) {
                        AffineTransform.rotation(transform.degrees, transform.pivotX, transform.pivotY)
                    } else {
                        AffineTransform.rotation(transform.degrees)
                    }
                }
                is ParsedTransform.Matrix -> transform.value
            }

            // Compose in SVG list order, matching equivalent nested groups.
            current = current.multiply(next)
        }

        return current.takeIf { it.hasVisibleEffect() }
    }

    fun appendCombinedTransformGroupStart(
        output: StringBuilder,
        transform: CombinedTransform,
        indent: String
    ) {
        output.appendLine("${indent}<group")

        if (!nearlyEqual(transform.translateX, 0f)) {
            output.appendLine("""${indent}    android:translateX="${formatFloat(transform.translateX)}"""")
        }

        if (!nearlyEqual(transform.translateY, 0f)) {
            output.appendLine("""${indent}    android:translateY="${formatFloat(transform.translateY)}"""")
        }

        if (!nearlyEqual(transform.scaleX, 1f) || !nearlyEqual(transform.scaleY, 1f)) {
            output.appendLine("""${indent}    android:scaleX="${formatFloat(transform.scaleX)}"""")
            output.appendLine("""${indent}    android:scaleY="${formatFloat(transform.scaleY)}"""")
        }

        if (!nearlyEqual(transform.rotation, 0f)) {
            output.appendLine("""${indent}    android:rotation="${formatFloat(transform.rotation)}"""")
            if (transform.pivotX != null && transform.pivotY != null) {
                output.appendLine("""${indent}    android:pivotX="${formatFloat(transform.pivotX)}"""")
                output.appendLine("""${indent}    android:pivotY="${formatFloat(transform.pivotY)}"""")
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

    private fun parseMatrixValues(nums: List<Float>): AffineTransform? {
        if (nums.size != 6) {
            unsupportedMatrixTransforms++
            return null
        }

        return AffineTransform(
            a = nums[0],
            b = nums[1],
            c = nums[2],
            d = nums[3],
            e = nums[4],
            f = nums[5]
        )
    }
}

private fun nearlyEqual(a: Float, b: Float, epsilon: Float = 0.0001f): Boolean {
    return abs(a - b) <= epsilon
}

private fun normalizeZero(value: Float): Float {
    return if (nearlyEqual(value, 0f)) 0f else value
}

private fun formatFloat(value: Float): String {
    val normalized = normalizeZero(value)
    val snapped = when {
        nearlyEqual(normalized, normalized.toInt().toFloat()) -> normalized.toInt().toFloat()
        nearlyEqual(normalized, (normalized * 1000f).toInt() / 1000f) -> (normalized * 1000f).toInt() / 1000f
        else -> normalized
    }

    return String.format(Locale.US, "%.4f", snapped)
        .trimEnd('0')
        .trimEnd('.')
}
