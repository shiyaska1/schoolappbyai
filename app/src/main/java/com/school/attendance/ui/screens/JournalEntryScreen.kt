package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
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
import com.school.attendance.data.JournalEntry
import com.school.attendance.data.JournalLine
import com.school.attendance.data.JournalVoucherType
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class JournalViewModel(app: Application) : AndroidViewModel(app) {
    private val acc = AccountingRepository(app)
    val entries = acc.journalEntries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val heads = acc.heads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun linesFor(entryId: Long) = acc.linesFor(entryId)
    fun save(entry: JournalEntry, lines: List<JournalLine>) = viewModelScope.launch { acc.saveJournal(entry, lines) }
    fun delete(entry: JournalEntry) = viewModelScope.launch { acc.deleteJournal(entry) }
}

private data class LineDraft(var headId: Long = 0L, var amount: String = "", var isDebit: Boolean = true)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryScreen(onBack: () -> Unit, vm: JournalViewModel = viewModel()) {
    val entries by vm.entries.collectAsState()
    val heads by vm.heads.collectAsState()
    var editEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var editLines by remember { mutableStateOf<List<JournalLine>>(emptyList()) }
    var creatingNew by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    if (creatingNew || editEntry != null) {
        JournalDialog(
            initial = editEntry ?: JournalEntry(voucherNo = "", dateMillis = System.currentTimeMillis()),
            initialLines = editLines, heads = heads,
            onSave = { e, l -> vm.save(e, l); editEntry = null; creatingNew = false; editLines = emptyList() },
            onDismiss = { editEntry = null; creatingNew = false; editLines = emptyList() },
            onDelete = editEntry?.let { e -> { vm.delete(e); editEntry = null; editLines = emptyList() } }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Journal / Contra") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) },
        floatingActionButton = { FloatingActionButton(onClick = { creatingNew = true }) { Icon(Icons.Filled.Add, "Add") } }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(entries, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(e.voucherNo) },
                    supportingContent = { Text(listOfNotNull(e.voucherType, e.narration.ifBlank { null }, SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(e.dateMillis)).joinToString("  ·  ")) },
                    modifier = Modifier.fillMaxWidth().clickable { scope.launch { editLines = vm.linesFor(e.id); editEntry = e } }
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalDialog(initial: JournalEntry, initialLines: List<JournalLine>, heads: List<AccountHead>, onSave: (JournalEntry, List<JournalLine>) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(initial.dateMillis) }
    var narration by remember { mutableStateOf(initial.narration) }
    var voucherType by remember { mutableStateOf(initial.voucherType) }
    val lines = remember {
        mutableStateListOf<LineDraft>().apply {
            if (initialLines.isEmpty()) { add(LineDraft(isDebit = true)); add(LineDraft(isDebit = false)) }
            else addAll(initialLines.map { LineDraft(it.headId, it.amount.toString(), it.isDebit) })
        }
    }
    var error by remember { mutableStateOf<String?>(null) }

    val totalDebit = lines.filter { it.isDebit }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val totalCredit = lines.filter { !it.isDebit }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "New voucher" else "Edit voucher") },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = voucherType == JournalVoucherType.JOURNAL, onClick = { voucherType = JournalVoucherType.JOURNAL }, label = { Text("Journal") })
                    FilterChip(selected = voucherType == JournalVoucherType.CONTRA, onClick = { voucherType = JournalVoucherType.CONTRA }, label = { Text("Contra") })
                }
                OutlinedButton(onClick = { pickDate(context, date) { date = it } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)) }
                OutlinedTextField(value = narration, onValueChange = { narration = it }, label = { Text("Narration") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                lines.forEachIndexed { i, line ->
                    LineRow(line, heads, onChange = { lines[i] = it }, onRemove = { if (lines.size > 2) lines.removeAt(i) })
                }
                TextButton(onClick = { lines.add(LineDraft()) }) { Text("+ Add line") }
                Text("Debit ₹%.2f   Credit ₹%.2f".format(totalDebit, totalCredit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (totalDebit != totalCredit || totalDebit == 0.0) { error = "Debit and credit totals must match and be non-zero"; return@TextButton }
                if (lines.any { it.headId == 0L }) { error = "Pick an account for every line"; return@TextButton }
                val no = initial.voucherNo.ifBlank { (if (voucherType == JournalVoucherType.CONTRA) "CTR" else "JNL") + System.currentTimeMillis().toString().takeLast(6) }
                val jLines = lines.mapNotNull { l ->
                    val amt = l.amount.toDoubleOrNull() ?: return@mapNotNull null
                    val h = heads.firstOrNull { it.id == l.headId } ?: return@mapNotNull null
                    JournalLine(entryId = initial.id, headId = h.id, headName = h.name, amount = amt, isDebit = l.isDebit)
                }
                onSave(initial.copy(voucherNo = no, dateMillis = date, narration = narration.trim(), voucherType = voucherType), jLines)
            }) { Text("Save") }
        },
        dismissButton = { Row { if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LineRow(line: LineDraft, heads: List<AccountHead>, onChange: (LineDraft) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.weight(1.4f)) {
            OutlinedTextField(value = heads.firstOrNull { it.id == line.headId }?.name ?: "", onValueChange = {}, readOnly = true, label = { Text("Account") },
                trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                heads.forEach { h -> DropdownMenuItem(text = { Text(h.name) }, onClick = { onChange(line.copy(headId = h.id)); menu = false }) }
            }
        }
        OutlinedTextField(value = line.amount, onValueChange = { onChange(line.copy(amount = it.filter { c -> c.isDigit() || c == '.' })) }, label = { Text("Amt") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
        FilterChip(selected = line.isDebit, onClick = { onChange(line.copy(isDebit = !line.isDebit)) }, label = { Text(if (line.isDebit) "Dr" else "Cr") })
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, "Remove line") }
    }
}
