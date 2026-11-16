package com.example.musicplayer

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.load
import com.example.musicplayer.databinding.FragmentPlayerDetailBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlayerDetailFragment : Fragment() {

    private var _binding: FragmentPlayerDetailBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // 定时刷新进度条
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            val controller = mediaController
            if (controller != null && controller.isConnected && !isUserSeeking) {
                try {
                    val currentPos = controller.currentPosition
                    val duration = controller.duration

                    // 只有时长有效才更新 UI
                    if (duration > 0 && duration != C.TIME_UNSET) {
                        binding.seekBarDetail.max = duration.toInt()
                        binding.seekBarDetail.progress = currentPos.toInt()
                        binding.tvTimeCurrent.text = formatTime(currentPos)
                        binding.tvTimeTotal.text = formatTime(duration)
                    }

                    // 动态切换红色的播放/暂停按钮图标
                    if (controller.isPlaying) {
                        binding.btnPlayDetail.setImageResource(android.R.drawable.ic_media_pause)
                    } else {
                        binding.btnPlayDetail.setImageResource(android.R.drawable.ic_media_play)
                    }
                } catch (e: Exception) {}
            }
            // 每 500 毫秒刷新一次
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

        // 1. 先展示当前歌曲的静态信息（防止刚进来白屏）
        updateSongUI()

        // 2. 连接后台播放服务
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()

            // 🌟 核心监听：如果在别的地方切歌了，详情页 UI 也会跟着自动变
            mediaController?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    updateSongUI()
                }
            })

            // 连接成功后，开始疯狂刷新进度条
            handler.post(updateProgressRunnable)
        }, MoreExecutors.directExecutor())

        // 3. 返回按钮
        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 4. 大播放/暂停按钮
        binding.btnPlayDetail.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            if (controller.isPlaying) controller.pause() else controller.play()
        }

        // 5. 上一首
        binding.btnPrev.setOnClickListener {
            val prevSong = QueueManager.getPreviousSong()
            if (prevSong != null) {
                playNewSong(prevSong)
            } else {
                Toast.makeText(requireContext(), "已经是第一首了", Toast.LENGTH_SHORT).show()
            }
        }

        // 6. 下一首
        binding.btnNext.setOnClickListener {
            val nextSong = QueueManager.getNextSong()
            if (nextSong != null) {
                playNewSong(nextSong)
            } else {
                Toast.makeText(requireContext(), "已经是最后一首了", Toast.LENGTH_SHORT).show()
            }
        }

        // 7. 拖动进度条跳转
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

    // 刷新页面显示的歌名、歌手、封面
    // 刷新当前页面显示的歌名、封面
    // 刷新当前页面显示的歌名、封面
    private fun updateSongUI() {
        val currentSong = QueueManager.getCurrentSong()
        currentSong?.let { song ->
            binding.tvDetailSongName.text = song.name
            binding.tvDetailArtist.text = song.artist

            // 🌟 改这里：不再用网络图兜底，用系统本地图标
            binding.ivCover.load(song.pic) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery) // 加载中显示的本地图
                error(android.R.drawable.ic_menu_report_image)  // 加载失败显示的本地图
            }
        }
    }

    // 切歌执行逻辑
    private fun playNewSong(song: SongData) {
        binding.tvDetailSongName.text = song.name
        binding.tvDetailArtist.text = song.artist

        // 🌟 改这里：同样用本地系统图标兜底
        binding.ivCover.load(song.pic) {
            crossfade(true)
            error(android.R.drawable.ic_menu_report_image)
        }

        val controller = mediaController
        if (controller != null) {
            val mediaItem = MediaItem.fromUri(song.url)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }
    // 格式化时间（毫秒 -> 00:00）
    private fun formatTime(millis: Long): String {
        if (millis <= 0) return "00:00"
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 🌟 极其重要：清理 Handler，释放 Controller，防内存泄漏
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }

}