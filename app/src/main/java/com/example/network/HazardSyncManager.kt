package com.example.network

import android.content.Context
import android.location.Geocoder
import android.util.Log
import com.example.model.ImpactEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Manages background asynchronous synchronization of detected hazards (potholes and speed breakers)
 * directly to Supabase or a custom HTTP Routing API.
 *
 * Formatted to match the Supabase `hazards` table schema:
 * - id (text)
 * - latitude (float8)
 * - longitude (float8)
 * - road_name (text)
 * - type (text)
 * - severity (text)
 * - peak_force (float8)
 * - confidence (float8)
 * - consensus_count (int4)
 * - status (text)
 * - priority (text)
 * - reported_at (timestamptz)
 * - updated_at (timestamptz)
 */
object HazardSyncManager {

    private const val TAG = "HazardSyncManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Asynchronously uploads an impact event to the database.
     */
    fun uploadHazard(event: ImpactEvent, context: Context? = null) {
        if (!SupabaseConfig.isConfigured) {
            Log.e(TAG, "Supabase is not configured! Check SUPABASE_URL and SUPABASE_ANON_KEY in SupabaseConfig.kt.")
            return
        }

        // Do not upload normal road vibration
        if (event.classificationLabel.equals("Normal", ignoreCase = true)) {
            Log.d(TAG, "Suppressed normal road reading from upload.")
            return
        }

        scope.launch {
            try {
                val formattedType = if (event.classificationLabel.contains("Speed", ignoreCase = true)) {
                    "SPEED_BREAKER"
                } else {
                    "POTHOLE"
                }

                val severity = when {
                    event.peakMagnitude >= 22.0f -> "CRITICAL"
                    event.peakMagnitude >= 16.0f -> "HIGH"
                    event.peakMagnitude >= 12.0f -> "MEDIUM"
                    else -> "LOW"
                }

                val priority = when (severity) {
                    "CRITICAL" -> "P1"
                    "HIGH" -> "P2"
                    else -> "P3"
                }

                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(event.timestamp))

                // Reverse geocode to get the actual street/road name from real GPS coordinates
                var roadName = String.format(Locale.US, "RoadPulse GPS (%.4f, %.4f)", event.latitude, event.longitude)
                if (context != null && (event.latitude != 0.0 || event.longitude != 0.0)) {
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses = geocoder.getFromLocation(event.latitude, event.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            val street = addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: ""
                            val city = addr.locality ?: addr.adminArea ?: ""
                            if (street.isNotBlank() && city.isNotBlank()) {
                                roadName = "$street, $city"
                            } else if (street.isNotBlank()) {
                                roadName = street
                            } else if (!addr.featureName.isNullOrBlank()) {
                                roadName = "${addr.featureName}, ${addr.adminArea ?: ""}".trimEnd(',', ' ')
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Geocoder reverse-lookup skipped: ${e.message}")
                    }
                }

                val jsonBody = JSONObject().apply {
                    put("id", event.id)
                    put("latitude", event.latitude)
                    put("longitude", event.longitude)
                    put("road_name", roadName)
                    put("type", formattedType)
                    put("severity", severity)
                    put("peak_force", event.peakMagnitude.toDouble())
                    put("confidence", event.classificationConfidence.toDouble())
                    put("consensus_count", 1)
                    put("status", "UNASSIGNED")
                    put("priority", priority)
                    put("reported_at", isoDate)
                    put("updated_at", isoDate)
                }

                val request = if (SupabaseConfig.CUSTOM_API_URL.isNotBlank()) {
                    // Send to custom HTTP routing API
                    Request.Builder()
                        .url(SupabaseConfig.CUSTOM_API_URL)
                        .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .header("Content-Type", "application/json")
                        .build()
                } else {
                    // Send directly to Supabase REST endpoint
                    val endpoint = "${SupabaseConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/hazards"
                    Request.Builder()
                        .url(endpoint)
                        .post(jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .header("Content-Type", "application/json")
                        .header("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
                        .header("Prefer", "return=minimal")
                        .build()
                }

                Log.i(TAG, "Sending hazard ${event.id} ($formattedType) to Supabase: $jsonBody")

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "SUCCESS! Hazard ${event.id} ($formattedType) successfully uploaded to Supabase. HTTP ${response.code}")
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.e(TAG, "FAILED to upload hazard to Supabase: HTTP ${response.code} - $errorBody")
                    }
                }
            } catch (e: Exception) {
                // Fail-safe: log warning without crashing or interrupting the app
                Log.e(TAG, "Exception during hazard upload to Supabase: ${e.message}", e)
            }
        }
    }
}
