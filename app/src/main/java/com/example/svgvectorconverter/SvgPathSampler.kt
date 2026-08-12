package com.example.svgvectorconverter

import kotlin.math.*

/** Flattens SVG path data into short line segments for text-on-path placement. */
internal object SvgPathSampler {
    data class Sample(val x: Float, val y: Float, val angleDegrees: Float)

    internal data class Point(val x: Float, val y: Float)
    internal data class Segment(
        val from: Point,
        val to: Point,
        val start: Float,
        val length: Float,
        val endsAtSourceVertex: Boolean = false
    )

    class MeasuredPath internal constructor(
        private val segments: List<Segment>,
        val length: Float,
        val isClosed: Boolean
    ) {
        fun sample(distance: Float, wrapClosed: Boolean = false): Sample? {
            if (segments.isEmpty()) return null
            val d = if (wrapClosed && isClosed && length > 0.0001f) {
                ((distance % length) + length) % length
            } else {
                distance.coerceIn(0f, length)
            }
            val segment = segments.firstOrNull { d <= it.start + it.length } ?: segments.last()
            val ratio = if (segment.length <= 0.0001f) 0f else ((d - segment.start) / segment.length).coerceIn(0f, 1f)
            val x = segment.from.x + (segment.to.x - segment.from.x) * ratio
            val y = segment.from.y + (segment.to.y - segment.from.y) * ratio
            val angle = Math.toDegrees(atan2((segment.to.y - segment.from.y).toDouble(), (segment.to.x - segment.from.x).toDouble())).toFloat()
            return Sample(x, y, angle)
        }


        /**
         * Returns the flattened geometry as separate, ordered SVG subpaths.
         * Curves and arcs are already represented by short line segments.
         * A new list is started whenever the next segment does not begin at
         * the previous segment's end point (which corresponds to an SVG move).
         */
        internal data class FlattenedPoint(
            val point: Point,
            val isSourceVertex: Boolean
        )

        internal fun flattenedSubpathsWithVertices(): List<List<FlattenedPoint>> {
            if (segments.isEmpty()) return emptyList()

            val result = mutableListOf<MutableList<FlattenedPoint>>()
            var currentSubpath: MutableList<FlattenedPoint>? = null
            var previousEnd: Point? = null

            fun samePoint(a: Point?, b: Point): Boolean {
                if (a == null) return false
                return abs(a.x - b.x) <= 0.0001f && abs(a.y - b.y) <= 0.0001f
            }

            for (segment in segments) {
                if (currentSubpath == null || !samePoint(previousEnd, segment.from)) {
                    currentSubpath = mutableListOf(FlattenedPoint(segment.from, true))
                    result.add(currentSubpath)
                } else if (!samePoint(currentSubpath.lastOrNull()?.point, segment.from)) {
                    currentSubpath.add(FlattenedPoint(segment.from, false))
                }

                val last = currentSubpath.lastOrNull()
                if (!samePoint(last?.point, segment.to)) {
                    currentSubpath.add(FlattenedPoint(segment.to, segment.endsAtSourceVertex))
                } else if (segment.endsAtSourceVertex && last != null && !last.isSourceVertex) {
                    currentSubpath[currentSubpath.lastIndex] = last.copy(isSourceVertex = true)
                }
                previousEnd = segment.to
            }

            return result.filter { it.size >= 2 }
        }

        internal fun flattenedSubpaths(): List<List<Point>> {
            return flattenedSubpathsWithVertices().map { subpath -> subpath.map { it.point } }
        }

    }

