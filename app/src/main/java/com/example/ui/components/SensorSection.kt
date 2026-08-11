package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SensorData

@Composable
fun SensorSection(
    title: String,
    isAvailable: Boolean,
    sensorData: SensorData,
    peakData: SensorData,
    unit: String,
    maxRange: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
    useDegrees: Boolean = false,
    showGraph: Boolean = false,
    history: List<SensorData> = emptyList()
) {
    // Conversion factors
    val radToDeg = 57.29578f
    val displayUnit = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) "°/s" else unit

    val displayX = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) sensorData.x * radToDeg else sensorData.x
    val displayY = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) sensorData.y * radToDeg else sensorData.y
    val displayZ = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) sensorData.z * radToDeg else sensorData.z

    val peakX = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) peakData.x * radToDeg else peakData.x
    val peakY = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) peakData.y * radToDeg else peakData.y
    val peakZ = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) peakData.z * radToDeg else peakData.z

    val displayMaxRange = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) maxRange * radToDeg else maxRange

    val displayHistory = if (useDegrees && title.contains("GYROSCOPE", ignoreCase = true)) {
        history.map { SensorData(it.x * radToDeg, it.y * radToDeg, it.z * radToDeg, it.timestamp) }
    } else {
        history
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("sensor_section_${title.lowercase()}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = displayUnit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isAvailable) {
                // Not available placeholder state
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .testTag("sensor_unavailable_${title.lowercase()}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚠️ Hardware Sensor Not Available",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This device lacks physical hardware support for $title.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                if (!showGraph) {
                    // Digital display row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DigitalValueCard(
                            label = "X",
                            value = displayX,
                            peak = peakX,
                            unit = displayUnit,
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        DigitalValueCard(
                            label = "Y",
                            value = displayY,
                            peak = peakY,
                            unit = displayUnit,
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                        DigitalValueCard(
                            label = "Z",
                            value = displayZ,
                            peak = peakZ,
                            unit = displayUnit,
                            accentColor = accentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Analog bars
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnalogBar(
                            axisLabel = "X",
                            value = displayX,
                            maxRange = displayMaxRange,
                            unit = displayUnit,
                            positiveColor = accentColor,
                            negativeColor = MaterialTheme.colorScheme.error
                        )
                        AnalogBar(
                            axisLabel = "Y",
                            value = displayY,
                            maxRange = displayMaxRange,
                            unit = displayUnit,
                            positiveColor = accentColor,
                            negativeColor = MaterialTheme.colorScheme.error
                        )
                        AnalogBar(
                            axisLabel = "Z",
                            value = displayZ,
                            maxRange = displayMaxRange,
                            unit = displayUnit,
                            positiveColor = accentColor,
                            negativeColor = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    RealtimeGraph(
                        history = displayHistory,
                        maxRange = displayMaxRange,
                        isGyro = title.contains("GYROSCOPE", ignoreCase = true),
                        unit = displayUnit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
