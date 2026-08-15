package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.SensorData
import com.example.model.SensorUiState
import com.example.model.ImpactEvent
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


    init {
        _uiState.update { state ->
            state.copy(
                accelAvailable = accelerometer != null,
                gyroAvailable = gyroscope != null
            )
        }
    }

    fun startListening() {
        if (_uiState.value.isListening) return

        // Register accelerometer
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Register gyroscope
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        _uiState.update { it.copy(isListening = true) }

        // Start Hz monitoring job
        hzJob?.cancel()
        hzJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                // Use the active sensor count (accelerometer or gyroscope) to estimate rate
                val rate = when {
                    accelerometer != null -> accelCount.getAndSet(0)
                    gyroscope != null -> gyroCount.getAndSet(0)
                    else -> 0
                }
                // Also reset gyroCount if not used as primary rate source to avoid accumulation
                if (accelerometer != null) {
                    gyroCount.getAndSet(0)
                }
                _uiState.update { it.copy(updateRateHz = rate) }
            }
        }
    }

    fun stopListening() {
        if (!_uiState.value.isListening) return

        sensorManager.unregisterListener(this)
        hzJob?.cancel()
        hzJob = null
        accelCount.set(0)
        gyroCount.set(0)

        _uiState.update { it.copy(isListening = false, updateRateHz = 0) }
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

    private fun classifyImpact(accelData: List<SensorData>): String {
        if (accelData.isEmpty()) return "General Shock"

        val baseline = 9.80665f
        
        // Find the absolute maximum deviation from gravity to locate the exact trigger center
        var maxDevFromGravity = 0f
        var triggerIndex = -1
        for (i in accelData.indices) {
            val p = accelData[i]
            val mag = kotlin.math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
            val dev = kotlin.math.abs(mag - baseline)
            if (dev > maxDevFromGravity) {
                maxDevFromGravity = dev
                triggerIndex = i
            }
        }

        if (triggerIndex == -1 || maxDevFromGravity < 3f) {
            return "General Shock"
        }

        // Scan a window around the main trigger point (e.g., 30 samples before to 30 samples after)
        val windowStart = (triggerIndex - 35).coerceAtLeast(0)
        val windowEnd = (triggerIndex + 35).coerceAtMost(accelData.size - 1)

        var maxPositiveDev = 0f
        var maxPositiveIndex = -1
        var maxNegativeDev = 0f // Negative deviation below gravity
        var maxNegativeIndex = -1

        for (i in windowStart..windowEnd) {
            val p = accelData[i]
            val mag = kotlin.math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
            val dev = mag - baseline

            if (dev > maxPositiveDev) {
                maxPositiveDev = dev
                maxPositiveIndex = i
            }
            if (dev < maxNegativeDev) {
                maxNegativeDev = dev
                maxNegativeIndex = i
            }
        }

        // Check if both components are substantial
        val sigThreshold = 2.5f
        return if (maxPositiveDev > sigThreshold && maxNegativeDev < -sigThreshold) {
            if (maxPositiveIndex < maxNegativeIndex) {
                "Speed Breaker"
            } else {
                "Pothole"
            }
        } else if (maxPositiveDev > sigThreshold) {
            "Speed Breaker"
        } else if (maxNegativeDev < -sigThreshold) {
            "Pothole"
        } else {
            "General Shock"
        }
    }

    private fun finalizeImpactEvent() {
        val calculatedLabel = classifyImpact(activeAccelRecord)
        val newEvent = com.example.model.ImpactEvent(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            peakMagnitude = peakMagnitudeDuringEvent,
            accelData = ArrayList(activeAccelRecord),
            gyroData = ArrayList(activeGyroRecord),
            label = calculatedLabel
        )
        _uiState.update { currentState ->
            currentState.copy(
                detectedImpacts = (listOf(newEvent) + currentState.detectedImpacts).take(15),
                isRecordingImpact = false
            )
        }
        isRecordingPost = false
        activeAccelRecord.clear()
        activeGyroRecord.clear()
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
    }
}
