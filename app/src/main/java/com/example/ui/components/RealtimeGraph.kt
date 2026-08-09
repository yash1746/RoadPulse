package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SensorData

@Composable
fun RealtimeGraph(
    history: List<SensorData>,
    maxRange: Float,
    isGyro: Boolean,
    modifier: Modifier = Modifier
) {
    val axisXColor = Color(0xFFF43F5E) // Elegant Rose Red
    val axisYColor = Color(0xFF10B981) // Emerald Green
    val axisZColor = Color(0xFF3B82F6) // Bright Blue

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(14.dp)
            .testTag(if (isGyro) "gyro_graph_card" else "accel_graph_card")
    ) {
        // Graph Title and Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "REAL-TIME TELEMETRY PLOT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 10.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.weight(1f))
            
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendItem(label = "X", color = axisXColor)
                LegendItem(label = "Y", color = axisYColor)
                LegendItem(label = "Z", color = axisZColor)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Custom Line Chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
        ) {
            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            val zeroLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(if (isGyro) "gyro_canvas" else "accel_canvas")
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                // Draw Horizontal Grid lines (+Max, Center, -Max)
                drawLine(
                    color = zeroLineColor,
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, centerY - centerY * 0.5f),
                    end = Offset(width, centerY - centerY * 0.5f),
                    strokeWidth = 0.5.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, centerY + centerY * 0.5f),
                    end = Offset(width, centerY + centerY * 0.5f),
                    strokeWidth = 0.5.dp.toPx()
                )

                // Vertical Grid lines
                val vGridCount = 5
                for (i in 1 until vGridCount) {
                    val gridX = (width / vGridCount) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(gridX, 0f),
                        end = Offset(gridX, height),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }

                if (history.isNotEmpty()) {
                    val pointsCount = history.size
                    val segmentWidth = width / (45 - 1) // align with max history size 45
                    val startOffset = width - (pointsCount - 1) * segmentWidth

                    val pathX = Path()
                    val pathY = Path()
                    val pathZ = Path()

                    history.forEachIndexed { index, data ->
                        val xVal = data.x.coerceIn(-maxRange, maxRange)
                        val yVal = data.y.coerceIn(-maxRange, maxRange)
                        val zVal = data.z.coerceIn(-maxRange, maxRange)

                        // Map values to Y coordinate: centerY is 0. positive value is up (smaller Y), negative value is down (larger Y)
                        val pyX = centerY - (xVal / maxRange) * centerY
                        val pyY = centerY - (yVal / maxRange) * centerY
                        val pyZ = centerY - (zVal / maxRange) * centerY

                        val px = startOffset + index * segmentWidth

                        if (index == 0) {
                            pathX.moveTo(px, pyX)
                            pathY.moveTo(px, pyY)
                            pathZ.moveTo(px, pyZ)
                        } else {
                            pathX.lineTo(px, pyX)
                            pathY.lineTo(px, pyY)
                            pathZ.lineTo(px, pyZ)
                        }
                    }

                    // Draw the three strokes
                    drawPath(path = pathX, color = axisXColor, style = Stroke(width = 2.dp.toPx()))
                    drawPath(path = pathY, color = axisYColor, style = Stroke(width = 2.dp.toPx()))
                    drawPath(path = pathZ, color = axisZColor, style = Stroke(width = 2.dp.toPx()))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Axis description
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "AXIS INTERPRETATION",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            if (!isGyro) {
                // Accelerometer axis description
                AxisDescriptionRow(
                    label = "X",
                    color = axisXColor,
                    desc = "Lateral acceleration (Tilt Left ↔ Right)"
                )
                AxisDescriptionRow(
                    label = "Y",
                    color = axisYColor,
                    desc = "Vertical acceleration (Portrait Pitch up ↔ down)"
                )
                AxisDescriptionRow(
                    label = "Z",
                    color = axisZColor,
                    desc = "Depth acceleration (Screen Face up ↔ down)"
                )
            } else {
                // Gyroscope axis description
                AxisDescriptionRow(
                    label = "X",
                    color = axisXColor,
                    desc = "Pitch rate (Rotation tilting front ↔ back)"
                )
                AxisDescriptionRow(
                    label = "Y",
                    color = axisYColor,
                    desc = "Roll rate (Rotation tilting side ↔ side)"
                )
                AxisDescriptionRow(
                    label = "Z",
                    color = axisZColor,
                    desc = "Yaw rate (Rotation spinning clockwise ↔ counter)"
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AxisDescriptionRow(label: String, color: Color, desc: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, shape = RoundedCornerShape(1.5.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$label:",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
    }
}
