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
import com.school.attendance.data.Expense
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val PAYMENT_MODES = listOf("Cash", "UPI", "Card", "Cheque", "Bank")

class ExpensesViewModel(app: Application) : AndroidViewModel(app) {
    private val acc = AccountingRepository(app)
    val expenses = acc.expenses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val heads = acc.heads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun save(e: Expense) = viewModelScope.launch { acc.saveExpense(e) }
    fun delete(e: Expense) = viewModelScope.launch { acc.deleteExpense(e) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(onBack: () -> Unit, vm: ExpensesViewModel = viewModel()) {
    val expenses by vm.expenses.collectAsState()
    val heads by vm.heads.collectAsState()
    var edit by remember { mutableStateOf<Expense?>(null) }
    var creatingNew by remember { mutableStateOf(false) }

    if (edit != null || creatingNew) {
        ExpenseDialog(
            initial = edit ?: Expense(voucherNo = "", dateMillis = System.currentTimeMillis(), description = "", amount = 0.0),
            heads = heads,
            onSave = { vm.save(it); edit = null; creatingNew = false },
            onDismiss = { edit = null; creatingNew = false },
            onDelete = edit?.let { e -> { vm.delete(e); edit = null } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Expenses") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { creatingNew = true }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(expenses, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text("${e.voucherNo} — ₹%.2f".format(e.amount)) },
                    supportingContent = { Text(listOfNotNull(e.description.ifBlank { null }, e.paymentMode, SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(e.dateMillis)).joinToString("  ·  ")) },
                    modifier = Modifier.fillMaxWidth().clickable { edit = e }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDialog(initial: Expense, heads: List<AccountHead>, onSave: (Expense) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(initial.dateMillis) }
    var description by remember { mutableStateOf(initial.description) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var mode by remember { mutableStateOf(initial.paymentMode.ifBlank { "Cash" }) }
    var payTo by remember { mutableStateOf(initial.payTo) }
    var fromAccountId by remember { mutableStateOf(initial.fromAccountId) }
    var accountMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New expense" else "Edit expense") },
        text = {
            Column {
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth()) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = payTo, onValueChange = { payTo = it }, label = { Text("Paid to (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_MODES.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = heads.firstOrNull { it.id == fromAccountId }?.name ?: "Auto ($mode)", onValueChange = {}, readOnly = true, label = { Text("Paid from") },
                        trailingIcon = { IconButton(onClick = { accountMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        DropdownMenuItem(text = { Text("Auto ($mode)") }, onClick = { fromAccountId = 0L; accountMenu = false })
                        heads.forEach { h -> DropdownMenuItem(text = { Text(h.name) }, onClick = { fromAccountId = h.id; accountMenu = false } ) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull() ?: return@TextButton
                if (amt <= 0) return@TextButton
                val no = initial.voucherNo.ifBlank { "EXP" + System.currentTimeMillis().toString().takeLast(6) }
                onSave(initial.copy(voucherNo = no, dateMillis = date, description = description.trim(), amount = amt, paymentMode = mode, payTo = payTo.trim(), fromAccountId = fromAccountId))
            }) { Text("Save") }
        },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } }
    )
}
