package com.school.attendance.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolDao {
    // ---- courses ----
    @Query("SELECT * FROM courses ORDER BY name COLLATE NOCASE") fun courses(): Flow<List<Course>>
    @Query("SELECT * FROM courses") suspend fun coursesOnce(): List<Course>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCourse(c: Course): Long
    @Delete suspend fun deleteCourse(c: Course)

    // ---- divisions ----
    @Query("SELECT * FROM divisions ORDER BY name COLLATE NOCASE") fun divisions(): Flow<List<Division>>
    @Query("SELECT * FROM divisions") suspend fun divisionsOnce(): List<Division>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertDivision(d: Division): Long
    @Delete suspend fun deleteDivision(d: Division)

    // ---- subjects ----
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE") fun subjects(): Flow<List<Subject>>
    @Query("SELECT * FROM subjects") suspend fun subjectsOnce(): List<Subject>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSubject(s: Subject): Long
    @Delete suspend fun deleteSubject(s: Subject)

    // ---- teachers ----
    @Query("SELECT * FROM teachers WHERE active = 1 ORDER BY name COLLATE NOCASE") fun teachers(): Flow<List<Teacher>>
    @Query("SELECT * FROM teachers") suspend fun teachersOnce(): List<Teacher>
    @Query("SELECT * FROM teachers WHERE id = :id") suspend fun teacherById(id: Long): Teacher?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTeacher(t: Teacher): Long
    @Delete suspend fun deleteTeacher(t: Teacher)

    // ---- students ----
    @Query("SELECT * FROM students WHERE active = 1 ORDER BY name COLLATE NOCASE") fun students(): Flow<List<Student>>
    @Query("SELECT * FROM students") suspend fun studentsOnce(): List<Student>
    @Query("SELECT * FROM students WHERE divisionId = :divisionId AND active = 1 ORDER BY name COLLATE NOCASE")
    suspend fun studentsInDivision(divisionId: Long): List<Student>
    @Query("SELECT * FROM students WHERE id = :id") suspend fun studentById(id: Long): Student?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertStudent(s: Student): Long
    @Update suspend fun updateStudent(s: Student)
    @Delete suspend fun deleteStudent(s: Student)

    // ---- attendance ----
    @Query("SELECT * FROM attendance_records") fun attendance(): Flow<List<AttendanceRecord>>
    @Query(
        "SELECT * FROM attendance_records WHERE divisionId = :divisionId AND session = :session " +
            "AND dateMillis BETWEEN :dayStart AND :dayEnd"
    )
    suspend fun attendanceForDay(divisionId: Long, session: String, dayStart: Long, dayEnd: Long): List<AttendanceRecord>

    @Query(
        "SELECT * FROM attendance_records WHERE studentId = :studentId " +
            "AND dateMillis BETWEEN :from AND :to ORDER BY dateMillis"
    )
    suspend fun attendanceForStudent(studentId: Long, from: Long, to: Long): List<AttendanceRecord>

    @Query(
        "SELECT * FROM attendance_records WHERE divisionId = :divisionId " +
            "AND dateMillis BETWEEN :from AND :to ORDER BY dateMillis"
    )
    suspend fun attendanceForDivision(divisionId: Long, from: Long, to: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE dateMillis BETWEEN :from AND :to ORDER BY dateMillis")
    suspend fun attendanceInRange(from: Long, to: Long): List<AttendanceRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAttendance(a: List<AttendanceRecord>)
    @Query(
        "DELETE FROM attendance_records WHERE divisionId = :divisionId AND session = :session " +
            "AND dateMillis BETWEEN :dayStart AND :dayEnd"
    )
    suspend fun clearAttendanceForDay(divisionId: Long, session: String, dayStart: Long, dayEnd: Long)

    @androidx.room.Transaction
    suspend fun saveAttendanceForDay(
        divisionId: Long, session: String, dayStart: Long, dayEnd: Long, records: List<AttendanceRecord>
    ) {
        clearAttendanceForDay(divisionId, session, dayStart, dayEnd)
        insertAttendance(records)
    }

    // ---- teacher attendance (admin marks staff present/absent) ----
    @Query("SELECT * FROM teacher_attendance_records WHERE dateMillis BETWEEN :dayStart AND :dayEnd")
    suspend fun teacherAttendanceForDay(dayStart: Long, dayEnd: Long): List<TeacherAttendanceRecord>
    @Query("SELECT * FROM teacher_attendance_records WHERE teacherId = :teacherId AND dateMillis BETWEEN :from AND :to ORDER BY dateMillis")
    suspend fun teacherAttendanceFor(teacherId: Long, from: Long, to: Long): List<TeacherAttendanceRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTeacherAttendance(a: List<TeacherAttendanceRecord>)
    @Query("DELETE FROM teacher_attendance_records WHERE dateMillis BETWEEN :dayStart AND :dayEnd")
    suspend fun clearTeacherAttendanceForDay(dayStart: Long, dayEnd: Long)

    @androidx.room.Transaction
    suspend fun saveTeacherAttendanceForDay(dayStart: Long, dayEnd: Long, records: List<TeacherAttendanceRecord>) {
        clearTeacherAttendanceForDay(dayStart, dayEnd)
        insertTeacherAttendance(records)
    }

    @Query("DELETE FROM teacher_attendance_records WHERE teacherId = :teacherId AND dateMillis BETWEEN :dayStart AND :dayEnd")
    suspend fun clearOneTeacherAttendanceForDay(teacherId: Long, dayStart: Long, dayEnd: Long)

    @androidx.room.Transaction
    suspend fun saveOneTeacherAttendanceForDay(teacherId: Long, dayStart: Long, dayEnd: Long, record: TeacherAttendanceRecord) {
        clearOneTeacherAttendanceForDay(teacherId, dayStart, dayEnd)
        insertTeacherAttendance(listOf(record))
    }

    // ---- holidays ----
    @Query("SELECT * FROM holidays ORDER BY dateMillis") fun holidays(): Flow<List<Holiday>>
    @Query("SELECT * FROM holidays ORDER BY dateMillis") suspend fun holidaysOnce(): List<Holiday>
    @Query("SELECT * FROM holidays WHERE dateMillis BETWEEN :from AND :to AND (divisionId = 0 OR divisionId = :divisionId)")
    suspend fun holidaysFor(divisionId: Long, from: Long, to: Long): List<Holiday>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHoliday(h: Holiday): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHolidays(h: List<Holiday>)
    @Delete suspend fun deleteHoliday(h: Holiday)

    // ---- messages ----
    @Query("SELECT * FROM messages WHERE studentId = :studentId ORDER BY dateMillis") fun messagesForStudent(studentId: Long): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE fromRole = 'SCHOOL' AND studentId = 0 AND (divisionId = 0 OR divisionId = :divisionId) ORDER BY dateMillis DESC")
    fun broadcastsForDivision(divisionId: Long): Flow<List<Message>>
    @Query("SELECT * FROM messages ORDER BY dateMillis DESC") fun allMessages(): Flow<List<Message>>
    @Query("SELECT * FROM messages WHERE pushed = 0") suspend fun unpushedMessages(): List<Message>
    @Query("UPDATE messages SET pushed = 1 WHERE id IN (:ids)") suspend fun markMessagesPushed(ids: List<Long>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMessage(m: Message): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMessages(m: List<Message>)
    @Query("SELECT * FROM messages") suspend fun messagesOnce(): List<Message>

    // ---- buses ----
    @Query("SELECT * FROM buses ORDER BY busNumber COLLATE NOCASE") fun buses(): Flow<List<Bus>>
    @Query("SELECT * FROM buses") suspend fun busesOnce(): List<Bus>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBus(b: Bus): Long
    @Delete suspend fun deleteBus(b: Bus)
    @Query("DELETE FROM buses") suspend fun wipeBuses()

    // ---- location pings (driver GPS tracking) ----
    @Insert suspend fun insertLocationPing(p: LocationPing): Long
    @Query("SELECT * FROM location_pings WHERE pushed = 0 ORDER BY dateMillis") suspend fun unpushedLocationPings(): List<LocationPing>
    @Query("UPDATE location_pings SET pushed = 1 WHERE id IN (:ids)") suspend fun markLocationPingsPushed(ids: List<Long>)
    @Query("SELECT * FROM location_pings WHERE teacherId = :teacherId AND dateMillis BETWEEN :from AND :to ORDER BY dateMillis")
    suspend fun routeFor(teacherId: Long, from: Long, to: Long): List<LocationPing>
    @Query("DELETE FROM location_pings WHERE dateMillis < :beforeMillis") suspend fun pruneLocationPings(beforeMillis: Long)

    // ---- exams ----
    @Query("SELECT * FROM exams ORDER BY dateMillis") fun exams(): Flow<List<Exam>>
    @Query("SELECT * FROM exams") suspend fun examsOnce(): List<Exam>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExam(e: Exam): Long
    @Delete suspend fun deleteExam(e: Exam)

    // ---- exam marks ----
    @Query("SELECT * FROM exam_marks") fun examMarks(): Flow<List<ExamMark>>
    @Query("SELECT * FROM exam_marks") suspend fun examMarksOnce(): List<ExamMark>
    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND subjectId = :subjectId")
    suspend fun examMarksFor(examId: Long, subjectId: Long): List<ExamMark>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExamMarks(m: List<ExamMark>)
    @Query("DELETE FROM exam_marks WHERE examId = :examId AND subjectId = :subjectId")
    suspend fun clearExamMarksFor(examId: Long, subjectId: Long)

    @androidx.room.Transaction
    suspend fun saveExamMarksFor(examId: Long, subjectId: Long, marks: List<ExamMark>) {
        clearExamMarksFor(examId, subjectId)
        insertExamMarks(marks)
    }

    // ---- grade bands ----
    @Query("SELECT * FROM grade_bands ORDER BY minPercent DESC") fun gradeBands(): Flow<List<GradeBand>>
    @Query("SELECT * FROM grade_bands ORDER BY minPercent DESC") suspend fun gradeBandsOnce(): List<GradeBand>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertGradeBand(g: GradeBand): Long
    @Delete suspend fun deleteGradeBand(g: GradeBand)

    // ---- wipe (Complete restore starts from empty) ----
    @Query("DELETE FROM courses") suspend fun wipeCourses()
    @Query("DELETE FROM divisions") suspend fun wipeDivisions()
    @Query("DELETE FROM subjects") suspend fun wipeSubjects()
    @Query("DELETE FROM teachers") suspend fun wipeTeachers()
    @Query("DELETE FROM students") suspend fun wipeStudents()
    @Query("DELETE FROM attendance_records") suspend fun wipeAttendance()
    @Query("DELETE FROM teacher_attendance_records") suspend fun wipeTeacherAttendance()
    @Query("DELETE FROM holidays") suspend fun wipeHolidays()

    @Query("DELETE FROM messages") suspend fun wipeMessages()

    @Query("DELETE FROM exams") suspend fun wipeExams()
    @Query("DELETE FROM exam_marks") suspend fun wipeExamMarks()
    @Query("DELETE FROM grade_bands") suspend fun wipeGradeBands()

    @androidx.room.Transaction
    suspend fun wipeAll() {
        wipeAttendance(); wipeTeacherAttendance(); wipeHolidays(); wipeMessages()
        wipeStudents(); wipeSubjects(); wipeDivisions(); wipeTeachers(); wipeCourses(); wipeBuses()
        wipeExamMarks(); wipeExams(); wipeGradeBands()
    }
}
