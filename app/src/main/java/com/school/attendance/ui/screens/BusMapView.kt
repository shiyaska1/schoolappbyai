package com.school.attendance.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

/** [color] lets "all buses" mode give each bus its own color; [driverName]/[driverPhone], when set,
 * are shown under the marker with the phone number as a tappable tel: link. */
data class MapMarker(
    val busNumber: String,
    val lat: Double,
    val lng: Double,
    val stale: Boolean,
    val color: String = "#e53935",
    val driverName: String = "",
    val driverPhone: String = ""
)

private class CallLinkWebViewClient(private val context: android.content.Context) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.scheme == "tel") {
            runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, uri)) }
            return true
        }
        return false
    }
}

/** A dependency-free map: plain OpenStreetMap tile images stitched into a static collage with
 * absolutely-positioned CSS markers on top, plus a little inline (non-CDN) JavaScript just to
 * toggle a marker's info popup — no Leaflet, no external <script>/<link> tags. Earlier versions
 * loaded Leaflet.js from a CDN, which some WebView builds silently refused to execute (blank white
 * screen, no error); plain <img> tags and inline script don't have that problem. */
@Composable
fun BusMapView(markers: List<MapMarker>, modifier: Modifier = Modifier, canvasW: Int = 640, canvasH: Int = 420) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = CallLinkWebViewClient(context)
            }
        },
        update = { webView -> webView.loadDataWithBaseURL("https://localhost/", buildStaticMapHtml(markers, emptyList(), canvasW, canvasH), "text/html", "utf-8", null) }
    )
}

/** Same idea, but for one bus's route: a trail of smaller dots connecting to one highlighted
 * "current position" marker (the newest point), sized to fit the whole route on screen. */
@Composable
fun BusRouteMapView(marker: MapMarker, priorPoints: List<Pair<Double, Double>>, modifier: Modifier = Modifier, canvasW: Int = 640, canvasH: Int = 420) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = CallLinkWebViewClient(context)
            }
        },
        update = { webView ->
            val trail = priorPoints.map { MapMarker(marker.busNumber, it.first, it.second, stale = false, color = marker.color) }
            webView.loadDataWithBaseURL("https://localhost/", buildStaticMapHtml(listOf(marker), trail, canvasW, canvasH), "text/html", "utf-8", null)
        }
    )
}

private const val TILE = 256

private fun lonToGlobalPixelX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * TILE * 2.0.pow(zoom)
private fun latToGlobalPixelY(lat: Double, zoom: Int): Double {
    val latRad = lat * PI / 180.0
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * TILE * 2.0.pow(zoom)
}

/** Highest zoom (most detail) at which every point still fits within the canvas, so a route
 * spanning a whole town doesn't get clipped, while a single point still zooms in close. */
private fun pickZoom(points: List<MapMarker>, canvasW: Int, canvasH: Int): Int {
    if (points.size <= 1) return 16
    val lats = points.map { it.lat }; val lngs = points.map { it.lng }
    val minLat = lats.min(); val maxLat = lats.max(); val minLng = lngs.min(); val maxLng = lngs.max()
    for (zoom in 18 downTo 3) {
        val spanX = lonToGlobalPixelX(maxLng, zoom) - lonToGlobalPixelX(minLng, zoom)
        val spanY = latToGlobalPixelY(minLat, zoom) - latToGlobalPixelY(maxLat, zoom)
        if (spanX <= canvasW - 60 && spanY <= canvasH - 60) return zoom
    }
    return 3
}

private fun esc(s: String): String = s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "&quot;")

