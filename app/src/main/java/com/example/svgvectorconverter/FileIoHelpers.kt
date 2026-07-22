package com.example.svgvectorconverter

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileIoHelpers {
    fun readTextFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: ""
    }

    fun writeTextToUri(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(text.toByteArray())
        }
    }

    fun writeBitmapPngToUri(context: Context, uri: Uri, bitmap: Bitmap) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode PNG"
            }
        } ?: error("Could not open output file")
    }

    fun writeZipToUri(
        context: Context,
        uri: Uri,
        results: List<BatchResult>
    ) {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output).use { zip ->
                results
                    .filter { it.success && it.xml != null }
                    .forEach { result ->
                        zip.putNextEntry(ZipEntry(result.fileName))
                        zip.write(result.xml!!.toByteArray())
                        zip.closeEntry()
                    }
            }
        }
    }

    fun makeXmlFileName(context: Context, uri: Uri): String {
        var displayName: String? = null

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIndex)
            }
        }

        val name = displayName ?: "converted_vector.svg"

        val baseName = name
            .substringBeforeLast(".")
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "converted_vector" }

        return "$baseName.xml"
    }
}
