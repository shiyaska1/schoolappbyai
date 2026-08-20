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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import com.school.attendance.report.ReportCardEngine
import com.school.attendance.report.StudentReportCard
import com.school.attendance.util.ReportCardPdf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReportCardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exams = repo.exams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val results = MutableStateFlow<List<Pair<Student, StudentReportCard>>>(emptyList())

    fun build(divisionId: Long, termGroup: String) {
        viewModelScope.launch {
            val students = repo.studentsInDivision(divisionId)
            val subjects = repo.subjectsOnce()
            val allExams = repo.examsOnce()
            val marks = repo.examMarksOnce()
            val bands = repo.gradeBandsOnce()
            val cards = ReportCardEngine.build(termGroup, divisionId, students, subjects, allExams, marks, bands)
            results.value = students.mapNotNull { s -> cards.firstOrNull { it.studentId == s.id }?.let { s to it } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCardScreen(onBack: () -> Unit, vm: ReportCardViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val divisions by vm.divisions.collectAsState()
    val exams by vm.exams.collectAsState()
    val results by vm.results.collectAsState()

    var divisionId by remember { mutableStateOf(0L) }
    var termGroup by remember { mutableStateOf("") }
    var divMenu by remember { mutableStateOf(false) }
    var termMenu by remember { mutableStateOf(false) }

    val termGroups = remember(divisionId, exams) { ReportCardEngine.termGroupsFor(divisionId, exams) }
    val divisionLabel = divisions.firstOrNull { it.id == divisionId }?.name ?: ""

    Scaffold(topBar = { TopAppBar(title = { Text("Report Cards") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Box {
                OutlinedTextField(value = divisionLabel, onValueChange = {}, readOnly = true, label = { Text("Division") },
                    trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                    divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; termGroup = ""; divMenu = false }) }
                }
            }
            Box(Modifier.padding(top = 8.dp)) {
                OutlinedTextField(value = termGroup, onValueChange = {}, readOnly = true, label = { Text("Report card group") },
                    trailingIcon = { IconButton(onClick = { if (divisionId != 0L) termMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = termMenu, onDismissRequest = { termMenu = false }) {
                    if (termGroups.isEmpty()) DropdownMenuItem(text = { Text("No exam groups yet for this division") }, onClick = {})
                    termGroups.forEach { g -> DropdownMenuItem(text = { Text(g) }, onClick = { termGroup = g; termMenu = false }) }
                }
            }
            Button(
                onClick = { vm.build(divisionId, termGroup) },
                enabled = divisionId != 0L && termGroup.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Build report cards") }

            if (results.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val file = ReportCardPdf.generateForDivision(context, prefs.schoolName, termGroup, divisionLabel, results, prefs.reportCardDisplayMode)
                            ReportCardPdf.share(context, file)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Share whole division as one PDF") }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(results, key = { it.first.id }) { (student, card) ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp).fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text(student.name, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Roll ${student.rollNumber} · %.1f%% · Grade ${card.overallGrade}".format(card.overallPercent),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                OutlinedButton(onClick = {
                                    val file = ReportCardPdf.generateForStudent(context, prefs.schoolName, termGroup, divisionLabel, student, card, prefs.reportCardDisplayMode)
                                    ReportCardPdf.share(context, file)
                                }) { Text("Share") }
                            }
                        }
                    }
                }
            }
        }
    }
}
