package com.school.attendance.data

import android.content.Context
import android.net.Uri

/** Manual, file-based backup/restore — separate from [com.school.attendance.sync.CloudSyncManager]'s
 * push/pull, for a teacher who wants a local file (e.g. to keep on Drive, or hand to another device
 * directly) instead of a server round-trip. Same underlying JSON shape as the cloud sync, same two
 * restore modes as the POS billing app: Complete (wipe first, so the file becomes the new truth) or
 * Merge (add to what's already here, keeping newer attendance on conflicts). */
object LocalBackup {
    suspend fun exportJson(context: Context): String = AttendanceSync.exportJson(context)

    suspend fun restore(context: Context, uri: Uri, merge: Boolean): MergeResult {
        val json = context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
        if (!merge) Repository(context).wipeAll()
        return AttendanceSync.importJson(context, json)
    }
}
