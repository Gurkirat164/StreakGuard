package com.streakguard.app.ui.home

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.util.TimeUtils
import com.streakguard.app.ui.theme.Primary
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.AppBackground
import com.streakguard.app.ui.theme.CardBackground
import com.streakguard.app.ui.theme.CardInnerBackground
import com.streakguard.app.ui.theme.DangerLight
import com.streakguard.app.ui.theme.Secondary
import com.streakguard.app.ui.theme.OnPrimary
import com.streakguard.app.ui.theme.PillBackground
import com.streakguard.app.ui.theme.TextMuted
import com.streakguard.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(container: AppContainer) {
    // Shared with the LeetCode tab so both stay in sync.
    val activity = LocalContext.current as ComponentActivity
    val vm: HomeViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = remember { HomeViewModel.Factory(container) },
    )

    Column(
        modifier = Modifier
            .background(AppBackground)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        AppHeader()
        Spacer(Modifier.height(16.dp))

        val platforms = vm.platforms
        if (platforms.isEmpty()) {
            Text(
                "Loading…",
                color = TextMuted,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        platforms.forEach { platform ->
            StatusBanner(platform = platform)
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { vm.checkNow() },
            enabled = !vm.isChecking,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = OnPrimary,
                disabledContainerColor = PillBackground,
                disabledContentColor = TextMuted,
            ),
        ) {
            if (vm.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = OnPrimary,
                )
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (vm.isChecking) "CHECKING…" else "CHECK NOW",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
            )
        }

        vm.lastCheckedText?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = TextMuted, fontSize = 12.sp)
        }
        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DangerLight, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Ticking countdown to the next UTC midnight, e.g. "11h 03m 25s". */
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

@Composable
private fun StatusBanner(platform: PlatformUiState) {
    val context = LocalContext.current
    val countdown = rememberUtcCountdown()

    val (pillText, pillColor) = when (platform.completed) {
        true -> "SOLVED" to Secondary
        false -> "PENDING" to Tertiary
        null -> "UNKNOWN" to TextMuted
    }
    val resetLocal = remember { TimeUtils.utcMidnightInLocalTime() }
    val inspectionLine = when {
        platform.username.isBlank() -> "Set a username to begin inspection"
        platform.challengeTitle == null -> "Not inspected yet — run a check"
        platform.completed == true -> "Inspection: solved after $resetLocal"
        platform.completed == false -> "Inspection: 0 AC after $resetLocal"
        else -> "Inspection: unknown — last check failed"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardInnerBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${platform.displayName} Pipeline",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Surface(color = PillBackground, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                pillText,
                                color = pillColor,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        inspectionLine,
                        color = TextMuted,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        val url = platform.challengeUrl ?: "https://leetcode.com"
                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = Primary,
                        contentColor = OnPrimary,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(
                        "SOLVE NOW",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.8.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Live timer strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardInnerBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DAY CLOSES IN",
                    color = TextMuted,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    countdown,
                    color = TextPrimary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
