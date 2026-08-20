package com.school.attendance.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDay(millis: Long): Long = startOfDay(millis) + 24 * 60 * 60 * 1000L - 1

data class AttendanceSummary(val workingDays: Int, val present: Int, val absent: Int) {
    val percent: Int get() = if (workingDays > 0) present * 100 / workingDays else 0
}

data class Payroll(val summary: AttendanceSummary, val perDayRate: Double, val payable: Double)

class Repository(context: Context) {
    private val dao = AppDatabase.get(context).dao()
    private val prefs = AppPrefs(context)

    val courses: Flow<List<Course>> get() = dao.courses()
    val divisions: Flow<List<Division>> get() = dao.divisions()
    val subjects: Flow<List<Subject>> get() = dao.subjects()
    val teachers: Flow<List<Teacher>> get() = dao.teachers()
    val students: Flow<List<Student>> get() = dao.students()
    val holidays: Flow<List<Holiday>> get() = dao.holidays()
    val buses: Flow<List<Bus>> get() = dao.buses()

    suspend fun upsertBus(b: Bus) = dao.upsertBus(b.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteBus(b: Bus) = dao.deleteBus(b)
    suspend fun busesOnce() = dao.busesOnce()

    /** The bus number a student rides, resolved for display — never the driver's identity. */
    suspend fun busNumberForStudent(studentId: Long): String? {
        val student = dao.studentById(studentId) ?: return null
        if (student.busId == 0L) return null
        return dao.busesOnce().firstOrNull { it.id == student.busId }?.busNumber
    }

    /** The bus number a driver (Teacher.busId) is assigned to — what their phone pushes location under. */
    suspend fun busNumberForDriver(teacherId: Long): String? {
        val teacher = dao.teacherById(teacherId) ?: return null
        if (teacher.busId == 0L) return null
        return dao.busesOnce().firstOrNull { it.id == teacher.busId }?.busNumber
    }

    /** The driver currently assigned to a bus — used only to dial them (never shown as text) or,
     * for admin/teacher, to label the fleet list. Null if no one's assigned. */
    suspend fun driverForBus(busId: Long): Teacher? = dao.teachersOnce().firstOrNull { it.busId == busId }

    suspend fun upsertCourse(c: Course) = dao.upsertCourse(c.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteCourse(c: Course) = dao.deleteCourse(c)
    suspend fun upsertDivision(d: Division) = dao.upsertDivision(d.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteDivision(d: Division) = dao.deleteDivision(d)
    suspend fun upsertSubject(s: Subject) = dao.upsertSubject(s.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteSubject(s: Subject) = dao.deleteSubject(s)
    suspend fun upsertTeacher(t: Teacher) = dao.upsertTeacher(t.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteTeacher(t: Teacher) = dao.deleteTeacher(t.copy(active = false))
    suspend fun upsertStudent(s: Student) = dao.upsertStudent(s.copy(updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteStudent(s: Student) = dao.updateStudent(s.copy(active = false))
    suspend fun studentsInDivision(divisionId: Long) = dao.studentsInDivision(divisionId)
    suspend fun teacherById(id: Long) = dao.teacherById(id)
    suspend fun teachersOnce() = dao.teachersOnce()
    suspend fun coursesOnce() = dao.coursesOnce()
    suspend fun divisionsOnce() = dao.divisionsOnce()
    suspend fun subjectsOnce() = dao.subjectsOnce()
    suspend fun studentsOnce() = dao.studentsOnce()

    suspend fun upsertHoliday(h: Holiday) = dao.upsertHoliday(h.copy(dateMillis = startOfDay(h.dateMillis), updatedAtMillis = System.currentTimeMillis()))
    suspend fun deleteHoliday(h: Holiday) = dao.deleteHoliday(h)

    /** Erases every table — used by a "Complete" restore, which replaces rather than merges. */
    suspend fun wipeAll() = dao.wipeAll()

    // ---- location pings ----
    suspend fun recordLocationPing(teacherId: Long, lat: Double, lng: Double, speedMps: Float): Long =
        dao.insertLocationPing(LocationPing(teacherId = teacherId, lat = lat, lng = lng, speedMps = speedMps, dateMillis = System.currentTimeMillis()))
    suspend fun unpushedLocationPings() = dao.unpushedLocationPings()
    suspend fun markLocationPingsPushed(ids: List<Long>) = dao.markLocationPingsPushed(ids)
    suspend fun routeToday(teacherId: Long): List<LocationPing> = dao.routeFor(teacherId, startOfDay(System.currentTimeMillis()), endOfDay(System.currentTimeMillis()))

    // ---- messages ----
    fun messagesForStudent(studentId: Long) = dao.messagesForStudent(studentId)
    fun broadcastsForDivision(divisionId: Long) = dao.broadcastsForDivision(divisionId)
    fun allMessages() = dao.allMessages()

    suspend fun sendMessage(fromRole: String, fromTeacherId: Long, studentId: Long, divisionId: Long, body: String, deviceId: String) {
        dao.insertMessage(Message(
            dateMillis = System.currentTimeMillis(), fromRole = fromRole, fromTeacherId = fromTeacherId,
            studentId = studentId, divisionId = divisionId, body = body, deviceId = deviceId,
            updatedAtMillis = System.currentTimeMillis()
        ))
    }

    /** True if this date is a working day for [divisionId] — not a weekly-off, not a holiday. */
    suspend fun isWorkingDay(dateMillis: Long, divisionId: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        if (cal.get(Calendar.DAY_OF_WEEK) in prefs.weeklyOffDays) return false
        val day = startOfDay(dateMillis)
        return dao.holidaysFor(divisionId, day, day).isEmpty()
    }

    /** Whole-school working-day check (used for staff attendance) — only whole-school holidays apply. */
    suspend fun isSchoolWorkingDay(dateMillis: Long): Boolean = isWorkingDay(dateMillis, 0L)

    suspend fun attendanceForDay(divisionId: Long, session: String, dateMillis: Long): List<AttendanceRecord> =
        dao.attendanceForDay(divisionId, session, startOfDay(dateMillis), endOfDay(dateMillis))

    suspend fun saveAttendance(
        divisionId: Long, session: String, dateMillis: Long, teacherId: Long, deviceId: String, marks: Map<Long, Boolean>
    ) {
        val day = startOfDay(dateMillis)
        val now = System.currentTimeMillis()
        val records = marks.map { (studentId, present) ->
            AttendanceRecord(
                dateMillis = day, session = session, divisionId = divisionId, studentId = studentId,
                present = present, markedByTeacherId = teacherId, deviceId = deviceId, updatedAtMillis = now
            )
        }
        dao.saveAttendanceForDay(divisionId, session, day, endOfDay(dateMillis), records)
    }

    /** Attendance summary for one student over [from]..[to], counting only working days. */
    suspend fun summaryForStudent(studentId: Long, divisionId: Long, from: Long, to: Long): AttendanceSummary {
        val records = dao.attendanceForStudent(studentId, startOfDay(from), endOfDay(to))
        return summarize(records, divisionId, from, to)
    }

    /** Attendance summary for a whole division (all students combined) over [from]..[to]. */
    suspend fun summaryForDivision(divisionId: Long, from: Long, to: Long): AttendanceSummary {
        val records = dao.attendanceForDivision(divisionId, startOfDay(from), endOfDay(to))
        return summarize(records, divisionId, from, to)
    }

    private suspend fun summarize(records: List<AttendanceRecord>, divisionId: Long, from: Long, to: Long): AttendanceSummary {
        var working = 0
        var day = startOfDay(from)
        val end = startOfDay(to)
        while (day <= end) {
            if (isWorkingDay(day, divisionId)) working++
            day += 24 * 60 * 60 * 1000L
        }
        val present = records.count { it.present }
        val absent = records.count { !it.present }
        return AttendanceSummary(working, present, absent)
    }

    // ---- teacher / staff attendance ----
    suspend fun teacherAttendanceForDay(dateMillis: Long): List<TeacherAttendanceRecord> =
        dao.teacherAttendanceForDay(startOfDay(dateMillis), endOfDay(dateMillis))

    suspend fun saveTeacherAttendance(dateMillis: Long, adminId: Long, deviceId: String, marks: Map<Long, Boolean>) {
        val day = startOfDay(dateMillis)
        val now = System.currentTimeMillis()
        val records = marks.map { (teacherId, present) ->
            TeacherAttendanceRecord(dateMillis = day, teacherId = teacherId, present = present, markedByAdminId = adminId, deviceId = deviceId, updatedAtMillis = now)
        }
        dao.saveTeacherAttendanceForDay(day, endOfDay(dateMillis), records)
    }

    /** A teacher marking their own attendance (geo-fenced) — replaces only this teacher's row for
     * today, unlike [saveTeacherAttendance] which replaces the whole day (admin marking everyone). */
    suspend fun saveSelfAttendance(teacherId: Long, deviceId: String, latitude: Double, longitude: Double) {
        val day = startOfDay(System.currentTimeMillis())
        val now = System.currentTimeMillis()
        dao.saveOneTeacherAttendanceForDay(
            teacherId, day, endOfDay(now),
            TeacherAttendanceRecord(dateMillis = day, teacherId = teacherId, present = true, selfMarked = true, latitude = latitude, longitude = longitude, deviceId = deviceId, updatedAtMillis = now)
        )
    }

    suspend fun alreadySelfMarkedToday(teacherId: Long): Boolean {
        val day = startOfDay(System.currentTimeMillis())
        return dao.teacherAttendanceFor(teacherId, day, endOfDay(day)).any { it.selfMarked }
    }

    suspend fun teacherSummary(teacherId: Long, from: Long, to: Long): AttendanceSummary {
        val records = dao.teacherAttendanceFor(teacherId, startOfDay(from), endOfDay(to))
        var working = 0
        var day = startOfDay(from)
        val end = startOfDay(to)
        while (day <= end) {
            if (isSchoolWorkingDay(day)) working++
            day += 24 * 60 * 60 * 1000L
        }
        return AttendanceSummary(working, records.count { it.present }, records.count { !it.present })
    }

    /** Payroll for one teacher over [from]..[to]: per-day rate = monthlySalary / workingDaysInPeriod,
     * payable = perDay * presentDays. A simple, common no-pay-for-absence model — schools that pay
     * full salary regardless of a few absences can still use the raw present/absent counts shown alongside it. */
    suspend fun payrollFor(teacher: Teacher, from: Long, to: Long): Payroll {
        val summary = teacherSummary(teacher.id, from, to)
        val perDay = if (summary.workingDays > 0) teacher.monthlySalary / summary.workingDays else 0.0
        return Payroll(summary, perDay, perDay * summary.present)
    }

    /** Downloads India's public holidays for [year] from the free Nager.Date API and stores them
     * (source = PUBLIC, whole school). No API key required. */
    suspend fun fetchIndianPublicHolidays(year: Int): Result<Int> = runCatching {
        val url = URL("https://date.nager.at/api/v3/PublicHolidays/$year/IN")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        val code = conn.responseCode
        if (code !in 200..299) { conn.disconnect(); throw java.io.IOException("HTTP $code") }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        val arr = org.json.JSONArray(body)
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val holidays = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val dateStr = o.optString("date")
            val name = o.optString("localName", o.optString("name", "Holiday"))
            val date = runCatching { fmt.parse(dateStr) }.getOrNull() ?: return@mapNotNull null
            Holiday(dateMillis = startOfDay(date.time), name = name, source = "PUBLIC", divisionId = 0, updatedAtMillis = System.currentTimeMillis())
        }
        dao.insertHolidays(holidays)
        holidays.size
    }
}
