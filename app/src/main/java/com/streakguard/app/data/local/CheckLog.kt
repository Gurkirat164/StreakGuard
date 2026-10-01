package com.streakguard.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per daily platform check.
 */
@Entity(tableName = "check_log")
data class CheckLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platformId: String,
    /** Local calendar date of the check, yyyy-MM-dd. */
    val date: String,
    /** True when the challenge was confirmed done; false covers both "not done" and "unknown". */
    val completed: Boolean,
    val checkedAtEpoch: Long,
    val challengeTitle: String?,
)
