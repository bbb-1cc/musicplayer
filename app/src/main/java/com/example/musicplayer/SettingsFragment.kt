package com.example.musicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.musicplayer.databinding.FragmentSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 返回按钮
        binding.ivSettingsBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        // 发送定时广播的方法
        fun sendTimerBroadcast(action: String, minutes: Int = 0) {
            val intent = Intent(action).apply {
                setPackage(requireContext().packageName)
                putExtra("MINUTES", minutes)
            }
            requireContext().sendBroadcast(intent)
            if (minutes > 0) {
                Toast.makeText(requireContext(), "已设置 ${minutes} 分钟后停止播放", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "已取消定时", Toast.LENGTH_SHORT).show()
            }
        }

        // 睡眠定时器按钮
        binding.btnTimer15.setOnClickListener { sendTimerBroadcast("ACTION_SET_SLEEP_TIMER", 15) }
        binding.btnTimer30.setOnClickListener { sendTimerBroadcast("ACTION_SET_SLEEP_TIMER", 30) }
        binding.btnTimerCancel.setOnClickListener { sendTimerBroadcast("ACTION_CANCEL_SLEEP_TIMER") }

        // 🌟 清空数据库播放进度
        binding.btnClearProgress.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 获取数据库实例，调用 Dao 清空数据
                    AppDatabase.getInstance(requireContext()).playProgressDao().clearAllProgress()

                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "已清空所有歌曲的播放进度", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "清理失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}