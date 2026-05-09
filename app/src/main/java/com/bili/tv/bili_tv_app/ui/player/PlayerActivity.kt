package com.bili.tv.bili_tv_app.ui.player

import android.os.Bundle
import android.view.*
import android.widget.Toast
import android.view.GestureDetector
import android.view.ViewConfiguration
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.bili.tv.bili_tv_app.R
import com.bili.tv.bili_tv_app.data.api.BilibiliApi
import com.bili.tv.bili_tv_app.data.model.Video
import kotlinx.coroutines.launch

/**
 * 播放器Activity - 支持触摸和遥控器控制
 * 使用 MergingMediaSource 合并 DASH 视频+音频流
 */
@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private var currentVideo: Video? = null
    private var currentBvid: String = ""
    private var currentCid: Long = 0

    private var controlsVisible = true
    
    private val recommendVideos = mutableListOf<com.bili.tv.bili_tv_app.data.model.Video>()
    private var currentVideoIndex = -1
    private val hideControlsRunnable = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏横屏模式
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)

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
        val swipeThreshold = ViewConfiguration.get(this).scaledTouchSlop * 4
        val swipeVelocityThreshold = ViewConfiguration.get(this).scaledMinimumFlingVelocity * 2
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dy = e1.y - e2.y
                val dx = e1.x - e2.x
                if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && kotlin.math.abs(dy) > swipeThreshold && kotlin.math.abs(velocityY) > swipeVelocityThreshold) {
                    val isUp = dy > 0
                    if (isUp) {
                        playPrev()
                    } else {
                        playNext()
                    }
                    return true
                }
                return false
            }
        })

        val touchListener = View.OnTouchListener { v, event ->
            val handled = detector.onTouchEvent(event)
            if (!handled) {
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (controlsVisible) {
                            hideControls()
                        } else {
                            showControls()
                        }
                    }
                }
            }
            true
        }

        playerView = findViewById(R.id.playerView)
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
            controlsVisible = visibility == View.VISIBLE
        })

        playerView.setOnTouchListener(touchListener)
    }

    private fun loadIntent() {
        currentBvid = intent.getStringExtra("bvid") ?: ""
        if (currentBvid.isEmpty()) {
            Toast.makeText(this, "无效的视频", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadVideoInfo()
    }

    /**
     * 配置 HTTP 数据源工厂，添加 B站必需的 Referer 头
     */
    private fun createDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.bilibili.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            ))
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
    }

    /**
     * 使用 MergingMediaSource 合并视频和音频 DASH 流
     */
    private fun initializePlayer(videoUrl: String, audioUrl: String?) {
        val dataSourceFactory = createDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        android.util.Log.d("PlayerActivity", "Playback state: $playbackState")
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        android.util.Log.e("PlayerActivity", "Player error: ${error.errorCodeName} - ${error.message}", error)
                        Toast.makeText(this@PlayerActivity, "播放失败: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                })
            }

        playerView.player = player

        // 使用 ProgressiveMediaSource 加载直接的视频/音频文件，然后合并
        try {
            val progressiveFactory = ProgressiveMediaSource.Factory(dataSourceFactory)

            val videoItem = MediaItem.fromUri(videoUrl)
            val videoSource = progressiveFactory.createMediaSource(videoItem)

            if (!audioUrl.isNullOrBlank()) {
                val audioItem = MediaItem.fromUri(audioUrl)
                val audioSource = progressiveFactory.createMediaSource(audioItem)

                // 合并视频和音频流
                val mergedSource = MergingMediaSource(videoSource, audioSource)
                player?.setMediaSource(mergedSource)
                android.util.Log.d("PlayerActivity", "Playing with merged video+audio (Progressive)")
            } else {
                player?.setMediaSource(videoSource)
                android.util.Log.d("PlayerActivity", "Playing video only (no audio)")
            }

            player?.prepare()
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "Failed to create media source: ${e.message}", e)
            Toast.makeText(this@PlayerActivity, "播放源创建失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadVideoInfo() {
        lifecycleScope.launch {
            val detail = BilibiliApi.getVideoDetail(currentBvid)
            if (detail != null) {
                currentCid = detail.cid
                android.util.Log.d("PlayerActivity", "Video detail: bvid=$currentBvid, cid=$currentCid, title=${detail.title}")
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

                // 选择最高画质的视频流
                val videoTrack = dashData.video
                    ?.sortedByDescending { it.height }
                    ?.firstOrNull()

                // 选择最高音质的音频流
                val audioTrack = dashData.audio
                    ?.sortedByDescending { it.id }
                    ?.firstOrNull()

                val videoUrl = videoTrack?.baseUrl ?: ""
                val audioUrl = audioTrack?.baseUrl

                android.util.Log.d("PlayerActivity", "Video: ${videoTrack?.width}x${videoTrack?.height} codecs=${videoTrack?.id}")
                android.util.Log.d("PlayerActivity", "Audio: id=${audioTrack?.id}")
                android.util.Log.d("PlayerActivity", "Video URL: ${videoUrl.take(80)}...")
                android.util.Log.d("PlayerActivity", "Audio URL: ${audioUrl?.take(80)}...")

                if (videoUrl.isNotEmpty()) {
                    initializePlayer(videoUrl, audioUrl)
                } else {
                    Toast.makeText(this@PlayerActivity, "获取播放地址失败", Toast.LENGTH_SHORT).show()
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        playPrev()
                    } else {
                        playNext()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    playPrev()
                }
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    playNext()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.let {
                    val position = it.currentPosition
                    it.seekTo(maxOf(0, position - 10000))
                }
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.let {
                    val position = it.currentPosition
                    val duration = it.duration
                    it.seekTo(minOf(duration, position + 10000))
                }
                showControls()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
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

    override fun onResume() {
        super.onResume()
        window.decorView.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    private fun playNext() {
        if (recommendVideos.isEmpty()) {
            loadRecommendVideos()
            return
        }
        
        currentVideoIndex = (currentVideoIndex + 1) % recommendVideos.size
        val nextVideo = recommendVideos[currentVideoIndex]
        playVideo(nextVideo)
    }

    private fun playPrev() {
        if (recommendVideos.isEmpty()) {
            loadRecommendVideos()
            return
        }
        
        currentVideoIndex = if (currentVideoIndex <= 0) {
            recommendVideos.size - 1
        } else {
            currentVideoIndex - 1
        }
        val prevVideo = recommendVideos[currentVideoIndex]
        playVideo(prevVideo)
    }

    private fun playVideo(video: com.bili.tv.bili_tv_app.data.model.Video) {
        currentBvid = video.bvid
        currentCid = video.cid
        loadVideoInfo()
    }

    private fun loadRecommendVideos() {
        lifecycleScope.launch {
            val videos = try {
                BilibiliApi.getRecommendVideos()
            } catch (e: Exception) {
                android.util.Log.e("PlayerActivity", "Load recommend videos error: ${e.message}", e)
                emptyList()
            }
            
            if (videos.isNotEmpty()) {
                recommendVideos.clear()
                recommendVideos.addAll(videos)
                currentVideoIndex = recommendVideos.indexOfFirst { it.bvid == currentBvid }
                if (currentVideoIndex == -1) {
                    currentVideoIndex = 0
                }
                android.util.Log.d("PlayerActivity", "Loaded ${recommendVideos.size} recommend videos")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(hideControlsRunnable)
        player?.release()
        player = null
    }
}
