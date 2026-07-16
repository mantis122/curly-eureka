package com.example.svgvectorconverter

import org.w3c.dom.Element
import java.util.Locale

/**
 * Converts SVG dashed strokes into solid VectorDrawable path subpaths.
 *
 * This object owns dash parsing, inherited dash properties, path/shape sampling,
 * dash-offset normalization, transform compensation, and dashed path generation.
 * Reporting remains centralized in [SvgTreeConverter].
 */
object SvgDashApproximator {
    private fun floatAttr(element: Element, name: String): Float? {
        return element.getAttribute(name)
            .replace("px", "")
            .replace("dp", "")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toFloatOrNull()
    }

    private data class DashPoint(val x: Float, val y: Float, val startsNewSubpath: Boolean = false)

    private sealed class DashArrayParseResult {
        data object None : DashArrayParseResult()
        data class Valid(val pattern: List<Float>, val duplicatedOddList: Boolean) : DashArrayParseResult()
        data object Invalid : DashArrayParseResult()
    }

    internal fun approximateStroke(
        element: Element,
        style: String?,
        sourceTag: String?,
        pathData: String,
        stroke: String?,
        strokeLineCap: String?
    ): String? {
        if (sourceTag !in setOf("line", "polyline", "polygon", "rect", "circle", "ellipse", "path")) return null
        if (stroke.isNullOrBlank() || stroke.equals("none", ignoreCase = true)) return null

        val dashArrayValue = inheritedStyleOrAttribute(element, style, "stroke-dasharray") ?: return null
        when (val parsed = parseDashArrayStrict(dashArrayValue)) {
            DashArrayParseResult.None -> return null
            DashArrayParseResult.Invalid -> {
                SvgDashContext.recordDashedStrokeInvalid(solidFallback = true)
                return null
            }
            is DashArrayParseResult.Valid -> {
                if (parsed.duplicatedOddList) SvgDashContext.recordOddDashListDuplicated()

                val dashOffsetValue = inheritedStyleOrAttribute(element, style, "stroke-dashoffset")
                val rawDashOffset = when (val parsedOffset = parseSingleLengthStrict(dashOffsetValue ?: "0")) {
                    null -> {
                        SvgDashContext.recordInvalidDashOffsetFallback()
                        0f
                    }
                    else -> parsedOffset
                }

                val dashCompensation = SvgDashContext.currentTransformCompensation()
                val effectivePattern = if (dashCompensation.enabled) {
                    parsed.pattern.map { it / dashCompensation.scale }
                } else parsed.pattern
                val effectiveDashOffset = rawDashOffset / if (dashCompensation.enabled) dashCompensation.scale else 1f
                val patternLength = effectivePattern.sum()
                if (patternLength > 0.0001f && (effectiveDashOffset < 0f || kotlin.math.abs(effectiveDashOffset) >= patternLength)) {
                    SvgDashContext.recordDashOffsetNormalized()
                }

                val dashPoints = dashApproximationPoints(element, sourceTag, pathData)
                if (dashPoints.size < 2) {
                    SvgDashContext.recordDashedStroke(didApproximate = false)
                    return null
                }

                val dashed = buildDashedPath(
                    points = dashPoints,
                    pattern = effectivePattern,
                    dashOffset = effectiveDashOffset,
                    strokeLineCap = strokeLineCap
                )
                if (dashed.isBlank()) {
                    SvgDashContext.recordDashedStroke(didApproximate = false)
                    return null
                }

                if (dashCompensation.enabled) {
                    SvgDashContext.recordDashTransformCompensation(dashCompensation.approximate)
                }
                SvgDashContext.recordDashedStroke(didApproximate = true)
                return dashed
            }
        }
    }

    private fun inheritedStyleOrAttribute(element: Element, directStyle: String?, property: String): String? {
        SvgPaintResolver.styleValue(directStyle, property)?.let { return it }
        element.getAttribute(property).trim().takeIf { it.isNotBlank() }?.let { return it }

        var parent = element.parentNode
        while (parent is Element) {
            val parentStyle = parent.getAttribute("style").ifBlank { null }
            SvgPaintResolver.styleValue(parentStyle, property)?.let { return it }
            parent.getAttribute(property).trim().takeIf { it.isNotBlank() }?.let { return it }
            parent = parent.parentNode
        }
        return null
    }

