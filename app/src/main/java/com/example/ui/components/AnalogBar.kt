package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun AnalogBar(
    axisLabel: String,
    value: Float,
    maxRange: Float,
    unit: String,
    positiveColor: Color = Color(0xFF0D9488), // Teal
    negativeColor: Color = Color(0xFFE11D48), // Rose
    modifier: Modifier = Modifier
) {
    // Clamp the value to the maxRange
    val clampedValue = value.coerceIn(-maxRange, maxRange)
    val fraction = clampedValue / maxRange

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("analog_bar_${axisLabel.lowercase()}")
    ) {
        // Label and value row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$axisLabel-Axis",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${String.format("%.2f", value)} $unit",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Custom drawn centered linear bar
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        val centerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
        ) {
            val width = size.width
            val height = size.height
            val radius = height / 2

            // Draw background track
            drawRoundRect(
                color = trackColor,
                size = Size(width, height),
                cornerRadius = CornerRadius(radius, radius)
            )

            // Draw active bar originating from center
            val centerX = width / 2f
            if (fraction != 0f) {
                val barWidth = abs(fraction) * centerX
                val startX = if (fraction > 0) centerX else centerX - barWidth
                val color = positiveColor

                drawRoundRect(
                    color = color,
                    topLeft = Offset(startX, 0f),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            // Draw zero center line
            drawLine(
                color = centerLineColor,
                start = Offset(centerX, -1f),
                end = Offset(centerX, height + 1f),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}
