package com.example.svgvectorconverter

import kotlin.math.*

/** Flattens SVG path data into short line segments for text-on-path placement. */
internal object SvgPathSampler {
    data class Sample(val x: Float, val y: Float, val angleDegrees: Float)

    internal data class Point(val x: Float, val y: Float)
    internal data class Segment(val from: Point, val to: Point, val start: Float, val length: Float)

    class MeasuredPath internal constructor(private val segments: List<Segment>, val length: Float) {
        fun sample(distance: Float): Sample? {
            if (segments.isEmpty()) return null
            val d = distance.coerceIn(0f, length)
            val segment = segments.firstOrNull { d <= it.start + it.length } ?: segments.last()
            val ratio = if (segment.length <= 0.0001f) 0f else ((d - segment.start) / segment.length).coerceIn(0f, 1f)
            val x = segment.from.x + (segment.to.x - segment.from.x) * ratio
            val y = segment.from.y + (segment.to.y - segment.from.y) * ratio
            val angle = Math.toDegrees(atan2((segment.to.y - segment.from.y).toDouble(), (segment.to.x - segment.from.x).toDouble())).toFloat()
            return Sample(x, y, angle)
        }
    }

    fun measure(pathData: String, curveSteps: Int = 24): MeasuredPath? {
        val tokens = tokenize(pathData)
        if (tokens.isEmpty()) return null
        val points = mutableListOf<Pair<Point, Point>>()
        var index = 0
        var command: Char? = null
        var current = Point(0f, 0f)
        var subStart = current
        var lastCubic: Point? = null
        var lastQuad: Point? = null
        var previousCommand: Char? = null

        fun hasNumber(): Boolean = index < tokens.size && !isCommand(tokens[index])
        fun read(): Float? = tokens.getOrNull(index++)?.toFloatOrNull()
        fun addLine(to: Point) {
            if (hypot((to.x-current.x).toDouble(), (to.y-current.y).toDouble()) > 0.0001) points.add(current to to)
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
                if (hypot((p.x-prev.x).toDouble(), (p.y-prev.y).toDouble()) > 0.0001) points.add(prev to p)
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
                if (hypot((p.x-prev.x).toDouble(), (p.y-prev.y).toDouble()) > 0.0001) points.add(prev to p)
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
                    for(p in arc) addLine(p)
                    lastCubic=null;lastQuad=null;previousCommand='A'
                }
                'Z' -> { addLine(subStart); lastCubic=null;lastQuad=null;previousCommand='Z' }
                else -> return null
            }
        }
        var walked=0f
        val segments=points.map { (from,to) ->
            val len=hypot((to.x-from.x).toDouble(),(to.y-from.y).toDouble()).toFloat()
            Segment(from,to,walked,len).also { walked+=len }
        }.filter { it.length>0.0001f }
        return if(segments.isEmpty()) null else MeasuredPath(segments,walked)
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

    private fun tokenize(data:String)=Regex("[A-Za-z]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(data).map{it.value}.toList()
    private fun isCommand(token:String)=token.length==1&&token[0].isLetter()
}
