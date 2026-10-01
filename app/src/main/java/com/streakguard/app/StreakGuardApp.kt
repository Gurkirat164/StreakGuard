package com.streakguard.app

import android.app.Application
import com.streakguard.app.di.AppContainer

class StreakGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
