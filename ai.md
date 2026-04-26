这个代码主界面不显示视频列表怎么解决


一、千问
根据分析上传的代码文件，主界面不显示视频列表的主要原因是WBI签名算法问题。B站API对请求进行了严格的签名验证，如果签名不正确会导致请求失败，返回空数据。
核心问题分析
在BilibiliApi.kt文件中的signedWbiUrl方法存在编码不一致的问题：
kotlin

编辑



private fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
    val signed = WbiSigner.signQuery(params, keys)
    return buildString {
        append(BASE_URL)
        append(path)
        append('?')
        signed.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) append('&')
            append(URLEncoder.encode(k, "UTF-8")) // 问题在这里
            append('=')
            append(URLEncoder.encode(v, "UTF-8")) // 问题在这里
        }
    }
}
问题根源：
签名计算时使用的是WbiSigner.enc()自定义编码（类似JavaScript的encodeURIComponent）
但构建URL时使用了Java的URLEncoder.encode()，两者编码规则不同
URLEncoder.encode()会将空格变成+，而B站要求%20
这导致服务器校验签名失败，返回风控错误（通常是code=-352或空数据）
解决方案
1. 修复WBI签名URL构建方法
修改BilibiliApi.kt中的signedWbiUrl方法，使用与签名计算相同的编码方式：
kotlin

编辑



private fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
    val signed = WbiSigner.signQuery(params, keys)
    return buildString {
        append(BASE_URL)
        append(path)
        append('?')
        signed.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) append('&')
            append(WbiSigner.enc(k)) // 使用与签名相同的编码
            append('=')
            append(WbiSigner.enc(v)) // 使用与签名相同的编码
        }
    }
}
2. 将WbiSigner的enc方法改为internal访问级别
在BilibiliApi.kt中找到WbiSigner对象，修改enc方法的访问权限：
kotlin

编辑



private object WbiSigner {
    // ... 其他代码
    
    internal fun enc(s: String): String { // 将private改为internal
        // 编码实现保持不变
        val sb = StringBuilder()
        for (c in s) {
            when {
                c.isLetterOrDigit() || c in "-._~" -> sb.append(c)
                else -> sb.append("%${c.code.toString(16).uppercase()}")
            }
        }
        return sb.toString()
    }
}
3. 补充必要的请求头
在BilibiliApi.kt的getJson方法中，确保包含必要的请求头：
kotlin

编辑



private fun getJson(url: String): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Referer", "https://www.bilibili.com/")
        .header("Origin", "https://www.bilibili.com")
        .header("Connection", "keep-alive")
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        .build()
    
    val response = client.newCall(request).execute()
    if (!response.isSuccessful) throw IOException("Unexpected code $response")
    return response.body?.string() ?: ""
}
4. 添加错误日志以便调试
在HomeFragment.kt的加载方法中添加错误日志：
kotlin

编辑



private fun loadVideos() {
    viewModelScope.launch {
        try {
            loadingView.visibility = View.VISIBLE
            val videos = withContext(Dispatchers.IO) {
                when (currentCategoryId) {
                    0 -> apiClient.getRecommendVideos()
                    1 -> apiClient.getPopularVideos()
                    else -> apiClient.getRegionVideos(currentCategoryId)
                }
            }
            videoAdapter.submitList(videos)
            emptyView.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
        } catch (e: Exception) {
            Log.e("HomeFragment", "加载视频失败: ${e.message}", e)
            emptyView.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "加载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            loadingView.visibility = View.GONE
        }
    }
}
验证方法
重新编译应用：修复代码后重新构建APK
检查网络请求：使用抓包工具（如Charles）查看API请求和响应
查看日志：在Logcat中搜索"HomeFragment"和"BilibiliApi"相关日志
测试不同网络环境：确保在不同网络环境下都能正常加载
额外建议
如果修复后仍然有问题，建议检查以下方面：
Cookie存储：B站可能需要持久化存储Cookie，考虑实现CookieStore
设备指纹：B站可能检测设备信息，确保User-Agent包含合理的设备信息
API版本更新：B站API可能会更新，需要定期检查接口变更
分页逻辑：确保分页加载逻辑正确处理"没有更多数据"的情况
修复WBI签名问题后，主界面的视频列表应该能够正常显示。这是B站TV版应用最常见的问题，也是导致列表为空的最主要原因。

这个代码主界面不显示视频列表怎么解决
﻿



内容由AI生成，可能不准确，请注意核实、、、、、




二、gemini:

Gemini-2.5-Flash
追问
引用
复制
复制MD
重试
根据 trae_auto.md 中的详细分析，以及对现有代码的仔细审查，发现 BiliClient.kt 中 signedWbiUrl 方法的 URL 编码部分已经按照建议进行了修正（即使用 WbiSigner.enc 进行编码，而非 URLEncoder.encode）。这表明 trae_auto.md 之前提出的“双重编码”问题可能已经在当前代码中得到解决。

然而，主界面仍然显示空列表，这通常意味着：

