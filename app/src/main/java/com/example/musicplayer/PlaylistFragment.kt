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
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // 最终要展示在列表上的数据
    private var displayList = mutableListOf<SongData>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchSongData() // 允许了，去请求网络
        } else {
            showLocalFallbackData() // 拒绝了，走本地降级
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 连接后台播放服务
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

        // 2. 跳转详情页按钮
        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }
        // 🌟 点击齿轮图标去设置页
        binding.ivGoSettings.setOnClickListener {
            // 这里的 R.id.action_playlist_to_settings 需要你在 nav_graph.xml 里连好线
            findNavController().navigate(R.id.action_playlist_to_settings)
        }
        // 3. 检查权限（决定是走网络还是走降级）
        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                fetchSongData() // 已经有权限了，直接请求网络
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            fetchSongData()
        }
    }

    // 🌟 网络请求逻辑
    // 🌟 网络请求逻辑
    private fun fetchSongData() {
        // 注意这里调用的方法改成了 getPlaylist()
        RetrofitClient.apiService.getPlaylist().enqueue(object : Callback<List<SongData>> {
            override fun onResponse(call: Call<List<SongData>>, response: Response<List<SongData>>) {
                if (response.isSuccessful) {
                    val songList = response.body()
                    if (!songList.isNullOrEmpty()) {
                        // 🌟 直接使用网络返回的完整列表！
                        displayList.clear()
                        displayList.addAll(songList)

                        requireActivity().runOnUiThread {
                            setupRecyclerView(displayList)
                        }
                    } else {
                        showLocalFallbackData()
                    }
                } else {
                    showLocalFallbackData()
                }
            }

            override fun onFailure(call: Call<List<SongData>>, t: Throwable) {
                showLocalFallbackData()
            }
        })
    }

    // 🌟 降级兜底逻辑
    private fun showLocalFallbackData() {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), "网络异常，已降级为本地歌单", Toast.LENGTH_SHORT).show()
            displayList.clear()
            displayList.addAll(LocalDataProvider.mockSongList)
            setupRecyclerView(displayList)
        }
    }

    // 🌟 初始化列表
    private fun setupRecyclerView(list: List<SongData>) {
        val adapter = SongAdapter(list) { position ->
            playSong(position)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    // 🌟 点击歌曲播放
    private fun playSong(position: Int) {
        val controller = mediaController
        if (controller == null || !controller.isConnected) {
            Toast.makeText(requireContext(), "播放器连接中，请稍候...", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 更新自定义队列管理器
        QueueManager.setQueue(displayList, position)
        val song = QueueManager.getCurrentSong() ?: return

        // 2. 保存数据给通知栏
        SpUtils.saveCurrentSong(requireContext(), song.name, song.artist, song.pic)
        SpUtils.savePlayState(requireContext(), true)

        Toast.makeText(requireContext(), "正在播放：${song.name}", Toast.LENGTH_SHORT).show()

        // 🌟 3. 核心修改：把整个歌单打包成 MediaItem 列表塞给播放器
        val mediaItems = displayList.map { MediaItem.fromUri(it.url) }
        controller.setMediaItems(mediaItems, position, 0L)
        controller.prepare()
        controller.play()

        // 🌟 4. 监听播放器自动切歌，同步我们的队列
        controller.addListener(object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // 播放器自动切歌了，同步 QueueManager 的索引
                val newIndex = controller.currentMediaItemIndex
                QueueManager.setQueue(displayList, newIndex)

                // 更新 SP
                val newSong = QueueManager.getCurrentSong()
                if (newSong != null) {
                    SpUtils.saveCurrentSong(requireContext(), newSong.name, newSong.artist, newSong.pic)
                }
            }
        })

        // 5. 跳转详情页
        findNavController().navigate(R.id.action_playlist_to_detail)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}