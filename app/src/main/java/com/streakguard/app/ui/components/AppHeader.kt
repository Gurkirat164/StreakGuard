package com.streakguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streakguard.app.ui.theme.FigmaAccent
import com.streakguard.app.ui.theme.FigmaAmber
import com.streakguard.app.ui.theme.FigmaCardInner
import com.streakguard.app.ui.theme.FigmaPill
import com.streakguard.app.ui.theme.FigmaTextMuted
import com.streakguard.app.ui.theme.FigmaTextPrimary

/**
 * App header from the Figma design: flame mark, "StreakGuard" wordmark and the
 * version badge.
 */
@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(FigmaCardInner),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Whatshot,
                contentDescription = "StreakGuard",
                tint = FigmaAccent,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "StreakGuard",
                color = FigmaTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Surface(
                color = FigmaPill,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "v0.1.0 PRE-RELEASE",
                    color = FigmaAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Small caption used across cards, e.g. "LOCAL STORAGE". */
@Composable
fun MonoCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = FigmaTextMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    )
}
