package com.school.attendance.data

import android.content.Context
import java.util.UUID

/** SharedPreferences store for session, sync and settings. Mirrors the POS billing app's
 * AppPrefs (push/pull cloud sync + bulk-SMS-gateway pattern), scoped to attendance. */
class AppPrefs(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)

    /** Stable per-install id, auto-generated once. Shown in Settings so the admin can register
     * this phone with the school's server (each school/device gets one id to fetch/push by). */
    val deviceId: String
        get() {
            val existing = p.getString("device_id", null)
            if (!existing.isNullOrBlank()) return existing
            val fresh = UUID.randomUUID().toString().replace("-", "").take(16)
            p.edit().putString("device_id", fresh).apply()
            return fresh
        }

    /** Identifies the school on a shared server (multiple schools, one endpoint). */
    var schoolId: String
        get() = (p.getString("school_id", "") ?: "").trim()
        set(v) { p.edit().putString("school_id", v.trim()).apply() }

    var loggedInTeacherId: Long
        get() = p.getLong("teacher_id", -1L)
        set(v) { p.edit().putLong("teacher_id", v).apply() }

    fun clearSession() { loggedInTeacherId = -1L }

    // ---- attendance mode ----
    var attendanceMode: String
        get() = p.getString("attendance_mode", AttendanceMode.ONCE.name) ?: AttendanceMode.ONCE.name
        set(v) { p.edit().putString("attendance_mode", v).apply() }

    // ---- weekly off days ----
    /** [java.util.Calendar] DAY_OF_WEEK values (1=Sunday..7=Saturday) that are never working days.
     * Defaults to Saturday + Sunday. */
    var weeklyOffDays: Set<Int>
        get() {
            val stored = p.getString("weekly_off_days", null)
            if (stored == null) return setOf(java.util.Calendar.SATURDAY, java.util.Calendar.SUNDAY)
            return stored.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
        set(v) { p.edit().putString("weekly_off_days", v.joinToString(",")).apply() }

    // ---- cloud push/pull sync (zip of courses/divisions/subjects/teachers/students/attendance) ----
    /** Where "Push" uploads the backup zip. The server should overwrite, not version, the file. */
    var syncPushUrl: String
        get() = (p.getString("sync_push_url", "") ?: "").trim()
        set(v) { p.edit().putString("sync_push_url", v.trim()).apply() }

    /** Where "Pull" downloads the backup zip from. */
    var syncPullUrl: String
        get() = (p.getString("sync_pull_url", "") ?: "").trim()
        set(v) { p.edit().putString("sync_pull_url", v.trim()).apply() }

    var cloudAutoSync: Boolean
        get() = p.getBoolean("cloud_auto_sync", false)
        set(v) { p.edit().putBoolean("cloud_auto_sync", v).apply() }

    var cloudAutoSyncIntervalSec: Int
        get() = p.getInt("cloud_auto_sync_interval_sec", 300)
        set(v) { p.edit().putInt("cloud_auto_sync_interval_sec", v.coerceAtLeast(30)).apply() }

    var lastCloudSyncAt: Long
        get() = p.getLong("last_cloud_sync_at", 0)
        set(v) { p.edit().putLong("last_cloud_sync_at", v).apply() }

    var lastCloudSyncOk: Boolean
        get() = p.getBoolean("last_cloud_sync_ok", true)
        set(v) { p.edit().putBoolean("last_cloud_sync_ok", v).apply() }

    var lastCloudSyncMessage: String
        get() = p.getString("last_cloud_sync_message", "") ?: ""
        set(v) { p.edit().putString("last_cloud_sync_message", v).apply() }

    // ---- bulk SMS gateway (generic, provider-agnostic — absence alerts to guardians) ----
    /** Send URL template with placeholders {number} {message} {apikey} {sender}. */
    var smsGatewayUrl: String
        get() = (p.getString("sms_url", "") ?: "").trim()
        set(v) { p.edit().putString("sms_url", v.trim()).apply() }

    /** "GET" or "POST". */
    var smsGatewayMethod: String
        get() = (p.getString("sms_method", "GET") ?: "GET")
        set(v) { p.edit().putString("sms_method", v).apply() }

    var smsApiKey: String
        get() = (p.getString("sms_apikey", "") ?: "").trim()
        set(v) { p.edit().putString("sms_apikey", v.trim()).apply() }

    var smsSenderId: String
        get() = (p.getString("sms_sender", "") ?: "").trim()
        set(v) { p.edit().putString("sms_sender", v.trim()).apply() }

    /** Optional JSON request body template for token-style APIs. */
    var smsJsonBody: String
        get() = (p.getString("sms_json_body", "") ?: "")
        set(v) { p.edit().putString("sms_json_body", v).apply() }

    var smsBearer: Boolean
        get() = p.getBoolean("sms_bearer", false)
        set(v) { p.edit().putBoolean("sms_bearer", v).apply() }

    /** When on, marking a student absent auto-queues a guardian SMS via the gateway above. */
    var smsOnAbsent: Boolean
        get() = p.getBoolean("sms_on_absent", false)
        set(v) { p.edit().putBoolean("sms_on_absent", v).apply() }
}
