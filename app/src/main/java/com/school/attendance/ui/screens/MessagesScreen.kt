package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Division
import com.school.attendance.data.Message
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class MessagesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val messages = repo.allMessages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val students = repo.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun send(studentId: Long, divisionId: Long, body: String) {
        val prefs = AppPrefs(getApplication())
        viewModelScope.launch {
            repo.sendMessage(
                fromRole = if (studentId == 0L) "SCHOOL" else "TEACHER",
                fromTeacherId = prefs.loggedInTeacherId.coerceAtLeast(0),
                studentId = studentId, divisionId = divisionId, body = body, deviceId = prefs.deviceId
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(onBack: () -> Unit, vm: MessagesViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val students by vm.students.collectAsState()
    val divisions by vm.divisions.collectAsState()
    var showCompose by remember { mutableStateOf(false) }
    val studentNames = remember(students) { students.associate { it.id to it.name } }
    val divisionNames = remember(divisions) { divisions.associate { it.id to it.name } }
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    if (showCompose) {
        ComposeDialog(students, divisions, onSend = { sid, did, body -> vm.send(sid, did, body); showCompose = false }, onDismiss = { showCompose = false })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Messages") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { showCompose = true }) { Icon(Icons.Filled.Add, "New message") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            items(messages.sortedByDescending { it.dateMillis }, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        val target = when {
                            m.studentId != 0L -> studentNames[m.studentId]?.let { "To/from: $it" } ?: "Student thread"
                            m.divisionId != 0L -> "Broadcast: ${divisionNames[m.divisionId] ?: "a division"}"
                            else -> "Broadcast: whole school"
                        }
                        Text("${m.fromRole} · $target", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(m.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Text(fmt.format(m.dateMillis), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeDialog(students: List<Student>, divisions: List<Division>, onSend: (Long, Long, String) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("School") }
    var studentId by remember { mutableStateOf(0L) }
    var divisionId by remember { mutableStateOf(0L) }
    var studentMenu by remember { mutableStateOf(false) }
    var divMenu by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == "School", onClick = { kind = "School" }, label = { Text("Whole school") })
                    FilterChip(selected = kind == "Division", onClick = { kind = "Division" }, label = { Text("Division") })
                    FilterChip(selected = kind == "Student", onClick = { kind = "Student" }, label = { Text("One parent") })
                }
                if (kind == "Division") {
                    Box(Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") },
                            trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                            divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; divMenu = false }) }
                        }
                    }
                }
                if (kind == "Student") {
                    Box(Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = students.firstOrNull { it.id == studentId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Student") },
                            trailingIcon = { IconButton(onClick = { studentMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = studentMenu, onDismissRequest = { studentMenu = false }) {
                            students.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { studentId = s.id; studentMenu = false }) }
                        }
                    }
                }
                OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (body.isNotBlank()) onSend(if (kind == "Student") studentId else 0L, if (kind == "Division") divisionId else 0L, body.trim())
            }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
