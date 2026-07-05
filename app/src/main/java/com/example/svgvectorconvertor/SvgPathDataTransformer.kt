package com.example.svgvectorconverter

import java.util.Locale

/**
 * Applies a full SVG affine transform directly to path coordinates.
 *
 * This is used when a transform cannot be represented by Android VectorDrawable
 * <group> attributes, for example skewX/skewY or arbitrary matrix(...).
 *
 * Supported path commands: M, L, H, V, C, S, Q, T, Z, with relative variants.
 * Arc commands are deliberately rejected for matrix-flattening because a skewed
 * SVG arc cannot be represented exactly as a VectorDrawable arc command without
 * converting the arc to cubic curves first.
 */
internal object SvgPathDataTransformer {
    fun applyAffineTransform(pathData: String, matrix: AffineTransform): String? {
        val tokens = tokenize(pathData)
        if (tokens.isEmpty()) return pathData
        if (tokens.any { it.equals("A", true) }) return null

        val output = StringBuilder()
        var index = 0
        var command: Char? = null
        var currentX = 0f
        var currentY = 0f
        var subPathStartX = 0f
        var subPathStartY = 0f

        fun hasNumberAt(i: Int): Boolean = i < tokens.size && !isCommand(tokens[i])
        fun readNumber(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()
        fun appendPoint(commandName: Char, x: Float, y: Float) {
            val mapped = matrix.mapPoint(x, y)
            output.append(commandName)
                .append(' ')
                .append(formatNumber(mapped.first))
                .append(',')
                .append(formatNumber(mapped.second))
                .append(' ')
        }

        while (index < tokens.size) {
            if (isCommand(tokens[index])) {
                command = tokens[index++][0]
            } else if (command == null) {
                return null
            }

            val cmd = command
            val absolute = cmd.isUpperCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var first = true
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendPoint(if (first) 'M' else 'L', x, y)
                        currentX = x
                        currentY = y
                        if (first) {
                            subPathStartX = x
                            subPathStartY = y
                            first = false
                        }
                    }
                    command = if (absolute) 'L' else 'l'
                }
                'L' -> {
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendPoint('L', x, y)
                        currentX = x
                        currentY = y
                    }
                }
                'H' -> {
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        appendPoint('L', x, currentY)
                        currentX = x
                    }
                }
                'V' -> {
                    while (hasNumberAt(index)) {
                        val yRaw = readNumber() ?: return null
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendPoint('L', currentX, y)
                        currentY = y
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
                        val p1 = matrix.mapPoint(x1, y1)
                        val p2 = matrix.mapPoint(x2, y2)
                        val p = matrix.mapPoint(x, y)
                        output.append("C ")
                            .append(formatNumber(p1.first)).append(',').append(formatNumber(p1.second)).append(' ')
                            .append(formatNumber(p2.first)).append(',').append(formatNumber(p2.second)).append(' ')
                            .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
                        currentX = x
                        currentY = y
                    }
                }
                'S' -> {
                    while (hasNumberAt(index)) {
                        val x2Raw = readNumber() ?: return null
                        val y2Raw = readNumber() ?: return null
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x2 = if (absolute) x2Raw else currentX + x2Raw
                        val y2 = if (absolute) y2Raw else currentY + y2Raw
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        val p2 = matrix.mapPoint(x2, y2)
                        val p = matrix.mapPoint(x, y)
                        output.append("S ")
                            .append(formatNumber(p2.first)).append(',').append(formatNumber(p2.second)).append(' ')
                            .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
                        currentX = x
                        currentY = y
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
                        val p1 = matrix.mapPoint(x1, y1)
                        val p = matrix.mapPoint(x, y)
                        output.append("Q ")
                            .append(formatNumber(p1.first)).append(',').append(formatNumber(p1.second)).append(' ')
                            .append(formatNumber(p.first)).append(',').append(formatNumber(p.second)).append(' ')
                        currentX = x
                        currentY = y
                    }
                }
                'T' -> {
                    while (hasNumberAt(index)) {
                        val xRaw = readNumber() ?: return null
                        val yRaw = readNumber() ?: return null
                        val x = if (absolute) xRaw else currentX + xRaw
                        val y = if (absolute) yRaw else currentY + yRaw
                        appendPoint('T', x, y)
                        currentX = x
                        currentY = y
                    }
                }
                'Z' -> {
                    output.append('Z').append(' ')
                    currentX = subPathStartX
                    currentY = subPathStartY
                    command = null
                }
                else -> return null
            }
        }

        return output.toString().trim().takeIf { it.isNotBlank() }
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

    private fun formatNumber(value: Float): String {
        val normalized = if (kotlin.math.abs(value) < 0.0001f) 0f else value
        return String.format(Locale.US, "%.4f", normalized)
            .trimEnd('0')
            .trimEnd('.')
    }
}
