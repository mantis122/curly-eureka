package com.example.svgvectorconverter

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

object SvgTreeConverter {
private var activeClipPathData: Map<String, String> = emptyMap()
private var activeAppliedClipPaths = 0
private var activeResolvedUseExpansions = 0
private var activeUnresolvedUseReferences = 0

val appliedClipPaths: Int get() = activeAppliedClipPaths
val resolvedUseExpansions: Int get() = activeResolvedUseExpansions
val unresolvedUseReferences: Int get() = activeUnresolvedUseReferences

private lateinit var appendElementPathCallback: (
    StringBuilder, Element, String,
    String?, String?, String?, String?, String?, String?,
    String?, String?, String?, String?, String?, String?
) -> Unit

private lateinit var appendBasicShapePathCallback: (
    StringBuilder, Element, String, String,
    String?, String?, String?, String?, String?, String?,
    String?, String?, String?, String?, String?, String?
) -> Unit

private lateinit var appendFlatPathsFallbackCallback: (StringBuilder, String, String) -> Unit
private lateinit var basicShapeToPathDataCallback: (Element, String) -> String?
private lateinit var floatAttrCallback: (Element, String) -> Float?
private lateinit var escapeXmlCallback: (String) -> String

fun resetStats(clipPathData: Map<String, String>) {
    activeClipPathData = clipPathData
    activeAppliedClipPaths = 0
    activeResolvedUseExpansions = 0
    activeUnresolvedUseReferences = 0
}

fun collectClipPathData(
    svg: String,
    basicShapeToPathData: (Element, String) -> String?
): Map<String, String> {
    basicShapeToPathDataCallback = basicShapeToPathData
    val result = mutableMapOf<String, String>()

    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        val root = document.documentElement
        val definitions = collectSvgDefinitions(root)

        fun elementToPathData(element: Element, depth: Int = 0): String {
            if (depth > 20) return ""

            val tag = element.tagName.substringAfter(":").lowercase()

            return when (tag) {
                "path" -> element.getAttribute("d").trim()
                "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                    basicShapeToPathDataCallback(element, tag).orEmpty()
                "use" -> {
                    val href = element.getAttribute("href").ifBlank {
                        element.getAttribute("xlink:href").ifBlank {
                            element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
                        }
                    }.trim()
                    val id = href.removePrefix("#").trim()
                    val referenced = definitions[id] ?: return ""
                    elementToPathData(referenced, depth + 1)
                }
                else -> {
                    val parts = mutableListOf<String>()
                    val children = element.childNodes
                    for (i in 0 until children.length) {
                        val child = children.item(i)
                        if (child.nodeType != Node.ELEMENT_NODE) continue
                        val childPath = elementToPathData(child as Element, depth + 1)
                        if (childPath.isNotBlank()) parts.add(childPath)
                    }
                    parts.joinToString(" ")
                }
            }
        }

        fun visit(node: Node) {
            if (node.nodeType != Node.ELEMENT_NODE) return

            val element = node as Element
            val tag = element.tagName.substringAfter(":").lowercase()

            if (tag == "clippath") {
                val id = element.getAttribute("id").trim()
                val pathData = elementToPathData(element)
                if (id.isNotBlank() && pathData.isNotBlank()) {
                    result[id] = pathData
                }
            }

            val children = element.childNodes
            for (i in 0 until children.length) {
                visit(children.item(i))
            }
        }

        visit(root)
    } catch (_: Exception) {
        return emptyMap()
    }

    return result
}

