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
}
