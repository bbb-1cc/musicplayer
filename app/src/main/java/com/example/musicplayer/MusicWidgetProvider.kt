package com.example.musicplayer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_PREV = "ACTION_WIDGET_PREV"
        const val ACTION_WIDGET_NEXT = "ACTION_WIDGET_NEXT"
        const val ACTION_WIDGET_UPDATE = "ACTION_WIDGET_UPDATE"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidgetUI(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_WIDGET_PLAY_PAUSE ||
            intent.action == ACTION_WIDGET_PREV ||
            intent.action == ACTION_WIDGET_NEXT) {

            val controlIntent = Intent("ACTION_WIDGET_CONTROL").apply {
                setPackage(context.packageName)
                putExtra("WIDGET_ACTION", intent.action)
            }
            context.sendBroadcast(controlIntent)

        } else if (intent.action == ACTION_WIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, MusicWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            onUpdate(context, appWidgetManager, allWidgetIds)
        }
    }

    private fun updateWidgetUI(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_music)

        val songName = SpUtils.getSongName(context)
        val artist = SpUtils.getArtist(context)
        val picUrl = SpUtils.getPicUrl(context)
        val isPlaying = SpUtils.isPlaying(context)

        views.setTextViewText(R.id.tvWidgetSongName, songName)
        views.setTextViewText(R.id.tvWidgetArtist, artist)

        val playIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        views.setImageViewResource(R.id.btnWidgetPlayPause, playIcon)

        // 绑定点击事件
        val piPlay = PendingIntent.getBroadcast(context, 0, Intent(context, MusicWidgetProvider::class.java).apply { action = ACTION_WIDGET_PLAY_PAUSE }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btnWidgetPlayPause, piPlay)

        val piPrev = PendingIntent.getBroadcast(context, 1, Intent(context, MusicWidgetProvider::class.java).apply { action = ACTION_WIDGET_PREV }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btnWidgetPrev, piPrev)

        val piNext = PendingIntent.getBroadcast(context, 2, Intent(context, MusicWidgetProvider::class.java).apply { action = ACTION_WIDGET_NEXT }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.btnWidgetNext, piNext)

        // 🌟 核心：如果有图片链接，使用 Glide 异步加载
        if (picUrl.isNotEmpty()) {
            Glide.with(context)
                .asBitmap()
                .load(picUrl)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        // 图片加载成功，塞进 RemoteViews 并更新桌面
                        views.setImageViewBitmap(R.id.ivWidgetCover, resource)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        // 如果图片被清理，使用默认图
                        views.setImageViewResource(R.id.ivWidgetCover, android.R.drawable.ic_menu_gallery)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        // 加载失败，使用默认图
                        views.setImageViewResource(R.id.ivWidgetCover, android.R.drawable.ic_menu_gallery)
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                })
        } else {
            // 没图片链接，直接用默认图
            views.setImageViewResource(R.id.ivWidgetCover, android.R.drawable.ic_menu_gallery)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}