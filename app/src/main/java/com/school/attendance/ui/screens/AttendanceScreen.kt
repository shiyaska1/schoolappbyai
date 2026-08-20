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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateMapOf
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
import com.school.attendance.data.AttendanceMode
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import com.school.attendance.sms.SmsSender
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class AttendanceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)
    val courses = repo.courses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val roster = MutableStateFlow<List<Student>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val isWorkingDay = MutableStateFlow(true)
    val attendanceMode: AttendanceMode get() = runCatching { AttendanceMode.valueOf(prefs.attendanceMode) }.getOrDefault(AttendanceMode.ONCE)

    fun loadRoster(divisionId: Long, session: String, dateMillis: Long, onMarks: (Map<Long, Boolean>) -> Unit) {
        viewModelScope.launch {
            isWorkingDay.value = repo.isWorkingDay(dateMillis, divisionId)
            val students = repo.studentsInDivision(divisionId)
            roster.value = students
            val existing = repo.attendanceForDay(divisionId, session, dateMillis).associateBy { it.studentId }
            onMarks(students.associate { it.id to (existing[it.id]?.present ?: true) })
        }
    }

    fun save(divisionId: Long, session: String, dateMillis: Long, marks: Map<Long, Boolean>) {
        if (marks.isEmpty()) { message.value = "No students in this division"; return }
        viewModelScope.launch {
            val teacherId = prefs.loggedInTeacherId
            repo.saveAttendance(divisionId, session, dateMillis, teacherId, prefs.deviceId, marks)
            message.value = "Attendance saved"
            if (prefs.smsOnAbsent) {
                val absentees = roster.value.filter { marks[it.id] == false }
                absentees.forEach { s ->
                    val number = s.guardianPhone.ifBlank { s.guardianWhatsapp }
                    if (number.isNotBlank()) {
                        val msg = "${s.name} was marked absent today (${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(dateMillis)})."
                        SmsSender.send(getApplication(), number, msg)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(onBack: () -> Unit, vm: AttendanceViewModel = viewModel()) {
    val context = LocalContext.current
    val courses by vm.courses.collectAsState()
    val divisions by vm.divisions.collectAsState()
    val roster by vm.roster.collectAsState()
    val message by vm.message.collectAsState()
    val isWorkingDay by vm.isWorkingDay.collectAsState()

    var courseId by remember { mutableStateOf(0L) }
    var divisionId by remember { mutableStateOf(0L) }
    var session by remember { mutableStateOf("FULL") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var courseMenu by remember { mutableStateOf(false) }
    var divMenu by remember { mutableStateOf(false) }
    val marks = remember { mutableStateMapOf<Long, Boolean>() }
    var loaded by remember { mutableStateOf(false) }
    val divisionsInCourse = divisions.filter { it.courseId == courseId }

    Scaffold(topBar = { TopAppBar(title = { Text("Mark Attendance") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Box {
                OutlinedTextField(value = divisions.firstOrNull { it.id == divisionId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Division") },
                    trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                    divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; loaded = false; divMenu = false }) }
                }
            }
            if (vm.attendanceMode == AttendanceMode.TWICE) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MORNING" to "Morning", "AFTERNOON" to "Afternoon").forEach { (v, label) ->
                        FilterChip(selected = session == v, onClick = { session = v; loaded = false }, label = { Text(label) })
                    }
                }
            } else {
                session = "FULL"
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(context, date) { date = it; loaded = false } }, modifier = Modifier.weight(1f)) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
                Button(onClick = { if (divisionId != 0L) vm.loadRoster(divisionId, session, date) { m -> marks.clear(); marks.putAll(m); loaded = true } }, enabled = divisionId != 0L, modifier = Modifier.weight(1f)) { Text("Load") }
            }

            if (loaded && !isWorkingDay) {
                Text("This day is marked as a holiday. You can still mark attendance if needed.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }

            if (loaded) {
                val present = marks.values.count { it }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Present $present / ${roster.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { roster.forEach { marks[it.id] = true } }) { Text("All present") }
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(roster, key = { it.id }) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = marks[s.id] ?: true, onCheckedChange = { marks[s.id] = it })
                            Column(Modifier.weight(1f)) {
                                Text(s.name)
                                if (s.rollNumber.isNotBlank()) Text("Roll ${s.rollNumber}", style = MaterialTheme.typography.labelSmall)
                            }
                            Text(if (marks[s.id] != false) "Present" else "Absent", color = if (marks[s.id] != false) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider()
                    }
                }
                Button(onClick = { vm.save(divisionId, session, date, marks.toMap()) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save attendance") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
