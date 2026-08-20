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
import com.school.attendance.data.AccountingRepository
import com.school.attendance.data.Receipt
import com.school.attendance.data.Repository
import com.school.attendance.data.Student
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private val PAYMENT_MODES = listOf("Cash", "UPI", "Card", "Cheque", "Bank")

class ReceiptsViewModel(app: Application) : AndroidViewModel(app) {
    private val acc = AccountingRepository(app)
    private val school = Repository(app)
    val receipts = acc.receipts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val heads = acc.heads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val students = school.students.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(r: Receipt) = viewModelScope.launch { acc.saveReceipt(r) }
    fun delete(r: Receipt) = viewModelScope.launch { acc.deleteReceipt(r) }
    suspend fun nextReceiptNo(): String = "RCT" + (acc.allReceipts().size + 1).toString().padStart(4, '0')
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(onBack: () -> Unit, vm: ReceiptsViewModel = viewModel()) {
    val receipts by vm.receipts.collectAsState()
    val heads by vm.heads.collectAsState()
    val students by vm.students.collectAsState()
    var edit by remember { mutableStateOf<Receipt?>(null) }
    var creatingNew by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (edit != null || creatingNew) {
        ReceiptDialog(
            initial = edit ?: Receipt(receiptNo = "", dateMillis = System.currentTimeMillis(), amount = 0.0),
            students = students, heads = heads,
            onSave = { vm.save(it); edit = null; creatingNew = false },
            onDismiss = { edit = null; creatingNew = false },
            onDelete = edit?.let { r -> { vm.delete(r); edit = null } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Receipts") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { creatingNew = true }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(receipts, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text("${r.receiptNo} — ₹%.2f".format(r.amount)) },
                    supportingContent = { Text(listOfNotNull(r.studentName.ifBlank { r.payFrom.ifBlank { null } }, r.paymentMode, SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(r.dateMillis)).joinToString("  ·  ")) },
                    modifier = Modifier.fillMaxWidth().clickable { edit = r }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptDialog(initial: Receipt, students: List<Student>, heads: List<com.school.attendance.data.AccountHead>, onSave: (Receipt) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(initial.dateMillis) }
    var studentId by remember { mutableStateOf(initial.studentId) }
    var payFrom by remember { mutableStateOf(initial.payFrom) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var mode by remember { mutableStateOf(initial.paymentMode.ifBlank { "Cash" }) }
    var toAccountId by remember { mutableStateOf(initial.toAccountId) }
    var narration by remember { mutableStateOf(initial.narration) }
    var studentMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New receipt" else "Edit receipt") },
        text = {
            Column {
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth()) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = students.firstOrNull { it.id == studentId }?.name ?: "None (other receipt)", onValueChange = {}, readOnly = true, label = { Text("Student (optional)") },
                        trailingIcon = { IconButton(onClick = { studentMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = studentMenu, onDismissRequest = { studentMenu = false }) {
                        DropdownMenuItem(text = { Text("None (other receipt)") }, onClick = { studentId = 0L; studentMenu = false })
                        students.forEach { s -> DropdownMenuItem(text = { Text(s.name) }, onClick = { studentId = s.id; studentMenu = false }) }
                    }
                }
                if (studentId == 0L) {
                    OutlinedTextField(value = payFrom, onValueChange = { payFrom = it }, label = { Text("Received from") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PAYMENT_MODES.forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
                }
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = heads.firstOrNull { it.id == toAccountId }?.name ?: "Auto ($mode)", onValueChange = {}, readOnly = true, label = { Text("Received into") },
                        trailingIcon = { IconButton(onClick = { accountMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        DropdownMenuItem(text = { Text("Auto ($mode)") }, onClick = { toAccountId = 0L; accountMenu = false })
                        heads.forEach { h -> DropdownMenuItem(text = { Text(h.name) }, onClick = { toAccountId = h.id; accountMenu = false } ) }
                    }
                }
                OutlinedTextField(value = narration, onValueChange = { narration = it }, label = { Text("Narration (optional)") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.toDoubleOrNull() ?: return@TextButton
                if (amt <= 0) return@TextButton
                val no = initial.receiptNo.ifBlank { "RCT" + System.currentTimeMillis().toString().takeLast(6) }
                onSave(initial.copy(
                    receiptNo = no, dateMillis = date, studentId = studentId,
                    studentName = students.firstOrNull { it.id == studentId }?.name ?: "",
                    payFrom = payFrom.trim(), amount = amt, paymentMode = mode, toAccountId = toAccountId, narration = narration.trim()
                ))
            }) { Text("Save") }
        },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } }
    )
}
