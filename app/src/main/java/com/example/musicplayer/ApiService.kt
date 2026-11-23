package com.example.musicplayer

import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    // 🌟 直接用 suspend 挂起函数，Retrofit 会自动在后台线程请求
    @GET("meting/")
    suspend fun getPlaylist(
        @Query("type") type: String = "playlist",
        @Query("id") id: String = "3778678"
    ): List<SongData>
}