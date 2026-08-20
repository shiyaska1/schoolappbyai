package com.school.attendance.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.License

@Composable
fun LicenseScreen(onActivated: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val deviceId = remember { License.deviceId(context) }
    val dueMilestone = remember { License.dueMilestone(prefs.installDateMillis) }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth()) {
            Text("Trial ended", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your ${License.TRIAL_DAYS}-day free trial has ended. Call or WhatsApp to activate School App for your school.",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${License.SUPPORT_PHONE}"))) },
                    modifier = Modifier.weight(1f)
                ) { Text("Call ${License.SUPPORT_PHONE}") }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(License.buyUrlFor(deviceId)))) },
                    modifier = Modifier.weight(1f)
                ) { Text("WhatsApp") }
            }

            Text("Device ID", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 20.dp))
            Text(deviceId, style = MaterialTheme.typography.bodyLarge)
            Text("Read this to support to get your activation key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(value = key, onValueChange = { key = it; error = null }, label = { Text("Activation key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
            Button(
                onClick = {
                    if (License.isValid(deviceId, key, dueMilestone)) {
                        prefs.licensedMilestone = dueMilestone
                        onActivated()
                    } else {
                        error = "That key doesn't match this device. Double-check it, or contact support."
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Activate") }
        }
    }
}