fun clipPathIdFromValue(value: String?): String? {
    val v = value?.trim() ?: return null
    return Regex("""url\(\s*#([^)'"\s]+)\s*\)""")
        .find(v)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

fun hasClipPathData(clipPathId: String?): Boolean {
    return clipPathId != null && activeClipPathData.containsKey(clipPathId)
}

fun appendClipPath(output: StringBuilder, clipPathId: String?, indent: String): Boolean {
    val id = clipPathId ?: return false
    val pathData = activeClipPathData[id] ?: return false

    activeAppliedClipPaths++

    output.appendLine("""${indent}<clip-path""")
    output.appendLine("""${indent}    android:pathData="${escapeXmlCallback(pathData)}"""")
    output.appendLine("""${indent}/>""")
    return true
}

fun appendConvertedSvgTree(
    output: StringBuilder,
    svg: String,
    appendElementPath: (
        StringBuilder, Element, String,
        String?, String?, String?, String?, String?, String?,
        String?, String?, String?, String?, String?, String?
    ) -> Unit,
    appendBasicShapePath: (
        StringBuilder, Element, String, String,
        String?, String?, String?, String?, String?, String?,
        String?, String?, String?, String?, String?, String?
    ) -> Unit,
    appendFlatPathsFallback: (StringBuilder, String, String) -> Unit,
    basicShapeToPathData: (Element, String) -> String?,
    floatAttr: (Element, String) -> Float?,
    escapeXml: (String) -> String
) {
    appendElementPathCallback = appendElementPath
    appendBasicShapePathCallback = appendBasicShapePath
    appendFlatPathsFallbackCallback = appendFlatPathsFallback
    basicShapeToPathDataCallback = basicShapeToPathData
    floatAttrCallback = floatAttr
    escapeXmlCallback = escapeXml
    try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
        }

        val document = factory
            .newDocumentBuilder()
            .parse(InputSource(StringReader(svg)))

        val root = document.documentElement
        val definitions = collectSvgDefinitions(root)

        walkSvgNode(
            output = output,
            node = root,
            indent = "    ",
            definitions = definitions
        )
    } catch (e: Exception) {
        // Fallback for imperfect SVG/XML files
        appendFlatPathsFallbackCallback(output, svg, "    ")
    }
}

private fun collectSvgDefinitions(root: Element): Map<String, Element> {
    val definitions = linkedMapOf<String, Element>()

    fun visit(node: Node) {
        if (node.nodeType != Node.ELEMENT_NODE) return

        val element = node as Element
        val id = element.getAttribute("id").trim()

        if (id.isNotBlank()) {
            definitions[id] = element
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            visit(children.item(i))
        }
    }

    visit(root)
    return definitions
}

private fun childClipPathId(node: Node, inheritedClipPath: String?): String? {
    if (node.nodeType != Node.ELEMENT_NODE) return null

    val element = node as Element
    val style = element.getAttribute("style").ifBlank { null }
    val clipPathValue = SvgPaintResolver.styleValue(style, "clip-path")
        ?: element.getAttribute("clip-path").ifBlank { inheritedClipPath ?: "" }

    val clipId = clipPathIdFromValue(clipPathValue)
    return clipId?.takeIf { activeClipPathData.containsKey(it) }
}

