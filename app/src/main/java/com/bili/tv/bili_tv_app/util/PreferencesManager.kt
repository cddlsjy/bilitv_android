package com.bili.tv.bili_tv_app.util

import android.content.Context
import android.content.SharedPreferences
import com.bili.tv.bili_tv_app.data.model.Video

/**
 * 本地存储服务
 */
object PreferencesManager {
    private const val PREF_NAME = "bili_tv_prefs"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    //region 播放设置
    var videoQuality: Int
        get() = prefs.getInt("video_quality", 80)
        set(value) = prefs.edit().putInt("video_quality", value).apply()

    var autoPlay: Boolean
        get() = prefs.getBoolean("auto_play", true)
        set(value) = prefs.edit().putBoolean("auto_play", value).apply()

    var danmakuEnabled: Boolean
        get() = prefs.getBoolean("danmaku_enabled", true)
        set(value) = prefs.edit().putBoolean("danmaku_enabled", value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.0f)
        set(value) = prefs.edit().putFloat("playback_speed", value).apply()
    //endregion

    //region 界面设置
    var showMiniProgress: Boolean
        get() = prefs.getBoolean("show_mini_progress", true)
        set(value) = prefs.edit().putBoolean("show_mini_progress", value).apply()

    var alwaysShowPlayerTime: Boolean
        get() = prefs.getBoolean("always_show_player_time", false)
        set(value) = prefs.edit().putBoolean("always_show_player_time", value).apply()
    //endregion

    //region 搜索历史
    fun addSearchHistory(keyword: String) {
        val history = getSearchHistory().toMutableList()
        history.remove(keyword)
        history.add(0, keyword)
        if (history.size > 20) {
            history.removeAt(history.size - 1)
        }
        prefs.edit().putStringSet("search_history", history.toSet()).apply()
    }

    fun getSearchHistory(): List<String> {
        return prefs.getStringSet("search_history", emptySet())?.toList() ?: emptyList()
    }

    fun clearSearchHistory() {
        prefs.edit().remove("search_history").apply()
    }
    //endregion

    //region 观看历史
    fun addWatchHistory(video: Video) {
        val historyJson = prefs.getString("watch_history", "") ?: ""
        val history = if (historyJson.isNotEmpty()) {
            com.google.gson.Gson().fromJson(historyJson, Array<Video>::class.java).toMutableList()
        } else {
            mutableListOf()
        }

        // 移除重复
        history.removeAll { it.bvid == video.bvid }
        // 添加到开头
        history.add(0, video)
        // 限制数量
        if (history.size > 100) {
            history.removeAt(history.size - 1)
        }

        prefs.edit().putString("watch_history", com.google.gson.Gson().toJson(history)).apply()
    }

    fun getWatchHistory(): List<Video> {
        val historyJson = prefs.getString("watch_history", "") ?: ""
        return if (historyJson.isNotEmpty()) {
            try {
                com.google.gson.Gson().fromJson(historyJson, Array<Video>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun clearWatchHistory() {
        prefs.edit().remove("watch_history").apply()
    }
    //endregion

    //region 播放进度
    fun savePlaybackProgress(bvid: String, progress: Long, duration: Long) {
        val key = "progress_$bvid"
        prefs.edit()
            .putLong("${key}_progress", progress)
            .putLong("${key}_duration", duration)
            .putLong("${key}_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun getPlaybackProgress(bvid: String): Pair<Long, Long>? {
        val key = "progress_$bvid"
        val progress = prefs.getLong("${key}_progress", -1)
        val duration = prefs.getLong("${key}_duration", -1)
        return if (progress >= 0 && duration > 0) {
            Pair(progress, duration)
        } else null
    }
    //endregion
}