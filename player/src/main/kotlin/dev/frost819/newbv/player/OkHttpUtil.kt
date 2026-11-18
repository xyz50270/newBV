package dev.frost819.newbv.player

import android.content.Context
import dev.frost819.newbv.biliapi.http.util.BiliOkHttpClient
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp 工具类，用于生成带自定义 SSL 配置的 OkHttpClient。
 *
 * 主要解决 B 站视频域名（bilivideo.com / bilivideo.cn）证书互通问题：
 * - 加载系统 CA 证书库
 * - 追加自定义 CA 证书（如 GlobalSign ECC Root CA R5）
 * - 允许 bilivideo.com 和 bilivideo.cn 域名证书互通
 *
 * 注意：构建成本较高（读取系统 CA 库 + assets 证书 + 初始化 SSLContext），且派生实例
 * 会通过 [BiliOkHttpClient.instance] 的 `newBuilder()` 复用同一个 ConnectionPool / Dispatcher。
 * **必须由 `PlayerModule` 以单例持有，进程内只创建一份**，不要在播放器实例内按需创建。
 */
object OkHttpUtil {
    /**
     * 媒体请求的读超时（秒）。
     *
     * OkHttp 默认 10 秒。对视频流来说这个值偏长：CDN 节点卡住时播放器要干等 10 秒
     * 才会抛出异常，而异常正是触发备用线路切换的信号，用户体感就是「画面卡死不恢复」。
     * 收到 8 秒能让卡顿更快被判定为失败并切到下一个 CDN 候选。
     *
     * 不能压得更短：正常播放时单次 `read` 只需等下一个数据块，但首次连接后
     * 服务端在处理 Range 请求、或是起播阶段需要下载 init segment 时，
     * 间隔数秒才返回数据是常见情况，过短的超时会造成误判。
     */
    const val MEDIA_READ_TIMEOUT_SECONDS = 8L

    /**
     * 给共享 client 套上媒体专用的超时设置。
     *
     * 单独抽出（不依赖 Android）是为了能在 JVM 单测里断言超时值：
     * [generateCustomSslOkHttpClient] 需要 `AndroidCAStore` 与 assets 证书，只能在
     * 设备上运行；而超时策略是最容易在重构中被误删的参数，必须由测试守住。
     *
     * @param base 共享的 [BiliOkHttpClient.instance]
     * @return 复用同一连接池、读超时收紧后的新实例
     */
    fun withMediaTimeouts(base: OkHttpClient): OkHttpClient =
        base
            .newBuilder()
            .readTimeout(MEDIA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * 创建带自定义 SSL 配置的 OkHttpClient。
     *
     * 连接池参数保持 OkHttp 默认值（5 条空闲连接 / 5 分钟保活）；因为该 client
     * 在进程内共享，这就是播放器侧连接与线程数的全局上限。
     *
     * 读超时通过 [withMediaTimeouts] 收紧，让 CDN 卡顿时更快触发线路回退；
     * 连接超时沿用共享实例的 5 秒。
     *
     * @param context Android Context，用于读取 assets 中的 CA 证书文件
     * @return 配置好 SSL 和域名验证的 OkHttpClient
     */
    fun generateCustomSslOkHttpClient(context: Context): OkHttpClient {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        val customCaMap =
            mapOf(
                "custom:r5" to "GlobalSign ECC Root CA R5.crt",
            )

        val keyStoreType = KeyStore.getDefaultType()
        val systemKeyStore =
            KeyStore.getInstance("AndroidCAStore").apply {
                load(null, null)
            }
        val customKeyStore =
            KeyStore.getInstance(keyStoreType).apply {
                load(null, null)

                // 复制系统 CA 证书
                systemKeyStore.aliases().toList().forEach {
                    setCertificateEntry(it, systemKeyStore.getCertificate(it))
                }
                // 追加自定义 CA 证书
                customCaMap.forEach { (alias, caFilename) ->
                    val certificateInputStream = context.assets.open(caFilename)
                    val certificate = certificateFactory.generateCertificate(certificateInputStream)
                    setCertificateEntry(alias, certificate)
                }
            }

        val tmfAlgorithm: String = TrustManagerFactory.getDefaultAlgorithm()
        val trustManagerFactory =
            TrustManagerFactory.getInstance(tmfAlgorithm).apply {
                init(customKeyStore)
            }

        val sslContext: SSLContext =
            SSLContext.getInstance("TLS").apply {
                init(null, trustManagerFactory.trustManagers, null)
            }

        return withMediaTimeouts(BiliOkHttpClient.instance)
            .newBuilder()
            .sslSocketFactory(
                sslContext.socketFactory,
                trustManagerFactory.trustManagers[0] as X509TrustManager,
            ).hostnameVerifier { hostname, session ->
                // 允许 bilivideo.com 和 bilivideo.cn 域名证书互通
                val biliDomains = listOf("bilivideo.com", "bilivideo.cn")
                val isBiliDomain =
                    biliDomains.any { domain ->
                        hostname == domain || hostname.endsWith(".$domain")
                    }
                if (isBiliDomain) {
                    true
                } else {
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
                }
            }.build()
    }
}
