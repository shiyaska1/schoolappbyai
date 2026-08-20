package com.school.attendance.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.school.attendance.MainActivity
import com.school.attendance.data.AppPrefs
import com.school.attendance.data.Repository
import com.school.attendance.sync.LocationSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Foreground service for a driver's "share my location" toggle. Every fix is saved to the local
 * queue ([Repository.recordLocationPing]) first, then a flush attempt pushes whatever's queued —
 * so a dead zone on the route just delays delivery instead of losing points. Only runs while
 * explicitly switched on from the Dashboard (start of a bus run to end of it), to save battery. */
class LocationTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        if (running) return
        running = true
        val prefs = AppPrefs(this)
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        val repo = Repository(applicationContext)
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                scope.launch {
                    val teacherId = prefs.loggedInTeacherId
                    if (teacherId <= 0) return@launch
                    repo.recordLocationPing(teacherId, location.latitude, location.longitude, location.speed)
                    val bus = repo.busNumberForDriver(teacherId) ?: return@launch
                    LocationSync.flushQueue(applicationContext, teacherId, bus)
                }
            }
        }
        listener = l
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { lm.isProviderEnabled(it) }
        providers.forEach { runCatching { lm.requestLocationUpdates(it, 60_000L, 0f, l, Looper.getMainLooper()) } }
    }

    private fun buildNotification(): Notification {
        val channelId = "location_tracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(NotificationChannel(channelId, "Location tracking", NotificationManager.IMPORTANCE_LOW))
            }
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Sharing your bus location")
            .setContentText("Turn off from the Dashboard when your run is done.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        listener?.let { runCatching { locationManager?.removeUpdates(it) } }
        running = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 4201

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
