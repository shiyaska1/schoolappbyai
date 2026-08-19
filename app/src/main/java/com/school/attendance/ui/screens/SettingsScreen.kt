package com.school.attendance.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.AttendanceMode
import com.school.attendance.sync.CloudSyncManager
import kotlinx.coroutines.launch
import java.util.Calendar

private val WEEK_DAYS = listOf(
    Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val syncStatus by CloudSyncManager.status.collectAsState()

    var schoolId by remember { mutableStateOf(prefs.schoolId) }
    var pushUrl by remember { mutableStateOf(prefs.syncPushUrl) }
    var pullUrl by remember { mutableStateOf(prefs.syncPullUrl) }
    var autoSync by remember { mutableStateOf(prefs.cloudAutoSync) }
    var mode by remember { mutableStateOf(prefs.attendanceMode) }
    var offDays by remember { mutableStateOf(prefs.weeklyOffDays) }
    var smsUrl by remember { mutableStateOf(prefs.smsGatewayUrl) }
    var smsMethod by remember { mutableStateOf(prefs.smsGatewayMethod) }
    var smsApiKey by remember { mutableStateOf(prefs.smsApiKey) }
    var smsSender by remember { mutableStateOf(prefs.smsSenderId) }
    var smsOnAbsent by remember { mutableStateOf(prefs.smsOnAbsent) }

    fun persist() {
        prefs.schoolId = schoolId; prefs.syncPushUrl = pushUrl; prefs.syncPullUrl = pullUrl
        prefs.cloudAutoSync = autoSync; prefs.attendanceMode = mode; prefs.weeklyOffDays = offDays
        prefs.smsGatewayUrl = smsUrl; prefs.smsGatewayMethod = smsMethod; prefs.smsApiKey = smsApiKey
        prefs.smsSenderId = smsSender; prefs.smsOnAbsent = smsOnAbsent
        if (autoSync) CloudSyncManager.startAuto(context, prefs.cloudAutoSyncIntervalSec * 1000L) else CloudSyncManager.stopAuto()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {

            Text("Attendance mode", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == AttendanceMode.ONCE.name, onClick = { mode = AttendanceMode.ONCE.name }, label = { Text("Once a day") })
                FilterChip(selected = mode == AttendanceMode.TWICE.name, onClick = { mode = AttendanceMode.TWICE.name }, label = { Text("Morning + Afternoon") })
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Weekly off days", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WEEK_DAYS.forEach { (day, label) ->
                    FilterChip(
                        selected = day in offDays, label = { Text(label) },
                        onClick = { offDays = if (day in offDays) offDays - day else offDays + day }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("This device / school ID", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Device ID: ${prefs.deviceId}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { clipboard.setText(AnnotatedString(prefs.deviceId)) }) { Text("Copy") }
            }
            Text("Give this device ID to your server admin so this school's records can be found by it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = schoolId, onValueChange = { schoolId = it }, label = { Text("School ID (if your server hosts more than one school)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Push / pull sync", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = pushUrl, onValueChange = { pushUrl = it }, label = { Text("Push URL (uploads this device's data)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = pullUrl, onValueChange = { pullUrl = it }, label = { Text("Pull URL (downloads the school's data)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Auto-sync while app is open", Modifier.weight(1f))
                Switch(checked = autoSync, onCheckedChange = { autoSync = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { persist(); scope.launch { CloudSyncManager.runOnePullMergePush(context) } }, modifier = Modifier.weight(1f)) { Text("Sync now") }
            }
            if (syncStatus.isNotBlank()) Text(syncStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Bulk SMS gateway (absence alerts)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = smsUrl, onValueChange = { smsUrl = it }, label = { Text("Gateway URL — {number} {message} {apikey} {sender}") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = smsMethod == "GET", onClick = { smsMethod = "GET" }, label = { Text("GET") })
                FilterChip(selected = smsMethod == "POST", onClick = { smsMethod = "POST" }, label = { Text("POST") })
            }
            OutlinedTextField(value = smsApiKey, onValueChange = { smsApiKey = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = smsSender, onValueChange = { smsSender = it }, label = { Text("Sender ID") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Auto-SMS guardian when a student is marked absent", Modifier.weight(1f))
                Switch(checked = smsOnAbsent, onCheckedChange = { smsOnAbsent = it })
            }

            Button(onClick = { persist() }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Save settings") }
        }
    }
}
