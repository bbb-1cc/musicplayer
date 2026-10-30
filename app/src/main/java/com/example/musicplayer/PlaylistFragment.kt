package com.example.musicplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.musicplayer.databinding.FragmentPlaylistBinding
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : Fragment() {

    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 按钮跳转
        binding.btnGoDetail.setOnClickListener {
            findNavController().navigate(R.id.action_playlist_to_detail)
        }

        // 发起网络请求
        fetchSongData()
    }

    private fun fetchSongData() {
        // 注意这里：泛型变成了 List<SongData>，和 ApiService 完美匹配
        RetrofitClient.apiService.getRandomSong().enqueue(object : Callback<List<SongData>> {
            override fun onResponse(call: Call<List<SongData>>, response: Response<List<SongData>>) {
                if (response.isSuccessful) {
                    val songList = response.body()
                    if (!songList.isNullOrEmpty()) {
                        // 拿到列表的第一首歌
                        val songData = songList[0]
                        android.util.Log.d("NetworkTest", "成功获取歌曲：${songData.name} - ${songData.artist}")

                        // 更新到界面上
                        requireActivity().runOnUiThread {
                            binding.tvTitle.text = "${songData.name} - ${songData.artist}"
                        }
                    } else {
                        android.util.Log.e("NetworkTest", "请求成功，但没有数据")
                    }
                } else {
                    android.util.Log.e("NetworkTest", "请求失败，状态码：${response.code()}")
                }
            }

            override fun onFailure(call: Call<List<SongData>>, t: Throwable) {
                android.util.Log.e("NetworkTest", "网络错误：${t.message}")
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}