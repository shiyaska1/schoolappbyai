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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AttendanceSummary
import com.school.attendance.data.Division
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import com.school.attendance.util.CsvExport
import com.school.attendance.util.WhatsAppShare
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class StudentReportRow(val student: Student, val summary: AttendanceSummary)

class ReportsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun loadDivision(divisionId: Long, from: Long, to: Long): List<StudentReportRow> {
        val students = repo.studentsInDivision(divisionId)
        return students.map { StudentReportRow(it, repo.summaryForStudent(it.id, divisionId, from, to)) }
    }

    suspend fun loadStudent(student: Student, from: Long, to: Long): AttendanceSummary =
        repo.summaryForStudent(student.id, student.divisionId, from, to)
}

private fun firstOfThisMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit, vm: ReportsViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val divisions by vm.divisions.collectAsState()

    var divisionId by remember { mutableStateOf(0L) }
    var divMenu by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("All students") }
    var studentId by remember { mutableStateOf(0L) }
    var studentMenu by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf(firstOfThisMonth()) }
    var to by remember { mutableStateOf(System.currentTimeMillis()) }
    var rows by remember { mutableStateOf<List<StudentReportRow>>(emptyList()) }
    var shareQueue by remember { mutableStateOf<List<StudentReportRow>>(emptyList()) }
    var shareIndex by remember { mutableStateOf(0) }

    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    fun summaryMessage(r: StudentReportRow): String =
        "${r.student.name}'s attendance (${dateFmt.format(from)} – ${dateFmt.format(to)}): " +
            "Present ${r.summary.present}/${r.summary.workingDays} working days (${r.summary.percent}%), Absent ${r.summary.absent}."

    Scaffold(topBar = { TopAppBar(title = { Text("Attendance Reports") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Box {
                OutlinedButton(onClick = { divMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(divisions.firstOrNull { it.id == divisionId }?.name ?: "Choose division") }
                DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                    divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; studentId = 0L; rows = emptyList(); divMenu = false }) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All students", "One student").forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
            }
            if (mode == "One student" && rows.isNotEmpty()) {
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedButton(onClick = { studentMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(rows.firstOrNull { it.student.id == studentId }?.student?.name ?: "Choose student") }
                    DropdownMenu(expanded = studentMenu, onDismissRequest = { studentMenu = false }) {
                        rows.forEach { r -> DropdownMenuItem(text = { Text(r.student.name) }, onClick = { studentId = r.student.id; studentMenu = false }) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(context, from) { from = it } }, modifier = Modifier.weight(1f)) { Text("From " + dateFmt.format(from)) }
                OutlinedButton(onClick = { pickDate(context, to) { to = it } }, modifier = Modifier.weight(1f)) { Text("To " + dateFmt.format(to)) }
            }
            Button(
                onClick = { if (divisionId != 0L) scope.launch { rows = vm.loadDivision(divisionId, from, to) } },
                enabled = divisionId != 0L, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("Load report") }

            val visibleRows = if (mode == "One student" && studentId != 0L) rows.filter { it.student.id == studentId } else rows

            if (visibleRows.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        CsvExport.export(
                            context, "attendance_report.csv",
                            listOf("Student", "Roll", "Working Days", "Present", "Absent", "Percent"),
                            visibleRows.map { listOf(it.student.name, it.student.rollNumber, it.summary.workingDays.toString(), it.summary.present.toString(), it.summary.absent.toString(), it.summary.percent.toString()) }
                        )
                    }, modifier = Modifier.weight(1f)) { Text("Export CSV") }
                    OutlinedButton(onClick = { shareQueue = visibleRows.filter { it.student.guardianWhatsapp.isNotBlank() }; shareIndex = 0
                        shareQueue.getOrNull(0)?.let { WhatsAppShare.send(context, it.student.guardianWhatsapp, summaryMessage(it)) }
                    }, modifier = Modifier.weight(1f)) { Text("Share via WhatsApp") }
                }
                if (shareQueue.isNotEmpty() && shareIndex < shareQueue.size) {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("Shared ${shareIndex + 1}/${shareQueue.size} — ", modifier = Modifier.weight(1f))
                        Button(onClick = {
                            shareIndex++
                            shareQueue.getOrNull(shareIndex)?.let { WhatsAppShare.send(context, it.student.guardianWhatsapp, summaryMessage(it)) }
                        }, enabled = shareIndex + 1 < shareQueue.size) { Text("Next") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(visibleRows, key = { it.student.id }) { r ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.student.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Working ${r.summary.workingDays} · Present ${r.summary.present} · Absent ${r.summary.absent} · ${r.summary.percent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (r.summary.percent >= 75) androidx.compose.ui.graphics.Color(0xFF2E7D32) else if (r.summary.percent < 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
