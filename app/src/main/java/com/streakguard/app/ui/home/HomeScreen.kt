package com.streakguard.app.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streakguard.app.di.AppContainer
import com.streakguard.app.ui.components.AppHeader
import com.streakguard.app.ui.components.PlatformCard
import com.streakguard.app.ui.theme.AppBackground
import com.streakguard.app.ui.theme.DangerLight
import com.streakguard.app.ui.theme.TextMuted

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
            PlatformCard(
                platform = platform,
                isChecking = vm.isChecking,
                onRefresh = { vm.checkNow() },
            )
            Spacer(Modifier.height(16.dp))
        }

        vm.lastCheckedText?.let {
            Text(it, color = TextMuted, fontSize = 12.sp)
        }
        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DangerLight, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}
