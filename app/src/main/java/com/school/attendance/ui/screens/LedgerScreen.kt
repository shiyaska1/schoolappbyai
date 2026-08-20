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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.AccountGroup
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

private fun drCr(v: Double): String = if (v >= 0) "₹%.2f Dr".format(v) else "₹%.2f Cr".format(-v)

class LedgerViewModel(app: Application) : AndroidViewModel(app) {
    private val acc = AccountingRepository(app)
    val groups = MutableStateFlow<List<AccountGroup>>(emptyList())
    val postings = MutableStateFlow<List<Posting>>(emptyList())

    init {
        viewModelScope.launch {
            groups.value = acc.allGroups()
            postings.value = AccountingEngine.build(
                acc.allHeads(), acc.allGroups(), acc.allReceipts(), acc.allExpenses(), acc.allPurchases(),
                acc.allJournalEntries(), acc.allJournalLines(), acc.allCostCenters()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(onBack: () -> Unit, vm: LedgerViewModel = viewModel()) {
    val context = LocalContext.current
    val groups by vm.groups.collectAsState()
    val postings by vm.postings.collectAsState()
    val fmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var groupId by remember { mutableStateOf(0L) }
    var groupMenu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var accQuery by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis) }
    var to by remember { mutableStateOf(System.currentTimeMillis()) }
    var result by remember { mutableStateOf<AccountingEngine.LedgerResult?>(null) }

    val groupName = if (groupId == 0L) null else groups.firstOrNull { it.id == groupId }?.name
    val names = remember(postings, groupName) { AccountingEngine.accountNames(postings, groupName) }

    Scaffold(topBar = { TopAppBar(title = { Text("Ledger") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Box {
                OutlinedTextField(
                    value = groupName ?: "All groups", onValueChange = {}, readOnly = true, label = { Text("Account group (optional)") },
                    trailingIcon = { IconButton(onClick = { groupMenu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                    DropdownMenuItem(text = { Text("All groups") }, onClick = { groupId = 0; selected = null; result = null; groupMenu = false })
                    groups.forEach { g -> DropdownMenuItem(text = { Text(g.name) }, onClick = { groupId = g.id; selected = null; result = null; groupMenu = false }) }
                }
            }
            OutlinedTextField(
                value = accQuery, onValueChange = { accQuery = it; selected = null; result = null },
                label = { Text("Account (type to search)") },
                trailingIcon = { if (selected != null) IconButton(onClick = { selected = null; accQuery = "" }) { Icon(Icons.Filled.ArrowDropDown, "Clear") } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .onFocusChanged { fs -> if (fs.isFocused) { accQuery = ""; selected = null } else if (selected != null) accQuery = selected!! }
            )
            if (selected == null && accQuery.isNotBlank()) {
                names.filter { it.contains(accQuery, ignoreCase = true) }.take(6).forEach { nm ->
                    Text(nm, modifier = Modifier.fillMaxWidth().clickable { selected = nm; accQuery = nm }.padding(vertical = 10.dp, horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(context, from) { from = it } }, modifier = Modifier.weight(1f)) { Text("From: " + fmt.format(from)) }
                OutlinedButton(onClick = { pickDate(context, to) { to = it } }, modifier = Modifier.weight(1f)) { Text("To: " + fmt.format(to)) }
            }
            Button(
                onClick = { selected?.let { result = AccountingEngine.ledgerOf(postings, it, from, to) } },
                enabled = selected != null, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text("View ledger") }

            result?.let { res ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = {
                        CsvExport.export(
                            context, "ledger-${selected}.csv", listOf("Date", "Particulars", "Voucher", "Debit", "Credit", "Balance"),
                            listOf(listOf("", "Opening Balance", "", "", "", drCr(res.opening))) +
                                res.rows.map { r -> listOf(fmt.format(r.date), r.particulars, r.vch, if (r.debit != 0.0) "%.2f".format(r.debit) else "", if (r.credit != 0.0) "%.2f".format(r.credit) else "", drCr(r.balance)) } +
                                listOf(listOf("", "Closing Balance", "", "", "", drCr(res.closing)))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Export CSV") }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Opening balance", fontWeight = FontWeight.Bold)
                    Text(drCr(res.opening), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Date", Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                    Text("Particulars", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
                    Text("Debit", Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall)
                    Text("Credit", Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall)
                    Text("Balance", Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                LazyColumn(Modifier.weight(1f)) {
                    items(res.rows) { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(fmt.format(r.date), Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                            Column(Modifier.weight(2f)) {
                                Text(r.particulars, style = MaterialTheme.typography.bodySmall)
                                if (r.vch.isNotBlank()) Text(r.vch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Text(if (r.debit != 0.0) "%.2f".format(r.debit) else "", Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall)
                            Text(if (r.credit != 0.0) "%.2f".format(r.credit) else "", Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall)
                            Text(drCr(r.balance), Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Closing balance", fontWeight = FontWeight.Bold)
                    Text(drCr(res.closing), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
