package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    // 🌟 关键修改：不再声明 ExoPlayer，而是声明 MediaController 用来连接 Service
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

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

        // 🌟 初始化 MediaController 连接到我们的 MusicService
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            Toast.makeText(requireContext(), "后台播放服务已连接", Toast.LENGTH_SHORT).show()
        }, MoreExecutors.directExecutor())

        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }

        // 播放/暂停按钮逻辑（通过 MediaController 控制后台 Service）
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
        // 🌟 释放 Controller 连接
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}