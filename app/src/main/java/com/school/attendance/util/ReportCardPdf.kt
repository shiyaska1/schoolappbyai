package com.school.attendance.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Student
import com.school.attendance.report.StudentReportCard
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/** Draws one A4 page per student: school name, term, student photo + identity, a subject-wise
 * marks/grade table, and an overall total — the same "consolidate every teacher's mark entry into
 * one printable sheet" idea as a CBSE/ICSE report card. No external PDF library — Android's own
 * [PdfDocument] + [android.graphics.Canvas] is plenty for a table this simple. */
object ReportCardPdf {
    private const val PAGE_W = 595 // A4 at 72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private fun safe(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)

    fun generateForDivision(
        context: Context, schoolName: String, termGroup: String, divisionLabel: String,
        cards: List<Pair<Student, StudentReportCard>>, displayMode: String
    ): File {
        val doc = PdfDocument()
        cards.forEach { (student, card) -> drawPage(doc, schoolName, termGroup, divisionLabel, student, card, displayMode) }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "report-cards-${safe(divisionLabel)}-${safe(termGroup)}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun generateForStudent(
        context: Context, schoolName: String, termGroup: String, divisionLabel: String,
        student: Student, card: StudentReportCard, displayMode: String
    ): File = generateForDivision(context, schoolName, termGroup, divisionLabel, listOf(student to card), displayMode)
        .let { multi ->
            // Single-student convenience: rename so a repeat share doesn't collide with a batch file.
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "report-card-${safe(student.name)}-${safe(termGroup)}.pdf")
            multi.copyTo(file, overwrite = true)
            file
        }

    private fun drawPage(
        doc: PdfDocument, schoolName: String, termGroup: String, divisionLabel: String,
        student: Student, card: StudentReportCard, displayMode: String
    ) {
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, doc.pages.size + 1).create())
        val canvas = page.canvas
        val black = Paint().apply { color = Color.BLACK; isAntiAlias = true }
        val gray = Paint().apply { color = Color.DKGRAY; isAntiAlias = true }
        val line = Paint().apply { color = Color.BLACK; strokeWidth = 1f }

        var y = MARGIN
        val titlePaint = Paint(black).apply { textSize = 20f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        canvas.drawText(schoolName.ifBlank { "School Report Card" }, PAGE_W / 2f, y + 20f, titlePaint)
        y += 32f
        val subPaint = Paint(gray).apply { textSize = 13f; textAlign = Paint.Align.CENTER }
        canvas.drawText("Report Card — $termGroup", PAGE_W / 2f, y, subPaint)
        y += 16f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
        y += 20f

        // Photo (top-right) + identity block (left)
        val photoW = 90f; val photoH = 110f
        val photoLeft = PAGE_W - MARGIN - photoW
        val bmp = if (student.photoPath.isNotBlank()) runCatching { BitmapFactory.decodeFile(student.photoPath) }.getOrNull() else null
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(photoLeft, y, photoLeft + photoW, y + photoH), null)
            canvas.drawRect(photoLeft, y, photoLeft + photoW, y + photoH, Paint(line).apply { style = Paint.Style.STROKE })
        } else {
            canvas.drawRect(photoLeft, y, photoLeft + photoW, y + photoH, Paint(line).apply { style = Paint.Style.STROKE })
            canvas.drawText("Photo", photoLeft + photoW / 2f, y + photoH / 2f, Paint(gray).apply { textAlign = Paint.Align.CENTER; textSize = 11f })
        }

        val labelPaint = Paint(black).apply { textSize = 13f }
        val boldPaint = Paint(black).apply { textSize = 13f; isFakeBoldText = true }
        var infoY = y + 14f
        canvas.drawText("Name: ${student.name}", MARGIN, infoY, boldPaint); infoY += 20f
        canvas.drawText("Roll No: ${student.rollNumber}", MARGIN, infoY, labelPaint); infoY += 20f
        canvas.drawText("Class / Division: $divisionLabel", MARGIN, infoY, labelPaint); infoY += 20f
        canvas.drawText("Date: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(System.currentTimeMillis())}", MARGIN, infoY, labelPaint)
        y += photoH + 24f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
        y += 20f

        // Table
        val gradeOnly = displayMode == com.school.attendance.data.ReportCardDisplayMode.GRADE_ONLY
        val colSubject = MARGIN
        val colMarks = MARGIN + 220f
        val colMax = MARGIN + 320f
        val colGrade = MARGIN + 400f
        val rowH = 24f
        val headerPaint = Paint(black).apply { textSize = 12f; isFakeBoldText = true }
        canvas.drawText("Subject", colSubject, y, headerPaint)
        if (!gradeOnly) {
            canvas.drawText("Marks", colMarks, y, headerPaint)
            canvas.drawText("Max", colMax, y, headerPaint)
        }
        canvas.drawText("Grade", colGrade, y, headerPaint)
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_W - MARGIN, y, line)
        y += rowH - 8f

        val cellPaint = Paint(black).apply { textSize = 12f }
        card.subjects.forEach { s ->
            canvas.drawText(s.subjectName, colSubject, y, cellPaint)
            if (!gradeOnly) {
                canvas.drawText(if (s.allAbsent) "AB" else "%.1f".format(s.marks), colMarks, y, cellPaint)
                canvas.drawText("%.0f".format(s.maxMarks), colMax, y, cellPaint)
            }
            canvas.drawText(s.grade, colGrade, y, cellPaint)
            y += rowH
        }
        canvas.drawLine(MARGIN, y - rowH + 8f, PAGE_W - MARGIN, y - rowH + 8f, line)
        y += 6f

        // Total row
        val totalPaint = Paint(black).apply { textSize = 13f; isFakeBoldText = true }
        canvas.drawText("Total", colSubject, y, totalPaint)
        if (!gradeOnly) {
            canvas.drawText("%.1f".format(card.totalMarks), colMarks, y, totalPaint)
            canvas.drawText("%.0f".format(card.totalMax), colMax, y, totalPaint)
        }
        canvas.drawText(card.overallGrade, colGrade, y, totalPaint)
        y += 28f
        canvas.drawText("Percentage: %.1f%%".format(card.overallPercent), colSubject, y, boldPaint)
        y += 20f
        canvas.drawText("Overall Grade: ${card.overallGrade}", colSubject, y, boldPaint)

        // Signature blocks near the bottom of the page
        val sigY = PAGE_H - MARGIN - 30f
        canvas.drawLine(MARGIN, sigY, MARGIN + 140f, sigY, line)
        canvas.drawText("Class Teacher", MARGIN, sigY + 16f, subPaint.apply { textAlign = Paint.Align.LEFT })
        val midX = PAGE_W / 2f - 70f
        canvas.drawLine(midX, sigY, midX + 140f, sigY, line)
        canvas.drawText("Principal", midX, sigY + 16f, subPaint)
        val rightX = PAGE_W - MARGIN - 140f
        canvas.drawLine(rightX, sigY, rightX + 140f, sigY, line)
        canvas.drawText("Parent/Guardian", rightX, sigY + 16f, subPaint)

        doc.finishPage(page)
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report card"))
    }
}
