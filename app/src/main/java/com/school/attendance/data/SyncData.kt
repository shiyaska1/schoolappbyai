package com.school.attendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MergeResult(
    var coursesAdded: Int = 0, var divisionsAdded: Int = 0, var subjectsAdded: Int = 0,
    var teachersAdded: Int = 0, var studentsAdded: Int = 0, var attendanceAdded: Int = 0,
    var attendanceUpdated: Int = 0, var holidaysAdded: Int = 0, var busesAdded: Int = 0,
    var teacherAttendanceAdded: Int = 0, var teacherAttendanceUpdated: Int = 0
) {
    val summary: String
        get() = "+$coursesAdded courses, +$divisionsAdded divisions, +$subjectsAdded subjects, +$busesAdded buses, " +
            "+$teachersAdded teachers, +$studentsAdded students, +$attendanceAdded/$attendanceUpdated attendance, " +
            "+$teacherAttendanceAdded/$teacherAttendanceUpdated staff attendance, +$holidaysAdded holidays"
}

/**
 * JSON export/import of the whole school dataset, keyed by natural names/roll numbers rather than
 * local autoincrement ids — so two independently-provisioned phones (or a phone and the server) can
 * exchange data and merge it without id collisions. Mirrors the POS billing app's push/pull cloud
 * sync (same idea: export -> upload; download -> merge), just JSON instead of a zip, since there are
 * no photo/attachment binaries in the sync payload (photos stay local for now).
 */
object AttendanceSync {

    private fun divisionKey(courseName: String, divisionName: String) = "${courseName.trim().lowercase()}|${divisionName.trim().lowercase()}"
    private fun studentKey(divKey: String, rollNumber: String, name: String) =
        if (rollNumber.isNotBlank()) "$divKey|roll:${rollNumber.trim().lowercase()}" else "$divKey|name:${name.trim().lowercase()}"
    private fun teacherKey(phone: String, name: String) =
        if (phone.isNotBlank()) "phone:${phone.trim()}" else "name:${name.trim().lowercase()}"

    suspend fun exportJson(context: Context): String {
        val repo = Repository(context)
        val courses = repo.coursesOnce()
        val divisions = repo.divisionsOnce()
        val subjects = repo.subjectsOnce()
        val teachers = repo.teachersOnce()
        val students = repo.studentsOnce()
        val buses = repo.busesOnce()
        val dao = AppDatabase.get(context).dao()
        val attendance = dao.attendanceInRange(0L, Long.MAX_VALUE)
        val teacherAttendance = dao.teacherAttendanceForDay(0L, Long.MAX_VALUE)
        val holidays = dao.holidaysOnce()

        val courseName = courses.associate { it.id to it.name }
        val divisionName = divisions.associate { it.id to it.name }
        val teacherPhone = teachers.associate { it.id to it.phone }
        val busNumber = buses.associate { it.id to it.busNumber }
        val studentInfo = students.associate { it.id to it }

        val root = JSONObject()
        root.put("courses", JSONArray(courses.map { JSONObject().put("name", it.name) }))
        root.put("divisions", JSONArray(divisions.map {
            JSONObject().put("name", it.name).put("courseName", courseName[it.courseId] ?: "")
        }))
        root.put("subjects", JSONArray(subjects.map {
            JSONObject().put("name", it.name)
                .put("divisionName", divisionName[it.divisionId] ?: "")
                .put("teacherPhone", teacherPhone[it.teacherId] ?: "")
        }))
        root.put("buses", JSONArray(buses.map { JSONObject().put("busNumber", it.busNumber).put("route", it.route) }))
        root.put("teachers", JSONArray(teachers.map {
            JSONObject().put("name", it.name).put("phone", it.phone).put("designation", it.designation)
                .put("pin", it.pin).put("isAdmin", it.isAdmin).put("isTeachingStaff", it.isTeachingStaff)
                .put("monthlySalary", it.monthlySalary).put("canSelfMarkAttendance", it.canSelfMarkAttendance)
                .put("busNumber", busNumber[it.busId] ?: "")
        }))
        root.put("students", JSONArray(students.map { s ->
            JSONObject().put("name", s.name).put("rollNumber", s.rollNumber)
                .put("divisionName", divisionName[s.divisionId] ?: "")
                .put("guardianName", s.guardianName).put("guardianPhone", s.guardianPhone)
                .put("guardianWhatsapp", s.guardianWhatsapp)
                .put("gender", s.gender).put("address", s.address)
                .put("admissionDateMillis", s.admissionDateMillis)
                .put("busNumber", busNumber[s.busId] ?: "")
                .put("username", s.username).put("password", s.password)
        }))
        root.put("attendance", JSONArray(attendance.map { a ->
            val s = studentInfo[a.studentId]
            JSONObject().put("studentRoll", s?.rollNumber ?: "").put("studentName", s?.name ?: "")
                .put("divisionName", divisionName[a.divisionId] ?: "")
                .put("dateMillis", a.dateMillis).put("session", a.session).put("present", a.present)
                .put("markedByTeacherPhone", teacherPhone[a.markedByTeacherId] ?: "")
                .put("deviceId", a.deviceId).put("updatedAtMillis", a.updatedAtMillis)
        }))
        root.put("teacherAttendance", JSONArray(teacherAttendance.map { a ->
            JSONObject().put("teacherPhone", teacherPhone[a.teacherId] ?: "")
                .put("dateMillis", a.dateMillis).put("present", a.present)
                .put("markedByAdminPhone", teacherPhone[a.markedByAdminId] ?: "").put("selfMarked", a.selfMarked)
                .put("deviceId", a.deviceId).put("updatedAtMillis", a.updatedAtMillis)
        }))
        root.put("holidays", JSONArray(holidays.map { h ->
            JSONObject().put("dateMillis", h.dateMillis).put("name", h.name).put("source", h.source)
                .put("divisionName", if (h.divisionId == 0L) "" else (divisionName[h.divisionId] ?: ""))
        }))
        return root.toString()
    }

