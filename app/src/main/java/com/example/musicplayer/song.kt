package com.example.musicplayer

import com.google.gson.annotations.SerializedName

data class SongData(
    @SerializedName("name") val name: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("url") val url: String,
    @SerializedName("pic") val pic: String
)