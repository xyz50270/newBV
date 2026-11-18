package dev.frost819.newbv.player

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * [OkHttpUtil.withMediaTimeouts] 的单元测试。
 *
 * 只验证纯 JVM 可构造的部分：超时值本身。完整的 SSL 配置需要 `AndroidCAStore`
 * 与 assets 证书，只能在设备上运行，不在此覆盖。
 */
class OkHttpUtilTest {
    @Test
    fun `withMediaTimeouts lowers read timeout to 8 seconds`() {
        val base = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build()

        val media = OkHttpUtil.withMediaTimeouts(base)

        assertThat(media.readTimeoutMillis).isEqualTo(8_000)
    }

    @Test
    fun `withMediaTimeouts keeps shared connection pool`() {
        val base = OkHttpClient.Builder().build()

        val media = OkHttpUtil.withMediaTimeouts(base)

        // 派生实例必须复用同一连接池，否则每加一个 client 就多一套空闲连接
        assertThat(media.connectionPool).isSameInstanceAs(base.connectionPool)
    }

    @Test
    fun `withMediaTimeouts keeps connect timeout from base`() {
        val base = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).build()

        val media = OkHttpUtil.withMediaTimeouts(base)

        assertThat(media.connectTimeoutMillis).isEqualTo(5_000)
    }
}
