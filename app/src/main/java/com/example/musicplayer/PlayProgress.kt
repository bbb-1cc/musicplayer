package com.example.musicplayer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "progress_table")
data class PlayProgress(
    @PrimaryKey
    val songUrl: String,
    val currentPosition: Long // 记录播放到第几毫秒
)