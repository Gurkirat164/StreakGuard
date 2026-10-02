package com.streakguard.app.ui.leetcode

import android.content.Intent
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.components.MonoCaption
import com.streakguard.app.ui.home.HomeViewModel
import com.streakguard.app.ui.theme.FigmaAccent
import com.streakguard.app.ui.theme.FigmaAmber
import com.streakguard.app.ui.theme.FigmaBackground
import com.streakguard.app.ui.theme.FigmaCard
import com.streakguard.app.ui.theme.FigmaGreen
import com.streakguard.app.ui.theme.FigmaOnAccent
import com.streakguard.app.ui.theme.FigmaTextMuted
import com.streakguard.app.ui.theme.FigmaTextPrimary
import com.streakguard.app.ui.theme.FigmaPill

/**
 * Platform detail tab. Shows only data the app already has: the configured
 * username, today's challenge and its check status. No new features.
 */
@Composable
fun LeetCodeScreen(container: AppContainer) {
    val vm: HomeViewModel = viewModel(factory = remember { HomeViewModel.Factory(container) })
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .background(FigmaBackground)
            .padding(horizontal = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(16.dp))
        AppHeader()
        Spacer(Modifier.height(16.dp))

        vm.platforms.forEach { platform ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = FigmaCard),
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
                            color = FigmaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                        )
                        val (pillText, pillColor) = when (platform.completed) {
                            true -> "SOLVED" to FigmaGreen
                            false -> "PENDING" to FigmaAmber
                            null -> "UNKNOWN" to FigmaTextMuted
                        }
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

                    if (platform.username.isBlank()) {
                        Text(
                            "No username set — add it in Settings.",
                            color = FigmaTextMuted,
                            fontSize = 13.sp,
                        )
                    } else {
                        Text(
                            "@${platform.username}",
                            color = FigmaAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }

                    MonoCaption("TODAY'S CHALLENGE")
                    Text(
                        text = platform.challengeTitle ?: "Not checked yet — run a check from Today.",
                        color = if (platform.challengeUrl != null) FigmaAccent else FigmaTextMuted,
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
                            color = FigmaTextMuted,
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
                            containerColor = FigmaAccent,
                            contentColor = FigmaOnAccent,
                        ),
                        modifier = Modifier.height(40.dp),
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
                            Icons.Filled.OpenInNew,
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
