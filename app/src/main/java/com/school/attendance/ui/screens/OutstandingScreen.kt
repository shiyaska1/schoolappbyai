package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AccountingRepository
import com.school.attendance.report.AccountingEngine
import com.school.attendance.report.Posting
import com.school.attendance.util.CsvExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class OutstandingViewModel(app: Application) : AndroidViewModel(app) {
    private val acc = AccountingRepository(app)
    val postings = MutableStateFlow<List<Posting>>(emptyList())

    init {
        viewModelScope.launch {
            postings.value = AccountingEngine.build(
                acc.allHeads(), acc.allGroups(), acc.allReceipts(), acc.allExpenses(), acc.allPurchases(),
                acc.allJournalEntries(), acc.allJournalLines(), acc.allCostCenters()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutstandingScreen(onBack: () -> Unit, vm: OutstandingViewModel = viewModel()) {
    val context = LocalContext.current
    val postings by vm.postings.collectAsState()
    var payable by remember { mutableStateOf(true) }
    var nameQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val groupName = if (payable) "Sundry Creditors" else "Sundry Debtors"
    val list = remember(postings, payable) { AccountingEngine.outstandingOf(postings, groupName, System.currentTimeMillis()) }
        .filter { nameQuery.isBlank() || it.head.contains(nameQuery, ignoreCase = true) }
    val grandTotal = list.sumOf { it.debit + it.credit }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Outstanding") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = payable, onClick = { payable = true }, label = { Text("Payable (Suppliers)") })
                FilterChip(selected = !payable, onClick = { payable = false }, label = { Text("Receivable") })
            }
            OutlinedTextField(
                value = nameQuery, onValueChange = { nameQuery = it },
                label = { Text(if (payable) "Search supplier" else "Search party") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (!payable) {
                Text(
                    "Receivables only appear here for a party explicitly booked as a debtor (e.g. a journal entry raising a fee due) — a normal fee Receipt is money already collected, so it doesn't create a receivable by itself.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (list.isEmpty()) {
                Text("Nothing outstanding.", color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 16.dp))
            } else {
                Button(
                    onClick = {
                        CsvExport.export(
                            context, "outstanding-${groupName}.csv", listOf("Party", "Amount"),
                            list.map { listOf(it.head, "%.2f".format(it.debit + it.credit)) }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Export CSV") }
                LazyColumn(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
                    items(list, key = { it.head }) { hb ->
                        val amount = hb.debit + hb.credit
                        val isOpen = hb.head in expanded
                        Row(
                            Modifier.fillMaxWidth().clickable { expanded = if (isOpen) expanded - hb.head else expanded + hb.head }.padding(vertical = 10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(hb.head.ifBlank { "(no name)" }, fontWeight = FontWeight.Bold)
                            }
                            Text("₹%.2f".format(amount), fontWeight = FontWeight.Bold)
                            Icon(if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null)
                        }
                        if (isOpen) {
                            val ledger = AccountingEngine.ledgerOf(postings, hb.head, Long.MIN_VALUE + 1, System.currentTimeMillis())
                            ledger.rows.sortedByDescending { it.date }.forEach { r ->
                                Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${r.particulars}  •  ${fmt.format(r.date)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    Text("₹%.2f".format(if (r.debit != 0.0) r.debit else -r.credit), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                HorizontalDivider(Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(if (payable) "TOTAL PAYABLE" else "TOTAL RECEIVABLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Text("₹%.2f".format(grandTotal), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("${list.size} ${if (payable) "supplier(s)" else "part(y/ies)"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
