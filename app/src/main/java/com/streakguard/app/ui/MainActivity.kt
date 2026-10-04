package com.streakguard.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.StreakGuardApp
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.home.HomeViewModel
import com.streakguard.app.ui.onboarding.OnboardingDialog
import com.streakguard.app.ui.splash.StaticSplashScreen
import com.streakguard.app.ui.theme.StreakGuardTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container: AppContainer = (application as StreakGuardApp).container

        // Keep the silent midnight refresh armed (cheap, idempotent).
        lifecycleScope.launch {
            runCatching { container.alarmScheduler.scheduleMidnightRefresh() }
        }

        setContent {
            StreakGuardTheme {
                // Activity-scoped: the same instance HomeScreen uses.
                val vm: HomeViewModel =
                    viewModel(factory = remember { HomeViewModel.Factory(container) })

                var splashDone by remember { mutableStateOf(false) }
                splashScreen.setKeepOnScreenCondition { !splashDone }

                var onboardingNeeded by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) {
                    onboardingNeeded =
                        !runCatching { container.settingsStore.onboardingDone.first() }
                            .getOrDefault(true)
                }

                if (!splashDone) {
                    StaticSplashScreen(
                        isReady = vm.initialized,
                        onDone = { splashDone = true },
                    )
                } else {
                    StreakGuardNav(container)
                    if (onboardingNeeded == true) {
                        OnboardingDialog(
                            container = container,
                            onDone = { onboardingNeeded = false },
                        )
                    }
                }
            }
        }
    }
}
