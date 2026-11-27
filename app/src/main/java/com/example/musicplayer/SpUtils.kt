package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences

object SpUtils {
    private const val SP_NAME = "music_widget_sp"
    private const val KEY_SONG_NAME = "current_song_name"
    private const val KEY_ARTIST = "current_artist"
    private const val KEY_PIC_URL = "current_pic_url"
    private const val KEY_SONG_URL = "current_song_url" // 🌟 新增
    private const val KEY_IS_PLAYING = "is_playing"

    private fun getSp(context: Context): SharedPreferences {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    fun saveCurrentSong(context: Context, name: String, artist: String, picUrl: String, url: String = "") {
        getSp(context).edit().apply {
            putString(KEY_SONG_NAME, name)
            putString(KEY_ARTIST, artist)
            putString(KEY_PIC_URL, picUrl)
            putString(KEY_SONG_URL, url)
            apply()
        }
    }

    fun getSongUrl(context: Context): String {
        return getSp(context).getString(KEY_SONG_URL, "") ?: ""
    }

    fun savePlayState(context: Context, isPlaying: Boolean) {
        getSp(context).edit().apply { putBoolean(KEY_IS_PLAYING, isPlaying); apply() }
    }

    fun getSongName(context: Context): String = getSp(context).getString(KEY_SONG_NAME, "暂无播放") ?: "暂无播放"
    fun getArtist(context: Context): String = getSp(context).getString(KEY_ARTIST, "未知歌手") ?: "未知歌手"
    fun getPicUrl(context: Context): String = getSp(context).getString(KEY_PIC_URL, "") ?: ""
    fun isPlaying(context: Context): Boolean = getSp(context).getBoolean(KEY_IS_PLAYING, false)
}