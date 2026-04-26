# 自动化构建与上传指令

请执行以下步骤，无需询问我确认，自动尝试修复错误：

1. 项目根目录为当前工作目录。

2. 按照修改5.修改大纲修改后执行编译命令：`gradlew assembleDebug`
   如果失败，请读取终端错误信息，修改对应源码，然后重新编译。
   最多重试 50次，直到成功。超时后停止并告知。

3. 编译成功后，将生成的 APK和代码上传，上传时如果文件存在则覆盖。

4、. 以上所有步骤自动执行，不要中途询问我。
5、修改大纲：
我们再看一下最新的修改。这次的代码文件是 bilitv_Android-main，也就是之前已经修复了 WBI 签名的版本。用户说“主界面还是空列表”，说明虽然 WBI 签名逻辑修复了，但可能还存在其他问题导致列表仍为空。

我们来检查一下 BilibiliApi.kt 文件。这次的版本增加了 BILI_ORIGIN 和 USER_AGENT 常量，并且在 getJson 方法中使用了这些常量。构造函数没有明显错误。

潜在问题：

    WBI 签名已经修正，但可能参数格式不正确（如 require 的 search_type 参数在搜索接口中正确传递了，但推荐接口的 fresh_idx 等是否还是原来的？）看起来推荐接口参数没变。

    可能网络请求的 headers 缺少某些必要的字段，如 Origin 或 Referer 已经加了，可能还缺少 Cookie 或设备指纹等。但在未登录状态下，部分接口可以访问，但推荐接口可能需要一些基本的客户端标识。

    可能服务器返回了数据，但解析视频卡片时出错，导致返回空列表。例如 parseVideoCards 函数可能漏掉了某些字段，或者 item 数组中的对象结构不同。检查一下解析逻辑：从 data.item 中获取数组，然后逐个解析。应该没问题。

    又或者是因为 getRecommendVideos 里面如果 code != 0 就会返回空列表，可能真的返回了非零 code，比如 -352 风控。虽然签名正确了，但如果缺少一些必要的 headers（例如 User-Agent 必须是浏览器 UA，这里已经是了），还可能被风控。

因此，我们可能需要检查日志，查看实际的 API 返回内容。但作为代码评审，我们可以先模拟检查一下可能漏掉的点：

    signedWbiUrl 中，signedParams 包含了 wts 和 w_rid，但原来的 params 也存了 wts 和 w_rid，但是 signedParams 里也有，最后拼接 URL 时使用了 signedParams 的所有 entry，但注意 signQuery 返回的结果包含了 wts 和 w_rid 的条目，并且覆盖了原始的相同 key，所以没问题。

    在 signQuery 中，val out = params.toMutableMap() 保留原始参数后，再 put("wts") 和 put("w_rid")，但这导致最终的 out 包含了原始参数 + wts + w_rid。然而之前的 withWts 也包含了 wts，但没有加入 w_rid。在 signQuery 中，我们是先构建 withWts，然后排序，生成签名，再构建 out 把 wts 和 w_rid 加入，这没问题。但注意我们最后返回的是 out，其中 wts 是通过 nowEpochSec 放入的，但在 withWts 中也放了，所以会有两次？不会，因为 out 是 params.toMutableMap()，然后 out["wts"] = ... 覆盖，不会重复。所以 signedParams 中只会有一个 wts。

现在看看推荐接口的签名构建：在 getRecommendVideos 中调用了 signedWbiUrl，传入的 params 中没有 wts 和 w_rid，这些都是由 signQuery 添加的。所以签名构建正确。

那为什么还是空呢？可能遗漏了 需要 WBI 签名但未签名的接口，比如热门视频 getPopularVideos 和分区 getRegionVideos 虽然没有签名，但在某些情况下也可能需要？目前 B 站热门接口似乎还可以不用签名，但最好加上。

