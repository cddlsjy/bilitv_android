package com.bili.tv.bili_tv_app.data.model

import com.google.gson.annotations.SerializedName

/**
 * 视频数据模型
 */
data class Video(
    @SerializedName("bvid")
    val bvid: String = "",

    @SerializedName("aid")
    val aid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("pic")
    val pic: String = "",

    @SerializedName("duration")
    val duration: Int = 0,

    @SerializedName("owner")
    val owner: VideoOwner? = null,

    @SerializedName("stat")
    val stat: VideoStat? = null,

    @SerializedName("desc")
    val desc: String? = null,

    @SerializedName("cid")
    val cid: Long = 0,

    @Transient
    var ownerMid: Long = 0,

    @Transient
    var ownerName: String = owner?.name ?: "",

    @Transient
    var ownerFace: String = owner?.face ?: ""
) {
    val durationStr: String
        get() {
            val minutes = duration / 60
            val seconds = duration % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

    val viewCount: String
        get() = stat?.view?.formatCount() ?: "0"

    val likeCount: String
        get() = stat?.like?.formatCount() ?: "0"

    val danmakuCount: String
        get() = stat?.danmaku?.formatCount() ?: "0"
}

data class VideoOwner(
    @SerializedName("mid")
    val mid: Long = 0,

    @SerializedName("name")
    val name: String = "",

    @SerializedName("face")
    val face: String = ""
)

data class VideoStat(
    @SerializedName("view")
    val view: Long = 0,

    @SerializedName("like")
    val like: Long = 0,

    @SerializedName("coin")
    val coin: Long = 0,

    @SerializedName("favorite")
    val favorite: Long = 0,

    @SerializedName("danmaku")
    val danmaku: Long = 0,

    @SerializedName("reply")
    val reply: Long = 0
)

// 扩展函数：格式化数字
fun Long.formatCount(): String {
    return when {
        this >= 100000000 -> String.format("%.1f亿", this / 100000000.0)
        this >= 10000 -> String.format("%.1f万", this / 10000.0)
        else -> this.toString()
    }
}

/**
 * 推荐视频响应
 */
data class RecommendResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: List<Video>? = null
)

/**
 * 热门视频响应
 */
data class PopularResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: PopularData? = null
)

data class PopularData(
    @SerializedName("list")
    val list: List<Video>? = null
)

/**
 * 搜索结果响应
 */
data class SearchResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("data")
    val data: SearchData? = null
)

data class SearchData(
    @SerializedName("result")
    val result: List<Video>? = null
)

/**
 * 播放地址响应
 */
data class PlayUrlResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("data")
    val data: PlayUrlData? = null
)

data class PlayUrlData(
    @SerializedName("dash")
    val dash: DashData? = null
)

data class DashData(
    @SerializedName("video")
    val video: List<VideoQuality>? = null,

    @SerializedName("audio")
    val audio: List<AudioQuality>? = null
)

data class VideoQuality(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("baseUrl")
    val baseUrl: String = "",

    @SerializedName("width")
    val width: Int = 0,

    @SerializedName("height")
    val height: Int = 0
)

data class AudioQuality(
    @SerializedName("id")
    val id: Int = 0,

    @SerializedName("baseUrl")
    val baseUrl: String = ""
)

/**
 * 选集信息
 */
data class EpisodeInfo(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("part")
    val part: String = ""
)

/**
 * 视频详情
 */
data class VideoDetailResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("data")
    val data: VideoDetailData? = null
)

data class VideoDetailData(
    @SerializedName("cid")
    val cid: Long = 0,

    @SerializedName("title")
    val title: String = "",

    @SerializedName("pages")
    val pages: List<EpisodeInfo>? = null
)