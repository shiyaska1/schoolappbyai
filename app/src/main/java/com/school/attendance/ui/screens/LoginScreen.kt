package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LoginViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addFirstAdmin(name: String, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repo.upsertTeacher(Teacher(name = name, isAdmin = true))) }
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit, vm: LoginViewModel = viewModel()) {
    val context = LocalContext.current
    val teachers by vm.teachers.collectAsState()
    var selected by remember { mutableStateOf<Teacher?>(null) }
    var menu by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var newAdminName by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth()) {
            Text("School Attendance", style = MaterialTheme.typography.headlineSmall)
            Text("Sign in", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

            if (teachers.isEmpty()) {
                Text("No teachers set up yet. Create the first admin account:", modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(value = newAdminName, onValueChange = { newAdminName = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { if (newAdminName.isNotBlank()) vm.addFirstAdmin(newAdminName) { id -> AppPrefs(context).loggedInTeacherId = id; onLoggedIn() } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text("Create admin account") }
                return@Column
            }

            Box {
                OutlinedTextField(
                    value = selected?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Teacher") },
                    trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    teachers.forEach { t -> DropdownMenuItem(text = { Text(t.name + if (t.isAdmin) " (Admin)" else "") }, onClick = { selected = t; menu = false; error = null }) }
                }
            }
            if (selected?.pin?.isNotBlank() == true) {
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Button(
                onClick = {
                    val t = selected
                    when {
                        t == null -> error = "Pick your name"
                        t.pin.isNotBlank() && t.pin != pin -> error = "Wrong PIN"
                        else -> { AppPrefs(context).loggedInTeacherId = t.id; onLoggedIn() }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Sign in") }
        }
    }
}