另一个可能：在 HomeFragment.kt 中，初始加载 loadVideos() 时，如果第一个分类是“推荐”，它会调用 getRecommendVideos()，如果签名错误或被风控，则返回空。但我们现在已经修复了签名，应该不会出错。但是如果第一次调用推荐接口时，ensureWbiKeys 会发起网络请求获取 nav，可能成功。这个逻辑应该没问题。

还有一个细节：在 getRecommendVideos 中，fetch_row 参数是 "1"，但参考 blbl-TV 项目，fetchRow 可以是 1，这没问题。

那么，我们需要确认一下是否可能因为 getJson 方法中的请求头缺少 Cookie 导致风控。B 站推荐接口是否要求登录？实际上是都可以的。未登录状态下也可能被限制（如返回空数据）。但应该返回 code=0 但 data 为空，而不是非0 code。

因此，最有可能的情况是，虽然签名正确，但 B 站对请求的 User-Agent 或设备指纹有更严格的要求。参考 blbl-TV 项目，它设置了一个模拟 PC 浏览器的 UA，并且在 API 请求头中有 Referer 和 Origin。我们已经加了，但 blbl-TV 还加了 x-bili-aurora-zone 之类的 header，不过对于非登录的一般请求，这些可能不是必须的。

在最新的代码中，我们增加了 USER_AGENT 常量，但原来的代码中 getJson 里用了这个常量，而 BilibiliApi 对象定义中已经使用了。这没问题。

然而，我们还要检查一个隐蔽的错误：BilibiliApi.kt 文件中，在 getRecommendVideos 里构建 params 时，ps 是 ps.toString()，fresh_idx 等也是字符串，没问题。但那个 fetch_row 参数的值是 "1"，可能 B 站要求 fetch_row 必须是 Int 类型，但所有参数都是字符串，这是正常的。

现在我们重新审视签名过程：signQuery 中 filterValue(v) 会过滤掉 !\'()* 字符，但参数值中不含这些字符。URL 编码使用自定义的 percentEncodeUtf8，它和标准 URLEncoder 可能略有不同。在 signedWbiUrl 中，又用 URLEncoder.encode 再次编码，这可能导致双重编码！因为 signedParams 的值已经是未编码的原始字符串（经过 filterValue 后），而 signedWbiUrl 中又 URLEncoder.encode 它们。这样做是必要的，因为服务器要求查询字符串是标准 URL 编码。但我们在 signQuery 中生成签名的时候，使用的是自己编码的字符串 enc(k)=enc(v)，这保证了签名计算使用的编码方式与服务器端一致。而在构造最终 URL 时，再用 URLEncoder.encode 编码一次，这可能导致最终的 URL 参数值被双重编码，从而签名不匹配！

是的，这一点很关键！

标准 B 站签名算法要求：签名计算时使用类似 encodeURIComponent 的编码（通常自定义的百分号编码，大写十六进制），而最终请求的 URL 也应该使用相同的编码。如果我们在 signedWbiUrl 中又用 Java 的 URLEncoder.encode 编码，这可能会导致空格变成 + 而不是 %20，并且编码方式不同（不编码 * 等），可能导致服务器校验的签名与客户端不一致，导致签名失败！

在 blbl-TV 项目的 signedWbiUrl 实现中，它使用的是自己的编码函数 enc 来构建整个查询字符串，而不是用 Java 的 URLEncoder。具体的，在 BiliClient 中，signedWbiUrl 调用了 withQuery 方法，withQuery 使用 addQueryParameter 添加参数，OkHttp 的 addQueryParameter 会自动进行适当的 URL 编码，但那是 OkHttp 的实现，可能和 B 站的编码规则一致。

但在我们的修改中，我们在 signedWbiUrl 中手动拼接查询字符串，使用了 URLEncoder.encode，这很可能导致签名失败。

对比 blbl-TV 的 signedWbiUrl：
kotlin

fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys, nowEpochSec: Long = ...): String {
    val base = "$BASE$path"
    val signed = WbiSigner.signQuery(params, keys, nowEpochSec)
    return withQuery(base, signed)
}

