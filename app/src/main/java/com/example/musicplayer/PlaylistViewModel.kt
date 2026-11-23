package com.example.musicplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PlaylistViewModel : ViewModel() {

    // 歌单数据
    private val _songList = MutableLiveData<List<SongData>>()
    val songList: LiveData<List<SongData>> = _songList

    // 加载状态（控制转圈圈）
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // 错误提示（控制重试界面）
    private val _errorMsg = MutableLiveData<String>()
    val errorMsg: LiveData<String> = _errorMsg

    fun fetchPlaylist() {
        _isLoading.value = true
        // 🌟 viewModelScope 会跟随 ViewModel 自动销毁，绝对不泄漏
        viewModelScope.launch {
            try {
                // 直接调用 suspend 函数，不需要 Callback 了！
                val result = RetrofitClient.apiService.getPlaylist()
                if (result.isNotEmpty()) {
                    _songList.value = result
                } else {
                    _errorMsg.value = "暂无数据"
                }
            } catch (e: Exception) {
                _errorMsg.value = e.message ?: "网络异常"
            } finally {
                _isLoading.value = false
            }
        }
    }
}