package com.streakguard.app.ui.leetcode

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.components.MonoCaption
import com.streakguard.app.ui.home.HomeViewModel
import com.streakguard.app.ui.theme.Primary
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.AppBackground
import com.streakguard.app.ui.theme.CardBackground
import com.streakguard.app.ui.theme.DangerLight
import com.streakguard.app.ui.theme.Secondary
import com.streakguard.app.ui.theme.OnPrimary
import com.streakguard.app.ui.theme.TextMuted
import com.streakguard.app.ui.theme.TextPrimary
import com.streakguard.app.ui.theme.PillBackground

/**
 * Platform detail tab. Shows the persisted check for today's UTC platform day
 * (loaded on launch, no network) plus a manual refresh.
 */
@Composable
fun LeetCodeScreen(container: AppContainer) {
    // Same instance as the Today tab so both stay in sync.
    val activity = LocalContext.current as ComponentActivity
    val vm: HomeViewModel = viewModel(
        viewModelStoreOwner = activity,
        factory = remember { HomeViewModel.Factory(container) },
    )
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .background(AppBackground)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        AppHeader()
        Spacer(Modifier.height(16.dp))

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
                if (vm.isChecking) "CHECKING…" else "REFRESH STATUS",
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
        Spacer(Modifier.height(16.dp))

        vm.platforms.forEach { platform ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            platform.displayName,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        val (pillText, pillColor) = when (platform.completed) {
                            true -> "SOLVED" to Secondary
                            false -> "PENDING" to Tertiary
                            null -> "UNKNOWN" to TextMuted
                        }
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

                    if (platform.username.isBlank()) {
                        Text(
                            "No username set — add it in Settings.",
                            color = TextMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        Text(
                            "@${platform.username}",
                            color = Tertiary,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 13.sp,
                        )
                    }

                    MonoCaption("TODAY'S CHALLENGE")
                    Text(
                        text = platform.challengeTitle ?: "Not checked yet — tap refresh above.",
                        color = if (platform.challengeUrl != null) Primary else TextMuted,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable(enabled = platform.challengeUrl != null) {
                            platform.challengeUrl?.let { url ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        },
                    )

                    platform.streak?.let { streak ->
                        Text(
                            "Current streak: $streak days",
                            color = TextMuted,
                            fontSize = 13.sp,
                        )
                    }

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
                        modifier = Modifier.height(40.dp),
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
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}
