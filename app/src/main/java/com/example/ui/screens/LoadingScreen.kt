package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoadingScreen() {
    val bgColor = MaterialTheme.colorScheme.background
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val text = "NexLoad"
            text.forEachIndexed { index, char ->
                AnimatedCharacter(
                    char = char.toString(),
                    delay = index * 100 // 100ms staggered delay
                )
            }
        }
        
        // Minimalist version tag
        Text(
            text = "V1.2.0",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun AnimatedCharacter(char: String, delay: Int) {
    val animatedProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }
    
    val yOffset = (1f - animatedProgress.value) * 20f
    
    Text(
        text = char,
        style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .graphicsLayer(
                alpha = animatedProgress.value,
                translationY = yOffset
            )
    )
}
