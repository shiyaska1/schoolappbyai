package com.school.attendance.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Opens WhatsApp (or WhatsApp Business) with a prefilled message to one number — the user still
 * taps Send themselves, same as the phone's own share sheet. [number] should include country code,
 * digits only (a leading "+" or spaces are stripped). */
object WhatsAppShare {
    fun send(context: Context, number: String, message: String) {
        val digits = number.filter { it.isDigit() }
        if (digits.isBlank()) {
            Toast.makeText(context, "No WhatsApp number on file for this student", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse("https://wa.me/$digits?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
