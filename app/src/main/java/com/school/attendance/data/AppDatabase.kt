package com.school.attendance.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

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
                .addCallback(object : RoomDatabase.Callback() {
                    // Fires exactly once, the moment the database file is first created (fresh
                    // install) — seeds a default admin so there's always a real, predictable login
                    // (username "admin", PIN "123456") rather than requiring a first-run setup step.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        val columns = "(name, phone, designation, isTeachingStaff, monthlySalary, pin, isAdmin, " +
                            "canSelfMarkAttendance, busId, canViewBusLocation, active, updatedAtMillis, aadharNumber, " +
                            "bloodGroup, religion, secondMobile, email, permanentAddress, photoPath, hiddenModules)"
                        db.execSQL("INSERT INTO teachers $columns VALUES ('admin', '', 'Admin', 1, 0.0, '123456', 1, 0, 0, 0, 1, 0, '', '', '', '', '', '', '', '')")
                        db.execSQL("INSERT INTO teachers $columns VALUES ('teacher', '', 'Teacher', 1, 0.0, '123456', 0, 0, 0, 0, 1, 0, '', '', '', '', '', '', '', '')")
                    }
                })
                .build().also { INSTANCE = it }
        }
    }
}
