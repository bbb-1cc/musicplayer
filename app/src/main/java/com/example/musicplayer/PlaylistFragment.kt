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
import androidx.navigation.fragment.findNavController
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    // 1. 注册动态权限申请回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 用户同意了权限
            Toast.makeText(requireContext(), "权限已授予，正在联网拉取...", Toast.LENGTH_SHORT).show()
            fetchSongData()
        } else {
            // 用户拒绝了权限，执行降级方案
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

        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }

        // 2. 开始检查并申请权限
        checkAndRequestPermission()
    }

    private fun checkAndRequestPermission() {
        // 只有 Android 13 及以上才需要动态申请通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                // 已经有权限，直接请求网络
                fetchSongData()
            } else {
                // 没有权限，弹窗动态申请
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 13 以下直接请求网络
            fetchSongData()
        }
    }

    private fun fetchSongData() {
        RetrofitClient.apiService.getRandomSong().enqueue(object : Callback<List<SongData>> {
            override fun onResponse(call: Call<List<SongData>>, response: Response<List<SongData>>) {
                if (response.isSuccessful) {
                    val songList = response.body()
                    if (!songList.isNullOrEmpty()) {
                        val songData = songList[0]
                        requireActivity().runOnUiThread {
                            binding.tvTitle.text = "${songData.name} - ${songData.artist}"
                        }
                    } else {
                        showLocalFallbackData("服务器返回空数据降级")
                    }
                } else {
                    showLocalFallbackData("服务器错误降级")
                }
            }

            override fun onFailure(call: Call<List<SongData>>, t: Throwable) {
                // 网络失败，降级！
                showLocalFallbackData("网络异常降级")
            }
        })
    }

    // 3. 核心降级方案：展示本地假数据
    private fun showLocalFallbackData(reason: String) {
        requireActivity().runOnUiThread {
            binding.tvTitle.text = reason
            // 将本地的 5 首假歌拼接到界面上展示
            val stringBuilder = StringBuilder("本地兜底歌单：\n")
            for (song in LocalDataProvider.mockSongList) {
                stringBuilder.append("🎵 ${song.name} - ${song.artist}\n")
            }
            binding.tvFallbackList.text = stringBuilder.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}