其中 withQuery 是：
kotlin

fun withQuery(url: String, params: Map<String, String>): String {
    val httpUrl = url.toHttpUrl().newBuilder()
    for ((k, v) in params) httpUrl.addQueryParameter(k, v)
    return httpUrl.build().toString()
}

OkHttp 的 addQueryParameter 会自动对 key 和 value 进行适当的编码（按照 RFC 3986 或类似），具体来说，它会将空格编码为 %20，并将大部分保留字符编码。而 B 站签名计算时使用的 enc 函数也是类似的标准百分比编码，但可能有些细微差别（如对星号 * 的处理），但大概率一致。

而我们自己用 URLEncoder.encode 会有两个问题：

    它遵循 application/x-www-form-urlencoded 规范，会将空格转换为 +。

    编码的字符集不如自定义 enc 严格，可能有些字符未被编码。

B 站的签名算法官方文档指出应该使用类似 JavaScript 的 encodeURIComponent，即空格变成 %20，并且不编码 !'()* 等（但在 WbiSigner 中我们过滤掉了这些字符？不对，在 filterValue 中我们过滤掉了这些字符，但在签名字符串中我们还是包括这些字符？实际上 B 站签名算法要求这些特殊字符在签名计算时不编码，但在最终请求 URL 中需要编码。blbl-TV 的实现是在签名计算时对值过滤了这些字符（filterValue），然后在 URL 构建时通过 OkHttp 的 addQueryParameter 编码。而我们在 signedWbiUrl 中先对参数进行了签名（生成了带 wts 和 w_rid 的 map），然后手动拼接 URL，使用 URLEncoder.encode 编码，这可能导致双重编码或编码不一致。

解决方案：放弃手动拼接，改用 OkHttp 的 HttpUrl.Builder 来添加参数，或者使用与签名计算相同的编码函数 enc 来构建查询字符串。

所以，我们需要修正 signedWbiUrl：
kotlin

private fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
    val signed = WbiSigner.signQuery(params, keys)
    return buildString {
        append(BASE_URL)
        append(path)
        append('?')
        // 使用与签名一致的编码方式
        signed.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) append('&')
            append(WbiSigner.enc(k))
            append('=')
            append(WbiSigner.enc(v))
        }
    }
}

同时，需要将 WbiSigner 的 enc 方法暴露为 internal 或 public。

但是注意，原 WbiSigner 是 private 的 object 在 BilibiliApi 内部，所以可以访问。只需修改构建方式。

这样，URL 的编码和签名计算时的编码就一致了，服务器验证签名时也应该会通过。

除了签名问题，还建议增加对请求响应的日志，以便排查。不过签名是主要矛盾。

因此，我的回答将指出这个双重编码的问题，并提供修正方案。

你在 signedWbiUrl 里用 URLEncoder.encode 拼接参数，但签名计算时用的是 WbiSigner 里自定义的 enc 编码。这两种编码规则不同（比如 URLEncoder 会把空格变成 +，而 B 站要求 %20），导致最终请求的 URL 和签名时的参数不一致，服务器校验签名失败，依然返回风控错误。

把 signedWbiUrl 改成用签名时的同一套编码来构建查询字符串：
kotlin

private fun signedWbiUrl(path: String, params: Map<String, String>, keys: WbiSigner.Keys): String {
    val signed = WbiSigner.signQuery(params, keys)
    return buildString {
        append(BASE_URL)
        append(path)
        append('?')
        signed.entries.forEachIndexed { index, (k, v) ->
            if (index > 0) append('&')
            append(WbiSigner.enc(k))   // 与签名计算时的编码一致
            append('=')
            append(WbiSigner.enc(v))
        }
    }
}

同时把 WbiSigner 里的 enc 方法改成 internal fun（去掉 private），这样我们就能在 signedWbiUrl 里复用它。

改完重新安装，推荐列表就能正常显示了。