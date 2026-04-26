package com.bili.tv.bili_tv_app.data.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BiliClient {
    private const val BASE = "https://api.bilibili.com"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    lateinit var cookies: CookieStore
        private set
    lateinit var apiOkHttp: OkHttpClient
        private set

    private var wbiKeys: WbiSigner.Keys? = null

    fun init(context: Context) {
        cookies = CookieStore(context.applicationContext)
        val baseClient = OkHttpClient.Builder()
            .cookieJar(cookies)
            .connectTimeout(12, TimeUnit.SECONDS)
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
                if (original.header("Origin").isNullOrBlank()) {
                    builder.header("Origin", "https://www.bilibili.com")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val response = apiOkHttp.newCall(request).execute()
        val body = response.body?.string() ?: ""
        JSONObject(body)
    }

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
        return keys
    }

    fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): String {
        val base = "$BASE$path"
        val signed = WbiSigner.signQuery(params, keys, nowEpochSec)
        val query = signed.entries.joinToString("&") { (k, v) ->
            "${WbiSigner.enc(k)}=${WbiSigner.enc(v)}"
        }
        return "$base?$query"
    }
}