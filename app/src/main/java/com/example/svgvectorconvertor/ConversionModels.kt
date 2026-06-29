package com.example.svgvectorconverter

data class ConversionResult(
    val xml: String,
    val report: String
)

data class BatchResult(
    val fileName: String,
    val xml: String?,
    val warningCount: Int,
    val success: Boolean,
    val definitionPathCount: Int = 0,
    val error: String? = null
)

