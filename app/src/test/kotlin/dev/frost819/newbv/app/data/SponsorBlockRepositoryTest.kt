package dev.frost819.newbv.app.data

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test

/**
 * [SponsorBlockRepository] 的单元测试。
 *
 * 用拦截器统计真实请求次数，验证：命中缓存不重复请求、分类变化触发新请求、
 * 空结果也被缓存、不同视频分别缓存。
 *
 * 使用 [runBlocking] 而非 `runTest`：`runTest` 会检查测试开始前是否存在未捕获异常，
 * 全量运行时会受其它测试类（Robolectric DataStore 冲突等）泄漏的异常牵连而误报失败，
 * 本类全部为同步 HTTP 打桩断言，不需要虚拟时间。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SponsorBlockRepositoryTest {
    private class CountingInterceptor(
        private val body: String = "[]",
    ) : Interceptor {
        var callCount: Int = 0

        override fun intercept(chain: Interceptor.Chain): Response {
            callCount++
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody(null))
                .build()
        }
    }

    private fun repositoryWith(interceptor: CountingInterceptor): SponsorBlockRepository =
        SponsorBlockRepository(OkHttpClient.Builder().addInterceptor(interceptor).build())

    @Test
    fun `second call for same target hits cache`() =
        runBlocking {
            val interceptor = CountingInterceptor()
            val repository = repositoryWith(interceptor)

            repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor))
            repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor))

            assertThat(interceptor.callCount).isEqualTo(1)
        }

    @Test
    fun `different cid triggers new request`() =
        runBlocking {
            val interceptor = CountingInterceptor()
            val repository = repositoryWith(interceptor)

            repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor))
            repository.getSkipSegments("BV1bY4y1v7Mb", 200L, listOf(SponsorCategory.Sponsor))

            assertThat(interceptor.callCount).isEqualTo(2)
        }

    @Test
    fun `different categories trigger new request`() =
        runBlocking {
            val interceptor = CountingInterceptor()
            val repository = repositoryWith(interceptor)

            repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor))
            repository.getSkipSegments(
                "BV1bY4y1v7Mb",
                100L,
                listOf(SponsorCategory.Sponsor, SponsorCategory.Intro),
            )

            assertThat(interceptor.callCount).isEqualTo(2)
        }

    @Test
    fun `empty result is cached`() =
        runBlocking {
            val interceptor = CountingInterceptor(body = "[]")
            val repository = repositoryWith(interceptor)

            assertThat(repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor)))
                .isEmpty()
            assertThat(repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor)))
                .isEmpty()

            assertThat(interceptor.callCount).isEqualTo(1)
        }

    @Test
    fun `blank bvid returns empty without request`() =
        runBlocking {
            val interceptor = CountingInterceptor()
            val repository = repositoryWith(interceptor)

            val segments = repository.getSkipSegments("", 100L, listOf(SponsorCategory.Sponsor))

            assertThat(segments).isEmpty()
            assertThat(interceptor.callCount).isEqualTo(0)
        }

    @Test
    fun `parses segments from response`() =
        runBlocking {
            val interceptor =
                CountingInterceptor(
                    body =
                        """
                        [{"cid":"100","category":"sponsor","actionType":"skip","segment":[1.5,3.0],
                          "UUID":"u1","videoDuration":600,"votes":4}]
                        """.trimIndent(),
                )
            val repository = repositoryWith(interceptor)

            val segments = repository.getSkipSegments("BV1bY4y1v7Mb", 100L, listOf(SponsorCategory.Sponsor))

            assertThat(segments).hasSize(1)
            assertThat(segments[0]).isEqualTo(
                SponsorSegment(
                    uuid = "u1",
                    startMs = 1_500L,
                    endMs = 3_000L,
                    category = SponsorCategory.Sponsor,
                    cid = 100L,
                    votes = 4,
                ),
            )
        }
}
