package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AccountGroup
import com.school.attendance.data.AccountHead
import com.school.attendance.data.AccountNature
import com.school.attendance.data.AccountingRepository
import com.school.attendance.data.CostCenter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AccountingRepository(app)
    val groups = repo.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val heads = repo.heads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val costCenters = repo.costCenters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { viewModelScope.launch { repo.ensureDefaultChartOfAccounts() } }

    fun saveGroup(g: AccountGroup) = viewModelScope.launch { repo.saveGroup(g) }
    fun deleteGroup(g: AccountGroup) = viewModelScope.launch { repo.deleteGroup(g) }
    fun saveHead(h: AccountHead) = viewModelScope.launch { repo.saveHead(h) }
    fun deleteHead(h: AccountHead) = viewModelScope.launch { repo.deleteHead(h) }
    fun saveCostCenter(c: CostCenter) = viewModelScope.launch { repo.saveCostCenter(c) }
    fun deleteCostCenter(c: CostCenter) = viewModelScope.launch { repo.deleteCostCenter(c) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit, vm: AccountsViewModel = viewModel()) {
    val groups by vm.groups.collectAsState()
    val heads by vm.heads.collectAsState()
    val costCenters by vm.costCenters.collectAsState()
    var tab by remember { mutableStateOf("Groups") }
    var editGroup by remember { mutableStateOf<AccountGroup?>(null) }
    var editHead by remember { mutableStateOf<AccountHead?>(null) }
    var editCc by remember { mutableStateOf<CostCenter?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    editGroup?.takeIf { showDialog && tab == "Groups" }?.let { g -> GroupDialog(g, groups, { vm.saveGroup(it); showDialog = false }, { showDialog = false }, if (g.id != 0L && !g.isSystem) { { vm.deleteGroup(g); showDialog = false } } else null) }
    editHead?.takeIf { showDialog && tab == "Heads" }?.let { h -> HeadDialog(h, groups, { vm.saveHead(it); showDialog = false }, { showDialog = false }, if (h.id != 0L && !h.isSystem) { { vm.deleteHead(h); showDialog = false } } else null) }
    editCc?.takeIf { showDialog && tab == "Cost Centers" }?.let { c -> NameOnlyDialog("Cost center", c.name, { vm.saveCostCenter(c.copy(name = it)); showDialog = false }, { showDialog = false }, if (c.id != 0L) { { vm.deleteCostCenter(c); showDialog = false } } else null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Chart of Accounts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                when (tab) {
                    "Groups" -> editGroup = AccountGroup(name = "", nature = AccountNature.ASSET)
                    "Heads" -> editHead = AccountHead(name = "", groupId = groups.firstOrNull()?.id ?: 0L)
                    else -> editCc = CostCenter(name = "")
                }
                showDialog = true
            }) { Icon(Icons.Filled.Add, "Add") }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Groups", "Heads", "Cost Centers").forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) }) }
            }
            val groupNames = remember(groups) { groups.associate { it.id to it.name } }
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                when (tab) {
                    "Groups" -> items(groups, key = { it.id }) { g -> MasterRow(g.name, g.nature.label) { editGroup = g; showDialog = true } }
                    "Heads" -> items(heads, key = { it.id }) { h -> MasterRow(h.name, groupNames[h.groupId] ?: "") { editHead = h; showDialog = true } }
                    else -> items(costCenters, key = { it.id }) { c -> MasterRow(c.name, "") { editCc = c; showDialog = true } }
                }
            }
        }
    }
}

@Composable
private fun MasterRow(title: String, sub: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { if (sub.isNotBlank()) Text(sub) }, modifier = Modifier.fillMaxWidth().clickable { onClick() })
    HorizontalDivider()
}

@Composable
private fun NameOnlyDialog(label: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.isBlank()) "New $label" else "Edit $label") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("$label name") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()) }) { Text("Save") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDialog(initial: AccountGroup, groups: List<AccountGroup>, onSave: (AccountGroup) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var nature by remember { mutableStateOf(initial.nature) }
    var parentId by remember { mutableStateOf(initial.parentGroupId) }
    var natureMenu by remember { mutableStateOf(false) }
    var parentMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New group" else "Edit group") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") }, singleLine = true, enabled = !initial.isSystem, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = nature.label, onValueChange = {}, readOnly = true, enabled = !initial.isSystem, label = { Text("Nature") },
                        trailingIcon = { IconButton(onClick = { natureMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = natureMenu, onDismissRequest = { natureMenu = false }) {
                        AccountNature.entries.forEach { n -> DropdownMenuItem(text = { Text(n.label) }, onClick = { nature = n; natureMenu = false }) }
                    }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = groups.firstOrNull { it.id == parentId }?.name ?: "None", onValueChange = {}, readOnly = true, label = { Text("Parent group (optional)") },
                        trailingIcon = { IconButton(onClick = { parentMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = parentMenu, onDismissRequest = { parentMenu = false }) {
                        DropdownMenuItem(text = { Text("None") }, onClick = { parentId = null; parentMenu = false })
                        groups.filter { it.id != initial.id }.forEach { g -> DropdownMenuItem(text = { Text(g.name) }, onClick = { parentId = g.id; parentMenu = false }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(initial.copy(name = name.trim(), nature = nature, parentGroupId = parentId)) }) { Text("Save") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadDialog(initial: AccountHead, groups: List<AccountGroup>, onSave: (AccountHead) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initial.name) }
    var groupId by remember { mutableStateOf(initial.groupId) }
    var opening by remember { mutableStateOf(if (initial.openingBalance > 0) initial.openingBalance.toString() else "") }
    var isDebit by remember { mutableStateOf(initial.openingIsDebit) }
    var groupMenu by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New account head" else "Edit account head") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Head name") }, singleLine = true, enabled = !initial.isSystem, modifier = Modifier.fillMaxWidth())
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = groups.firstOrNull { it.id == groupId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Group") },
                        trailingIcon = { IconButton(onClick = { groupMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        groups.forEach { g -> DropdownMenuItem(text = { Text(g.name) }, onClick = { groupId = g.id; groupMenu = false }) }
                    }
                }
                OutlinedTextField(value = opening, onValueChange = { opening = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Opening balance") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Opening balance is Debit", Modifier.weight(1f))
                    Switch(checked = isDebit, onCheckedChange = { isDebit = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && groupId != 0L) onSave(initial.copy(name = name.trim(), groupId = groupId, openingBalance = opening.toDoubleOrNull() ?: 0.0, openingIsDebit = isDebit)) }) { Text("Save") } },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } }
    )
}
