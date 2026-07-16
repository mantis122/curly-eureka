package com.example.svgvectorconverter

/**
 * Shared state and reporting bridge for dashed-stroke conversion.
 *
 * SvgTreeConverter pushes the effective transform compensation before an
 * element is emitted. SvgDashApproximator reads the current value while it
 * builds dashed path data. Keeping this state here prevents the emitter and
 * approximator from owning duplicate transform stacks.
 */
object SvgDashContext {
    data class TransformCompensation internal constructor(
        val enabled: Boolean,
        val scale: Float,
        val approximate: Boolean
    )

    private val transformCompensationStack = mutableListOf<TransformCompensation>()

    internal fun pushTransformCompensation(
        enabled: Boolean,
        scale: Float,
        approximate: Boolean
    ) {
        val safeScale = if (scale.isFinite() && scale > 0.0001f) scale else 1f
        transformCompensationStack.add(
            TransformCompensation(
                enabled = enabled,
                scale = safeScale,
                approximate = approximate
            )
        )
    }

    internal fun popTransformCompensation() {
        if (transformCompensationStack.isNotEmpty()) {
            transformCompensationStack.removeAt(transformCompensationStack.lastIndex)
        }
    }

    internal fun currentTransformCompensation(): TransformCompensation =
        transformCompensationStack.lastOrNull()
            ?: TransformCompensation(enabled = false, scale = 1f, approximate = false)

    internal fun reset() {
        transformCompensationStack.clear()
    }

    // Reporting remains centralized in SvgTreeConverter. These bridge methods
    // keep geometry code independent of the converter's counter fields.
    internal fun recordDashedStroke(didApproximate: Boolean) =
        SvgTreeConverter.recordDashedStroke(didApproximate)

    internal fun recordDashedStrokeInvalid(solidFallback: Boolean) =
        SvgTreeConverter.recordDashedStrokeInvalid(solidFallback)

    internal fun recordOddDashListDuplicated() =
        SvgTreeConverter.recordOddDashListDuplicated()

    internal fun recordInvalidDashOffsetFallback() =
        SvgTreeConverter.recordInvalidDashOffsetFallback()

    internal fun recordDashOffsetNormalized() =
        SvgTreeConverter.recordDashOffsetNormalized()

    internal fun recordDashTransformCompensation(approximate: Boolean) =
        SvgTreeConverter.recordDashTransformCompensation(approximate)
}
