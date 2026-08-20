package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

    fun addFirstAdmin(name: String, onDone: (Teacher) -> Unit) {
        viewModelScope.launch {
            val admin = Teacher(name = name, isAdmin = true)
            val id = repo.upsertTeacher(admin)
            onDone(admin.copy(id = id))
        }
    }

    fun setPin(teacher: Teacher, pin: String, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repo.upsertTeacher(teacher.copy(pin = pin))) }
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
    var pendingPinSetup by remember { mutableStateOf<Teacher?>(null) }

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(Modifier.fillMaxWidth().align(Alignment.Center)) {
            Text("School App", style = MaterialTheme.typography.headlineSmall)
            Text("Sign in", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

            pendingPinSetup?.let { admin ->
                SetPinStep(
                    onSkip = { AppPrefs(context).loggedInTeacherId = admin.id; onLoggedIn() },
                    onSave = { pin -> vm.setPin(admin, pin) { AppPrefs(context).loggedInTeacherId = it; onLoggedIn() } }
                )
                return@Column
            }

            if (teachers.isEmpty()) {
                Text("No teachers set up yet. Create the first admin account:", modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(value = newAdminName, onValueChange = { newAdminName = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { if (newAdminName.isNotBlank()) vm.addFirstAdmin(newAdminName) { admin -> pendingPinSetup = admin } },
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

        Text(
            "MOBI CARE COMPUTERS, ERNAKULAM, MOB(ICON): 9961128378",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SetPinStep(onSkip: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Admin account created. Set a login PIN so only you can sign in as admin (optional — you can skip and add one later from Teachers & Staff).", modifier = Modifier.padding(bottom = 12.dp))
    OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("PIN (4-6 digits)") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Confirm PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text("Skip") }
        Button(
            onClick = {
                when {
                    pin.length < 4 -> error = "PIN must be at least 4 digits"
                    pin != confirm -> error = "PINs don't match"
                    else -> onSave(pin)
                }
            },
            modifier = Modifier.weight(1f)
        ) { Text("Set PIN") }
    }
}
