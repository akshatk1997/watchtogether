package com.example.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FloatingReactionEvent
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun FloatingReactionsOverlay(
    reactions: List<FloatingReactionEvent>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        reactions.forEach { reaction ->
            key(reaction.id) {
                FloatingReactionParticle(
                    reaction = reaction,
                    containerWidthPx = widthPx,
                    containerHeightPx = heightPx
                )
            }
        }
    }
}

@Composable
private fun FloatingReactionParticle(
    reaction: FloatingReactionEvent,
    containerWidthPx: Float,
    containerHeightPx: Float
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(reaction.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    val yOffset = (containerHeightPx * (0.82f - 0.72f * p)).roundToInt()
    val swayPx = (sin(p * Math.PI * 3.0) * 24.0).toFloat()
    val xOffset = (containerWidthPx * reaction.laneFraction + swayPx)
        .coerceIn(16f, (containerWidthPx - 96f).coerceAtLeast(16f))
        .roundToInt()
    val alpha = when {
        p < 0.15f -> p / 0.15f
        p > 0.75f -> ((1f - p) / 0.25f).coerceIn(0f, 1f)
        else -> 1f
    }
    val scale = if (p < 0.2f) 0.7f + (p / 0.2f) * 0.45f else 1.15f - p * 0.15f

    Box(
        modifier = Modifier
            .offset { IntOffset(xOffset, yOffset) }
            .alpha(alpha)
            .scale(scale)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = reaction.emoji,
                fontSize = 30.sp
            )
            if (reaction.senderName.isNotBlank()) {
                Text(
                    text = reaction.senderName.take(12),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
