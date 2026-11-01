package dev.frost819.newbv.sponsorblock.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/skipSegments` 的单个响应元素。
 *
 * 服务端字段名为大写开头的 `UUID`，其余为小写驼峰，这里显式声明序列化名。
 *
 * 数值字段的类型必须与服务端实际返回一致，否则 kotlinx.serialization 会抛
 * `JsonDecodingException` 导致整包解析失败。实测 `videoDuration` 是**小数**
 * （如 `1013.525`），声明成 `Int` 会让整个响应解析不出来，表现为「明明有标注、
 * App 却显示没有可跳过片段」。
 *
 * @property segment 片段起止时间（秒），固定两个元素 `[start, end]`
 * @property cid 片段所属分 P 的 CID（字符串形式）
 * @property uuid 片段唯一标识
 * @property category 分类标识
 * @property actionType 动作类型，`skip` 为跳过、`mute` 为静音、`full` 表示整段视频都是该分类
 * @property votes 社区投票数
 * @property videoDuration 提交时的视频时长（秒，小数），0 表示未知
 */
@Serializable
internal data class SkipSegmentResponse(
    val segment: List<Double> = emptyList(),
    val cid: String? = null,
    @SerialName("UUID")
    val uuid: String = "",
    val category: String = "",
    val actionType: String = "",
    val votes: Int = 0,
    val videoDuration: Double = 0.0,
) {
    /** 片段开始时间（秒）。 */
    val startSeconds: Double? get() = segment.getOrNull(0)

    /** 片段结束时间（秒）。 */
    val endSeconds: Double? get() = segment.getOrNull(1)
}
