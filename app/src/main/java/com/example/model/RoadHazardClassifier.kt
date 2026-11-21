package com.example.model

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException

class RoadHazardClassifier(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var labels: List<String> = listOf("Normal", "Pothole", "Speed Breaker")
    private var means: FloatArray = floatArrayOf(
        0.025382642f, 0.010651469f, 10.204599f,
        -0.0003858858f, 0.00023132358f, -0.00014445591f
    )
    private var scales: FloatArray = floatArrayOf(
        0.61670655f, 0.68362094f, 1.6180978f,
        0.53569675f, 0.19013333f, 0.03563155f
    )
    
    var isTfLiteLoaded: Boolean = false
        private set

    init {
        loadParamsAndLabels()
        try {
            val modelBuffer = loadModelFile(context, "road_hazard_model.tflite")
            interpreter = Interpreter(modelBuffer)
            isTfLiteLoaded = true
            Log.d("RoadHazardClassifier", "Successfully loaded TensorFlow Lite model.")
        } catch (e: Exception) {
            Log.w("RoadHazardClassifier", "Failed to load TFLite model: ${e.message}. Using high-fidelity heuristic fallback.", e)
        }
    }

    private fun loadParamsAndLabels() {
        try {
            val normContent = context.assets.open("normalization_params.json").bufferedReader().use { it.readText() }
            val normObj = JSONObject(normContent)
            val meanArr = normObj.getJSONArray("mean")
            val scaleArr = normObj.getJSONArray("scale")
            
            means = FloatArray(6) { i -> meanArr.getDouble(i).toFloat() }
            scales = FloatArray(6) { i -> scaleArr.getDouble(i).toFloat() }
            
            val labelsContent = context.assets.open("labels.json").bufferedReader().use { it.readText() }
            val labelsArr = JSONArray(labelsContent)
            labels = List(labelsArr.length()) { i -> labelsArr.getString(i) }
        } catch (e: Exception) {
            Log.e("RoadHazardClassifier", "Failed to load params/labels: ${e.message}", e)
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(accelData: List<SensorData>, gyroData: List<SensorData>): ClassificationResult {
        val activeInterpreter = interpreter
        if (activeInterpreter != null && isTfLiteLoaded) {
            try {
                // Get input shape: should be [1, sequence_length, 6]
                val inputShape = activeInterpreter.getInputTensor(0).shape()
                val seqLength = inputShape[1]
                val numFeatures = inputShape[2] // should be 6
                
                // Prepare input buffer: FloatArray of size sequence_length * 6
                // Shape required by interpreter.run is FloatArray of size [1, sequence_length, 6]
                val inputArray = Array(1) { Array(seqLength) { FloatArray(numFeatures) } }
                
                // Fill with data, alignment is crucial! We take the last seqLength elements.
                for (t in 0 until seqLength) {
                    val dataIdx = accelData.size - seqLength + t
                    val accel = if (dataIdx >= 0 && dataIdx < accelData.size) accelData[dataIdx] else SensorData()
                    val gyro = if (dataIdx >= 0 && dataIdx < gyroData.size) gyroData[dataIdx] else SensorData()
                    
                    // Normalize features using the exact standardization parameters
                    inputArray[0][t][0] = (accel.x - means[0]) / scales[0]
                    inputArray[0][t][1] = (accel.y - means[1]) / scales[1]
                    inputArray[0][t][2] = (accel.z - means[2]) / scales[2]
                    inputArray[0][t][3] = (gyro.x - means[3]) / scales[3]
                    inputArray[0][t][4] = (gyro.y - means[4]) / scales[4]
                    inputArray[0][t][5] = (gyro.z - means[5]) / scales[5]
                }
                
                // Get output shape: should be [1, num_classes] (which is 3)
                val outputShape = activeInterpreter.getOutputTensor(0).shape()
                val numClasses = outputShape[1]
                val outputArray = Array(1) { FloatArray(numClasses) }
                
                // Run inference
                activeInterpreter.run(inputArray, outputArray)
                
                // Find class with highest confidence
                var maxIdx = 0
                var maxVal = -1f
                for (i in 0 until numClasses) {
                    if (outputArray[0][i] > maxVal) {
                        maxVal = outputArray[0][i]
                        maxIdx = i
                    }
                }
                
                val label = if (maxIdx < labels.size) labels[maxIdx] else "Unknown"
                return ClassificationResult(
                    label = label,
                    confidence = maxVal,
                    isHeuristic = false
                )
            } catch (e: Exception) {
                Log.e("RoadHazardClassifier", "Inference error: ${e.message}. Using heuristic fallback.", e)
            }
        }
        
        // High-fidelity heuristic fallback
        return runHeuristicClassifier(accelData)
    }

    private fun runHeuristicClassifier(accelData: List<SensorData>): ClassificationResult {
        var maxMagnitude = 0f
        var maxZDev = 0f
        
        for (data in accelData) {
            val mag = kotlin.math.sqrt(data.x * data.x + data.y * data.y + data.z * data.z)
            if (mag > maxMagnitude) {
                maxMagnitude = mag
            }
            val zDev = kotlin.math.abs(data.z - 9.80665f)
            if (zDev > maxZDev) {
                maxZDev = zDev
            }
        }

        // Heuristic classification logic:
        // - Pothole: Very high acceleration spike, major Z deviation.
        // - Speed Breaker: Moderate spike, medium vertical deviation.
        // - Normal: Low variation.
        val label = when {
            maxMagnitude > 23f || maxZDev > 14f -> "Pothole"
            maxMagnitude > 14f || maxZDev > 6f -> "Speed Breaker"
            else -> "Normal"
        }
        
        val baseConfidence = when (label) {
            "Pothole" -> 0.75f + (maxZDev / 40f).coerceAtMost(0.24f)
            "Speed Breaker" -> 0.70f + (maxMagnitude / 50f).coerceAtMost(0.28f)
            else -> 0.85f
        }

        return ClassificationResult(
            label = label,
            confidence = baseConfidence,
            isHeuristic = true
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val isHeuristic: Boolean
)
