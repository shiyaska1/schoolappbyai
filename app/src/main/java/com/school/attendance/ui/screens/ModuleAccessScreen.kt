package com.school.attendance.ui.screens

import android.app.Application
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.Routes
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Every module a non-admin dashboard can show. Admins always see everything — this screen only
 * ever restricts what a teacher/staff/driver's own dashboard offers them. */
private val HIDEABLE_MODULES = listOf(
    Routes.ATTENDANCE to "Mark Attendance",
    Routes.SELF_ATTENDANCE to "My Attendance (self, geo-fenced)",
    Routes.LIVE_LOCATION to "Bus Location",
    Routes.MARK_ENTRY to "Exam Mark Entry",
    Routes.SWITCH_PARENT to "Switch to Parent View",
    Routes.MESSAGES to "Messages",
    Routes.REPORTS to "Reports",
    Routes.REPORT_CARDS to "Report Cards"
)

class ModuleAccessViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(t: Teacher) = viewModelScope.launch { repo.upsertTeacher(t) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleAccessScreen(onBack: () -> Unit, vm: ModuleAccessViewModel = viewModel()) {
    val allTeachers by vm.teachers.collectAsState()
    val teachers = allTeachers.filter { !it.isAdmin }
    var selectedId by remember { mutableStateOf(0L) }
    var menu by remember { mutableStateOf(false) }
    val visible = remember { mutableStateMapOf<String, Boolean>() }
    var loadedFor by remember { mutableStateOf(0L) }

    val selected = teachers.firstOrNull { it.id == selectedId }
    if (selected != null && loadedFor != selected.id) {
        val hidden = selected.hiddenModules.split("|").filter { it.isNotBlank() }.toSet()
        visible.clear()
        HIDEABLE_MODULES.forEach { (route, _) -> visible[route] = route !in hidden }
        loadedFor = selected.id
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Module Access") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text(
                "Choose which modules a teacher/staff member's own dashboard shows. Admin accounts always see everything, regardless of this setting.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = selected?.let { it.name + if (it.designation.isNotBlank()) " (${it.designation})" else "" } ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("Teacher / staff") },
                    trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (teachers.isEmpty()) DropdownMenuItem(text = { Text("No non-admin staff yet") }, onClick = {})
                    teachers.forEach { t -> DropdownMenuItem(text = { Text(t.name + if (t.designation.isNotBlank()) " (${t.designation})" else "") }, onClick = { selectedId = t.id; menu = false }) }
                }
            }

            if (selected != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(HIDEABLE_MODULES) { (route, label) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, Modifier.weight(1f))
                            Checkbox(checked = visible[route] ?: true, onCheckedChange = { visible[route] = it })
                        }
                    }
                }
                Button(
                    onClick = {
                        val hidden = HIDEABLE_MODULES.map { it.first }.filter { visible[it] == false }
                        vm.save(selected.copy(hiddenModules = hidden.joinToString("|")))
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Save") }
            }
        }
    }
}
