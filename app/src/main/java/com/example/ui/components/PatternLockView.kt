package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private const val GRID_SIZE = 3
private const val MIN_POINT_COUNT = 4
private val DOT_RADIUS = 8.dp
private val DOT_HIT_RADIUS = 40.dp
private val LINE_STROKE = 4.dp
private val NORMAL_COLOR = Color(0xFF888888)
private val ACTIVE_COLOR = Color(0xFF6750A4)
private val ERROR_COLOR = Color(0xFFB3261E)

@Composable
fun PatternLockView(
    onPatternComplete: (List<Int>) -> Unit,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dotRadiusPx = with(density) { DOT_RADIUS.toPx() }
    val lineStrokePx = with(density) { LINE_STROKE.toPx() }
    val hitRadiusPx = with(density) { DOT_HIT_RADIUS.toPx() }

    var activeDots by remember { mutableStateOf(emptyList<Int>()) }
    var currentPointer by remember { mutableStateOf(Offset.Zero) }
    var dotCenters by remember { mutableStateOf(listOf<Offset>()) }
    var isDragging by remember { mutableStateOf(false) }

    val targetColor = if (isError) ERROR_COLOR else ACTIVE_COLOR
    val lineColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "lineColor"
    )
    val dotColorAnim by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(300),
        label = "dotColor"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(32.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val hit = findDotAt(offset, dotCenters, hitRadiusPx)
                        if (hit >= 0) {
                            activeDots = listOf(hit)
                            currentPointer = offset
                        }
                    },
                    onDrag = { change, _ ->
                        currentPointer = change.position
                        val hit = findDotAt(change.position, dotCenters, hitRadiusPx)
                        if (hit >= 0 && hit !in activeDots) {
                            activeDots = activeDots + hit
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        if (activeDots.size >= MIN_POINT_COUNT) {
                            onPatternComplete(activeDots)
                        } else {
                            activeDots = emptyList()
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        activeDots = emptyList()
                    }
                )
            }
    ) {
        val canvasSize = size.width
        val dotSpacing = canvasSize / (GRID_SIZE + 1)

        dotCenters = (0 until GRID_SIZE * GRID_SIZE).map { i ->
            val row = i / GRID_SIZE
            val col = i % GRID_SIZE
            Offset(
                x = dotSpacing * (col + 1),
                y = dotSpacing * (row + 1)
            )
        }

        if (activeDots.isNotEmpty()) {
            drawPatternLines(activeDots, dotCenters, lineColor, lineStrokePx)

            if (isDragging && activeDots.isNotEmpty()) {
                val lastDot = dotCenters[activeDots.last()]
                drawLine(
                    color = lineColor.copy(alpha = 0.5f),
                    start = lastDot,
                    end = currentPointer,
                    strokeWidth = lineStrokePx,
                    cap = StrokeCap.Round
                )
            }
        }

        dotCenters.forEachIndexed { index, center ->
            val isActive = index in activeDots
            val color = if (isActive) dotColorAnim else NORMAL_COLOR
            drawCircle(color = color, radius = dotRadiusPx, center = center)
            if (isActive) {
                drawCircle(color = color.copy(alpha = 0.3f), radius = dotRadiusPx * 1.8f, center = center)
            }
        }
    }
}

private fun DrawScope.drawPatternLines(
    activeDots: List<Int>,
    centers: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (activeDots.size < 2) return
    val path = Path()
    path.moveTo(centers[activeDots[0]].x, centers[activeDots[0]].y)
    for (i in 1 until activeDots.size) {
        path.lineTo(centers[activeDots[i]].x, centers[activeDots[i]].y)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

private fun findDotAt(point: Offset, centers: List<Offset>, hitRadiusPx: Float): Int {
    centers.forEachIndexed { index, center ->
        val dx = abs(point.x - center.x)
        val dy = abs(point.y - center.y)
        if (dx <= hitRadiusPx && dy <= hitRadiusPx) return index
    }
    return -1
}
