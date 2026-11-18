package dev.frost819.newbv.app.network

import dev.frost819.newbv.BuildConfig
import dev.frost819.newbv.app.network.entity.GithubRelease
import dev.frost819.newbv.app.network.entity.ReleaseManifest
import dev.frost819.newbv.biliapi.http.util.useSharedBiliClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.header
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
 *
 * ## 双通道设计
 *
 * 直连 `api.github.com` 优先；失败时（超时、403、DNS 不通等）自动改用镜像
 * 读取随版本上传的 [ReleaseManifest] 清单。原因见 [MIRROR_MANIFEST_URL] 注释：
 * 公益镜像只代理文件路径，不代理 API，所以清单必须作为 Release 附件上传。
 *
 * 下载同理：优先直连 GitHub，失败再走镜像。
 */
object GithubApi {
    /** 发布仓库 owner。 */
    const val OWNER: String = BuildConfig.RELEASE_REPO_OWNER

    /** 发布仓库名。 */
    const val REPO: String = BuildConfig.RELEASE_REPO_NAME

    /** 仓库主页地址，供关于页展示。 */
    val REPO_HTML_URL: String get() = "https://github.com/$OWNER/$REPO"

    /** 备用下载/清单镜像前缀。 */
    private const val MIRROR_PREFIX = "https://ghfast.top/"

    /**
     * 镜像侧的版本清单地址。
     *
     * `releases/latest/download/<name>` 是 GitHub 的固定语义路径：`latest` 会被解析为
     * 当前最新 Release，所以这个 URL 无需知道具体版本号即可指向最新清单。
     * 每个版本发版时都会把同名的 `latest.json` 作为附件上传（见 release.yml）。
     */
    private val MIRROR_MANIFEST_URL: String
        get() = "${MIRROR_PREFIX}https://github.com/$OWNER/$REPO/releases/latest/download/$MANIFEST_ASSET_NAME"

    /** 清单附件文件名，需与 CI 上传的名字一致。 */
    const val MANIFEST_ASSET_NAME = "latest.json"

    private val json =
        Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
            prettyPrint = true
        }

    private val client: HttpClient =
        HttpClient(OkHttp) {
            engine {
                useSharedBiliClient()
            }
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
     * 先直连 GitHub API；直连失败时自动回退到镜像清单（见 [MIRROR_MANIFEST_URL]）。
     * 两路都失败才抛出原始异常。
     *
     * @throws NoReleasePublishedException 仓库存在但尚未发布任何 Release
     *   （GitHub 对「无 Release」与「仓库不存在」都返回 404，需按响应体区分）
     * @throws IllegalStateException 其它 API 错误
     */
    suspend fun getLatestBuild(): GithubRelease =
        runCatching { fetchLatestFromApi() }
            .getOrElse { apiError ->
                // 「尚未发版」是确定结论，不是网络问题，不必再试镜像
                if (apiError is NoReleasePublishedException) throw apiError
                runCatching { fetchLatestFromMirror() }
                    .getOrElse { throw apiError }
            }

    /** 直连 GitHub API 获取最新 Release。 */
    private suspend fun fetchLatestFromApi(): GithubRelease {
        val response = client.get("repos/$OWNER/$REPO/releases/latest").bodyAsText()
        if (isNotFound(response)) throw NoReleasePublishedException()
        checkErrorMessage(response)
        return json.decodeFromString(response)
    }

    /**
     * 从镜像读取版本清单并转换为 [GithubRelease]。
     *
     * 清单只含检查更新必需的字段，这里补齐 UI 会读到的部分：下载地址按镜像路径构造，
     * 保证后续 [downloadUpdate] 即使直连失败也能拿到可用链接。
     */
    private suspend fun fetchLatestFromMirror(): GithubRelease {
        val response =
            client
                .get(MIRROR_MANIFEST_URL) {
                    // 镜像按 URL 路径转发，不能复用默认的 api.github.com host
                    header("Accept", "application/json")
                }.bodyAsText()

        val manifest =
            runCatching { json.decodeFromString<ReleaseManifest>(response) }
                .getOrElse {
                    error("镜像清单解析失败，响应内容：${response.take(200)}")
                }

        return manifest.toGithubRelease()
    }

    /** 把镜像清单转换成与 API 响应同形的模型，避免上层改动。 */
    private fun ReleaseManifest.toGithubRelease(): GithubRelease {
        val apkUrl = "https://github.com/$OWNER/$REPO/releases/download/$tagName/$apkName"
        return GithubRelease(
            assets =
                listOf(
                    GithubRelease.Asset(
                        browserDownloadUrl = apkUrl,
                        contentType = "application/vnd.android.package-archive",
                        downloadCount = 0,
                        id = 0,
                        name = apkName,
                        size = apkSize.toInt(),
                        state = "uploaded",
                        url = apkUrl,
                    ),
                ),
            assetsUrl = "",
            body = body,
            createdAt = publishedAt,
            draft = false,
            htmlUrl = "$REPO_HTML_URL/releases/tag/$tagName",
            id = 0,
            name = "newBV $versionName",
            prerelease = false,
            publishedAt = publishedAt,
            tagName = tagName,
            tarballUrl = "",
            targetCommitish = "",
            uploadUrl = "",
            url = "",
            zipballUrl = "",
        )
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
     * 先直连 GitHub 下载；失败时自动改用镜像。镜像只代理文件路径，所以这里拼的是
     * 原始下载地址而不是 API 地址。
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

        runCatching {
            downloadTo(downloadUrl, file, onProgress)
        }.getOrElse { directError ->
            // 直连失败后走镜像；先清掉可能已写入的部分内容，避免拼出损坏的 APK
            runCatching { if (file.exists()) file.delete() }
            runCatching {
                downloadTo(toMirrorUrl(downloadUrl), file, onProgress)
            }.getOrElse {
                throw directError
            }
        }
    }

    /** 执行一次下载，不在内部做任何回退。 */
    private suspend fun downloadTo(
        url: String,
        file: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        client
            .prepareRequest {
                url(url)
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

    /**
     * 把 GitHub 下载地址转成镜像地址。
     *
     * 已经带镜像前缀时原样返回，避免 `downloadUpdate` 重试时重复拼接。
     */
    internal fun toMirrorUrl(originalUrl: String): String =
        if (originalUrl.startsWith(MIRROR_PREFIX)) originalUrl else "$MIRROR_PREFIX$originalUrl"
}

/**
 * 发布仓库尚未发布任何 Release。
 *
 * 新建 / 迁移后的仓库在首次打 tag 发版前会一直返回该状态，
 * 与网络故障、仓库不存在等错误区分开，便于 UI 给出准确提示。
 */
class NoReleasePublishedException : IllegalStateException("${GithubApi.OWNER}/${GithubApi.REPO} 尚未发布任何 Release")
