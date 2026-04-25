package com.bili.tv.bili_tv_app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.bili.tv.bili_tv_app.util.PreferencesManager

/**
 * 启动页面
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏模式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_splash)

        // 初始化偏好设置
        PreferencesManager.init(this)

        // 延迟跳转到主页面
        window.decorView.postDelayed({
            startActivity(android.content.Intent(this, MainActivity::class.java))
            finish()
        }, 2000)
    }
}