package com.bili.tv.bili_tv_app.ui.player

import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.data.api.BilibiliApi
import com.bili.tv.bili_tv_app.data.model.Video
import kotlinx.coroutines.launch

/**
 * 播放器Activity - 支持触摸和遥控器控制
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private var currentVideo: Video? = null
    private var currentBvid: String = ""
    private var currentCid: Long = 0

    private var controlsVisible = true
    private val hideControlsRunnable = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏横屏模式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setFullScreen()

        setContentView(R.layout.activity_player)

        initViews()
        loadIntent()
    }

    private fun setFullScreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

        // 设置播放器控制器
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            controlsVisible = visibility == View.VISIBLE
        })

        // 触摸显示/隐藏控制栏
        playerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    if (controlsVisible) {
                        hideControls()
                    } else {
                        showControls()
                    }
                }
            }
            true
        }

        // 长按快进快退
        playerView.setOnLongClickListener {
            // 长按处理
            true
        }
    }

    private fun loadIntent() {
        currentBvid = intent.getStringExtra("bvid") ?: ""
        if (currentBvid.isEmpty()) {
            Toast.makeText(this, "无效的视频", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 初始化播放器
        initializePlayer()

        // 获取视频信息
        loadVideoInfo()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF

            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            // 播放就绪
                        }
                        Player.STATE_ENDED -> {
                            // 播放结束
                        }
                        Player.STATE_BUFFERING -> {
                            // 缓冲中
                        }
                        Player.STATE_IDLE -> {
                            // 空闲
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "播放失败: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        }

        playerView.player = player
    }

    private fun loadVideoInfo() {
        lifecycleScope.launch {
            val detail = BilibiliApi.getVideoDetail(currentBvid)
            if (detail != null) {
                currentCid = detail.cid
                loadPlayUrl()
            } else {
                Toast.makeText(this@PlayerActivity, "获取视频信息失败", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadPlayUrl() {
        lifecycleScope.launch {
            val response = BilibiliApi.getPlayUrl(currentBvid, currentCid)
            if (response?.data?.dash != null) {
                val dashData = response.data!!.dash!!

                // 优先使用 H.265 / 高画质
                val videoUrl = dashData.video
                    ?.sortedByDescending { it.height }
                    ?.firstOrNull()
                    ?.baseUrl ?: ""

                val audioUrl = dashData.audio
                    ?.sortedByDescending { it.id }
                    ?.firstOrNull()
                    ?.baseUrl ?: ""

                if (videoUrl.isNotEmpty()) {
                    val mediaItem = MediaItem.fromUri(videoUrl)
                    player?.setMediaItem(mediaItem)
                    player?.prepare()
                }
            } else {
                Toast.makeText(this@PlayerActivity, "获取播放地址失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showControls() {
        playerView.showController()
        handler.postDelayed(hideControlsRunnable, 5000)
    }

    private fun hideControls() {
        if (player?.isPlaying == true) {
            playerView.hideController()
        }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                // 确认键：播放/暂停
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // 左方向键：快退10秒
                player?.let {
                    val position = it.currentPosition
                    it.seekTo(maxOf(0, position - 10000))
                }
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // 右方向键：快进10秒
                player?.let {
                    val position = it.currentPosition
                    val duration = it.duration
                    it.seekTo(minOf(duration, position + 10000))
                }
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                // 上方向键：增加音量
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                // 下方向键：减少音量
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                // 返回键：退出
                if (player?.isPlaying == true) {
                    player?.pause()
                }
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_UP -> {
                if (controlsVisible) {
                    hideControls()
                } else {
                    showControls()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideControlsRunnable)
        player?.release()
        player = null
    }
}