    fun measure(pathData: String, curveSteps: Int = 24): MeasuredPath? {
        val tokens = tokenize(pathData)
        if (tokens.isEmpty()) return null
        val points = mutableListOf<Triple<Point, Point, Boolean>>()
        var index = 0
        var command: Char? = null
        var current = Point(0f, 0f)
        var subStart = current
        var lastCubic: Point? = null
        var lastQuad: Point? = null
        var previousCommand: Char? = null
        var closed = false

        fun hasNumber(): Boolean = index < tokens.size && !isCommand(tokens[index])
        fun read(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()
        fun addLine(to: Point, endsAtSourceVertex: Boolean = true) {
            if (hypot((to.x-current.x).toDouble(), (to.y-current.y).toDouble()) > 0.0001) {
                points.add(Triple(current, to, endsAtSourceVertex))
            }
            current = to
        }
        fun cubic(p0: Point, p1: Point, p2: Point, p3: Point) {
            var prev = p0
            for (step in 1..curveSteps) {
                val t = step.toFloat()/curveSteps
                val u = 1f-t
                val p = Point(
                    u*u*u*p0.x + 3f*u*u*t*p1.x + 3f*u*t*t*p2.x + t*t*t*p3.x,
                    u*u*u*p0.y + 3f*u*u*t*p1.y + 3f*u*t*t*p2.y + t*t*t*p3.y
                )
                if (hypot((p.x-prev.x).toDouble(), (p.y-prev.y).toDouble()) > 0.0001) points.add(Triple(prev, p, step == curveSteps))
                prev = p
            }
            current = p3
        }
        fun quad(p0: Point, p1: Point, p2: Point) {
            var prev = p0
            for (step in 1..curveSteps) {
                val t = step.toFloat()/curveSteps
                val u = 1f-t
                val p = Point(u*u*p0.x + 2f*u*t*p1.x + t*t*p2.x, u*u*p0.y + 2f*u*t*p1.y + t*t*p2.y)
                if (hypot((p.x-prev.x).toDouble(), (p.y-prev.y).toDouble()) > 0.0001) points.add(Triple(prev, p, step == curveSteps))
                prev = p
            }
            current = p2
        }

        while (index < tokens.size) {
            if (isCommand(tokens[index])) command = tokens[index++][0] else if (command == null) return null
            val cmd = command ?: return null
            val absolute = cmd.isUpperCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var first = true
                    while (hasNumber()) {
                        val xr=read()?:return null; val yr=read()?:return null
                        val p=Point(if(absolute) xr else current.x+xr, if(absolute) yr else current.y+yr)
                        if(first){ current=p; subStart=p; first=false } else addLine(p)
                    }
                    command=if(absolute)'L' else 'l'; lastCubic=null; lastQuad=null; previousCommand='M'
                }
                'L' -> while(hasNumber()){ val xr=read()?:return null; val yr=read()?:return null; addLine(Point(if(absolute)xr else current.x+xr,if(absolute)yr else current.y+yr)); lastCubic=null;lastQuad=null;previousCommand='L' }
                'H' -> while(hasNumber()){ val xr=read()?:return null; addLine(Point(if(absolute)xr else current.x+xr,current.y)); lastCubic=null;lastQuad=null;previousCommand='H' }
                'V' -> while(hasNumber()){ val yr=read()?:return null; addLine(Point(current.x,if(absolute)yr else current.y+yr)); lastCubic=null;lastQuad=null;previousCommand='V' }
                'C' -> while(hasNumber()){
                    val a=read()?:return null;val b=read()?:return null;val c=read()?:return null;val d=read()?:return null;val e=read()?:return null;val f=read()?:return null
                    val p1=Point(if(absolute)a else current.x+a,if(absolute)b else current.y+b);val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d);val p3=Point(if(absolute)e else current.x+e,if(absolute)f else current.y+f)
                    cubic(current,p1,p2,p3);lastCubic=p2;lastQuad=null;previousCommand='C'
                }
                'S' -> while(hasNumber()){
                    val c=read()?:return null;val d=read()?:return null;val e=read()?:return null;val f=read()?:return null
                    val p1=if(previousCommand=='C'||previousCommand=='S') Point(2*current.x-(lastCubic?.x?:current.x),2*current.y-(lastCubic?.y?:current.y)) else current
                    val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d);val p3=Point(if(absolute)e else current.x+e,if(absolute)f else current.y+f)
                    cubic(current,p1,p2,p3);lastCubic=p2;lastQuad=null;previousCommand='S'
                }
                'Q' -> while(hasNumber()){
                    val a=read()?:return null;val b=read()?:return null;val c=read()?:return null;val d=read()?:return null
                    val p1=Point(if(absolute)a else current.x+a,if(absolute)b else current.y+b);val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d)
                    quad(current,p1,p2);lastQuad=p1;lastCubic=null;previousCommand='Q'
                }
                'T' -> while(hasNumber()){
                    val c=read()?:return null;val d=read()?:return null
                    val p1=if(previousCommand=='Q'||previousCommand=='T') Point(2*current.x-(lastQuad?.x?:current.x),2*current.y-(lastQuad?.y?:current.y)) else current
                    val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d)
                    quad(current,p1,p2);lastQuad=p1;lastCubic=null;previousCommand='T'
                }
                'A' -> while(hasNumber()){
                    val rx=abs(read()?:return null);val ry=abs(read()?:return null);val rotation=read()?:return null
                    val large=(read()?:return null)!=0f;val sweep=(read()?:return null)!=0f;val xr=read()?:return null;val yr=read()?:return null
                    val end=Point(if(absolute)xr else current.x+xr,if(absolute)yr else current.y+yr)
                    val arc=sampleArc(current,end,rx,ry,rotation,large,sweep,curveSteps)
                    for((arcIndex,p) in arc.withIndex()) addLine(p, endsAtSourceVertex = arcIndex == arc.lastIndex)
                    lastCubic=null;lastQuad=null;previousCommand='A'
                }
                'Z' -> {
                    addLine(subStart)
                    closed = true
                    lastCubic=null;lastQuad=null;previousCommand='Z'
                }
                else -> return null
            }
        }
        var walked=0f
        val segments=points.map { (from,to,endsAtSourceVertex) ->
            val len=hypot((to.x-from.x).toDouble(),(to.y-from.y).toDouble()).toFloat()
            Segment(from,to,walked,len,endsAtSourceVertex).also { walked+=len }
        }.filter { it.length>0.0001f }
        return if(segments.isEmpty()) null else MeasuredPath(segments,walked,closed)
    }


    /**
     * Flattens a path and maps every generated point through [mapper].
     * This is used for non-affine textPath stretching, where each point in a
     * glyph must follow a different tangent and normal on the referenced path.
     */
    fun mapFlattenedPath(
        pathData: String,
        curveSteps: Int = 12,
        mapper: (Float, Float) -> Point?
    ): String? {
        val tokens = tokenize(pathData)
        if (tokens.isEmpty()) return null
        val output = StringBuilder()
        var index = 0
        var command: Char? = null
        var current = Point(0f, 0f)
        var subStart = current
        var lastCubic: Point? = null
        var lastQuad: Point? = null
        var previousCommand: Char? = null

        fun hasNumber(): Boolean = index < tokens.size && !isCommand(tokens[index])
        fun read(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()
        fun number(value: Float): String {
            if (!value.isFinite()) return "0"
            val rounded = kotlin.math.round(value * 10000f) / 10000f
            return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.0001f) {
                rounded.toInt().toString()
            } else rounded.toString().trimEnd('0').trimEnd('.')
        }
        fun appendMapped(prefix: Char, point: Point): Boolean {
            val mapped = mapper(point.x, point.y) ?: return false
            output.append(prefix).append(' ')
                .append(number(mapped.x)).append(',').append(number(mapped.y)).append(' ')
            return true
        }
        fun lineTo(point: Point): Boolean {
            current = point
            return appendMapped('L', point)
        }
        fun cubic(p0: Point, p1: Point, p2: Point, p3: Point): Boolean {
            for (step in 1..curveSteps) {
                val t = step.toFloat() / curveSteps
                val u = 1f - t
                val point = Point(
                    u*u*u*p0.x + 3f*u*u*t*p1.x + 3f*u*t*t*p2.x + t*t*t*p3.x,
                    u*u*u*p0.y + 3f*u*u*t*p1.y + 3f*u*t*t*p2.y + t*t*t*p3.y
                )
                if (!appendMapped('L', point)) return false
            }
            current = p3
            return true
        }
        fun quad(p0: Point, p1: Point, p2: Point): Boolean {
            for (step in 1..curveSteps) {
                val t = step.toFloat() / curveSteps
                val u = 1f - t
                val point = Point(
                    u*u*p0.x + 2f*u*t*p1.x + t*t*p2.x,
                    u*u*p0.y + 2f*u*t*p1.y + t*t*p2.y
                )
                if (!appendMapped('L', point)) return false
            }
            current = p2
            return true
        }

        while (index < tokens.size) {
            if (isCommand(tokens[index])) command = tokens[index++][0]
            else if (command == null) return null
            val cmd = command ?: return null
            val absolute = cmd.isUpperCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    var first = true
                    while (hasNumber()) {
                        val xr = read() ?: return null
                        val yr = read() ?: return null
                        val point = Point(if (absolute) xr else current.x + xr, if (absolute) yr else current.y + yr)
                        if (first) {
                            if (!appendMapped('M', point)) return null
                            current = point
                            subStart = point
                            first = false
                        } else if (!lineTo(point)) return null
                    }
                    command = if (absolute) 'L' else 'l'
                    lastCubic = null; lastQuad = null; previousCommand = 'M'
                }
                'L' -> while (hasNumber()) {
                    val xr = read() ?: return null; val yr = read() ?: return null
                    if (!lineTo(Point(if (absolute) xr else current.x + xr, if (absolute) yr else current.y + yr))) return null
                    lastCubic = null; lastQuad = null; previousCommand = 'L'
                }
                'H' -> while (hasNumber()) {
                    val xr = read() ?: return null
                    if (!lineTo(Point(if (absolute) xr else current.x + xr, current.y))) return null
                    lastCubic = null; lastQuad = null; previousCommand = 'H'
                }
                'V' -> while (hasNumber()) {
                    val yr = read() ?: return null
                    if (!lineTo(Point(current.x, if (absolute) yr else current.y + yr))) return null
                    lastCubic = null; lastQuad = null; previousCommand = 'V'
                }
                'C' -> while (hasNumber()) {
                    val a=read()?:return null; val b=read()?:return null; val c=read()?:return null
                    val d=read()?:return null; val e=read()?:return null; val f=read()?:return null
                    val p1=Point(if(absolute)a else current.x+a,if(absolute)b else current.y+b)
                    val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d)
                    val p3=Point(if(absolute)e else current.x+e,if(absolute)f else current.y+f)
                    if (!cubic(current,p1,p2,p3)) return null
                    lastCubic=p2; lastQuad=null; previousCommand='C'
                }
                'S' -> while (hasNumber()) {
                    val c=read()?:return null; val d=read()?:return null; val e=read()?:return null; val f=read()?:return null
                    val p1=if(previousCommand=='C'||previousCommand=='S') Point(2f*current.x-(lastCubic?.x?:current.x),2f*current.y-(lastCubic?.y?:current.y)) else current
                    val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d)
                    val p3=Point(if(absolute)e else current.x+e,if(absolute)f else current.y+f)
                    if (!cubic(current,p1,p2,p3)) return null
                    lastCubic=p2; lastQuad=null; previousCommand='S'
                }
                'Q' -> while (hasNumber()) {
                    val a=read()?:return null; val b=read()?:return null; val c=read()?:return null; val d=read()?:return null
                    val p1=Point(if(absolute)a else current.x+a,if(absolute)b else current.y+b)
                    val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d)
                    if (!quad(current,p1,p2)) return null
                    lastQuad=p1; lastCubic=null; previousCommand='Q'
                }
                'T' -> while (hasNumber()) {
                    val c=read()?:return null; val d=read()?:return null
                    val p1=if(previousCommand=='Q'||previousCommand=='T') Point(2f*current.x-(lastQuad?.x?:current.x),2f*current.y-(lastQuad?.y?:current.y)) else current
                    val p2=Point(if(absolute)c else current.x+c,if(absolute)d else current.y+d)
                    if (!quad(current,p1,p2)) return null
                    lastQuad=p1; lastCubic=null; previousCommand='T'
                }
                'A' -> while (hasNumber()) {
                    val rx=abs(read()?:return null); val ry=abs(read()?:return null); val rotation=read()?:return null
                    val large=(read()?:return null)!=0f; val sweep=(read()?:return null)!=0f
                    val xr=read()?:return null; val yr=read()?:return null
                    val end=Point(if(absolute)xr else current.x+xr,if(absolute)yr else current.y+yr)
                    for (point in sampleArc(current,end,rx,ry,rotation,large,sweep,curveSteps)) {
                        if (!appendMapped('L', point)) return null
                    }
                    current=end; lastCubic=null; lastQuad=null; previousCommand='A'
                }
                'Z' -> {
                    output.append("Z ")
                    current=subStart; lastCubic=null; lastQuad=null; previousCommand='Z'
                }
                else -> return null
            }
        }
        return output.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun sampleArc(start: Point,end: Point,rxIn:Float,ryIn:Float,rotDeg:Float,large:Boolean,sweep:Boolean,steps:Int):List<Point>{
        if(rxIn<=0f||ryIn<=0f||start==end)return listOf(end)
        val phi=Math.toRadians((rotDeg%360f).toDouble());val cosPhi=cos(phi);val sinPhi=sin(phi)
        val dx=(start.x-end.x)/2.0;val dy=(start.y-end.y)/2.0
        val x1p=cosPhi*dx+sinPhi*dy;val y1p=-sinPhi*dx+cosPhi*dy
        var rx=rxIn.toDouble();var ry=ryIn.toDouble();val lambda=x1p*x1p/(rx*rx)+y1p*y1p/(ry*ry)
        if(lambda>1){val s=sqrt(lambda);rx*=s;ry*=s}
        val sign=if(large==sweep)-1.0 else 1.0
        val num=max(0.0,rx*rx*ry*ry-rx*rx*y1p*y1p-ry*ry*x1p*x1p);val den=rx*rx*y1p*y1p+ry*ry*x1p*x1p
        val coef=if(den==0.0)0.0 else sign*sqrt(num/den)
        val cxp=coef*(rx*y1p/ry);val cyp=coef*(-ry*x1p/rx)
        val cx=cosPhi*cxp-sinPhi*cyp+(start.x+end.x)/2.0;val cy=sinPhi*cxp+cosPhi*cyp+(start.y+end.y)/2.0
        fun angle(ux:Double,uy:Double,vx:Double,vy:Double):Double{val dot=ux*vx+uy*vy;val len=sqrt((ux*ux+uy*uy)*(vx*vx+vy*vy));var a=acos((dot/len).coerceIn(-1.0,1.0));if(ux*vy-uy*vx<0)a=-a;return a}
        val ux=(x1p-cxp)/rx;val uy=(y1p-cyp)/ry;val vx=(-x1p-cxp)/rx;val vy=(-y1p-cyp)/ry
        val theta1=angle(1.0,0.0,ux,uy);var delta=angle(ux,uy,vx,vy)
        if(!sweep&&delta>0)delta-=2*PI else if(sweep&&delta<0)delta+=2*PI
        val count=max(4,ceil(abs(delta)/(PI/2)*steps/4).toInt())
        return (1..count).map { i -> val t=theta1+delta*i/count; Point((cx+cosPhi*rx*cos(t)-sinPhi*ry*sin(t)).toFloat(),(cy+sinPhi*rx*cos(t)+cosPhi*ry*sin(t)).toFloat()) }
    }


    /**
     * G3.9 diagnostic comparator. Compares ordered flattened subpaths after
     * removing only subdivision vertices that lie strictly on the same
     * monotonic straight segment. This makes equivalent line subdivisions
     * invariant without erasing reversals/backtracking.
     */
    internal data class SubdivisionInvariantDiagnostic(
        val equivalent: Boolean,
        val firstSubpaths: Int,
        val secondSubpaths: Int,
        val firstVerticesBefore: Int,
        val secondVerticesBefore: Int,
        val firstVerticesAfter: Int,
        val secondVerticesAfter: Int,
        val maximumMatchedVertexDeviation: Double,
        val reason: String
    )

    internal fun subdivisionInvariantGeometryDiagnostic(
        first: String,
        second: String,
        curveSteps: Int = 256
    ): SubdivisionInvariantDiagnostic {
        val firstMeasured = measure(first, curveSteps)
        val secondMeasured = measure(second, curveSteps)
        if (firstMeasured == null || secondMeasured == null) {
            return SubdivisionInvariantDiagnostic(
                equivalent = false,
                firstSubpaths = firstMeasured?.flattenedSubpaths()?.size ?: 0,
                secondSubpaths = secondMeasured?.flattenedSubpaths()?.size ?: 0,
                firstVerticesBefore = 0,
                secondVerticesBefore = 0,
                firstVerticesAfter = 0,
                secondVerticesAfter = 0,
                maximumMatchedVertexDeviation = Double.POSITIVE_INFINITY,
                reason = "path could not be flattened"
            )
        }

        val firstRaw = firstMeasured.flattenedSubpaths()
        val secondRaw = secondMeasured.flattenedSubpaths()
        val firstSimple = firstRaw.map(::removeMonotonicCollinearSubdivisionVertices)
        val secondSimple = secondRaw.map(::removeMonotonicCollinearSubdivisionVertices)
        val firstBefore = firstRaw.sumOf { it.size }
        val secondBefore = secondRaw.sumOf { it.size }
        val firstAfter = firstSimple.sumOf { it.size }
        val secondAfter = secondSimple.sumOf { it.size }

        if (firstSimple.size != secondSimple.size) {
            return SubdivisionInvariantDiagnostic(false, firstSimple.size, secondSimple.size,
                firstBefore, secondBefore, firstAfter, secondAfter,
                Double.POSITIVE_INFINITY, "subpath count differs")
        }

        var maxDeviation = 0.0
        for (subpathIndex in firstSimple.indices) {
            val a = firstSimple[subpathIndex]
            val b = secondSimple[subpathIndex]
            if (a.size != b.size) {
                return SubdivisionInvariantDiagnostic(false, firstSimple.size, secondSimple.size,
                    firstBefore, secondBefore, firstAfter, secondAfter,
                    Double.POSITIVE_INFINITY,
                    "simplified vertex count differs in subpath ${subpathIndex + 1}: ${a.size} vs ${b.size}")
            }
            for (index in a.indices) {
                val dx = a[index].x.toDouble() - b[index].x.toDouble()
                val dy = a[index].y.toDouble() - b[index].y.toDouble()
                val deviation = sqrt(dx * dx + dy * dy)
                if (deviation > maxDeviation) maxDeviation = deviation
                if (!floatGeometryCoordinateEquivalent(a[index].x, b[index].x) ||
                    !floatGeometryCoordinateEquivalent(a[index].y, b[index].y)
                ) {
                    return SubdivisionInvariantDiagnostic(false, firstSimple.size, secondSimple.size,
                        firstBefore, secondBefore, firstAfter, secondAfter,
                        maxDeviation,
                        "vertex ${index + 1} differs in subpath ${subpathIndex + 1}")
                }
            }
        }

        return SubdivisionInvariantDiagnostic(true, firstSimple.size, secondSimple.size,
            firstBefore, secondBefore, firstAfter, secondAfter,
            maxDeviation, "equivalent after monotonic collinear subdivision removal")
    }

    internal fun subdivisionInvariantGeometryEquivalent(
        first: String,
        second: String,
        curveSteps: Int = 256
    ): Boolean = subdivisionInvariantGeometryDiagnostic(first, second, curveSteps).equivalent

    private fun removeMonotonicCollinearSubdivisionVertices(points: List<Point>): List<Point> {
        if (points.size <= 2) return points
        val result = ArrayList<Point>(points.size)
        for (point in points) {
            result.add(point)
            while (result.size >= 3) {
                val a = result[result.size - 3]
                val b = result[result.size - 2]
                val c = result[result.size - 1]
                if (!isRemovableMonotonicCollinearVertex(a, b, c)) break
                result.removeAt(result.size - 2)
            }
        }
        return result
    }

    private fun isRemovableMonotonicCollinearVertex(a: Point, b: Point, c: Point): Boolean {
        val abx = b.x.toDouble() - a.x.toDouble()
        val aby = b.y.toDouble() - a.y.toDouble()
        val bcx = c.x.toDouble() - b.x.toDouble()
        val bcy = c.y.toDouble() - b.y.toDouble()
        val abLength = hypot(abx, aby)
        val bcLength = hypot(bcx, bcy)
        if (abLength <= 1e-12 || bcLength <= 1e-12) return true

        val cross = abx * bcy - aby * bcx
        val crossTolerance = 1e-9 * max(1.0, abLength * bcLength)
        if (abs(cross) > crossTolerance) return false

        // Positive dot product means both segments continue in the same
        // direction. A reversal/backtrack therefore remains observable.
        val dot = abx * bcx + aby * bcy
        if (dot <= 0.0) return false

        val acx = c.x.toDouble() - a.x.toDouble()
        val acy = c.y.toDouble() - a.y.toDouble()
        val projection = (abx * acx + aby * acy) / max(1e-30, acx * acx + acy * acy)
        return projection > 0.0 && projection < 1.0
    }

    private fun floatGeometryCoordinateEquivalent(a: Float, b: Float): Boolean {
        if (a == b) return true
        if (!a.isFinite() || !b.isFinite()) return false
        val ulpTolerance = 8.0 * max(java.lang.Math.ulp(a), java.lang.Math.ulp(b)).toDouble()
        return abs(a.toDouble() - b.toDouble()) <= max(1e-4, ulpTolerance)
    }



    /** G3.10 diagnostic-only bidirectional polyline comparator. */
    internal data class BidirectionalPolylineDiagnostic(
        val equivalent: Boolean,
        val firstSubpaths: Int,
        val secondSubpaths: Int,
        val firstVertices: Int,
        val secondVertices: Int,
        val maximumFirstToSecondDeviation: Double,
        val maximumSecondToFirstDeviation: Double,
        val firstOffendingPoint: String,
        val secondOffendingPoint: String,
        val firstNearestSegment: String,
        val secondNearestSegment: String,
        val reason: String
    )

    internal fun bidirectionalPolylineGeometryDiagnostic(
        first: String,
        second: String,
        curveSteps: Int = 256
    ): BidirectionalPolylineDiagnostic {
        val exactBookkeeping = SvgPathDataOptimizer.orderedTraversalPairDiagnostic(first, second)
        if (!exactBookkeeping.parseable) {
            return BidirectionalPolylineDiagnostic(false, 0, 0, 0, 0,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, "", "", "", "",
                "exact path bookkeeping parse failed")
        }
        if (!exactBookkeeping.endpointsPreserved) {
            return BidirectionalPolylineDiagnostic(false, 0, 0, 0, 0,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                exactBookkeeping.firstEndpointSummary,
                exactBookkeeping.secondEndpointSummary,
                "", "",
                "exact open/closed subpath endpoints differ")
        }

        val aMeasured = measure(first, curveSteps)
        val bMeasured = measure(second, curveSteps)
        if (aMeasured == null || bMeasured == null) {
            return BidirectionalPolylineDiagnostic(false, 0, 0, 0, 0,
                Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, "", "", "", "",
                "path could not be flattened")
        }
        val a = aMeasured.flattenedSubpaths()
        val b = bMeasured.flattenedSubpaths()
        val av = a.sumOf { it.size }
        val bv = b.sumOf { it.size }
        if (a.size != b.size) return BidirectionalPolylineDiagnostic(false,a.size,b.size,av,bv,
            Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,"","","","","subpath count differs")

        var maxAB = 0.0; var maxBA = 0.0
        var pointAB = ""; var pointBA = ""; var segAB = ""; var segBA = ""
        for (i in a.indices) {
            val pa=a[i]; val pb=b[i]
            if (pa.size < 2 || pb.size < 2) continue

            /*
             * G3.12 bookkeeping repair:
             * remove only same-direction collinear subdivision vertices before
             * traveled-length accounting. Reversals/backtracking remain.
             */
            val paLengthBasis = removeMonotonicCollinearSubdivisionVertices(pa)
            val pbLengthBasis = removeMonotonicCollinearSubdivisionVertices(pb)
            val la=polylineLength(paLengthBasis); val lb=polylineLength(pbLengthBasis)
            val lenTol=max(1e-4, 1e-7*max(1.0,max(la,lb)))
            if (abs(la-lb)>lenTol) return BidirectionalPolylineDiagnostic(false,a.size,b.size,av,bv,maxAB,maxBA,"","","","",
                "traveled length differs in subpath ${i+1}: $la vs $lb")

            val ab=directedPolylineDeviation(pa,pb)
            val ba=directedPolylineDeviation(pb,pa)
            if (ab.distance>maxAB){maxAB=ab.distance;pointAB=formatPoint(ab.point);segAB=ab.segment}
            if (ba.distance>maxBA){maxBA=ba.distance;pointBA=formatPoint(ba.point);segBA=ba.segment}
            val scale=max(1.0, max(polylineBoundsScale(pa), polylineBoundsScale(pb)))
            val tol=max(1e-4, 8.0*Math.ulp(scale.toFloat()).toDouble())
            if (ab.distance>tol || ba.distance>tol) return BidirectionalPolylineDiagnostic(false,a.size,b.size,av,bv,maxAB,maxBA,pointAB,pointBA,segAB,segBA,
                "bidirectional polyline deviation exceeds tolerance in subpath ${i+1}")
        }
        return BidirectionalPolylineDiagnostic(true,a.size,b.size,av,bv,maxAB,maxBA,pointAB,pointBA,segAB,segBA,
            "equivalent under G3.12 exact bookkeeping + bidirectional adaptive polyline distance")
    }

    private data class DirectedDeviation(val distance:Double,val point:Point,val segment:String)

    private fun directedPolylineDeviation(source:List<Point>, target:List<Point>):DirectedDeviation {
        var best=DirectedDeviation(0.0, source.first(), "")
        fun test(p:Point){
            val nearest=nearestSegmentDistance(p,target)
            if(nearest.first>best.distance) best=DirectedDeviation(nearest.first,p,nearest.second)
        }
        for (p in source) test(p)
        for (i in 0 until source.lastIndex) {
            val a=source[i]; val b=source[i+1]
            fun refine(p0:Point,p1:Point,depth:Int){
                val mid=Point((p0.x+p1.x)/2f,(p0.y+p1.y)/2f); test(mid)
                if(depth<5){ refine(p0,mid,depth+1); refine(mid,p1,depth+1) }
            }
            refine(a,b,0)
        }
        return best
    }

    private fun nearestSegmentDistance(p:Point, poly:List<Point>):Pair<Double,String>{
        var best=Double.POSITIVE_INFINITY; var label=""
        for(i in 0 until poly.lastIndex){
            val a=poly[i]; val b=poly[i+1]
            val vx=b.x.toDouble()-a.x; val vy=b.y.toDouble()-a.y
            val wx=p.x.toDouble()-a.x; val wy=p.y.toDouble()-a.y
            val denom=vx*vx+vy*vy
            val t=if(denom<=1e-30)0.0 else ((wx*vx+wy*vy)/denom).coerceIn(0.0,1.0)
            val qx=a.x+vx*t; val qy=a.y+vy*t
            val d=hypot(p.x-qx,p.y-qy)
            if(d<best){best=d;label="${formatPoint(a)} → ${formatPoint(b)}"}
        }
        return best to label
    }

    private fun polylineLength(points:List<Point>):Double=(0 until points.lastIndex).sumOf{i->hypot(
        points[i+1].x.toDouble()-points[i].x.toDouble(),points[i+1].y.toDouble()-points[i].y.toDouble())}
    private fun polylineBoundsScale(points:List<Point>):Double{
        var m=0.0; for(p in points)m=max(m,max(abs(p.x.toDouble()),abs(p.y.toDouble()))); return m
    }
    private fun samePointLoose(a:Point,b:Point)=pointEquivalentLoose(a,b)
    private fun pointEquivalentLoose(a:Point,b:Point)=floatGeometryCoordinateEquivalent(a.x,b.x)&&floatGeometryCoordinateEquivalent(a.y,b.y)
    private fun formatPoint(p:Point)=String.format(java.util.Locale.US,"%.9g,%.9g",p.x.toDouble(),p.y.toDouble())

    private fun tokenize(data:String)=Regex("[A-Za-z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(data).map{it.value}.toList()
    private fun isCommand(token:String)=token.length==1&&token[0].isLetter()
}
