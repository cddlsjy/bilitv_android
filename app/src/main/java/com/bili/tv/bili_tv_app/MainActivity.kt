package com.bili.tv.bili_tv_app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.bili.tv.bili_tv_app.databinding.ActivityMainBinding
import com.bili.tv.bili_tv_app.ui.home.HomeFragment
import com.bili.tv.bili_tv_app.ui.player.PlayerActivity
import com.bili.tv.bili_tv_app.ui.search.SearchFragment
import com.bili.tv.bili_tv_app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // 底部导航索引
    private var currentTabIndex = 0
    private val fragments = mutableMapOf<Int, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏模式 - 隐藏状态栏和导航栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFullScreen()
        setupBottomNavigation()
        setupTouchNavigation()

        // 默认显示首页
        if (savedInstanceState == null) {
            showFragment(0)
        }
    }

    private fun setupFullScreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(0)
                    currentTabIndex = 0
                    true
                }
                R.id.nav_hot -> {
                    showFragment(1)
                    currentTabIndex = 1
                    true
                }
                R.id.nav_search -> {
                    showFragment(2)
                    currentTabIndex = 2
                    true
                }
                R.id.nav_settings -> {
                    showFragment(3)
                    currentTabIndex = 3
                    true
                }
                else -> false
            }
        }
    }

    // 支持触摸屏点击导航
    private fun setupTouchNavigation() {
        binding.bottomNav.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                binding.bottomNav.performClick()
            }
            true
        }

        // 让底部导航支持触摸点击
        for (i in 0 until binding.bottomNav.childCount) {
            binding.bottomNav.getChildAt(i).setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performClick()
                }
                true
            }
        }
    }

    private fun showFragment(index: Int) {
        val transaction = supportFragmentManager.beginTransaction()

        // 隐藏所有已添加的fragment
        fragments.values.forEach { transaction.hide(it) }

        val fragment = fragments.getOrPut(index) {
            createFragment(index)
        }
        transaction.show(fragment)
        transaction.commit()
    }

    private fun createFragment(index: Int): Fragment {
        return when (index) {
            0 -> HomeFragment()
            1 -> HomeFragment() // 热门
            2 -> SearchFragment()
            3 -> SettingsFragment()
            else -> HomeFragment()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 遥控器方向键导航
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                // 如果在首页顶部，焦点移动到底部导航
                if (currentTabIndex == 0) {
                    binding.bottomNav.requestFocus()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // 如果焦点在底部导航，移动到首页内容
                if (currentTabIndex == 0) {
                    // 触发首页内容区域获取焦点
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (currentTabIndex > 0) {
                    currentTabIndex--
                    binding.bottomNav.menu.getItem(currentTabIndex + 1).isChecked = false
                    binding.bottomNav.menu.getItem(currentTabIndex).isChecked = true
                    showFragment(currentTabIndex)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (currentTabIndex < 3) {
                    currentTabIndex++
                    binding.bottomNav.menu.getItem(currentTabIndex - 1).isChecked = false
                    binding.bottomNav.menu.getItem(currentTabIndex).isChecked = true
                    showFragment(currentTabIndex)
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (binding.bottomNav.hasFocus()) {
                    binding.bottomNav.selectedItemId = binding.bottomNav.menu.getItem(currentTabIndex).itemId
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // 触摸滑动切换Tab
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            when (it.action) {
                MotionEvent.ACTION_DOWN -> touchStartX = it.x
                MotionEvent.ACTION_UP -> {
                    val deltaX = it.x - touchStartX
                    if (kotlin.math.abs(deltaX) > SWIPE_THRESHOLD) {
                        if (deltaX > 0 && currentTabIndex > 0) {
                            // 向右滑 -> 切换到左侧Tab
                            currentTabIndex--
                            binding.bottomNav.menu.getItem(currentTabIndex).isChecked = true
                            showFragment(currentTabIndex)
                        } else if (deltaX < 0 && currentTabIndex < 3) {
                            // 向左滑 -> 切换到右侧Tab
                            currentTabIndex++
                            binding.bottomNav.menu.getItem(currentTabIndex).isChecked = true
                            showFragment(currentTabIndex)
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private var touchStartX = 0f
    private val SWIPE_THRESHOLD = 100f

    fun navigateToPlayer(bvid: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("bvid", bvid)
        startActivity(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullScreen()
        }
    }
}