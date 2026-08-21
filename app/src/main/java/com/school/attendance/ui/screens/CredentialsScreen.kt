package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Repository
import com.school.attendance.util.WhatsAppShare
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class CredentialsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val students = repo.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

private data class ShareItem(val name: String, val phone: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(onBack: () -> Unit, vm: CredentialsViewModel = viewModel()) {
    val context = LocalContext.current
    val students by vm.students.collectAsState()
    val teachers by vm.teachers.collectAsState()
    var tab by remember { mutableStateOf("Students") }
    var query by remember { mutableStateOf("") }
    var selectedStudentIds by remember { mutableStateOf(setOf<Long>()) }
    var selectedTeacherIds by remember { mutableStateOf(setOf<Long>()) }

    // A queued bulk-share: WhatsApp can only be opened for one chat at a time, so this walks the
    // list one person per tap — skipping anyone with no phone number entered — instead of a single
    // button trying (and failing) to fire off several chats at once.
    var queue by remember { mutableStateOf<List<ShareItem>>(emptyList()) }
    var queueIndex by remember { mutableStateOf(0) }
    var skippedCount by remember { mutableStateOf(0) }

    fun launchNext() {
        if (queueIndex >= queue.size) return
        val item = queue[queueIndex]
        WhatsAppShare.send(context, item.phone, item.message)
        queueIndex++
    }

    val filteredStudents = students.filter { it.username.isNotBlank() && (query.isBlank() || it.name.contains(query, true) || it.rollNumber.contains(query, true)) }
    val filteredTeachers = teachers.filter { query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true) }

    Scaffold(topBar = { TopAppBar(title = { Text("Login Credentials") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "Students", onClick = { tab = "Students"; queue = emptyList() }, label = { Text("Students") })
                FilterChip(selected = tab == "Staff", onClick = { tab = "Staff"; queue = emptyList() }, label = { Text("Staff") })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            HorizontalDivider(Modifier.padding(top = 8.dp))

            if (queueIndex in queue.indices) {
                val nextUp = queue.getOrNull(queueIndex - 1)
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        if (nextUp != null) Text("Opened WhatsApp for ${nextUp.name}.", style = MaterialTheme.typography.bodySmall)
                        Text("${queue.size - queueIndex} of ${queue.size} left" + if (skippedCount > 0) " ($skippedCount skipped — no phone number)" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { launchNext() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text("Send to next: ${queue[queueIndex].name}")
                        }
                    }
                }
            } else if (queue.isNotEmpty()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Done — sent to all ${queue.size}.", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            if (tab == "Students") {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = filteredStudents.isNotEmpty() && filteredStudents.all { it.id in selectedStudentIds },
                        onCheckedChange = { checked -> selectedStudentIds = if (checked) selectedStudentIds + filteredStudents.map { it.id } else selectedStudentIds - filteredStudents.map { it.id }.toSet() }
                    )
                    Text("Select all", Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            val chosen = filteredStudents.filter { it.id in selectedStudentIds }
                            val withPhone = chosen.mapNotNull { s ->
                                val phone = s.guardianWhatsapp.ifBlank { s.guardianPhone }
                                if (phone.isBlank()) null else ShareItem(s.name, phone, "Dear Parent, your ward ${s.name}'s School App login — Username: ${s.username}, Password: ${s.password}. Please keep this safe.")
                            }
                            skippedCount = chosen.size - withPhone.size
                            queue = withPhone; queueIndex = 0
                            launchNext()
                        },
                        enabled = selectedStudentIds.isNotEmpty()
                    ) { Text("Share selected (${selectedStudentIds.size})") }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filteredStudents, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = s.id in selectedStudentIds, onCheckedChange = { checked -> selectedStudentIds = if (checked) selectedStudentIds + s.id else selectedStudentIds - s.id })
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                                    Text("Username: ${s.username}   Password: ${s.password}", style = MaterialTheme.typography.bodyMedium)
                                    val whatsapp = s.guardianWhatsapp.ifBlank { s.guardianPhone }
                                    if (whatsapp.isNotBlank()) {
                                        OutlinedButton(onClick = {
                                            WhatsAppShare.send(context, whatsapp, "Dear Parent, your ward ${s.name}'s School App login — Username: ${s.username}, Password: ${s.password}. Please keep this safe.")
                                        }, modifier = Modifier.padding(top = 4.dp)) { Text("Share to parent via WhatsApp") }
                                    } else {
                                        Text("No phone number on file", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = filteredTeachers.isNotEmpty() && filteredTeachers.all { it.id in selectedTeacherIds },
                        onCheckedChange = { checked -> selectedTeacherIds = if (checked) selectedTeacherIds + filteredTeachers.map { it.id } else selectedTeacherIds - filteredTeachers.map { it.id }.toSet() }
                    )
                    Text("Select all", Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            val chosen = filteredTeachers.filter { it.id in selectedTeacherIds }
                            val withPhone = chosen.mapNotNull { t ->
                                if (t.phone.isBlank()) null else ShareItem(t.name, t.phone, "Hi ${t.name}, your School App sign-in: pick your name at login" + (if (t.pin.isNotBlank()) " and PIN ${t.pin}" else " (no PIN set)") + ".")
                            }
                            skippedCount = chosen.size - withPhone.size
                            queue = withPhone; queueIndex = 0
                            launchNext()
                        },
                        enabled = selectedTeacherIds.isNotEmpty()
                    ) { Text("Share selected (${selectedTeacherIds.size})") }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filteredTeachers, key = { it.id }) { t ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = t.id in selectedTeacherIds, onCheckedChange = { checked -> selectedTeacherIds = if (checked) selectedTeacherIds + t.id else selectedTeacherIds - t.id })
                                Column(Modifier.weight(1f)) {
                                    Text(t.name + if (t.isAdmin) " (Admin)" else "", style = MaterialTheme.typography.titleSmall)
                                    Text("PIN: ${t.pin.ifBlank { "(none set)" }}", style = MaterialTheme.typography.bodyMedium)
                                    if (t.phone.isNotBlank()) {
                                        OutlinedButton(onClick = {
                                            WhatsAppShare.send(context, t.phone, "Hi ${t.name}, your School App sign-in: pick your name at login" + (if (t.pin.isNotBlank()) " and PIN ${t.pin}" else " (no PIN set)") + ".")
                                        }, modifier = Modifier.padding(top = 4.dp)) { Text("Share via WhatsApp") }
                                    } else {
                                        Text("No phone number on file", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
