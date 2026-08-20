package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AccountingRepository
import com.school.attendance.report.AccountingEngine
import com.school.attendance.report.Posting
import com.school.attendance.util.CsvExport
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FinancialReportsViewModel(app: Application) : AndroidViewModel(app) {
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

private fun firstOfMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialReportsScreen(onBack: () -> Unit, vm: FinancialReportsViewModel = viewModel()) {
    val context = LocalContext.current
    val postings by vm.postings.collectAsState()
    var tab by remember { mutableStateOf("Day Book") }
    var from by remember { mutableStateOf(firstOfMonth()) }
    var to by remember { mutableStateOf(System.currentTimeMillis()) }
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Financial Reports") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Day Book", "Trial Balance", "Profit & Loss", "Balance Sheet").forEach { t -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(t) }) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(context, from) { from = it } }, modifier = Modifier.weight(1f)) { Text("From " + fmt.format(from)) }
                OutlinedButton(onClick = { pickDate(context, to) { to = it } }, modifier = Modifier.weight(1f)) { Text("To " + fmt.format(to)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            when (tab) {
                "Day Book" -> {
                    val rows = AccountingEngine.dayBookOf(postings, from, to)
                    Button(onClick = { CsvExport.export(context, "day-book.csv", listOf("Date", "Vch", "Particulars", "Head", "Debit", "Credit"), rows.map { listOf(fmt.format(it.date), it.vch, it.particulars, it.head, "%.2f".format(it.debit), "%.2f".format(it.credit)) }) }, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(rows) { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f)) { Text(p.particulars, style = MaterialTheme.typography.bodyMedium); Text("${p.head} · ${p.vch}", style = MaterialTheme.typography.labelSmall) }
                                Text(if (p.debit > 0) "Dr ₹%.2f".format(p.debit) else "Cr ₹%.2f".format(p.credit), style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                "Trial Balance" -> {
                    val rows = AccountingEngine.trialBalanceOf(postings, to)
                    Button(onClick = { CsvExport.export(context, "trial-balance.csv", listOf("Head", "Group", "Debit", "Credit"), rows.map { listOf(it.head, it.group, "%.2f".format(it.debit), "%.2f".format(it.credit)) }) }, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(rows) { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(Modifier.weight(1f)) { Text(r.head); Text(r.group, style = MaterialTheme.typography.labelSmall) }
                                Text(if (r.debit > 0) "Dr ₹%.2f".format(r.debit) else "Cr ₹%.2f".format(r.credit))
                            }
                            HorizontalDivider()
                        }
                        item {
                            val td = rows.sumOf { it.debit }; val tc = rows.sumOf { it.credit }
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall); Text("Dr ₹%.2f  Cr ₹%.2f".format(td, tc), style = MaterialTheme.typography.titleSmall) }
                        }
                    }
                }
                "Profit & Loss" -> {
                    val pnl = AccountingEngine.profitLossOf(postings, from, to)
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { Text("Income", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                        items(pnl.income) { l -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(l.head, Modifier.weight(1f)); Text("₹%.2f".format(l.amount)) } }
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Expense", style = MaterialTheme.typography.titleSmall) }
                        items(pnl.expense) { l -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(l.head, Modifier.weight(1f)); Text("₹%.2f".format(l.amount)) } }
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth()) { Text("Net Profit", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text("₹%.2f".format(pnl.netProfit), style = MaterialTheme.typography.titleMedium, color = if (pnl.netProfit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                else -> {
                    val bs = AccountingEngine.balanceSheetOf(postings, to)
                    LazyColumn(Modifier.fillMaxSize()) {
                        item { Text("Assets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp)) }
                        items(bs.assets) { l -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(l.head, Modifier.weight(1f)); Text("₹%.2f".format(l.amount)) } }
                        item { HorizontalDivider(Modifier.padding(vertical = 4.dp)); Text("Liabilities", style = MaterialTheme.typography.titleSmall) }
                        items(bs.liabilities) { l -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text(l.head, Modifier.weight(1f)); Text("₹%.2f".format(l.amount)) } }
                        item { Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Text("Net Profit (this year to date)", Modifier.weight(1f)); Text("₹%.2f".format(bs.netProfit)) } }
                        item {
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth()) { Text("Total Assets ₹%.2f".format(bs.totalAssets)); }
                            Row(Modifier.fillMaxWidth()) { Text("Total Liabilities + Profit ₹%.2f".format(bs.totalLiabilities)) }
                        }
                    }
                }
            }
        }
    }
}
