package dev.frost819.newbv.sponsorblock.http

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

/**
 * [SponsorBlockApi] 的单元测试。
 *
 * 用自定义 [Interceptor] 拦截请求，断言 URL/请求头并返回构造的响应，
 * 不依赖真实网络。
 */
class SponsorBlockApiTest {
    private class CapturingInterceptor(
        private val body: String,
        private val code: Int = 200,
    ) : Interceptor {
        var capturedRequest: Request? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            capturedRequest = chain.request()
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("OK")
                .body(body.toResponseBody(null))
                .build()
        }
    }

    private fun apiWith(
        body: String,
        code: Int = 200,
    ): Pair<SponsorBlockApi, CapturingInterceptor> {
        val interceptor = CapturingInterceptor(body, code)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        return SponsorBlockApi(client) to interceptor
    }

    @Test
    fun `getSkipSegments sends videoID cid and categories`() =
        runTest {
            val (api, interceptor) = apiWith("[]")

            api.getSkipSegments(bvid = "BV1bY4y1v7Mb", cid = 564881041L)

            val url = requireNotNull(interceptor.capturedRequest).url
            assertThat(url.encodedPath).isEqualTo("/api/skipSegments")
            assertThat(url.queryParameter("videoID")).isEqualTo("BV1bY4y1v7Mb")
            assertThat(url.queryParameter("cid")).isEqualTo("564881041")
            assertThat(url.queryParameter("categories")).isEqualTo("[\"sponsor\"]")
        }

    @Test
    fun `getSkipSegments sends origin and version headers`() =
        runTest {
            val (api, interceptor) = apiWith("[]")

            api.getSkipSegments(bvid = "BV1bY4y1v7Mb")

            val request = requireNotNull(interceptor.capturedRequest)
            assertThat(request.header("origin")).isEqualTo(SponsorBlockApi.DEFAULT_ORIGIN)
            assertThat(request.header("x-ext-version")).isEqualTo(SponsorBlockApi.DEFAULT_VERSION)
        }

    @Test
    fun `getSkipSegments omits cid when null`() =
        runTest {
            val (api, interceptor) = apiWith("[]")

            api.getSkipSegments(bvid = "BV1bY4y1v7Mb", cid = null)

            assertThat(requireNotNull(interceptor.capturedRequest).url.queryParameter("cid")).isNull()
        }

    @Test
    fun `getSkipSegments parses segment list sorted by start time`() =
        runTest {
            val (api, _) =
                apiWith(
                    """
                    [
                      {"cid":"564881041","category":"sponsor","actionType":"skip","segment":[61.609,75.363],
                       "UUID":"b","videoDuration":679,"locked":0,"votes":24},
                      {"cid":"564881041","category":"sponsor","actionType":"skip","segment":[18.913,32.876],
                       "UUID":"a","videoDuration":679,"locked":0,"votes":26}
                    ]
                    """.trimIndent(),
                )

            val segments = api.getSkipSegments(bvid = "BV1bY4y1v7Mb", cid = 564881041L)

            assertThat(segments).hasSize(2)
            assertThat(segments[0].uuid).isEqualTo("a")
            assertThat(segments[0].startMs).isEqualTo(18_913L)
            assertThat(segments[0].endMs).isEqualTo(32_876L)
            assertThat(segments[0].category).isEqualTo(SponsorCategory.Sponsor)
            assertThat(segments[1].uuid).isEqualTo("b")
        }

    @Test
    fun `getSkipSegments returns empty list on 404`() =
        runTest {
            val (api, _) = apiWith(body = "not found", code = 404)

            val segments = api.getSkipSegments(bvid = "BV1xx411c7mD")

            assertThat(segments).isEmpty()
        }

    @Test
    fun `getSkipSegments returns empty list on malformed json`() =
        runTest {
            val (api, _) = apiWith(body = "<html>error</html>")

            val segments = api.getSkipSegments(bvid = "BV1bY4y1v7Mb")

            assertThat(segments).isEmpty()
        }

    @Test
    fun `getSkipSegments returns empty list for blank bvid`() =
        runTest {
            val (api, interceptor) = apiWith("[]")

            val segments = api.getSkipSegments(bvid = "  ")

            assertThat(segments).isEmpty()
            assertThat(interceptor.capturedRequest).isNull()
        }

    @Test
    fun `parseSegments drops mute action type`() {
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [{"cid":"1","category":"sponsor","actionType":"mute","segment":[1,2],"UUID":"m"}]
                    """.trimIndent(),
                requestedCid = 1L,
            )

        assertThat(segments).isEmpty()
    }

    @Test
    fun `parseSegments drops unknown category`() {
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [{"cid":"1","category":"brandNew","actionType":"skip","segment":[1,2],"UUID":"u"}]
                    """.trimIndent(),
                requestedCid = 1L,
            )

        assertThat(segments).isEmpty()
    }

    @Test
    fun `parseSegments drops segments belonging to other cid`() {
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [{"cid":"999","category":"sponsor","actionType":"skip","segment":[1,2],"UUID":"other"}]
                    """.trimIndent(),
                requestedCid = 1L,
            )

        assertThat(segments).isEmpty()
    }

    @Test
    fun `parseSegments keeps segments without cid when cid requested`() {
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [{"category":"sponsor","actionType":"skip","segment":[1,2],"UUID":"global"}]
                    """.trimIndent(),
                requestedCid = 1L,
            )

        assertThat(segments).hasSize(1)
        assertThat(segments[0].cid).isNull()
    }

    @Test
    fun `parseSegments drops invalid time range`() {
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [
                      {"category":"sponsor","actionType":"skip","segment":[10,10],"UUID":"zero"},
                      {"category":"sponsor","actionType":"skip","segment":[20,10],"UUID":"reverse"},
                      {"category":"sponsor","actionType":"skip","segment":[],"UUID":"empty"}
                    ]
                    """.trimIndent(),
                requestedCid = null,
            )

        assertThat(segments).isEmpty()
    }

    @Test
    fun `getSkipSegments encodes multiple categories`() =
        runTest {
            val (api, interceptor) = apiWith("[]")

            api.getSkipSegments(
                bvid = "BV1bY4y1v7Mb",
                categories = listOf(SponsorCategory.Sponsor, SponsorCategory.Intro),
            )

            assertThat(requireNotNull(interceptor.capturedRequest).url.queryParameter("categories"))
                .isEqualTo("[\"sponsor\",\"intro\"]")
        }

    @Test
    fun `parseSegments accepts real response with fractional videoDuration`() {
        // 回归：服务端 videoDuration 是小数（1013.525），模型曾声明为 Int，
        // 导致反序列化抛 JsonDecodingException、整包被吞成空列表，
        // 表现为「有标注却显示没有可跳过片段」。
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [{"cid":"43197532586","category":"sponsor","actionType":"skip","segment":[286.8,409.92],
                      "UUID":"b703302c1e8ff9184bc538cffca0071278572bd53301ac64e80000919f2626047",
                      "videoDuration":1013.525,"locked":0,"votes":0,"description":""}]
                    """.trimIndent(),
                requestedCid = 43_197_532_586L,
            )

        assertThat(segments).hasSize(1)
        assertThat(segments[0].startMs).isEqualTo(286_800L)
        assertThat(segments[0].endMs).isEqualTo(409_920L)
        assertThat(segments[0].category).isEqualTo(SponsorCategory.Sponsor)
        assertThat(segments[0].cid).isEqualTo(43_197_532_586L)
    }

    @Test
    fun `parseSegments keeps good entries when one entry has wrong field type`() {
        // 逐条容错：单条字段类型异常只丢弃该条，不能连带清空同响应里的合法片段
        val (api, _) = apiWith("[]")

        val segments =
            api.parseSegments(
                body =
                    """
                    [
                      {"cid":"1","category":"sponsor","actionType":"skip","segment":[1,2],"UUID":"ok"},
                      {"cid":"1","category":"sponsor","actionType":"skip","segment":"broken","UUID":"bad"},
                      {"cid":"1","category":"intro","actionType":"skip","segment":[5,6],"UUID":"ok2"}
                    ]
                    """.trimIndent(),
                requestedCid = 1L,
            )

        assertThat(segments.map { it.uuid }).containsExactly("ok", "ok2").inOrder()
    }

    @Test
    fun `parseSegments reports parse failure through onError`() {
        val errors = mutableListOf<Throwable>()
        val client = OkHttpClient.Builder().addInterceptor(CapturingInterceptor("[]")).build()
        val api = SponsorBlockApi(client, onError = { errors += it })

        val segments = api.parseSegments(body = "<html>not json</html>", requestedCid = 1L)

        assertThat(segments).isEmpty()
        assertThat(errors).hasSize(1)
    }

    @Test
    fun `parseSegments reports per-entry failure without failing the batch`() {
        val errors = mutableListOf<Throwable>()
        val client = OkHttpClient.Builder().addInterceptor(CapturingInterceptor("[]")).build()
        val api = SponsorBlockApi(client, onError = { errors += it })

        val segments =
            api.parseSegments(
                body =
                    """
                    [
                      {"category":"sponsor","actionType":"skip","segment":[1,2],"UUID":"ok"},
                      {"category":"sponsor","actionType":"skip","segment":"broken","UUID":"bad"}
                    ]
                    """.trimIndent(),
                requestedCid = null,
            )

        assertThat(segments.map { it.uuid }).containsExactly("ok")
        assertThat(errors).hasSize(1)
    }
}
