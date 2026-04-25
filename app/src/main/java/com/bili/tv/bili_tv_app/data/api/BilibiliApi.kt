package com.bili.tv.bili_tv_app.data.api

import com.bili.tv.bili_tv_app.data.model.Video
import com.bili.tv.bili_tv_app.data.model.VideoOwner
import com.bili.tv.bili_tv_app.data.model.VideoStat
import com.bili.tv.bili_tv_app.data.model.PlayUrlResponse
import com.bili.tv.bili_tv_app.data.model.VideoDetailData
import com.bili.tv.bili_tv_app.data.model.EpisodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * 哔哩哔哩 API 服务 - 已修复WBI签名问题
 */
object BilibiliApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // API 基础 URL
    private const val BASE_URL = "https://api.bilibili.com"

    private const val PILI_REFERER = "https://www.bilibili.com"

    // WBI 密钥相关
    private var wbiKeys: WbiSigner.Keys? = null
    private var wbiKeysFetchTime = 0L
    private val WBI_KEYS_CACHE_TTL = 3600 * 1000L // 1小时

    /**
     * 获取推荐视频列表
     */
    suspend fun getRecommendVideos(freshIdx: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val keys = ensureWbiKeys()
            val params = mapOf(
                "ps" to ps.toString(),
                "fresh_idx" to freshIdx.toString(),
                "fresh_idx_1h" to freshIdx.toString(),
                "fetch_row" to "1",
                "feed_version" to "V8"
            )
            val url = signedWbiUrl("/x/web-interface/wbi/index/top/feed/rcmd", params, keys)
            val json = getJson(url)

            // 检查API返回码
            val code = json.optInt("code", 0)
            if (code != 0) {
                val msg = json.optString("message", json.optString("msg", "Unknown error"))
                android.util.Log.e("BilibiliApi", "API error code=$code: $msg")
                return@withContext emptyList()
            }

            val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
            parseVideoCards(items)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取热门视频列表
     */
    suspend fun getPopularVideos(pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/popular?pn=${pn.coerceAtLeast(1)}&ps=${ps.coerceIn(1, 50)}"
            val json = getJson(url)
            val code = json.optInt("code", 0)
            if (code != 0) {
                val msg = json.optString("message", json.optString("msg", ""))
                android.util.Log.e("BilibiliApi", "API error code=$code: $msg")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val list = data.optJSONArray("list") ?: JSONArray()
            parseVideoCards(list)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取分区视频列表
     */
    suspend fun getRegionVideos(tid: Int, pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/dynamic/region?rid=${tid}&pn=${pn.coerceAtLeast(1)}&ps=${ps.coerceIn(1, 50)}"
            val json = getJson(url)
            val code = json.optInt("code", 0)
            if (code != 0) {
                val msg = json.optString("message", json.optString("msg", ""))
                android.util.Log.e("BilibiliApi", "API error code=$code: $msg")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val archives = data.optJSONArray("archives") ?: JSONArray()
            parseVideoCards(archives)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 搜索视频
     */
    suspend fun searchVideos(keyword: String, page: Int = 1): List<Video> = withContext(Dispatchers.IO) {
        try {
            val keys = ensureWbiKeys()
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val params = mapOf(
                "search_type" to "video",
                "keyword" to keyword,
                "page" to page.toString()
            )
            val url = signedWbiUrl("/x/search/type", params, keys)
            val json = getJson(url)

            // 检查API返回码
            val code = json.optInt("code", 0)
            if (code != 0) {
                val msg = json.optString("message", json.optString("msg", "Unknown error"))
                android.util.Log.e("BilibiliApi", "Search API error code=$code: $msg")
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

    /**
     * 获取视频播放地址
     */
    suspend fun getPlayUrl(bvid: String, cid: Long): PlayUrlResponse? = withContext(Dispatchers.IO) {
        try {
            val keys = ensureWbiKeys()
            val params = mapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to "80",
                "fnval" to "4048",
                "fnver" to "0",
                "fourk" to "1"
            )
            val url = signedWbiUrl("/x/player/wbi/playurl", params, keys)
            val json = getJson(url)

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

    /**
     * 获取视频详情（选集列表）
     */
    suspend fun getVideoDetail(bvid: String): VideoDetailData? = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/view?bvid=$bvid"
            val json = getJson(url)
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

    /**
     * 获取弹幕
     */
    suspend fun getDanmaku(cid: Long): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .addHeader("Referer", "https://www.bilibili.com/")
                .build()

            val response = client.newCall(request).execute()
            response.body?.string() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val requestBuilder = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
            .addHeader("Referer", PILI_REFERER)

        headers.forEach { (key, value) ->
            requestBuilder.addHeader(key, value)
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val body = response.body?.string() ?: return JSONObject()
        return JSONObject(body)
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
        return com.bili.tv.bili_tv_app.data.model.PlayUrlResponse(
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

    fun parseDuration(text: String): Int {
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

    // ========== WBI 签名相关 ==========

    /**
     * 确保WBI密钥已获取并缓存
     */
    private fun ensureWbiKeys(): WbiSigner.Keys {
        val now = System.currentTimeMillis() / 1000
        val cached = wbiKeys

        // 检查缓存是否有效（1小时TTL）
        if (cached != null && now - wbiKeysFetchTime < 3600) {
            return cached
        }

        // 重新获取密钥
        val keys = fetchWbiKeys()
        wbiKeys = keys
        wbiKeysFetchTime = now
        return keys
    }

    /**
     * 从B站API获取WBI密钥
     */
    private fun fetchWbiKeys(): WbiSigner.Keys {
        try {
            val json = getJson("${BASE_URL}/x/web-interface/nav")
            val data = json.optJSONObject("data") ?: JSONObject()
            val wbiImg = data.optJSONObject("wbi_img") ?: JSONObject()

            val imgUrl = wbiImg.optString("img_url", "")
            val subUrl = wbiImg.optString("sub_url", "")

            // 从URL中提取密钥
            val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
            val subKey = subUrl.substringAfterLast("/").substringBefore(".")

            if (imgKey.isNotBlank() && subKey.isNotBlank()) {
                return WbiSigner.Keys(
                    imgKey = imgKey,
                    subKey = subKey,
                    fetchedAtEpochSec = System.currentTimeMillis() / 1000
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BilibiliApi", "Failed to fetch WBI keys: ${e.message}")
        }

        // 返回默认密钥作为后备
        return WbiSigner.Keys(
            imgKey = "6Zx2m",
            subKey = "KAt5S",
            fetchedAtEpochSec = System.currentTimeMillis() / 1000
        )
    }

    /**
     * 生成带有WBI签名的URL
     */
    private fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
        // 使用WbiSigner生成签名参数
        val signedParams = WbiSigner.signQuery(params, keys)

        // 构建查询字符串（使用与签名计算一致的编码方式）
        val query = signedParams.entries.joinToString("&") { (key, value) ->
            "${WbiSigner.enc(key)}=${WbiSigner.enc(value)}"
        }

        return "${BASE_URL}${path}?$query"
    }

    // ========== WBI 签名器 (正确实现) ==========
    object WbiSigner {
        private val mixinKeyEncTab = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52,
        )

        data class Keys(
            val imgKey: String,
            val subKey: String,
            val fetchedAtEpochSec: Long,
        )

        /**
         * 生成带签名的查询参数
         */
        fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
            // 生成mixinKey（取imgKey + subKey的前32位）
            val mixinKey = genMixinKey(keys.imgKey + keys.subKey)

            // 添加wts时间戳
            val withWts = params.toMutableMap()
            withWts["wts"] = nowEpochSec.toString()

            // 按key排序并过滤特殊字符
            val sorted = withWts.entries.sortedBy { it.key }.associate { it.key to filterValue(it.value) }

            // 构建查询字符串（URL编码）
            val query = sorted.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

            // 计算w_rid签名
            val wRid = md5Hex(query + mixinKey)

            // 返回带签名的参数
            val out = params.toMutableMap()
            out["wts"] = nowEpochSec.toString()
            out["w_rid"] = wRid
            return out
        }

        /**
         * 生成mixinKey
         */
        private fun genMixinKey(raw: String): String {
            val bytes = raw.toByteArray()
            val sb = StringBuilder(64)
            for (i in mixinKeyEncTab) {
                if (i in bytes.indices) {
                    sb.append(bytes[i].toInt().toChar())
                }
            }
            return sb.toString().substring(0, min(32, sb.length))
        }

        /**
         * 过滤值中的特殊字符
         */
        private fun filterValue(v: String): String = v.filterNot { it in "!\'()*" }

        /**
         * URL编码
         */
        internal fun enc(s: String): String = percentEncodeUtf8(s)

        /**
         * UTF-8 URL编码（类似encodeURIComponent）
         */
        private fun percentEncodeUtf8(s: String): String {
            val bytes = s.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder(bytes.size * 3)
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                val isUnreserved =
                    (c in 'a'.code..'z'.code) ||
                    (c in 'A'.code..'Z'.code) ||
                    (c in '0'.code..'9'.code) ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
                if (isUnreserved) {
                    sb.append(c.toChar())
                } else {
                    sb.append('%')
                    sb.append("0123456789ABCDEF"[c ushr 4])
                    sb.append("0123456789ABCDEF"[c and 0x0F])
                }
            }
            return sb.toString()
        }

        /**
         * MD5哈希
         */
        private fun md5Hex(s: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
            val sb = StringBuilder(digest.size * 2)
            for (b in digest) {
                sb.append(String.format(Locale.US, "%02x", b))
            }
            return sb.toString()
        }
    }
}
