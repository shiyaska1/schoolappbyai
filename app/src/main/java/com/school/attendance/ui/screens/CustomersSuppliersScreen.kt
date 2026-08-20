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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AccountingRepository
import com.school.attendance.data.Customer
import com.school.attendance.data.Supplier
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CustomersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AccountingRepository(app)
    val customers = repo.customers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(c: Customer) = viewModelScope.launch { repo.saveCustomer(c) }
    fun delete(c: Customer) = viewModelScope.launch { repo.deleteCustomer(c) }
}

class SuppliersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AccountingRepository(app)
    val suppliers = repo.suppliers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(s: Supplier) = viewModelScope.launch { repo.saveSupplier(s) }
    fun delete(s: Supplier) = viewModelScope.launch { repo.deleteSupplier(s) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(onBack: () -> Unit, vm: CustomersViewModel = viewModel()) {
    val customers by vm.customers.collectAsState()
    var edit by remember { mutableStateOf<Customer?>(null) }
    edit?.let { c -> PartyDialog("customer", c.name, c.phone, c.address, { n, p, a -> vm.save(c.copy(name = n, phone = p, address = a)); edit = null }, { edit = null }, if (c.id != 0L) { { vm.delete(c); edit = null } } else null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Customers") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Customer(name = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(customers, key = { it.id }) { c ->
                ListItem(headlineContent = { Text(c.name) }, supportingContent = { if (c.phone.isNotBlank()) Text(c.phone) }, modifier = Modifier.fillMaxWidth().clickable { edit = c })
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(onBack: () -> Unit, vm: SuppliersViewModel = viewModel()) {
    val suppliers by vm.suppliers.collectAsState()
    var edit by remember { mutableStateOf<Supplier?>(null) }
    edit?.let { s -> PartyDialog("supplier", s.name, s.phone, s.address, { n, p, a -> vm.save(s.copy(name = n, phone = p, address = a)); edit = null }, { edit = null }, if (s.id != 0L) { { vm.delete(s); edit = null } } else null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Suppliers") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { edit = Supplier(name = "") }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(suppliers, key = { it.id }) { s ->
                ListItem(headlineContent = { Text(s.name) }, supportingContent = { if (s.phone.isNotBlank()) Text(s.phone) }, modifier = Modifier.fillMaxWidth().clickable { edit = s })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PartyDialog(kind: String, initialName: String, initialPhone: String, initialAddress: String, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddress) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "New $kind" else "Edit $kind") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() } }, label = { Text("Phone") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), phone.trim(), address.trim()) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
