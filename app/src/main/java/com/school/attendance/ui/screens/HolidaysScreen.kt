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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import com.school.attendance.data.Holiday
import com.school.attendance.data.Repository
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HolidaysViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    val holidays = repo.holidays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val divisions = repo.divisions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(h: Holiday) = viewModelScope.launch { repo.upsertHoliday(h) }
    fun delete(h: Holiday) = viewModelScope.launch { repo.deleteHoliday(h) }
    fun fetchPublic(year: Int, onDone: (String) -> Unit) = viewModelScope.launch {
        repo.fetchIndianPublicHolidays(year).fold(
            onSuccess = { onDone("Added $it public holidays for $year") },
            onFailure = { onDone("Fetch failed: ${it.message}") }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(onBack: () -> Unit, vm: HolidaysViewModel = viewModel()) {
    val context = LocalContext.current
    val holidays by vm.holidays.collectAsState()
    val divisions by vm.divisions.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val divisionNames = remember(divisions) { divisions.associate { it.id to it.name } }

    if (showAdd) HolidayDialog(divisions, { vm.save(it); showAdd = false }, { showAdd = false })

    Scaffold(
        topBar = { TopAppBar(title = { Text("Holidays") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add holiday") } }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Text("Saturdays and Sundays are off by default (change in Settings).", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { val year = Calendar.getInstance().get(Calendar.YEAR); vm.fetchPublic(year) { message = it } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) { Text("Fetch Indian public holidays for ${Calendar.getInstance().get(Calendar.YEAR)}") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                items(holidays.sortedBy { it.dateMillis }, key = { it.id }) { h ->
                    ListItem(
                        headlineContent = { Text(h.name) },
                        supportingContent = { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(h.dateMillis) + "  ·  " + (if (h.divisionId == 0L) "Whole school" else (divisionNames[h.divisionId] ?: "")) + "  ·  " + h.source) },
                        trailingContent = { IconButton(onClick = { vm.delete(h) }) { Icon(Icons.Filled.Delete, "Delete") } }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayDialog(divisions: List<Division>, onSave: (Holiday) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var divisionId by remember { mutableStateOf(0L) }
    var menu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New holiday") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Reason") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = if (divisionId == 0L) "Whole school" else (divisions.firstOrNull { it.id == divisionId }?.name ?: ""),
                        onValueChange = {}, readOnly = true, label = { Text("Applies to") },
                        trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Whole school") }, onClick = { divisionId = 0L; menu = false })
                        divisions.forEach { d -> DropdownMenuItem(text = { Text(d.name) }, onClick = { divisionId = d.id; menu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(Holiday(dateMillis = date, name = name.trim(), source = "MANUAL", divisionId = divisionId)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
