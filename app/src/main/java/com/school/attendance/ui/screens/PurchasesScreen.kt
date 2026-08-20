package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AccountHead
import com.school.attendance.data.AccountingRepository
import com.school.attendance.data.Purchase
import com.school.attendance.data.Supplier
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val PAYMENT_METHODS = listOf("Credit", "Cash", "UPI", "Card", "Cheque", "Bank")

class PurchasesViewModel(app: Application) : AndroidViewModel(app) {
    private val acc = AccountingRepository(app)
    val purchases = acc.purchases.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val suppliers = acc.suppliers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val heads = acc.heads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(p: Purchase) = viewModelScope.launch { acc.savePurchase(p) }
    fun delete(p: Purchase) = viewModelScope.launch { acc.deletePurchase(p) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(onBack: () -> Unit, vm: PurchasesViewModel = viewModel()) {
    val purchases by vm.purchases.collectAsState()
    val suppliers by vm.suppliers.collectAsState()
    val heads by vm.heads.collectAsState()
    var edit by remember { mutableStateOf<Purchase?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    if (edit != null || creatingNew) {
        PurchaseDialog(
            initial = edit ?: Purchase(purchaseNo = "", dateMillis = System.currentTimeMillis(), supplierId = 0, supplierName = "", description = "", amount = 0.0),
            suppliers = suppliers, heads = heads,
            onSave = { vm.save(it); edit = null; creatingNew = false },
            onDismiss = { edit = null; creatingNew = false },
            onDelete = edit?.let { p -> { vm.delete(p); edit = null } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Purchases") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { creatingNew = true }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(purchases, key = { it.id }) { p ->
                ListItem(
                    headlineContent = { Text("${p.purchaseNo} — ₹%.2f".format(p.amount)) },
                    supportingContent = { Text(listOfNotNull(p.supplierName.ifBlank { null }, p.paymentMethod, SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(p.dateMillis)).joinToString("  ·  ")) },
                    modifier = Modifier.fillMaxWidth().clickable { edit = p }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseDialog(initial: Purchase, suppliers: List<Supplier>, heads: List<AccountHead>, onSave: (Purchase) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(initial.dateMillis) }
    var supplierId by remember { mutableStateOf(initial.supplierId) }
    var description by remember { mutableStateOf(initial.description) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var method by remember { mutableStateOf(initial.paymentMethod.ifBlank { "Credit" }) }
    var fromAccountId by remember { mutableStateOf(initial.fromAccountId) }
    var supplierMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New purchase" else "Edit purchase") },
        text = {
            Column {
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth()) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = suppliers.firstOrNull { it.id == supplierId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Supplier") },
                        trailingIcon = { IconButton(onClick = { supplierMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = supplierMenu, onDismissRequest = { supplierMenu = false }) {
                        suppliers.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { supplierId = s.id; supplierMenu = false }) }
                    }
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description (e.g. Stationery)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_METHODS.forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(m) }) }
                }
                if (method != "Credit") {
                    Box(Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = heads.firstOrNull { it.id == fromAccountId }?.name ?: "Auto ($method)", onValueChange = {}, readOnly = true, label = { Text("Paid from") },
                            trailingIcon = { IconButton(onClick = { accountMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                            DropdownMenuItem(text = { Text("Auto ($method)") }, onClick = { fromAccountId = 0L; accountMenu = false })
                            heads.forEach { h -> DropdownMenuItem(text = { Text(h.name) }, onClick = { fromAccountId = h.id; accountMenu = false } ) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull() ?: return@TextButton
                if (amt <= 0 || supplierId == 0L) return@TextButton
                val no = initial.purchaseNo.ifBlank { "PUR" + System.currentTimeMillis().toString().takeLast(6) }
                onSave(initial.copy(
                    purchaseNo = no, dateMillis = date, supplierId = supplierId,
                    supplierName = suppliers.firstOrNull { it.id == supplierId }?.name ?: "",
                    description = description.trim(), amount = amt, paymentMethod = method, fromAccountId = fromAccountId
                ))
            }) { Text("Save") }
        },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } }
    )
}
