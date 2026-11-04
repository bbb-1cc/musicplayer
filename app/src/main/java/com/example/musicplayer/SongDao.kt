package com.example.musicplayer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    @Query("SELECT * FROM song_table")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("DELETE FROM song_table")
    suspend fun clearAll()
}