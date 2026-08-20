package com.school.attendance.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.school.attendance.data.AppPrefs
import com.school.attendance.sync.CloudSyncManager
import com.school.attendance.sync.MessageSync

/** Handles a `schoolapp://join?base=..&school=..` link: writes the school's server settings —
 * nothing else — then pulls the school's data so the normal Login screen has something to log
 * into. The person still signs in themselves afterwards (name+PIN, or student username+password);
 * this screen only ever removes the need to hand-type server settings, never credentials, so
 * nothing sensitive travels inside a shareable link. */
@Composable
fun JoinScreen(uri: Uri?, onReady: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    var status by remember { mutableStateOf("Setting up your school app...") }

    LaunchedEffect(uri) {
        val base = uri?.getQueryParameter("base")
        val school = uri?.getQueryParameter("school")
        if (uri == null || base.isNullOrBlank()) {
            onReady(); return@LaunchedEffect
        }
        prefs.baseUrl = base
        if (!school.isNullOrBlank()) prefs.schoolId = school

        status = "Fetching this school's setup..."
        CloudSyncManager.runOnePullMergePush(context)
        MessageSync.pushAndPull(context)
        onReady() // lands on the normal Login screen; the person still signs in themselves there.
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
