package com.school.attendance.sms

import android.content.Context
import com.school.attendance.data.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SmsResult(val ok: Boolean, val response: String)

/** Sends an absence/attendance SMS through a generic, provider-agnostic HTTP gateway configured in
 * Settings — same URL-template approach as the POS billing app's bulk SMS. */
object SmsSender {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun jsonEsc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")

    suspend fun send(context: Context, number: String, message: String): SmsResult {
        if (number.isBlank()) return SmsResult(false, "No number")
        val prefs = AppPrefs(context)
        val url = prefs.smsGatewayUrl
        if (url.isBlank()) return SmsResult(false, "No gateway URL set in Settings")

        val jsonTpl = prefs.smsJsonBody
        if (jsonTpl.isNotBlank()) {
            val body = jsonTpl
                .replace("{number}", jsonEsc(number)).replace("{message}", jsonEsc(message))
                .replace("{apikey}", jsonEsc(prefs.smsApiKey)).replace("{sender}", jsonEsc(prefs.smsSenderId))
            val bearer = if (prefs.smsBearer) prefs.smsApiKey else null
            return withContext(Dispatchers.IO) { httpCall(url, "POST", body, "application/json", bearer) }
        }

        val method = if (prefs.smsGatewayMethod.equals("POST", true)) "POST" else "GET"
        fun subst(): String = url
            .replace("{number}", enc(number)).replace("{message}", enc(message))
            .replace("{apikey}", enc(prefs.smsApiKey)).replace("{sender}", enc(prefs.smsSenderId))
        return withContext(Dispatchers.IO) {
            if (method == "POST") {
                val full = subst()
                val q = full.indexOf('?')
                val base = if (q >= 0) full.substring(0, q) else full
                val body = if (q >= 0) full.substring(q + 1) else ""
                httpCall(base, "POST", body, "application/x-www-form-urlencoded", if (prefs.smsBearer) prefs.smsApiKey else null)
            } else {
                httpCall(subst(), "GET", null, null, if (prefs.smsBearer) prefs.smsApiKey else null)
            }
        }
    }

    private fun httpCall(urlStr: String, method: String, body: String?, contentType: String?, bearer: String?): SmsResult = try {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 10000
        conn.readTimeout = 20000
        if (!bearer.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $bearer")
        if (method == "POST") {
            conn.doOutput = true
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
            conn.outputStream.use { it.write((body ?: "").toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val out = ByteArrayOutputStream()
        stream?.use { it.copyTo(out) }
        conn.disconnect()
        val text = out.toString("UTF-8").trim()
        SmsResult(code in 200..299, if (text.isBlank()) "HTTP $code" else text)
    } catch (e: Exception) {
        SmsResult(false, e.message ?: "network error")
    }
}
