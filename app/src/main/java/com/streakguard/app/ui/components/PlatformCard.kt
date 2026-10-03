package com.streakguard.app.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.streakguard.app.R
import com.streakguard.app.ui.home.PlatformUiState
import com.streakguard.app.ui.theme.GeistFontFamily
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import com.streakguard.app.util.TimeUtils
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// Exact colors sampled from the Figma design (StreakGuard-LeetcodeCard).
// ---------------------------------------------------------------------------
private val FigmaCardBg = Color(0xFF1A1F2D)
private val FigmaSubpanelBg = Color(0xFF161B29)
private val FigmaTileBg = Color(0xFF2F3543)
private val FigmaTrackBg = Color(0xFF2F3543)
private val FigmaRefreshBg = Color(0xFF242A38)
private val FigmaOrange = Color(0xFFFF5722)
private val FigmaAmber = Color(0xFFFFB95F)
private val FigmaMint = Color(0xFF4EDEA3)
private val FigmaDarkText = Color(0xFF3B0900)
private val FigmaTitleWhite = Color(0xFFE8ECF8)
private val FigmaWarmWhite = Color(0xFFFFEDE6)
private val FigmaWarmGray = Color(0xFFA89B93)
private val FigmaRefreshText = Color(0xFFDDE2F5)
private val FigmaPillStart = Color(0xFF2A2530)
private val FigmaPillEnd = Color(0xFF5A3A22)
private val FigmaPillSolvedStart = Color(0xFF22302A)
private val FigmaPillSolvedEnd = Color(0xFF2C4A3C)
private val FigmaBtnEnd = Color(0xFFFEAB94)
private val FigmaProgEnd = Color(0xFFFEB25A)

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
 * Platform status card, reproduced exactly from the Figma design:
 * header with platform badge + status pill, streak hero with glowing flame,
 * reset countdown strip with UTC-day progress bar, Direct Solve Daily and
 * Refresh Now actions. Used identically on the Today and LeetCode tabs.
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
    val dayProgress = remember(countdown) { TimeUtils.utcDayProgress() }

    val (pillText, pillDot, pillStart, pillEnd) = when (platform.completed) {
        true -> Quad("SOLVED", FigmaMint, FigmaPillSolvedStart, FigmaPillSolvedEnd)
        false -> Quad("PENDING TODAY", FigmaAmber, FigmaPillStart, FigmaPillEnd)
        null -> Quad("UNKNOWN", FigmaWarmGray, FigmaPillStart, FigmaPillEnd)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FigmaCardBg),
    ) {
        // --- Ambient overlays (behind content, clipped to the card) ---
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(176.dp)
                .background(
                    Brush.radialGradient(
                        0f to Color(0xFFFF4A1F).copy(alpha = 0.30f),
                        1f to Color.Transparent,
                    ),
                    CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-48).dp, y = 48.dp)
                .size(144.dp)
                .background(
                    Brush.radialGradient(
                        0f to Color(0xFF4A3A30).copy(alpha = 0.45f),
                        1f to Color.Transparent,
                    ),
                    CircleShape,
                ),
        )

        Column(modifier = Modifier.padding(16.dp)) {
            // --- Header: platform badge, name, status pill ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(FigmaTileBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo_code),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(15.dp, 12.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${platform.displayName} Daily",
                        color = FigmaTitleWhite,
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                    )
                    Text(
                        if (platform.username.isBlank()) "No username set"
                        else "@${platform.username}",
                        color = FigmaWarmGray,
                        fontFamily = GeistFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.horizontalGradient(listOf(pillStart, pillEnd)))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(pillDot, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            pillText,
                            color = pillDot,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }

            // --- Streak hero + glowing flame ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(75.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            platform.streak?.toString() ?: "–",
                            color = FigmaWarmWhite,
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 40.sp,
                            lineHeight = 44.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "DAYS",
                            color = FigmaWarmWhite,
                            fontFamily = GeistFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "CURRENT STREAK",
                        color = FigmaWarmGray,
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        lineHeight = 14.sp,
                    )
                }
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    0f to FigmaOrange.copy(alpha = 0.45f),
                                    0.7f to FigmaOrange.copy(alpha = 0.12f),
                                    1f to Color.Transparent,
                                ),
                                CircleShape,
                            ),
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_flame),
                        contentDescription = "Streak flame",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp, 27.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- Reset countdown strip + UTC-day progress ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FigmaSubpanelBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_hourglass),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(11.dp, 13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Reset in $countdown",
                        color = FigmaAmber,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "00:00 UTC",
                        color = FigmaWarmGray,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(FigmaTrackBg),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(dayProgress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(listOf(FigmaOrange, FigmaProgEnd)),
                            ),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- Direct Solve Daily (with soft orange glow) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.radialGradient(
                                0f to FigmaOrange.copy(alpha = 0.35f),
                                0.65f to FigmaOrange.copy(alpha = 0.10f),
                                1f to Color.Transparent,
                            ),
                            RoundedCornerShape(14.dp),
                        ),
                )
                Button(
                    onClick = {
                        val url = platform.challengeUrl ?: "https://leetcode.com"
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = FigmaDarkText,
                    ),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(listOf(FigmaOrange, FigmaBtnEnd)),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Direct Solve Daily",
                                fontFamily = GeistFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_external),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // --- Refresh Now ---
            Button(
                onClick = onRefresh,
                enabled = !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FigmaRefreshBg,
                    contentColor = FigmaRefreshText,
                    disabledContainerColor = FigmaRefreshBg,
                    disabledContentColor = FigmaWarmGray,
                ),
                contentPadding = PaddingValues(0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = FigmaMint,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isChecking) "Checking…" else "Refresh Now",
                        fontFamily = GeistFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

/** Tiny holder to keep the pill styling readable. */
private data class Quad(
    val text: String,
    val dot: Color,
    val start: Color,
    val end: Color,
)
