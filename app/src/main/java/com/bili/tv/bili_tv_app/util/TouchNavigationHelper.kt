package com.bili.tv.bili_tv_app.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.ViewGroupCompat

/**
 * 触摸导航帮助类
 * 支持触摸屏和遥控器混合操作
 */
object TouchNavigationHelper {

    /**
     * 处理遥控器方向键导航
     */
    fun handleDirectionKey(
        currentView: View,
        event: KeyEvent
    ): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val focusDirection = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> View.FOCUS_FORWARD
        }

        // 查找下一个焦点视图
        val nextFocus = FocusFinder.getInstance().findNextFocus(
            currentView.parent as ViewGroup,
            currentView,
            focusDirection
        )

        return if (nextFocus != null) {
            nextFocus.requestFocus()
            true
        } else {
            false
        }
    }

    /**
     * 检查是否应该将焦点移动到侧边栏
     */
    fun shouldMoveToSidebar(
        currentView: View,
        direction: Int
    ): Boolean {
        // 当在最左边且向左移动时
        if (direction == View.FOCUS_LEFT) {
            val location = IntArray(2)
            currentView.getLocationOnScreen(location)
            return location[0] <= 50
        }
        return false
    }

    /**
     * 设置触摸监听器以支持触摸点击
     */
    fun setupTouchClickListener(view: View, onClick: () -> Unit) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
                onClick()
            }
            true
        }
    }

    /**
     * 获取视图在屏幕上的位置
     */
    fun getViewPosition(view: View): Pair<Int, Int> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Pair(location[0], location[1])
    }

    /**
     * 判断是否为边缘位置
     */
    fun isAtEdge(view: View, edge: Int): Boolean {
        val position = getViewPosition(view)
        return when (edge) {
            View.FOCUS_LEFT -> position.first <= 50
            View.FOCUS_RIGHT -> {
                val screenWidth = view.context.resources.displayMetrics.widthPixels
                position.first + view.width >= screenWidth - 50
            }
            View.FOCUS_UP -> position.second <= 50
            View.FOCUS_DOWN -> {
                val screenHeight = view.context.resources.displayMetrics.heightPixels
                position.second + view.height >= screenHeight - 50
            }
            else -> false
        }
    }
}

/**
 * 焦点移动方向
 */
enum class FocusMoveDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * 焦点状态追踪
 */
class FocusTracker(private val rootView: ViewGroup) {
    private var currentFocusedView: View? = null

    init {
        setupFocusListener()
    }

    private fun setupFocusListener() {
        rootView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            currentFocusedView = newFocus
        }
    }

    fun getCurrentFocus(): View? = currentFocusedView

    fun moveFocus(direction: FocusMoveDirection): Boolean {
        val current = currentFocusedView ?: return false
        val focusDirection = when (direction) {
            FocusMoveDirection.UP -> View.FOCUS_UP
            FocusMoveDirection.DOWN -> View.FOCUS_DOWN
            FocusMoveDirection.LEFT -> View.FOCUS_LEFT
            FocusMoveDirection.RIGHT -> View.FOCUS_RIGHT
        }

        val next = FocusFinder.getInstance().findNextFocus(rootView, current, focusDirection)
        return if (next != null) {
            next.requestFocus()
            true
        } else {
            false
        }
    }

    fun resetFocus() {
        currentFocusedView?.clearFocus()
        currentFocusedView = null
    }
}