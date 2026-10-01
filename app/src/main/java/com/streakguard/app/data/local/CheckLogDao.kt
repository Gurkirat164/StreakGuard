package com.streakguard.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckLogDao {

    @Insert
    suspend fun insert(log: CheckLog)

    @Query("SELECT * FROM check_log ORDER BY checkedAtEpoch DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<CheckLog>>

    @Query(
        "SELECT * FROM check_log WHERE platformId = :platformId AND date = :date " +
            "ORDER BY checkedAtEpoch DESC LIMIT 1"
    )
    suspend fun getForDate(platformId: String, date: String): CheckLog?
}
