package com.school.attendance.ui.screens

import android.app.Activity
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.attendance.Routes
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import com.school.attendance.data.Teacher
import com.school.attendance.sync.CloudSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val me = MutableStateFlow<Teacher?>(null)
    init {
        viewModelScope.launch {
            val id = AppPrefs(app).loggedInTeacherId
            me.value = if (id > 0) repo.teacherById(id) else null
        }
    }

    fun changePin(newPin: String, onDone: () -> Unit) {
        val t = me.value ?: return
        viewModelScope.launch { repo.upsertTeacher(t.copy(pin = newPin)); me.value = t.copy(pin = newPin); onDone() }
    }
}

private data class Tile(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String, val section: String = "Transactions")
private val SECTION_ORDER = listOf("Masters", "Transactions", "Reports", "Accounts")
private val ACCOUNTS_SUB_ORDER = listOf("Accounts/Masters", "Accounts/Transactions", "Accounts/Reports")

@Composable
private fun DashboardTile(tile: Tile, onClick: () -> Unit) {
    Card(modifier = Modifier.padding(6.dp).fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(tile.label, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change my PIN") },
        text = {
            Column {
                Text("Leave both blank to remove your PIN.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("New PIN") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = confirm, onValueChange = { confirm = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("Confirm") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(onClick = {
                when {
                    pin != confirm -> error = "PINs don't match"
                    pin.isNotBlank() && pin.length < 4 -> error = "PIN must be at least 4 digits"
                    else -> onSave(pin)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onOpen: (String) -> Unit, onLogout: () -> Unit, vm: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val me by vm.me.collectAsState()
    var adminView by remember { mutableStateOf(true) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val canSync = prefs.syncPushUrl.isNotBlank() || prefs.syncPullUrl.isNotBlank()
    var showExitConfirm by remember { mutableStateOf(false) }
    BackHandler { showExitConfirm = true }
    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("Close School App?") },
            confirmButton = { TextButton(onClick = { (context as? Activity)?.finish() }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { showExitConfirm = false }) { Text("Cancel") } }
        )
    }

    val isAdmin = me?.isAdmin == true
    val showAdminTiles = isAdmin && adminView

    val isDriver = me?.designation.equals("Driver", ignoreCase = true)
    val canSelfMark = me?.canSelfMarkAttendance == true
    val canViewBus = me?.canViewBusLocation == true || isAdmin
    var showChangePin by remember { mutableStateOf(false) }

    if (showChangePin) {
        ChangePinDialog(onDismiss = { showChangePin = false }, onSave = { newPin -> vm.changePin(newPin) { showChangePin = false } })
    }

    val tiles = if (showAdminTiles) listOfNotNull(
        Tile("Courses / Divisions / Subjects", Icons.Filled.School, Routes.MASTERS, "Masters"),
        Tile("Teachers & Staff", Icons.Filled.Badge, Routes.TEACHERS, "Masters"),
        Tile("Students", Icons.Filled.Groups, Routes.STUDENTS, "Masters"),
        Tile("Buses", Icons.Filled.DirectionsBus, Routes.BUSES, "Masters"),
        Tile("Holidays", Icons.Filled.EventBusy, Routes.HOLIDAYS, "Masters"),
        Tile("Login Credentials", Icons.Filled.Key, Routes.CREDENTIALS, "Masters"),
        Tile("Exams", Icons.Filled.Quiz, Routes.EXAMS, "Masters"),
        Tile("Grade Scale", Icons.Filled.Grade, Routes.GRADE_SCALE, "Masters"),
        Tile("Settings", Icons.Filled.Settings, Routes.SETTINGS, "Masters"),
        Tile("Mark Attendance", Icons.Filled.CheckCircle, Routes.ATTENDANCE, "Transactions"),
        Tile("Staff Attendance", Icons.Filled.Badge, Routes.TEACHER_ATTENDANCE, "Transactions"),
        Tile("Exam Mark Entry", Icons.Filled.EditNote, Routes.MARK_ENTRY, "Transactions"),
        Tile("Bus Location", Icons.Filled.LocationOn, Routes.LIVE_LOCATION, "Transactions"),
        Tile("Switch to Parent View", Icons.Filled.SwitchAccount, Routes.SWITCH_PARENT, "Transactions"),
        Tile("Messages", Icons.Filled.Message, Routes.MESSAGES, "Transactions"),
        Tile("Reports", Icons.Filled.Assessment, Routes.REPORTS, "Reports"),
        Tile("Report Cards", Icons.Filled.Description, Routes.REPORT_CARDS, "Reports"),
        Tile("Payroll", Icons.Filled.Payments, Routes.PAYROLL, "Reports"),
        Tile("Chart of Accounts", Icons.Filled.AccountTree, Routes.ACCOUNTS, "Accounts/Masters"),
        Tile("Customers", Icons.Filled.People, Routes.CUSTOMERS, "Accounts/Masters"),
        Tile("Suppliers", Icons.Filled.LocalShipping, Routes.SUPPLIERS, "Accounts/Masters"),
        Tile("Receipts", Icons.Filled.Receipt, Routes.RECEIPTS, "Accounts/Transactions"),
        Tile("Expenses", Icons.Filled.MoneyOff, Routes.EXPENSES, "Accounts/Transactions"),
        Tile("Purchases", Icons.Filled.ShoppingCart, Routes.PURCHASES, "Accounts/Transactions"),
        Tile("Journal / Contra", Icons.Filled.Book, Routes.JOURNAL, "Accounts/Transactions"),
        Tile("Financial Reports", Icons.Filled.BarChart, Routes.FINANCIAL_REPORTS, "Accounts/Reports")
    ) else listOfNotNull(
        Tile("Mark Attendance", Icons.Filled.CheckCircle, Routes.ATTENDANCE, "Transactions"),
        if (canSelfMark) Tile("My Attendance", Icons.Filled.MyLocation, Routes.SELF_ATTENDANCE, "Transactions") else null,
        if (canViewBus) Tile("Bus Location", Icons.Filled.LocationOn, Routes.LIVE_LOCATION, "Transactions") else null,
        Tile("Exam Mark Entry", Icons.Filled.EditNote, Routes.MARK_ENTRY, "Transactions"),
        Tile("Switch to Parent View", Icons.Filled.SwitchAccount, Routes.SWITCH_PARENT, "Transactions"),
        Tile("Messages", Icons.Filled.Message, Routes.MESSAGES, "Transactions"),
        Tile("Reports", Icons.Filled.Assessment, Routes.REPORTS, "Reports"),
        Tile("Report Cards", Icons.Filled.Description, Routes.REPORT_CARDS, "Reports")
    )
    val openSections = remember { mutableStateMapOf<String, Boolean>().apply { SECTION_ORDER.forEach { put(it, false) }; ACCOUNTS_SUB_ORDER.forEach { put(it, false) } } }
    var searchQuery by remember { mutableStateOf("") }
    val matchingTiles = if (searchQuery.isBlank()) null else tiles.filter { it.label.contains(searchQuery, ignoreCase = true) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Hi, ${me?.name ?: ""}") },
            actions = {
                if (canSync) {
                    IconButton(onClick = {
                        if (!syncing) {
                            syncing = true
                            scope.launch {
                                val ok = CloudSyncManager.runOnePullMergePush(context)
                                syncing = false
                                syncMessage = CloudSyncManager.status.value
                                if (ok) android.widget.Toast.makeText(context, CloudSyncManager.status.value, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        if (syncing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Sync, "Sync now")
                    }
                }
                if (isAdmin) {
                    IconButton(onClick = { adminView = !adminView }) { Icon(Icons.Filled.SwapHoriz, "Switch to " + if (adminView) "teacher view" else "admin view") }
                }
                IconButton(onClick = { showChangePin = true }) { Icon(Icons.Filled.Lock, "Change my PIN") }
                IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Log out") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            syncMessage?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (canSync) {
                val lastSync = prefs.lastCloudSyncAt
                val dayMs = 24 * 60 * 60 * 1000L
                val stale = lastSync == 0L || System.currentTimeMillis() - lastSync > dayMs
                Text(
                    if (lastSync == 0L) "Never synced — tap the sync icon above."
                    else "Last synced: " + java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(lastSync) + if (stale) " — please sync" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            if (isAdmin) {
                Text(
                    if (adminView) "Admin view" else "Teacher view",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (isDriver) {
                var tracking by remember { mutableStateOf(prefs.geoTrackingEnabled) }
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (tracking) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (tracking) "🔴 Tracking is ON — sharing your bus location" else "Share my bus location",
                            style = if (tracking) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            color = if (tracking) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (tracking) Text("Turn off below when your run is done.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Switch(checked = tracking, onCheckedChange = { on ->
                        tracking = on
                        prefs.geoTrackingEnabled = on
                        if (on) {
                            val pm = context.getSystemService(android.os.PowerManager::class.java)
                            if (pm?.isIgnoringBatteryOptimizations(context.packageName) == false) {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:${context.packageName}")))
                                }
                            }
                            com.school.attendance.service.LocationTrackingService.start(context)
                        } else {
                            com.school.attendance.service.LocationTrackingService.stop(context)
                        }
                    })
                }
            }
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true,
                placeholder = { Text("Search modules") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                if (matchingTiles != null) {
                    if (matchingTiles.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text("No modules match \"$searchQuery\"", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        items(matchingTiles) { tile -> DashboardTile(tile) { onOpen(tile.route) } }
                    }
                } else if (showAdminTiles) {
                    SECTION_ORDER.forEach { section ->
                        if (section == "Accounts") {
                            val accTiles = tiles.filter { it.section.startsWith("Accounts/") }
                            if (accTiles.isEmpty()) return@forEach
                            val open = openSections[section] == true
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { openSections[section] = !open }.padding(top = 12.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Accounts", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                            if (open) {
                                ACCOUNTS_SUB_ORDER.forEach { sub ->
                                    val subTiles = accTiles.filter { it.section == sub }
                                    if (subTiles.isEmpty()) return@forEach
                                    val subOpen = openSections[sub] == true
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(start = 16.dp).clickable { openSections[sub] = !subOpen }.padding(top = 8.dp, bottom = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(if (subOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.secondary)
                                            Text(sub.removePrefix("Accounts/"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                    if (subOpen) items(subTiles) { tile -> DashboardTile(tile) { onOpen(tile.route) } }
                                }
                            }
                            return@forEach
                        }
                        val sectionTiles = tiles.filter { it.section == section }
                        if (sectionTiles.isEmpty()) return@forEach
                        val open = openSections[section] == true
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier.fillMaxWidth().clickable { openSections[section] = !open }.padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                                Text(section, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        if (open) {
                            items(sectionTiles) { tile -> DashboardTile(tile) { onOpen(tile.route) } }
                        }
                    }
                } else {
                    items(tiles) { tile -> DashboardTile(tile) { onOpen(tile.route) } }
                }
            }
        }
    }
}