private fun appendChildrenWithClipGrouping(
    output: StringBuilder,
    parent: Element,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedStrokeLineCap: String?,
    inheritedStrokeLineJoin: String?,
    inheritedStrokeMiterLimit: String?,
    inheritedFillRule: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?,
    inheritedClipPath: String?,
    definitions: Map<String, Element>,
    useDepth: Int,
    activeClipPathId: String?
) {
    val children = parent.childNodes
    var i = 0

    while (i < children.length) {
        val child = children.item(i)
        val clipId = childClipPathId(child, inheritedClipPath)

        if (clipId != null && clipId != activeClipPathId) {
            output.appendLine("${indent}<group")
            output.appendLine("${indent}>")
            appendClipPath(output, clipId, indent + "    ")

            while (i < children.length) {
                val groupedChild = children.item(i)
                val groupedClipId = childClipPathId(groupedChild, inheritedClipPath)
                if (groupedClipId != clipId) break

                walkSvgNode(
                    output,
                    groupedChild,
                    indent + "    ",
                    inheritedFill,
                    inheritedStroke,
                    inheritedStrokeWidth,
                    inheritedStrokeLineCap,
                    inheritedStrokeLineJoin,
                    inheritedStrokeMiterLimit,
                    inheritedFillRule,
                    inheritedOpacity,
                    inheritedFillOpacity,
                    inheritedStrokeOpacity,
                    inheritedClipPath,
                    definitions,
                    useDepth,
                    clipId
                )

                i++
            }

            output.appendLine("${indent}</group>")
            output.appendLine()
        } else {
            walkSvgNode(
                output,
                child,
                indent,
                inheritedFill,
                inheritedStroke,
                inheritedStrokeWidth,
                inheritedStrokeLineCap,
                inheritedStrokeLineJoin,
                inheritedStrokeMiterLimit,
                inheritedFillRule,
                inheritedOpacity,
                inheritedFillOpacity,
                inheritedStrokeOpacity,
                inheritedClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
            i++
        }
    }
}

private fun walkSvgNode(
    output: StringBuilder,
    node: Node,
    indent: String,
    inheritedFill: String? = null,
    inheritedStroke: String? = null,
    inheritedStrokeWidth: String? = null,
    inheritedStrokeLineCap: String? = null,
    inheritedStrokeLineJoin: String? = null,
    inheritedStrokeMiterLimit: String? = null,
    inheritedFillRule: String? = null,
    inheritedOpacity: String? = null,
    inheritedFillOpacity: String? = null,
    inheritedStrokeOpacity: String? = null,
    inheritedClipPath: String? = null,
    definitions: Map<String, Element> = emptyMap(),
    useDepth: Int = 0,
    activeClipPathId: String? = null
) {
    if (node.nodeType != Node.ELEMENT_NODE) return

    val element = node as Element
val style = element.getAttribute("style").ifBlank { null }

val currentFill = SvgPaintResolver.styleValue(style, "fill")
    ?: element.getAttribute("fill").ifBlank { inheritedFill ?: "" }

val currentStroke = SvgPaintResolver.styleValue(style, "stroke")
    ?: element.getAttribute("stroke").ifBlank { inheritedStroke ?: "" }

val currentStrokeWidth = SvgPaintResolver.styleValue(style, "stroke-width")
    ?: element.getAttribute("stroke-width").ifBlank { inheritedStrokeWidth ?: "" }

val currentStrokeLineCap = SvgPaintResolver.styleValue(style, "stroke-linecap")
    ?: element.getAttribute("stroke-linecap").ifBlank { inheritedStrokeLineCap ?: "" }

val currentStrokeLineJoin = SvgPaintResolver.styleValue(style, "stroke-linejoin")
    ?: element.getAttribute("stroke-linejoin").ifBlank { inheritedStrokeLineJoin ?: "" }

val currentStrokeMiterLimit = SvgPaintResolver.styleValue(style, "stroke-miterlimit")
    ?: element.getAttribute("stroke-miterlimit").ifBlank { inheritedStrokeMiterLimit ?: "" }

val currentFillRule = SvgPaintResolver.styleValue(style, "fill-rule")
    ?: element.getAttribute("fill-rule").ifBlank { inheritedFillRule ?: "" }

val currentOpacity = SvgPaintResolver.combineAlpha(
    inheritedOpacity,
    SvgPaintResolver.styleValue(style, "opacity")
        ?: element.getAttribute("opacity").ifBlank { "" }
)

val currentFillOpacity = SvgPaintResolver.styleValue(style, "fill-opacity")
    ?: element.getAttribute("fill-opacity").ifBlank { inheritedFillOpacity ?: "" }

val currentStrokeOpacity = SvgPaintResolver.styleValue(style, "stroke-opacity")
    ?: element.getAttribute("stroke-opacity").ifBlank { inheritedStrokeOpacity ?: "" }

val currentClipPath = SvgPaintResolver.styleValue(style, "clip-path")
    ?: element.getAttribute("clip-path").ifBlank { inheritedClipPath ?: "" }

   val tagName = element.tagName.substringAfter(":").lowercase()

    when (tagName) {
        "defs" -> {
            // Definitions are not drawn directly. They are expanded when a <use> references them.
            return
        }

        "symbol" -> {
            // Symbols are reusable definitions. Draw them only when expanded from a <use>.
            if (useDepth <= 0) return
            appendChildrenWithClipGrouping(
                output,
                element,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentStrokeMiterLimit,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity,
                currentClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
        }

        "use" -> {
            appendUseElement(
                output,
                element,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentStrokeMiterLimit,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity,
                currentClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
        }

        "g" -> {
            val transform = element.getAttribute("transform")
            val transforms = SvgTransformParser.parseTransformList(transform)
            val groupClipPathId = clipPathIdFromValue(currentClipPath)
            val hasClipPath = groupClipPathId != null && groupClipPathId != activeClipPathId && activeClipPathData.containsKey(groupClipPathId)

            val needsGroup = transforms.any { it.hasVisibleEffect() } || hasClipPath

            if (needsGroup) {
                var currentIndent = indent
                var openedGroupCount = 0

                if (hasClipPath) {
                    output.appendLine("${currentIndent}<group>")
                    appendClipPath(output, groupClipPathId, currentIndent + "    ")
                    currentIndent += "    "
                    openedGroupCount++
                }

                val combinedTransform = SvgTransformParser.combineTransformList(transforms)
                if (combinedTransform != null) {
                    SvgTransformParser.appendCombinedTransformGroupStart(output, combinedTransform, currentIndent)
                    currentIndent += "    "
                    openedGroupCount++
                }

                appendChildrenWithClipGrouping(
                    output,
                    element,
                    currentIndent,
                    currentFill,
                    currentStroke,
                    currentStrokeWidth,
                    currentStrokeLineCap,
                    currentStrokeLineJoin,
                    currentStrokeMiterLimit,
                    currentFillRule,
                    currentOpacity,
                    currentFillOpacity,
                    currentStrokeOpacity,
                    currentClipPath,
                    definitions,
                    useDepth,
                    if (hasClipPath) groupClipPathId else activeClipPathId
                )

                repeat(openedGroupCount) {
                    currentIndent = currentIndent.dropLast(4)
                    output.appendLine("${currentIndent}</group>")
                }
                output.appendLine()
            } else {
                appendChildrenWithClipGrouping(
                    output,
                    element,
                    indent,
                    currentFill,
                    currentStroke,
                    currentStrokeWidth,
                    currentStrokeLineCap,
                    currentStrokeLineJoin,
                    currentStrokeMiterLimit,
                    currentFillRule,
                    currentOpacity,
                    currentFillOpacity,
                    currentStrokeOpacity,
                    currentClipPath,
                    definitions,
                    useDepth,
                    activeClipPathId
                )
            }
        }

        "path" -> {
            appendElementPathCallback(
                output,
                element,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentStrokeMiterLimit,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity,
                currentClipPath,
                activeClipPathId
            )
        }

        "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
            appendBasicShapePathCallback(
                output,
                element,
                tagName,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentStrokeMiterLimit,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity,
                currentClipPath,
                activeClipPathId
            )
        }

        else -> {
            appendChildrenWithClipGrouping(
                output,
                element,
                indent,
                currentFill,
                currentStroke,
                currentStrokeWidth,
                currentStrokeLineCap,
                currentStrokeLineJoin,
                currentStrokeMiterLimit,
                currentFillRule,
                currentOpacity,
                currentFillOpacity,
                currentStrokeOpacity,
                currentClipPath,
                definitions,
                useDepth,
                activeClipPathId
            )
        }
    }
}

private fun useHrefId(element: Element): String? {
    val href = element.getAttribute("href").ifBlank {
        element.getAttribute("xlink:href").ifBlank {
            element.getAttributeNS("http://www.w3.org/1999/xlink", "href")
        }
    }.trim()

    return href
        .removePrefix("#")
        .takeIf { it.isNotBlank() }
}

private data class SvgViewBox(val minX: Float, val minY: Float, val width: Float, val height: Float)

private fun elementViewBox(element: Element): SvgViewBox? {
    val parts = element.getAttribute("viewBox")
        .trim()
        .split(Regex("[,\\s]+"))
        .mapNotNull { it.toFloatOrNull() }

    return if (parts.size >= 4 && parts[2] != 0f && parts[3] != 0f) {
        SvgViewBox(parts[0], parts[1], parts[2], parts[3])
    } else {
        null
    }
}

private fun appendUseElement(
    output: StringBuilder,
    element: Element,
    indent: String,
    inheritedFill: String?,
    inheritedStroke: String?,
    inheritedStrokeWidth: String?,
    inheritedStrokeLineCap: String?,
    inheritedStrokeLineJoin: String?,
    inheritedStrokeMiterLimit: String?,
    inheritedFillRule: String?,
    inheritedOpacity: String?,
    inheritedFillOpacity: String?,
    inheritedStrokeOpacity: String?,
    inheritedClipPath: String?,
    definitions: Map<String, Element>,
    useDepth: Int,
    activeClipPathId: String? = null
) {
    if (useDepth >= 20) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use>: nesting limit reached -->")
        return
    }

    val id = useHrefId(element)
    if (id == null) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use>: missing href -->")
        return
    }

    val referenced = definitions[id]
    if (referenced == null) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use href=\"#$id\"> -->")
        return
    }

    val referencedTag = referenced.tagName.substringAfter(":").lowercase()

    if (referencedTag == "defs" || referencedTag == "clippath" || referencedTag == "lineargradient" ||
        referencedTag == "radialgradient" || referencedTag == "mask" || referencedTag == "filter" ||
        referencedTag == "pattern"
    ) {
        activeUnresolvedUseReferences++
        output.appendLine("${indent}<!-- unresolved <use href=\"#$id\">: referenced <$referencedTag> is not drawable -->")
        return
    }

    activeResolvedUseExpansions++

    val referencedViewBox =
        if (referencedTag == "symbol" || referencedTag == "svg") elementViewBox(referenced) else null

    val useWidth = floatAttrCallback(element, "width")
    val useHeight = floatAttrCallback(element, "height")

    val symbolScaleX =
        if (referencedViewBox != null && useWidth != null) useWidth / referencedViewBox.width else 1f

    val symbolScaleY =
        if (referencedViewBox != null && useHeight != null) useHeight / referencedViewBox.height else 1f

    val x = floatAttrCallback(element, "x") ?: 0f
    val y = floatAttrCallback(element, "y") ?: 0f

    val transform = element.getAttribute("transform")
    val placementTransforms = mutableListOf<ParsedTransform>()
    placementTransforms.addAll(SvgTransformParser.parseTransformList(transform))

    val viewBoxTranslateX = -((referencedViewBox?.minX ?: 0f) * symbolScaleX)
    val viewBoxTranslateY = -((referencedViewBox?.minY ?: 0f) * symbolScaleY)

    if (x + viewBoxTranslateX != 0f || y + viewBoxTranslateY != 0f) {
        placementTransforms.add(ParsedTransform.Translate(x + viewBoxTranslateX, y + viewBoxTranslateY))
    }

    if (symbolScaleX != 1f || symbolScaleY != 1f) {
        placementTransforms.add(ParsedTransform.Scale(symbolScaleX, symbolScaleY))
    }

    val needsGroup = placementTransforms.any { it.hasVisibleEffect() }

    if (needsGroup) {
        val combinedTransform = SvgTransformParser.combineTransformList(placementTransforms)

        if (combinedTransform != null) {
            SvgTransformParser.appendCombinedTransformGroupStart(output, combinedTransform, indent)
            val childIndent = indent + "    "

            output.appendLine("${childIndent}<!-- expanded from <use href=\"#$id\"> -->")

            walkSvgNode(
                output,
                referenced,
                childIndent,
                inheritedFill,
                inheritedStroke,
                inheritedStrokeWidth,
                inheritedStrokeLineCap,
                inheritedStrokeLineJoin,
                inheritedStrokeMiterLimit,
                inheritedFillRule,
                inheritedOpacity,
                inheritedFillOpacity,
                inheritedStrokeOpacity,
                inheritedClipPath,
                definitions,
                useDepth + 1,
                activeClipPathId
            )

            output.appendLine("${indent}</group>")
            output.appendLine()
        }
    } else {
        output.appendLine("${indent}<!-- expanded from <use href=\"#$id\"> -->")

        walkSvgNode(
            output,
            referenced,
            indent,
            inheritedFill,
            inheritedStroke,
            inheritedStrokeWidth,
            inheritedStrokeLineCap,
            inheritedStrokeLineJoin,
            inheritedStrokeMiterLimit,
            inheritedFillRule,
            inheritedOpacity,
            inheritedFillOpacity,
            inheritedStrokeOpacity,
            inheritedClipPath,
            definitions,
            useDepth + 1,
            activeClipPathId
        )
    }
}


}
