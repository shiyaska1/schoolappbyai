package com.school.attendance.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** True once the user has granted either fine or coarse location — every location API here
 * (self-attendance, driver tracking) throws [SecurityException] at runtime if called without it,
 * regardless of what's declared in the manifest, so this must be checked before each call. */
fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** One-shot GPS fix via the plain platform [LocationManager] — no Play Services dependency needed
 * just for "am I near the school". Falls back to the last known fix if a fresh one doesn't arrive
 * within [timeoutMs]; returns null if location is off or no provider has ever had a fix. */
@SuppressLint("MissingPermission")
suspend fun getCurrentLocation(context: Context, timeoutMs: Long = 15_000L): Location? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
    if (providers.isEmpty()) return null

    return suspendCancellableCoroutine { cont ->
        var resolved = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (resolved) return
                resolved = true
                providers.forEach { runCatching { lm.removeUpdates(this) } }
                cont.resume(location)
            }
        }
        providers.forEach { lm.requestLocationUpdates(it, 0L, 0f, listener, Looper.getMainLooper()) }
        cont.invokeOnCancellation { providers.forEach { runCatching { lm.removeUpdates(listener) } } }

        android.os.Handler(Looper.getMainLooper()).postDelayed({
            if (!resolved) {
                resolved = true
                providers.forEach { runCatching { lm.removeUpdates(listener) } }
                val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
                if (cont.isActive) cont.resume(last)
            }
        }, timeoutMs)
    }
}

/** Distance in meters between two lat/lng points. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val result = FloatArray(1)
    Location.distanceBetween(lat1, lng1, lat2, lng2, result)
    return result[0]
}
