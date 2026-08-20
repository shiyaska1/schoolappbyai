package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.AttendanceSummary
import com.school.attendance.data.Message
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import com.school.attendance.sync.CloudSyncManager
import com.school.attendance.sync.MessageSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ParentDashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val student = MutableStateFlow<Student?>(null)
    val busId = MutableStateFlow(0L)
    val busNumber = MutableStateFlow<String?>(null)
    val summary = MutableStateFlow<AttendanceSummary?>(null)
    val messages = MutableStateFlow<List<Message>>(emptyList())

    fun reload(studentIdOverride: Long? = null) {
        viewModelScope.launch {
            val id = studentIdOverride ?: AppPrefs(getApplication()).loggedInStudentId
            val s = repo.studentsOnce().firstOrNull { it.id == id }
            student.value = s
            if (s != null) {
                busId.value = s.busId
                busNumber.value = repo.busNumberForStudent(s.id)
                val from = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                summary.value = repo.summaryForStudent(s.id, s.divisionId, from, System.currentTimeMillis())
                // One-shot snapshot (not a live subscription) — matches this screen's "reload()" pattern.
                messages.value = repo.allMessages().first()
                    .filter { it.studentId == s.id || it.divisionId == s.divisionId || (it.studentId == 0L && it.divisionId == 0L) }
                    .sortedByDescending { it.dateMillis }
            }
        }
    }

    fun send(body: String) {
        val s = student.value ?: return
        val prefs = AppPrefs(getApplication())
        viewModelScope.launch {
            repo.sendMessage(fromRole = "PARENT", fromTeacherId = 0, studentId = s.id, divisionId = s.divisionId, body = body, deviceId = prefs.deviceId)
            reload(s.id)
        }
    }
}

/** [studentIdOverride] + [onBack] together mean "an admin/teacher is viewing this student's parent
 * screen from Switch to Parent" — no session change, no auto-sync (they already have the data),
 * and a Back arrow instead of Log out. Normal parent use passes neither: reads the real session
 * via [AppPrefs.loggedInStudentId] and syncs on open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(onLogout: () -> Unit, studentIdOverride: Long? = null, onBack: (() -> Unit)? = null, vm: ParentDashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val student by vm.student.collectAsState()
    val busId by vm.busId.collectAsState()
    val busNumber by vm.busNumber.collectAsState()
    val summary by vm.summary.collectAsState()
    val messages by vm.messages.collectAsState()
    var showBusLocation by remember { mutableStateOf(false) }
    var newMessage by remember { mutableStateOf("") }
    val viewingAsAdmin = studentIdOverride != null
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    LaunchedEffect(studentIdOverride) {
        if (!viewingAsAdmin) {
            CloudSyncManager.runOnePullMergePush(context)
            MessageSync.pushAndPull(context)
        }
        vm.reload(studentIdOverride)
    }

    if (showBusLocation && busId != 0L) {
        LiveLocationScreen(onBack = { showBusLocation = false }, showHistory = false, restrictToBusId = busId)
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text((student?.name ?: "Parent") + if (viewingAsAdmin) " (viewing as parent)" else "") },
            navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { if (!viewingAsAdmin) IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Log out") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This month's attendance", style = MaterialTheme.typography.titleSmall)
                    summary?.let { s ->
                        Text("Present ${s.present} / ${s.workingDays} working days (${s.percent}%)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                        Text("Absent: ${s.absent}", style = MaterialTheme.typography.bodyMedium)
                    } ?: Text("Loading...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            busNumber?.let { bus ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("School bus", style = MaterialTheme.typography.titleSmall)
                        Text(bus, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        OutlinedButton(onClick = { showBusLocation = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Icon(Icons.Filled.DirectionsBus, null, modifier = Modifier.padding(end = 6.dp)); Text("Track my bus")
                        }
                    }
                }
            }
            Text("Messages", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            if (!viewingAsAdmin) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newMessage, onValueChange = { newMessage = it }, label = { Text("Message to teacher") }, modifier = Modifier.weight(1f))
                    Button(onClick = { if (newMessage.isNotBlank()) { vm.send(newMessage.trim()); newMessage = "" } }) { Text("Send") }
                }
            }
            messages.forEach { m ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.fromRole, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(m.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                        Text(fmt.format(m.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}