WBI 签名仍未通过：即使编码方式看起来正确，Bilibili 的 WBI 算法可能对参数处理有更严格或隐蔽的要求。特别是 WbiSigner.kt 中的 filterValue 函数，它在计算 MD5 签名之前移除了 !\'()* 这些字符。然而，这些字符在最终的 URL 中通常需要被百分号编码。如果在 MD5 签名字符串中这些字符被移除，但在最终 URL 中被编码，就会导致签名不匹配。
其他网络或解析问题：API 返回了非零错误码（即使签名成功），或者返回的数据结构不符合预期，导致解析失败并返回空列表。
主要修复方向：
基于第一点，最有可能导致 WBI 签名失败的问题是 WbiSigner.kt 中 signQuery 方法里 filterValue 的不当使用。它在 MD5 签名计算前修改了参数值，导致用于签名的字符串与服务器期望的不一致。正确的做法是，所有参数值在参与签名计算（MD5）之前，也应该像最终 URL 一样，使用 WbiSigner.enc 进行统一的百分号编码，而不是先过滤掉某些字符。

次要改进：
为了方便调试和未来排查问题，将增加 API 请求和响应的关键日志输出。

修改大纲：

修改 app/src/main/java/com/bili/tv/bili_tv_app/data/net/WbiSigner.kt：
在 signQuery 方法中，移除 filterValue 对参数值的处理，确保用于 MD5 签名的查询字符串中的键值对都通过 enc() 进行编码。
修改 app/src/main/java/com/bili/tv/bili_tv_app/data/api/BilibiliApi.kt：
添加 android.util.Log 导入。
在所有 API 请求函数（getRecommendVideos, getPopularVideos, getRegionVideos, searchVideos, getPlayUrl, getVideoDetail, getDanmaku）中，增加 Log.d 输出请求的 URL、API 响应的 code 和 message，以及捕获到的异常信息，便于问题诊断。
在 getDanmaku 函数中，将硬编码的 User-Agent 替换为 BiliClient.USER_AGENT，保持一致性。
代码修改：

1. 文件: app/src/main/java/com/bili/tv/bili_tv_app/data/net/WbiSigner.kt

kotlin
复制代码
收起
package com.bili.tv.bili_tv_app.data.net

import java.security.MessageDigest
import java.util.Locale

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

    fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        val mixinKey = genMixinKey(keys.imgKey + keys.subKey)

        val withWts = params.toMutableMap()
        withWts["wts"] = nowEpochSec.toString()

        // --- 核心修改：移除 filterValue 的使用 ---
        // 签名计算时，参数值应该直接通过 enc() 进行编码，而不是先过滤掉特定字符。
        // filterValue 函数本身可以保留或根据其在其他地方的使用情况决定是否删除。
        val sortedEntries = withWts.entries.sortedBy { it.key } // 保持排序

        // 构建用于 MD5 计算的查询字符串，使用 enc() 编码键和值
        val query = sortedEntries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

        val wRid = md5Hex(query + mixinKey)

        val out = params.toMutableMap()
        out["wts"] = nowEpochSec.toString()
        out["w_rid"] = wRid
        return out
    }

    private fun genMixinKey(raw: String): String {
        val bytes = raw.toByteArray()
        val sb = StringBuilder(64)
        for (i in mixinKeyEncTab) {
            if (i in bytes.indices) {
                sb.append(bytes[i].toInt().toChar())
            }
        }
        return sb.toString().substring(0, minOf(32, sb.length))
    }

    // filterValue 函数保留在此，但不再在 signQuery 中使用。
    // 如果它在项目的其他地方没有被使用，可以考虑将其删除以精简代码。
    private fun filterValue(v: String): String = v.filterNot { it in "!\'()*" }

    internal fun enc(s: String): String = percentEncodeUtf8(s)

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

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}
复制
2. 文件: app/src/main/java/com/bili/tv/bili_tv_app/data/api/BilibiliApi.kt

kotlin
复制代码
收起
package com.bili.tv.bili_tv_app.data.api

