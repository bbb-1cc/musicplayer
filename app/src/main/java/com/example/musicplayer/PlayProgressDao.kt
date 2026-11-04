package com.example.musicplayer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PlayProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlayProgress)

    @Query("SELECT * FROM progress_table WHERE songUrl = :url LIMIT 1")
    suspend fun getProgressByUrl(url: String): PlayProgress?
}