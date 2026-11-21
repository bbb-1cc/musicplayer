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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var displayList = mutableListOf<SongData>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            fetchSongData()
        } else {
            showErrorUI("权限被拒绝")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 连接后台播放服务
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

        // 顶部按钮
        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }
        binding.ivGoSettings.setOnClickListener {
            try {
                val navController = androidx.navigation.Navigation.findNavController(requireActivity(), R.id.nav_host_fragment)
                navController.navigate(R.id.settingsFragment)
            } catch (e: Exception) {
                findNavController().navigate(R.id.settingsFragment)
            }
        }

        // 🌟 重试按钮点击
        binding.btnRetry.setOnClickListener {
            checkAndRequestPermission()
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

    // 🌟 核心网络请求
    private fun fetchSongData() {
        showLoadingUI() // 1. 先显示转圈圈

        RetrofitClient.apiService.getPlaylist().enqueue(object : Callback<List<SongData>> {
            override fun onResponse(call: Call<List<SongData>>, response: Response<List<SongData>>) {
                if (response.isSuccessful) {
                    val songList = response.body()
                    if (!songList.isNullOrEmpty()) {
                        displayList.clear()
                        displayList.addAll(songList)
                        requireActivity().runOnUiThread {
                            showListUI() // 2. 成功且有数据，显示列表
                        }
                    } else {
                        requireActivity().runOnUiThread { showEmptyUI() } // 3. 成功但没数据，显示空状态
                    }
                } else {
                    requireActivity().runOnUiThread { showErrorUI("服务器错误") } // 4. 失败，显示错误状态
                }
            }

            override fun onFailure(call: Call<List<SongData>>, t: Throwable) {
                requireActivity().runOnUiThread { showErrorUI("网络异常") } // 5. 失败，显示错误状态
            }
        })
    }

    private fun setupRecyclerView() {
        val adapter = SongAdapter(displayList) { position ->
            playSong(position)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    private fun playSong(position: Int) {
        val controller = mediaController
        if (controller == null || !controller.isConnected) {
            Toast.makeText(requireContext(), "播放器连接中...", Toast.LENGTH_SHORT).show()
            return
        }

        QueueManager.setQueue(displayList, position)
        val song = QueueManager.getCurrentSong() ?: return

        SpUtils.saveCurrentSong(requireContext(), song.name, song.artist, song.pic)
        SpUtils.savePlayState(requireContext(), true)

        val mediaItems = displayList.map { MediaItem.fromUri(it.url) }
        controller.setMediaItems(mediaItems, position, 0L)
        controller.prepare()
        controller.play()

        // 🌟 恢复：去数据库查进度并跳转
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val savedProgress = AppDatabase.getInstance(requireContext()).playProgressDao().getProgressByUrl(song.url)
            requireActivity().runOnUiThread {
                if (savedProgress != null && savedProgress.currentPosition > 0) {
                    controller.seekTo(position, savedProgress.currentPosition)
                    Toast.makeText(requireContext(), "已恢复上次进度", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findNavController().navigate(R.id.action_playlist_to_detail)
    }

    // ==========================================
    // 🌟 状态切换魔法
    // ==========================================
    private fun showLoadingUI() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
    }

    private fun showListUI() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.layoutError.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        setupRecyclerView()
    }

    private fun showErrorUI(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.layoutError.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
    }

    private fun showEmptyUI() {
        binding.progressBar.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.layoutError.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}