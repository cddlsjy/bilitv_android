package com.bili.tv.bili_tv_app.data.net

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

object WbiSigner {
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52,
    )

    // 需要过滤的字符
    private val forbiddenChars = Regex("[!'()*]")

    data class Keys(
        val imgKey: String,
        val subKey: String,
        val fetchedAtEpochSec: Long,
    )

    /**
     * 签名查询参数 - 参考 BiliTVNative 的实现
     */
    fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        val mixinKey = genMixinKey(keys.imgKey, keys.subKey)

        // 添加 wts 参数
        val signed = params.toMutableMap()
        signed["wts"] = nowEpochSec.toString()

        // 过滤特殊字符并排序
        val filtered = signed.mapValues { (_, value) ->
            value.replace(forbiddenChars, "")
        }.toSortedMap()

        // 构建查询字符串：key 不编码，value 使用 URLEncoder 编码
        val query = filtered.entries.joinToString("&") { (key, value) ->
            "$key=${encodeComponent(value)}"
        }

        // 计算 w_rid
        val wRid = md5Hex(query + mixinKey)
        filtered["w_rid"] = wRid
        return filtered
    }

    private fun genMixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        return mixinKeyEncTab.take(32)
            .mapNotNull { index -> raw.getOrNull(index) }
            .joinToString(separator = "")
    }

    /**
     * URL 编码 - 与 BiliTVNative 一致
     */
    private fun encodeComponent(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("%7E", "~")
            .replace("%7e", "~")
    }

    /**
     * 用于构建普通 URL 的编码（非签名用途）
     */
    internal fun enc(s: String): String = encodeComponent(s)

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(String.format(Locale.US, "%02x", b))
        }
        return sb.toString()
    }
}
