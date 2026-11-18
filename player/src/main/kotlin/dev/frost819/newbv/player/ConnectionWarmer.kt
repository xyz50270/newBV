package dev.frost819.newbv.player

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 播放地址连接预热器。
 *
 * 起播链路是「取播放地址 → 选 CDN → ExoPlayer prepare」。`prepare` 之前播放器不会
 * 与 CDN 建立任何连接，因此 TCP 握手 + TLS 协商全部计入起播耗时（跨省 CDN 通常
 * 30~80ms，TLS 1.3 再加 1-RTT）。
 *
 * 本类在拿到候选地址的那一刻就用极小的 Range 请求把连接建好并留在共享
 * [okhttp3.ConnectionPool] 里，等 ExoPlayer 真正 prepare 时直接复用已建立的连接，
 * 省掉握手时间。预热是**尽力而为**的：任何失败都静默忽略，绝不影响播放。
 *
 * 设计要点：
 * - 只预热**首个候选**地址，不做多地址并发。播放最终只用第一个地址，预热其余地址
 *   会白白占用连接池里的空闲名额（池上限 10 条，媒体与接口共用）。
 * - 请求 `Range: bytes=0-0`（1 字节），避免把整个分片拉下来。
 * - 用完立即 `close()` 响应体，把连接归还连接池而不是泄漏。
 * - 同一地址在 [warmUpTtlMs] 内只发一次请求：切画质、音轨回退、重播都会重复走
 *   解析路径，重复预热没有意义。
 *
 * @param client 播放器媒体请求复用的共享 OkHttpClient（必须是同一个连接池）
 * @param scope 预热协程的作用域；应由 ViewModel 传入 `viewModelScope`
 * @param warmUpTtlMs 同一地址的预热有效期，默认 5 分钟
 * @param dispatcher 执行预热请求的调度器；默认 [Dispatchers.IO]，单测可注入测试调度器
 */
class ConnectionWarmer(
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
    private val warmUpTtlMs: Long = DEFAULT_WARM_UP_TTL_MS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** 已预热地址 → 预热时刻，LRU 上限 [WARMED_CAPACITY]。 */
    private val warmed =
        object : LinkedHashMap<String, Long>(WARMED_CAPACITY, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>): Boolean =
                size > WARMED_CAPACITY
        }

    /**
     * 异步预热单个播放地址。
     *
     * 立即返回，不阻塞调用方；预热失败不影响任何业务状态。
     *
     * @param url 播放地址（视频或音频轨道的 base URL）
     */
    fun warmUp(url: String?) {
        if (url.isNullOrBlank()) return
        if (!shouldWarmUp(url)) return

        scope.launch(dispatcher) {
            runCatching { sendWarmUpRequest(url) }
        }
    }

    /**
     * 异步预热一组候选地址中的第一个。
     *
     * 播放只使用首个候选，预热其余地址会占用连接池空闲名额而无收益。
     *
     * @param urls 按优先级排列的候选地址
     */
    fun warmUpFirst(urls: List<String>) {
        warmUp(urls.firstOrNull())
    }

    /**
     * 记录预热并判断是否应真正发起请求。
     *
     * 同一地址在 [warmUpTtlMs] 内返回 false，避免重复预热。
     */
    private fun shouldWarmUp(url: String): Boolean {
        val now = System.currentTimeMillis()
        return synchronized(warmed) {
            val last = warmed[url]
            if (last != null && now - last < warmUpTtlMs) {
                false
            } else {
                warmed[url] = now
                true
            }
        }
    }

    /**
     * 发一个只要 1 字节的请求，把 TCP/TLS 连接建立并留在连接池。
     *
     * 不检查响应码：403/404 同样意味着连接已建立，预热目的已达成。
     */
    private fun sendWarmUpRequest(url: String) {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()
        client.newCall(request).execute().use { response ->
            // 读 1 字节确保连接被真正用满（部分 CDN 在首字节前才完成完整握手）
            runCatching { response.body?.source()?.readByte() }
        }
    }

    companion object {
        /** 已预热地址的缓存条目上限。 */
        private const val WARMED_CAPACITY = 32

        /** 同一地址预热结果的默认有效期：5 分钟，覆盖一次播放会话内的画质切换。 */
        const val DEFAULT_WARM_UP_TTL_MS = 5 * 60 * 1000L

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        private const val REFERER = "https://www.bilibili.com"
    }
}
