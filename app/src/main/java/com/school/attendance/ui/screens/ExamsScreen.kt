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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Division
import com.school.attendance.data.Exam
import com.school.attendance.data.ExamType
import com.school.attendance.data.Repository
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class ExamsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val exams = repo.exams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(e: Exam) = viewModelScope.launch { repo.upsertExam(e) }
    fun delete(e: Exam) = viewModelScope.launch { repo.deleteExam(e) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(onBack: () -> Unit, vm: ExamsViewModel = viewModel()) {
    val exams by vm.exams.collectAsState()
    val divisions by vm.divisions.collectAsState()
    var editing by remember { mutableStateOf<Exam?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val divisionName = remember(divisions) { divisions.associate { it.id to it.name } }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    if (showNew || editing != null) {
        ExamDialog(
            exam = editing, divisions = divisions,
            onSave = { vm.save(it); showNew = false; editing = null },
            onDismiss = { showNew = false; editing = null }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exams") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "New exam") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            items(exams.sortedByDescending { it.dateMillis }, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp).fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                (if (e.examType == ExamType.TERM_EXAM) "Term Exam" else "Unit Test") +
                                    " · ${divisionName[e.divisionId] ?: "?"} · Term group: ${e.termGroup.ifBlank { "-" }}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "${fmt.format(e.dateMillis)} · Out of ${e.maxMarks.toInt()}, counts for ${e.reportWeight.toInt()} on report card",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editing = e }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = { vm.delete(e) }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDialog(exam: Exam?, divisions: List<Division>, onSave: (Exam) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(exam?.name ?: "") }
    var examType by remember { mutableStateOf(exam?.examType ?: ExamType.UNIT_TEST) }
    var termGroup by remember { mutableStateOf(exam?.termGroup ?: "") }
    var divisionId by remember { mutableStateOf(exam?.divisionId ?: 0L) }
    var divMenu by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(exam?.dateMillis ?: System.currentTimeMillis()) }
    var maxMarks by remember { mutableStateOf((exam?.maxMarks ?: 100.0).toInt().toString()) }
    var reportWeight by remember { mutableStateOf((exam?.reportWeight ?: 100.0).toInt().toString()) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exam == null) "New exam" else "Edit exam") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Unit Test 1)") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = examType == ExamType.UNIT_TEST, onClick = { examType = ExamType.UNIT_TEST }, label = { Text("Unit Test") })
                    FilterChip(selected = examType == ExamType.TERM_EXAM, onClick = { examType = ExamType.TERM_EXAM }, label = { Text("Term Exam") })
                }
                OutlinedTextField(
                    value = termGroup, onValueChange = { termGroup = it },
                    label = { Text("Report card group (e.g. Term 1)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") },
                        trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                        divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; divMenu = false }) }
                    }
                }
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Date: " + fmt.format(date)) }
                OutlinedTextField(
                    value = maxMarks, onValueChange = { maxMarks = it.filter { c -> c.isDigit() } },
                    label = { Text("Marks entered out of (e.g. 100)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = reportWeight, onValueChange = { reportWeight = it.filter { c -> c.isDigit() } },
                    label = { Text("Counts for, on report card (e.g. 20)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    "Teachers enter marks out of the first number; the report card scales it to the second so several exams can add up to a term total.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && divisionId != 0L) {
                    onSave(
                        (exam ?: Exam(name = name, divisionId = divisionId)).copy(
                            name = name, examType = examType, termGroup = termGroup.trim(), divisionId = divisionId,
                            dateMillis = date, maxMarks = (maxMarks.toDoubleOrNull() ?: 100.0), reportWeight = (reportWeight.toDoubleOrNull() ?: 100.0)
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
