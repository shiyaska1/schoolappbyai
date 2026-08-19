package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Division
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudentsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val students = repo.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(s: Student) = viewModelScope.launch { repo.upsertStudent(s) }
    fun delete(s: Student) = viewModelScope.launch { repo.deleteStudent(s) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(onBack: () -> Unit, vm: StudentsViewModel = viewModel()) {
    val students by vm.students.collectAsState()
    val divisions by vm.divisions.collectAsState()
    var edit by remember { mutableStateOf<Student?>(null) }
    val divisionNames = remember(divisions) { divisions.associate { it.id to it.name } }

    edit?.let { s -> StudentDialog(s, divisions, { vm.save(it); edit = null }, { edit = null }, { vm.delete(it); edit = null }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Students") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Student(name = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(students, key = { it.id }) { s ->
                ListItem(
                    headlineContent = { Text(s.name) },
                    supportingContent = { Text(listOfNotNull(s.rollNumber.ifBlank { null }, divisionNames[s.divisionId]).joinToString("  ·  ")) },
                    modifier = Modifier.fillMaxWidth().clickable { edit = s }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentDialog(initial: Student, divisions: List<Division>, onSave: (Student) -> Unit, onDismiss: () -> Unit, onDelete: (Student) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var roll by remember { mutableStateOf(initial.rollNumber) }
    var divisionId by remember { mutableStateOf(initial.divisionId) }
    var divMenu by remember { mutableStateOf(false) }
    var guardianName by remember { mutableStateOf(initial.guardianName) }
    var guardianPhone by remember { mutableStateOf(initial.guardianPhone) }
    var guardianWhatsapp by remember { mutableStateOf(initial.guardianWhatsapp) }
    var gender by remember { mutableStateOf(initial.gender) }
    var address by remember { mutableStateOf(initial.address) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New student" else "Edit student") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = roll, onValueChange = { roll = it }, label = { Text("Roll number") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") },
                        trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                        divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; divMenu = false }) }
                    }
                }
                OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Gender") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = guardianName, onValueChange = { guardianName = it }, label = { Text("Guardian name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = guardianPhone, onValueChange = { guardianPhone = it.filter { c -> c.isDigit() } }, label = { Text("Guardian phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = guardianWhatsapp, onValueChange = { guardianWhatsapp = it.filter { c -> c.isDigit() } }, label = { Text("Guardian WhatsApp (with country code)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) onSave(initial.copy(
                    name = name.trim(), rollNumber = roll.trim(), divisionId = divisionId, gender = gender.trim(),
                    guardianName = guardianName.trim(), guardianPhone = guardianPhone.trim(), guardianWhatsapp = guardianWhatsapp.trim(),
                    address = address.trim()
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
