package com.example.musicplayer

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    // 🌟 改成 type=playlist，id 是网易云热歌榜的 ID (3778678)
    @GET("meting/")
    fun getPlaylist(
        @Query("type") type: String = "playlist",
        @Query("id") id: String = "3778678"
    ): Call<List<SongData>>
}