    private fun parseDashArrayStrict(value: String?): DashArrayParseResult {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("none", ignoreCase = true)) return DashArrayParseResult.None

        val tokens = raw.replace(",", " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return DashArrayParseResult.Invalid

        val values = mutableListOf<Float>()
        for (token in tokens) {
            val number = parseSupportedLengthToken(token) ?: return DashArrayParseResult.Invalid
            if (!number.isFinite() || number < 0f) return DashArrayParseResult.Invalid
            values += number
        }

        if (values.all { it == 0f }) return DashArrayParseResult.None
        val odd = values.size % 2 != 0
        return DashArrayParseResult.Valid(if (odd) values + values else values, odd)
    }

    private fun parseSingleLengthStrict(value: String?): Float? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return 0f
        if (raw.contains(',') || raw.split(Regex("\\s+")).size != 1) return null
        return parseSupportedLengthToken(raw)?.takeIf { it.isFinite() }
    }

    private fun parseSupportedLengthToken(token: String): Float? {
        val trimmed = token.trim()
        if (trimmed.endsWith("%")) return null
        val numeric = when {
            trimmed.endsWith("px", ignoreCase = true) -> trimmed.dropLast(2)
            trimmed.endsWith("dp", ignoreCase = true) -> trimmed.dropLast(2)
            else -> trimmed
        }
        return numeric.toFloatOrNull()
    }

    private fun dashApproximationPoints(element: Element, sourceTag: String?, pathData: String): List<DashPoint> {
        return when (sourceTag) {
            "line", "polyline" -> extractLinePoints(pathData)
            "polygon" -> closeDashPolygon(extractLinePoints(pathData))
            "rect" -> rectDashPoints(element) ?: closeDashPolygon(extractLinePoints(pathData))
            "circle" -> circleDashPoints(element) ?: closeDashPolygon(extractLinePoints(pathData))
            "ellipse" -> ellipseDashPoints(element) ?: closeDashPolygon(extractLinePoints(pathData))
            "path" -> sampledPathDashPoints(pathData) ?: straightPathDashPoints(pathData) ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Stage 2 dashed-stroke support for complete SVG path geometry.
     * SvgPathSampler flattens L/H/V, C/S, Q/T, A and Z commands while this
     * adapter preserves move-separated subpaths so the dash pattern does not
     * accidentally bridge across an SVG M command.
     */
    private fun sampledPathDashPoints(pathData: String): List<DashPoint>? {
        val measured = SvgPathSampler.measure(pathData, curveSteps = 32) ?: return null
        val subpaths = measured.flattenedSubpaths()
        if (subpaths.isEmpty()) return null

        val result = mutableListOf<DashPoint>()
        subpaths.forEachIndexed { subpathIndex, subpath ->
            subpath.forEachIndexed { pointIndex, point ->
                result.add(
                    DashPoint(
                        x = point.x,
                        y = point.y,
                        startsNewSubpath = subpathIndex > 0 && pointIndex == 0
                    )
                )
            }
        }
        return result.takeIf { it.size >= 2 }
    }

    private fun rectDashPoints(element: Element): List<DashPoint>? {
        val x = floatAttr(element, "x") ?: 0f
        val y = floatAttr(element, "y") ?: 0f
        val width = floatAttr(element, "width") ?: return null
        val height = floatAttr(element, "height") ?: return null
        if (width <= 0f || height <= 0f) return null

        val rawRx = floatAttr(element, "rx")
        val rawRy = floatAttr(element, "ry")
        val rx = when {
            rawRx != null -> rawRx
            rawRy != null -> rawRy
            else -> 0f
        }.coerceAtLeast(0f).coerceAtMost(width / 2f)
        val ry = when {
            rawRy != null -> rawRy
            rawRx != null -> rawRx
            else -> 0f
        }.coerceAtLeast(0f).coerceAtMost(height / 2f)

        if (rx <= 0.0001f || ry <= 0.0001f) {
            return listOf(
                DashPoint(x, y),
                DashPoint(x + width, y),
                DashPoint(x + width, y + height),
                DashPoint(x, y + height),
                DashPoint(x, y)
            )
        }

        val points = mutableListOf<DashPoint>()
        points.add(DashPoint(x + rx, y))
        points.add(DashPoint(x + width - rx, y))
        appendArcPoints(points, x + width - rx, y + ry, rx, ry, -90f, 0f, includeFirst = false)
        points.add(DashPoint(x + width, y + height - ry))
        appendArcPoints(points, x + width - rx, y + height - ry, rx, ry, 0f, 90f, includeFirst = false)
        points.add(DashPoint(x + rx, y + height))
        appendArcPoints(points, x + rx, y + height - ry, rx, ry, 90f, 180f, includeFirst = false)
        points.add(DashPoint(x, y + ry))
        appendArcPoints(points, x + rx, y + ry, rx, ry, 180f, 270f, includeFirst = false)
        points.add(points.first())
        return points
    }

    private fun circleDashPoints(element: Element): List<DashPoint>? {
        val cx = floatAttr(element, "cx") ?: 0f
        val cy = floatAttr(element, "cy") ?: 0f
        val r = floatAttr(element, "r") ?: return null
        if (r <= 0f) return null
        return ellipsePerimeterPoints(cx, cy, r, r)
    }

    private fun ellipseDashPoints(element: Element): List<DashPoint>? {
        val cx = floatAttr(element, "cx") ?: 0f
        val cy = floatAttr(element, "cy") ?: 0f
        val rx = floatAttr(element, "rx") ?: return null
        val ry = floatAttr(element, "ry") ?: return null
        if (rx <= 0f || ry <= 0f) return null
        return ellipsePerimeterPoints(cx, cy, rx, ry)
    }

    private fun ellipsePerimeterPoints(cx: Float, cy: Float, rx: Float, ry: Float): List<DashPoint> {
        val points = mutableListOf<DashPoint>()
        val steps = 96
        for (i in 0..steps) {
            val angle = (Math.PI * 2.0 * i.toDouble()) / steps.toDouble()
            points.add(
                DashPoint(
                    x = cx + (kotlin.math.cos(angle) * rx).toFloat(),
                    y = cy + (kotlin.math.sin(angle) * ry).toFloat()
                )
            )
        }
        return points
    }

    private fun appendArcPoints(
        points: MutableList<DashPoint>,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        startDegrees: Float,
        endDegrees: Float,
        includeFirst: Boolean
    ) {
        val steps = 12
        val startIndex = if (includeFirst) 0 else 1
        for (i in startIndex..steps) {
            val t = i.toFloat() / steps.toFloat()
            val degrees = startDegrees + (endDegrees - startDegrees) * t
            val radians = Math.toRadians(degrees.toDouble())
            points.add(
                DashPoint(
                    x = cx + (kotlin.math.cos(radians) * rx).toFloat(),
                    y = cy + (kotlin.math.sin(radians) * ry).toFloat()
                )
            )
        }
    }

    private fun extractLinePoints(pathData: String): List<DashPoint> {
        val points = mutableListOf<DashPoint>()
        val commandRegex = Regex("""([MmLl])\s*([-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)[,\s]+([-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?)""")
        commandRegex.findAll(pathData).forEach { match ->
            val x = match.groupValues.getOrNull(2)?.toFloatOrNull()
            val y = match.groupValues.getOrNull(3)?.toFloatOrNull()
            if (x != null && y != null) points.add(DashPoint(x, y))
        }
        return points
    }

    private fun closeDashPolygon(points: List<DashPoint>): List<DashPoint> {
        if (points.size < 2) return points
        val first = points.first()
        val last = points.last()
        val alreadyClosed = kotlin.math.abs(first.x - last.x) < 0.0001f &&
            kotlin.math.abs(first.y - last.y) < 0.0001f
        return if (alreadyClosed) points else points + first
    }

    private fun straightPathDashPoints(pathData: String): List<DashPoint>? {
        val tokens = tokenizePathData(pathData)
        if (tokens.isEmpty()) return null

        val points = mutableListOf<DashPoint>()
        var i = 0
        var command: Char? = null
        var currentX = 0f
        var currentY = 0f
        var subpathStartX = 0f
        var subpathStartY = 0f
        var hasCurrentPoint = false

        fun isCommandToken(index: Int): Boolean = index < tokens.size && tokens[index].length == 1 && tokens[index][0].isLetter()
        fun readFloat(): Float? = tokens.getOrNull(i)?.toFloatOrNull()?.also { i++ }
        fun addPoint(x: Float, y: Float, startsNewSubpath: Boolean = false) {
            points.add(DashPoint(x, y, startsNewSubpath))
            currentX = x
            currentY = y
            hasCurrentPoint = true
        }

        while (i < tokens.size) {
            if (isCommandToken(i)) {
                command = tokens[i][0]
                i++
            } else if (command == null) {
                return null
            }

            when (command) {
                'M', 'm' -> {
                    val relative = command == 'm'
                    val x = readFloat() ?: return null
                    val y = readFloat() ?: return null
                    val moveX = if (relative) currentX + x else x
                    val moveY = if (relative) currentY + y else y
                    addPoint(moveX, moveY, startsNewSubpath = points.isNotEmpty())
                    subpathStartX = moveX
                    subpathStartY = moveY

                    // Additional coordinate pairs after M/m are implicit L/l commands.
                    command = if (relative) 'l' else 'L'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val lx = readFloat() ?: return null
                        val ly = readFloat() ?: return null
                        addPoint(if (relative) currentX + lx else lx, if (relative) currentY + ly else ly)
                    }
                }
                'L', 'l' -> {
                    if (!hasCurrentPoint) return null
                    val relative = command == 'l'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return null
                        val y = readFloat() ?: return null
                        addPoint(if (relative) currentX + x else x, if (relative) currentY + y else y)
                    }
                }
                'H', 'h' -> {
                    if (!hasCurrentPoint) return null
                    val relative = command == 'h'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val x = readFloat() ?: return null
                        addPoint(if (relative) currentX + x else x, currentY)
                    }
                }
                'V', 'v' -> {
                    if (!hasCurrentPoint) return null
                    val relative = command == 'v'
                    while (i < tokens.size && !isCommandToken(i)) {
                        val y = readFloat() ?: return null
                        addPoint(currentX, if (relative) currentY + y else y)
                    }
                }
                'Z', 'z' -> {
                    if (!hasCurrentPoint) return null
                    addPoint(subpathStartX, subpathStartY)
                    command = null
                }
                else -> return null
            }
        }

        return points.takeIf { it.size >= 2 }
    }

    private fun tokenizePathData(pathData: String): List<String> {
        val tokenRegex = Regex("""[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?""")
        return tokenRegex.findAll(pathData).map { it.value }.toList()
    }

    /**
     * Builds one compound VectorDrawable path from the sampled geometry.
     *
     * A visible dash that crosses an original segment boundary remains one
     * continuous M/L/L... subpath, allowing Android's stroke-linejoin and
     * stroke-miterlimit to apply at that corner. A gap always starts a new M.
     *
     * SVG restarts the dash pattern for each independently moved subpath.
     * Zero-length "on" entries are retained for round/square caps as degenerate
     * subpaths, which allows cap-only dots/squares where the renderer supports
     * zero-length stroked segments. Butt-capped zero-length dashes are invisible.
     */
    private fun buildDashedPath(
        points: List<DashPoint>,
        pattern: List<Float>,
        dashOffset: Float,
        strokeLineCap: String?
    ): String {
        if (points.size < 2 || pattern.isEmpty()) return ""

        val totalPatternLength = pattern.sum()
        if (totalPatternLength <= 0f) return ""

        val cap = strokeLineCap?.trim()?.lowercase(Locale.US).orEmpty()
        val preserveZeroLengthDash = cap == "round" || cap == "square"
        val commands = mutableListOf<String>()

        var patternIndex = 0
        var drawDash = true
        var remainingInPattern = 0f
        var drawingSubpath = false
        var lastDrawX = Float.NaN
        var lastDrawY = Float.NaN

        fun samePoint(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
            return kotlin.math.abs(x1 - x2) <= 0.0005f &&
                kotlin.math.abs(y1 - y2) <= 0.0005f
        }

        fun appendZeroLengthDash(x: Float, y: Float) {
            if (!preserveZeroLengthDash) return
            commands += "M ${formatDashNumber(x)},${formatDashNumber(y)} L ${formatDashNumber(x)},${formatDashNumber(y)}"
            drawingSubpath = false
        }

        fun advancePastZeroEntries(x: Float, y: Float) {
            var guard = 0
            while (remainingInPattern <= 0.000001f && guard < pattern.size * 3) {
                if (drawDash) appendZeroLengthDash(x, y)
                patternIndex = (patternIndex + 1) % pattern.size
                drawDash = patternIndex % 2 == 0
                remainingInPattern = pattern[patternIndex]
                if (!drawDash) drawingSubpath = false
                guard++
            }
        }

        fun resetPatternAtSubpathStart(x: Float, y: Float) {
            patternIndex = 0
            drawDash = true
            drawingSubpath = false
            lastDrawX = Float.NaN
            lastDrawY = Float.NaN

            // Positive SVG offsets shift the pattern backwards along the path.
            var offset = positiveModulo(-dashOffset, totalPatternLength)
            remainingInPattern = pattern[patternIndex]

            var guard = 0
            while (offset > 0.000001f && guard < pattern.size * 4) {
                advancePastZeroEntries(x, y)
                if (remainingInPattern <= 0.000001f) break
                if (offset < remainingInPattern) {
                    remainingInPattern -= offset
                    offset = 0f
                } else {
                    offset -= remainingInPattern
                    remainingInPattern = 0f
                    advancePastZeroEntries(x, y)
                }
                guard++
            }
            advancePastZeroEntries(x, y)
        }

        fun appendVisibleSegment(x1: Float, y1: Float, x2: Float, y2: Float) {
            if (!drawingSubpath || !samePoint(lastDrawX, lastDrawY, x1, y1)) {
                commands += "M ${formatDashNumber(x1)},${formatDashNumber(y1)} L ${formatDashNumber(x2)},${formatDashNumber(y2)}"
            } else {
                commands[commands.lastIndex] =
                    commands.last() + " L ${formatDashNumber(x2)},${formatDashNumber(y2)}"
            }
            drawingSubpath = true
            lastDrawX = x2
            lastDrawY = y2
        }

        resetPatternAtSubpathStart(points.first().x, points.first().y)

        for (i in 0 until points.lastIndex) {
            val from = points[i]
            val to = points[i + 1]

            if (to.startsNewSubpath) {
                resetPatternAtSubpathStart(to.x, to.y)
                continue
            }

            val dx = to.x - from.x
            val dy = to.y - from.y
            val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (length <= 0.0001f) continue

            var walked = 0f
            while (walked < length - 0.0001f) {
                advancePastZeroEntries(
                    from.x + dx * (walked / length),
                    from.y + dy * (walked / length)
                )
                if (remainingInPattern <= 0.000001f) break

                val step = minOf(remainingInPattern, length - walked)
                val startRatio = walked / length
                val endRatio = (walked + step) / length
                val x1 = from.x + dx * startRatio
                val y1 = from.y + dy * startRatio
                val x2 = from.x + dx * endRatio
                val y2 = from.y + dy * endRatio

                if (drawDash && step > 0.0001f) {
                    appendVisibleSegment(x1, y1, x2, y2)
                }

                walked += step
                remainingInPattern -= step

                if (remainingInPattern <= 0.0001f) {
                    if (drawDash) drawingSubpath = false
                    advancePastZeroEntries(x2, y2)
                }
            }
        }

        return commands.joinToString(" ")
    }

    private fun positiveModulo(value: Float, modulo: Float): Float {
        if (modulo <= 0f) return 0f
        val result = value % modulo
        return if (result < 0f) result + modulo else result
    }

    private fun formatDashNumber(value: Float): String {
        return java.lang.String.format(Locale.US, "%.3f", value)
            .trimEnd('0')
            .trimEnd('.')
    }


}
