package com.example.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import coil.load
import com.example.musicplayer.databinding.FragmentPlayerDetailBinding

class PlayerDetailFragment : Fragment() {

    private var _binding: FragmentPlayerDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 从队列管理器里拿到当前正在播放的歌
        val currentSong = QueueManager.getCurrentSong()

        // 2. 把数据绑到界面上
        currentSong?.let { song ->
            binding.tvDetailSongName.text = song.name
            binding.tvDetailArtist.text = song.artist

            // 使用 Coil 加载网络图片
            if (song.pic.isNotEmpty()) {
                // 🌟 放弃拼参数，直接加载原图
                binding.ivCover.load(song.pic) {
                    crossfade(true)
                    placeholder(android.R.drawable.ic_menu_gallery)
                    error(android.R.drawable.ic_menu_report_image)
                }
            }
        }

        // 3. 返回按钮逻辑
        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}