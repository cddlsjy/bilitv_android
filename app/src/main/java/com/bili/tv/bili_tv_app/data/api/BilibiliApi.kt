package com.bili.tv.bili_tv_app.data.api

import android.util.Log
import com.bili.tv.bili_tv_app.data.model.Video
import com.bili.tv.bili_tv_app.data.model.VideoOwner
import com.bili.tv.bili_tv_app.data.model.VideoStat
import com.bili.tv.bili_tv_app.data.model.PlayUrlResponse
import com.bili.tv.bili_tv_app.data.model.VideoDetailData
import com.bili.tv.bili_tv_app.data.model.EpisodeInfo
import com.bili.tv.bili_tv_app.data.net.BiliClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

object BilibiliApi {

    private const val BASE_URL = "https://api.bilibili.com"
    private const val TAG = "BilibiliApi"

    fun init(context: android.content.Context) {
        BiliClient.init(context)
    }

    suspend fun getRecommendVideos(freshIdx: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val keys = BiliClient.ensureWbiKeys()
            val url = BiliClient.signedWbiUrl(
                path = "/x/web-interface/wbi/index/top/feed/rcmd",
                params = mapOf(
                    "ps" to ps.toString(),
                    "fresh_idx" to freshIdx.toString(),
                    "fresh_idx_1h" to freshIdx.toString(),
                    "fetch_row" to "1",
                    "feed_version" to "V8"
                ),
                keys = keys
            )
            Log.d(TAG, "Recommend URL: $url")
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            Log.d(TAG, "Recommend response: code=$code, msg=$message, data=${json.optJSONObject("data")?.toString()?.take(200)}")
            if (code != 0) {
                Log.e(TAG, "Recommend API error: $code - $message")
                return@withContext emptyList()
            }
            val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
            Log.d(TAG, "Found ${items.length()} items")
            if (items.length() == 0) {
                Log.w(TAG, "No items found in recommend videos response data.")
            }
            parseVideoCards(items)
        } catch (e: Exception) {
            Log.e(TAG, "Recommend exception", e)
            emptyList()
        }
    }

    suspend fun getPopularVideos(pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val keys = BiliClient.ensureWbiKeys()
            val url = BiliClient.signedWbiUrl(
                path = "/x/web-interface/popular",
                params = mapOf(
                    "pn" to pn.coerceAtLeast(1).toString(),
                    "ps" to ps.coerceIn(1, 50).toString()
                ),
                keys = keys
            )
            Log.d(TAG, "Popular URL: $url")
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            Log.d(TAG, "Popular response: code=$code, msg=$message, data=${json.optJSONObject("data")?.toString()?.take(200)}")
            if (code != 0) {
                Log.e(TAG, "Popular API error: $code - $message")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val list = data.optJSONArray("list") ?: JSONArray()
            Log.d(TAG, "Found ${list.length()} items")
            if (list.length() == 0) {
                Log.w(TAG, "No items found in popular videos response data.")
            }
            parseVideoCards(list)
        } catch (e: Exception) {
            Log.e(TAG, "Popular exception", e)
            emptyList()
        }
    }

    suspend fun getRegionVideos(tid: Int, pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val keys = BiliClient.ensureWbiKeys()
            val url = BiliClient.signedWbiUrl(
                path = "/x/web-interface/dynamic/region",
                params = mapOf(
                    "rid" to tid.toString(),
                    "pn" to pn.coerceAtLeast(1).toString(),
                    "ps" to ps.coerceIn(1, 50).toString()
                ),
                keys = keys
            )
            Log.d(TAG, "Region URL: $url")
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            Log.d(TAG, "Region response: code=$code, msg=$message, data=${json.optJSONObject("data")?.toString()?.take(200)}")
            if (code != 0) {
                Log.e(TAG, "Region API error: $code - $message")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val archives = data.optJSONArray("archives") ?: JSONArray()
            Log.d(TAG, "Found ${archives.length()} items")
            if (archives.length() == 0) {
                Log.w(TAG, "No items found in region videos response data.")
            }
            parseVideoCards(archives)
        } catch (e: Exception) {
            Log.e(TAG, "Region exception", e)
            emptyList()
        }
    }

    suspend fun searchVideos(keyword: String, page: Int = 1): List<Video> = withContext(Dispatchers.IO) {
        try {
            val keys = BiliClient.ensureWbiKeys()
            val params = mapOf(
                "search_type" to "video",
                "keyword" to keyword,
                "page" to page.toString()
            )
            val url = BiliClient.signedWbiUrl("/x/search/type", params, keys)
            Log.d(TAG, "Search URL: $url")
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            Log.d(TAG, "Search response: code=$code, msg=$message, data=${json.optJSONObject("data")?.toString()?.take(200)}")
            if (code != 0) {
                Log.e(TAG, "Search API error: $code - $message")
                return@withContext emptyList()
            }
            val searchResponse = json.optJSONObject("data") ?: JSONObject()
            val result = searchResponse.optJSONArray("result") ?: JSONArray()
            Log.d(TAG, "Found ${result.length()} items")
            if (result.length() == 0) {
                Log.w(TAG, "No items found in search videos response data.")
            }
            parseSearchResults(result)
        } catch (e: Exception) {
            Log.e(TAG, "Search exception", e)
            emptyList()
        }
    }

    suspend fun getPlayUrl(bvid: String, cid: Long): PlayUrlResponse? = withContext(Dispatchers.IO) {
        try {
            val keys = BiliClient.ensureWbiKeys()
            val params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to "80",
                "fnval" to "4048",
                "fnver" to "0",
                "fourk" to "1"
            )
            val url = BiliClient.signedWbiUrl("/x/player/wbi/playurl", params, keys)
            Log.d(TAG, "PlayUrl URL: $url")
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            Log.d(TAG, "PlayUrl response: code=$code, msg=$message, data=${json.optJSONObject("data")?.toString()?.take(200)}")
            if (code != 0) {
                Log.e(TAG, "PlayUrl API error: $code - $message")
                return@withContext null
            }
            parsePlayUrl(json)
        } catch (e: Exception) {
            Log.e(TAG, "PlayUrl exception", e)
            null
        }
    }

    suspend fun getVideoDetail(bvid: String): VideoDetailData? = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/view?bvid=$bvid"
            Log.d(TAG, "VideoDetail URL: $url")
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", -1)
            val message = json.optString("message", "")
            Log.d(TAG, "VideoDetail response: code=$code, msg=$message, data=${json.optJSONObject("data")?.toString()?.take(200)}")
            if (code != 0) {
                Log.e(TAG, "VideoDetail API error: $code - $message")
                return@withContext null
            }
            parseVideoDetail(json)
        } catch (e: Exception) {
            Log.e(TAG, "VideoDetail exception", e)
            null
        }
    }

    suspend fun getDanmaku(cid: Long): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"
            Log.d(TAG, "Danmaku URL: $url")
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", BiliClient.USER_AGENT)
                .addHeader("Referer", "https://www.bilibili.com/")
                .build()

            val response = BiliClient.apiOkHttp.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d(TAG, "Danmaku response body length: ${body.length}")
            body
        } catch (e: Exception) {
            Log.e(TAG, "Danmaku exception", e)
            ""
        }
    }

    private fun parseVideoCards(arr: JSONArray): List<Video> {
        val out = ArrayList<Video>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val bvid = obj.optString("bvid", "").trim()
            if (bvid.isBlank()) continue

            val owner = obj.optJSONObject("owner")
            val stat = obj.optJSONObject("stat")

            val durationText = obj.optString("duration_text", obj.optString("duration", "0:00"))
            val durationSec = parseDuration(durationText)

            out.add(
                Video(
                    bvid = bvid,
                    aid = obj.optLong("aid").takeIf { it > 0 } ?: 0,
                    cid = obj.optLong("cid").takeIf { it > 0 } ?: 0,
                    title = obj.optString("title", ""),
                    pic = obj.optString("pic", obj.optString("cover", "")),
                    duration = durationSec,
                    desc = obj.optString("desc", null),
                    owner = VideoOwner(
                        mid = owner?.optLong("mid") ?: 0,
                        name = owner?.optString("name", "") ?: "",
                        face = owner?.optString("face", "") ?: ""
                    ),
                    stat = VideoStat(
                        view = stat?.optLong("view")?.takeIf { it > 0 } ?: stat?.optLong("play")?.takeIf { it > 0 } ?: 0,
                        danmaku = stat?.optLong("danmaku")?.takeIf { it > 0 } ?: stat?.optLong("dm")?.takeIf { it > 0 } ?: 0,
                        reply = stat?.optLong("reply")?.takeIf { it > 0 } ?: 0,
                        favorite = stat?.optLong("favorite")?.takeIf { it > 0 } ?: 0,
                        coin = stat?.optLong("coin")?.takeIf { it > 0 } ?: 0
                    )
                ).apply {
                    ownerMid = owner?.optLong("mid") ?: 0
                    ownerName = owner?.optString("name", "") ?: ""
                    ownerFace = owner?.optString("face", "") ?: ""
                }
            )
        }
        return out
    }

    private fun parseSearchResults(arr: JSONArray): List<Video> {
        val out = ArrayList<Video>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val bvid = obj.optString("bvid", "").trim()
            if (bvid.isBlank()) continue

            val durationText = obj.optString("duration", "")
            val durationSec = parseDuration(durationText)

            out.add(
                Video(
                    bvid = bvid,
                    aid = obj.optLong("aid").takeIf { it > 0 } ?: 0,
                    cid = obj.optLong("cid").takeIf { it > 0 } ?: 0,
                    title = obj.optString("title", ""),
                    pic = obj.optString("pic", ""),
                    duration = durationSec,
                    desc = obj.optString("description", null),
                    owner = VideoOwner(
                        mid = obj.optLong("mid").takeIf { it > 0 } ?: 0,
                        name = obj.optString("author", ""),
                        face = ""
                    ),
                    stat = VideoStat(
                        view = parseCountText(obj.optString("play", "0")),
                        danmaku = parseCountText(obj.optString("video_review", "0")),
                        reply = 0,
                        favorite = 0,
                        coin = 0
                    )
                ).apply {
                    ownerName = obj.optString("author", "")
                }
            )
        }
        return out
    }

    private fun parsePlayUrl(json: JSONObject): PlayUrlResponse? {
        val data = json.optJSONObject("data") ?: return null
        return PlayUrlResponse(
            code = json.optInt("code", 0),
            data = com.bili.tv.bili_tv_app.data.model.PlayUrlData(
                dash = parseDashData(data)
            )
        )
    }

    private fun parseDashData(data: JSONObject): com.bili.tv.bili_tv_app.data.model.DashData? {
        val dash = data.optJSONObject("dash") ?: return null
        val videos = mutableListOf<com.bili.tv.bili_tv_app.data.model.VideoQuality>()
        val videoArray = dash.optJSONArray("video")
        if (videoArray != null) {
            for (i in 0 until videoArray.length()) {
                val v = videoArray.optJSONObject(i) ?: continue
                videos.add(
                    com.bili.tv.bili_tv_app.data.model.VideoQuality(
                        id = v.optInt("id", 0),
                        baseUrl = v.optString("baseUrl", v.optString("url", "")),
                        width = v.optInt("width", 0),
                        height = v.optInt("height", 0)
                    )
                )
            }
        }

        val audios = mutableListOf<com.bili.tv.bili_tv_app.data.model.AudioQuality>()
        val audioArray = dash.optJSONArray("audio")
        if (audioArray != null) {
            for (i in 0 until audioArray.length()) {
                val a = audioArray.optJSONObject(i) ?: continue
                audios.add(
                    com.bili.tv.bili_tv_app.data.model.AudioQuality(
                        id = a.optInt("id", 0),
                        baseUrl = a.optString("baseUrl", a.optString("url", ""))
                    )
                )
            }
        }

        return com.bili.tv.bili_tv_app.data.model.DashData(videos, audios)
    }

    private fun parseVideoDetail(json: JSONObject): VideoDetailData? {
        val data = json.optJSONObject("data") ?: return null
        val pages = data.optJSONArray("pages") ?: JSONArray()
        val pageList = mutableListOf<EpisodeInfo>()
        for (i in 0 until pages.length()) {
            val p = pages.optJSONObject(i) ?: continue
            pageList.add(
                EpisodeInfo(
                    cid = p.optLong("cid", 0),
                    title = p.optString("title", ""),
                    part = p.optString("part", "")
                )
            )
        }
        return VideoDetailData(
            cid = data.optLong("cid", 0),
            title = data.optString("title", ""),
            pages = pageList
        )
    }

    private fun parseDuration(text: String): Int {
        if (text.isBlank()) return 0
        val parts = text.split(":")
        return try {
            when (parts.size) {
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                2 -> parts[0].toInt() * 60 + parts[1].toInt()
                1 -> parts[0].toInt()
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun parseCountText(text: String): Long {
        val s = text.trim()
        if (s.isBlank()) return 0
        val multiplier = when {
            s.contains("亿") -> 100_000_000L
            s.contains("万") -> 10_000L
            else -> 1L
        }
        val numText = s.replace(Regex("[^0-9.]"), "")
        if (numText.isBlank()) return 0
        val value = numText.toDoubleOrNull() ?: return 0
        if (value.isNaN() || value.isInfinite()) return 0
        return (value * multiplier).roundToLong()
    }
}