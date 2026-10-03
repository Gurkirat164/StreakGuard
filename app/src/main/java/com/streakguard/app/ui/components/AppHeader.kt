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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.streakguard.app.ui.theme.JetBrainsMonoFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streakguard.app.ui.theme.Primary
import com.streakguard.app.ui.theme.Tertiary
import com.streakguard.app.ui.theme.CardInnerBackground
import com.streakguard.app.ui.theme.PillBackground
import com.streakguard.app.ui.theme.TextMuted
import com.streakguard.app.ui.theme.TextPrimary

/**
 * App header: flame mark, "StreakGuard" wordmark and the version badge.
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
                .background(CardInnerBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "StreakGuard",
                tint = Primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "StreakGuard",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Surface(
                color = PillBackground,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    "v0.2.0",
                    color = Tertiary,
                    fontFamily = JetBrainsMonoFontFamily,
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
        color = TextMuted,
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    )
}
