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

    // Dedicated channel class for independent event recording
    private class RecordingChannel(val labelType: String) {
        var isRecording = false
        var triggerSensorTimestamp = 0L
        var lastTriggerTimeMs = 0L
        var peakMagnitude = 0f
        val accelRecord = mutableListOf<SensorData>()
        val gyroRecord = mutableListOf<SensorData>()

        fun trigger(sensorTimestamp: Long, initialAccel: List<SensorData>, initialGyro: List<SensorData>, startPeak: Float) {
            isRecording = true
            triggerSensorTimestamp = sensorTimestamp
            lastTriggerTimeMs = System.currentTimeMillis()
            peakMagnitude = startPeak
            accelRecord.clear()
            accelRecord.addAll(initialAccel)
            gyroRecord.clear()
            gyroRecord.addAll(initialGyro)
        }

        fun reset() {
            isRecording = false
            accelRecord.clear()
            gyroRecord.clear()
        }
    }

    private val potholeBreakerChannel = RecordingChannel("PotholeOrBreaker")
    private val brakeChannel = RecordingChannel("Sudden Brake")
    private val turnChannel = RecordingChannel("Sudden Turn")

    private val COOLDOWN_MS = 4000L // 4 seconds cooldown per channel to avoid redundant triggers
    private val SPIKE_THRESHOLD_DIFF_GRAVITY = 3.2f // Sensitive threshold deviation for Pothole/Breaker (from standard gravity 9.81)


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
        _uiState.update { it.copy(detectedImpacts = emptyList(), isRecordingImpact = false) }
        synchronized(this) {
            rollingAccel.clear()
            rollingGyro.clear()
            potholeBreakerChannel.reset()
            brakeChannel.reset()
            turnChannel.reset()
        }
    }

    private fun classifyImpact(accelData: List<SensorData>): String {
        if (accelData.isEmpty()) return "General Shock"

        val g = getGravityDirection()
        val gMag = kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
        val baseline = if (gMag > 0.1f) gMag else 9.80665f

        // Calculate peak horizontal deceleration and peak vertical deviation to identify heavy braking
        var maxHorizontal = 0f
        var maxVerticalDev = 0f

        for (p in accelData) {
            val dot = p.x * g.x + p.y * g.y + p.z * g.z
            val aVertical = if (gMag > 0.1f) dot / gMag else p.z
            val totalMagSq = p.x * p.x + p.y * p.y + p.z * p.z
            val hMagSq = (totalMagSq - aVertical * aVertical).coerceAtLeast(0f)
            val aHorizontal = kotlin.math.sqrt(hMagSq)

            val vDev = kotlin.math.abs(aVertical - baseline)
            if (vDev > maxVerticalDev) {
                maxVerticalDev = vDev
            }
            if (aHorizontal > maxHorizontal) {
                maxHorizontal = aHorizontal
            }
        }

        // If the horizontal braking force is dominant over any vertical shock deviation,
        // it is classified as a Sudden Brake rather than a false Speed Breaker from suspension nose-dive.
        if (maxHorizontal > 3.8f && maxHorizontal > maxVerticalDev * 1.15f) {
            return "Sudden Brake"
        }

        // Locate the trigger center for vertical profiling (Pothole / Speed Breaker)
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

        if (triggerIndex == -1 || maxDevFromGravity < 1.5f) {
            return "General Shock"
        }

        // Scan a window around the main trigger point (35 samples before to 35 samples after)
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
        val sigThreshold = 2.0f
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

    private fun getGravityDirection(): SensorData {
        if (rollingAccel.isEmpty()) return SensorData(0f, 0f, 9.80665f)
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        for (p in rollingAccel) {
            sumX += p.x
            sumY += p.y
            sumZ += p.z
        }
        val count = rollingAccel.size.toFloat()
        return SensorData(sumX / count, sumY / count, sumZ / count)
    }

    private fun detectSuddenBrake(accelData: List<SensorData>): Boolean {
        if (accelData.size < 5) return false
        val g = getGravityDirection()
        val gMag = kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
        if (gMag < 0.1f) return false

        for (p in accelData) {
            val dot = p.x * g.x + p.y * g.y + p.z * g.z
            val aVertical = dot / gMag
            val totalMagSq = p.x * p.x + p.y * p.y + p.z * p.z
            val hMagSq = (totalMagSq - aVertical * aVertical).coerceAtLeast(0f)
            val aHorizontal = kotlin.math.sqrt(hMagSq)
            if (aHorizontal > 3.8f) {
                return true
            }
        }
        return false
    }

    private fun detectSuddenTurn(gyroData: List<SensorData>): Boolean {
        if (gyroData.isEmpty()) return false
        for (p in gyroData) {
            val wMag = kotlin.math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z)
            if (wMag > 2.6f) {
                return true
            }
        }
        return false
    }

    private fun finalizePotholeOrBreaker() {
        val accelData = potholeBreakerChannel.accelRecord
        val calculatedLabel = classifyImpact(accelData)

        // If it is classified as General Shock, discard it and do not record it!
        if (calculatedLabel == "General Shock") {
            potholeBreakerChannel.reset()
            updateRecordingStateInUi()
            return
        }

        val hasSuddenBrake = detectSuddenBrake(accelData) || calculatedLabel == "Sudden Brake"
        val hasSuddenTurn = detectSuddenTurn(potholeBreakerChannel.gyroRecord) || calculatedLabel == "Sudden Turn"

        // Reset concurrent/active channels to absorb/suppress duplicate cards
        if (hasSuddenBrake) {
            brakeChannel.reset()
        }
        if (hasSuddenTurn) {
            turnChannel.reset()
        }

        val newEvent = com.example.model.ImpactEvent(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            peakMagnitude = potholeBreakerChannel.peakMagnitude,
            accelData = ArrayList(accelData),
            gyroData = ArrayList(potholeBreakerChannel.gyroRecord),
            label = calculatedLabel,
            isSuddenBrake = hasSuddenBrake,
            isSuddenTurn = hasSuddenTurn
        )
        addEventToUi(newEvent)
        potholeBreakerChannel.reset()
        updateRecordingStateInUi()
    }

    private fun finalizeSuddenBrake() {
        // If there is a Pothole or Speed Breaker recorded within 3.5 seconds or currently recording, ignore/suppress this standalone card
        val recentPotholeOrBreaker = _uiState.value.detectedImpacts.any {
            (it.label == "Pothole" || it.label == "Speed Breaker") &&
            (System.currentTimeMillis() - it.timestamp < 3500L)
        }
        if (recentPotholeOrBreaker || potholeBreakerChannel.isRecording) {
            brakeChannel.reset()
            updateRecordingStateInUi()
            return
        }

        val newEvent = com.example.model.ImpactEvent(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            peakMagnitude = brakeChannel.peakMagnitude,
            accelData = ArrayList(brakeChannel.accelRecord),
            gyroData = ArrayList(brakeChannel.gyroRecord),
            label = "Sudden Brake",
            isSuddenBrake = true,
            isSuddenTurn = false
        )
        addEventToUi(newEvent)
        brakeChannel.reset()
        updateRecordingStateInUi()
    }

    private fun finalizeSuddenTurn() {
        // If there is a Pothole or Speed Breaker recorded within 3.5 seconds or currently recording, ignore/suppress this standalone card
        val recentPotholeOrBreaker = _uiState.value.detectedImpacts.any {
            (it.label == "Pothole" || it.label == "Speed Breaker") &&
            (System.currentTimeMillis() - it.timestamp < 3500L)
        }
        if (recentPotholeOrBreaker || potholeBreakerChannel.isRecording) {
            turnChannel.reset()
            updateRecordingStateInUi()
            return
        }

        val newEvent = com.example.model.ImpactEvent(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            peakMagnitude = turnChannel.peakMagnitude,
            accelData = ArrayList(turnChannel.accelRecord),
            gyroData = ArrayList(turnChannel.gyroRecord),
            label = "Sudden Turn",
            isSuddenBrake = false,
            isSuddenTurn = true
        )
        addEventToUi(newEvent)
        turnChannel.reset()
        updateRecordingStateInUi()
    }

    private fun addEventToUi(event: com.example.model.ImpactEvent) {
        _uiState.update { currentState ->
            currentState.copy(
                detectedImpacts = (listOf(event) + currentState.detectedImpacts).take(15)
            )
        }
    }

    private fun updateRecordingStateInUi() {
        val anyRecording = potholeBreakerChannel.isRecording || brakeChannel.isRecording || turnChannel.isRecording
        _uiState.update { it.copy(isRecordingImpact = anyRecording) }
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

                // Independent Accelerometer Triggers & Logging
                val rawMagnitude = kotlin.math.sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
                synchronized(this) {
                    rollingAccel.addLast(newData)
                    if (rollingAccel.size > 100) {
                        rollingAccel.pollFirst()
                    }

                    // Feed samples to active recording channels
                    if (potholeBreakerChannel.isRecording) {
                        potholeBreakerChannel.accelRecord.add(newData)
                        if (rawMagnitude > potholeBreakerChannel.peakMagnitude) {
                            potholeBreakerChannel.peakMagnitude = rawMagnitude
                        }
                    }
                    if (brakeChannel.isRecording) {
                        brakeChannel.accelRecord.add(newData)
                        if (rawMagnitude > brakeChannel.peakMagnitude) {
                            brakeChannel.peakMagnitude = rawMagnitude
                        }
                    }
                    if (turnChannel.isRecording) {
                        turnChannel.accelRecord.add(newData)
                        if (rawMagnitude > turnChannel.peakMagnitude) {
                            turnChannel.peakMagnitude = rawMagnitude
                        }
                    }

                    // Check for elapsed recording durations (2.0 seconds post-trigger)
                    if (potholeBreakerChannel.isRecording && event.timestamp >= potholeBreakerChannel.triggerSensorTimestamp + 2_000_000_000L) {
                        finalizePotholeOrBreaker()
                    }
                    if (brakeChannel.isRecording && event.timestamp >= brakeChannel.triggerSensorTimestamp + 2_000_000_000L) {
                        finalizeSuddenBrake()
                    }
                    if (turnChannel.isRecording && event.timestamp >= turnChannel.triggerSensorTimestamp + 2_000_000_000L) {
                        finalizeSuddenTurn()
                    }

                    // Trigger Channel 1: Pothole / Speed Breaker
                    val gravityDelta = kotlin.math.abs(rawMagnitude - 9.80665f)
                    if (!potholeBreakerChannel.isRecording && gravityDelta > SPIKE_THRESHOLD_DIFF_GRAVITY &&
                        (System.currentTimeMillis() - potholeBreakerChannel.lastTriggerTimeMs > COOLDOWN_MS)) {
                        
                        potholeBreakerChannel.trigger(
                            event.timestamp,
                            ArrayList(rollingAccel),
                            ArrayList(rollingGyro),
                            rawMagnitude
                        )
                        updateRecordingStateInUi()
                    }

                    // Trigger Channel 2: Sudden Brake
                    val g = getGravityDirection()
                    val gMag = kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
                    val dot = rawX * g.x + rawY * g.y + rawZ * g.z
                    val aVertical = if (gMag > 0.1f) dot / gMag else 9.80665f
                    val totalMagSq = rawX * rawX + rawY * rawY + rawZ * rawZ
                    val hMagSq = (totalMagSq - aVertical * aVertical).coerceAtLeast(0f)
                    val aHorizontal = kotlin.math.sqrt(hMagSq)

                    if (!brakeChannel.isRecording && aHorizontal > 3.8f &&
                        (System.currentTimeMillis() - brakeChannel.lastTriggerTimeMs > COOLDOWN_MS)) {
                        
                        brakeChannel.trigger(
                            event.timestamp,
                            ArrayList(rollingAccel),
                            ArrayList(rollingGyro),
                            rawMagnitude
                        )
                        updateRecordingStateInUi()
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

                // Independent Gyroscope Triggers & Logging
                val wMag = kotlin.math.sqrt(rawX * rawX + rawY * rawY + rawZ * rawZ)
                synchronized(this) {
                    rollingGyro.addLast(newData)
                    if (rollingGyro.size > 100) {
                        rollingGyro.pollFirst()
                    }

                    // Feed samples to active recording channels
                    if (potholeBreakerChannel.isRecording) {
                        potholeBreakerChannel.gyroRecord.add(newData)
                    }
                    if (brakeChannel.isRecording) {
                        brakeChannel.gyroRecord.add(newData)
                    }
                    if (turnChannel.isRecording) {
                        turnChannel.gyroRecord.add(newData)
                    }

                    // Trigger Channel 3: Sudden Turn
                    if (!turnChannel.isRecording && wMag > 2.6f &&
                        (System.currentTimeMillis() - turnChannel.lastTriggerTimeMs > COOLDOWN_MS)) {
                        
                        turnChannel.trigger(
                            event.timestamp,
                            ArrayList(rollingAccel),
                            ArrayList(rollingGyro),
                            9.80665f // baseline default magnitude
                        )
                        updateRecordingStateInUi()
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
