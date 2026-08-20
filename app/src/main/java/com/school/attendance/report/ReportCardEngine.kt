package com.school.attendance.report

import com.school.attendance.data.Exam
import com.school.attendance.data.ExamMark
import com.school.attendance.data.GradeBand
import com.school.attendance.data.Student
import com.school.attendance.data.Subject

/** One subject's consolidated result within a report card: every exam sharing the report's
 * [com.school.attendance.data.Exam.termGroup] is scaled from its own entry max down (or up) to its
 * [com.school.attendance.data.Exam.reportWeight] share, then summed — so a teacher can keep marking a
 * unit test out of 100 while it only counts for, say, 20 of the term's 100. */
data class SubjectResult(
    val subjectId: Long,
    val subjectName: String,
    val marks: Double,
    val maxMarks: Double,
    val percent: Double,
    val grade: String,
    val allAbsent: Boolean
)

data class StudentReportCard(
    val studentId: Long,
    val subjects: List<SubjectResult>,
    val totalMarks: Double,
    val totalMax: Double,
    val overallPercent: Double,
    val overallGrade: String
)

object ReportCardEngine {
    fun gradeFor(percent: Double, bands: List<GradeBand>): String =
        bands.firstOrNull { percent >= it.minPercent && percent <= it.maxPercent }?.grade ?: "-"

    /** Every distinct termGroup name present for a division's exams — what a report-card screen lets you pick. */
    fun termGroupsFor(divisionId: Long, exams: List<Exam>): List<String> =
        exams.filter { it.divisionId == divisionId && it.termGroup.isNotBlank() }
            .map { it.termGroup }.distinct().sortedBy { it.lowercase() }

    fun build(
        termGroup: String,
        divisionId: Long,
        students: List<Student>,
        subjects: List<Subject>,
        exams: List<Exam>,
        marks: List<ExamMark>,
        bands: List<GradeBand>
    ): List<StudentReportCard> {
        val groupExams = exams.filter { it.divisionId == divisionId && it.termGroup == termGroup }
        val divSubjects = subjects.filter { it.divisionId == divisionId }
        return students.filter { it.divisionId == divisionId }.map { s ->
            val subjectResults = divSubjects.map { subj ->
                var scaled = 0.0
                var maxWeight = 0.0
                var entered = 0
                var absentCount = 0
                groupExams.forEach { ex ->
                    val m = marks.firstOrNull { it.examId == ex.id && it.studentId == s.id && it.subjectId == subj.id }
                    maxWeight += ex.reportWeight
                    if (m != null) {
                        entered++
                        if (m.absent) absentCount++
                        else if (ex.maxMarks > 0) scaled += (m.marksObtained / ex.maxMarks) * ex.reportWeight
                    }
                }
                val percent = if (maxWeight > 0) (scaled / maxWeight) * 100 else 0.0
                SubjectResult(
                    subj.id, subj.name, scaled, maxWeight, percent,
                    if (entered > 0 && absentCount == entered) "AB" else gradeFor(percent, bands),
                    allAbsent = entered > 0 && absentCount == entered
                )
            }
            val totalMarks = subjectResults.sumOf { it.marks }
            val totalMax = subjectResults.sumOf { it.maxMarks }
            val overallPercent = if (totalMax > 0) (totalMarks / totalMax) * 100 else 0.0
            StudentReportCard(s.id, subjectResults, totalMarks, totalMax, overallPercent, gradeFor(overallPercent, bands))
        }
    }
}
