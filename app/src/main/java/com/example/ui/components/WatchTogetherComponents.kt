package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.StreamHealth
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalWatchTogetherExtras
import com.example.ui.theme.parseHexColor

@Composable
fun CinematicBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primary.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.08f),
                        radius = size.maxDimension * 0.55f
                    ),
                    radius = size.maxDimension * 0.55f,
                    center = Offset(size.width * 0.15f, size.height * 0.08f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondary.copy(alpha = 0.09f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.25f),
                        radius = size.maxDimension * 0.45f
                    ),
                    radius = size.maxDimension * 0.45f,
                    center = Offset(size.width * 0.92f, size.height * 0.25f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tertiary.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.95f),
                        radius = size.maxDimension * 0.5f
                    ),
                    radius = size.maxDimension * 0.5f,
                    center = Offset(size.width * 0.5f, size.height * 0.95f)
                )
            }
    ) {
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val extras = LocalWatchTogetherExtras.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = extras.glassSurface,
        border = BorderStroke(1.dp, extras.glassBorder),
        tonalElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun UserAvatarBadge(
    displayName: String,
    colorHex: String,
    size: Dp = 38.dp,
    speaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = parseHexColor(colorHex)
    val extras = LocalWatchTogetherExtras.current
    val initials = displayName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "W" }

    val pulseAlpha = if (speaking) {
        val infiniteTransition = rememberInfiniteTransition(label = "speaking_pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(550),
                repeatMode = RepeatMode.Reverse
            ),
            label = "speaking_alpha"
        )
        anim
    } else 0f

    Box(
        modifier = modifier
            .size(size + if (speaking) 6.dp else 0.dp)
            .then(
                if (speaking) {
                    Modifier.border(
                        width = 2.5.dp,
                        color = extras.syncCyan.copy(alpha = pulseAlpha),
                        shape = CircleShape
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(bgColor, bgColor.copy(alpha = 0.72f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RoomCodePill(
    roomCode: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Text(
            text = roomCode,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun StreamHealthBadge(
    health: StreamHealth,
    modifier: Modifier = Modifier
) {
    val extras = LocalWatchTogetherExtras.current
    val dotColor = when (health) {
        StreamHealth.ONLINE -> extras.onlineGreen
        StreamHealth.DEGRADED -> extras.degradedAmber
        StreamHealth.OFFLINE -> extras.offlineSlate
        StreamHealth.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = dotColor.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, dotColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = health.label,
                style = MaterialTheme.typography.labelSmall,
                color = dotColor
            )
        }
    }
}

@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha)
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = base,
        border = BorderStroke(1.dp, border)
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

