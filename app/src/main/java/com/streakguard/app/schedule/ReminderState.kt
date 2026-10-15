package com.streakguard.app.schedule

import android.content.Context

/** Durable delivery/outage state survives process death and device reboots. */
@android.annotation.SuppressLint("ApplySharedPref") // Persist delivery decisions before scheduling external work.
class ReminderState(context: Context) {
    private val prefs = context.getSharedPreferences("reminder_delivery", Context.MODE_PRIVATE)

    @Synchronized fun delivered(key: String): Boolean = prefs.getBoolean("sent_$key", false)
    @Synchronized fun markDelivered(key: String) { prefs.edit().putBoolean("sent_$key", true).commit() }
    @Synchronized fun attemptFinished(key: String, attempt: Int): Boolean =
        prefs.getInt("attempt_$key", -1) >= attempt
    @Synchronized fun finishAttempt(key: String, attempt: Int) {
        prefs.edit().putInt("attempt_$key", attempt).commit()
    }

    @Synchronized fun claimFailureNotice(): Boolean {
        if (prefs.getBoolean("outage", false)) return false
        prefs.edit().putBoolean("outage", true).commit()
        return true
    }

    @Synchronized fun recovered() { prefs.edit().remove("outage").commit() }
    @Synchronized fun clear() { prefs.edit().clear().commit() }
}
