package com.school.attendance.util

import android.content.Context
import android.net.Uri

/** Minimal CSV reader (quoted fields with embedded commas/quotes supported) — the counterpart to
 * [CsvExport]. Returns every row including the header; callers drop row 0 themselves. */
object CsvImport {
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimEnd('\r')
            if (line.isBlank()) continue
            val fields = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> { fields.add(sb.toString()); sb.clear() }
                    else -> sb.append(c)
                }
                i++
            }
            fields.add(sb.toString())
            rows.add(fields)
        }
        return rows
    }

    fun readUri(context: Context, uri: Uri): List<List<String>> {
        val text = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        return parse(text)
    }
}
