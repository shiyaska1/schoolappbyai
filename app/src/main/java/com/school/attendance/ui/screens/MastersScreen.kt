package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Course
import com.school.attendance.data.Division
import com.school.attendance.data.Repository
import com.school.attendance.data.Subject
import com.school.attendance.data.Teacher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MastersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val courses = repo.courses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val subjects = repo.subjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCourse(c: Course) = viewModelScope.launch { repo.upsertCourse(c) }
    fun deleteCourse(c: Course) = viewModelScope.launch { repo.deleteCourse(c) }
    fun saveDivision(d: Division) = viewModelScope.launch { repo.upsertDivision(d) }
    fun deleteDivision(d: Division) = viewModelScope.launch { repo.deleteDivision(d) }
    fun saveSubject(s: Subject) = viewModelScope.launch { repo.upsertSubject(s) }
    fun deleteSubject(s: Subject) = viewModelScope.launch { repo.deleteSubject(s) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MastersScreen(onBack: () -> Unit, vm: MastersViewModel = viewModel()) {
    val courses by vm.courses.collectAsState()
    val divisions by vm.divisions.collectAsState()
    val subjects by vm.subjects.collectAsState()
    val teachers by vm.teachers.collectAsState()
    var tab by remember { mutableStateOf("Courses") }
    var editCourse by remember { mutableStateOf<Course?>(null) }
    var editDivision by remember { mutableStateOf<Division?>(null) }
    var editSubject by remember { mutableStateOf<Subject?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    editCourse?.takeIf { showDialog && tab == "Courses" }?.let { c ->
        NameDialog("Course", c.name, { vm.saveCourse(c.copy(name = it)); showDialog = false }, { showDialog = false })
    }
    editDivision?.takeIf { showDialog && tab == "Divisions" }?.let { d ->
        DivisionDialog(d, courses, { vm.saveDivision(it); showDialog = false }, { showDialog = false })
    }
    editSubject?.takeIf { showDialog && tab == "Subjects" }?.let { s ->
        SubjectDialog(s, divisions, teachers, { vm.saveSubject(it); showDialog = false }, { showDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Courses / Divisions / Subjects") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary))
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (tab) {
                    "Courses" -> editCourse = Course(name = "")
                    "Divisions" -> editDivision = Division(name = "")
                    else -> editSubject = Subject(name = "")
                }
                showDialog = true
            }) { Icon(Icons.Filled.Add, "Add") }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Courses", "Divisions", "Subjects").forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) }) }
            }
            val courseNames = remember(courses) { courses.associate { it.id to it.name } }
            val divisionNames = remember(divisions) { divisions.associate { it.id to it.name } }
            val teacherNames = remember(teachers) { teachers.associate { it.id to it.name } }
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                when (tab) {
                    "Courses" -> items(courses, key = { it.id }) { c -> MasterRow(c.name, "") { editCourse = c; showDialog = true } }
                    "Divisions" -> items(divisions, key = { it.id }) { d -> MasterRow(d.name, courseNames[d.courseId] ?: "") { editDivision = d; showDialog = true } }
                    else -> items(subjects, key = { it.id }) { s ->
                        MasterRow(s.name, listOfNotNull(divisionNames[s.divisionId], teacherNames[s.teacherId]).joinToString("  ·  ")) { editSubject = s; showDialog = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterRow(title: String, sub: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { if (sub.isNotBlank()) Text(sub) }, modifier = Modifier.fillMaxWidth().clickable { onClick() })
    HorizontalDivider()
}

@Composable
private fun NameDialog(label: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.isBlank()) "New $label" else "Edit $label") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("$label name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DivisionDialog(initial: Division, courses: List<Course>, onSave: (Division) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var courseId by remember { mutableStateOf(initial.courseId) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "New division" else "Edit division") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Division name (e.g. A)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = courses.firstOrNull { it.id == courseId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Course") },
                        trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        courses.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { courseId = c.id; menu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(initial.copy(name = name.trim(), courseId = courseId)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDialog(initial: Subject, divisions: List<Division>, teachers: List<Teacher>, onSave: (Subject) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var divisionId by remember { mutableStateOf(initial.divisionId) }
    var teacherId by remember { mutableStateOf(initial.teacherId) }
    var divMenu by remember { mutableStateOf(false) }
    var teacherMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.id == 0L) "New subject" else "Edit subject") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") },
                        trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                        divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; divMenu = false }) }
                    }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = teachers.firstOrNull { it.id == teacherId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Teacher") },
                        trailingIcon = { IconButton(onClick = { teacherMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = teacherMenu, onDismissRequest = { teacherMenu = false }) {
                        teachers.forEach { t -> DropdownMenuItem(text = { Text(t.name) }, onClick = { teacherId = t.id; teacherMenu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(initial.copy(name = name.trim(), divisionId = divisionId, teacherId = teacherId)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
