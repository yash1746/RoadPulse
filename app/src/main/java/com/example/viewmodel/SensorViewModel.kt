package com.example.viewmodel

import android.os.SystemClock

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.media.AudioManager
import android.media.ToneGenerator
import com.example.model.SensorData
import com.example.model.SensorUiState
import com.example.model.ImpactEvent
import com.example.model.RoadHazardClassifier
import com.example.network.HazardSyncManager
import com.example.websocket.SensorWebSocketServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

class SensorViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _uiState = MutableStateFlow(SensorUiState())
    val uiState: StateFlow<SensorUiState> = _uiState.asStateFlow()

    private val accelCount = AtomicInteger(0)
    private val gyroCount = AtomicInteger(0)
    private var hzJob: Job? = null

    // Rolling buffers for pre-trigger logging (2 seconds at ~50Hz = ~100 samples)
    private val rollingAccel = java.util.ArrayDeque<SensorData>(110)
    private val rollingGyro = java.util.ArrayDeque<SensorData>(110)

    // Impact event recording state
    private var isRecordingPost = false
    private var spikeSensorTimestamp = 0L
    private var peakMagnitudeDuringEvent = 0f
    private val activeAccelRecord = mutableListOf<SensorData>()
    private val activeGyroRecord = mutableListOf<SensorData>()
    private var lastTriggerTimeMs = 0L

    private val COOLDOWN_MS = 4000L // 4 seconds cooldown to avoid multiple triggers on the same impact
    private val SPIKE_THRESHOLD_DIFF_GRAVITY = 9.0f // m/s2 deviation from standard gravity 9.81

    private val classifier = RoadHazardClassifier(application)
    private var webSocketServer: SensorWebSocketServer? = null

    init {
        _uiState.update { state ->
            state.copy(
                accelAvailable = accelerometer != null,
                gyroAvailable = gyroscope != null,
                isModelLoaded = classifier.isTfLiteLoaded
            )
        }
    }

    fun setSensorSource(source: String) {
        if (_uiState.value.sensorSource == source) return

        stopListening()
        _uiState.update { it.copy(sensorSource = source) }
        
        // Reset graph timers to prevent clock-mismatch freezing
        lastAccelGraphTime = 0L
        lastGyroGraphTime = 0L
        
        startListening()
    }

    private fun startWebSocketServer() {
        stopWebSocketServer()
        try {
            val server = SensorWebSocketServer(
                port = 8080,
                onSensorDataReceived = { ax, ay, az, gx, gy, gz ->
                    processIncomingSimulatedData(ax, ay, az, gx, gy, gz)
                },
                onStatusChanged = { status ->
                    _uiState.update { it.copy(webSocketStatus = status) }
                },
                onLocationReceived = { lat, lng ->
                    updateSimulatedLocation(lat, lng)
                }
            )
            server.isReuseAddr = true
            server.start()
            webSocketServer = server
            val localIp = SensorWebSocketServer.getLocalIpAddress()
            _uiState.update { 
                it.copy(
                    webSocketServerUrl = "ws://$localIp:8080",
                    webSocketStatus = "Listening for simulation..."
                ) 
            }
        } catch (e: Exception) {
            android.util.Log.e("SensorViewModel", "Error starting WebSocket server", e)
            _uiState.update { it.copy(webSocketStatus = "Error: ${e.message}") }
        }
    }

    private fun stopWebSocketServer() {
        webSocketServer?.let {
            try {
                it.stop(1000)
            } catch (e: Exception) {
                android.util.Log.e("SensorViewModel", "Error stopping WebSocket server", e)
            }
        }
        webSocketServer = null
        _uiState.update { it.copy(webSocketStatus = "Disconnected", webSocketServerUrl = "") }
    }

    fun startListening() {
        if (_uiState.value.isListening) return

        if (_uiState.value.sensorSource == "WebSocket Simulator") {
            startWebSocketServer()
            _uiState.update { it.copy(isListening = true) }
            startHzMonitoring()
            return
        }

        // Register accelerometer
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Register gyroscope
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        _uiState.update { it.copy(isListening = true) }
        startHzMonitoring()
    }

    private fun startHzMonitoring() {
        hzJob?.cancel()
        hzJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val rate = when {
                    _uiState.value.sensorSource == "WebSocket Simulator" -> {
                        accelCount.getAndSet(0)
                    }
                    accelerometer != null -> accelCount.getAndSet(0)
                    gyroscope != null -> gyroCount.getAndSet(0)
                    else -> 0
                }
                if (accelerometer != null || _uiState.value.sensorSource == "WebSocket Simulator") {
                    gyroCount.getAndSet(0)
                }
                _uiState.update { it.copy(updateRateHz = rate) }
            }
        }
    }

    fun stopListening() {
        if (!_uiState.value.isListening) return

        if (_uiState.value.sensorSource == "WebSocket Simulator") {
            stopWebSocketServer()
        } else {
            sensorManager.unregisterListener(this)
        }
        
        hzJob?.cancel()
        hzJob = null
        accelCount.set(0)
        gyroCount.set(0)

        _uiState.update { it.copy(isListening = false, updateRateHz = 0) }
    }

    fun processIncomingSimulatedData(ax: Float, ay: Float, az: Float, gx: Float, gy: Float, gz: Float) {
        if (_uiState.value.isPaused) return
        val state = _uiState.value
        // Use elapsedRealtimeNanos to match SensorEvent.timestamp standard
        val timestamp = SystemClock.elapsedRealtimeNanos()

        // 1. Process Accelerometer
        accelCount.incrementAndGet()
        val fAx = filterValue(ax, state.accelerometer.x, state.isStabilized)
        val fAy = filterValue(ay, state.accelerometer.y, state.isStabilized)
        val fAz = filterValue(az, state.accelerometer.z, state.isStabilized)
        val accelData = SensorData(fAx, fAy, fAz, timestamp)

        // 2. Process Gyroscope
        gyroCount.incrementAndGet()
        val fGx = filterValue(gx, state.gyroscope.x, state.isStabilized)
        val fGy = filterValue(gy, state.gyroscope.y, state.isStabilized)
        val fGz = filterValue(gz, state.gyroscope.z, state.isStabilized)
        val gyroData = SensorData(fGx, fGy, fGz, timestamp)

        // Update UI State
        _uiState.update { currentState ->
            val newAccelPeak = SensorData(
                x = maxOf(currentState.accelPeak.x, abs(fAx)),
                y = maxOf(currentState.accelPeak.y, abs(fAy)),
                z = maxOf(currentState.accelPeak.z, abs(fAz))
            )
            val newGyroPeak = SensorData(
                x = maxOf(currentState.gyroPeak.x, abs(fGx)),
                y = maxOf(currentState.gyroPeak.y, abs(fGy)),
                z = maxOf(currentState.gyroPeak.z, abs(fGz))
            )

            val newAccelHistory = if (timestamp - lastAccelGraphTime >= GRAPH_INTERVAL_NS) {
                lastAccelGraphTime = timestamp
                (currentState.accelHistory + accelData).takeLast(45)
            } else {
                currentState.accelHistory
            }

            val newGyroHistory = if (timestamp - lastGyroGraphTime >= GRAPH_INTERVAL_NS) {
                lastGyroGraphTime = timestamp
                (currentState.gyroHistory + gyroData).takeLast(45)
            } else {
                currentState.gyroHistory
            }

            currentState.copy(
                accelerometer = accelData,
                accelPeak = newAccelPeak,
                accelHistory = newAccelHistory,
                gyroscope = gyroData,
                gyroPeak = newGyroPeak,
                gyroHistory = newGyroHistory
            )
        }

        // 3. Handle Impact Detection & Recording on Accelerometer Magnitude spike
        val rawMagnitude = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
        synchronized(this) {
            rollingAccel.addLast(accelData)
            if (rollingAccel.size > 100) {
                rollingAccel.pollFirst()
            }

            rollingGyro.addLast(gyroData)
            if (rollingGyro.size > 100) {
                rollingGyro.pollFirst()
            }

            val gravityDelta = kotlin.math.abs(rawMagnitude - 9.80665f)
            if (!isRecordingPost && gravityDelta > SPIKE_THRESHOLD_DIFF_GRAVITY &&
                (System.currentTimeMillis() - lastTriggerTimeMs > COOLDOWN_MS)) {
                
                isRecordingPost = true
                lastTriggerTimeMs = System.currentTimeMillis()
                spikeSensorTimestamp = timestamp
                peakMagnitudeDuringEvent = rawMagnitude

                activeAccelRecord.clear()
                activeAccelRecord.addAll(rollingAccel)

                activeGyroRecord.clear()
                activeGyroRecord.addAll(rollingGyro)

                _uiState.update { it.copy(isRecordingImpact = true) }
            }

            if (isRecordingPost) {
                activeAccelRecord.add(accelData)
                activeGyroRecord.add(gyroData)
                
                if (rawMagnitude > peakMagnitudeDuringEvent) {
                    peakMagnitudeDuringEvent = rawMagnitude
                }

                if (timestamp >= spikeSensorTimestamp + 2_000_000_000L) {
                    finalizeImpactEvent()
                }
            }
        }
    }

    private var lastAccelGraphTime = 0L
    private var lastGyroGraphTime = 0L
    private val GRAPH_INTERVAL_NS = 60_000_000L // ~60ms in nanoseconds (~16 Hz)

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun toggleGyroUnit() {
        _uiState.update { it.copy(useDegreesPerSecond = !it.useDegreesPerSecond) }
    }

    fun toggleStabilization() {
        _uiState.update { it.copy(isStabilized = !it.isStabilized) }
    }

    fun toggleShowGraph() {
        _uiState.update { it.copy(showGraph = !it.showGraph) }
    }

    fun setShowGraph(show: Boolean) {
        _uiState.update { it.copy(showGraph = show) }
    }

    fun resetPeaks() {
        _uiState.update {
            it.copy(
                accelPeak = SensorData(),
                gyroPeak = SensorData(),
                accelHistory = emptyList(),
                gyroHistory = emptyList()
            )
        }
    }

    fun clearImpacts() {
        _uiState.update { it.copy(detectedImpacts = emptyList()) }
        synchronized(this) {
            rollingAccel.clear()
            rollingGyro.clear()
            activeAccelRecord.clear()
            activeGyroRecord.clear()
            isRecordingPost = false
        }
    }

    private var lastKnownLat = 28.6139
    private var lastKnownLng = 77.2090
    private var simHeadingAngle = 0.65

    fun updateSimulatedLocation(lat: Double, lng: Double) {
        lastKnownLat = lat
        lastKnownLng = lng
        _uiState.update { it.copy(currentLatitude = lat, currentLongitude = lng) }
    }

    private fun getCurrentLocationForImpact(): Pair<Double, Double> {
        try {
            val locationManager = getApplication<Application>().getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            if (locationManager != null) {
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    getApplication(), android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                    getApplication(), android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    val loc = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                    if (loc != null) {
                        lastKnownLat = loc.latitude
                        lastKnownLng = loc.longitude
                        _uiState.update { it.copy(currentLatitude = loc.latitude, currentLongitude = loc.longitude) }
                        return Pair(loc.latitude, loc.longitude)
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback gracefully if location service is not available or permission not yet granted
        }

        // Advance simulated route ~45-80 meters forward along trajectory per impact
        val distanceDegrees = 0.00045 + (Math.random() * 0.00025)
        simHeadingAngle += (Math.random() - 0.5) * 0.35
        lastKnownLat += distanceDegrees * kotlin.math.cos(simHeadingAngle)
        lastKnownLng += distanceDegrees * kotlin.math.sin(simHeadingAngle)
        _uiState.update { it.copy(currentLatitude = lastKnownLat, currentLongitude = lastKnownLng) }

        return Pair(lastKnownLat, lastKnownLng)
    }

    private fun finalizeImpactEvent() {
        val classification = classifier.classify(activeAccelRecord, activeGyroRecord)
        
        // Suppress any events classified as Normal Road to prevent false triggers
        if (classification.label == "Normal") {
            isRecordingPost = false
            activeAccelRecord.clear()
            activeGyroRecord.clear()
            _uiState.update { currentState ->
                currentState.copy(
                    isRecordingImpact = false
                )
            }
            return
        }

        val (lat, lng) = getCurrentLocationForImpact()
        val newEvent = com.example.model.ImpactEvent(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            peakMagnitude = peakMagnitudeDuringEvent,
            accelData = ArrayList(activeAccelRecord),
            gyroData = ArrayList(activeGyroRecord),
            classificationLabel = classification.label,
            classificationConfidence = classification.confidence,
            isHeuristic = classification.isHeuristic,
            latitude = lat,
            longitude = lng
        )
        _uiState.update { currentState ->
            currentState.copy(
                detectedImpacts = (listOf(newEvent) + currentState.detectedImpacts).take(15),
                isRecordingImpact = false
            )
        }
        triggerBeepNotification(classification.label)

        // Asynchronously sync detected hazard to Supabase or HTTP Routing API (non-blocking, fail-safe)
        HazardSyncManager.uploadHazard(newEvent)

        isRecordingPost = false
        activeAccelRecord.clear()
        activeGyroRecord.clear()
    }

    private fun triggerBeepNotification(label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = when (label) {
                "Pothole" -> 1
                "Speed Breaker" -> 2
                "Normal" -> 3
                else -> 1
            }
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                for (i in 0 until count) {
                    if (i > 0) {
                        delay(250)
                    }
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    delay(150)
                }
                toneGen.release()
            } catch (e: Exception) {
                android.util.Log.e("SensorViewModel", "Failed to trigger beep: ${e.message}")
            }
        }
    }

    private fun filterValue(newValue: Float, oldValue: Float, isStabilized: Boolean): Float {
        if (!isStabilized) return newValue
        val alpha = 0.08f
        val smoothed = alpha * newValue + (1f - alpha) * oldValue
        // Noise deadband: if change is extremely small, keep previous to stabilize completely in static positions
        return if (abs(smoothed - oldValue) < 0.015f) oldValue else smoothed
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || _uiState.value.isPaused) return

        val state = _uiState.value
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]
                accelCount.incrementAndGet()

                val x = filterValue(rawX, state.accelerometer.x, state.isStabilized)
                val y = filterValue(rawY, state.accelerometer.y, state.isStabilized)
                val z = filterValue(rawZ, state.accelerometer.z, state.isStabilized)

                val newData = SensorData(x, y, z, event.timestamp)

                _uiState.update { currentState ->
                    val newPeak = SensorData(
                        x = maxOf(currentState.accelPeak.x, abs(x)),
                        y = maxOf(currentState.accelPeak.y, abs(y)),
                        z = maxOf(currentState.accelPeak.z, abs(z))
                    )
                    
                    val newHistory = if (event.timestamp - lastAccelGraphTime >= GRAPH_INTERVAL_NS) {
                        lastAccelGraphTime = event.timestamp
                        (currentState.accelHistory + newData).takeLast(45)
                    } else {
                        currentState.accelHistory
                    }

                    currentState.copy(
                        accelerometer = newData,
                        accelPeak = newPeak,
                        accelHistory = newHistory
                    )
                }

                // Impact Detection & Recording
                val rawMagnitude = kotlin.math.sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
                synchronized(this) {
                    rollingAccel.addLast(newData)
                    if (rollingAccel.size > 100) {
                        rollingAccel.pollFirst()
                    }

                    val gravityDelta = kotlin.math.abs(rawMagnitude - 9.80665f)
                    if (!isRecordingPost && gravityDelta > SPIKE_THRESHOLD_DIFF_GRAVITY &&
                        (System.currentTimeMillis() - lastTriggerTimeMs > COOLDOWN_MS)) {
                        
                        isRecordingPost = true
                        lastTriggerTimeMs = System.currentTimeMillis()
                        spikeSensorTimestamp = event.timestamp
                        peakMagnitudeDuringEvent = rawMagnitude

                        activeAccelRecord.clear()
                        activeAccelRecord.addAll(rollingAccel)

                        activeGyroRecord.clear()
                        activeGyroRecord.addAll(rollingGyro)

                        _uiState.update { it.copy(isRecordingImpact = true) }
                    }

                    if (isRecordingPost) {
                        activeAccelRecord.add(newData)
                        if (rawMagnitude > peakMagnitudeDuringEvent) {
                            peakMagnitudeDuringEvent = rawMagnitude
                        }

                        if (event.timestamp >= spikeSensorTimestamp + 2_000_000_000L) {
                            finalizeImpactEvent()
                        }
                    }
                }
            }
            Sensor.TYPE_GYROSCOPE -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]
                gyroCount.incrementAndGet()

                val x = filterValue(rawX, state.gyroscope.x, state.isStabilized)
                val y = filterValue(rawY, state.gyroscope.y, state.isStabilized)
                val z = filterValue(rawZ, state.gyroscope.z, state.isStabilized)

                val newData = SensorData(x, y, z, event.timestamp)

                _uiState.update { currentState ->
                    val newPeak = SensorData(
                        x = maxOf(currentState.gyroPeak.x, abs(x)),
                        y = maxOf(currentState.gyroPeak.y, abs(y)),
                        z = maxOf(currentState.gyroPeak.z, abs(z))
                    )

                    val newHistory = if (event.timestamp - lastGyroGraphTime >= GRAPH_INTERVAL_NS) {
                        lastGyroGraphTime = event.timestamp
                        (currentState.gyroHistory + newData).takeLast(45)
                    } else {
                        currentState.gyroHistory
                    }

                    currentState.copy(
                        gyroscope = newData,
                        gyroPeak = newPeak,
                        gyroHistory = newHistory
                    )
                }

                // Logging gyroscope into rolling and active buffers
                synchronized(this) {
                    rollingGyro.addLast(newData)
                    if (rollingGyro.size > 100) {
                        rollingGyro.pollFirst()
                    }

                    if (isRecordingPost) {
                        activeGyroRecord.add(newData)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
        stopWebSocketServer()
        classifier.close()
    }
}
