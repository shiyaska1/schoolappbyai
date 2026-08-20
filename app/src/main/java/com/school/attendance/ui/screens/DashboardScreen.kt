package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
}

private data class Tile(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)

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

    val isAdmin = me?.isAdmin == true
    val showAdminTiles = isAdmin && adminView

    val isDriver = me?.designation.equals("Driver", ignoreCase = true)
    val canSelfMark = me?.canSelfMarkAttendance == true
    val canViewBus = me?.canViewBusLocation == true || isAdmin

    val tiles = if (showAdminTiles) listOfNotNull(
        Tile("Courses / Divisions / Subjects", Icons.Filled.School, Routes.MASTERS),
        Tile("Teachers & Staff", Icons.Filled.Badge, Routes.TEACHERS),
        Tile("Students", Icons.Filled.Groups, Routes.STUDENTS),
        Tile("Buses", Icons.Filled.DirectionsBus, Routes.BUSES),
        Tile("Mark Attendance", Icons.Filled.CheckCircle, Routes.ATTENDANCE),
        Tile("Staff Attendance", Icons.Filled.Badge, Routes.TEACHER_ATTENDANCE),
        Tile("Bus Location", Icons.Filled.LocationOn, Routes.LIVE_LOCATION),
        Tile("Holidays", Icons.Filled.EventBusy, Routes.HOLIDAYS),
        Tile("Reports", Icons.Filled.Assessment, Routes.REPORTS),
        Tile("Payroll", Icons.Filled.Payments, Routes.PAYROLL),
        Tile("Settings", Icons.Filled.Settings, Routes.SETTINGS)
    ) else listOfNotNull(
        Tile("Mark Attendance", Icons.Filled.CheckCircle, Routes.ATTENDANCE),
        Tile("Reports", Icons.Filled.Assessment, Routes.REPORTS),
        if (canSelfMark) Tile("My Attendance", Icons.Filled.MyLocation, Routes.SELF_ATTENDANCE) else null,
        if (canViewBus) Tile("Bus Location", Icons.Filled.LocationOn, Routes.LIVE_LOCATION) else null
    )

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
                IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Log out") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            syncMessage?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text("Share my bus location", Modifier.weight(1f))
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
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(tiles) { tile ->
                    Card(modifier = Modifier.padding(6.dp).fillMaxWidth(), onClick = { onOpen(tile.route) }) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary)
                            Text(tile.label, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
