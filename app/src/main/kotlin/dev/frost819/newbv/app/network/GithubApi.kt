package dev.frost819.newbv.app.network

import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.network.entity.GithubRelease
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * GitHub Releases API 封装。
 *
 * 用于检查更新和下载 APK。仓库坐标来自 [BuildConfig]，由
 * `AppConfiguration.releaseRepoOwner/releaseRepoName` 注入，不在代码里硬编码，
 * 迁移仓库时只改构建配置一处。关于页展示的仓库地址同样读取这两个值。
 * 下载通过 `ghfast.top` 代理加速国内访问。
 */
object GithubApi {
    /** 发布仓库 owner。 */
    const val OWNER: String = BuildConfig.RELEASE_REPO_OWNER

    /** 发布仓库名。 */
    const val REPO: String = BuildConfig.RELEASE_REPO_NAME

    /** 仓库主页地址，供关于页展示。 */
    val REPO_HTML_URL: String get() = "https://github.com/$OWNER/$REPO"

    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private val client: HttpClient =
        HttpClient(OkHttp) {
            BrowserUserAgent()
            install(ContentNegotiation) {
                json(json)
            }
            install(ContentEncoding) {
                deflate(1.0f)
                gzip(0.9f)
            }
            defaultRequest {
                url {
                    protocol = URLProtocol.HTTPS
                    host = "api.github.com"
                }
            }
        }

    /**
     * 获取最新 Release（非预发布）。
     *
     * @throws NoReleasePublishedException 仓库存在但尚未发布任何 Release
     *   （GitHub 对「无 Release」与「仓库不存在」都返回 404，需按响应体区分）
     * @throws IllegalStateException 其它 API 错误
     */
    suspend fun getLatestBuild(): GithubRelease {
        val response = client.get("repos/$OWNER/$REPO/releases/latest").bodyAsText()
        if (isNotFound(response)) throw NoReleasePublishedException()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /** 获取所有 Releases。 */
    suspend fun getReleases(
        pageSize: Int = 30,
        page: Int = 1,
    ): List<GithubRelease> {
        val response =
            client
                .get("repos/$OWNER/$REPO/releases") {
                    parameter("per_page", pageSize)
                    parameter("page", page)
                }.bodyAsText()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /**
     * 下载 Release 中的 APK 附件。
     *
     * @param release Release 信息。
     * @param file 目标文件路径。
     * @param onProgress 下载进度回调（已下载字节, 总字节）。
     */
    suspend fun downloadUpdate(
        release: GithubRelease,
        file: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ) {
        val downloadUrl =
            release.assets
                .firstOrNull {
                    it.name.contains("release") || it.name.contains("alpha")
                }?.browserDownloadUrl
                ?: throw IllegalStateException("Didn't find download url")

        client
            .prepareRequest {
                url(toGhProxyUrl(downloadUrl))
                onDownload { downloaded, total ->
                    onProgress(downloaded, total ?: 0)
                }
            }.execute { response ->
                response.bodyAsChannel().copyTo(file.outputStream())
            }
    }

    /**
     * 判断响应是否为 GitHub 的 404 错误体。
     *
     * `internal` 仅为便于单测覆盖：迁移仓库后「尚无 Release」会一直返回 404，
     * 这条判定如果失效，UI 会把「还没发版」误报成网络故障。
     */
    internal fun isNotFound(data: String): Boolean {
        val element = runCatching { json.parseToJsonElement(data) }.getOrNull() as? JsonObject ?: return false
        return element.jsonObject["status"]?.jsonPrimitive?.content == "404"
    }

    private fun checkErrorMessage(data: String) {
        val responseElement = json.parseToJsonElement(data)
        if (responseElement !is JsonObject) return
        val responseObject = responseElement.jsonObject
        if (responseObject["message"] != null) {
            error(responseObject["message"]!!.jsonPrimitive.content)
        }
    }

    private fun toGhProxyUrl(originalUrl: String): String = "https://ghfast.top/$originalUrl"
}

/**
 * 发布仓库尚未发布任何 Release。
 *
 * 新建 / 迁移后的仓库在首次打 tag 发版前会一直返回该状态，
 * 与网络故障、仓库不存在等错误区分开，便于 UI 给出准确提示。
 */
class NoReleasePublishedException : IllegalStateException("${GithubApi.OWNER}/${GithubApi.REPO} 尚未发布任何 Release")
