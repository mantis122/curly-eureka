package com.example.svgvectorconverter

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Applies a full SVG affine transform directly to path coordinates.
 *
 * Android VectorDrawable does not support arbitrary affine matrices on <group>.
 * This helper flattens those transforms into path geometry. Elliptical arcs are
 * converted to cubic Bezier curves first, which lets skew/matrix transforms be
 * applied safely even when the source path contains A/a commands.
 */
internal object SvgPathDataTransformer {
    fun applyAffineTransform(pathData: String, matrix: AffineTransform): String? {
        val tokens = tokenize(pathData)
        if (tokens.isEmpty()) return pathData

        val output = StringBuilder()
        var index = 0
        var command: Char? = null
        var currentX = 0f
        var currentY = 0f
        var subPathStartX = 0f
        var subPathStartY = 0f
        var previousCommand: Char? = null
        var lastCubicControlX: Float? = null
        var lastCubicControlY: Float? = null
        var lastQuadControlX: Float? = null
        var lastQuadControlY: Float? = null

        fun hasNumberAt(i: Int): Boolean = i < tokens.size && !isCommand(tokens[i])
        fun readNumber(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()
        fun readFlag(): Boolean? {
            val value = readNumber() ?: return null
            return value != 0f
        }
        fun mappedPoint(x: Float, y: Float): Pair<Float, Float> = matrix.mapPoint(x, y)
        fun appendMoveTo(x: Float, y: Float) {
            val p = mappedPoint(x, y)
            output.append("M ")
                .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
        }
        fun appendLineTo(x: Float, y: Float) {
            val p = mappedPoint(x, y)
            output.append("L ")
                .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
        }
        fun appendCubic(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) {
            val p1 = mappedPoint(x1, y1)
            val p2 = mappedPoint(x2, y2)
            val p = mappedPoint(x, y)
            output.append("C ")
                .append(formatNumber(p1.first)).append(',').append(formatNumber(p1.second)).append(' ')
                .append(formatNumber(p2.first)).append(',').append(formatNumber(p2.second)).append(' ')
                .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
        }
        fun appendQuad(x1: Float, y1: Float, x: Float, y: Float) {
            val p1 = mappedPoint(x1, y1)
            val p = mappedPoint(x, y)
            output.append("Q ")
                .append(formatNumber(p1.first)).append(',').append(formatNumber(p1.second)).append(' ')
                .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
        }
        fun clearCurveMemory() {
            lastCubicControlX = null
            lastCubicControlY = null
            lastQuadControlX = null
            lastQuadControlY = null
        }

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                command = tokens[index++][0]
            } else if (command == null) {
                return null
            }

            val cmd = command ?: return null
            val absolute = cmd.isUpperCase()

            when (cmd.uppercaseChar()) {
                'M' -> {
                    var first = true
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        if (first) {
                            appendMoveTo(x, y)
                            subPathStartX = x
                            subPathStartY = y
                            first = false
                            previousCommand = 'M'
                        } else {
                            appendLineTo(x, y)
                            previousCommand = 'L'
                        }
                        currentX = x
                        currentY = y
                        clearCurveMemory()
                    }
                    command = if (absolute) 'L' else 'l'
                }
                'L' -> {
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendLineTo(x, y)
                        currentX = x
                        currentY = y
                        previousCommand = 'L'
                        clearCurveMemory()
                    }
                }
                'H' -> {
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        appendLineTo(x, currentY)
                        currentX = x
                        previousCommand = 'H'
                        clearCurveMemory()
                    }
                }
                'V' -> {
                    while (hasNumberAt(index)) {
                        val yRaw = readNumber() ?: return null
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendLineTo(currentX, y)
                        currentY = y
                        previousCommand = 'V'
                        clearCurveMemory()
                    }
                }
                'C' -> {
                    while (hasNumberAt(index)) {
                        val x1Raw = readNumber() ?: return null
                        val y1Raw = readNumber() ?: return null
                        val x2Raw = readNumber() ?: return null
                        val y2Raw = readNumber() ?: return null
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x1 = if (absolute) x1Raw else currentX + x1Raw
                        val y1 = if (absolute) y1Raw else currentY + y1Raw
                        val x2 = if (absolute) x2Raw else currentX + x2Raw
                        val y2 = if (absolute) y2Raw else currentY + y2Raw
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendCubic(x1, y1, x2, y2, x, y)
                        currentX = x
                        currentY = y
                        lastCubicControlX = x2
                        lastCubicControlY = y2
                        lastQuadControlX = null
                        lastQuadControlY = null
                        previousCommand = 'C'
                    }
                }
                'S' -> {
                    while (hasNumberAt(index)) {
                        val x2Raw = readNumber() ?: return null
                        val y2Raw = readNumber() ?: return null
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x1 = if (previousCommand == 'C' || previousCommand == 'S') {
                            2f * currentX - (lastCubicControlX ?: currentX)
                        } else currentX
                        val y1 = if (previousCommand == 'C' || previousCommand == 'S') {
                            2f * currentY - (lastCubicControlY ?: currentY)
                        } else currentY
                        val x2 = if (absolute) x2Raw else currentX + x2Raw
                        val y2 = if (absolute) y2Raw else currentY + y2Raw
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendCubic(x1, y1, x2, y2, x, y)
                        currentX = x
                        currentY = y
                        lastCubicControlX = x2
                        lastCubicControlY = y2
                        lastQuadControlX = null
                        lastQuadControlY = null
                        previousCommand = 'S'
                    }
                }
                'Q' -> {
                    while (hasNumberAt(index)) {
                        val x1Raw = readNumber() ?: return null
                        val y1Raw = readNumber() ?: return null
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x1 = if (absolute) x1Raw else currentX + x1Raw
                        val y1 = if (absolute) y1Raw else currentY + y1Raw
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendQuad(x1, y1, x, y)
                        currentX = x
                        currentY = y
                        lastQuadControlX = x1
                        lastQuadControlY = y1
                        lastCubicControlX = null
                        lastCubicControlY = null
                        previousCommand = 'Q'
                    }
                }
                'T' -> {
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x1 = if (previousCommand == 'Q' || previousCommand == 'T') {
                            2f * currentX - (lastQuadControlX ?: currentX)
                        } else currentX
                        val y1 = if (previousCommand == 'Q' || previousCommand == 'T') {
                            2f * currentY - (lastQuadControlY ?: currentY)
                        } else currentY
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendQuad(x1, y1, x, y)
                        currentX = x
                        currentY = y
                        lastQuadControlX = x1
                        lastQuadControlY = y1
                        lastCubicControlX = null
                        lastCubicControlY = null
                        previousCommand = 'T'
                    }
                }
                'A' -> {
                    while (hasNumberAt(index)) {
                        val rxRaw = readNumber() ?: return null
                        val ryRaw = readNumber() ?: return null
                        val xAxisRotation = readNumber() ?: return null
                        val largeArc = readFlag() ?: return null
                        val sweep = readFlag() ?: return null
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw

                        val cubicSegments = arcToCubicSegments(
                            startX = currentX,
                            startY = currentY,
                            rx = abs(rxRaw),
                            ry = abs(ryRaw),
                            xAxisRotationDegrees = xAxisRotation,
                            largeArc = largeArc,
                            sweep = sweep,
                            endX = x,
                            endY = y
                        )

                        if (cubicSegments.isEmpty()) {
                            appendLineTo(x, y)
                        } else {
                            cubicSegments.forEach { segment ->
                                appendCubic(
                                    segment.x1,
                                    segment.y1,
                                    segment.x2,
                                    segment.y2,
                                    segment.x,
                                    segment.y
                                )
                            }
                        }

                        currentX = x
                        currentY = y
                        previousCommand = 'A'
                        clearCurveMemory()
                    }
                }
                'Z' -> {
                    output.append('Z').append(' ')
                    currentX = subPathStartX
                    currentY = subPathStartY
                    previousCommand = 'Z'
                    clearCurveMemory()
                    command = null
                }
                else -> return null
            }
        }

        return output.toString().trim().takeIf { it.isNotBlank() }
    }

    private data class CubicSegment(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float
    )

    private fun arcToCubicSegments(
        startX: Float,
        startY: Float,
        rx: Float,
        ry: Float,
        xAxisRotationDegrees: Float,
        largeArc: Boolean,
        sweep: Boolean,
        endX: Float,
        endY: Float
    ): List<CubicSegment> {
        if (nearlyEqual(startX, endX) && nearlyEqual(startY, endY)) return emptyList()
        if (nearlyEqual(rx, 0f) || nearlyEqual(ry, 0f)) return emptyList()

        val phi = xAxisRotationDegrees.toDouble() * PI / 180.0
        val cosPhi = cos(phi)
        val sinPhi = sin(phi)

        val dx2 = (startX - endX).toDouble() / 2.0
        val dy2 = (startY - endY).toDouble() / 2.0
        val x1Prime = cosPhi * dx2 + sinPhi * dy2
        val y1Prime = -sinPhi * dx2 + cosPhi * dy2

        var rxD = abs(rx.toDouble())
        var ryD = abs(ry.toDouble())

        val lambda = (x1Prime * x1Prime) / (rxD * rxD) + (y1Prime * y1Prime) / (ryD * ryD)
        if (lambda > 1.0) {
            val scale = sqrt(lambda)
            rxD *= scale
            ryD *= scale
        }

        val rxSq = rxD * rxD
        val rySq = ryD * ryD
        val x1PrimeSq = x1Prime * x1Prime
        val y1PrimeSq = y1Prime * y1Prime

        val denominator = rxSq * y1PrimeSq + rySq * x1PrimeSq
        if (denominator == 0.0) return emptyList()

        val signValue = if (largeArc == sweep) -1.0 else 1.0
        val centerScale = signValue * sqrt(max(0.0, (rxSq * rySq - rxSq * y1PrimeSq - rySq * x1PrimeSq) / denominator))
        val cxPrime = centerScale * (rxD * y1Prime / ryD)
        val cyPrime = centerScale * (-ryD * x1Prime / rxD)

        val centerX = cosPhi * cxPrime - sinPhi * cyPrime + (startX + endX).toDouble() / 2.0
        val centerY = sinPhi * cxPrime + cosPhi * cyPrime + (startY + endY).toDouble() / 2.0

        fun vectorAngle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val length = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            if (length == 0.0) return 0.0
            val clamped = max(-1.0, min(1.0, dot / length))
            val angle = kotlin.math.acos(clamped)
            val cross = ux * vy - uy * vx
            return if (cross < 0.0) -angle else angle
        }

        val ux = (x1Prime - cxPrime) / rxD
        val uy = (y1Prime - cyPrime) / ryD
        val vx = (-x1Prime - cxPrime) / rxD
        val vy = (-y1Prime - cyPrime) / ryD

        val theta1 = vectorAngle(1.0, 0.0, ux, uy)
        var deltaTheta = vectorAngle(ux, uy, vx, vy)

        if (!sweep && deltaTheta > 0.0) {
            deltaTheta -= 2.0 * PI
        } else if (sweep && deltaTheta < 0.0) {
            deltaTheta += 2.0 * PI
        }

        val segmentCount = max(1, ceil(abs(deltaTheta) / (PI / 2.0)).toInt())
        val segmentDelta = deltaTheta / segmentCount
        val result = mutableListOf<CubicSegment>()

        for (i in 0 until segmentCount) {
            val t1 = theta1 + i * segmentDelta
            val t2 = t1 + segmentDelta
            result.add(arcSegmentToCubic(centerX, centerY, rxD, ryD, phi, t1, t2))
        }

        return result
    }

    private fun arcSegmentToCubic(
        centerX: Double,
        centerY: Double,
        rx: Double,
        ry: Double,
        phi: Double,
        theta1: Double,
        theta2: Double
    ): CubicSegment {
        val delta = theta2 - theta1
        val alpha = 4.0 / 3.0 * tan(delta / 4.0)

        val cosPhi = cos(phi)
        val sinPhi = sin(phi)
        val sinT1 = sin(theta1)
        val cosT1 = cos(theta1)
        val sinT2 = sin(theta2)
        val cosT2 = cos(theta2)

        fun mapUnit(x: Double, y: Double): Pair<Double, Double> {
            val px = centerX + rx * x * cosPhi - ry * y * sinPhi
            val py = centerY + rx * x * sinPhi + ry * y * cosPhi
            return Pair(px, py)
        }

        val p1UnitX = cosT1 - alpha * sinT1
        val p1UnitY = sinT1 + alpha * cosT1
        val p2UnitX = cosT2 + alpha * sinT2
        val p2UnitY = sinT2 - alpha * cosT2

        val control1 = mapUnit(p1UnitX, p1UnitY)
        val control2 = mapUnit(p2UnitX, p2UnitY)
        val end = mapUnit(cosT2, sinT2)

        return CubicSegment(
            x1 = control1.first.toFloat(),
            y1 = control1.second.toFloat(),
            x2 = control2.first.toFloat(),
            y2 = control2.second.toFloat(),
            x = end.first.toFloat(),
            y = end.second.toFloat()
        )
    }

    private fun tokenize(pathData: String): List<String> {
        return Regex("""[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\d+\.\d+|\d+|\.\d+)(?:[eE][-+]?\d+)?""")
            .findAll(pathData)
            .map { it.value }
            .toList()
    }

    private fun isCommand(token: String): Boolean {
        return token.length == 1 && token[0].isLetter()
    }

    private fun nearlyEqual(a: Float, b: Float, epsilon: Float = 0.0001f): Boolean {
        return abs(a - b) <= epsilon
    }

    private fun formatNumber(value: Float): String {
        val normalized = if (abs(value) < 0.0001f) 0f else value
        return String.format(Locale.US, "%.4f", normalized)
            .trimEnd('0')
            .trimEnd('.')
    }
}
