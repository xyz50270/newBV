package dev.frost819.newbv.app.network.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 发版清单，作为 Release 附件随每个版本上传。
 *
 * 存在的原因：`api.github.com` 在部分地区不可达，而 `ghfast.top` 这类公益镜像
 * **只代理文件路径**（release 下载、raw），不代理 API（对 `api.github.com` 返回 403）。
 * 因此直连失败时无法从镜像拿到 Release 元数据。
 *
 * 解决办法是把检查更新所需的字段写成 JSON 一起发上去：镜像的
 * `releases/latest/download/latest.json` 永远指向最新版附件，`latest` 段由 GitHub
 * 解析成「当前最新 Release」，所以这个 URL 不需要知道具体版本号就能取到最新清单。
 *
 * @property versionCode 整数版本号，用于判断是否有更新（唯一比较依据）
 * @property versionName 显示用版本名（如 `1.3.1.2.r244.290fc17`）
 * @property tagName Git 标签
 * @property apkName APK 附件文件名
 * @property apkSize APK 字节数，供下载前校验
 * @property publishedAt 发布时间（ISO 8601）
 * @property body Release notes 正文（Markdown）
 */
@Serializable
data class ReleaseManifest(
    @SerialName("versionCode")
    val versionCode: Int,
    @SerialName("versionName")
    val versionName: String,
    @SerialName("tagName")
    val tagName: String,
    @SerialName("apkName")
    val apkName: String,
    @SerialName("apkSize")
    val apkSize: Long,
    @SerialName("publishedAt")
    val publishedAt: String = "",
    @SerialName("body")
    val body: String = "",
)
