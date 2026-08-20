package com.school.attendance.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File

/** Picks up a photo Uri, downsizes and re-compresses it as JPEG until it's under [maxBytes]
 * (default 40 KB, per the school's server-storage constraint), and saves it into the app's own
 * files dir. Returns the saved file's absolute path, or null if the image couldn't be read. */
object PhotoUtil {
    fun importCompressed(context: Context, uri: Uri, targetName: String, maxBytes: Int = 40_000): String? {
        val bitmap = readAndDownscale(context, uri) ?: return null
        val bytes = compressUnder(bitmap, maxBytes) ?: return null
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "$targetName.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun readAndDownscale(context: Context, uri: Uri, maxDim: Int = 480): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        val full = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, full) }
    }

    /** Steps quality down until the JPEG fits [maxBytes]; if even quality 10 doesn't fit, shrinks
     * dimensions further and tries again — a couple of passes is always enough for a face photo. */
    private fun compressUnder(bitmap: Bitmap, maxBytes: Int): ByteArray? {
        var bmp = bitmap
        repeat(4) {
            var quality = 90
            while (quality >= 10) {
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= maxBytes) return out.toByteArray()
                quality -= 15
            }
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.7).toInt().coerceAtLeast(64), (bmp.height * 0.7).toInt().coerceAtLeast(64), true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 10, out)
        return out.toByteArray()
    }
}
