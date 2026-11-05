package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences

/**
 * 核心通信工具类：用于主App与桌面小组件的数据同步
 */
object SpUtils {

    private const val SP_NAME = "music_widget_sp"
    private const val KEY_SONG_NAME = "current_song_name"
    private const val KEY_ARTIST = "current_artist"
    private const val KEY_PIC_URL = "current_pic_url"
    private const val KEY_IS_PLAYING = "is_playing"

    // 获取 SharedPreferences 实例
    private fun getSp(context: Context): SharedPreferences {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    // ========== 保存数据（主App调用） ==========

    /**
     * 保存当前播放的歌曲信息
     */
    fun saveCurrentSong(context: Context, name: String, artist: String, picUrl: String) {
        getSp(context).edit().apply {
            putString(KEY_SONG_NAME, name)
            putString(KEY_ARTIST, artist)
            putString(KEY_PIC_URL, picUrl)
            apply()
        }
    }

    /**
     * 保存当前的播放状态（播放中 / 暂停）
     */
    fun savePlayState(context: Context, isPlaying: Boolean) {
        getSp(context).edit().apply {
            putBoolean(KEY_IS_PLAYING, isPlaying)
            apply()
        }
    }

    // ========== 读取数据（小组件调用） ==========

    fun getSongName(context: Context): String {
        return getSp(context).getString(KEY_SONG_NAME, "暂无播放") ?: "暂无播放"
    }

    fun getArtist(context: Context): String {
        return getSp(context).getString(KEY_ARTIST, "未知歌手") ?: "未知歌手"
    }

    fun getPicUrl(context: Context): String {
        return getSp(context).getString(KEY_PIC_URL, "") ?: ""
    }

    fun isPlaying(context: Context): Boolean {
        return getSp(context).getBoolean(KEY_IS_PLAYING, false)
    }
}