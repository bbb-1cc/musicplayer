package com.example.musicplayer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_table")
data class SongEntity(
    @PrimaryKey
    val url: String, // 用歌曲的 url 作为唯一标识
    val name: String,
    val artist: String,
    val pic: String,
    val isFavorite: Boolean = false // 预留一个收藏字段
)