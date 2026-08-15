package com.example.model

data class SensorData(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val timestamp: Long = 0L
)

data class ImpactEvent(
    val id: String,
    val timestamp: Long, // System.currentTimeMillis()
    val peakMagnitude: Float,
    val accelData: List<SensorData>,
    val gyroData: List<SensorData>,
    val label: String = "General Shock"
)

data class SensorUiState(
    val accelerometer: SensorData = SensorData(),
    val gyroscope: SensorData = SensorData(),
    val accelAvailable: Boolean = false,
    val gyroAvailable: Boolean = false,
    val isListening: Boolean = false,
    val updateRateHz: Int = 0,
    val isPaused: Boolean = false,
    val accelPeak: SensorData = SensorData(),
    val gyroPeak: SensorData = SensorData(),
    val useDegreesPerSecond: Boolean = false,
    val isStabilized: Boolean = true,
    val showGraph: Boolean = true,
    val accelHistory: List<SensorData> = emptyList(),
    val gyroHistory: List<SensorData> = emptyList(),
    val detectedImpacts: List<ImpactEvent> = emptyList(),
    val isRecordingImpact: Boolean = false
)

