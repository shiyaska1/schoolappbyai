package com.school.attendance.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.school.attendance.data.LocalBackup
import com.school.attendance.sync.CloudSyncManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val WEEK_DAYS = listOf(
    Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
    Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val clipboard: ClipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val syncStatus by CloudSyncManager.status.collectAsState()
    var restoreMerge by remember { mutableStateOf(true) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val saveBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = LocalBackup.exportJson(context)
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            backupStatus = "Backup saved"
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = LocalBackup.restore(context, uri, restoreMerge)
            backupStatus = (if (restoreMerge) "Merged: " else "Restored: ") + result.summary
            if (!restoreMerge) onSignedOut()
        }
    }
    val exportSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportSettingsJson().toByteArray(Charsets.UTF_8)) }
        backupStatus = "Settings exported"
    }

    var schoolId by remember { mutableStateOf(prefs.schoolId) }
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var dataPushPath by remember { mutableStateOf(prefs.dataPushPath) }
    var dataPullPath by remember { mutableStateOf(prefs.dataPullPath) }
    var messagePushPath by remember { mutableStateOf(prefs.messagePushPath) }
    var messagePullPath by remember { mutableStateOf(prefs.messagePullPath) }
    var locationPushPath by remember { mutableStateOf(prefs.locationPushPath) }
    var locationPullPath by remember { mutableStateOf(prefs.locationPullPath) }
    var autoSync by remember { mutableStateOf(prefs.cloudAutoSync) }
    var mode by remember { mutableStateOf(prefs.attendanceMode) }
    var offDays by remember { mutableStateOf(prefs.weeklyOffDays) }
    var smsUrl by remember { mutableStateOf(prefs.smsGatewayUrl) }
    var smsMethod by remember { mutableStateOf(prefs.smsGatewayMethod) }
    var smsApiKey by remember { mutableStateOf(prefs.smsApiKey) }
    var smsSender by remember { mutableStateOf(prefs.smsSenderId) }
    var smsOnAbsent by remember { mutableStateOf(prefs.smsOnAbsent) }
    var schoolLat by remember { mutableStateOf(if (prefs.schoolLatitude != 0.0) prefs.schoolLatitude.toString() else "") }
    var schoolLng by remember { mutableStateOf(if (prefs.schoolLongitude != 0.0) prefs.schoolLongitude.toString() else "") }
    var geoRadius by remember { mutableStateOf(prefs.geoFenceRadiusMeters.toString()) }

    fun persist() {
        prefs.schoolId = schoolId; prefs.baseUrl = baseUrl
        prefs.dataPushPath = dataPushPath; prefs.dataPullPath = dataPullPath
        prefs.messagePushPath = messagePushPath; prefs.messagePullPath = messagePullPath
        prefs.locationPushPath = locationPushPath; prefs.locationPullPath = locationPullPath
        prefs.cloudAutoSync = autoSync; prefs.attendanceMode = mode; prefs.weeklyOffDays = offDays
        prefs.smsGatewayUrl = smsUrl; prefs.smsGatewayMethod = smsMethod; prefs.smsApiKey = smsApiKey
        prefs.smsSenderId = smsSender; prefs.smsOnAbsent = smsOnAbsent
        prefs.schoolLatitude = schoolLat.toDoubleOrNull() ?: 0.0
        prefs.schoolLongitude = schoolLng.toDoubleOrNull() ?: 0.0
        prefs.geoFenceRadiusMeters = geoRadius.toIntOrNull() ?: 100
        if (autoSync) CloudSyncManager.startAuto(context, prefs.cloudAutoSyncIntervalSec * 1000L) else CloudSyncManager.stopAuto()
    }

    val importSettingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return@rememberLauncherForActivityResult
        prefs.importSettingsJson(text)
        schoolId = prefs.schoolId; baseUrl = prefs.baseUrl
        dataPushPath = prefs.dataPushPath; dataPullPath = prefs.dataPullPath
        messagePushPath = prefs.messagePushPath; messagePullPath = prefs.messagePullPath
        locationPushPath = prefs.locationPushPath; locationPullPath = prefs.locationPullPath
        autoSync = prefs.cloudAutoSync; mode = prefs.attendanceMode; offDays = prefs.weeklyOffDays
        smsUrl = prefs.smsGatewayUrl; smsMethod = prefs.smsGatewayMethod; smsApiKey = prefs.smsApiKey
        smsSender = prefs.smsSenderId; smsOnAbsent = prefs.smsOnAbsent
        schoolLat = if (prefs.schoolLatitude != 0.0) prefs.schoolLatitude.toString() else ""
        schoolLng = if (prefs.schoolLongitude != 0.0) prefs.schoolLongitude.toString() else ""
        geoRadius = prefs.geoFenceRadiusMeters.toString()
        backupStatus = "Settings imported"
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {

            Text("Copy these settings to another phone", style = MaterialTheme.typography.titleSmall)
            Text("Shares this screen's fields (URLs, gateway, attendance mode) as a text file — not the school's data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { persist(); exportSettingsLauncher.launch("attendance-settings.txt") }, modifier = Modifier.weight(1f)) { Text("Export settings") }
                Button(onClick = { importSettingsLauncher.launch("text/*") }, modifier = Modifier.weight(1f)) { Text("Import settings") }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
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
            Text("Server", style = MaterialTheme.typography.titleSmall)
            Text("One base URL, plus a small PHP file per job — see server/README.md for what to deploy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text("Base URL, e.g. https://yourdomain.com/server") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = dataPushPath, onValueChange = { dataPushPath = it }, label = { Text("Data push filename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = dataPullPath, onValueChange = { dataPullPath = it }, label = { Text("Data pull filename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = messagePushPath, onValueChange = { messagePushPath = it }, label = { Text("Message push filename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = messagePullPath, onValueChange = { messagePullPath = it }, label = { Text("Message pull filename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = locationPushPath, onValueChange = { locationPushPath = it }, label = { Text("Location push filename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            OutlinedTextField(value = locationPullPath, onValueChange = { locationPullPath = it }, label = { Text("Location pull filename") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Auto-sync while app is open", Modifier.weight(1f))
                Switch(checked = autoSync, onCheckedChange = { autoSync = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { persist(); scope.launch { CloudSyncManager.runOnePullMergePush(context) } }, modifier = Modifier.weight(1f)) { Text("Sync now") }
            }
            if (syncStatus.isNotBlank()) Text(syncStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("School location (geo-fence for driver/staff self-attendance)", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = schoolLat, onValueChange = { schoolLat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = schoolLng, onValueChange = { schoolLng = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = geoRadius, onValueChange = { geoRadius = it }, label = { Text("Allowed radius (meters)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))

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

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Local backup / restore", style = MaterialTheme.typography.titleSmall)
            Text("Saves or loads a file directly — no server needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { saveBackupLauncher.launch("attendance-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())}.json") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Backup now") }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { restoreMerge = true; restoreLauncher.launch("application/json") }, modifier = Modifier.weight(1f)) { Text("Restore — Merge") }
                Button(onClick = { restoreMerge = false; restoreLauncher.launch("application/json") }, modifier = Modifier.weight(1f)) { Text("Restore — Complete") }
            }
            Text("Merge adds to what's already here (keeps the newer record on any conflict). Complete erases everything on this device first, then loads the file — you'll be signed out afterwards.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            backupStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }

            Button(onClick = { persist() }, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text("Save settings") }
        }
    }
}
