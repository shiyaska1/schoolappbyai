package com.school.attendance.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

data class MapMarker(val busNumber: String, val lat: Double, val lng: Double, val stale: Boolean)

/** A small self-contained map: WebView + Leaflet.js pulling OpenStreetMap tiles from CDN at
 * runtime — no Google Maps SDK, no API key, no billing setup required from the school. Needs the
 * device to have internet (already required for sync), which is the only real dependency. */
@Composable
fun BusMapView(markers: List<MapMarker>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            // A null/blank base URL leaves the page on an opaque "about:blank"-like origin where
            // some WebView versions silently refuse to fetch the CDN's <script>/<link> tags — no
            // error, just nothing rendering. A real https origin avoids that.
            webView.loadDataWithBaseURL("https://localhost/", buildHtml(markers), "text/html", "utf-8", null)
        }
    )
}

private fun buildHtml(markers: List<MapMarker>): String {
    val points = markers.joinToString(",\n") { m ->
        val color = if (m.stale) "#9e9e9e" else "#1976d2"
        val label = m.busNumber.replace("\"", "'") + if (m.stale) " (not started today)" else ""
        "{lat:${m.lat}, lng:${m.lng}, label:\"$label\", color:\"$color\"}"
    }
    val center = markers.firstOrNull()?.let { "[${it.lat}, ${it.lng}]" } ?: "[20.5937, 78.9629]"
    val zoom = if (markers.isEmpty()) 4 else 15
    return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <style>html,body,#map{height:100%;margin:0;padding:0;}</style>
        </head><body>
        <div id="map"></div>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <script>
          var map = L.map('map').setView($center, $zoom);
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'
          }).addTo(map);
          var points = [$points];
          var bounds = [];
          points.forEach(function(p) {
            var marker = L.circleMarker([p.lat, p.lng], {radius: 9, color: p.color, fillColor: p.color, fillOpacity: 0.9}).addTo(map);
            marker.bindPopup(p.label);
            bounds.push([p.lat, p.lng]);
          });
          if (bounds.length > 1) map.fitBounds(bounds, {padding: [30, 30]});
        </script>
        </body></html>
    """.trimIndent()
}
