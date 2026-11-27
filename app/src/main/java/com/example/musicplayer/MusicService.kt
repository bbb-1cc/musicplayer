package com.example.musicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate)
    private var saveProgressJob: Job? = null

    private val widgetControlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_WIDGET_CONTROL") {
                val player = mediaSession?.player ?: return
                val specificAction = intent.getStringExtra("WIDGET_ACTION")

                when (specificAction) {
                    MusicWidgetProvider.ACTION_WIDGET_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                    MusicWidgetProvider.ACTION_WIDGET_PREV -> if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                    MusicWidgetProvider.ACTION_WIDGET_NEXT -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }
                SpUtils.savePlayState(applicationContext, player.isPlaying)
                sendBroadcast(Intent(MusicWidgetProvider.ACTION_WIDGET_UPDATE).apply { setPackage(packageName) })
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
        startSaveProgressTask()

        ContextCompat.registerReceiver(this, widgetControlReceiver, IntentFilter("ACTION_WIDGET_CONTROL"), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun startSaveProgressTask() {
        saveProgressJob?.cancel()
        saveProgressJob = serviceScope.launch {
            while (true) {
                delay(3000) // 3秒存一次，更灵敏
                val player = mediaSession?.player
                if (player != null && player.isPlaying) {
                    val currentMediaItem = player.currentMediaItem
                    val currentPosition = player.currentPosition
                    val songUrl = currentMediaItem?.localConfiguration?.uri?.toString()

                    if (songUrl != null && currentPosition > 0) {
                        withContext(Dispatchers.IO) {
                            val dao = AppDatabase.getInstance(applicationContext).playProgressDao()
                            // 先清空，再存，保证只有一首
                            dao.clearAllProgress()
                            dao.saveProgress(PlayProgress(songUrl, currentPosition))
                        }
                    }
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        mediaSession?.player?.pause()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        saveProgressJob?.cancel()
        try { unregisterReceiver(widgetControlReceiver) } catch (e: Exception) {}
        mediaSession?.run { player.release(); release(); mediaSession = null }
        super.onDestroy()
    }
}