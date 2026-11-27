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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlaylistViewModel by viewModels()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var displayList = mutableListOf<SongData>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) viewModel.fetchPlaylist() else showErrorUI("权限被拒绝")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMediaController()
        setupListeners()
        observeViewModel()
    }

    private fun setupMediaController() {
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { updateMiniPlayerUI() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { updateMiniPlayerUI() }
            })
            restoreLastSongFromSp()
        }, MoreExecutors.directExecutor())
    }

    private fun restoreLastSongFromSp() {
        val lastSongUrl = SpUtils.getSongUrl(requireContext())
        if (lastSongUrl.isNotEmpty()) {
            if (QueueManager.queue.isEmpty()) {
                val lastSong = SongData(SpUtils.getSongName(requireContext()), SpUtils.getArtist(requireContext()), lastSongUrl, SpUtils.getPicUrl(requireContext()))
                QueueManager.setQueue(listOf(lastSong), 0)
            }
            updateMiniPlayerUI()
        }
    }

    private fun updateMiniPlayerUI() {
        val controller = mediaController
        val currentSong = QueueManager.getCurrentSong()

        if (currentSong != null) {
            binding.cardMiniPlayer.visibility = View.VISIBLE
            binding.tvMiniSongName.text = currentSong.name
            binding.tvMiniArtist.text = currentSong.artist
            if (currentSong.pic.isNotEmpty()) binding.ivMiniCover.load(currentSong.pic) { crossfade(true) }
            else binding.ivMiniCover.setImageResource(android.R.drawable.ic_menu_gallery)

            val isPlaying = controller?.isPlaying ?: false
            binding.btnMiniPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        } else {
            binding.cardMiniPlayer.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        binding.ivGoSettings.setOnClickListener { findNavController().navigate(R.id.settingsFragment) }
        binding.ivGoFavorite.setOnClickListener { findNavController().navigate(R.id.action_playlist_to_favorite) }
        binding.btnRetry.setOnClickListener { checkAndRequestPermission() }
        binding.cardMiniPlayer.setOnClickListener { findNavController().navigate(R.id.action_playlist_to_detail) }

        binding.btnMiniPlayPause.setOnClickListener {
            val controller = mediaController ?: return@setOnClickListener
            val currentSong = QueueManager.getCurrentSong() ?: return@setOnClickListener

            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.mediaItemCount == 0) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        val savedProgress = AppDatabase.getInstance(requireContext()).playProgressDao().getProgressByUrl(currentSong.url)
                        val startPosition = savedProgress?.currentPosition ?: 0L
                        requireActivity().runOnUiThread {
                            val mediaItem = MediaItem.Builder().setUri(currentSong.url).setMediaMetadata(
                                androidx.media3.common.MediaMetadata.Builder().setTitle(currentSong.name).setArtist(currentSong.artist).setArtworkUri(android.net.Uri.parse(currentSong.pic)).build()
                            ).build()
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
        checkAndRequestPermission()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { if (it) showLoadingUI() }
        viewModel.songList.observe(viewLifecycleOwner) { songs ->
            if (!songs.isNullOrEmpty()) {
                displayList.clear()
                displayList.addAll(songs)
                showListUI()
            }
        }
        viewModel.errorMsg.observe(viewLifecycleOwner) { if (!it.isNullOrEmpty()) showErrorUI(it) }
    }

    private fun checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) viewModel.fetchPlaylist()
            else requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else viewModel.fetchPlaylist()
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = SongAdapter(displayList) { position -> playSong(position) }
    }

    private fun playSong(position: Int) {
        val controller = mediaController
        if (controller == null || !controller.isConnected) return

        val clickedSong = displayList[position]
        val currentPlayingUrl = controller.currentMediaItem?.localConfiguration?.uri?.toString()

        if (currentPlayingUrl == clickedSong.url) {
            findNavController().navigate(R.id.action_playlist_to_detail)
            return
        }

        QueueManager.setQueue(displayList, position)
        val song = QueueManager.getCurrentSong() ?: return

        SpUtils.saveCurrentSong(requireContext(), song.name, song.artist, song.pic, song.url)
        SpUtils.savePlayState(requireContext(), true)

        val mediaItems = displayList.map {
            MediaItem.Builder().setUri(it.url).setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder().setTitle(it.name).setArtist(it.artist).setArtworkUri(android.net.Uri.parse(it.pic)).build()
            ).build()
        }

        requireActivity().lifecycleScope.launch(Dispatchers.IO) {
            val savedProgress = AppDatabase.getInstance(requireContext()).playProgressDao().getProgressByUrl(song.url)
            val startPosition = savedProgress?.currentPosition ?: 0L

            requireActivity().runOnUiThread {
                controller.setMediaItems(mediaItems, position, startPosition)
                controller.prepare()
                controller.play()
                if (startPosition > 0) Toast.makeText(requireContext(), "已恢复上次进度: ${startPosition/1000}秒", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_playlist_to_detail)
            }
        }
    }

    private fun showLoadingUI() { binding.progressBar.visibility = View.VISIBLE; binding.recyclerView.visibility = View.GONE; binding.layoutError.visibility = View.GONE }
    private fun showListUI() { binding.progressBar.visibility = View.GONE; binding.recyclerView.visibility = View.VISIBLE; binding.layoutError.visibility = View.GONE; setupRecyclerView() }
    private fun showErrorUI(msg: String) { binding.progressBar.visibility = View.GONE; binding.recyclerView.visibility = View.GONE; binding.layoutError.visibility = View.VISIBLE }

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}