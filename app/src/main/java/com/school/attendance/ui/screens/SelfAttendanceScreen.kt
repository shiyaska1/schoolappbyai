package com.school.attendance.ui.screens

import android.Manifest
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import com.school.attendance.util.distanceMeters
import com.school.attendance.util.getCurrentLocation
import com.school.attendance.util.hasLocationPermission
import kotlinx.coroutines.launch

class SelfAttendanceViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SelfAttendanceScreen(onBack: () -> Unit, vm: SelfAttendanceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AppPrefs(context) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    fun markPresent() {
        working = true; message = null
        scope.launch {
            val loc = getCurrentLocation(context)
            working = false
            if (loc == null) {
                message = "Couldn't get your location — check GPS is on and try again."
                return@launch
            }
            val dist = distanceMeters(loc.latitude, loc.longitude, prefs.schoolLatitude, prefs.schoolLongitude)
            if (dist > prefs.geoFenceRadiusMeters) {
                message = "You're %.0f m from the school — out of range.".format(dist)
            } else {
                vm.repo.saveSelfAttendance(prefs.loggedInTeacherId, prefs.deviceId, loc.latitude, loc.longitude)
                success = true
                message = "Marked present (%.0f m from school).".format(dist)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true || results[Manifest.permission.ACCESS_COARSE_LOCATION] == true) markPresent()
        else message = "Location permission is needed to mark attendance here."
    }

    Scaffold(topBar = { TopAppBar(title = { Text("My Attendance") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)) }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!prefs.geoFenceSet) {
                    Text("The school hasn't set its location yet — ask an admin to set it in Settings before you can mark attendance here.", modifier = Modifier.padding(bottom = 16.dp))
                } else {
                    Text("Mark yourself present — you must be within ${prefs.geoFenceRadiusMeters} m of the school.", modifier = Modifier.padding(bottom = 16.dp))
                    if (working) {
                        CircularProgressIndicator()
                    } else {
                        Button(onClick = {
                            if (hasLocationPermission(context)) markPresent()
                            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }) { Text("Mark present now") }
                    }
                }
                message?.let {
                    Text(it, modifier = Modifier.padding(top = 16.dp), color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
