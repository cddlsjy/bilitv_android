package com.bili.tv.bili_tv_app.data.api

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

    fun init(context: android.content.Context) {
        BiliClient.init(context)
    }

    suspend fun getRecommendVideos(freshIdx: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            // 尝试使用简单的推荐接口（不需要 WBI 签名）
            val url = "${BASE_URL}/x/web-interface/index/top/feed/rcmd?ps=${ps.coerceIn(1, 50)}&fresh_idx=${freshIdx}"
            android.util.Log.d("BilibiliApi", "Recommend URL: $url")
            val json = BiliClient.getJson(url)
            
            val rawJson = json.toString()
            android.util.Log.d("BilibiliApi", "Response: ${rawJson.substring(0, minOf(500, rawJson.length))}")

            val code = json.optInt("code", 0)
            if (code != 0) {
                android.util.Log.e("BilibiliApi", "API error code=$code: ${json.optString("message")}")
                return@withContext emptyList()
            }

            val data = json.optJSONObject("data") ?: JSONObject()
            android.util.Log.d("BilibiliApi", "Data JSON keys: ${data.keys()}")
            
            // 尝试不同的字段名
            val items = data.optJSONArray("item") 
                ?: data.optJSONArray("card_list") 
                ?: data.optJSONArray("list")
                ?: JSONArray()
                
            android.util.Log.d("BilibiliApi", "Items count: ${items.length()}")
            parseVideoCards(items)
        } catch (e: Exception) {
            android.util.Log.e("BilibiliApi", "Exception: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getPopularVideos(pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/popular?pn=${pn.coerceAtLeast(1)}&ps=${ps.coerceIn(1, 50)}"
            android.util.Log.d("BilibiliApi", "Popular URL: $url")
            val json = BiliClient.getJson(url)
            
            val code = json.optInt("code", 0)
            if (code != 0) {
                android.util.Log.e("BilibiliApi", "Popular API error code=$code: ${json.optString("message")}")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val list = data.optJSONArray("list") ?: JSONArray()
            android.util.Log.d("BilibiliApi", "Popular list count: ${list.length()}")
            parseVideoCards(list)
        } catch (e: Exception) {
            android.util.Log.e("BilibiliApi", "Popular Exception: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getRegionVideos(tid: Int, pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/dynamic/region?rid=${tid}&pn=${pn.coerceAtLeast(1)}&ps=${ps.coerceIn(1, 50)}"
            android.util.Log.d("BilibiliApi", "Region URL: $url (tid=$tid)")
            val json = BiliClient.getJson(url)
            
            android.util.Log.d("BilibiliApi", "Region Response code: ${json.optInt("code")}")
            val code = json.optInt("code", 0)
            if (code != 0) {
                android.util.Log.e("BilibiliApi", "Region API error code=$code: ${json.optString("message")}")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val archives = data.optJSONArray("archives") ?: JSONArray()
            android.util.Log.d("BilibiliApi", "Region archives count: ${archives.length()}")
            parseVideoCards(archives)
        } catch (e: Exception) {
            android.util.Log.e("BilibiliApi", "Region Exception: ${e.message}", e)
            e.printStackTrace()
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
            val json = BiliClient.getJson(url)

            val code = json.optInt("code", 0)
            if (code != 0) {
                android.util.Log.e("BilibiliApi", "Search API error code=$code: ${json.optString("message")}")
                return@withContext emptyList()
            }

            val searchResponse = json.optJSONObject("data") ?: JSONObject()
            val result = searchResponse.optJSONArray("result") ?: JSONArray()
            parseSearchResults(result)
        } catch (e: Exception) {
            e.printStackTrace()
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
            val json = BiliClient.getJson(url)

            val code = json.optInt("code", 0)
            if (code != 0) {
                android.util.Log.e("BilibiliApi", "PlayUrl API error code=$code")
                return@withContext null
            }
            parsePlayUrl(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getVideoDetail(bvid: String): VideoDetailData? = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/view?bvid=$bvid"
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", 0)
            if (code != 0) {
                return@withContext null
            }
            parseVideoDetail(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getDanmaku(cid: Long): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"
            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build()

            val response = BiliClient.apiOkHttp.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseVideoCards(arr: JSONArray): List<Video> {
        val out = ArrayList<Video>()
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.optJSONObject(i) ?: continue
                val bvid = obj.optString("bvid", "").trim()
                if (bvid.isBlank()) continue

                val owner = obj.optJSONObject("owner")
                val stat = obj.optJSONObject("stat")

                val aid = obj.optLong("aid").takeIf { it > 0 }
                    ?: obj.optLong("id").takeIf { it > 0 }
                    ?: 0

                val cid = obj.optLong("cid").takeIf { it > 0 } ?: 0

                val pic = obj.optString("pic", "").ifBlank { obj.optString("cover", "") }

                val durationSec = parseDurationValue(obj, "duration")

                val title = obj.optString("title", "")

                out.add(
                    Video(
                        bvid = bvid,
                        aid = aid,
                        cid = cid,
                        title = title,
                        pic = pic,
                        duration = durationSec,
                        desc = obj.optString("desc", null),
                        owner = VideoOwner(
                            mid = owner?.optLong("mid") ?: 0,
                            name = owner?.optString("name", "") ?: "",
                            face = owner?.optString("face", "") ?: ""
                        ),
                        stat = VideoStat(
                            view = stat?.optLong("view")?.takeIf { it > 0 }
                                ?: stat?.optLong("play")?.takeIf { it > 0 }
                                ?: 0,
                            danmaku = stat?.optLong("danmaku")?.takeIf { it > 0 }
                                ?: stat?.optLong("dm")?.takeIf { it > 0 }
                                ?: 0,
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
            } catch (e: Exception) {
                android.util.Log.w("BilibiliApi", "parseVideoCards skip item $i: ${e.message}")
            }
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

    private fun parseDurationValue(obj: JSONObject, key: String): Int {
        val dur = obj.opt(key) ?: return 0
        return when (dur) {
            is Int -> dur
            is Long -> dur.toInt()
            is Number -> dur.toInt()
            is String -> parseDuration(dur)
            else -> 0
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