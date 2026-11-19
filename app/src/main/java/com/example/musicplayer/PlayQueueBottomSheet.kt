package com.example.musicplayer

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.musicplayer.databinding.FragmentPlayQueueBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class PlayQueueBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentPlayQueueBinding? = null
    private val binding get() = _binding!!

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayQueueBinding.inflate(inflater, container, false)
        // 把右上角的返回图标当成关闭弹窗的按钮
        binding.ivQueueBack.setOnClickListener { dismiss() }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val screenHeight = resources.displayMetrics.heightPixels
            val targetHeight = (screenHeight * 0.38).toInt()

            it.layoutParams.height = targetHeight
            it.requestLayout()

            val behavior = BottomSheetBehavior.from(it)
            behavior.peekHeight = targetHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sessionToken = SessionToken(requireContext(), ComponentName(requireContext(), MusicService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            mediaController = controllerFuture?.get()
        }, MoreExecutors.directExecutor())

        setupList()
    }

    private fun setupList() {
        // 复制一份数据，避免直接修改 QueueManager 的原始列表导致崩溃
        val queueCopy = QueueManager.queue.toMutableList()
        if (queueCopy.isEmpty()) return

        // 🌟 核心修复：先声明，后初始化
        lateinit var adapter: QueueAdapter

        adapter = QueueAdapter(queueCopy,
            onItemClick = { position ->
                mediaController?.let { controller ->
                    QueueManager.setCurrentIndex(position)
                    controller.seekToDefaultPosition(position)
                    controller.play()
                    dismiss()
                }
            },
            onDeleteClick = { position ->
                if (queueCopy.size <= 1) {
                    Toast.makeText(requireContext(), "至少保留一首歌曲", Toast.LENGTH_SHORT).show()
                    return@QueueAdapter
                }

                // 1. 从界面移除
                adapter.removeItem(position)

                // 2. 从本地队列移除
                QueueManager.removeSongAt(position)

                // 3. 从 ExoPlayer 移除
                mediaController?.removeMediaItem(position)
            }
        )

        binding.recyclerViewQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewQueue.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        _binding = null
    }
}