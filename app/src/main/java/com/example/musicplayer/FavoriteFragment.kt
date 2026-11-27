package com.example.musicplayer

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.FragmentFavoriteBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FavoriteFragment : Fragment() {

    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var favoriteSongs = mutableListOf<SongData>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 连接播放器
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

        binding.ivFavBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        loadFavorites()
    }

    private fun loadFavorites() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 去数据库捞收藏
            val entities = AppDatabase.getInstance(requireContext()).songDao().getAllFavorites()

            // 把 Entity 转换成播放器能用的 SongData
            favoriteSongs = entities.map {
                SongData(it.name, it.artist, it.url, it.pic)
            }.toMutableList()

            requireActivity().runOnUiThread {
                if (favoriteSongs.isEmpty()) {
                    binding.tvFavEmpty.visibility = View.VISIBLE
                    binding.recyclerViewFav.visibility = View.GONE
                } else {
                    binding.tvFavEmpty.visibility = View.GONE
                    binding.recyclerViewFav.visibility = View.VISIBLE
                    setupRecyclerView()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        // 复用之前的 SongAdapter
        val adapter = SongAdapter(favoriteSongs) { position ->
            playFavoriteSong(position)
        }
        binding.recyclerViewFav.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFav.adapter = adapter
    }

    private fun playFavoriteSong(position: Int) {
        val controller = mediaController
        if (controller == null || !controller.isConnected) {
            Toast.makeText(requireContext(), "播放器连接中...", Toast.LENGTH_SHORT).show()
            return
        }

        val clickedSong = favoriteSongs[position]

        // 🌟 核心修复：判断当前点击的这首歌，是不是播放器正在播的那首
        val currentPlayingUrl = controller.currentMediaItem?.localConfiguration?.uri?.toString()

        if (currentPlayingUrl == clickedSong.url) {
            // 如果点的是同一首歌，直接去详情页，绝对不打断音乐！
            findNavController().navigate(R.id.action_favorite_to_detail)
            return // 直接结束，不执行下面的重播代码
        }

        // ================= 以下为正常切歌逻辑 =================
        QueueManager.setQueue(favoriteSongs, position)
        val song = QueueManager.getCurrentSong() ?: return

        SpUtils.saveCurrentSong(requireContext(), song.name, song.artist, song.pic)
        SpUtils.savePlayState(requireContext(), true)

        val mediaItems = favoriteSongs.map {
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

        controller.setMediaItems(mediaItems, position, 0L)
        controller.prepare()
        controller.play()

        // 跳转到详情页
        findNavController().navigate(R.id.action_favorite_to_detail)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}