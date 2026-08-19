package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
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
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TeacherAttendanceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = AppPrefs(app)
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadDay(dateMillis: Long, onMarks: (Map<Long, Boolean>) -> Unit) {
        viewModelScope.launch {
            val existing = repo.teacherAttendanceForDay(dateMillis).associateBy { it.teacherId }
            onMarks(teachers.value.associate { it.id to (existing[it.id]?.present ?: true) })
        }
    }

    fun save(dateMillis: Long, marks: Map<Long, Boolean>, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.saveTeacherAttendance(dateMillis, prefs.loggedInTeacherId, prefs.deviceId, marks)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherAttendanceScreen(onBack: () -> Unit, vm: TeacherAttendanceViewModel = viewModel()) {
    val context = LocalContext.current
    val teachers by vm.teachers.collectAsState()
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    val marks = remember { mutableStateMapOf<Long, Boolean>() }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(date, teachers) { vm.loadDay(date) { m -> marks.clear(); marks.putAll(m) } }

    Scaffold(topBar = { TopAppBar(title = { Text("Staff Attendance") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth()) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
            val present = marks.values.count { it }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Present $present / ${teachers.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { teachers.forEach { marks[it.id] = true } }) { Text("All present") }
            }
            HorizontalDivider()
            LazyColumn(Modifier.weight(1f)) {
                items(teachers, key = { it.id }) { t: Teacher ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = marks[t.id] ?: true, onCheckedChange = { marks[t.id] = it })
                        Column(Modifier.weight(1f)) {
                            Text(t.name)
                            Text(if (t.isTeachingStaff) "Teaching" else "Non-teaching", style = MaterialTheme.typography.labelSmall)
                        }
                        Text(if (marks[t.id] != false) "Present" else "Absent", color = if (marks[t.id] != false) androidx.compose.ui.graphics.Color(0xFF2E7D32) else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                }
            }
            Button(onClick = { vm.save(date, marks.toMap()) { message = "Attendance saved" } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Save") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
