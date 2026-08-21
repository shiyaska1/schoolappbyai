package com.school.attendance.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

private val BUS_COLORS = listOf("#e53935", "#1e88e5", "#43a047", "#fb8c00", "#8e24aa", "#00897b", "#c0ca33", "#6d4c41")
private fun busColor(index: Int): String = BUS_COLORS[index % BUS_COLORS.size]

private fun openInGoogleMaps(context: android.content.Context, lat: Double, lng: Double, label: String) {
    val gmmIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (gmmIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(gmmIntent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")))
    }
}

@Composable
private fun ZoomControls(zoom: Int, onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onZoomIn,
            modifier = Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
        ) { Icon(Icons.Filled.Add, "Zoom in", tint = androidx.compose.ui.graphics.Color.White) }
        IconButton(
            onClick = onZoomOut,
            modifier = Modifier.padding(top = 4.dp).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
        ) { Icon(Icons.Filled.Remove, "Zoom out", tint = androidx.compose.ui.graphics.Color.White) }
    }
}

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
    var driverName: String? = null
        private set

    suspend fun loadDriverPhone(busId: Long) {
        val t = repo.driverForBus(busId)
        driverPhone = t?.phone
        driverName = t?.name
    }

    suspend fun driverInfoFor(busId: Long): Pair<String, String> {
        val t = repo.driverForBus(busId)
        return (t?.name ?: "") to (t?.phone ?: "")
    }
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
    var driverInfoByBus by remember { mutableStateOf<Map<Long, Pair<String, String>>>(emptyMap()) }
    var showFullscreen by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(16) }
    var allBusesZoom by remember { mutableStateOf(13) }

    fun refreshSelected(bus: Bus) {
        scope.launch { fix = LocationSync.pullLatest(context, bus.busNumber); status = if (fix == null) "No location received yet" else null }
    }

    LaunchedEffect(restrictToBusId, buses) {
        if (restrictToBusId != null && selected == null) {
            buses.firstOrNull { it.id == restrictToBusId }?.let { b -> selected = b; vm.loadDriverPhone(b.id); refreshSelected(b) }
        }
    }
    LaunchedEffect(selected) { zoom = 16 }
    LaunchedEffect(route.isNotEmpty()) { if (route.isNotEmpty()) zoom = 13 }

    // Auto-refresh so a new point shows up on its own — no need to keep tapping "Refresh position".
    LaunchedEffect(selected, allBusesMode) {
        if (allBusesMode) return@LaunchedEffect
        val bus = selected ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(8_000)
            fix = LocationSync.pullLatest(context, bus.busNumber)
        }
    }
    LaunchedEffect(allBusesMode, buses) {
        if (!allBusesMode) return@LaunchedEffect
        while (true) {
            allFixes = buses.map { b -> b to LocationSync.pullLatest(context, b.busNumber) }
            driverInfoByBus = buses.associate { it.id to vm.driverInfoFor(it.id) }
            kotlinx.coroutines.delay(8_000)
        }
    }

    val selectedMarker = fix?.let { f ->
        val (dName, dPhone) = vm.driverName.orEmpty() to vm.driverPhone.orEmpty()
        MapMarker(selected?.busNumber ?: "", f.lat, f.lng, stale = false, color = busColor(0), driverName = dName, driverPhone = dPhone)
    }
    val allBusMarkers = allFixes.mapIndexedNotNull { i, (b, f) ->
        f?.let {
            val (dName, dPhone) = driverInfoByBus[b.id] ?: ("" to "")
            MapMarker(b.busNumber, it.lat, it.lng, stale = !isToday(it.updatedAtMillis), color = busColor(i), driverName = dName, driverPhone = dPhone)
        }
    }
    val routeMarker = selectedMarker
    val routeTrail = route.dropLast(1).map { it.lat to it.lng }

    if (showFullscreen) {
        val screenW = LocalConfiguration.current.screenWidthDp
        val screenH = LocalConfiguration.current.screenHeightDp
        Dialog(onDismissRequest = { showFullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                if (allBusesMode) {
                    BusMapView(allBusMarkers, modifier = Modifier.fillMaxSize(), canvasW = screenW, canvasH = screenH, zoomOverride = allBusesZoom)
                } else if (route.isNotEmpty() && routeMarker != null) {
                    BusRouteMapView(routeMarker, routeTrail, modifier = Modifier.fillMaxSize(), canvasW = screenW, canvasH = screenH, zoomOverride = zoom)
                } else if (selectedMarker != null) {
                    BusMapView(listOf(selectedMarker), modifier = Modifier.fillMaxSize(), canvasW = screenW, canvasH = screenH, zoomOverride = zoom)
                }
                IconButton(onClick = { showFullscreen = false }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Filled.Close, "Close", tint = androidx.compose.ui.graphics.Color.White)
                }
                ZoomControls(
                    zoom = if (allBusesMode) allBusesZoom else zoom,
                    onZoomIn = { if (allBusesMode) allBusesZoom = (allBusesZoom + 1).coerceAtMost(MAP_MAX_ZOOM) else zoom = (zoom + 1).coerceAtMost(MAP_MAX_ZOOM) },
                    onZoomOut = { if (allBusesMode) allBusesZoom = (allBusesZoom - 1).coerceAtLeast(MAP_MIN_ZOOM) else zoom = (zoom - 1).coerceAtLeast(MAP_MIN_ZOOM) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
            }
        }
        return
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
                            driverInfoByBus = buses.associate { it.id to vm.driverInfoFor(it.id) }
                            loadingAll = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(if (loadingAll) "Loading..." else "Refresh all buses") }

                if (allBusMarkers.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        BusMapView(allBusMarkers, modifier = Modifier.fillMaxWidth().height(380.dp), zoomOverride = allBusesZoom)
                        IconButton(
                            onClick = { showFullscreen = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                        ) { Icon(Icons.Filled.Fullscreen, "Fullscreen", tint = androidx.compose.ui.graphics.Color.White) }
                        ZoomControls(
                            zoom = allBusesZoom,
                            onZoomIn = { allBusesZoom = (allBusesZoom + 1).coerceAtMost(MAP_MAX_ZOOM) },
                            onZoomOut = { allBusesZoom = (allBusesZoom - 1).coerceAtLeast(MAP_MIN_ZOOM) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                LazyColumn(Modifier.weight(1f).navigationBarsPadding()) {
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
                                if (f != null) {
                                    OutlinedButton(
                                        onClick = { openInGoogleMaps(context, f.lat, f.lng, b.busNumber) },
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) { Icon(Icons.Filled.Map, null, modifier = Modifier.padding(end = 6.dp)); Text("Open in Google Maps") }
                                }
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
            } else if (selectedMarker != null) {
                val f = fix!!
                Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    if (route.isNotEmpty()) {
                        BusRouteMapView(selectedMarker, routeTrail, modifier = Modifier.fillMaxWidth().height(400.dp), zoomOverride = zoom)
                    } else {
                        BusMapView(listOf(selectedMarker), modifier = Modifier.fillMaxWidth().height(400.dp), zoomOverride = zoom)
                    }
                    IconButton(
                        onClick = { showFullscreen = true },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                    ) { Icon(Icons.Filled.Fullscreen, "Fullscreen", tint = androidx.compose.ui.graphics.Color.White) }
                    ZoomControls(
                        zoom = zoom,
                        onZoomIn = { zoom = (zoom + 1).coerceAtMost(MAP_MAX_ZOOM) },
                        onZoomOut = { zoom = (zoom - 1).coerceAtLeast(MAP_MIN_ZOOM) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    )
                }
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
                            OutlinedButton(onClick = { openInGoogleMaps(context, f.lat, f.lng, selected?.busNumber ?: "Bus") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Map, null, modifier = Modifier.padding(end = 6.dp)); Text("Open in Google Maps")
                            }
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
                LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
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
