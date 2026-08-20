package com.school.attendance.sync

import android.content.Context
import com.school.attendance.data.AppDatabase
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.LocationPing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LocationFix(val lat: Double, val lng: Double, val speedMps: Float, val updatedAtMillis: Long)

/**
 * Live location — keyed by **bus number**, never by driver identity (so a parent's query never
 * needs, or reveals, who's driving). Pushes are batched from the local queue in [LocationPing] so
 * nothing captured while offline is lost: the tracking service always writes to Room first, then
 * this flushes whatever's unpushed whenever it gets a chance.
 */
object LocationSync {
    private fun url(prefs: AppPrefs, path: String, extra: String = ""): String? {
        if (prefs.baseUrl.isBlank()) return null
        val school = prefs.schoolId.ifBlank { "school" }
        return "${prefs.baseUrl.trimEnd('/')}/$path?school=$school&device=${prefs.deviceId}$extra"
    }

    /** Pushes every not-yet-pushed [LocationPing] for [teacherId], batched in one request. */
    suspend fun flushQueue(context: Context, teacherId: Long, busNumber: String): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val dao = AppDatabase.get(context).dao()
        val pending = dao.unpushedLocationPings().filter { it.teacherId == teacherId }
        if (pending.isEmpty()) return@withContext true
        val target = url(prefs, prefs.locationPushPath) ?: return@withContext false
        try {
            val points = JSONArray(pending.map { p ->
                JSONObject().put("lat", p.lat).put("lng", p.lng).put("speedMps", p.speedMps).put("dateMillis", p.dateMillis)
            })
            val body = JSONObject().put("busNumber", busNumber).put("points", points)
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val ok = conn.responseCode in 200..299
            conn.disconnect()
            if (ok) dao.markLocationPingsPushed(pending.map { it.id })
            ok
        } catch (e: Exception) {
            false
        }
    }

    /** Current position only — what a parent sees. Nothing is deleted server-side. */
    suspend fun pullLatest(context: Context, busNumber: String): LocationFix? = withContext(Dispatchers.IO) {
        pull(context, busNumber, "latest")?.let { JSONObject(it).toFix() }
    }

    /** Full route for the day — admin/teacher only. Server clears it after returning it. */
    suspend fun pullHistory(context: Context, busNumber: String): List<LocationFix> = withContext(Dispatchers.IO) {
        val text = pull(context, busNumber, "history") ?: return@withContext emptyList()
        val arr = JSONArray(text)
        (0 until arr.length()).map { arr.getJSONObject(it).toFix() }
    }

    private fun JSONObject.toFix() = LocationFix(optDouble("lat"), optDouble("lng"), optDouble("speedMps").toFloat(), optLong("dateMillis"))

    private fun pull(context: Context, busNumber: String, mode: String): String? {
        val prefs = AppPrefs(context)
        val target = url(prefs, prefs.locationPullPath, "&bus=$busNumber&mode=$mode") ?: return null
        return try {
            val conn = URL(target).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            val code = conn.responseCode
            if (code !in 200..299) { conn.disconnect(); return null }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            text
        } catch (e: Exception) {
            null
        }
    }
}
