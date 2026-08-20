package com.school.attendance.data

import android.content.Context
import android.provider.Settings
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Trial + device-locked activation — same scheme as the POS billing app, so the same key-generation
 * process and secret work for both. The activation key is a keyed HMAC-SHA256 of the device id (and
 * milestone), so it can't be forged without [SECRET]. To activate a school: they read you the
 * Device ID from the expiry screen, you compute the key (see server/generate-license-key.ps1 in this
 * repo, or any HMAC-SHA256 tool: key = SECRET, message = the Device ID exactly as shown, take the
 * first 16 hex chars, upper-case), and they type it in.
 */
object License {
    const val TRIAL_DAYS = 30

    // ---- Support / purchase contact, shown wherever a licence key is needed ----
    const val SUPPORT_WHATSAPP = "919961128378"
    const val SUPPORT_PHONE = "+919961128378"

    fun buyUrlFor(deviceId: String): String {
        val msg = java.net.URLEncoder.encode("I want to buy School App. My Device ID is $deviceId", "UTF-8")
        return "https://wa.me/$SUPPORT_WHATSAPP?text=$msg"
    }

    /** Same value as the POS billing app's — deliberately shared so one key-generation process
     * covers both apps. >>> CHANGE THIS (in both apps together) before publishing either. <<< */
    private const val SECRET = "POSB-change-this-secret-2024"

    /** Stable per-device identifier (Android ID), shown to the user for activation. */
    fun deviceId(context: Context): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return (if (id.isBlank()) "UNKNOWNDEVICE" else id).uppercase()
    }

    val MILESTONES = listOf(1, 6, 12, 36, 48)

    fun monthsSince(installMillis: Long): Int {
        if (installMillis <= 0L) return 0
        val now = java.util.Calendar.getInstance()
        val probe = java.util.Calendar.getInstance().apply { timeInMillis = installMillis }
        var months = 0
        while (true) {
            probe.add(java.util.Calendar.MONTH, 1)
            if (probe.timeInMillis > now.timeInMillis) break
            months++
        }
        return months
    }

    fun dueMilestone(installMillis: Long): Int {
        if (!trialExpired(installMillis)) return 0
        val months = monthsSince(installMillis)
        return MILESTONES.filter { it <= maxOf(months, 1) }.maxOrNull() ?: 0
    }

    fun nextMilestone(milestone: Int): Int? = MILESTONES.firstOrNull { it > milestone }

    fun activationKey(deviceId: String, milestone: Int = 1): String {
        val message = if (milestone <= 1) deviceId.trim().uppercase() else deviceId.trim().uppercase() + milestone
        val hex = hmacHex(message).take(16).uppercase()
        return hex.chunked(4).joinToString("-")
    }

    fun isValid(deviceId: String, key: String, milestone: Int = 1): Boolean {
        val norm = key.uppercase().replace(Regex("[^0-9A-F]"), "")
        if (norm.isEmpty()) return false
        return activationKey(deviceId, milestone).replace("-", "") == norm
    }

    private fun hmacHex(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun daysSince(installMillis: Long): Long {
        if (installMillis <= 0L) return 0L
        return (System.currentTimeMillis() - installMillis) / (1000L * 60 * 60 * 24)
    }

    fun trialExpired(installMillis: Long): Boolean = daysSince(installMillis) >= TRIAL_DAYS
    fun daysLeft(installMillis: Long): Int = (TRIAL_DAYS - daysSince(installMillis)).toInt().coerceIn(0, TRIAL_DAYS)
}
