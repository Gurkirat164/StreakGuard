package com.streakguard.app.ui.home

import android.content.Intent
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.theme.FigmaAccent
import com.streakguard.app.ui.theme.FigmaAmber
import com.streakguard.app.ui.theme.FigmaBackground
import com.streakguard.app.ui.theme.FigmaCard
import com.streakguard.app.ui.theme.FigmaCardInner
import com.streakguard.app.ui.theme.FigmaDangerLight
import com.streakguard.app.ui.theme.FigmaGreen
import com.streakguard.app.ui.theme.FigmaOnAccent
import com.streakguard.app.ui.theme.FigmaPill
import com.streakguard.app.ui.theme.FigmaTextMuted
import com.streakguard.app.ui.theme.FigmaTextPrimary
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModel(factory = remember { HomeViewModel.Factory(container) })

    Column(
        modifier = Modifier
            .background(FigmaBackground)
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
                color = FigmaTextMuted,
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
                containerColor = FigmaAccent,
                contentColor = FigmaOnAccent,
                disabledContainerColor = FigmaPill,
                disabledContentColor = FigmaTextMuted,
            ),
        ) {
            if (vm.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = FigmaOnAccent,
                )
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (vm.isChecking) "CHECKING…" else "CHECK NOW",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 0.8.sp,
            )
        }

        vm.lastCheckedText?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = FigmaTextMuted, fontSize = 12.sp)
        }
        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = FigmaDangerLight, fontSize = 13.sp)
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
    val midnight = ZonedDateTime.now(ZoneOffset.UTC)
        .toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
        .toInstant().toEpochMilli()
    val diff = (midnight - now).coerceAtLeast(0)
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
        true -> "SOLVED" to FigmaGreen
        false -> "PENDING" to FigmaAmber
        null -> "UNKNOWN" to FigmaTextMuted
    }
    val inspectionLine = when {
        platform.username.isBlank() -> "Set a username to begin inspection"
        platform.challengeTitle == null -> "Not inspected yet — run a check"
        platform.completed == true -> "Inspection: solved after 00:00 UTC"
        platform.completed == false -> "Inspection: 0 AC after 00:00 UTC"
        else -> "Inspection: unknown — last check failed"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FigmaCard),
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
                        .background(FigmaCardInner),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = FigmaAccent,
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
                            color = FigmaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        Surface(color = FigmaPill, shape = RoundedCornerShape(999.dp)) {
                            Text(
                                pillText,
                                color = pillColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        inspectionLine,
                        color = FigmaTextMuted,
                        fontFamily = FontFamily.Monospace,
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
                        containerColor = FigmaAccent,
                        contentColor = FigmaOnAccent,
                    ),
                    modifier = Modifier.height(38.dp),
                ) {
                    Text(
                        "SOLVE NOW",
                        fontFamily = FontFamily.Monospace,
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
                    .background(FigmaCardInner)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DAY CLOSES IN",
                    color = FigmaTextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    countdown,
                    color = FigmaTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
