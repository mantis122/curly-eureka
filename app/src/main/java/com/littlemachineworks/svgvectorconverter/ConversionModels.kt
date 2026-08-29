package com.littlemachineworks.svgvectorconverter

data class ConversionResult(
    val xml: String,
    val report: String,
    val reportData: SvgConversionReportData? = null
)

data class BatchResult(
    val fileName: String,
    val xml: String?,
    val warningCount: Int,
    val success: Boolean,
    val definitionPathCount: Int = 0,
    val error: String? = null,
    val report: String = ""
)

