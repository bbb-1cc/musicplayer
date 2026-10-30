package com.example.musicplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 加载包含 NavHostFragment 的布局
        setContentView(R.layout.activity_main)
    }
}