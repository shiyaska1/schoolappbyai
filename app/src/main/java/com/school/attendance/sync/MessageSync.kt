package com.school.attendance.sync

import android.content.Context
import com.school.attendance.data.AppDatabase
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Messages sync on their own endpoint, separate from [CloudSyncManager]'s data push/pull — a
 * mailbox, not a merge: a device pushes what it has to say, and pulls (then asks the server to
 * clear) what's waiting for it. Simpler server contract than the merged data blob, and messages
 * don't pile up on the server once every recipient device has fetched them.
 *
 * Contract for the school's server (client-only here, same as [CloudSyncManager]):
 * - `POST <messagePushUrl>?school=..&device=..` with a JSON array body of new messages from this device.
 * - `GET <messagePullUrl>?school=..&device=..` returns a JSON array addressed to this device (or `[]`/404 if none).
 * - `DELETE <messagePullUrl>?school=..&device=..` clears whatever was queued for this device — called
 *   right after a successful GET, so a message is only ever delivered once per device.
 */
object MessageSync {
    val status = MutableStateFlow("Idle")

    suspend fun pushAndPull(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        if (prefs.messagePushUrl.isBlank() && prefs.messagePullUrl.isBlank()) return@withContext false
        val pushOk = if (prefs.messagePushUrl.isNotBlank()) push(context) else true
        val pullOk = if (prefs.messagePullUrl.isNotBlank()) pull(context) else true
        pushOk && pullOk
    }

    private fun withParams(base: String, prefs: AppPrefs): String {
        val sep = if (base.contains("?")) "&" else "?"
        return "$base${sep}school=${prefs.schoolId.ifBlank { "school" }}&device=${prefs.deviceId}"
    }

    suspend fun push(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val url = prefs.messagePushUrl
        if (url.isBlank()) return@withContext false
        try {
            val repo = Repository(context)
            val dao = AppDatabase.get(context).dao()
            val outgoing = dao.unpushedMessages()
            if (outgoing.isEmpty()) { status.value = "No new messages to send"; return@withContext true }

            val students = repo.studentsOnce().associateBy { it.id }
            val divisions = repo.divisionsOnce().associateBy { it.id }
            val teachers = repo.teachersOnce().associateBy { it.id }

            val arr = JSONArray(outgoing.map { m ->
                val s = students[m.studentId]
                JSONObject()
                    .put("dateMillis", m.dateMillis)
                    .put("fromRole", m.fromRole)
                    .put("fromTeacherPhone", teachers[m.fromTeacherId]?.phone ?: "")
                    .put("studentRoll", s?.rollNumber ?: "")
                    .put("studentName", s?.name ?: "")
                    .put("divisionName", divisions[m.divisionId]?.name ?: divisions[s?.divisionId]?.name ?: "")
                    .put("body", m.body)
            })

            val conn = URL(withParams(url, prefs)).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(arr.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            if (code !in 200..299) { status.value = "Message push failed (HTTP $code)"; return@withContext false }
            dao.markMessagesPushed(outgoing.map { it.id })
            status.value = "Sent ${outgoing.size} message(s)"
            true
        } catch (e: Exception) {
            status.value = "Message push failed: ${e.javaClass.simpleName}" + (e.message?.let { " — $it" } ?: "")
            false
        }
    }

    suspend fun pull(context: Context): Boolean = withContext(Dispatchers.IO) {
        val prefs = AppPrefs(context)
        val url = prefs.messagePullUrl
        if (url.isBlank()) return@withContext false
        try {
            val conn = URL(withParams(url, prefs)).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            val code = conn.responseCode
            if (code == 404) { conn.disconnect(); status.value = "No new messages"; return@withContext true }
            if (code !in 200..299) { conn.disconnect(); status.value = "Message pull failed (HTTP $code)"; return@withContext false }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val arr = JSONArray(body)
            if (arr.length() == 0) { status.value = "No new messages"; return@withContext true }

            val repo = Repository(context)
            val dao = AppDatabase.get(context).dao()
            val students = repo.studentsOnce()
            val divisions = repo.divisionsOnce()
            val teachers = repo.teachersOnce()
            val now = System.currentTimeMillis()

            val incoming = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val divName = o.optString("divisionName")
                val division = divisions.firstOrNull { it.name.equals(divName, true) }
                val student = students.firstOrNull {
                    it.rollNumber.equals(o.optString("studentRoll"), true) && it.divisionId == (division?.id ?: -1L)
                } ?: students.firstOrNull { it.name.equals(o.optString("studentName"), true) }
                val teacher = teachers.firstOrNull { it.phone == o.optString("fromTeacherPhone") }
                com.school.attendance.data.Message(
                    dateMillis = o.optLong("dateMillis", now), fromRole = o.optString("fromRole", "SCHOOL"),
                    fromTeacherId = teacher?.id ?: 0L, studentId = student?.id ?: 0L, divisionId = division?.id ?: 0L,
                    body = o.optString("body"), deviceId = "", updatedAtMillis = now, pushed = true
                )
            }
            dao.insertMessages(incoming)
            status.value = "Received ${incoming.size} message(s)"

            // Mailbox semantics: this device has the messages now, so ask the server to clear its copy.
            val delConn = URL(withParams(url, prefs)).openConnection() as HttpURLConnection
            delConn.requestMethod = "DELETE"
            delConn.connectTimeout = 15000
            delConn.readTimeout = 15000
            runCatching { delConn.responseCode }
            delConn.disconnect()
            true
        } catch (e: Exception) {
            status.value = "Message pull failed: ${e.javaClass.simpleName}" + (e.message?.let { " — $it" } ?: "")
            false
        }
    }
}
