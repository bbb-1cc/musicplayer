package com.example.musicplayer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    // 播放器
    private var exoPlayer: ExoPlayer? = null

    // 权限申请回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(requireContext(), "权限已授予，正在联网拉取...", Toast.LENGTH_SHORT).show()
            fetchSongData()
        } else {
            Toast.makeText(requireContext(), "您拒绝了权限，已为您加载本地歌单", Toast.LENGTH_LONG).show()
            showLocalFallbackData("权限拒绝降级")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 初始化 ExoPlayer
        exoPlayer = ExoPlayer.Builder(requireContext()).build()

        // 添加错误监听器
        exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                super.onPlayerError(error)
                android.util.Log.e("PlayerError", "播放失败原因: ${error.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "播放失败: ${error.cause?.message}", Toast.LENGTH_LONG).show()
                }
            }
        })

        // 2. 按钮跳转
        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }

        // 3. 播放/暂停按钮逻辑
        binding.btnPlayPause.setOnClickListener {
            val player = exoPlayer ?: return@setOnClickListener

            if (player.isPlaying) {
                player.pause()
                Toast.makeText(requireContext(), "已暂停", Toast.LENGTH_SHORT).show()
            } else {
                if (player.mediaItemCount == 0) {
                    val currentSong = QueueManager.getCurrentSong()
                    if (currentSong != null) {

                        // 🌟 核心修改：判断是本地还是网络
                        val mediaItem = if (currentSong.rawResId != null) {
                            // 本地资源：用 Android 原生资源路径解析
                            val uri = android.net.Uri.parse("android.resource://${requireContext().packageName}/${currentSong.rawResId}")
                            MediaItem.fromUri(uri)
                        } else {
                            // 网络资源：直接用 url
                            MediaItem.fromUri(currentSong.url)
                        }

                        player.setMediaItem(mediaItem)
                        player.prepare()
                    } else {
                        Toast.makeText(requireContext(), "暂无歌曲", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                player.play()
                Toast.makeText(requireContext(), "正在播放", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. 开始检查权限并请求网络
        checkAndRequestPermission()
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

                                // 预先准备好播放器数据
                                val mediaItem = MediaItem.fromUri(songData.url)
                                exoPlayer?.setMediaItem(mediaItem)
                                exoPlayer?.prepare()
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
                android.util.Log.e("NetworkTest", "网络错误：${t.message}")
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

            // 🌟 核心新增：把本地 5 首假歌塞进队列管理器
            QueueManager.setQueue(LocalDataProvider.mockSongList, 0)

            // 🌟 核心新增：把第一首假歌喂给播放器准备
            val firstLocalSong = QueueManager.getCurrentSong()
            if (firstLocalSong != null) {
                val mediaItem = MediaItem.fromUri(firstLocalSong.url)
                exoPlayer?.setMediaItem(mediaItem)
                exoPlayer?.prepare()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        exoPlayer?.release()
        exoPlayer = null
        _binding = null
    }
}