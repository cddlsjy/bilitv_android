package com.bili.tv.bili_tv_app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bili.tv.bili_tv_app.R

/**
 * 设置页面
 */
class SettingsFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSettingsList(view)
    }

    private fun setupSettingsList(view: View) {
        // 设置项列表
        val settingsList = listOf(
            SettingsItem("播放设置", "视频播放相关配置", R.drawable.ic_settings),
            SettingsItem("界面设置", "界面显示相关配置", R.drawable.ic_settings),
            SettingsItem("关于", "应用信息和更新", R.drawable.ic_info)
        )

        // 为每个设置项添加点击事件
        view.findViewById<View>(R.id.settingPlayback)?.setOnClickListener {
            // 打开播放设置
        }

        view.findViewById<View>(R.id.settingInterface)?.setOnClickListener {
            // 打开界面设置
        }

        view.findViewById<View>(R.id.settingAbout)?.setOnClickListener {
            // 打开关于页面
        }
    }
}

data class SettingsItem(
    val title: String,
    val description: String,
    val iconRes: Int
)