package com.streakguard.app.domain

/** Completion belongs to one account and one UTC platform day, never forever. */
object CompletedDay {
    fun matches(savedDay: String?, day: String, username: String): Boolean =
        username.isNotBlank() && savedDay == day
}