import android.util.Log // 导入 Log 类
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
            Log.d("BilibiliApi", "Requesting recommend videos from URL: $url") // 添加日志
            val json = BiliClient.getJson(url)

            val code = json.optInt("code", 0)
            val message = json.optString("message", "Unknown error")
            Log.d("BilibiliApi", "Recommend API response code: $code, message: $message, data: ${json.optJSONObject("data")?.toString()?.take(200)}...") // 添加日志，截取部分数据
            if (code != 0) {
                Log.e("BilibiliApi", "API error code=$code: $message")
                return@withContext emptyList()
            }

            val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
            if (items.length() == 0) {
                Log.w("BilibiliApi", "No items found in recommend videos response data.") // 数据为空警告
            }
            parseVideoCards(items)
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error fetching recommend videos", e) // 更详细的错误日志
            emptyList()
        }
    }

    suspend fun getPopularVideos(pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/popular?pn=${pn.coerceAtLeast(1)}&ps=${ps.coerceIn(1, 50)}"
            Log.d("BilibiliApi", "Requesting popular videos from URL: $url") // 添加日志
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", 0)
            val message = json.optString("message", "Unknown error")
            Log.d("BilibiliApi", "Popular API response code: $code, message: $message, data: ${json.optJSONObject("data")?.toString()?.take(200)}...") // 添加日志
            if (code != 0) {
                Log.e("BilibiliApi", "API error code=$code: $message")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val list = data.optJSONArray("list") ?: JSONArray()
            if (list.length() == 0) {
                Log.w("BilibiliApi", "No items found in popular videos response data.")
            }
            parseVideoCards(list)
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error fetching popular videos", e)
            emptyList()
        }
    }

    suspend fun getRegionVideos(tid: Int, pn: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/dynamic/region?rid=${tid}&pn=${pn.coerceAtLeast(1)}&ps=${ps.coerceIn(1, 50)}"
            Log.d("BilibiliApi", "Requesting region videos from URL: $url") // 添加日志
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", 0)
            val message = json.optString("message", "Unknown error")
            Log.d("BilibiliApi", "Region API response code: $code, message: $message, data: ${json.optJSONObject("data")?.toString()?.take(200)}...") // 添加日志
            if (code != 0) {
                Log.e("BilibiliApi", "API error code=$code: $message")
                return@withContext emptyList()
            }
            val data = json.optJSONObject("data") ?: JSONObject()
            val archives = data.optJSONArray("archives") ?: JSONArray()
            if (archives.length() == 0) {
                Log.w("BilibiliApi", "No items found in region videos response data.")
            }
            parseVideoCards(archives)
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error fetching region videos", e)
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
            Log.d("BilibiliApi", "Requesting search videos from URL: $url") // 添加日志
            val json = BiliClient.getJson(url)

            val code = json.optInt("code", 0)
            val message = json.optString("message", "Unknown error")
            Log.d("BilibiliApi", "Search API response code: $code, message: $message, data: ${json.optJSONObject("data")?.toString()?.take(200)}...") // 添加日志
            if (code != 0) {
                Log.e("BilibiliApi", "Search API error code=$code: $message")
                return@withContext emptyList()
            }

            val searchResponse = json.optJSONObject("data") ?: JSONObject()
            val result = searchResponse.optJSONArray("result") ?: JSONArray()
            if (result.length() == 0) {
                Log.w("BilibiliApi", "No items found in search videos response data.")
            }
            parseSearchResults(result)
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error searching videos", e)
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
            Log.d("BilibiliApi", "Requesting play URL from URL: $url") // 添加日志
            val json = BiliClient.getJson(url)

            val code = json.optInt("code", 0)
            val message = json.optString("message", "Unknown error")
            Log.d("BilibiliApi", "PlayUrl API response code: $code, message: $message, data: ${json.optJSONObject("data")?.toString()?.take(200)}...") // 添加日志
            if (code != 0) {
                Log.e("BilibiliApi", "PlayUrl API error code=$code: $message")
                return@withContext null
            }
            parsePlayUrl(json)
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error getting play URL", e)
            null
        }
    }

    suspend fun getVideoDetail(bvid: String): VideoDetailData? = withContext(Dispatchers.IO) {
        try {
            val url = "${BASE_URL}/x/web-interface/view?bvid=$bvid"
            Log.d("BilibiliApi", "Requesting video detail from URL: $url") // 添加日志
            val json = BiliClient.getJson(url)
            val code = json.optInt("code", 0)
            val message = json.optString("message", "Unknown error")
            Log.d("BilibiliApi", "VideoDetail API response code: $code, message: $message, data: ${json.optJSONObject("data")?.toString()?.take(200)}...") // 添加日志
            if (code != 0) {
                Log.e("BilibiliApi", "VideoDetail API error code=$code: $message")
                return@withContext null
            }
            parseVideoDetail(json)
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error getting video detail", e)
            null
        }
    }

    suspend fun getDanmaku(cid: Long): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.bilibili.com/x/v1/dm/list.so?oid=$cid"
            Log.d("BilibiliApi", "Requesting danmaku from URL: $url") // 添加日志
            val request = okhttp3.Request.Builder()
                .url(url)
                // --- 核心修改：使用 BiliClient.USER_AGENT 保持一致性 ---
                .addHeader("User-Agent", com.bili.tv.bili_tv_app.data.net.BiliClient.USER_AGENT)
                .addHeader("Referer", "https://www.bilibili.com/")
                .build()

            val response = BiliClient.apiOkHttp.newCall(request).execute()
            val body = response.body?.string() ?: ""
            Log.d("BilibiliApi", "Danmaku API response body length: ${body.length}") // 添加日志
            body
        } catch (e: Exception) {
            Log.e("BilibiliApi", "Error getting danmaku", e)
            ""
        }
    }

    private fun parseVideoCards(arr: JSONArray): List<Video> {
        val out = ArrayList<Video>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val bvid = obj.optString("bvid", "").trim()
            if (bvid.isBlank()) {
                Log.w("BilibiliApi", "Skipping video card due to blank BVID: ${obj.toString().take(100)}")
                continue
            }

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
            if (bvid.isBlank()) {
                Log.w("BilibiliApi", "Skipping search result due to blank BVID: ${obj.toString().take(100)}")
                continue
            }

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


三、kimi:
我来分析这个 BiliTV 项目主界面空列表的问题。从代码来看，这是一个 Android TV 版的哔哩哔哩客户端，核心问题是视频列表无法显示。
让我检查关键代码，找出根本原因：
问题分析
1. WBI 签名 URL 构建存在编码不一致（关键问题）
在 BiliClient.kt 中的 signedWbiUrl 方法：
kotlin
复制
fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): String {
    val base = "$BASE$path"
    val signed = WbiSigner.signQuery(params, keys, nowEpochSec)
    val query = signed.entries.joinToString("&") { (k, v) ->
        "${WbiSigner.enc(k)}=${WbiSigner.enc(v)}"  // 使用签名时的相同编码
    }
    return "$base?$query"
}
这里看起来使用了 WbiSigner.enc()，但让我检查 WbiSigner.kt 中的 enc 方法可见性...
kotlin
复制
internal fun enc(s: String): String = percentEncodeUtf8(s)
enc 是 internal 的，可以被同一模块访问，这部分看起来是正确的。
但是！让我仔细看 BilibiliApi.kt 中的调用：
kotlin
复制
val url = BiliClient.signedWbiUrl(
    path = "/x/web-interface/wbi/index/top/feed/rcmd",
    params = mapOf(...),
    keys = keys
)
val json = BiliClient.getJson(url)
而 getJson 方法：
kotlin
复制
suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .get()
        .build()
    val response = apiOkHttp.newCall(request).execute()
    val body = response.body?.string() ?: ""
    JSONObject(body)
}
问题 1：请求缺少必要的 Headers
getJson 没有添加 User-Agent、Referer、Origin 等 headers！虽然 apiOkHttp 配置了拦截器，但拦截器只在通过 apiOkHttp 发起的请求中生效。而 BilibiliApi.kt 中的 getDanmaku 方法直接使用了 BiliClient.apiOkHttp.newCall(request)，这是正确的。
但 getJson 使用的是 apiOkHttp.newCall(request)，应该也会经过拦截器... 等等，让我重新看：
kotlin
复制
apiOkHttp = baseClient.newBuilder()
    .addInterceptor { chain -> ... }
    .build()
