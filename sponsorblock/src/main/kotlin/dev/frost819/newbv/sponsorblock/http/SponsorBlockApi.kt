package dev.frost819.newbv.sponsorblock.http

import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * BilibiliSponsorBlock 片段时间轴接口客户端。
 *
 * 接口与浏览器扩展 hanydd/BilibiliSponsorBlock 相同，服务端为 `https://bsbsb.top`：
 * - 查询：`GET /api/skipSegments?videoID=<BVID>&cid=<CID>&categories=["sponsor"]`
 * - 必带请求头 `origin`（来源标识）与 `x-ext-version`（客户端版本），缺失会被拒绝
 * - 无数据时返回 `404`（部分网关实现会返回 `200 []`），两种情况都按「无片段」处理
 *
 * 参考文档：https://github.com/hanydd/BilibiliSponsorBlock/wiki/API
 *
 * 本类只负责 HTTP 与 JSON 解析，不做缓存与业务过滤，便于单测。
 *
 * @param client 复用的 OkHttpClient（连接池共享，不要在调用处新建）
 * @param baseUrl API 根地址，默认官方服务端
 * @param clientOrigin `origin` 请求头取值，标识调用方来源
 * @param clientVersion `x-ext-version` 请求头取值
 * @param onError 解析或请求失败的观测回调，用于把「静默返回空列表」变成可诊断日志
 */
class SponsorBlockApi(
    private val client: OkHttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val clientOrigin: String = DEFAULT_ORIGIN,
    private val clientVersion: String = DEFAULT_VERSION,
    private val onError: (Throwable) -> Unit = {},
) {
    companion object {
        /** 官方服务端根地址（末尾带斜杠，拼接路径时不要再补）。 */
        const val DEFAULT_BASE_URL = "https://bsbsb.top/api/"

        /** 来源标识：与浏览器扩展的 origin 格式对齐，声明为本项目。 */
        const val DEFAULT_ORIGIN = "dev.frost819.newbv"

        /** 客户端版本，随应用版本演进即可，服务端仅做粗粒度统计。 */
        const val DEFAULT_VERSION = "1.0.0"

        /** 未指定分类时采用的默认查询分类（只取跳过类片段，避免把静音类误当跳过）。 */
        val DEFAULT_CATEGORIES = listOf(SponsorCategory.Sponsor)

        /** 服务端动作类型：跳过。 */
        private const val ACTION_SKIP = "skip"
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    /**
     * 查询指定视频（可按分 P 过滤）的可跳过片段。
     *
     * @param bvid 视频 BV 号（如 `BV1xx411c7mD`）
     * @param cid 分 P 的 CID；为空时返回该视频所有分 P 的片段
     * @param categories 需要查询的分类，会作为 JSON 数组传给服务端
     * @return 片段列表，按开始时间升序；无数据或请求失败返回空列表
     */
    suspend fun getSkipSegments(
        bvid: String,
        cid: Long? = null,
        categories: Collection<SponsorCategory> = DEFAULT_CATEGORIES,
    ): List<SponsorSegment> {
        if (bvid.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { requestSegments(bvid, cid, categories) }
                .getOrElse { error ->
                    // 网络异常、服务端 5xx、JSON 结构变化都视为「本次无片段」，
                    // 由调用方决定是否重试；不能影响播放本身
                    onError(error)
                    if (error is IOException) throw error
                    emptyList()
                }
        }
    }

    private fun requestSegments(
        bvid: String,
        cid: Long?,
        categories: Collection<SponsorCategory>,
    ): List<SponsorSegment> {
        val urlBuilder =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .addPathSegment("skipSegments")
                .addQueryParameter("videoID", bvid)
        if (cid != null && cid > 0L) urlBuilder.addQueryParameter("cid", cid.toString())
        if (categories.isNotEmpty()) urlBuilder.addQueryParameter("categories", categories.toCategoryJson())
        val url = urlBuilder.build()

        val request =
            Request
                .Builder()
                .url(url)
                .header("origin", clientOrigin)
                .header("x-ext-version", clientVersion)
                .header("accept", "application/json")
                .get()
                .build()

        client.newCall(request).execute().use { response ->
            // 404 表示该视频没有任何标注，属于正常结果
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return parseSegments(body, cid)
        }
    }

    /**
     * 解析服务端响应并转换为业务模型。
     *
     * **逐条容错**：整体按 JSON 数组遍历，单条记录字段异常时只丢弃该条并上报
     * [onError]，不影响同一响应里的其它片段。此前一次性反序列化整个数组，
     * 任何一条字段类型不符（例如 `videoDuration` 是小数而模型声明为 `Int`）
     * 都会让整包解析失败，最终表现为「明明有标注、App 却显示没有可跳过片段」。
     *
     * 过滤规则：
     * - 只保留 `actionType == "skip"` 的片段（`mute` / `full` 不在此处理语义内）
     * - 未知分类直接丢弃，避免新增分类污染列表
     * - 请求指定了 cid 时，丢弃明确属于其它分 P 的片段；服务端未带 cid 的片段保留
     * - 起止时间缺失或非法（end <= start）的片段丢弃
     *
     * @param body 响应 JSON 文本
     * @param requestedCid 请求的分 P CID
     */
    internal fun parseSegments(
        body: String,
        requestedCid: Long?,
    ): List<SponsorSegment> {
        val element =
            runCatching { json.parseToJsonElement(body) }
                .getOrElse { error ->
                    onError(error)
                    return emptyList()
                }
        // 响应不是数组（网关错误页、纯文本等）同样要留痕，避免又一次「静默无片段」
        val array =
            element as? JsonArray
                ?: run {
                    onError(IllegalStateException("响应不是 JSON 数组：${body.take(200)}"))
                    return emptyList()
                }

        return array
            .mapNotNull { entry ->
                runCatching { json.decodeFromJsonElement(SkipSegmentResponse.serializer(), entry) }
                    .getOrElse { error ->
                        onError(error)
                        return@mapNotNull null
                    }.toSegment(requestedCid)
            }.sortedBy { it.startMs }
    }

    private fun SkipSegmentResponse.toSegment(requestedCid: Long?): SponsorSegment? {
        if (actionType != ACTION_SKIP) return null
        val category = SponsorCategory.fromApiName(category) ?: return null
        val start = startSeconds ?: return null
        val end = endSeconds ?: return null
        val startMs = (start * 1000).toLong()
        val endMs = (end * 1000).toLong()
        if (startMs < 0L || endMs <= startMs) return null

        val segmentCid = cid?.toLongOrNull()
        // 服务端在未指定分 P 提交时会写入其它 cid 或留空；请求了具体分 P 时只接受归属不明或同分 P 的片段
        if (requestedCid != null && requestedCid > 0L && segmentCid != null && segmentCid != requestedCid) {
            return null
        }
        return SponsorSegment(
            uuid = uuid,
            startMs = startMs,
            endMs = endMs,
            category = category,
            cid = segmentCid,
            votes = votes,
        )
    }

    /** 把分类集合编码为服务端要求的 JSON 数组字符串（如 `["sponsor","intro"]`）。 */
    private fun Collection<SponsorCategory>.toCategoryJson(): String =
        joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.apiName}\"" }
}
