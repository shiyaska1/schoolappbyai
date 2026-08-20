package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Bus
import com.school.attendance.data.Repository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BusesViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val buses = repo.buses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(b: Bus) = viewModelScope.launch { repo.upsertBus(b) }
    fun delete(b: Bus) = viewModelScope.launch { repo.deleteBus(b) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusesScreen(onBack: () -> Unit, vm: BusesViewModel = viewModel()) {
    val buses by vm.buses.collectAsState()
    var edit by remember { mutableStateOf<Bus?>(null) }

    edit?.let { b -> BusDialog(b, { vm.save(it); edit = null }, { edit = null }, { vm.delete(it); edit = null }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Buses") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Bus(busNumber = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(buses, key = { it.id }) { b ->
                ListItem(
                    headlineContent = { Text(b.busNumber) },
                    supportingContent = { if (b.route.isNotBlank()) Text(b.route) },
                    modifier = Modifier.fillMaxWidth().clickable { edit = b }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BusDialog(initial: Bus, onSave: (Bus) -> Unit, onDismiss: () -> Unit, onDelete: (Bus) -> Unit) {
    var busNumber by remember { mutableStateOf(initial.busNumber) }
    var route by remember { mutableStateOf(initial.route) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New bus" else "Edit bus") },
        text = {
            Column {
                OutlinedTextField(value = busNumber, onValueChange = { busNumber = it }, label = { Text("Bus number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = route, onValueChange = { route = it }, label = { Text("Route") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (busNumber.isNotBlank()) onSave(initial.copy(busNumber = busNumber.trim(), route = route.trim())) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (initial.id != 0L) TextButton(onClick = { onDelete(initial) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