是的，getJson 使用 apiOkHttp，会经过拦截器，所以 headers 应该没问题。
2. 真正的核心问题：WBI 签名参数处理
让我重新审视 WbiSigner.signQuery：
kotlin
复制
fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
    val mixinKey = genMixinKey(keys.imgKey + keys.subKey)

    val withWts = params.toMutableMap()
    withWts["wts"] = nowEpochSec.toString()

    val sorted = withWts.entries.sortedBy { it.key }.associate { it.key to filterValue(it.value) }
    val query = sorted.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    val wRid = md5Hex(query + mixinKey)

    val out = params.toMutableMap()
    out["wts"] = nowEpochSec.toString()
    out["w_rid"] = wRid
    return out
}
问题 2：签名计算时使用了 filterValue，但最终返回的参数值没有过滤
签名计算时：
kotlin
复制
val sorted = withWts.entries.sortedBy { it.key }.associate { it.key to filterValue(it.value) }
但返回的 out 中：
kotlin
复制
val out = params.toMutableMap()
out["wts"] = nowEpochSec.toString()
out["w_rid"] = wRid
返回的参数值没有经过 filterValue 处理！ 如果原始参数值中包含 !'()* 字符，签名时过滤了，但实际请求时没过滤，会导致签名不匹配。
3. URL 构建时的编码问题
在 BiliClient.signedWbiUrl 中：
kotlin
复制
val query = signed.entries.joinToString("&") { (k, v) ->
    "${WbiSigner.enc(k)}=${WbiSigner.enc(v)}"
}
这里对参数值进行了编码。但 WbiSigner.enc 使用的是自定义的百分比编码。而 signQuery 中签名计算时也使用了 enc 进行编码。这两者是相同的，所以编码应该一致。
但是，signQuery 签名时使用的是 filterValue(it.value) 处理后的值，而 signedWbiUrl 中编码的是原始值（没有经过 filterValue）。如果参数值包含 !'()*，就会出现不一致。
4. 推荐接口参数可能不正确
kotlin
复制
suspend fun getRecommendVideos(freshIdx: Int = 1, ps: Int = 20): List<Video> = withContext(Dispatchers.IO) {
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
参数看起来是合理的。但 fresh_idx 和 fresh_idx_1h 是否应该相同？这个可能需要根据实际 API 调整。
5. 缺少 buvid3 等设备指纹
B站 API 现在通常需要 buvid3 等 Cookie 才能正常返回数据。当前 CookieStore 只保存了从响应中获取的 Cookie，但没有主动生成 buvid3。
6. 解析逻辑可能有问题
kotlin
复制
val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
推荐接口返回的数据结构可能是 data.item 数组。但如果 API 返回了错误（如 -352 风控），data 可能为 null 或没有 item 字段。
解决方案
修复 1：修正 WBI 签名，确保过滤后的值用于 URL 构建
修改 WbiSigner.kt：
kotlin
复制
fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
    val mixinKey = genMixinKey(keys.imgKey + keys.subKey)

    val withWts = params.toMutableMap()
    withWts["wts"] = nowEpochSec.toString()

    // 过滤值用于签名计算
    val filtered = withWts.mapValues { filterValue(it.value) }
    val sorted = filtered.entries.sortedBy { it.key }
    val query = sorted.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    val wRid = md5Hex(query + mixinKey)

    // 返回的参数值也要过滤，确保一致性
    val out = filtered.toMutableMap()
    out["wts"] = nowEpochSec.toString()  // 重新设置，确保没被过滤
    out["w_rid"] = wRid
    return out
}
修复 2：添加 buvid3 生成和基础 Cookie
修改 CookieStore.kt，添加 buvid3 生成：
kotlin
复制
class CookieStore(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("bili_cookies", Context.MODE_PRIVATE)
    private val memoryStore = ConcurrentHashMap<String, List<Cookie>>()

    init {
        loadFromDisk()
        ensureBuvid3()
    }

    private fun ensureBuvid3() {
        val now = System.currentTimeMillis()
        val hasBuvid3 = memoryStore.values.flatten().any { it.name == "buvid3" && it.expiresAt >= now }
        if (!hasBuvid3) {
            val buvid3 = generateBuvid3()
            val cookie = Cookie.Builder()
                .name("buvid3")
                .value(buvid3)
                .domain(".bilibili.com")
                .path("/")
                .expiresAt(now + 365 * 24 * 60 * 60 * 1000L)
                .build()
            val current = memoryStore["api.bilibili.com"]?.toMutableList() ?: mutableListOf()
            current.add(cookie)
            memoryStore["api.bilibili.com"] = current
            saveToDisk()
        }
    }

    private fun generateBuvid3(): String {
        val chars = "ABCDEF0123456789"
        val sb = StringBuilder("XX")
        repeat(35) { sb.append(chars.random()) }
        sb.append("infoc")
        return sb.toString()
    }
    // ... 其余代码不变
}
修复 3：增强日志输出，便于调试
修改 BilibiliApi.kt 中的 getRecommendVideos：
kotlin
复制
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
        
        android.util.Log.d("BilibiliApi", "Request URL: $url")
        
        val json = BiliClient.getJson(url)
        
        android.util.Log.d("BilibiliApi", "Response: ${json.toString(2)}")

        val code = json.optInt("code", 0)
        if (code != 0) {
            android.util.Log.e("BilibiliApi", "API error code=$code: ${json.optString("message")}")
            return@withContext emptyList()
        }

        val items = json.optJSONObject("data")?.optJSONArray("item") ?: JSONArray()
        parseVideoCards(items)
    } catch (e: Exception) {
        android.util.Log.e("BilibiliApi", "Exception: ${e.message}", e)
        e.printStackTrace()
        emptyList()
    }
}
修复 4：确保请求头完整
检查 BiliClient.kt 的拦截器，确保所有必要 headers 都已添加：
kotlin
复制
apiOkHttp = baseClient.newBuilder()
    .addInterceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("User-Agent").isNullOrBlank()) {
            builder.header("User-Agent", USER_AGENT)
        }
        if (original.header("Referer").isNullOrBlank()) {
            builder.header("Referer", "https://www.bilibili.com/")
        }
        if (original.header("Origin").isNullOrBlank()) {
            builder.header("Origin", "https://www.bilibili.com")
        }
        // 添加 Accept header
        if (original.header("Accept").isNullOrBlank()) {
            builder.header("Accept", "application/json, text/plain, */*")
        }
        // 添加 Accept-Language
        if (original.header("Accept-Language").isNullOrBlank()) {
            builder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }
        chain.proceed(builder.build())
    }
    .build()
