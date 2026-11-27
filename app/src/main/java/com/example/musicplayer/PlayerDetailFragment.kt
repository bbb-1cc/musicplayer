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

    // 🌟 记录当前这首歌是否已被收藏
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
                    if (controller.isPlaying) {
                        binding.btnPlayDetail.setImageResource(android.R.drawable.ic_media_pause)
                    } else {
                        binding.btnPlayDetail.setImageResource(android.R.drawable.ic_media_play)
                    }
                } catch (e: Exception) {}
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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
                    val realIndex = mediaController?.currentMediaItemIndex ?: 0
                    QueueManager.setCurrentIndex(realIndex)
                    updateUIFromPlayer()
                }
            })

            updateUIFromPlayer()
            handler.post(updateProgressRunnable)
        }, MoreExecutors.directExecutor())

        binding.ivBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        binding.btnQueue.setOnClickListener {
            val bottomSheet = PlayQueueBottomSheet()
            bottomSheet.show(childFragmentManager, "PlayQueue")
        }

        // 🌟 核心：爱心点击事件
        binding.btnFavorite.setOnClickListener {
            val currentSong = QueueManager.getCurrentSong() ?: return@setOnClickListener

            lifecycleScope.launch(Dispatchers.IO) {
                if (isCurrentFavorite) {
                    // 已经收藏了，点击就取消
                    AppDatabase.getInstance(requireContext()).songDao().removeFavorite(currentSong.url)
                } else {
                    // 没收藏，点击就存入数据库
                    val songEntity = SongEntity(
                        url = currentSong.url,
                        name = currentSong.name,
                        artist = currentSong.artist,
                        pic = currentSong.pic,
                        isFavorite = true
                    )
                    AppDatabase.getInstance(requireContext()).songDao().insertSong(songEntity)
                }

                // 切回主线程更新 UI 状态
                requireActivity().runOnUiThread {
                    isCurrentFavorite = !isCurrentFavorite
                    updateFavoriteIcon(isCurrentFavorite)
                    val msg = if (isCurrentFavorite) "已加入收藏" else "已取消收藏"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnPlayDetail.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
        }

        binding.btnPrev.setOnClickListener { mediaController?.seekToPreviousMediaItem() }
        binding.btnNext.setOnClickListener { mediaController?.seekToNextMediaItem() }

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

    // 🌟 更新界面信息，并去数据库查询当前歌曲的收藏状态
    private fun updateUIFromPlayer() {
        val currentSong = QueueManager.getCurrentSong()
        if (currentSong != null) {
            binding.tvDetailSongName.text = currentSong.name
            binding.tvDetailArtist.text = currentSong.artist
            if (currentSong.pic.isNotEmpty()) {
                binding.ivCover.load(currentSong.pic) {
                    crossfade(true)
                    error(android.R.drawable.ic_menu_report_image)
                }
            } else {
                binding.ivCover.setImageResource(android.R.drawable.ic_menu_report_image)
            }

            // 开启协程查数据库
            lifecycleScope.launch(Dispatchers.IO) {
                val favoriteSong = AppDatabase.getInstance(requireContext()).songDao().getSongByUrl(currentSong.url)
                val isFav = favoriteSong != null
                requireActivity().runOnUiThread {
                    isCurrentFavorite = isFav
                    updateFavoriteIcon(isFav)
                }
            }
        }
    }

    // 🌟 改变爱心颜色
    private fun updateFavoriteIcon(isFav: Boolean) {
        if (isFav) {
            // 已收藏，变成亮红色
            binding.btnFavorite.setColorFilter(Color.parseColor("#E53935"))
        } else {
            // 未收藏，变成灰色
            binding.btnFavorite.setColorFilter(Color.parseColor("#888888"))
        }
    }

    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "00:00"
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}