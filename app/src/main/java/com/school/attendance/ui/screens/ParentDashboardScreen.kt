package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.AttendanceSummary
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class ParentDashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val student = MutableStateFlow<Student?>(null)
    val busNumber = MutableStateFlow<String?>(null)
    val summary = MutableStateFlow<AttendanceSummary?>(null)

    init {
        viewModelScope.launch {
            val id = AppPrefs(app).loggedInStudentId
            val s = repo.studentsOnce().firstOrNull { it.id == id }
            student.value = s
            if (s != null) {
                busNumber.value = repo.busNumberForStudent(s.id)
                val from = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
                summary.value = repo.summaryForStudent(s.id, s.divisionId, from, System.currentTimeMillis())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentDashboardScreen(onLogout: () -> Unit, vm: ParentDashboardViewModel = viewModel()) {
    val student by vm.student.collectAsState()
    val busNumber by vm.busNumber.collectAsState()
    val summary by vm.summary.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(student?.name ?: "Parent") },
            actions = { IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Log out") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("This month's attendance", style = MaterialTheme.typography.titleSmall)
                    summary?.let { s ->
                        Text("Present ${s.present} / ${s.workingDays} working days (${s.percent}%)", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                        Text("Absent: ${s.absent}", style = MaterialTheme.typography.bodyMedium)
                    } ?: Text("Loading...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            busNumber?.let { bus ->
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("School bus", style = MaterialTheme.typography.titleSmall)
                        Text(bus, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                        Text("Open Bus Location from the school app's staff side to track it live.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