private fun buildStaticMapHtml(highlighted: List<MapMarker>, trail: List<MapMarker>, canvasW: Int, canvasH: Int): String {
    val all = trail + highlighted
    if (all.isEmpty()) {
        return "<html><body style='margin:0;background:#e0e0e0;display:flex;align-items:center;justify-content:center;font-family:sans-serif;color:#666;'>No location yet</body></html>"
    }
    val zoom = pickZoom(all, canvasW, canvasH)
    val centerLat = (all.minOf { it.lat } + all.maxOf { it.lat }) / 2.0
    val centerLng = (all.minOf { it.lng } + all.maxOf { it.lng }) / 2.0
    val centerPx = lonToGlobalPixelX(centerLng, zoom)
    val centerPy = latToGlobalPixelY(centerLat, zoom)
    val topLeftPx = centerPx - canvasW / 2.0
    val topLeftPy = centerPy - canvasH / 2.0

    val firstTileX = Math.floor(topLeftPx / TILE).toInt()
    val firstTileY = Math.floor(topLeftPy / TILE).toInt()
    val tilesX = Math.ceil(canvasW.toDouble() / TILE).toInt() + 1
    val tilesY = Math.ceil(canvasH.toDouble() / TILE).toInt() + 1
    val maxTile = (1 shl zoom) - 1

    val tileImgs = StringBuilder()
    for (ty in 0..tilesY) {
        for (tx in 0..tilesX) {
            val gx = (firstTileX + tx).coerceIn(0, max(0, maxTile))
            val gy = (firstTileY + ty).coerceIn(0, max(0, maxTile))
            val screenX = (firstTileX + tx) * TILE - topLeftPx
            val screenY = (firstTileY + ty) * TILE - topLeftPy
            tileImgs.append(
                "<img src=\"https://tile.openstreetmap.org/$zoom/$gx/$gy.png\" style=\"position:absolute;left:${screenX}px;top:${screenY}px;width:${TILE}px;height:${TILE}px;\"/>\n"
            )
        }
    }

    fun screenPos(m: MapMarker): Pair<Double, Double> {
        val px = lonToGlobalPixelX(m.lng, zoom) - topLeftPx
        val py = latToGlobalPixelY(m.lat, zoom) - topLeftPy
        return px to py
    }

    val lines = StringBuilder()
    if (trail.size + highlighted.size > 1) {
        val ordered = trail + highlighted
        lines.append("<svg style=\"position:absolute;left:0;top:0;width:${canvasW}px;height:${canvasH}px;pointer-events:none;\">")
        for (i in 0 until ordered.size - 1) {
            val (x1, y1) = screenPos(ordered[i]); val (x2, y2) = screenPos(ordered[i + 1])
            val strokeColor = ordered[i + 1].color
            lines.append("<line x1=\"$x1\" y1=\"$y1\" x2=\"$x2\" y2=\"$y2\" stroke=\"$strokeColor\" stroke-width=\"3\" stroke-opacity=\"0.7\"/>")
        }
        lines.append("</svg>")
    }

    val trailDots = trail.joinToString("\n") { m ->
        val (x, y) = screenPos(m)
        "<div style=\"position:absolute;left:${x - 4}px;top:${y - 4}px;width:8px;height:8px;border-radius:50%;background:${m.color};opacity:0.6;\"></div>"
    }

    val popups = StringBuilder()
    val highlightDots = highlighted.mapIndexed { i, m ->
        val (x, y) = screenPos(m)
        val color = if (m.stale) "#9e9e9e" else m.color
        val busLabel = esc(m.busNumber) + if (m.stale) " (not started today)" else ""
        val popupId = "popup$i"
        val info = buildString {
            append("<div style=\"font-weight:bold;\">$busLabel</div>")
            if (m.driverName.isNotBlank()) append("<div>${esc(m.driverName)}</div>")
            if (m.driverPhone.isNotBlank()) append("<div><a href=\"tel:${esc(m.driverPhone)}\" style=\"color:#1976d2;text-decoration:none;\">&#128222; ${esc(m.driverPhone)}</a></div>")
        }
        popups.append(
            """<div id="$popupId" style="display:none;position:absolute;left:${(x - 90).coerceAtLeast(4.0)}px;top:${y + 16}px;min-width:140px;max-width:220px;background:white;border-radius:6px;padding:8px 10px;box-shadow:0 2px 8px rgba(0,0,0,0.35);font-family:sans-serif;font-size:12px;color:#222;z-index:10;">$info</div>"""
        )
        """
        <div onclick="var p=document.getElementById('$popupId');p.style.display=(p.style.display==='none'?'block':'none');"
             style="position:absolute;left:${x - 12}px;top:${y - 12}px;width:24px;height:24px;border-radius:50%;background:$color;border:3px solid white;box-shadow:0 0 6px rgba(0,0,0,0.5);cursor:pointer;"></div>
        <div style="position:absolute;left:${x - 60}px;top:${y + 14}px;width:120px;text-align:center;font-family:sans-serif;font-size:12px;font-weight:bold;color:$color;text-shadow:0 0 3px white,0 0 3px white,0 0 3px white;pointer-events:none;">$busLabel</div>
        """.trimIndent()
    }.joinToString("\n")

    return """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
        <style>html,body{margin:0;padding:0;background:#ddd;overflow:hidden;}</style>
        </head><body>
        <div style="position:relative;width:${canvasW}px;height:${canvasH}px;overflow:hidden;">
        $tileImgs
        $lines
        $trailDots
        $highlightDots
        $popups
        </div>
        </body></html>
    """.trimIndent()
}
