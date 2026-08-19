package com.school.attendance.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Writes rows to a CSV file in cache and hands it to the phone's share sheet — opens directly in
 * Excel/Sheets/WhatsApp etc. A real .xlsx would need an extra library (Apache POI); CSV needs none
 * and every spreadsheet app opens it natively, so it's the lean choice for exporting reports. */
object CsvExport {
    fun export(context: Context, fileName: String, header: List<String>, rows: List<List<String>>) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.bufferedWriter().use { w ->
            w.write(header.joinToString(",") { esc(it) }); w.newLine()
            rows.forEach { row -> w.write(row.joinToString(",") { esc(it) }); w.newLine() }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $fileName"))
    }

    private fun esc(s: String): String = if (s.contains(",") || s.contains("\"") || s.contains("\n"))
        "\"" + s.replace("\"", "\"\"") + "\"" else s
}
