package com.example.musicplayer

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.FragmentPlayQueueBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlayQueueFragment : Fragment() {

    private var _binding: FragmentPlayQueueBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 返回按钮
        binding.ivQueueBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 2. 连接后台播放服务
        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

        // 3. 渲染队列列表
        setupQueueList()
    }

    private fun setupQueueList() {
        // 从 QueueManager 获取当前正在播放的整个队列
        val currentQueue = QueueManager.queue

        if (currentQueue.isEmpty()) {
            Toast.makeText(requireContext(), "当前队列为空", Toast.LENGTH_SHORT).show()
            return
        }

        // 复用之前的 SongAdapter
        val adapter = SongAdapter(currentQueue) { position ->
            // 点击队列里的歌，立刻切歌
            playFromQueue(position)
        }

        binding.recyclerViewQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewQueue.adapter = adapter
    }

    private fun playFromQueue(position: Int) {
        val controller = mediaController
        if (controller == null || !controller.isConnected) {
            Toast.makeText(requireContext(), "播放器未连接", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. 重新设定队列，并把当前索引指到点击的那首
        QueueManager.setQueue(QueueManager.queue, position)
        val song = QueueManager.getCurrentSong() ?: return

        // 2. 更新 SharedPreferences (通知栏和小组件需要)
        SpUtils.saveCurrentSong(requireContext(), song.name, song.artist, song.pic)
        SpUtils.savePlayState(requireContext(), true)

        Toast.makeText(requireContext(), "切换播放：${song.name}", Toast.LENGTH_SHORT).show()

        // 3. 通知后台播放
        val mediaItem = MediaItem.fromUri(song.url)
        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.play()

        // 4. 切完歌自动退回上一页（可选）
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}