package com.school.attendance.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class MergeResult(
    var coursesAdded: Int = 0, var divisionsAdded: Int = 0, var subjectsAdded: Int = 0,
    var teachersAdded: Int = 0, var studentsAdded: Int = 0, var attendanceAdded: Int = 0,
    var attendanceUpdated: Int = 0, var holidaysAdded: Int = 0, var busesAdded: Int = 0,
    var teacherAttendanceAdded: Int = 0, var teacherAttendanceUpdated: Int = 0,
    var accountGroupsAdded: Int = 0, var accountHeadsAdded: Int = 0, var costCentersAdded: Int = 0,
    var journalEntriesAdded: Int = 0, var receiptsAdded: Int = 0, var expensesAdded: Int = 0,
    var customersAdded: Int = 0, var suppliersAdded: Int = 0, var purchasesAdded: Int = 0,
    var examsAdded: Int = 0, var examMarksAdded: Int = 0, var examMarksUpdated: Int = 0, var gradeBandsAdded: Int = 0
) {
    val summary: String
        get() = "+$coursesAdded courses, +$divisionsAdded divisions, +$subjectsAdded subjects, +$busesAdded buses, " +
            "+$teachersAdded teachers, +$studentsAdded students, +$attendanceAdded/$attendanceUpdated attendance, " +
            "+$teacherAttendanceAdded/$teacherAttendanceUpdated staff attendance, +$holidaysAdded holidays, " +
            "+$accountGroupsAdded groups, +$accountHeadsAdded heads, +$journalEntriesAdded journals, " +
            "+$receiptsAdded receipts, +$expensesAdded expenses, +$customersAdded customers, +$suppliersAdded suppliers, +$purchasesAdded purchases, " +
            "+$examsAdded exams, +$examMarksAdded/$examMarksUpdated exam marks, +$gradeBandsAdded grade bands"
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
                .put("aadharNumber", it.aadharNumber).put("bloodGroup", it.bloodGroup).put("religion", it.religion)
                .put("secondMobile", it.secondMobile).put("email", it.email).put("permanentAddress", it.permanentAddress)
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
                .put("aadharNumber", s.aadharNumber).put("bloodGroup", s.bloodGroup).put("religion", s.religion)
                .put("secondMobile", s.secondMobile).put("email", s.email).put("permanentAddress", s.permanentAddress)
                .put("admissionNumber", s.admissionNumber)
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

        // ---- accounting ----
        val acc = AccountingRepository(context)
        val groups = acc.allGroups()
        val heads = acc.allHeads()
        val costCenters = acc.allCostCenters()
        val jEntries = acc.allJournalEntries()
        val jLines = acc.allJournalLines()
        val receipts = acc.allReceipts()
        val expenses = acc.allExpenses()
        val customers = acc.allCustomers()
        val suppliers = acc.allSuppliers()
        val purchases = acc.allPurchases()

        val groupName = groups.associate { it.id to it.name }
        val headName = heads.associate { it.id to it.name }

        root.put("accountGroups", JSONArray(groups.map {
            JSONObject().put("name", it.name).put("nature", it.nature.name).put("isSystem", it.isSystem)
                .put("parentGroupName", it.parentGroupId?.let { pid -> groupName[pid] } ?: "")
        }))
        root.put("accountHeads", JSONArray(heads.map {
            JSONObject().put("name", it.name).put("groupName", groupName[it.groupId] ?: "")
                .put("openingBalance", it.openingBalance).put("openingIsDebit", it.openingIsDebit).put("isSystem", it.isSystem)
        }))
        root.put("costCenters", JSONArray(costCenters.map { JSONObject().put("name", it.name) }))
        root.put("journalEntries", JSONArray(jEntries.map { e ->
            JSONObject().put("voucherNo", e.voucherNo).put("dateMillis", e.dateMillis).put("narration", e.narration)
                .put("cashMode", e.cashMode).put("cashIsIn", e.cashIsIn).put("cashAmount", e.cashAmount)
                .put("voucherType", e.voucherType).put("updatedAtMillis", e.updatedAtMillis)
                .put("lines", JSONArray(jLines.filter { it.entryId == e.id }.map { l ->
                    JSONObject().put("headName", headName[l.headId] ?: l.headName).put("amount", l.amount).put("isDebit", l.isDebit)
                }))
        }))
        root.put("receipts", JSONArray(receipts.map { r ->
            val s = studentInfo[r.studentId]
            JSONObject().put("receiptNo", r.receiptNo).put("dateMillis", r.dateMillis)
                .put("studentRoll", s?.rollNumber ?: "").put("studentName", r.studentName.ifBlank { s?.name ?: "" })
                .put("divisionName", if (s != null) (divisionName[s.divisionId] ?: "") else "")
                .put("payFrom", r.payFrom).put("amount", r.amount).put("paymentMode", r.paymentMode)
                .put("toAccountName", headName[r.toAccountId] ?: "").put("narration", r.narration)
                .put("deviceId", r.deviceId).put("updatedAtMillis", r.updatedAtMillis)
        }))
        root.put("expenses", JSONArray(expenses.map { e ->
            JSONObject().put("voucherNo", e.voucherNo).put("dateMillis", e.dateMillis).put("description", e.description)
                .put("amount", e.amount).put("paymentMode", e.paymentMode).put("payTo", e.payTo)
                .put("fromAccountName", headName[e.fromAccountId] ?: "")
                .put("deviceId", e.deviceId).put("updatedAtMillis", e.updatedAtMillis)
        }))
        root.put("customers", JSONArray(customers.map { JSONObject().put("name", it.name).put("phone", it.phone).put("address", it.address) }))
        root.put("suppliers", JSONArray(suppliers.map { JSONObject().put("name", it.name).put("phone", it.phone).put("address", it.address) }))
        root.put("purchases", JSONArray(purchases.map { p ->
            JSONObject().put("purchaseNo", p.purchaseNo).put("dateMillis", p.dateMillis).put("supplierName", p.supplierName)
                .put("description", p.description).put("amount", p.amount).put("paymentMethod", p.paymentMethod)
                .put("fromAccountName", headName[p.fromAccountId] ?: "")
                .put("deviceId", p.deviceId).put("updatedAtMillis", p.updatedAtMillis)
        }))

        // ---- exams ----
        val exams = repo.examsOnce()
        val examMarks = repo.examMarksOnce()
        val gradeBands = repo.gradeBandsOnce()
        val subjectName = subjects.associate { it.id to it.name }
        val examName = exams.associate { it.id to it.name }

        root.put("exams", JSONArray(exams.map { e ->
            JSONObject().put("name", e.name).put("examType", e.examType).put("termGroup", e.termGroup)
                .put("divisionName", divisionName[e.divisionId] ?: "").put("dateMillis", e.dateMillis)
                .put("maxMarks", e.maxMarks).put("reportWeight", e.reportWeight).put("updatedAtMillis", e.updatedAtMillis)
        }))
        root.put("examMarks", JSONArray(examMarks.map { m ->
            val s = studentInfo[m.studentId]
            val exam = exams.firstOrNull { it.id == m.examId }
            JSONObject().put("examName", examName[m.examId] ?: "").put("divisionName", if (exam != null) (divisionName[exam.divisionId] ?: "") else "")
                .put("subjectName", subjectName[m.subjectId] ?: "")
                .put("studentRoll", s?.rollNumber ?: "").put("studentName", s?.name ?: "")
                .put("marksObtained", m.marksObtained).put("absent", m.absent)
                .put("deviceId", m.deviceId).put("updatedAtMillis", m.updatedAtMillis)
        }))
        root.put("gradeBands", JSONArray(gradeBands.map { g ->
            JSONObject().put("minPercent", g.minPercent).put("maxPercent", g.maxPercent).put("grade", g.grade)
                .put("remark", g.remark).put("updatedAtMillis", g.updatedAtMillis)
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

        val busIdCache = repo.busesOnce().associate { it.busNumber.trim().lowercase() to it.id }.toMutableMap()
        fun busId(busNumber: String): Long {
            if (busNumber.isBlank()) return 0
            val key = busNumber.trim().lowercase()
            busIdCache[key]?.let { return it }
            val id = kotlinx.coroutines.runBlocking { repo.upsertBus(Bus(busNumber = busNumber, updatedAtMillis = now)) }
            busIdCache[key] = id
            result.busesAdded++
            return id
        }
        root.optJSONArray("buses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = busId(o.optString("busNumber"))
                val route = o.optString("route")
                if (id != 0L && route.isNotBlank()) {
                    kotlinx.coroutines.runBlocking { repo.upsertBus(Bus(id = id, busNumber = o.optString("busNumber"), route = route, updatedAtMillis = now)) }
                }
            }
        }

        val teacherIdCache = existingTeachers.mapValues { it.value.id }.toMutableMap()
        fun teacherId(
            phone: String, name: String, designation: String = "", pin: String = "", isAdmin: Boolean = false,
            isTeachingStaff: Boolean = true, monthlySalary: Double = 0.0, busNumber: String = "",
            aadharNumber: String = "", bloodGroup: String = "", religion: String = "",
            secondMobile: String = "", email: String = "", permanentAddress: String = ""
        ): Long {
            if (name.isBlank() && phone.isBlank()) return 0
            val key = teacherKey(phone, name)
            teacherIdCache[key]?.let { return it }
            val id = kotlinx.coroutines.runBlocking {
                repo.upsertTeacher(Teacher(
                    name = name, phone = phone, designation = designation, pin = pin, isAdmin = isAdmin,
                    isTeachingStaff = isTeachingStaff, monthlySalary = monthlySalary, busId = busId(busNumber), updatedAtMillis = now,
                    aadharNumber = aadharNumber, bloodGroup = bloodGroup, religion = religion,
                    secondMobile = secondMobile, email = email, permanentAddress = permanentAddress
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
                    o.optBoolean("isAdmin"), o.optBoolean("isTeachingStaff", true), o.optDouble("monthlySalary", 0.0),
                    o.optString("busNumber"),
                    o.optString("aadharNumber"), o.optString("bloodGroup"), o.optString("religion"),
                    o.optString("secondMobile"), o.optString("email"), o.optString("permanentAddress")
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
        val subjectIdCache = repo.subjectsOnce().associate { "${it.divisionId}|${it.name.trim().lowercase()}" to it.id }

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
                            admissionDateMillis = o.optLong("admissionDateMillis"),
                            busId = busId(o.optString("busNumber")), username = o.optString("username"), password = o.optString("password"),
                            aadharNumber = o.optString("aadharNumber"), bloodGroup = o.optString("bloodGroup"), religion = o.optString("religion"),
                            secondMobile = o.optString("secondMobile"), email = o.optString("email"), permanentAddress = o.optString("permanentAddress"),
                            admissionNumber = o.optString("admissionNumber"), updatedAtMillis = now
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

        root.optJSONArray("teacherAttendance")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val teacherId = teacherIdCache[teacherKey(o.optString("teacherPhone"), "")] ?: continue
                val dateMillis = o.optLong("dateMillis")
                val incomingUpdatedAt = o.optLong("updatedAtMillis")
                val existing = kotlinx.coroutines.runBlocking { dao.teacherAttendanceFor(teacherId, dateMillis, dateMillis) }.firstOrNull()
                if (existing == null) {
                    kotlinx.coroutines.runBlocking {
                        dao.insertTeacherAttendance(listOf(TeacherAttendanceRecord(
                            dateMillis = dateMillis, teacherId = teacherId, present = o.optBoolean("present", true),
                            markedByAdminId = teacherIdCache[teacherKey(o.optString("markedByAdminPhone"), "")] ?: 0L,
                            selfMarked = o.optBoolean("selfMarked", false), deviceId = o.optString("deviceId"), updatedAtMillis = incomingUpdatedAt
                        )))
                    }
                    result.teacherAttendanceAdded++
                } else if (incomingUpdatedAt > existing.updatedAtMillis) {
                    kotlinx.coroutines.runBlocking { dao.insertTeacherAttendance(listOf(existing.copy(present = o.optBoolean("present", true), updatedAtMillis = incomingUpdatedAt))) }
                    result.teacherAttendanceUpdated++
                }
            }
        }

        // ---- accounting ----
        val acc = AccountingRepository(context)
        val existingGroups = acc.allGroups().associateBy { it.name.trim().lowercase() }.toMutableMap()
        val groupIdCache = existingGroups.mapValues { it.value.id }.toMutableMap()
        fun groupId(name: String, nature: AccountNature, parentName: String): Long {
            if (name.isBlank()) return 0
            val key = name.trim().lowercase()
            groupIdCache[key]?.let { return it }
            val parentId = if (parentName.isBlank()) null else groupIdCache[parentName.trim().lowercase()]
            val id = kotlinx.coroutines.runBlocking { acc.saveGroup(AccountGroup(name = name, nature = nature, parentGroupId = parentId, updatedAtMillis = now)) }
            groupIdCache[key] = id
            result.accountGroupsAdded++
            return id
        }
        root.optJSONArray("accountGroups")?.let { arr ->
            // Two passes so a child group can resolve its parent even if the parent appears later.
            for (pass in 0..1) for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val nature = runCatching { AccountNature.valueOf(o.optString("nature")) }.getOrDefault(AccountNature.ASSET)
                val parent = o.optString("parentGroupName")
                if (pass == 0 && parent.isNotBlank()) continue
                groupId(o.optString("name"), nature, parent)
            }
        }

        val existingHeads = acc.allHeads().associateBy { it.name.trim().lowercase() }.toMutableMap()
        val headIdCache = existingHeads.mapValues { it.value.id }.toMutableMap()
        fun headId(name: String, groupNameForHead: String): Long {
            if (name.isBlank()) return 0
            val key = name.trim().lowercase()
            headIdCache[key]?.let { return it }
            val gid = groupIdCache[groupNameForHead.trim().lowercase()] ?: return 0
            val id = kotlinx.coroutines.runBlocking { acc.saveHead(AccountHead(name = name, groupId = gid, updatedAtMillis = now)) }
            headIdCache[key] = id
            result.accountHeadsAdded++
            return id
        }
        root.optJSONArray("accountHeads")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.optString("name").trim().lowercase()
                if (headIdCache.containsKey(key)) continue
                val gid = groupIdCache[o.optString("groupName").trim().lowercase()] ?: continue
                val id = kotlinx.coroutines.runBlocking {
                    acc.saveHead(AccountHead(
                        name = o.optString("name"), groupId = gid, openingBalance = o.optDouble("openingBalance", 0.0),
                        openingIsDebit = o.optBoolean("openingIsDebit", true), updatedAtMillis = now
                    ))
                }
                headIdCache[key] = id
                result.accountHeadsAdded++
            }
        }

        val costCenterIdCache = acc.allCostCenters().associate { it.name.trim().lowercase() to it.id }.toMutableMap()
        root.optJSONArray("costCenters")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.getJSONObject(i).optString("name")
                val key = name.trim().lowercase()
                if (costCenterIdCache.containsKey(key)) continue
                costCenterIdCache[key] = kotlinx.coroutines.runBlocking { acc.saveCostCenter(CostCenter(name = name, updatedAtMillis = now)) }
                result.costCentersAdded++
            }
        }

        val existingJournalNos = acc.allJournalEntries().map { it.voucherNo }.toMutableSet()
        root.optJSONArray("journalEntries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vch = o.optString("voucherNo")
                if (vch.isBlank() || existingJournalNos.contains(vch)) continue
                val lines = o.optJSONArray("lines")?.let { larr ->
                    (0 until larr.length()).mapNotNull { j ->
                        val lo = larr.getJSONObject(j)
                        val hName = lo.optString("headName")
                        val hId = headIdCache[hName.trim().lowercase()] ?: return@mapNotNull null
                        JournalLine(entryId = 0, headId = hId, headName = hName, amount = lo.optDouble("amount", 0.0), isDebit = lo.optBoolean("isDebit", true))
                    }
                } ?: emptyList()
                if (lines.isEmpty()) continue
                kotlinx.coroutines.runBlocking {
                    acc.saveJournal(JournalEntry(
                        voucherNo = vch, dateMillis = o.optLong("dateMillis"), narration = o.optString("narration"),
                        cashMode = o.optString("cashMode"), cashIsIn = o.optBoolean("cashIsIn", true), cashAmount = o.optDouble("cashAmount", 0.0),
                        voucherType = o.optString("voucherType", JournalVoucherType.JOURNAL), updatedAtMillis = o.optLong("updatedAtMillis")
                    ), lines)
                }
                existingJournalNos.add(vch)
                result.journalEntriesAdded++
            }
        }

        val existingReceiptNos = acc.allReceipts().map { it.receiptNo }.toMutableSet()
        root.optJSONArray("receipts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val no = o.optString("receiptNo")
                if (no.isBlank() || existingReceiptNos.contains(no)) continue
                val divId = resolveDivisionId(o.optString("divisionName"))
                val sKey = studentKey(divId.toString(), o.optString("studentRoll"), o.optString("studentName"))
                kotlinx.coroutines.runBlocking {
                    acc.saveReceipt(Receipt(
                        receiptNo = no, dateMillis = o.optLong("dateMillis"), studentId = studentIdCache[sKey] ?: 0L,
                        studentName = o.optString("studentName"), payFrom = o.optString("payFrom"), amount = o.optDouble("amount", 0.0),
                        paymentMode = o.optString("paymentMode", "Cash"), toAccountId = headIdCache[o.optString("toAccountName").trim().lowercase()] ?: 0L,
                        narration = o.optString("narration"), deviceId = o.optString("deviceId"), updatedAtMillis = o.optLong("updatedAtMillis")
                    ))
                }
                existingReceiptNos.add(no)
                result.receiptsAdded++
            }
        }

        val existingExpenseNos = acc.allExpenses().map { it.voucherNo }.toMutableSet()
        root.optJSONArray("expenses")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val no = o.optString("voucherNo")
                if (no.isBlank() || existingExpenseNos.contains(no)) continue
                kotlinx.coroutines.runBlocking {
                    acc.saveExpense(Expense(
                        voucherNo = no, dateMillis = o.optLong("dateMillis"), description = o.optString("description"),
                        amount = o.optDouble("amount", 0.0), paymentMode = o.optString("paymentMode", "Cash"), payTo = o.optString("payTo"),
                        fromAccountId = headIdCache[o.optString("fromAccountName").trim().lowercase()] ?: 0L,
                        deviceId = o.optString("deviceId"), updatedAtMillis = o.optLong("updatedAtMillis")
                    ))
                }
                existingExpenseNos.add(no)
                result.expensesAdded++
            }
        }

        val existingCustomers = acc.allCustomers().map { "${it.name.trim().lowercase()}|${it.phone.trim()}" }.toMutableSet()
        root.optJSONArray("customers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = "${o.optString("name").trim().lowercase()}|${o.optString("phone").trim()}"
                if (existingCustomers.contains(key)) continue
                kotlinx.coroutines.runBlocking { acc.saveCustomer(Customer(name = o.optString("name"), phone = o.optString("phone"), address = o.optString("address"), updatedAtMillis = now)) }
                existingCustomers.add(key)
                result.customersAdded++
            }
        }

        val existingSuppliers = acc.allSuppliers().map { "${it.name.trim().lowercase()}|${it.phone.trim()}" }.toMutableSet()
        root.optJSONArray("suppliers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = "${o.optString("name").trim().lowercase()}|${o.optString("phone").trim()}"
                if (existingSuppliers.contains(key)) continue
                kotlinx.coroutines.runBlocking { acc.saveSupplier(Supplier(name = o.optString("name"), phone = o.optString("phone"), address = o.optString("address"), updatedAtMillis = now)) }
                existingSuppliers.add(key)
                result.suppliersAdded++
            }
        }

        val supplierIdByName = acc.allSuppliers().associate { it.name.trim().lowercase() to it.id }
        val existingPurchaseNos = acc.allPurchases().map { it.purchaseNo }.toMutableSet()
        root.optJSONArray("purchases")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val no = o.optString("purchaseNo")
                if (no.isBlank() || existingPurchaseNos.contains(no)) continue
                kotlinx.coroutines.runBlocking {
                    acc.savePurchase(Purchase(
                        purchaseNo = no, dateMillis = o.optLong("dateMillis"),
                        supplierId = supplierIdByName[o.optString("supplierName").trim().lowercase()] ?: 0L,
                        supplierName = o.optString("supplierName"), description = o.optString("description"),
                        amount = o.optDouble("amount", 0.0), paymentMethod = o.optString("paymentMethod", "Credit"),
                        fromAccountId = headIdCache[o.optString("fromAccountName").trim().lowercase()] ?: 0L,
                        deviceId = o.optString("deviceId"), updatedAtMillis = o.optLong("updatedAtMillis")
                    ))
                }
                existingPurchaseNos.add(no)
                result.purchasesAdded++
            }
        }

        // ---- exams ----
        val existingExams = repo.examsOnce().associateBy { "${it.divisionId}|${it.name.trim().lowercase()}" }.toMutableMap()
        root.optJSONArray("exams")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val divId = resolveDivisionId(o.optString("divisionName"))
                val key = "$divId|${o.optString("name").trim().lowercase()}"
                if (existingExams.containsKey(key)) continue
                val newExam = Exam(
                    name = o.optString("name"), examType = o.optString("examType", ExamType.UNIT_TEST), termGroup = o.optString("termGroup"),
                    divisionId = divId, dateMillis = o.optLong("dateMillis"), maxMarks = o.optDouble("maxMarks", 100.0),
                    reportWeight = o.optDouble("reportWeight", 100.0), updatedAtMillis = o.optLong("updatedAtMillis")
                )
                val id = kotlinx.coroutines.runBlocking { repo.upsertExam(newExam) }
                existingExams[key] = newExam.copy(id = id)
                result.examsAdded++
            }
        }
        val examIdCache = existingExams.mapValues { it.value.id }

        root.optJSONArray("examMarks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val divId = resolveDivisionId(o.optString("divisionName"))
                val examId = examIdCache["$divId|${o.optString("examName").trim().lowercase()}"] ?: continue
                val subjectId = subjectIdCache["$divId|${o.optString("subjectName").trim().lowercase()}"] ?: continue
                val sKey = studentKey(divId.toString(), o.optString("studentRoll"), o.optString("studentName"))
                val studentId = studentIdCache[sKey] ?: continue
                val incomingUpdatedAt = o.optLong("updatedAtMillis")
                val existing = kotlinx.coroutines.runBlocking { dao.examMarksFor(examId, subjectId) }.firstOrNull { it.studentId == studentId }
                if (existing == null) {
                    kotlinx.coroutines.runBlocking {
                        dao.insertExamMarks(listOf(ExamMark(
                            examId = examId, studentId = studentId, subjectId = subjectId,
                            marksObtained = o.optDouble("marksObtained", 0.0), absent = o.optBoolean("absent", false),
                            deviceId = o.optString("deviceId"), updatedAtMillis = incomingUpdatedAt
                        )))
                    }
                    result.examMarksAdded++
                } else if (incomingUpdatedAt > existing.updatedAtMillis) {
                    kotlinx.coroutines.runBlocking {
                        dao.insertExamMarks(listOf(existing.copy(
                            marksObtained = o.optDouble("marksObtained", 0.0), absent = o.optBoolean("absent", false), updatedAtMillis = incomingUpdatedAt
                        )))
                    }
                    result.examMarksUpdated++
                }
            }
        }

        val existingGradeBands = repo.gradeBandsOnce().associateBy { it.grade.trim().lowercase() }.toMutableMap()
        root.optJSONArray("gradeBands")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.optString("grade").trim().lowercase()
                if (key.isBlank()) continue
                val existing = existingGradeBands[key]
                val incomingUpdatedAt = o.optLong("updatedAtMillis")
                if (existing == null) {
                    kotlinx.coroutines.runBlocking {
                        repo.upsertGradeBand(GradeBand(
                            minPercent = o.optDouble("minPercent", 0.0), maxPercent = o.optDouble("maxPercent", 0.0),
                            grade = o.optString("grade"), remark = o.optString("remark"), updatedAtMillis = incomingUpdatedAt
                        ))
                    }
                    result.gradeBandsAdded++
                } else if (incomingUpdatedAt > existing.updatedAtMillis) {
                    kotlinx.coroutines.runBlocking {
                        repo.upsertGradeBand(existing.copy(
                            minPercent = o.optDouble("minPercent", existing.minPercent), maxPercent = o.optDouble("maxPercent", existing.maxPercent),
                            remark = o.optString("remark", existing.remark), updatedAtMillis = incomingUpdatedAt
                        ))
                    }
                }
            }
        }

        return result
    }
}
