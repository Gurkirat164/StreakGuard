package com.streakguard.app.ui.components

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.streakguard.app.R
import com.streakguard.app.ui.home.PlatformUiState
import com.streakguard.app.ui.theme.CardBackground
import com.streakguard.app.ui.theme.CardInnerBackground
import com.streakguard.app.ui.theme.GeistFontFamily
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import com.streakguard.app.ui.theme.OnPrimary
import com.streakguard.app.ui.theme.PillBackground
import com.streakguard.app.ui.theme.Primary
import com.streakguard.app.ui.theme.Secondary
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.TextMuted
import com.streakguard.app.ui.theme.TextPrimary
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.delay

/** Ticking countdown to the next UTC midnight, e.g. "03h 41m 18s". */
@Composable
private fun rememberUtcCountdown(): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val diff = (TimeUtils.nextUtcMidnightMillis() - now).coerceAtLeast(0)
    val h = diff / 3_600_000
    val m = (diff % 3_600_000) / 60_000
    val s = (diff % 60_000) / 1_000
    return "%02dh %02dm %02ds".format(h, m, s)
}

/**
 * Platform status card: header with status pill, streak hero with glowing
 * flame badge, reset countdown strip with day-progress bar, solve + refresh
 * actions. Used identically on the Today and LeetCode tabs.
 */
@Composable
fun PlatformCard(
    platform: PlatformUiState,
    isChecking: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val countdown = rememberUtcCountdown()
    val resetLocal = remember { TimeUtils.utcMidnightInLocalTime() }
    val dayProgress = remember(countdown) { TimeUtils.utcDayProgress() }

    val (pillText, pillColor) = when (platform.completed) {
        true -> "● SOLVED" to Secondary
        false -> "● PENDING TODAY" to Tertiary
        null -> "● UNKNOWN" to TextMuted
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // --- Header: icon tile, name, status pill ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardInnerBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "</>",
                        color = Tertiary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${platform.displayName} Daily",
                        color = TextPrimary,
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        if (platform.username.isBlank()) "No username set"
                        else "@${platform.username}",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(pillColor.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        pillText,
                        color = pillColor,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                    )
                }
            }

            // --- Streak hero + glowing flame badge ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            platform.streak?.toString() ?: "–",
                            color = TextPrimary,
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 46.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "DAYS",
                            color = Tertiary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        "CURRENT STREAK",
                        color = TextMuted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        letterSpacing = 1.5.sp,
                    )
                }
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                Brush.radialGradient(
                                    0f to Primary.copy(alpha = 0.45f),
                                    0.7f to Primary.copy(alpha = 0.12f),
                                    1f to Color.Transparent,
                                ),
                                CircleShape,
                            ),
                    )
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Streak flame",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                    )
                }
            }

            // --- Reset countdown strip + day progress ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardInnerBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Reset in",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            countdown,
                            color = Tertiary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                    }
                    Text(
                        resetLocal,
                        color = TextMuted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(PillBackground),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(dayProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                Brush.horizontalGradient(listOf(Primary, Tertiary)),
                            ),
                    )
                }
            }

            // --- Actions ---
            Button(
                onClick = {
                    val url = platform.challengeUrl ?: "https://leetcode.com"
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = OnPrimary,
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(Primary, Tertiary)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Direct Solve Daily",
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Button(
                onClick = onRefresh,
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardInnerBackground,
                    contentColor = TextPrimary,
                    disabledContainerColor = CardInnerBackground,
                    disabledContentColor = TextMuted,
                ),
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Secondary,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isChecking) "Checking…" else "Refresh Now",
                    fontFamily = GeistFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
