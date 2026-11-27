package com.example.musicplayer

import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.musicplayer.databinding.FragmentPlayerDetailBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerDetailFragment : Fragment() {

    private var _binding: FragmentPlayerDetailBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false
    private var isCurrentFavorite = false

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            val controller = mediaController
            if (controller != null && controller.isConnected && !isUserSeeking) {
                try {
                    val currentPos = controller.currentPosition
                    val duration = controller.duration
                    if (duration > 0 && duration != C.TIME_UNSET) {
                        binding.seekBarDetail.max = duration.toInt()
                        binding.seekBarDetail.progress = currentPos.toInt()
                        binding.tvTimeCurrent.text = formatTime(currentPos)
                        binding.tvTimeTotal.text = formatTime(duration)
                    }
                    binding.btnPlayDetail.setImageResource(if (controller.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                } catch (e: Exception) {}
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    QueueManager.setCurrentIndex(mediaController?.currentMediaItemIndex ?: 0)
                    updateUIFromPlayer()
                }
            })
            updateUIFromPlayer()
            handler.post(updateProgressRunnable)
        }, MoreExecutors.directExecutor())

        binding.ivBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.btnQueue.setOnClickListener { PlayQueueBottomSheet().show(childFragmentManager, "PlayQueue") }

        // 🌟 详情页播放按钮，处理重启后播放器为空的情况
        binding.btnPlayDetail.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            val currentSong = QueueManager.getCurrentSong() ?: return@setOnClickListener

            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.mediaItemCount == 0) {
                    // 如果重启后播放器里是空的，重新加载并跳到指定位置
                    lifecycleScope.launch(Dispatchers.IO) {
                        val savedProgress = AppDatabase.getInstance(requireContext()).playProgressDao().getProgressByUrl(currentSong.url)
                        val startPosition = savedProgress?.currentPosition ?: 0L
                        requireActivity().runOnUiThread {
                            val mediaItem = MediaItem.fromUri(currentSong.url)
                            controller.setMediaItem(mediaItem)
                            controller.prepare()
                            controller.seekTo(startPosition)
                            controller.play()
                        }
                    }
                } else {
                    controller.play()
                }
            }
        }

        binding.btnPrev.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        binding.btnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }

        binding.btnFavorite.setOnClickListener {
            val currentSong = QueueManager.getCurrentSong() ?: return@setOnClickListener
            lifecycleScope.launch(Dispatchers.IO) {
                if (isCurrentFavorite) AppDatabase.getInstance(requireContext()).songDao().removeFavorite(currentSong.url)
                else AppDatabase.getInstance(requireContext()).songDao().insertSong(SongEntity(currentSong.url, currentSong.name, currentSong.artist, currentSong.pic, true))
                requireActivity().runOnUiThread {
                    isCurrentFavorite = !isCurrentFavorite
                    updateFavoriteIcon(isCurrentFavorite)
                    Toast.makeText(requireContext(), if (isCurrentFavorite) "已收藏" else "已取消", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.seekBarDetail.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvTimeCurrent.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                mediaController?.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })
    }

    private fun updateUIFromPlayer() {
        // 1. 从 QueueManager 拿当前应该显示的歌曲数据
        val currentSong = QueueManager.getCurrentSong()

        if (currentSong != null) {
            binding.tvDetailSongName.text = currentSong.name
            binding.tvDetailArtist.text = currentSong.artist
            if (currentSong.pic.isNotEmpty()) binding.ivCover.load(currentSong.pic) { crossfade(true); error(android.R.drawable.ic_menu_report_image) }
            else binding.ivCover.setImageResource(android.R.drawable.ic_menu_report_image)

            lifecycleScope.launch(Dispatchers.IO) {
                val favoriteSong = AppDatabase.getInstance(requireContext()).songDao().getSongByUrl(currentSong.url)
                requireActivity().runOnUiThread {
                    isCurrentFavorite = favoriteSong != null
                    updateFavoriteIcon(isCurrentFavorite)
                }
            }

            // 🌟 核心修复：如果发现播放器里是空的（说明刚杀后台重进），自动把整个队列加载进去！
            val controller = mediaController
            if (controller != null && controller.mediaItemCount == 0) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val savedProgress = AppDatabase.getInstance(requireContext()).playProgressDao().getProgressByUrl(currentSong.url)
                    val startPosition = savedProgress?.currentPosition ?: 0L

                    requireActivity().runOnUiThread {
                        // 把 QueueManager 里的所有歌打包成 MediaItems
                        val mediaItems = QueueManager.queue.map {
                            MediaItem.Builder()
                                .setUri(it.url)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(it.name)
                                        .setArtist(it.artist)
                                        .setArtworkUri(android.net.Uri.parse(it.pic))
                                        .build()
                                ).build()
                        }

                        // 找到当前这首歌在队列里的索引（防止错位）
                        val index = QueueManager.queue.indexOfFirst { it.url == currentSong.url }
                        val safeIndex = if (index != -1) index else 0

                        // 一次性把整个队列塞进去，并定位到当前歌曲的进度！
                        controller.setMediaItems(mediaItems, safeIndex, startPosition)
                        controller.prepare()

                        // 手动更新一下进度条文字，让它立刻显示出来
                        binding.tvTimeCurrent.text = formatTime(startPosition)
                        if (controller.duration > 0) {
                            binding.tvTimeTotal.text = formatTime(controller.duration)
                            binding.seekBarDetail.max = controller.duration.toInt()
                            binding.seekBarDetail.progress = startPosition.toInt()
                        }
                    }
                }
            }
        }
    }

    private fun updateFavoriteIcon(isFav: Boolean) {
        binding.btnFavorite.setColorFilter(Color.parseColor(if (isFav) "#E53935" else "#888888"))
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "00:00"
        return String.format("%02d:%02d", (millis / (1000 * 60)) % 60, (millis / 1000) % 60)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}