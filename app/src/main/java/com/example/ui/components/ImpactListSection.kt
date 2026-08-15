package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Block
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ImpactEvent
import com.example.model.SensorData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImpactListSection(
    impacts: List<ImpactEvent>,
    isRecording: Boolean,
    onClearAll: () -> Unit,
    useDegrees: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("impact_list_section")
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ElectricBolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AUTOMATIC IMPACT CAPTURE",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (impacts.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("clear_all_impacts_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear all",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear All",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Capturing Indicator
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SUDDEN SPIKE DETECTED! CAPTURING WINDOW...",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (impacts.isEmpty()) {
            // Empty State
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(20.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "No events",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Impact Spikes Recorded",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The app continuously buffers 2s of data. A sudden shock/drop triggers automatic 4s capture (2s pre + 2s post spike) kept locally in memory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // Event List
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                impacts.forEach { event ->
                    ImpactEventCard(
                        event = event,
                        timeFormatted = timeFormat.format(Date(event.timestamp)),
                        useDegrees = useDegrees
                    )
                }
            }
        }
    }
}

@Composable
fun ImpactEventCard(
    event: ImpactEvent,
    timeFormatted: String,
    useDegrees: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    val (badgeBgColor, badgeTextColor, iconType) = remember(event.label) {
        when (event.label) {
            "Pothole" -> Triple(
                Color(0xFFFFEBEE), // Light Red
                Color(0xFFC62828), // Dark Red
                Icons.Default.WarningAmber
            )
            "Speed Breaker" -> Triple(
                Color(0xFFE8F5E9), // Light Green
                Color(0xFF2E7D32), // Dark Green
                Icons.Default.ElectricBolt
            )
            "Sudden Brake" -> Triple(
                Color(0xFFFFE0B2), // Light Orange/Brown
                Color(0xFFE65100), // Dark Orange
                Icons.Default.Block
            )
            "Sudden Turn" -> Triple(
                Color(0xFFE1F5FE), // Light Blue
                Color(0xFF01579B), // Dark Blue
                Icons.Default.Refresh
            )
            else -> Triple(
                Color(0xFFF5F5F5), // Light Gray
                Color(0xFF616161), // Dark Gray
                Icons.Default.ElectricBolt
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isExpanded)
                    badgeTextColor.copy(alpha = 0.5f)
                else
                    badgeTextColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(18.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded)
                badgeBgColor.copy(alpha = 0.40f)
            else
                badgeBgColor.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row (always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Type Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = iconType,
                            contentDescription = event.label,
                            tint = badgeTextColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = event.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeTextColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Stats summary
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Impact Recorded",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Peak: ${String.format(Locale.getDefault(), "%.2f", event.peakMagnitude)} m/s²",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    val showBrakeTag = event.isSuddenBrake && event.label != "Sudden Brake"
                    val showTurnTag = event.isSuddenTurn && event.label != "Sudden Turn"
                    if (showBrakeTag || showTurnTag) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            if (showBrakeTag) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFFE0B2)) // Light Orange
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Sudden Brake 🛑",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100) // Dark Orange
                                    )
                                }
                            }
                            if (showTurnTag) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFE1F5FE)) // Light Blue
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Sudden Turn 🔄",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF01579B) // Dark Blue
                                    )
                                }
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = "Expand details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanded content (Plots of 4s window)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Small Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Plot units & Tab selection between Accelerometer & Gyroscope
                    var selectedTab by remember { mutableIntStateOf(0) }
                    val tabs = listOf("ACCELEROMETER PLOT", "GYROSCOPE PLOT")

                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                height = 2.dp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = if (selectedTab == index)
                                            (if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (selectedTab == 0) {
                        // Accel 4s Plot
                        PlotCanvas(
                            data = event.accelData,
                            maxRange = maxOf(25f, event.peakMagnitude),
                            isGyro = false,
                            useDegrees = false
                        )
                    } else {
                        // Gyro 4s Plot
                        val maxGyro = event.gyroData.flatMap { listOf(it.x, it.y, it.z) }.map { kotlin.math.abs(it) }.maxOrNull() ?: 10f
                        PlotCanvas(
                            data = event.gyroData,
                            maxRange = maxOf(6f, maxGyro),
                            isGyro = true,
                            useDegrees = useDegrees
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Label informing about the 2s pre-spike and 2s post-spike split
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Timeline: First half shows 2s before spike. Mid-point shows impact spike. Second half shows 2s after spike.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotCanvas(
    data: List<SensorData>,
    maxRange: Float,
    isGyro: Boolean,
    useDegrees: Boolean
) {
    val axisXColor = Color(0xFFC0504D) // Excel Red
    val axisYColor = Color(0xFF9BBB59) // Excel Olive Green
    val axisZColor = Color(0xFF4F81BD) // Excel Slate Blue

    val radToDeg = 57.29578f
    val displayMaxRange = if (isGyro && useDegrees) maxRange * radToDeg else maxRange
    val unit = if (isGyro) {
        if (useDegrees) "°/s" else "rad/s"
    } else {
        "m/s²"
    }

    // Keep units simple, rounded to nearest whole number
    val simpleMaxCeil = kotlin.math.ceil(displayMaxRange).toInt().coerceAtLeast(1)

    var isPhotoOpened by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Main graph card that acts as a responsive preview button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable { isPhotoOpened = true }
        ) {
            // Render standard static graph
            StaticGraphImage(
                data = data,
                displayMaxRange = displayMaxRange,
                simpleMaxCeil = simpleMaxCeil,
                unit = unit,
                isGyro = isGyro,
                useDegrees = useDegrees,
                radToDeg = radToDeg,
                axisXColor = axisXColor,
                axisYColor = axisYColor,
                axisZColor = axisZColor,
                modifier = Modifier.fillMaxSize()
            )

            // Prompt Badge overlaying the bottom center
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "TAP TO ZOOM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Small Axis indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            LegendDot(label = "X", color = axisXColor)
            Spacer(modifier = Modifier.width(8.dp))
            LegendDot(label = "Y", color = axisYColor)
            Spacer(modifier = Modifier.width(8.dp))
            LegendDot(label = "Z", color = axisZColor)
        }
    }

    // Interactive Photo View Dialog
    if (isPhotoOpened) {
        Dialog(
            onDismissRequest = { isPhotoOpened = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
            ) {
                // Header with dynamic zoom indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Graph Analysis",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "Zoom: %.1fx (Pinch to scale)", scale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    IconButton(
                        onClick = { isPhotoOpened = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Photo View",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interactive 2D Zoomable image container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 80.dp)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 8f)
                                val extraWidth = (size.width * scale - size.width)
                                val extraHeight = (size.height * scale - size.height)
                                val maxOffsetX = (extraWidth / 2f).coerceAtLeast(0f)
                                val maxOffsetY = (extraHeight / 2f).coerceAtLeast(0f)
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Static high-fidelity graph rendering - does not change/replot when scaling!
                    StaticGraphImage(
                        data = data,
                        displayMaxRange = displayMaxRange,
                        simpleMaxCeil = simpleMaxCeil,
                        unit = unit,
                        isGyro = isGyro,
                        useDegrees = useDegrees,
                        radToDeg = radToDeg,
                        axisXColor = axisXColor,
                        axisYColor = axisYColor,
                        axisZColor = axisZColor,
                        zoomScale = scale,
                        panOffsetX = offsetX,
                        panOffsetY = offsetY,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    )
                }

                // Double finger instructions overlay at the bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Use two fingers to Pinch & Zoom • Drag to Pan",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticGraphImage(
    data: List<SensorData>,
    displayMaxRange: Float,
    simpleMaxCeil: Int,
    unit: String,
    isGyro: Boolean,
    useDegrees: Boolean,
    radToDeg: Float,
    axisXColor: Color,
    axisYColor: Color,
    axisZColor: Color,
    zoomScale: Float = 1f,
    panOffsetX: Float = 0f,
    panOffsetY: Float = 0f,
    modifier: Modifier = Modifier
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current

    // Dynamically calculate the step and labels depending on zoom scale
    val step = remember(simpleMaxCeil, zoomScale) {
        val virtualMax = (simpleMaxCeil / zoomScale).coerceAtLeast(1f)
        if (virtualMax > 6f) {
            (virtualMax / 4f).toInt().coerceAtLeast(1)
        } else {
            1
        }
    }

    val labelValues = remember(simpleMaxCeil, step) {
        val list = mutableListOf<Int>()
        for (v in simpleMaxCeil downTo -simpleMaxCeil step step) {
            list.add(v)
        }
        if (!list.contains(0)) {
            list.add(0)
        }
        list.sortedDescending()
    }

    BoxWithConstraints(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerY = heightPx / 2f
        val centerX = widthPx / 2f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            // Left vertical Y-axis line (static horizontally)
            val leftAxisX = 2.dp.toPx()
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.2f),
                start = Offset(leftAxisX, 0f),
                end = Offset(leftAxisX, heightPx),
                strokeWidth = 1.2.dp.toPx()
            )

            // Dynamic scale line sizes (stroke widths)
            val majorTickWidth = (1.2.dp + 0.1.dp * (zoomScale - 1f).coerceIn(0f, 4f)).toPx()
            val mediumTickWidth = (0.8.dp + 0.05.dp * (zoomScale - 1f).coerceIn(0f, 4f)).toPx()
            val minorTickWidth = (0.6.dp + 0.03.dp * (zoomScale - 1f).coerceIn(0f, 4f)).toPx()

            // Centimeter-style Ruler and Subdivision Ticks
            val maxTenths = (simpleMaxCeil * 10).toInt()
            for (t in -maxTenths..maxTenths) {
                val value = t / 10f
                if (value < -displayMaxRange || value > displayMaxRange) continue
                
                // Scale Y coordinates around the centerY viewport, shifted by panOffsetY
                val screenY = centerY - ((value / displayMaxRange) * centerY) * zoomScale + panOffsetY
                
                if (screenY < 0f || screenY > heightPx) continue

                if (t % 10 == 0) {
                    // Whole "cm" marker - Draw a grid line across the width
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.08f),
                        start = Offset(leftAxisX, screenY),
                        end = Offset(widthPx, screenY),
                        strokeWidth = 1.dp.toPx()
                    )
                    // Draw long ruler tick mark
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.4f),
                        start = Offset(leftAxisX, screenY),
                        end = Offset(leftAxisX + 6.dp.toPx() * zoomScale.coerceIn(1f, 1.5f), screenY),
                        strokeWidth = majorTickWidth
                    )
                } else if (t % 5 == 0) {
                    // Half-unit "0.5cm" marker - Draw medium tick and faint line
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.04f),
                        start = Offset(leftAxisX, screenY),
                        end = Offset(widthPx, screenY),
                        strokeWidth = 0.75.dp.toPx()
                    )
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.25f),
                        start = Offset(leftAxisX, screenY),
                        end = Offset(leftAxisX + 4.dp.toPx() * zoomScale.coerceIn(1f, 1.5f), screenY),
                        strokeWidth = mediumTickWidth
                    )
                } else {
                    // "mm" marker - Draw short tick mark on the vertical ruler line
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.15f),
                        start = Offset(leftAxisX, screenY),
                        end = Offset(leftAxisX + 2.dp.toPx() * zoomScale.coerceIn(1f, 1.5f), screenY),
                        strokeWidth = minorTickWidth
                    )
                }
            }

            // Horizontal center line (moves with vertical pan)
            val horizontalCenterY = centerY + panOffsetY
            if (horizontalCenterY in 0f..heightPx) {
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.12f),
                    start = Offset(0f, horizontalCenterY),
                    end = Offset(widthPx, horizontalCenterY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            if (data.isNotEmpty()) {
                val pointsCount = data.size
                val segmentWidth = widthPx / (pointsCount - 1).coerceAtLeast(1)

                val pathX = Path()
                val pathY = Path()
                val pathZ = Path()

                data.forEachIndexed { index, p ->
                    val factor = if (isGyro && useDegrees) radToDeg else 1f
                    val xVal = (p.x * factor).coerceIn(-displayMaxRange, displayMaxRange)
                    val yVal = (p.y * factor).coerceIn(-displayMaxRange, displayMaxRange)
                    val zVal = (p.z * factor).coerceIn(-displayMaxRange, displayMaxRange)

                    // Normalize to display range, scaled by zoomScale around centerX/centerY, shifted by panOffsetX/Y
                    val px = centerX + (index * segmentWidth - centerX) * zoomScale + panOffsetX
                    val pyX = centerY - ((xVal / displayMaxRange) * centerY) * zoomScale + panOffsetY
                    val pyY = centerY - ((yVal / displayMaxRange) * centerY) * zoomScale + panOffsetY
                    val pyZ = centerY - ((zVal / displayMaxRange) * centerY) * zoomScale + panOffsetY

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

                // Smooth stroke size adjustment
                val curveStrokeWidth = (3.dp + (0.2.dp * (zoomScale - 1f).coerceIn(0f, 4f))).toPx()

                // Draw curve lines nicely
                drawPath(
                    path = pathX,
                    color = axisXColor,
                    style = Stroke(width = curveStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = pathY,
                    color = axisYColor,
                    style = Stroke(width = curveStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    path = pathZ,
                    color = axisZColor,
                    style = Stroke(width = curveStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // Y-Axis labels overlay - Positioned dynamically with vertical pan & zoom using our high-precision layout modifier!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
        ) {
            // Determine left alignment position of the label block (static horizontally)
            val labelLeftOffset = 6.dp

            labelValues.forEach { valInt ->
                val value = valInt.toFloat()
                val screenY = centerY - ((value / displayMaxRange) * centerY) * zoomScale + panOffsetY

                // Only show labels that are within the viewport bounds
                if (screenY in 0f..heightPx) {
                    val labelText = if (valInt > 0) "+$valInt" else valInt.toString()
                    
                    // Slightly scale text font sizes dynamically so they are beautiful and fully readable
                    val baseFontSize = if (valInt == 0) 10.sp else 8.5.sp
                    val dynamicFontSize = baseFontSize * (1f + 0.05f * (zoomScale - 1f).coerceIn(0f, 4f))

                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = dynamicFontSize,
                            fontWeight = if (valInt == 0) FontWeight.ExtraBold else FontWeight.Bold
                        ),
                        color = if (valInt == 0)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            // Use our layout modifier to center the text box perfectly vertically around screenY
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    val y = (screenY - placeable.height / 2f).toInt()
                                    // Offset horizontally with panOffsetX so it stays aligned with the vertical ruler line!
                                    val x = labelLeftOffset.toPx().toInt()
                                    placeable.placeRelative(x, y)
                                }
                            }
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
