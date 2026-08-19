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

    // ---- holidays ----
    @Query("SELECT * FROM holidays ORDER BY dateMillis") fun holidays(): Flow<List<Holiday>>
    @Query("SELECT * FROM holidays ORDER BY dateMillis") suspend fun holidaysOnce(): List<Holiday>
    @Query("SELECT * FROM holidays WHERE dateMillis BETWEEN :from AND :to AND (divisionId = 0 OR divisionId = :divisionId)")
    suspend fun holidaysFor(divisionId: Long, from: Long, to: Long): List<Holiday>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHoliday(h: Holiday): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertHolidays(h: List<Holiday>)
    @Delete suspend fun deleteHoliday(h: Holiday)
}
