package com.school.attendance.data

import android.content.Context
import java.util.UUID

/** SharedPreferences store for session, sync and settings. Mirrors the POS billing app's
 * AppPrefs (push/pull cloud sync + bulk-SMS-gateway pattern), scoped to attendance. */
object ReportCardDisplayMode {
    const val MARKS_AND_GRADE = "MARKS_AND_GRADE"
    const val GRADE_ONLY = "GRADE_ONLY"
}

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

    /** Display name printed on report cards and other documents; falls back to [schoolId] when blank. */
    var schoolName: String
        get() = (p.getString("school_name", "") ?: "").trim()
        set(v) { p.edit().putString("school_name", v.trim()).apply() }

    /** How a report card shows a subject's result: marks + grade, or grade only. */
    var reportCardDisplayMode: String
        get() = p.getString("report_card_display_mode", ReportCardDisplayMode.MARKS_AND_GRADE) ?: ReportCardDisplayMode.MARKS_AND_GRADE
        set(v) { p.edit().putString("report_card_display_mode", v).apply() }

    var loggedInTeacherId: Long
        get() = p.getLong("teacher_id", -1L)
        set(v) { p.edit().putLong("teacher_id", v).apply() }

    /** Set instead of [loggedInTeacherId] for a parent session (logged in via their child's
     * student username/password). The two are mutually exclusive. */
    var loggedInStudentId: Long
        get() = p.getLong("student_id", -1L)
        set(v) { p.edit().putLong("student_id", v).apply() }

    val isParentSession: Boolean get() = loggedInStudentId > 0

    fun clearSession() { loggedInTeacherId = -1L; loggedInStudentId = -1L }

    // ---- server base URL + per-endpoint PHP filenames ----
    // One base URL (e.g. https://myschool.example.com/app) plus a separate small PHP script per
    // job, so nothing has to be retyped as a full URL — only the base changes between schools.
    /** Root folder the four endpoint files below live in, no trailing slash needed. */
    var baseUrl: String
        get() = (p.getString("base_url", "") ?: "").trim()
        set(v) { p.edit().putString("base_url", v.trim()).apply() }

    var dataPushPath: String
        get() = (p.getString("data_push_path", "") ?: "").ifBlank { "push.php" }
        set(v) { p.edit().putString("data_push_path", v.trim()).apply() }
    var dataPullPath: String
        get() = (p.getString("data_pull_path", "") ?: "").ifBlank { "pull.php" }
        set(v) { p.edit().putString("data_pull_path", v.trim()).apply() }
    var messagePushPath: String
        get() = (p.getString("message_push_path", "") ?: "").ifBlank { "message_push.php" }
        set(v) { p.edit().putString("message_push_path", v.trim()).apply() }
    var messagePullPath: String
        get() = (p.getString("message_pull_path", "") ?: "").ifBlank { "message_pull.php" }
        set(v) { p.edit().putString("message_pull_path", v.trim()).apply() }
    var locationPushPath: String
        get() = (p.getString("location_push_path", "") ?: "").ifBlank { "location_push.php" }
        set(v) { p.edit().putString("location_push_path", v.trim()).apply() }
    var locationPullPath: String
        get() = (p.getString("location_pull_path", "") ?: "").ifBlank { "location_pull.php" }
        set(v) { p.edit().putString("location_pull_path", v.trim()).apply() }

    private fun fullUrl(path: String): String =
        if (baseUrl.isBlank() || path.isBlank()) "" else baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    /** Where "Push" uploads this device's data — [baseUrl] + [dataPushPath]. */
    val syncPushUrl: String get() = fullUrl(dataPushPath)
    /** Where "Pull" downloads the school's data from — [baseUrl] + [dataPullPath]. */
    val syncPullUrl: String get() = fullUrl(dataPullPath)
    /** Where a device POSTs its own new outgoing messages — [baseUrl] + [messagePushPath]. */
    val messagePushUrl: String get() = fullUrl(messagePushPath)
    /** Where a device GETs its mailbox from — [baseUrl] + [messagePullPath]. The server should also
     * accept DELETE on the same URL+query to clear what was just fetched (see [com.school.attendance.sync.MessageSync]). */
    val messagePullUrl: String get() = fullUrl(messagePullPath)

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

    // ---- geo-fence for staff self-attendance ----
    /** School location staff must be within [geoFenceRadiusMeters] of to self-mark attendance.
     * One shared point for the whole school (set once by admin from Settings). 0,0 = not set. */
    var schoolLatitude: Double
        get() = java.lang.Double.longBitsToDouble(p.getLong("school_lat_bits", 0L))
        set(v) { p.edit().putLong("school_lat_bits", java.lang.Double.doubleToLongBits(v)).apply() }

    var schoolLongitude: Double
        get() = java.lang.Double.longBitsToDouble(p.getLong("school_lng_bits", 0L))
        set(v) { p.edit().putLong("school_lng_bits", java.lang.Double.doubleToLongBits(v)).apply() }

    val geoFenceSet: Boolean get() = schoolLatitude != 0.0 || schoolLongitude != 0.0

    var geoFenceRadiusMeters: Int
        get() = p.getInt("geo_fence_radius_m", 100)
        set(v) { p.edit().putInt("geo_fence_radius_m", v.coerceIn(10, 5000)).apply() }

    /** Whether the geo-fenced self-attendance feature is on at all. Turning it on is also when the
     * app asks the phone to exempt it from battery optimization — that check runs unreliably on
     * some phones' aggressive battery savers otherwise. */
    var geoTrackingEnabled: Boolean
        get() = p.getBoolean("geo_tracking_enabled", false)
        set(v) { p.edit().putBoolean("geo_tracking_enabled", v).apply() }

    // ---- licensing / trial (see data/License.kt) ----
    var installDateMillis: Long
        get() = p.getLong("install_date", 0L)
        set(v) { p.edit().putLong("install_date", v).apply() }

    /** Highest renewal milestone already activated (0 = still on trial / never activated). */
    var licensedMilestone: Int
        get() = p.getInt("licensed_milestone", 0)
        set(v) { p.edit().putInt("licensed_milestone", v).apply() }

    var cloudAutoSync: Boolean
        get() = p.getBoolean("cloud_auto_sync", false)
        set(v) { p.edit().putBoolean("cloud_auto_sync", v).apply() }

    var cloudAutoSyncIntervalSec: Int
        get() = p.getInt("cloud_auto_sync_interval_sec", 600)
        set(v) { p.edit().putInt("cloud_auto_sync_interval_sec", v.coerceAtLeast(60)).apply() }

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

    // ---- designation master (a saved, extensible list — like the POS app's customer types) ----
    var designations: List<String>
        get() {
            val stored = p.getString("designations", null)
            if (stored == null) return listOf("Teacher", "Admin", "Driver", "Clerk", "Peon", "Accountant")
            return stored.split("|").map { it.trim() }.filter { it.isNotBlank() }
        }
        set(v) { p.edit().putString("designations", v.joinToString("|")).apply() }

    fun addDesignation(name: String) {
        val n = name.trim()
        if (n.isBlank()) return
        if (designations.none { it.equals(n, true) }) designations = designations + n
    }

    /** Every setting worth copying to another phone as plain text — not [deviceId] (must stay
     * unique per install) and not [loggedInTeacherId] (session-specific, not a setting). Lets an
     * admin set up one phone, export this, and import it on the rest of the school's devices
     * instead of retyping every URL and gateway field. */
    fun exportSettingsJson(): String {
        val o = org.json.JSONObject()
        o.put("schoolId", schoolId)
        o.put("schoolName", schoolName)
        o.put("reportCardDisplayMode", reportCardDisplayMode)
        o.put("schoolLatitude", schoolLatitude)
        o.put("schoolLongitude", schoolLongitude)
        o.put("geoFenceRadiusMeters", geoFenceRadiusMeters)
        o.put("attendanceMode", attendanceMode)
        o.put("weeklyOffDays", weeklyOffDays.joinToString(","))
        o.put("baseUrl", baseUrl)
        o.put("dataPushPath", dataPushPath)
        o.put("dataPullPath", dataPullPath)
        o.put("messagePushPath", messagePushPath)
        o.put("messagePullPath", messagePullPath)
        o.put("locationPushPath", locationPushPath)
        o.put("locationPullPath", locationPullPath)
        o.put("cloudAutoSync", cloudAutoSync)
        o.put("cloudAutoSyncIntervalSec", cloudAutoSyncIntervalSec)
        o.put("cloudAutoSyncIntervalSec", cloudAutoSyncIntervalSec)
        o.put("smsGatewayUrl", smsGatewayUrl)
        o.put("smsGatewayMethod", smsGatewayMethod)
        o.put("smsApiKey", smsApiKey)
        o.put("smsSenderId", smsSenderId)
        o.put("smsJsonBody", smsJsonBody)
        o.put("smsBearer", smsBearer)
        o.put("smsOnAbsent", smsOnAbsent)
        return o.toString(2)
    }

    fun importSettingsJson(json: String) {
        val o = org.json.JSONObject(json)
        schoolId = o.optString("schoolId", schoolId)
        schoolName = o.optString("schoolName", schoolName)
        reportCardDisplayMode = o.optString("reportCardDisplayMode", reportCardDisplayMode)
        schoolLatitude = o.optDouble("schoolLatitude", schoolLatitude)
        schoolLongitude = o.optDouble("schoolLongitude", schoolLongitude)
        geoFenceRadiusMeters = o.optInt("geoFenceRadiusMeters", geoFenceRadiusMeters)
        attendanceMode = o.optString("attendanceMode", attendanceMode)
        weeklyOffDays = o.optString("weeklyOffDays", "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet().ifEmpty { weeklyOffDays }
        baseUrl = o.optString("baseUrl", baseUrl)
        dataPushPath = o.optString("dataPushPath", dataPushPath)
        dataPullPath = o.optString("dataPullPath", dataPullPath)
        messagePushPath = o.optString("messagePushPath", messagePushPath)
        messagePullPath = o.optString("messagePullPath", messagePullPath)
        locationPushPath = o.optString("locationPushPath", locationPushPath)
        locationPullPath = o.optString("locationPullPath", locationPullPath)
        cloudAutoSync = o.optBoolean("cloudAutoSync", cloudAutoSync)
        cloudAutoSyncIntervalSec = o.optInt("cloudAutoSyncIntervalSec", cloudAutoSyncIntervalSec)
        cloudAutoSyncIntervalSec = o.optInt("cloudAutoSyncIntervalSec", cloudAutoSyncIntervalSec)
        smsGatewayUrl = o.optString("smsGatewayUrl", smsGatewayUrl)
        smsGatewayMethod = o.optString("smsGatewayMethod", smsGatewayMethod)
        smsApiKey = o.optString("smsApiKey", smsApiKey)
        smsSenderId = o.optString("smsSenderId", smsSenderId)
        smsJsonBody = o.optString("smsJsonBody", smsJsonBody)
        smsBearer = o.optBoolean("smsBearer", smsBearer)
        smsOnAbsent = o.optBoolean("smsOnAbsent", smsOnAbsent)
    }
}