修复 5：检查并修正数据解析
推荐接口的返回结构可能有所不同。建议添加更详细的解析：
kotlin
复制
private fun parseVideoCards(arr: JSONArray): List<Video> {
    val out = ArrayList<Video>()
    for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        
        // 推荐接口的 item 可能包含不同的字段名
        val bvid = obj.optString("bvid", "").trim()
        if (bvid.isBlank()) continue

        // 有些接口返回 cover 而不是 pic
        val pic = obj.optString("pic", obj.optString("cover", ""))
        
        // 处理 owner 可能为 null 的情况
        val owner = obj.optJSONObject("owner")
        val stat = obj.optJSONObject("stat")

        // duration 可能是秒数，也可能是字符串
        val durationVal = obj.opt("duration")
        val durationSec = when (durationVal) {
            is Int -> durationVal
            is Long -> durationVal.toInt()
            is String -> parseDuration(durationVal)
            else -> 0
        }

        out.add(
            Video(
                bvid = bvid,
                aid = obj.optLong("aid").takeIf { it > 0 } ?: 0,
                cid = obj.optLong("cid").takeIf { it > 0 } ?: 0,
                title = obj.optString("title", ""),
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
                        ?: obj.optLong("play_count").takeIf { it > 0 }
                        ?: 0,
                    danmaku = stat?.optLong("danmaku")?.takeIf { it > 0 } ?: 0,
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
最可能的根本原因
根据 trae_auto.md 中的分析，最关键的问题是：
"你在 signedWbiUrl 里用 URLEncoder.encode 拼接参数，但签名计算时用的是 WbiSigner 里自定义的 enc 编码。这两种编码规则不同（比如 URLEncoder 会把空格变成 +，而 B 站要求 %20），导致最终请求的 URL 和签名时的参数不一致，服务器校验签名失败，依然返回风控错误。"
但查看当前代码，signedWbiUrl 已经使用了 WbiSigner.enc，而不是 URLEncoder.encode。所以这个问题可能已经修复。
当前最可能的原因是缺少 buvid3 Cookie 或请求被风控（-352 错误）。
建议的排查步骤
添加详细日志，查看 API 返回的具体内容（code、message）
检查是否有 -352 错误，这表示风控拦截
添加 buvid3 生成，这是 B 站识别设备的基础
尝试使用热门接口（不需要 WBI 签名）测试是否能获取数据：
kotlin
复制
// 测试热门接口是否能返回数据
suspend fun testPopular(): Boolean {
    val url = "${BASE_URL}/x/web-interface/popular?pn=1&ps=20"
    val json = BiliClient.getJson(url)
    val code = json.optInt("code", -1)
    android.util.Log.d("TEST", "Popular API code=$code, data=${json.optJSONObject("data")?.optJSONArray("list")?.length()}")
    return code == 0
}
如果热门接口能返回数据但推荐接口不行，说明 WBI 签名仍有问题；如果都不行，说明是更基础的 Cookie/Header 问题。



四、glm:
你提供的仅仅是项目 `build` 目录下的**构建产物目录树**（自动生成的文件），并没有包含任何实际的**业务源代码**（如 `HomeFragment.kt`、`VideoAdapter.kt`、布局 XML 等）。

因此，仅凭这个目录树，我无法直接指出你代码里的具体哪一行写错了。不过，根据目录树暴露出的项目架构（使用了 DataBinding、RecyclerView、ExoPlayer、B站API、WBI签名等），我可以为你总结出**Android开发中“列表不显示”最常见的几个原因及排查步骤**。

请按照以下步骤逐一排查：

### 第一步：查看 Logcat 日志（最关键的一步）
在 Android Studio 的 Logcat 中，过滤你的包名 `com.bili.tv.bili_tv_app` 或关键字 `OkHttp`、`BiliClient`。
* **如果看到 HTTP 400 / 403 / 412 错误**：说明请求被B站服务器拒绝了。
  * **重点排查**：目录树中有 `WbiSigner` 和 `CookieStore`。B站现在对大部分接口强制要求 **WBI 签名**。如果你的 `img_key` 和 `sub_key` 没有正确获取，或者拼接签名算法有误，接口会直接返回错误码（通常是 `-352` 或 `412`），导致拿不到数据，列表自然为空。
* **如果看到 JSON 解析异常**：说明接口返回的数据结构和你定义的 `PopularData`、`Video` 等 data class 不匹配，导致解析失败。
* **如果没有任何网络请求日志**：说明你的请求根本没发出去，可能是 `HomeFragment` 中的 `loadVideos()` 没有被调用，或者生命周期有问题。

### 第二步：检查布局文件 (`fragment_home.xml`)
很多情况下列表数据其实已经加载出来了，但是**被遮挡了**或者**高度为0**。
* **RecyclerView 的高度**：确保它不是 `wrap_content`（在某些嵌套布局中会导致高度为0）。如果使用了 `ConstraintLayout`，确保它有类似 `app:layout_constraintBottom_toBottomOf="parent"` 的约束，高度设为 `0dp`。
* **缺少 LayoutManager**：确保在 XML 中写了 `app:layoutManager="androidx.recyclerview.widget.GridLayoutManager"`，或者在代码中显式设置了 `layoutManager`。如果没有 LayoutManager，RecyclerView 什么都不显示。
* **可见性**：确保没有写 `android:visibility="gone"`。

### 第三步：检查适配器逻辑 (`VideoAdapter.kt`)
* **是否设置了 Adapter？** 在 `HomeFragment` 中是否有 `recyclerView.adapter = videoAdapter`？
* **是否更新了数据？** 在网络请求成功后，是否调用了 `videoAdapter.notifyDataSetChanged()` 或使用了 `ListAdapter` 的 `submitList()`？
* **DataBinding 绑定问题**：因为你的项目使用了 DataBinding（存在 `ItemVideoCardBinding`），检查 `item_video_card.xml` 中的变量名是否和 Adapter 中 `setVariable` 的名字完全一致。如果图片加载框架（如 Glide/Coil）没配置好，可能只显示空白。

### 第四步：检查网络请求与数据流转 (`HomeFragment.kt`)
在 `HomeFragment` 中打断点或打印 Log：
1. 进入页面时，`loadVideos()` 或 `loadMoreVideos()` 有没有执行？
2. 拿到 API 返回的 JSON 后，转成的 `List<Video>` 里面的 `size()` 是 0 还是有值？
3. 如果有值，传给 Adapter 的那一瞬间数据有没有丢失？

---

### 💡 如何让我帮你精准解决？

如果你想让我直接给出修改后的代码，请将以下**源代码文件**的内容粘贴出来（不要发 build 目录，发 `app/src/main/java` 和 `app/src/main/res/layout` 下的文件）：

1. **`app/src/main/res/layout/fragment_home.xml`** (首页布局)
2. **`app/src/main/java/.../ui/home/HomeFragment.kt`** (首页逻辑，特别是请求网络和设置列表的部分)
3. **`app/src/main/java/.../ui/home/VideoAdapter.kt`** (列表适配器)
4. **`app/src/main/java/.../data/net/BiliClient.kt`** (网络客户端，特别是 WBI 签名的实现)
5. **Logcat 中的报错信息**（如果有的话）

**大概率猜测**：作为一个B站第三方客户端，90%的概率是 **B站 API 的 WBI 签名未正确实现**，或者**接口请求参数缺少必要的 Cookie/Header** 导致返回了空数据或错误码。



五、deepseek

根据分析，主界面不显示视频列表的主要原因是**WBI签名编码不一致**导致签名校验失败，服务器返回风控或空数据。具体来说，`BiliClient.kt` 中的 `signedWbiUrl` 方法使用了 `URLEncoder.encode` 构建查询字符串，而签名计算时使用的是 `WbiSigner.enc`（自定义百分号编码），两种编码规则不同（例如空格变成 `+` vs `%20`），导致最终请求的 URL 与签名时的参数不一致，B站服务器拒绝响应或返回空列表。

## 解决方案

### 1. 统一编码方式（核心修复）

修改 `WbiSigner.kt` 和 `BiliClient.kt`，使签名计算和 URL 构建使用完全相同的编码函数。

**① 修改 `WbiSigner.kt`：将 `enc` 方法改为 internal 可见性**（以便在 `BiliClient` 中复用）

```kotlin
// 在 WbiSigner object 内部
internal fun enc(s: String): String = percentEncodeUtf8(s)
```

**② 修改 `BiliClient.kt` 中的 `signedWbiUrl` 方法**（删除 `URLEncoder`，使用 `WbiSigner.enc`）

```kotlin
// 原代码（错误）
// fun signedWbiUrl(...): String {
//     val signed = WbiSigner.signQuery(params, keys)
//     val query = signed.entries.joinToString("&") { (k, v) ->
//         "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
//     }
//     return "$BASE$path?$query"
// }

// 修改为（正确）
fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
    val signed = WbiSigner.signQuery(params, keys)
    return buildString {
        append(BASE)   // BASE = "https://api.bilibili.com"
        append(path)
        append('?')
        signed.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) append('&')
            append(WbiSigner.enc(k))
            append('=')
            append(WbiSigner.enc(v))
        }
    }
}
```

### 2. 增强请求头与设备指纹（可选但推荐）

B站部分接口对 `User-Agent` 和 Cookie (`buvid3`, `buvid4`) 敏感。建议在 `BiliClient.init()` 中添加默认的设备指纹 Cookie。

在 `CookieStore` 中添加一个方法：
```kotlin
fun ensureDeviceFingerprint() {
    if (getCookieValue("buvid3") == null) {
        val buvid3 = "XY" + System.currentTimeMillis() + "abcde" // 生成随机字符串
        val cookie = Cookie.Builder()
            .name("buvid3")
            .value(buvid3)
            .domain(".bilibili.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 365 * 24 * 3600 * 1000L)
            .build()
        memoryStore[".bilibili.com"] = listOf(cookie)
        saveToDisk()
    }
}
```

在 `BiliClient.init()` 末尾调用 `cookies.ensureDeviceFingerprint()`。

### 3. 添加调试日志（便于排查）

临时在 `BilibiliApi.getRecommendVideos()` 中添加日志，输出响应内容：

```kotlin
val json = BiliClient.getJson(url)
android.util.Log.e("BiliApi", "Response: $json")
```

查看 logcat 中返回的 `code` 和 `message`。如果 code 为 `-352` 或 `-404`，说明签名或风控仍有问题，请再次检查步骤 1 的编码是否完全替换。

### 4. 验证修改后的效果

重新编译并安装 APK，打开应用。如果首页仍为空，请检查：

- 是否清除了应用缓存/数据（旧 Cookie 可能导致异常）
- 是否开启了网络代理（部分代理会干扰请求）
- 可尝试切换到“热门”分类（`getPopularVideos` 对签名要求较低），若热门能显示，则证明签名修复成功，推荐接口可能需要额外参数或登录态。

## 完整修改文件示例

### `app/src/main/java/com/bili/tv/bili_tv_app/data/net/WbiSigner.kt`
```kotlin
// 仅修改 enc 的可见性
object WbiSigner {
    // ... 其他代码不变 ...

    internal fun enc(s: String): String = percentEncodeUtf8(s)   // 改为 internal

    private fun percentEncodeUtf8(s: String): String { /* 不变 */ }
    // ...
}
```

### `app/src/main/java/com/bili/tv/bili_tv_app/data/net/BiliClient.kt`
```kotlin
object BiliClient {
    private const val BASE = "https://api.bilibili.com"

    // ... 其他代码不变 ...

    fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
        val signed = WbiSigner.signQuery(params, keys)
        return buildString {
            append(BASE)
            append(path)
            append('?')
            signed.entries.forEachIndexed { index, (k, v) ->
                if (index > 0) append('&')
                append(WbiSigner.enc(k))
                append('=')
                append(WbiSigner.enc(v))
            }
        }
    }
}
```

## 总结

核心问题是**签名编码不一致**导致 API 请求被拒绝。按照上述步骤修改后，视频列表应能正常加载。若问题依然存在，请检查日志输出并反馈具体的 `code` 值。


六
豆包

BiliTV 主界面不显示视频列表的完整解决方案
根据代码分析，主界面空列表问题主要由签名编码不一致、缺少设备指纹 Cookie和API 错误静默失败三个核心原因导致。以下是分步修复方案：
一、修复签名编码不一致问题（最关键）
问题根源
在BiliClient.kt的signedWbiUrl方法中，虽然已经改为使用WbiSigner.enc编码，但WbiSigner.kt的signQuery方法存在参数过滤不一致的问题：签名计算时使用了过滤后的参数值，但返回给 URL 构建的是原始参数值。
修复步骤
修改app/src/main/java/com/bili/tv/bili_tv_app/data/net/WbiSigner.kt中的signQuery方法：
kotlin
fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
    val mixinKey = genMixinKey(keys.imgKey + keys.subKey)
    val withWts = params.toMutableMap()
    withWts["wts"] = nowEpochSec.toString()
    
    // 对所有参数值进行过滤，确保签名和请求使用相同的值
    val filteredParams = withWts.mapValues { filterValue(it.value) }
    val sorted = filteredParams.entries.sortedBy { it.key }
    val query = sorted.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    val wRid = md5Hex(query + mixinKey)
    
    // 返回过滤后的参数 + 签名
    val out = filteredParams.toMutableMap()
    out["w_rid"] = wRid
    return out
}
确保WbiSigner.enc方法是internal可见性（已在代码中正确设置）。
二、添加自动生成设备指纹 Cookie（buvid3）
问题根源
Bilibili API 要求请求必须包含buvid3设备指纹 Cookie，否则会返回空数据或风控错误。
修复步骤
在BiliClient.kt中添加生成 buvid3 的方法：
kotlin
import java.util.UUID

// 在BiliClient对象中添加
private fun generateBuvid3(): String {
    val uuid = UUID.randomUUID().toString().replace("-", "")
    return "XY${uuid.substring(0, 35)}"
}

// 修改init方法，在初始化时检查并设置buvid3
fun init(context: Context) {
    cookies = CookieStore(context.applicationContext)
    
    // 检查是否已有buvid3，没有则生成
    if (cookies.getCookieValue("buvid3") == null) {
        val buvid3 = generateBuvid3()
        val cookie = Cookie.Builder()
            .name("buvid3")
            .value(buvid3)
            .domain(".bilibili.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000) // 1年有效期
            .build()
        cookies.saveFromResponse(HttpUrl.parse("https://www.bilibili.com/")!!, listOf(cookie))
    }
    
    // 原有初始化代码...
}
三、添加 API 错误提示和日志增强
问题根源
当前代码在 API 调用失败时会静默返回空列表，用户无法知道具体错误原因。
修复步骤
修改BilibiliApi.kt中的所有 API 方法，添加更详细的错误日志：
kotlin
// 以getRecommendVideos为例，其他方法类似修改
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
        
        android.util.Log.d("BilibiliApi", "Request URL: $url")
        
        val json = BiliClient.getJson(url)
        val code = json.optInt("code", -1)
        val message = json.optString("message", "未知错误")
        
        android.util.Log.d("BilibiliApi", "Response: $json")
        
        if (code != 0) {
            android.util.Log.e("BilibiliApi", "API error code=$code: $message")
            return@withContext emptyList()
        }
        
        val data = json.optJSONObject("data")
        if (data == null) {
            android.util.Log.e("BilibiliApi", "Response data is null")
            return@withContext emptyList()
        }
        
        val items = data.optJSONArray("item") ?: JSONArray()
        android.util.Log.d("BilibiliApi", "Found ${items.length()} videos")
        
        parseVideoCards(items)
    } catch (e: Exception) {
        android.util.Log.e("BilibiliApi", "Network error", e)
        emptyList()
    }
}
修改HomeFragment.kt，在 API 失败时显示 Toast 提示：
kotlin
private fun loadVideos() {
    if (isLoading) return
    isLoading = true
    showLoading(true)
    lifecycleScope.launch {
        val videos = try {
            when (currentCategory) {
                0 -> BilibiliApi.getRecommendVideos()
                1 -> BilibiliApi.getPopularVideos(currentPage)
                else -> BilibiliApi.getRegionVideos(categoryTids[currentCategory], currentPage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
            emptyList()
        }
        isLoading = false
        showLoading(false)
        if (videos.isEmpty()) {
            showEmpty(true)
            Toast.makeText(context, "暂无视频数据，请检查网络或稍后重试", Toast.LENGTH_SHORT).show()
        } else {
            showEmpty(false)
            videoAdapter.setVideos(videos)
        }
    }
}
四、其他优化和修复
1. 修复热门和分区接口的签名问题
当前getPopularVideos和getRegionVideos方法没有使用 WBI 签名，Bilibili 已逐步要求所有接口都需要签名。修改这两个方法：
kotlin
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
        // 原有解析代码...
    } catch (e: Exception) {
        e.printStackTrace()
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
        // 原有解析代码...
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}
2. 修复视频解析中的 cid 问题
在parseVideoCards方法中，cid 字段可能在不同 API 返回中位置不同，添加更全面的解析：
kotlin
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
        
        // 修复cid解析
        var cid = obj.optLong("cid").takeIf { it > 0 } ?: 0
        if (cid == 0L) {
            // 尝试从pages中获取第一个cid
            val pages = obj.optJSONArray("pages")
            if (pages != null && pages.length() > 0) {
                cid = pages.optJSONObject(0)?.optLong("cid") ?: 0
            }
        }
        
        out.add(
            Video(
                bvid = bvid,
                aid = obj.optLong("aid").takeIf { it > 0 } ?: 0,
                cid = cid,
                // 其他字段...
            )
        )
    }
    return out
}
五、验证和测试
清理应用数据和缓存（重要！确保旧的 Cookie 和 WBI 密钥被清除）
重新编译并安装应用
查看 Logcat 中的 "BilibiliApi" 标签，确认：
请求 URL 包含正确的wts和w_rid参数
响应 code 为 0
响应 data.item 数组不为空
如果仍然有问题，检查：
网络连接是否正常
是否被 Bilibili 风控（尝试切换网络或等待一段时间）
User-Agent 是否正确（当前使用的是 Chrome Android UA，一般没问题）
六、最终验证
完成以上修复后，应用应该能够正常显示视频列表。如果仍然有问题，可以：
尝试使用浏览器访问生成的 API URL，看是否能正常返回数据
检查 WBI 签名是否正确（可以使用在线工具验证）
确认设备时间是否正确（WBI 签名依赖时间戳）
需要我帮你生成完整的修改后文件，直接替换即可解决问题吗？
