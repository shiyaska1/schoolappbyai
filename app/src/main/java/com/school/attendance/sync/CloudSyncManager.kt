package com.school.attendance.sync

import android.content.Context
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.AttendanceSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Push/pull sync with a school's own server, keyed by [AppPrefs.schoolId] + [AppPrefs.deviceId]
 * (one device id per school, as set up by the admin). Pull downloads the school's JSON, merges it
 * in; push uploads this device's current data. Mirrors the POS billing app's cloud backup sync
 * (same pull -> merge -> push cycle), just JSON instead of a zip since there are no attachments here.
 */
object CloudSyncManager {
    val status = MutableStateFlow("Idle")
    val isSyncing = MutableStateFlow(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var autoJob: Job? = null
    private val gate = Mutex()

    fun isRunning(): Boolean = autoJob != null

    /** Background auto-sync only ever runs over Wi-Fi — a phone left with auto-sync on shouldn't
     * quietly burn a driver's or teacher's mobile data every few minutes. Manual "Sync now" has no
     * such restriction. */
    fun startAuto(context: Context, intervalMs: Long) {
        stopAuto()
        val app = context.applicationContext
        autoJob = scope.launch {
            while (isActive) {
                if (isOnWifi(app)) runOnePullMergePush(app) else status.value = "Auto-sync waiting for Wi-Fi"
                delay(intervalMs)
            }
        }
    }

    fun stopAuto() { autoJob?.cancel(); autoJob = null }

    private fun hasInternet(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)

    private fun isOnWifi(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(net) ?: return@runCatching false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    suspend fun runOnePullMergePush(app: Context): Boolean = withContext(Dispatchers.IO) { gate.withLock {
        val prefs = AppPrefs(app)
        val pullUrl = prefs.syncPullUrl
        val pushUrl = prefs.syncPushUrl
        if (pullUrl.isBlank() || pushUrl.isBlank()) {
            status.value = "Set Push/Pull URL in Settings first"
            logResult(app, false); return@withLock false
        }
        if (!hasInternet(app)) {
            status.value = "No internet connection — skipped this sync"
            logResult(app, false); return@withLock false
        }
        isSyncing.value = true
        var ok = false
        try {
            val school = prefs.schoolId.ifBlank { "school" }.filter { it.isLetterOrDigit() || it == '-' }
            val dev = prefs.deviceId
            fun withParams(base: String): String {
                val sep = if (base.contains("?")) "&" else "?"
                return "$base${sep}school=$school&device=$dev"
            }

            // pull + merge
            val pullConn = URL(withParams(pullUrl)).openConnection() as HttpURLConnection
            pullConn.requestMethod = "GET"
            pullConn.connectTimeout = 15000
            pullConn.readTimeout = 60000
            val pullCode = pullConn.responseCode
            when (pullCode) {
                200 -> {
                    val body = pullConn.inputStream.bufferedReader().use { it.readText() }
                    pullConn.disconnect()
                    runCatching { AttendanceSync.importJson(app, body) }
                        .onFailure { status.value = "Sync: merge failed (${it.message}), pushing anyway" }
                        .onSuccess { status.value = "Merged: ${it.summary}" }
                }
                404 -> pullConn.disconnect() // first cycle ever for this school/device — nothing to merge yet
                else -> {
                    pullConn.disconnect()
                    status.value = "Sync: pull failed (HTTP $pullCode), skipped this cycle"
                    return@withLock false
                }
            }

            // push
            val json = AttendanceSync.exportJson(app)
            val pushConn = URL(withParams(pushUrl)).openConnection() as HttpURLConnection
            pushConn.requestMethod = "POST"
            pushConn.doOutput = true
            pushConn.connectTimeout = 15000
            pushConn.readTimeout = 60000
            pushConn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val bytes = json.toByteArray(Charsets.UTF_8)
            pushConn.setFixedLengthStreamingMode(bytes.size)
            pushConn.outputStream.use { it.write(bytes) }
            val pushCode = pushConn.responseCode
            pushConn.disconnect()
            if (pushCode !in 200..299) {
                status.value = "Sync: push failed (HTTP $pushCode)"
                return@withLock false
            }
            status.value = "Synced at " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            ok = true
            true
        } catch (e: Exception) {
            status.value = "Sync failed: ${e.javaClass.simpleName}" + (e.message?.let { " — $it" } ?: "")
            false
        } finally {
            isSyncing.value = false
            logResult(app, ok)
        }
    } }

    private fun logResult(app: Context, ok: Boolean) {
        val prefs = AppPrefs(app)
        prefs.lastCloudSyncAt = System.currentTimeMillis()
        prefs.lastCloudSyncOk = ok
        prefs.lastCloudSyncMessage = status.value
    }
}
