package com.example.network

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
 * to Supabase or a custom HTTP Routing API.
 *
 * Designed to be completely non-blocking and fail-safe:
 * - Operates entirely on Dispatchers.IO.
 * - Never throws unhandled exceptions or interrupts UI/sensor tracking.
 * - Silently bypasses sync if credentials are not yet configured or device is offline.
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
    fun uploadHazard(event: ImpactEvent) {
        if (!SupabaseConfig.isConfigured) {
            Log.d(TAG, "Supabase/API not yet configured in SupabaseConfig.kt. Skipping cloud sync.")
            return
        }

        // Do not upload normal road telemetry
        if (event.classificationLabel.equals("Normal", ignoreCase = true)) {
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

                val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date(event.timestamp))

                val roadNameFallback = String.format(Locale.US, "RoadPulse GPS (%.4f, %.4f)", event.latitude, event.longitude)

                val jsonBody = JSONObject().apply {
                    put("id", event.id)
                    put("latitude", event.latitude)
                    put("longitude", event.longitude)
                    put("road_name", roadNameFallback)
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

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.i(TAG, "Hazard ${event.id} ($formattedType) successfully uploaded to database. HTTP ${response.code}")
                    } else {
                        val errorBody = response.body?.string() ?: ""
                        Log.w(TAG, "Cloud sync returned HTTP ${response.code}: $errorBody")
                    }
                }
            } catch (e: Exception) {
                // Fail-safe: log warning without crashing or interrupting the app
                Log.w(TAG, "Failed to upload hazard to database: ${e.message}")
            }
        }
    }
}
