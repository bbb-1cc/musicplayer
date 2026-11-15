package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // 🌟 用于定时刷新进度条
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false // 标记用户是否正在拖动进度条

    private val updateProgressRunnable = object : Runnable {
        override fun run() {
            mediaController?.let { controller ->
                if (controller.isConnected && !isUserSeeking) {
                    val currentPos = controller.currentPosition
                    val duration = controller.duration

                    if (duration > 0) {
                        binding.seekBar.max = duration.toInt()
                        binding.seekBar.progress = currentPos.toInt()
                        binding.tvTime.text = "${formatTime(currentPos)} / ${formatTime(duration)}"
                    }
                }
            }
            // 每 500 毫秒刷新一次
            handler.postDelayed(this, 500)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchSongData()
        } else {
            showLocalFallbackData("权限拒绝降级")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 连接后台服务
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            // 🌟 连接成功后，开始循环刷新进度条
            handler.post(updateProgressRunnable)
        }, MoreExecutors.directExecutor())

        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }

        // 拖动进度条监听
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvTime.text = "${formatTime(progress.toLong())} / ${formatTime(mediaController?.duration ?: 0L)}"
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                mediaController?.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })

        binding.btnPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener

            if (controller.isPlaying) {
                controller.pause()
                Toast.makeText(requireContext(), "已暂停", Toast.LENGTH_SHORT).show()
            } else {
                if (controller.mediaItemCount == 0) {
                    val currentSong = QueueManager.getCurrentSong()
                    if (currentSong != null) {
                        val mediaItem = MediaItem.fromUri(currentSong.url)
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                    } else {
                        Toast.makeText(requireContext(), "暂无歌曲", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                controller.play()
                Toast.makeText(requireContext(), "正在播放", Toast.LENGTH_SHORT).show()
            }
        }

        checkAndRequestPermission()
    }

    // 格式化时间（毫秒 -> mm:ss）
    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                fetchSongData()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            fetchSongData()
        }
    }

    private fun fetchSongData() {
        RetrofitClient.apiService.getRandomSong().enqueue(object : Callback<List<SongData>> {
            override fun onResponse(call: Call<List<SongData>>, response: Response<List<SongData>>) {
                if (response.isSuccessful) {
                    val songList = response.body()
                    if (!songList.isNullOrEmpty()) {
                        QueueManager.setQueue(songList, 0)
                        val songData = QueueManager.getCurrentSong()
                        if (songData != null) {
                            requireActivity().runOnUiThread {
                                binding.tvTitle.text = "${songData.name} - ${songData.artist}"
                                SpUtils.saveCurrentSong(requireContext(), songData.name, songData.artist, songData.pic)
                                SpUtils.savePlayState(requireContext(), true)
                            }

                            // 🌟 核心新增：去数据库查询这首歌上次的进度
                            lifecycleScope.launch(Dispatchers.IO) {
                                val savedProgress = AppDatabase.getInstance(requireContext())
                                    .playProgressDao()
                                    .getProgressByUrl(songData.url)

                                if (savedProgress != null && savedProgress.currentPosition > 0) {
                                    // 如果查到了进度，切回主线程设置给播放器
                                    requireActivity().runOnUiThread {
                                        val controller = mediaController
                                        if (controller != null && controller.isConnected) {
                                            // 把这首歌塞进播放器，并直接跳转到上次的进度！
                                            val mediaItem = MediaItem.fromUri(songData.url)
                                            controller.setMediaItem(mediaItem)
                                            controller.prepare()
                                            controller.seekTo(savedProgress.currentPosition)

                                            Toast.makeText(requireContext(), "已恢复上次播放进度", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        showLocalFallbackData("服务器返回空数据降级")
                    }
                } else {
                    showLocalFallbackData("服务器错误降级")
                }
            }

            override fun onFailure(call: Call<List<SongData>>, t: Throwable) {
                showLocalFallbackData("网络异常降级")
            }
        })
    }

    private fun showLocalFallbackData(reason: String) {
        requireActivity().runOnUiThread {
            binding.tvTitle.text = reason
            val stringBuilder = StringBuilder("本地兜底歌单：\n")
            for (song in LocalDataProvider.mockSongList) {
                stringBuilder.append("🎵 ${song.name} - ${song.artist}\n")
            }
            binding.tvFallbackList.text = stringBuilder.toString()

            QueueManager.setQueue(LocalDataProvider.mockSongList, 0)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 🌟 移除进度条刷新和释放控制器
        handler.removeCallbacks(updateProgressRunnable)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}