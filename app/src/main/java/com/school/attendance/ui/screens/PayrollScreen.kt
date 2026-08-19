package com.school.attendance.ui.screens

import android.app.Application
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.data.Payroll
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import com.school.attendance.util.CsvExport
import com.school.attendance.util.pickDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class PayrollRow(val teacher: Teacher, val payroll: Payroll)

class PayrollViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val teachers = repo.teachers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    suspend fun load(from: Long, to: Long): List<PayrollRow> = teachers.value.map { PayrollRow(it, repo.payrollFor(it, from, to)) }
}

private fun firstOfThisMonthMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayrollScreen(onBack: () -> Unit, vm: PayrollViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf(firstOfThisMonthMillis()) }
    var to by remember { mutableStateOf(System.currentTimeMillis()) }
    var rows by remember { mutableStateOf<List<PayrollRow>>(emptyList()) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Payroll") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            Text("Per-day rate = monthly salary ÷ working days in this period. Payable = rate × present days.", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(context, from) { from = it } }, modifier = Modifier.weight(1f)) { Text("From " + dateFmt.format(from)) }
                OutlinedButton(onClick = { pickDate(context, to) { to = it } }, modifier = Modifier.weight(1f)) { Text("To " + dateFmt.format(to)) }
            }
            Button(onClick = { scope.launch { rows = vm.load(from, to) } }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Calculate") }
            if (rows.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        CsvExport.export(
                            context, "payroll.csv",
                            listOf("Teacher", "Working Days", "Present", "Absent", "Per-day Rate", "Payable"),
                            rows.map { listOf(it.teacher.name, it.payroll.summary.workingDays.toString(), it.payroll.summary.present.toString(), it.payroll.summary.absent.toString(), "%.2f".format(it.payroll.perDayRate), "%.2f".format(it.payroll.payable)) }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("Export CSV") }
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(rows, key = { it.teacher.id }) { r ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(r.teacher.name, style = MaterialTheme.typography.titleSmall)
                                Text("Present ${r.payroll.summary.present}/${r.payroll.summary.workingDays} · Payable ₹%.2f".format(r.payroll.payable), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
