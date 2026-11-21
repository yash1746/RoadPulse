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
    val classificationLabel: String = "Normal",
    val classificationConfidence: Float = 1.0f,
    val isHeuristic: Boolean = true,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
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
    val isRecordingImpact: Boolean = false,
    val isModelLoaded: Boolean = false,
    val sensorSource: String = "Device Sensors",
    val webSocketServerUrl: String = "",
    val webSocketStatus: String = "Disconnected",
    val currentLatitude: Double = 28.6139,
    val currentLongitude: Double = 77.2090
)

