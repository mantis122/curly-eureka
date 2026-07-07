package com.example.svgvectorconverter

import android.content.Context

data class ConverterSettings(
    val outputDpSize: Int = 24,
    val conversionProfile: String = "Default"
)

object ConverterSettingsStore {
    private const val PREFS_NAME = "svg_converter_settings"
    private const val KEY_OUTPUT_DP_SIZE = "outputDpSize"
    private const val KEY_CONVERSION_PROFILE = "conversionProfile"

    fun load(context: Context): ConverterSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return ConverterSettings(
            outputDpSize = prefs.getInt(KEY_OUTPUT_DP_SIZE, 24),
            conversionProfile = prefs.getString(KEY_CONVERSION_PROFILE, "Default") ?: "Default"
        )
    }

    fun saveOutputDpSize(context: Context, outputDpSize: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_OUTPUT_DP_SIZE, outputDpSize)
            .apply()
    }

    fun save(context: Context, settings: ConverterSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CONVERSION_PROFILE, settings.conversionProfile)
            .putInt(KEY_OUTPUT_DP_SIZE, settings.outputDpSize)
            .apply()
    }
}
