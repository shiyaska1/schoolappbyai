package com.school.attendance.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
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
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Bus
import com.school.attendance.data.Repository
import com.school.attendance.sync.LocationFix
import com.school.attendance.sync.LocationSync
import com.school.attendance.util.distanceMeters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Assumed average speed for the ETA estimate when the fix itself has no speed reading — this is
 * a straight-line distance / assumed-speed estimate, not real routing (no routing API is wired
 * up), so it's shown as a rough figure, not a promise. */
private const val FALLBACK_SPEED_KMH = 20.0

private fun isToday(millis: Long): Boolean {
    if (millis <= 0L) return false
    val a = Calendar.getInstance(); val b = Calendar.getInstance().apply { timeInMillis = millis }
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

class LiveLocationViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val buses = repo.buses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var driverPhone: String? = null
        private set

    suspend fun loadDriverPhone(busId: Long) { driverPhone = repo.driverForBus(busId)?.phone }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveLocationScreen(onBack: () -> Unit, showHistory: Boolean, restrictToBusId: Long? = null, vm: LiveLocationViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    val buses by vm.buses.collectAsState()

    var selected by remember { mutableStateOf<Bus?>(null) }
    var menu by remember { mutableStateOf(false) }
    var fix by remember { mutableStateOf<LocationFix?>(null) }
    var route by remember { mutableStateOf<List<LocationFix>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var allBusesMode by remember { mutableStateOf(false) }
    var allFixes by remember { mutableStateOf<List<Pair<Bus, LocationFix?>>>(emptyList()) }
    var loadingAll by remember { mutableStateOf(false) }

    fun refreshSelected(bus: Bus) {
        scope.launch { fix = LocationSync.pullLatest(context, bus.busNumber); status = if (fix == null) "No location received yet" else null }
    }

    LaunchedEffect(restrictToBusId, buses) {
        if (restrictToBusId != null && selected == null) {
            buses.firstOrNull { it.id == restrictToBusId }?.let { b -> selected = b; vm.loadDriverPhone(b.id); refreshSelected(b) }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Bus Location") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(12.dp)) {
            if (restrictToBusId == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !allBusesMode, onClick = { allBusesMode = false }, label = { Text("One bus") })
                    if (showHistory) FilterChip(selected = allBusesMode, onClick = { allBusesMode = true }, label = { Text("All buses") })
                }
            }

            if (allBusesMode && restrictToBusId == null) {
                Button(
                    onClick = {
                        loadingAll = true
                        scope.launch {
                            allFixes = buses.map { b -> b to LocationSync.pullLatest(context, b.busNumber) }
                            loadingAll = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(if (loadingAll) "Loading..." else "Refresh all buses") }

                val markers = allFixes.mapNotNull { (b, f) -> f?.let { MapMarker(b.busNumber, it.lat, it.lng, stale = !isToday(it.updatedAtMillis)) } }
                if (markers.isNotEmpty()) {
                    BusMapView(markers, modifier = Modifier.fillMaxWidth().height(280.dp).padding(top = 12.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(allFixes) { (b, f) ->
                        val started = f != null && isToday(f.updatedAtMillis)
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(b.busNumber, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (f == null) "No location received yet"
                                    else if (!started) "Not started today (last seen " + SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(f.updatedAtMillis) + ")"
                                    else "Live — updated " + SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(f.updatedAtMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (started) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                return@Column
            }

            if (restrictToBusId == null) {
                Box(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = selected?.let { "${it.busNumber}${if (it.route.isNotBlank()) " — ${it.route}" else ""}" } ?: "", onValueChange = {}, readOnly = true, label = { Text("Bus") },
                        trailingIcon = { IconButton(onClick = { menu = true }) { Icon(Icons.Filled.ArrowDropDown, null) } }, modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        buses.forEach { b -> DropdownMenuItem(text = { Text(b.busNumber) }, onClick = { selected = b; fix = null; route = emptyList(); status = null; menu = false; scope.launch { vm.loadDriverPhone(b.id) }; refreshSelected(b) }) }
                    }
                }
            } else {
                Text("Bus: ${selected?.busNumber ?: "..."}", style = MaterialTheme.typography.titleMedium)
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { selected?.let { refreshSelected(it) } },
                    enabled = selected != null, modifier = Modifier.weight(1f)
                ) { Text("Refresh position") }
                if (showHistory) {
                    OutlinedButton(
                        onClick = {
                            val bus = selected ?: return@OutlinedButton
                            scope.launch { route = LocationSync.pullHistory(context, bus.busNumber); status = if (route.isEmpty()) "No route recorded yet" else null }
                        },
                        enabled = selected != null, modifier = Modifier.weight(1f)
                    ) { Text("Today's route") }
                }
            }

            status?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }

            val startedToday = fix != null && isToday(fix!!.updatedAtMillis)
            if (fix != null && !startedToday) {
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Bus has not started today", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text(
                            "Last seen " + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(fix!!.updatedAtMillis),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else fix?.let { f ->
                BusMapView(
                    listOf(MapMarker(selected?.busNumber ?: "", f.lat, f.lng, stale = false)),
                    modifier = Modifier.fillMaxWidth().height(240.dp).padding(top = 12.dp)
                )
                Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        val distKm = if (prefs.geoFenceSet) distanceMeters(f.lat, f.lng, prefs.schoolLatitude, prefs.schoolLongitude) / 1000.0 else null
                        val speedKmh = if (f.speedMps > 0) f.speedMps * 3.6 else FALLBACK_SPEED_KMH
                        Text("Last updated: " + SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(f.updatedAtMillis), style = MaterialTheme.typography.bodyMedium)
                        Text("Speed: %.0f km/h".format(speedKmh), style = MaterialTheme.typography.bodyMedium)
                        if (distKm != null) {
                            Text("Distance to school: %.1f km".format(distKm), style = MaterialTheme.typography.bodyMedium)
                            Text("Estimated time to reach: ~${((distKm / speedKmh) * 60).toInt().coerceAtLeast(1)} min (straight-line estimate, not live traffic)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${f.lat},${f.lng}?q=${f.lat},${f.lng}"))) }, modifier = Modifier.weight(1f)) { Text("Open in Maps") }
                            val phone = vm.driverPhone
                            if (!phone.isNullOrBlank()) {
                                Button(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.Call, null, modifier = Modifier.padding(end = 6.dp)); Text("Call driver")
                                }
                            }
                        }
                    }
                }
            }

            if (route.isNotEmpty()) {
                Text("Today's route (${route.size} points)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxSize()) {
                    items(route) { p ->
                        Text(
                            SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(p.updatedAtMillis) + "  ·  %.5f, %.5f".format(p.lat, p.lng),
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
