package com.streakguard.app.di

import android.content.Context
import okhttp3.OkHttpClient
import com.streakguard.app.data.local.AppDatabase
import com.streakguard.app.data.prefs.SettingsStore
import com.streakguard.app.domain.DailyCheckOrchestrator
import com.streakguard.app.notify.NotificationHelper
import com.streakguard.app.platform.PlatformRegistry
import com.streakguard.app.platform.leetcode.LeetCodeApi
import com.streakguard.app.platform.leetcode.LeetCodePlatform
import com.streakguard.app.schedule.AlarmScheduler
import java.util.concurrent.TimeUnit

/**
 * Manual dependency container (no DI framework). Everything is lazy so the
 * app starts fast and receivers only pay for what they use.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val leetCodeApi: LeetCodeApi by lazy { LeetCodeApi(okHttpClient) }

    val platformRegistry: PlatformRegistry by lazy {
        PlatformRegistry(
            listOf(
                // To add a platform: implement StreakPlatform, then add one line here.
                LeetCodePlatform(leetCodeApi),
            )
        )
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val notificationHelper: NotificationHelper by lazy { NotificationHelper(appContext) }

    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(appContext, settingsStore) }

    val checkOrchestrator: DailyCheckOrchestrator by lazy {
        DailyCheckOrchestrator(
            registry = platformRegistry,
            settings = settingsStore,
            db = database,
            notifications = notificationHelper,
        )
    }
}
