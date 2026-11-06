package com.example.musicplayer

/**
 * 自定义播放队列管理器（纯业务逻辑模块）
 */
object QueueManager {

    // 内部维护的播放列表
    private val _queue = mutableListOf<SongData>()
    val queue: List<SongData> get() = _queue

    // 当前正在播放的索引
    private var currentIndex = -1

    /**
     * 设置全新的播放队列并指定开始播放的位置
     */
    fun setQueue(songs: List<SongData>, startIndex: Int = 0) {
        _queue.clear()
        _queue.addAll(songs)
        if (_queue.isNotEmpty()) {
            currentIndex = if (startIndex in _queue.indices) startIndex else 0
        }
    }

    /**
     * 获取当前正在播放的歌曲
     */
    fun getCurrentSong(): SongData? {
        return if (currentIndex in _queue.indices) _queue[currentIndex] else null
    }

    /**
     * 获取下一首（单曲循环/列表循环）
     */
    fun getNextSong(): SongData? {
        if (_queue.isEmpty()) return null
        // 如果是最后一首，回到第一首
        currentIndex = (currentIndex + 1) % _queue.size
        return getCurrentSong()
    }

    /**
     * 获取上一首
     */
    fun getPreviousSong(): SongData? {
        if (_queue.isEmpty()) return null
        // 如果是第一首，跳到最后一首
        currentIndex = if (currentIndex - 1 < 0) _queue.size - 1 else currentIndex - 1
        return getCurrentSong()
    }

    /**
     * 向队列尾部添加一首歌
     */
    fun addSong(song: SongData) {
        _queue.add(song)
    }

    /**
     * 从队列中移除指定歌曲
     */
    fun removeSong(song: SongData) {
        val index = _queue.indexOf(song)
        if (index != -1) {
            _queue.removeAt(index)
            // 如果删掉的正好是当前播放的，需要调整索引
            if (index < currentIndex) {
                currentIndex--
            } else if (index == currentIndex) {
                // 如果删的是当前歌，索引不能越界
                if (currentIndex >= _queue.size) {
                    currentIndex = 0
                }
            }
        }
    }

    /**
     * 清空队列
     */
    fun clearQueue() {
        _queue.clear()
        currentIndex = -1
    }
}