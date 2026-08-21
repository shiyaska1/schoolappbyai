package com.school.attendance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Course::class, Division::class, Subject::class, Teacher::class, Student::class, AttendanceRecord::class,
        Holiday::class, TeacherAttendanceRecord::class, Message::class, LocationPing::class, Bus::class,
        AccountGroup::class, AccountHead::class, CostCenter::class, JournalEntry::class, JournalLine::class,
        Receipt::class, Expense::class, Customer::class, Supplier::class, Purchase::class,
        Exam::class, ExamMark::class, GradeBand::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SchoolDao
    abstract fun accountingDao(): AccountingDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            // Schema is still moving during testing; destructive fallback (wipes + recreates)
            // beats a hard crash on the next install over an older schema. Replace with real
            // migrations once the schema is stable and there's real data to preserve.
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "school_attendance.db")
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
