package com.school.attendance.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    val me by vm.me.collectAsState()
    var adminView by remember { mutableStateOf(true) }

    val isAdmin = me?.isAdmin == true
    val showAdminTiles = isAdmin && adminView

    val tiles = if (showAdminTiles) listOf(
        Tile("Courses / Divisions / Subjects", Icons.Filled.School, Routes.MASTERS),
        Tile("Teachers & Staff", Icons.Filled.Badge, Routes.TEACHERS),
        Tile("Students", Icons.Filled.Groups, Routes.STUDENTS),
        Tile("Mark Attendance", Icons.Filled.CheckCircle, Routes.ATTENDANCE),
        Tile("Staff Attendance", Icons.Filled.Badge, Routes.TEACHER_ATTENDANCE),
        Tile("Holidays", Icons.Filled.EventBusy, Routes.HOLIDAYS),
        Tile("Reports", Icons.Filled.Assessment, Routes.REPORTS),
        Tile("Payroll", Icons.Filled.Payments, Routes.PAYROLL),
        Tile("Settings", Icons.Filled.Settings, Routes.SETTINGS)
    ) else listOf(
        Tile("Mark Attendance", Icons.Filled.CheckCircle, Routes.ATTENDANCE),
        Tile("Reports", Icons.Filled.Assessment, Routes.REPORTS)
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Hi, ${me?.name ?: ""}") },
            actions = {
                if (isAdmin) {
                    IconButton(onClick = { adminView = !adminView }) { Icon(Icons.Filled.SwapHoriz, "Switch to " + if (adminView) "teacher view" else "admin view") }
                }
                IconButton(onClick = onLogout) { Icon(Icons.Filled.Logout, "Log out") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (isAdmin) {
                Text(
                    if (adminView) "Admin view" else "Teacher view",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
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
