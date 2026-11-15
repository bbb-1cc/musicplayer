package com.example.musicplayer

import android.app.Service
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // 声明协程作用域和定时任务 Job
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate)
    private var saveProgressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // 1. 初始化 ExoPlayer
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 2. 创建 MediaSession
        mediaSession = MediaSession.Builder(this, player).build()

        // 3. 🌟 启动定时保存进度的任务
        startSaveProgressTask()
    }

    // 🌟 核心方法：协程定时任务，每5秒存一次进度
    private fun startSaveProgressTask() {
        saveProgressJob?.cancel()
        saveProgressJob = serviceScope.launch {
            while (true) {
                delay(5000) // 每 5 秒执行一次
                val player = mediaSession?.player
                if (player != null && player.isPlaying) {
                    val currentMediaItem = player.currentMediaItem
                    val currentPosition = player.currentPosition

                    // 获取当前歌曲的 URL（作为数据库的主键）
                    val songUrl = currentMediaItem?.localConfiguration?.uri?.toString()

                    if (songUrl != null) {
                        // 写入 Room 数据库
                        val progress = PlayProgress(songUrl, currentPosition)
                        AppDatabase.getInstance(applicationContext).playProgressDao().saveProgress(progress)

                        android.util.Log.d("ProgressSave", "已保存进度: $songUrl -> ${currentPosition}ms")
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        // 释放协程和播放器
        saveProgressJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}