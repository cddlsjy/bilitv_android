package com.bili.tv.bili_tv_app.data.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BiliClient {
    private const val BASE = "https://api.bilibili.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

    lateinit var cookies: CookieStore
        private set
    lateinit var apiOkHttp: OkHttpClient
        private set

    private var wbiKeys: WbiSigner.Keys? = null

    fun init(context: Context) {
        cookies = CookieStore(context.applicationContext)
        val baseClient = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

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
                chain.proceed(builder.build())
            }
            .build()
    }

    /**
     * 构建带查询参数的 URL - 使用 OkHttp 的 HttpUrl.Builder
     */
    fun buildUrl(path: String, params: Map<String, String>): String {
        val urlBuilder = "$BASE$path".toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        return urlBuilder.build().toString()
    }

    /**
     * 构建 WBI 签名 URL
     */
    fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
        val signedParams = WbiSigner.signQuery(params, keys)
        return buildUrl(path, signedParams)
    }

    /**
     * 发送 GET 请求
     */
    suspend fun getJsonWithCookie(url: String): JSONObject = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .get()

            // 如果有 SESSDATA，显式添加 Cookie 头
            val sessData = cookies.getCookieValue("SESSDATA")
            if (!sessData.isNullOrBlank()) {
                requestBuilder.header("Cookie", "SESSDATA=$sessData")
            }

            val response = apiOkHttp.newCall(requestBuilder.build()).execute()
            val code = response.code
            val body = response.body?.string() ?: ""
            if (body.isBlank()) {
                android.util.Log.w("BiliClient", "Empty response for $url, code=$code")
                return@withContext JSONObject().put("code", -1).put("message", "Empty response")
            }
            android.util.Log.d("BiliClient", "Response code=$code for ${url.substringBefore('?')}")
            try {
                JSONObject(body)
            } catch (e: Exception) {
                android.util.Log.e("BiliClient", "Invalid JSON for $url: ${body.substring(0, minOf(200, body.length))}")
                JSONObject().put("code", -1).put("message", "Invalid JSON response")
            }
        } catch (e: Exception) {
            android.util.Log.e("BiliClient", "Network error for $url: ${e.message}", e)
            JSONObject().put("code", -1).put("message", "Network error: ${e.message}")
        }
    }

    suspend fun getJson(url: String): JSONObject = getJsonWithCookie(url)

    suspend fun ensureWbiKeys(): WbiSigner.Keys {
        val cached = wbiKeys
        val nowSec = System.currentTimeMillis() / 1000
        if (cached != null && nowSec - cached.fetchedAtEpochSec < 12 * 60 * 60) {
            return cached
        }

        val url = "$BASE/x/web-interface/nav"
        val json = getJson(url)
        val data = json.optJSONObject("data") ?: JSONObject()
        val wbi = data.optJSONObject("wbi_img") ?: JSONObject()
        val imgUrl = wbi.optString("img_url", "")
        val subUrl = wbi.optString("sub_url", "")
        val imgKey = imgUrl.substringAfterLast('/').substringBefore('.')
        val subKey = subUrl.substringAfterLast('/').substringBefore('.')
        val keys = WbiSigner.Keys(imgKey = imgKey, subKey = subKey, fetchedAtEpochSec = nowSec)
        wbiKeys = keys
        android.util.Log.d("BiliClient", "WBI keys updated: imgKey=$imgKey, subKey=$subKey")
        return keys
    }
}
