package com.streakguard.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.streakguard.app.StreakGuardApp
import com.streakguard.app.ui.theme.StreakGuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as StreakGuardApp).container
        setContent {
            StreakGuardTheme {
                StreakGuardNav(container)
            }
        }
    }
}
