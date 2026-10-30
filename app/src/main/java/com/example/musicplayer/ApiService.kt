package com.example.musicplayer

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("meting/")
    fun getRandomSong(
        @Query("type") type: String = "song",
        @Query("id") id: String = "1330348068" // 起风了
    ): Call<List<SongData>>
}