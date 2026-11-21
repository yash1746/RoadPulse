package com.example.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.ImpactEvent
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HazardMapView(
    impacts: List<ImpactEvent>,
    modifier: Modifier = Modifier,
    initialLat: Double = 28.6139,
    initialLng: Double = 77.2090
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    val potholeCount = impacts.count { it.classificationLabel.equals("Pothole", ignoreCase = true) }
    val speedBreakerCount = impacts.count { it.classificationLabel.contains("Speed", ignoreCase = true) }

    // HTML template hosting Leaflet map with dark-mode tiles and custom animated hazard markers
    val leafletHtml = remember(initialLat, initialLng) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                * { box-sizing: border-box; }
                body, html { margin: 0; padding: 0; width: 100%; height: 100%; background: #0b1120; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                #map { width: 100%; height: 100%; background: #0b1120; }
                
                /* Leaflet controls styling */
                .leaflet-bar a {
                    background-color: #1e293b !important;
                    color: #94a3b8 !important;
                    border-bottom: 1px solid #334155 !important;
                }
                .leaflet-bar a:hover {
                    background-color: #334155 !important;
                    color: #f8fafc !important;
                }

                /* Custom Pothole Marker: Vibrant Red Circle with Pulse */
                .pothole-pin {
                    width: 28px;
                    height: 28px;
                    border-radius: 50%;
                    background: #ef4444;
                    border: 2.5px solid #ffffff;
                    box-shadow: 0 0 12px rgba(239, 68, 68, 0.9);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-size: 13px;
                    font-weight: 900;
                    animation: pulse-red 2s infinite;
                    cursor: pointer;
                }

                /* Custom Speed Breaker Marker: Amber Rounded Square with Pulse */
                .speedbreaker-pin {
                    width: 28px;
                    height: 28px;
                    border-radius: 8px;
                    background: #f59e0b;
                    border: 2.5px solid #ffffff;
                    box-shadow: 0 0 12px rgba(245, 158, 11, 0.9);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #0f172a;
                    font-size: 14px;
                    font-weight: 900;
                    animation: pulse-amber 2s infinite;
                    cursor: pointer;
                }

                @keyframes pulse-red {
                    0% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.8); }
                    70% { box-shadow: 0 0 0 14px rgba(239, 68, 68, 0); }
                    100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0); }
                }

                @keyframes pulse-amber {
                    0% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0.8); }
                    70% { box-shadow: 0 0 0 14px rgba(245, 158, 11, 0); }
                    100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
                }

                /* Popup design */
                .leaflet-popup-content-wrapper {
                    background: #1e293b !important;
                    color: #f8fafc !important;
                    border-radius: 12px !important;
                    border: 1px solid #334155 !important;
                    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.5) !important;
                    padding: 4px 6px !important;
                }
                .leaflet-popup-tip { background: #1e293b !important; }
                .popup-box { min-width: 175px; padding: 4px; }
                .popup-title { font-weight: 800; font-size: 13px; margin-bottom: 6px; letter-spacing: 0.5px; }
                .popup-pothole { color: #f87171; }
                .popup-speedbreaker { color: #fbbf24; }
                .popup-row { font-size: 11px; color: #94a3b8; margin: 3px 0; display: flex; justify-content: space-between; }
                .popup-val { color: #f1f5f9; font-weight: 600; }
                .popup-badge { font-size: 9px; padding: 2px 6px; border-radius: 4px; background: #334155; color: #cbd5e1; }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', { zoomControl: false }).setView([$initialLat, $initialLng], 14);
                
                // Sleek CartoDB Dark Matter tiles
                L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
                    attribution: '&copy; OpenStreetMap &copy; CARTO',
                    subdomains: 'abcd',
                    maxZoom: 19
                }).addTo(map);

                L.control.zoom({ position: 'bottomright' }).addTo(map);

                var markersGroup = L.featureGroup().addTo(map);

                function renderHazards(hazards) {
                    markersGroup.clearLayers();
                    if (!hazards || hazards.length === 0) return;

                    hazards.forEach(function(h) {
                        var isPothole = (h.type.toUpperCase().indexOf('POTHOLE') !== -1);
                        var cssClass = isPothole ? 'pothole-pin' : 'speedbreaker-pin';
                        var iconSymbol = isPothole ? '!' : '▲';

                        var customIcon = L.divIcon({
                            className: 'custom-hazard-pin-container',
                            html: '<div class="' + cssClass + '">' + iconSymbol + '</div>',
                            iconSize: [28, 28],
                            iconAnchor: [14, 14]
                        });

                        var marker = L.marker([h.lat, h.lng], { icon: customIcon });

                        var titleClass = isPothole ? 'popup-pothole' : 'popup-speedbreaker';
                        var titleText = isPothole ? '🚨 POTHOLE' : '⚠️ SPEED BREAKER';

                        var popupContent = '<div class="popup-box">' +
                            '<div class="popup-title ' + titleClass + '">' + titleText + '</div>' +
                            '<div class="popup-row"><span>Confidence:</span><span class="popup-val">' + (h.confidence * 100).toFixed(1) + '%</span></div>' +
                            '<div class="popup-row"><span>Peak Force:</span><span class="popup-val">' + h.peak.toFixed(2) + ' m/s²</span></div>' +
                            '<div class="popup-row"><span>Time:</span><span class="popup-val">' + h.time + '</span></div>' +
                            '<div class="popup-row"><span>Method:</span><span class="popup-badge">' + (h.isHeuristic ? 'Heuristic' : '1D-CNN') + '</span></div>' +
                            '</div>';

                        marker.bindPopup(popupContent);
                        markersGroup.addLayer(marker);
                    });

                    if (hazards.length === 1) {
                        map.setView([hazards[0].lat, hazards[0].lng], 16);
                    } else if (hazards.length > 1) {
                        map.fitBounds(markersGroup.getBounds().pad(0.25));
                    }
                }

                function fitAll() {
                    if (markersGroup.getLayers().length > 0) {
                        map.fitBounds(markersGroup.getBounds().pad(0.25));
                    }
                }

                function centerLatest(lat, lng) {
                    map.setView([lat, lng], 17);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Push updated impacts to Leaflet whenever impacts change or map becomes ready
    LaunchedEffect(impacts, isMapReady) {
        if (!isMapReady) return@LaunchedEffect
        val webView = webViewRef ?: return@LaunchedEffect

        val jsonArray = JSONArray().apply {
            impacts.forEach { impact ->
                put(JSONObject().apply {
                    put("id", impact.id)
                    put("type", impact.classificationLabel)
                    put("confidence", impact.classificationConfidence.toDouble())
                    put("peak", impact.peakMagnitude.toDouble())
                    put("isHeuristic", impact.isHeuristic)
                    put("lat", impact.latitude)
                    put("lng", impact.longitude)
                    put("time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(impact.timestamp)))
                })
            }
        }.toString()

        webView.evaluateJavascript("if (typeof renderHazards === 'function') { renderHazards($jsonArray); }", null)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Title and Marker Counts
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Map Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ROAD HAZARD MAP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "OpenStreetMap / Leaflet View",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Map control buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            webViewRef?.evaluateJavascript("if (typeof fitAll === 'function') { fitAll(); }", null)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropFree,
                            contentDescription = "Fit All Markers",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val latest = impacts.firstOrNull()
                            if (latest != null) {
                                webViewRef?.evaluateJavascript("if (typeof centerLatest === 'function') { centerLatest(${latest.latitude}, ${latest.longitude}); }", null)
                            } else {
                                webViewRef?.evaluateJavascript("map.setView([$initialLat, $initialLng], 15);", null)
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "Recenter",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hazard Legend & Counter Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pothole Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Potholes: $potholeCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF87171)
                    )
                }

                // Speed Breaker Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFF59E0B))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Speed Breakers: $speedBreakerCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Total Pin Count Badge
                Text(
                    text = "${impacts.size} Marked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Leaflet WebView Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                loadWithOverviewMode = true
                                useWideViewPort = true
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isMapReady = true
                                }
                            }
                            loadDataWithBaseURL("https://unpkg.com", leafletHtml, "text/html", "UTF-8", null)
                            webViewRef = this
                        }
                    },
                    update = {
                        // Handled via LaunchedEffect(impacts)
                    }
                )
            }
        }
    }
}
