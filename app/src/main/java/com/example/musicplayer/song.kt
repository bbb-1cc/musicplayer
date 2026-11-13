package com.example.musicplayer

import com.google.gson.annotations.SerializedName

data class SongData(
    val name: String,
    val artist: String,
    val url: String,
    val pic: String,
    val rawResId: Int? = null // 本地 raw 资源 ID，网络歌曲为 null
)