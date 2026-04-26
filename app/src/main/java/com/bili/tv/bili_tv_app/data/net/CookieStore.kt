package com.bili.tv.bili_tv_app.data.net

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class CookieStore(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("bili_cookies", Context.MODE_PRIVATE)
    private val memoryStore = ConcurrentHashMap<String, List<Cookie>>()

    init {
        loadFromDisk()
        ensureBuvid3()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        memoryStore[url.host] = cookies
        saveToDisk()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return memoryStore[url.host] ?: emptyList()
    }

    private fun saveToDisk() {
        val root = JSONObject()
        for ((host, cookies) in memoryStore) {
            val arr = JSONArray()
            for (cookie in cookies) {
                val obj = JSONObject()
                obj.put("name", cookie.name)
                obj.put("value", cookie.value)
                obj.put("domain", cookie.domain)
                obj.put("path", cookie.path)
                obj.put("expiresAt", cookie.expiresAt)
                obj.put("secure", cookie.secure)
                obj.put("httpOnly", cookie.httpOnly)
                arr.put(obj)
            }
            if (arr.length() > 0) root.put(host, arr)
        }
        prefs.edit().putString("cookies", root.toString()).apply()
    }

    private fun loadFromDisk() {
        val raw = prefs.getString("cookies", null) ?: return
        try {
            val root = JSONObject(raw)
            val it = root.keys()
            while (it.hasNext()) {
                val host = it.next()
                val arr = root.optJSONArray(host) ?: continue
                val list = mutableListOf<Cookie>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val builder = Cookie.Builder()
                        .name(obj.getString("name"))
                        .value(obj.getString("value"))
                        .path(obj.optString("path", "/"))

                    val domain = obj.optString("domain", host)
                    builder.domain(domain)

                    if (obj.optBoolean("secure", false)) builder.secure()
                    if (obj.optBoolean("httpOnly", false)) builder.httpOnly()

                    val expiresAt = obj.optLong("expiresAt", 0L)
                    if (expiresAt > 0L) builder.expiresAt(expiresAt)

                    list.add(builder.build())
                }
                if (list.isNotEmpty()) memoryStore[host] = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasSessData(): Boolean {
        val now = System.currentTimeMillis()
        return memoryStore.values.flatten().any { it.name == "SESSDATA" && it.expiresAt >= now }
    }

    fun getCookieValue(name: String): String? {
        val now = System.currentTimeMillis()
        return memoryStore.values.flatten().firstOrNull { it.name == name && it.expiresAt >= now }?.value
    }

    fun clearAll() {
        memoryStore.clear()
        prefs.edit().clear().apply()
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
                .expiresAt(now + 365L * 24 * 3600 * 1000)
                .build()
            val host = "api.bilibili.com"
            val current = memoryStore[host]?.toMutableList() ?: mutableListOf()
            current.add(cookie)
            memoryStore[host] = current
            saveToDisk()
        }
    }

    private fun generateBuvid3(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return "XY" + (1..35).map { chars.random() }.joinToString("")
    }
}