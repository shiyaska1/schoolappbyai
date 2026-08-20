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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SwitchToParentViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val courses = repo.courses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val students = repo.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwitchToParentScreen(onBack: () -> Unit, onPick: (Long) -> Unit, vm: SwitchToParentViewModel = viewModel()) {
    val courses by vm.courses.collectAsState()
    val divisions by vm.divisions.collectAsState()
    val students by vm.students.collectAsState()

    var courseId by remember { mutableStateOf(0L) }
    var divisionId by remember { mutableStateOf(0L) }
    var courseMenu by remember { mutableStateOf(false) }
    var divMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val divisionNames = remember(divisions) { divisions.associate { it.id to it.name } }
    val divisionsInCourse = if (courseId == 0L) divisions else divisions.filter { it.courseId == courseId }
    val filtered = students.filter { s ->
        (divisionId == 0L || s.divisionId == divisionId) &&
            (courseId == 0L || divisions.firstOrNull { it.id == s.divisionId }?.courseId == courseId) &&
            (query.isBlank() || s.name.contains(query, ignoreCase = true) || s.rollNumber.contains(query, ignoreCase = true))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Switch to Parent View") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, label = { Text("Search by name or roll number") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(value = courses.firstOrNull { it.id == courseId }?.name ?: "All courses", onValueChange = {}, readOnly = true, label = { Text("Course") },
                        trailingIcon = { IconButton(onClick = { courseMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = courseMenu, onDismissRequest = { courseMenu = false }) {
                        DropdownMenuItem(text = { Text("All courses") }, onClick = { courseId = 0L; divisionId = 0L; courseMenu = false })
                        courses.forEach { c -> DropdownMenuItem(text = { Text(c.name) }, onClick = { courseId = c.id; divisionId = 0L; courseMenu = false }) }
                    }
                }
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(value = divisionNames[divisionId] ?: "All divisions", onValueChange = {}, readOnly = true, label = { Text("Division") },
                        trailingIcon = { IconButton(onClick = { divMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = divMenu, onDismissRequest = { divMenu = false }) {
                        DropdownMenuItem(text = { Text("All divisions") }, onClick = { divisionId = 0L; divMenu = false })
                        divisionsInCourse.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; divMenu = false }) }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { s ->
                    ListItem(
                        headlineContent = { Text(s.name) },
                        supportingContent = { Text(listOfNotNull(s.rollNumber.ifBlank { null }, divisionNames[s.divisionId]).joinToString("  ·  ")) },
                        modifier = Modifier.fillMaxWidth().clickable { onPick(s.id) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
