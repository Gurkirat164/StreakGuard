package com.streakguard.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per daily platform check. The [date] is the UTC platform day
 * (yyyy-MM-dd) the check belongs to — the cache key for "today".
 */
@Entity(tableName = "check_log")
data class CheckLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    /** UTC calendar date of the platform day, yyyy-MM-dd. */
    val date: String,
    /**
     * True when the challenge was confirmed done. Only meaningful when [known]
     * is true; a row with known=false means the check couldn't determine a
     * status (network error, bad username).
     */
    val completed: Boolean,
    val known: Boolean = false,
    val checkedAtEpoch: Long,
    val challengeTitle: String?,
    val challengeUrl: String?,
    val streak: Int?,
)
