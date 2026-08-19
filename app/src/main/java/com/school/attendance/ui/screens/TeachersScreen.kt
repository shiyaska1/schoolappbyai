package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeachersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(t: Teacher) = viewModelScope.launch { repo.upsertTeacher(t) }
    fun delete(t: Teacher) = viewModelScope.launch { repo.deleteTeacher(t) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachersScreen(onBack: () -> Unit, vm: TeachersViewModel = viewModel()) {
    val teachers by vm.teachers.collectAsState()
    var edit by remember { mutableStateOf<Teacher?>(null) }

    edit?.let { t -> TeacherDialog(t, { vm.save(it); edit = null }, { edit = null }, { vm.delete(it); edit = null }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Teachers & Staff") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Teacher(name = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(teachers, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text(t.name + if (t.isAdmin) " (Admin)" else "") },
                    supportingContent = {
                        Text(listOfNotNull(
                            t.designation.ifBlank { null }, t.phone.ifBlank { null },
                            if (!t.isTeachingStaff) "Non-teaching" else null
                        ).joinToString("  ·  "))
                    },
                    modifier = Modifier.fillMaxWidth().clickable { edit = t }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TeacherDialog(initial: Teacher, onSave: (Teacher) -> Unit, onDismiss: () -> Unit, onDelete: (Teacher) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var designation by remember { mutableStateOf(initial.designation) }
    var pin by remember { mutableStateOf(initial.pin) }
    var salary by remember { mutableStateOf(if (initial.monthlySalary > 0) initial.monthlySalary.toString() else "") }
    var isAdmin by remember { mutableStateOf(initial.isAdmin) }
    var isTeaching by remember { mutableStateOf(initial.isTeachingStaff) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New teacher / staff" else "Edit teacher / staff") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() } }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Login PIN (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = salary, onValueChange = { salary = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Monthly salary (for payroll)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Teaching staff", Modifier.weight(1f))
                    Switch(checked = isTeaching, onCheckedChange = { isTeaching = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Admin access", Modifier.weight(1f))
                    Checkbox(checked = isAdmin, onCheckedChange = { isAdmin = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(initial.copy(
                    name = name.trim(), phone = phone.trim(), designation = designation.trim(), pin = pin.trim(),
                    monthlySalary = salary.toDoubleOrNull() ?: 0.0, isAdmin = isAdmin, isTeachingStaff = isTeaching
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) TextButton(onClick = { onDelete(initial) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
