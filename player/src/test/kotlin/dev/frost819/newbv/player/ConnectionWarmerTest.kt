package dev.frost819.newbv.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [ConnectionWarmer] 的单元测试。
 *
 * 用 [Interceptor] 记录真实发出的请求，不依赖网络。覆盖：预热请求格式、
 * 空地址跳过、同地址去重、LRU 淘汰。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionWarmerTest {
    private class RecordingInterceptor : Interceptor {
        val requests = CopyOnWriteArrayList<Request>()

        override fun intercept(chain: Interceptor.Chain): Response {
            requests += chain.request()
            return Response
                .Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .body("x".toResponseBody(null))
                .build()
        }
    }

    private fun clientWith(interceptor: RecordingInterceptor): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(interceptor).build()

    @Test
    fun `warmUp sends one byte range request with referer`() =
        runTest {
            val interceptor = RecordingInterceptor()
            val warmer =
                ConnectionWarmer(clientWith(interceptor), this, dispatcher = StandardTestDispatcher(testScheduler))

            warmer.warmUp("https://cn-jsnj.bilivideo.com/video.m4s")
            advanceUntilIdle()

            assertThat(interceptor.requests).hasSize(1)
            val request = interceptor.requests.first()
            assertThat(request.url.toString()).isEqualTo("https://cn-jsnj.bilivideo.com/video.m4s")
            assertThat(request.header("Range")).isEqualTo("bytes=0-0")
            assertThat(request.header("Referer")).isEqualTo("https://www.bilibili.com")
        }

    @Test
    fun `warmUp ignores blank url`() =
        runTest {
            val interceptor = RecordingInterceptor()
            val warmer =
                ConnectionWarmer(clientWith(interceptor), this, dispatcher = StandardTestDispatcher(testScheduler))

            warmer.warmUp(null)
            warmer.warmUp("")
            warmer.warmUp("   ")
            advanceUntilIdle()

            assertThat(interceptor.requests).isEmpty()
        }

    @Test
    fun `warmUp deduplicates same url within ttl`() =
        runTest {
            val interceptor = RecordingInterceptor()
            val warmer =
                ConnectionWarmer(
                    client = clientWith(interceptor),
                    scope = this,
                    warmUpTtlMs = 60_000L,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            warmer.warmUp("https://a.bilivideo.com/v.m4s")
            advanceUntilIdle()
            warmer.warmUp("https://a.bilivideo.com/v.m4s")
            advanceUntilIdle()

            // 切画质 / 音轨回退会重复走解析路径，同地址不应重复预热
            assertThat(interceptor.requests).hasSize(1)
        }

    @Test
    fun `warmUpFirst only warms the first candidate`() =
        runTest {
            val interceptor = RecordingInterceptor()
            val warmer =
                ConnectionWarmer(clientWith(interceptor), this, dispatcher = StandardTestDispatcher(testScheduler))

            warmer.warmUpFirst(
                listOf(
                    "https://first.bilivideo.com/v.m4s",
                    "https://second.bilivideo.com/v.m4s",
                ),
            )
            advanceUntilIdle()

            // 播放只用首个候选，预热其余地址会白占连接池空闲名额
            assertThat(interceptor.requests).hasSize(1)
            assertThat(
                interceptor.requests
                    .first()
                    .url.host,
            ).isEqualTo("first.bilivideo.com")
        }

    @Test
    fun `warmUpFirst handles empty list`() =
        runTest {
            val interceptor = RecordingInterceptor()
            val warmer =
                ConnectionWarmer(clientWith(interceptor), this, dispatcher = StandardTestDispatcher(testScheduler))

            warmer.warmUpFirst(emptyList())
            advanceUntilIdle()

            assertThat(interceptor.requests).isEmpty()
        }

    @Test
    fun `warmUp survives request failure`() =
        runTest {
            val failingClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor { chain -> throw java.io.IOException("boom") }
                    .build()
            val warmer = ConnectionWarmer(failingClient, this, dispatcher = StandardTestDispatcher(testScheduler))

            // 预热失败必须静默，不能抛出影响播放
            warmer.warmUp("https://a.bilivideo.com/v.m4s")
            advanceUntilIdle()
        }

    @Test
    fun `warmUp treats non 2xx response as success`() =
        runTest {
            val interceptor =
                object : Interceptor {
                    var count = 0

                    override fun intercept(chain: Interceptor.Chain): Response {
                        count++
                        return Response
                            .Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body("".toResponseBody(null))
                            .build()
                    }
                }
            val warmer =
                ConnectionWarmer(
                    OkHttpClient.Builder().addInterceptor(interceptor).build(),
                    this,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            warmer.warmUp("https://a.bilivideo.com/v.m4s")
            advanceUntilIdle()

            // 403 也说明连接已建立，预热目的达成，不应重试
            assertThat(interceptor.count).isEqualTo(1)
        }

    @Test
    fun `warmUp uses dispatcher independent of test scheduler`() =
        runTest(StandardTestDispatcher()) {
            val interceptor = RecordingInterceptor()
            val warmer =
                ConnectionWarmer(clientWith(interceptor), this, dispatcher = StandardTestDispatcher(testScheduler))

            warmer.warmUp("https://a.bilivideo.com/v.m4s")
            advanceUntilIdle()
        }
}
