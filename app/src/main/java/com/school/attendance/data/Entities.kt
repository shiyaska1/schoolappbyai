package com.school.attendance.data

import androidx.room.Entity
import androidx.room.PrimaryKey

// ---------- masters ----------
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAtMillis: Long = 0
)

/** A division/section under a course, e.g. Course "Grade 5" + Division "A". */
@Entity(tableName = "divisions")
data class Division(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val courseId: Long = 0,
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val divisionId: Long = 0,
    val teacherId: Long = 0,
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "teachers")
data class Teacher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val designation: String = "",
    /** False for non-teaching staff (office, peon, driver, etc.) — registered and attendance-marked
     * the same way as teaching staff, just flagged separately for reporting. */
    val isTeachingStaff: Boolean = true,
    /** Fixed monthly salary, used to compute a per-day payroll figure from attendance:
     * perDay = monthlySalary / workingDaysInPeriod; payable = perDay * presentDays. */
    val monthlySalary: Double = 0.0,
    /** Optional 4-6 digit PIN for the login screen; blank = no PIN required. */
    val pin: String = "",
    /** Admin teachers see masters (courses/divisions/subjects/teachers) and Settings; regular
     * teachers only see attendance marking + reports for the subjects/divisions assigned to them. */
    val isAdmin: Boolean = false,
    val active: Boolean = true,
    val updatedAtMillis: Long = 0
)

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val rollNumber: String = "",
    val divisionId: Long = 0,
    val guardianName: String = "",
    val guardianPhone: String = "",
    /** Parent/guardian WhatsApp number (may differ from [guardianPhone]) — attendance reports are
     * shared here via WhatsApp, one student at a time. Stored with country code, digits only. */
    val guardianWhatsapp: String = "",
    val gender: String = "",
    val address: String = "",
    val photoPath: String = "",
    val admissionDateMillis: Long = 0,
    val active: Boolean = true,
    val updatedAtMillis: Long = 0
)

/** One student's attendance mark for one calendar day (and session, if the school marks
 * morning/afternoon separately — see [com.school.attendance.data.AppPrefs.attendanceMode]). */
@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    /** "FULL" (once-a-day mode) or "MORNING" / "AFTERNOON" (twice-a-day mode). */
    val session: String = "FULL",
    val divisionId: Long,
    val studentId: Long,
    val present: Boolean = true,
    val markedByTeacherId: Long = 0,
    /** [com.school.attendance.data.AppPrefs.deviceId] of the phone the mark was made on — lets a
     * merge tell two independently-created marks apart and attribute a record to its device. */
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)

/** One teacher's attendance mark for one day — taken by an admin, separate from student attendance. */
@Entity(tableName = "teacher_attendance_records")
data class TeacherAttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val teacherId: Long,
    val present: Boolean = true,
    val markedByAdminId: Long = 0,
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)

enum class AttendanceMode { ONCE, TWICE }
enum class AttendanceSession(val label: String) { FULL("Full day"), MORNING("Morning"), AFTERNOON("Afternoon") }

/** One non-working day: a weekly off is computed from [com.school.attendance.data.AppPrefs.weeklyOffDays]
 * and never stored here — this table only holds public holidays (fetched online) and one-off manual
 * holidays, both of which attendance marking and reports treat as "not a working day". */
@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Start-of-day millis, so one date always maps to exactly one row per scope. */
    val dateMillis: Long,
    val name: String,
    /** "PUBLIC" (fetched) or "MANUAL" (admin/teacher added). */
    val source: String = "MANUAL",
    /** 0 = whole school; otherwise only this one division is off (e.g. a class picnic/exam day). */
    val divisionId: Long = 0,
    val updatedAtMillis: Long = 0
)
