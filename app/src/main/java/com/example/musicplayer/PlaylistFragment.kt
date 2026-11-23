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
import androidx.fragment.app.viewModels
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

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    // 🌟 使用委托获取 ViewModel
    private val viewModel: PlaylistViewModel by viewModels()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var displayList = mutableListOf<SongData>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.fetchPlaylist()
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

        setupMediaController()
        setupListeners()

        // 🌟 观察 ViewModel 的数据变化
        observeViewModel()
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())
    }

    private fun setupListeners() {
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
        binding.btnRetry.setOnClickListener {
            checkAndRequestPermission()
        }
        checkAndRequestPermission()
    }

    // 🌟 核心重构：监听 ViewModel 的状态，自动切换 UI
    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) showLoadingUI()
        }

        viewModel.songList.observe(viewLifecycleOwner) { songs ->
            if (!songs.isNullOrEmpty()) {
                displayList.clear()
                displayList.addAll(songs)
                showListUI()
            }
        }

        viewModel.errorMsg.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                showErrorUI(error)
            }
        }
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                viewModel.fetchPlaylist()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.fetchPlaylist()
        }
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

        // 🌟 核心修改：为每首歌构建完整的元数据
        val mediaItems = displayList.map {
            MediaItem.Builder()
                .setUri(it.url)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(it.name)
                        .setArtist(it.artist)
                        .setArtworkUri(android.net.Uri.parse(it.pic)) // 封面图网址
                        .build()
                )
                .build()
        }
        controller.setMediaItems(mediaItems, position, 0L)
        controller.prepare()
        controller.play()

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

    // ================= 状态切换 =================
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

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}