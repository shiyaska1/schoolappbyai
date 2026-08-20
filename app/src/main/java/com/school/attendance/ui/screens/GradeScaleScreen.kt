package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.GradeBand
import com.school.attendance.data.Repository
import com.school.attendance.data.ReportCardDisplayMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GradeScaleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val bands = repo.gradeBands.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(g: GradeBand) = viewModelScope.launch { repo.upsertGradeBand(g) }
    fun delete(g: GradeBand) = viewModelScope.launch { repo.deleteGradeBand(g) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeScaleScreen(onBack: () -> Unit, vm: GradeScaleViewModel = viewModel()) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val bands by vm.bands.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var displayMode by remember { mutableStateOf(prefs.reportCardDisplayMode) }

    if (showNew) {
        GradeBandDialog(onSave = { vm.save(it); showNew = false }, onDismiss = { showNew = false })
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Grade Scale") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { showNew = true }) { Icon(Icons.Filled.Add, "New band") } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Report card shows", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = displayMode == ReportCardDisplayMode.MARKS_AND_GRADE,
                    onClick = { displayMode = ReportCardDisplayMode.MARKS_AND_GRADE; prefs.reportCardDisplayMode = displayMode },
                    label = { Text("Marks + Grade") }
                )
                FilterChip(
                    selected = displayMode == ReportCardDisplayMode.GRADE_ONLY,
                    onClick = { displayMode = ReportCardDisplayMode.GRADE_ONLY; prefs.reportCardDisplayMode = displayMode },
                    label = { Text("Grade Only") }
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Grade bands (percentage ranges)", style = MaterialTheme.typography.titleSmall)
            if (bands.isEmpty()) {
                Text(
                    "No grade bands yet — add some (e.g. 90-100 = A+) or marks will show without a grade.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(bands.sortedByDescending { it.minPercent }, key = { it.id }) { g ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text("${g.minPercent.toInt()}% – ${g.maxPercent.toInt()}%  →  ${g.grade}", style = MaterialTheme.typography.bodyMedium)
                                if (g.remark.isNotBlank()) Text(g.remark, style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { vm.delete(g) }) { Icon(Icons.Filled.Delete, "Delete") }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeBandDialog(onSave: (GradeBand) -> Unit, onDismiss: () -> Unit) {
    var min by remember { mutableStateOf("") }
    var max by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New grade band") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = min, onValueChange = { min = it.filter { c -> c.isDigit() } }, label = { Text("Min %") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = max, onValueChange = { max = it.filter { c -> c.isDigit() } }, label = { Text("Max %") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = grade, onValueChange = { grade = it }, label = { Text("Grade (e.g. A+)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = remark, onValueChange = { remark = it }, label = { Text("Remark (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lo = min.toDoubleOrNull(); val hi = max.toDoubleOrNull()
                if (lo != null && hi != null && grade.isNotBlank()) onSave(GradeBand(minPercent = lo, maxPercent = hi, grade = grade.trim(), remark = remark.trim()))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
