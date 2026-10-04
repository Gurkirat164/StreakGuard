package com.streakguard.app.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streakguard.app.R
import com.streakguard.app.ui.theme.AppBackground
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import com.streakguard.app.ui.theme.Primary
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.TextMuted
import kotlinx.coroutines.delay

/**
 * Animated launch screen: the flame fades in, the fire grows with a glow
 * bloom, and a loading bar at the bottom tracks real init progress.
 *
 * [isReady] should flip true once the initial data load finishes; the bar
 * then completes and [onDone] fires. A safety cap guarantees the splash can
 * never trap the user.
 */
@Composable
fun AnimatedSplashScreen(
    isReady: Boolean,
    onDone: () -> Unit,
) {
    var logoVisible by remember { mutableStateOf(false) }
    var fireGrown by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var done by remember { mutableStateOf(false) }

    // Stage 1: logo fades in.
    LaunchedEffect(Unit) {
        delay(150)
        logoVisible = true
    }
    // Stage 2: the fire grows.
    LaunchedEffect(Unit) {
        delay(500)
        fireGrown = true
    }
    // Loading bar: crawl to 85% while work happens, finish when ready.
    LaunchedEffect(Unit) {
        animate(
            initialValue = 0f,
            targetValue = 0.85f,
            animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
        ) { value, _ -> if (!done) progress = value }
    }
    LaunchedEffect(isReady) {
        if (isReady && !done) {
            animate(
                initialValue = progress,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing),
            ) { value, _ -> progress = value }
            delay(250)
            done = true
            onDone()
        }
    }
    // Safety cap: never hold the splash longer than this.
    LaunchedEffect(Unit) {
        delay(SPLASH_MAX_MILLIS)
        if (!done) {
            done = true
            onDone()
        }
    }

    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(450),
        label = "logoAlpha",
    )
    val fireScale by animateFloatAsState(
        targetValue = if (fireGrown) 1f else 0.55f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "fireScale",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (fireGrown) 0.55f else 0.10f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "glowAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            // The fire.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(168.dp)
                        .alpha(glowAlpha)
                        .background(
                            Brush.radialGradient(
                                0f to Primary.copy(alpha = 0.55f),
                                0.7f to Primary.copy(alpha = 0.15f),
                                1f to Color.Transparent,
                            ),
                            CircleShape,
                        ),
                )
                Icon(
                    painter = painterResource(R.drawable.ic_flame),
                    contentDescription = "StreakGuard",
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .size(96.dp, 108.dp)
                        .scale(fireScale)
                        .alpha(logoAlpha),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "STREAKGUARD",
                color = TextMuted,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.alpha(logoAlpha),
            )
            Spacer(Modifier.weight(1f))
            // Loading bar.
            Box(
                modifier = Modifier
                    .padding(horizontal = 64.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF242A38)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Primary, Tertiary)),
                        ),
                )
            }
            Spacer(Modifier.height(64.dp))
        }
    }
}

private const val SPLASH_MAX_MILLIS = 8_000L
