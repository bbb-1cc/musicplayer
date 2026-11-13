package com.example.musicplayer

object LocalDataProvider {
    val mockSongList = listOf(
        // 🌟 第一首用本地资源 ID
        SongData(
            name = "我不难过",
            artist = "孙燕姿",
            url = "",
            pic = "",
            rawResId = R.raw.local_music
        ),//有bug，播放不了，不知道怎么修改
        // 后面四首依然用网络链接
        SongData("稻香", "周杰伦", "https://api.injahow.cn/meting/?type=song&id=185809", ""),
        SongData("告白气球", "周杰伦", "https://api.injahow.cn/meting/?type=song&id=418603077", ""),
        SongData("夜曲", "周杰伦", "https://api.injahow.cn/meting/?type=song&id=185811", ""),
        SongData("七里香", "周杰伦", "https://api.injahow.cn/meting/?type=song&id=185809", "")
    )
}