package com.example.musicplayer

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 修改这里的 BASE_URL
    private const val BASE_URL = "https://api.injahow.cn/"

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

//已完成Retrofit封装与在线拉取