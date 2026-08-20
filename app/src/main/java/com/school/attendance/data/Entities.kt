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

@Entity(tableName = "buses")
data class Bus(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val busNumber: String,
    val route: String = "",
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
    /** Only staff with this on see "My Attendance" (self, geo-fenced) — set per person in this
     * register, not a global toggle, since not every role should be able to self-mark. */
    val canSelfMarkAttendance: Boolean = false,
    /** The bus this person drives — only meaningful when [designation] is "Driver". Location
     * tracking is looked up by bus (so parents see a bus number, never the driver's identity). */
    val busId: Long = 0,
    /** Admin-granted, per-person: can this staff member open the bus-tracking screen at all?
     * Independent of [busId] — a driver doesn't need this on for their own bus to be tracked. */
    val canViewBusLocation: Boolean = false,
    val active: Boolean = true,
    val updatedAtMillis: Long = 0,
    // ---- optional identity/contact details ----
    val aadharNumber: String = "",
    val bloodGroup: String = "",
    val religion: String = "",
    /** Optional second contact number, distinct from [phone]. */
    val secondMobile: String = "",
    val email: String = "",
    /** Only filled in if different from the main address collected elsewhere. */
    val permanentAddress: String = "",
    val photoPath: String = ""
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
    /** Which bus this student rides, if any — a parent sees only the bus number/route from this,
     * never the driver's identity (see [Teacher.busId] and the location pull flow). */
    val busId: Long = 0,
    /** Optional app login for the student/parent (a future portal) — auto-generated when blank,
     * not something the admin has to think up. */
    val username: String = "",
    val password: String = "",
    val active: Boolean = true,
    val updatedAtMillis: Long = 0,
    // ---- optional identity/contact details ----
    val aadharNumber: String = "",
    val bloodGroup: String = "",
    val religion: String = "",
    /** Optional second guardian contact number, distinct from [guardianPhone]. */
    val secondMobile: String = "",
    val email: String = "",
    /** Only filled in if different from [address]. */
    val permanentAddress: String = "",
    /** School's own admission register number — distinct from [rollNumber] (the class roll list). */
    val admissionNumber: String = ""
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
    /** 0 when [selfMarked] is true — the teacher marked themself, not an admin. */
    val markedByAdminId: Long = 0,
    /** True when the teacher marked their own attendance (geo-fenced) rather than an admin marking it. */
    val selfMarked: Boolean = false,
    /** Captured only for a self-mark, so a flagged mark can be checked later against the school's geo-fence. */
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)

/** One GPS fix from a geo-tracked staff member (driver), saved locally the instant it's captured
 * so nothing is lost if there's no signal — [pushed] flips to true once it's made it to the
 * server, and a background flush retries whatever's still false when connectivity returns. The
 * accumulated rows are also this device's own local route history. */
@Entity(tableName = "location_pings")
data class LocationPing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teacherId: Long,
    val lat: Double,
    val lng: Double,
    /** Meters/second from the location fix, 0 if the provider didn't supply one. */
    val speedMps: Float = 0f,
    val dateMillis: Long,
    val pushed: Boolean = false
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

object ExamType {
    const val UNIT_TEST = "UNIT_TEST"
    const val TERM_EXAM = "TERM_EXAM"
}

/** One exam session for one division, e.g. "Unit Test 1" or "Term 1 Exam". Several exams can share
 * the same [termGroup] (e.g. two unit tests + a term exam all feeding "Term 1") — the report card
 * consolidates every exam in a group for a subject, scaling each one's [maxMarks] entry down (or up)
 * to its [reportWeight] share of that subject's final total, so a teacher can keep marking out of
 * 100 while a unit test only counts for, say, 20 of the term's 100. */
@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val examType: String = ExamType.UNIT_TEST,
    val termGroup: String = "",
    val divisionId: Long = 0,
    val dateMillis: Long = 0,
    /** What the teacher enters marks against, e.g. 100. */
    val maxMarks: Double = 100.0,
    /** This exam's contribution to its [termGroup]'s subject total on the report card, e.g. 20. */
    val reportWeight: Double = 100.0,
    val updatedAtMillis: Long = 0
)

/** One student's mark in one subject for one [Exam]. */
@Entity(tableName = "exam_marks")
data class ExamMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val studentId: Long,
    val subjectId: Long,
    val marksObtained: Double = 0.0,
    val absent: Boolean = false,
    val enteredByTeacherId: Long = 0,
    val deviceId: String = "",
    val updatedAtMillis: Long = 0
)

/** A percentage band mapped to a letter grade for report cards, e.g. 90-100 -> "A+". Bands should
 * not overlap; [grade] is treated as the natural key so re-editing a band's range updates it in place. */
@Entity(tableName = "grade_bands")
data class GradeBand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minPercent: Double,
    val maxPercent: Double,
    val grade: String,
    val remark: String = "",
    val updatedAtMillis: Long = 0
)

/** A message between a parent and their child's teachers, or a school-wide/division broadcast.
 * Synced the same way as everything else (push/pull) rather than a separate mailbox protocol —
 * that keeps it working with any server that already speaks the export/import JSON, and re-pulling
 * is naturally harmless since [com.school.attendance.data.AttendanceSync] dedupes what's already local. */
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    /** "SCHOOL" (admin broadcast to a division or the whole school), "TEACHER", or "PARENT". */
    val fromRole: String,
    /** Set when [fromRole] is TEACHER or SCHOOL. */
    val fromTeacherId: Long = 0,
    /** The thread this belongs to: a specific student's parent<->teacher conversation. 0 with a
     * [fromRole] of SCHOOL means a broadcast (to [divisionId], or the whole school if that's 0 too). */
    val studentId: Long = 0,
    val divisionId: Long = 0,
    val body: String,
    val deviceId: String = "",
    val updatedAtMillis: Long = 0,
    /** Local-only bookkeeping (never synced) — true once [com.school.attendance.sync.MessageSync]
     * has successfully pushed this message, so it isn't re-sent on the next push cycle. */
    val pushed: Boolean = false
)
