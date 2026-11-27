package com.example.musicplayer

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    // 🌟 查询这首歌是否已经在收藏库里
    @Query("SELECT * FROM song_table WHERE url = :songUrl LIMIT 1")
    suspend fun getSongByUrl(songUrl: String): SongEntity?

    // 🌟 删除收藏
    @Query("DELETE FROM song_table WHERE url = :songUrl")
    suspend fun removeFavorite(songUrl: String)

    // 🌟 查询所有被收藏的歌曲
    @Query("SELECT * FROM song_table WHERE isFavorite = 1")
    suspend fun getAllFavorites(): List<SongEntity>
}