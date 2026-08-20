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
import androidx.compose.material3.Card
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialsScreen(onBack: () -> Unit, vm: CredentialsViewModel = viewModel()) {
    val context = LocalContext.current
    val students by vm.students.collectAsState()
    val teachers by vm.teachers.collectAsState()
    var tab by remember { mutableStateOf("Students") }
    var query by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Login Credentials") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == "Students", onClick = { tab = "Students" }, label = { Text("Students") })
                FilterChip(selected = tab == "Staff", onClick = { tab = "Staff" }, label = { Text("Staff") })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            HorizontalDivider(Modifier.padding(top = 8.dp))

            if (tab == "Students") {
                val filtered = students.filter { it.username.isNotBlank() && (query.isBlank() || it.name.contains(query, true) || it.rollNumber.contains(query, true)) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { s ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(s.name, style = MaterialTheme.typography.titleSmall)
                                Text("Username: ${s.username}   Password: ${s.password}", style = MaterialTheme.typography.bodyMedium)
                                val whatsapp = s.guardianWhatsapp.ifBlank { s.guardianPhone }
                                if (whatsapp.isNotBlank()) {
                                    OutlinedButton(onClick = {
                                        WhatsAppShare.send(context, whatsapp, "Dear Parent, your ward ${s.name}'s School App login — Username: ${s.username}, Password: ${s.password}. Please keep this safe.")
                                    }, modifier = Modifier.padding(top = 4.dp)) { Text("Share to parent via WhatsApp") }
                                }
                            }
                        }
                    }
                }
            } else {
                val filtered = teachers.filter { query.isBlank() || it.name.contains(query, true) || it.phone.contains(query, true) }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { t ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(t.name + if (t.isAdmin) " (Admin)" else "", style = MaterialTheme.typography.titleSmall)
                                Text("PIN: ${t.pin.ifBlank { "(none set)" }}", style = MaterialTheme.typography.bodyMedium)
                                if (t.phone.isNotBlank()) {
                                    OutlinedButton(onClick = {
                                        WhatsAppShare.send(context, t.phone, "Hi ${t.name}, your School App sign-in: pick your name at login" + (if (t.pin.isNotBlank()) " and PIN ${t.pin}" else " (no PIN set)") + ".")
                                    }, modifier = Modifier.padding(top = 4.dp)) { Text("Share via WhatsApp") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