    suspend fun importJson(context: Context, json: String): MergeResult {
        val repo = Repository(context)
        val dao = AppDatabase.get(context).dao()
        val result = MergeResult()
        val now = System.currentTimeMillis()

        val existingCourses = repo.coursesOnce().associateBy { it.name.trim().lowercase() }.toMutableMap()
        val existingDivisions = repo.divisionsOnce().associateBy { divisionKey(existingCourses.values.firstOrNull { c -> c.id == it.courseId }?.name ?: "", it.name) }.toMutableMap()
        val existingTeachers = repo.teachersOnce().associateBy { teacherKey(it.phone, it.name) }.toMutableMap()
        val existingStudents = repo.studentsOnce()
        val courseNameById = existingCourses.values.associate { it.id to it.name }.toMutableMap()

        val root = JSONObject(json)

        fun courseId(name: String): Long {
            if (name.isBlank()) return 0
            val key = name.trim().lowercase()
            existingCourses[key]?.let { return it.id }
            val id = kotlinx.coroutines.runBlocking { repo.upsertCourse(Course(name = name, updatedAtMillis = now)) }
            existingCourses[key] = Course(id = id, name = name, updatedAtMillis = now)
            courseNameById[id] = name
            result.coursesAdded++
            return id
        }

        val divisionIdCache = existingDivisions.mapValues { it.value.id }.toMutableMap()
        fun divisionId(divName: String, courseName: String): Long {
            if (divName.isBlank()) return 0
            val cid = courseId(courseName)
            val key = divisionKey(courseName, divName)
            divisionIdCache[key]?.let { return it }
            val id = kotlinx.coroutines.runBlocking { repo.upsertDivision(Division(name = divName, courseId = cid, updatedAtMillis = now)) }
            divisionIdCache[key] = id
            result.divisionsAdded++
            return id
        }

        val teacherIdCache = existingTeachers.mapValues { it.value.id }.toMutableMap()
        fun teacherId(
            phone: String, name: String, designation: String = "", pin: String = "", isAdmin: Boolean = false,
            isTeachingStaff: Boolean = true, monthlySalary: Double = 0.0
        ): Long {
            if (name.isBlank() && phone.isBlank()) return 0
            val key = teacherKey(phone, name)
            teacherIdCache[key]?.let { return it }
            val id = kotlinx.coroutines.runBlocking {
                repo.upsertTeacher(Teacher(
                    name = name, phone = phone, designation = designation, pin = pin, isAdmin = isAdmin,
                    isTeachingStaff = isTeachingStaff, monthlySalary = monthlySalary, updatedAtMillis = now
                ))
            }
            teacherIdCache[key] = id
            result.teachersAdded++
            return id
        }

        val studentIdCache = existingStudents.associate {
            val divName = "" // resolved below only when needed for lookups during attendance import
            studentKey("", it.rollNumber, it.name) to it.id
        }.toMutableMap()
        // Build a richer cache keyed by full division-aware key too.
        val divisionNameById = mutableMapOf<Long, String>()

        root.optJSONArray("courses")?.let { arr ->
            for (i in 0 until arr.length()) courseId(arr.getJSONObject(i).optString("name"))
        }
        root.optJSONArray("divisions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                divisionId(o.optString("name"), o.optString("courseName"))
            }
        }
        root.optJSONArray("teachers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                teacherId(
                    o.optString("phone"), o.optString("name"), o.optString("designation"), o.optString("pin"),
                    o.optBoolean("isAdmin"), o.optBoolean("isTeachingStaff", true), o.optDouble("monthlySalary", 0.0)
                )
            }
        }
        root.optJSONArray("subjects")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val divName = o.optString("divisionName")
                val divId = divisionIdCache.entries.firstOrNull { it.key.endsWith("|" + divName.trim().lowercase()) }?.value ?: 0L
                val tId = teacherIdCache.entries.firstOrNull { it.key == teacherKey(o.optString("teacherPhone"), "") }?.value ?: 0L
                kotlinx.coroutines.runBlocking { repo.upsertSubject(Subject(name = o.optString("name"), divisionId = divId, teacherId = tId, updatedAtMillis = now)) }
                result.subjectsAdded++
            }
        }

        fun resolveDivisionId(divName: String): Long =
            divisionIdCache.entries.firstOrNull { it.key.endsWith("|" + divName.trim().lowercase()) }?.value ?: 0L

        root.optJSONArray("students")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val divId = resolveDivisionId(o.optString("divisionName"))
                val key = studentKey(divId.toString(), o.optString("rollNumber"), o.optString("name"))
                if (studentIdCache.containsKey(key)) continue
                val id = kotlinx.coroutines.runBlocking {
                    repo.upsertStudent(
                        Student(
                            name = o.optString("name"), rollNumber = o.optString("rollNumber"), divisionId = divId,
                            guardianName = o.optString("guardianName"), guardianPhone = o.optString("guardianPhone"),
                            guardianWhatsapp = o.optString("guardianWhatsapp"),
                            gender = o.optString("gender"), address = o.optString("address"),
                            admissionDateMillis = o.optLong("admissionDateMillis"), updatedAtMillis = now
                        )
                    )
                }
                studentIdCache[key] = id
                divisionNameById[divId] = o.optString("divisionName")
                result.studentsAdded++
            }
        }

        root.optJSONArray("attendance")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val divId = resolveDivisionId(o.optString("divisionName"))
                val key = studentKey(divId.toString(), o.optString("studentRoll"), o.optString("studentName"))
                val studentId = studentIdCache[key] ?: continue
                val dateMillis = o.optLong("dateMillis")
                val session = o.optString("session", "FULL")
                val incomingUpdatedAt = o.optLong("updatedAtMillis")
                val existing = kotlinx.coroutines.runBlocking {
                    dao.attendanceForStudent(studentId, dateMillis, dateMillis + 1).firstOrNull { it.session == session }
                }
                if (existing == null) {
                    kotlinx.coroutines.runBlocking {
                        dao.insertAttendance(listOf(AttendanceRecord(
                            dateMillis = dateMillis, session = session, divisionId = divId, studentId = studentId,
                            present = o.optBoolean("present", true),
                            markedByTeacherId = teacherIdCache[teacherKey(o.optString("markedByTeacherPhone"), "")] ?: 0L,
                            deviceId = o.optString("deviceId"), updatedAtMillis = incomingUpdatedAt
                        )))
                    }
                    result.attendanceAdded++
                } else if (incomingUpdatedAt > existing.updatedAtMillis) {
                    kotlinx.coroutines.runBlocking {
                        dao.insertAttendance(listOf(existing.copy(present = o.optBoolean("present", true), updatedAtMillis = incomingUpdatedAt)))
                    }
                    result.attendanceUpdated++
                }
            }
        }

        root.optJSONArray("holidays")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val divName = o.optString("divisionName")
                val divId = if (divName.isBlank()) 0L else resolveDivisionId(divName)
                val date = o.optLong("dateMillis")
                val already = kotlinx.coroutines.runBlocking { dao.holidaysFor(divId, date, date) }
                if (already.none { it.name == o.optString("name") }) {
                    kotlinx.coroutines.runBlocking {
                        dao.upsertHoliday(Holiday(dateMillis = date, name = o.optString("name"), source = o.optString("source", "MANUAL"), divisionId = divId, updatedAtMillis = now))
                    }
                    result.holidaysAdded++
                }
            }
        }

        return result
    }
}
