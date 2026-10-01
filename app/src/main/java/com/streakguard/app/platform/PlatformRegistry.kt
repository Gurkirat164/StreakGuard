package com.streakguard.app.platform

/**
 * Holds every streak platform the app knows about.
 *
 * To add a platform: implement [StreakPlatform], then add one line in [com.streakguard.app.di.AppContainer].
 */
class PlatformRegistry(val platforms: List<StreakPlatform>) {

    fun get(id: String): StreakPlatform? = platforms.firstOrNull { it.id == id }
}
