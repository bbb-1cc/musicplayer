package com.example.musicplayer

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

class MusicService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate)
    private var saveProgressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
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

        mediaSession = MediaSession.Builder(this, player).build()
        startSaveProgressTask()
    }

    private fun startSaveProgressTask() {
        saveProgressJob?.cancel()
        saveProgressJob = serviceScope.launch {
            while (true) {
                delay(5000) // 每 5 秒执行一次
                val player = mediaSession?.player
                if (player != null && player.isPlaying) {
                    val currentMediaItem = player.currentMediaItem
                    val currentPosition = player.currentPosition
                    val songUrl = currentMediaItem?.localConfiguration?.uri?.toString()

                    if (songUrl != null) {
                        val progress = PlayProgress(songUrl, currentPosition)
                        // 切到 IO 线程写数据库
                        kotlinx.coroutines.withContext(Dispatchers.IO) {
                            AppDatabase.getInstance(applicationContext).playProgressDao().saveProgress(progress)
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        saveProgressJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}