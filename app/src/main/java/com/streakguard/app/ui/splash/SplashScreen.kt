package com.streakguard.app.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.streakguard.app.R
import com.streakguard.app.ui.theme.AppBackground
import kotlinx.coroutines.delay

private const val SPLASH_MAX_MILLIS = 8_000L

/**
 * Static launch screen: the flame logo, shown fully at once.
 * No staged animation, no loading bar. Dismisses as soon as the
 * home data is ready, with a safety cap so it can never hang.
 */
@Composable
fun StaticSplashScreen(
    isReady: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(isReady) {
        if (isReady && !done) {
            done = true
            onDone()
        }
    }
    LaunchedEffect(Unit) {
        delay(SPLASH_MAX_MILLIS)
        if (!done) {
            done = true
            onDone()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_flame),
            contentDescription = "StreakGuard",
            tint = Color.Unspecified,
            modifier = Modifier.size(112.dp, 126.dp),
        )
    }
}
