package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.ExamMark
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarkEntryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val subjects = repo.subjects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exams = repo.exams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val roster = MutableStateFlow<List<Student>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val myTeacherId: Long = prefs.loggedInTeacherId
    val isAdmin = MutableStateFlow(false)

    init {
        viewModelScope.launch { isAdmin.value = repo.teacherById(myTeacherId)?.isAdmin == true }
    }

    fun loadRoster(divisionId: Long, examId: Long, subjectId: Long, onLoaded: (Map<Long, Pair<Double, Boolean>>) -> Unit) {
        viewModelScope.launch {
            val students = repo.studentsInDivision(divisionId)
            roster.value = students
            val existing = repo.examMarksFor(examId, subjectId).associateBy { it.studentId }
            onLoaded(students.associate { it.id to ((existing[it.id]?.marksObtained ?: 0.0) to (existing[it.id]?.absent ?: false)) })
        }
    }

    fun save(examId: Long, subjectId: Long, marks: Map<Long, Pair<Double, Boolean>>) {
        if (marks.isEmpty()) { message.value = "No students in this division"; return }
        viewModelScope.launch {
            repo.saveExamMarks(examId, subjectId, prefs.loggedInTeacherId, prefs.deviceId, marks)
            message.value = "Marks saved"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkEntryScreen(onBack: () -> Unit, vm: MarkEntryViewModel = viewModel()) {
    val divisions by vm.divisions.collectAsState()
    val allSubjects by vm.subjects.collectAsState()
    val allExams by vm.exams.collectAsState()
    val roster by vm.roster.collectAsState()
    val message by vm.message.collectAsState()
    val isAdmin by vm.isAdmin.collectAsState()

    var divisionId by remember { mutableStateOf(0L) }
    var subjectId by remember { mutableStateOf(0L) }
    var examId by remember { mutableStateOf(0L) }
    var divMenu by remember { mutableStateOf(false) }
    var subjMenu by remember { mutableStateOf(false) }
    var examMenu by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val marks = remember { mutableStateMapOf<Long, Pair<Double, Boolean>>() }

    // Admin sees every subject; a regular teacher only their own (Subject.teacherId) plus unassigned ones.
    val subjectsInDivision = allSubjects.filter { it.divisionId == divisionId && (isAdmin || it.teacherId == 0L || it.teacherId == vm.myTeacherId) }
    val examsForSubject = allExams.filter { it.divisionId == divisionId }

    Scaffold(topBar = { TopAppBar(title = { Text("Exam Mark Entry") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Box {
                OutlinedTextField(value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") },
                    trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                    divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; subjectId = 0L; examId = 0L; loaded = false; divMenu = false }) }
                }
            }
            Box(Modifier.padding(top = 8.dp)) {
                OutlinedTextField(value = subjectsInDivision.firstOrNull { it.id == subjectId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Subject") },
                    trailingIcon = { IconButton(onClick = { if (divisionId != 0L) subjMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = subjMenu, onDismissRequest = { subjMenu = false }) {
                    subjectsInDivision.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { subjectId = s.id; loaded = false; subjMenu = false }) }
                }
            }
            Box(Modifier.padding(top = 8.dp)) {
                OutlinedTextField(value = examsForSubject.firstOrNull { it.id == examId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Exam") },
                    trailingIcon = { IconButton(onClick = { if (divisionId != 0L) examMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = examMenu, onDismissRequest = { examMenu = false }) {
                    examsForSubject.forEach { e -> DropdownMenuItem(text = { Text("${e.name} (out of ${e.maxMarks.toInt()})") }, onClick = { examId = e.id; loaded = false; examMenu = false }) }
                }
            }
            Button(
                onClick = { vm.loadRoster(divisionId, examId, subjectId) { m -> marks.clear(); marks.putAll(m); loaded = true } },
                enabled = divisionId != 0L && subjectId != 0L && examId != 0L,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Load students") }

            val maxMarks = examsForSubject.firstOrNull { it.id == examId }?.maxMarks ?: 100.0
            if (loaded) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(roster, key = { it.id }) { s ->
                        val (m, absent) = marks[s.id] ?: (0.0 to false)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name)
                                if (s.rollNumber.isNotBlank()) Text("Roll ${s.rollNumber}", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedTextField(
                                value = if (m == 0.0 && absent) "" else if (m == 0.0) "" else m.toString(),
                                onValueChange = { v -> marks[s.id] = (v.toDoubleOrNull() ?: 0.0) to absent },
                                enabled = !absent, singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(90.dp), label = { Text("/${maxMarks.toInt()}") }
                            )
                            Column(Modifier.padding(start = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Checkbox(checked = absent, onCheckedChange = { checked -> marks[s.id] = (if (checked) 0.0 else m) to checked })
                                Text("Absent", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Button(onClick = { vm.save(examId, subjectId, marks.toMap()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save marks") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
