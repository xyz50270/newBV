package dev.frost819.newbv.app.ui.component.player

import androidx.compose.ui.graphics.Color
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment

/**
 * 片段分类在进度条上的标识色。
 *
 * 以「黑底上可见 + 互相可区分」为准则确定，而非直接照搬上游配色：
 * - 每个颜色对纯黑的 WCAG 对比度 ≥ 3.4（8dp 细线仍可辨认）；
 *   上游的片尾蓝 `#0202ED` 只有 2.23，几乎看不见，已调亮为 `#3D5BFF`。
 * - 任意两色的 CIELAB ΔE ≥ 27，避免色相过近的两种分类被误认。
 *   上游 `padding`/`poi_highlight`（粉色系 ΔE 不足）与 `jumpscare`/`exclusive_access`
 *   （红色系）两两过于接近，已分别拉开明度与色相。
 *
 * 12 个分类铺满色相环仍可能让人一时分不清，因此进度条区段同时用
 * 位置（起止时间）+ 颜色双重标识，设置页的类型列表也带同色色块可对照。
 */
val SponsorCategory.markerColor: Color
    get() =
        when (this) {
            SponsorCategory.Sponsor -> Color(0xFF00D400)
            SponsorCategory.SelfPromo -> Color(0xFFFFFF00)
            SponsorCategory.Interaction -> Color(0xFFCC00FF)
            SponsorCategory.Intro -> Color(0xFF00FFFF)
            SponsorCategory.Outro -> Color(0xFF3D5BFF)
            SponsorCategory.Preview -> Color(0xFF008FD6)
            SponsorCategory.Filler -> Color(0xFF6A2BFF)
            SponsorCategory.MusicOfftopic -> Color(0xFFFF9900)
            SponsorCategory.Padding -> Color(0xFFFF69B4)
            SponsorCategory.PoiHighlight -> Color(0xFFE6006B)
            SponsorCategory.Jumpscare -> Color(0xFFFF7A6E)
            SponsorCategory.ExclusiveAccess -> Color(0xFFD62B2B)
        }

/**
 * 进度条上的一个区段标识。
 *
 * @property startMs 区段开始（毫秒，已裁剪到 `[0, duration]`）
 * @property endMs 区段结束（毫秒，已裁剪到 `[0, duration]`）
 * @property color 区段颜色（按片段分类区分）
 */
data class SeekMarker(
    val startMs: Long,
    val endMs: Long,
    val color: Color,
)

/**
 * 把 SponsorBlock 片段转换为进度条上的区段标识。
 *
 * 只转换用户启用的分类；起止非法（end <= start）或完全落在视频时长之外的片段被丢弃，
 * 部分越界的片段裁剪到 `[0, durationMs]`。结果按开始时间升序，方便绘制与断言。
 *
 * @param segments 当前分 P 的片段列表（可能含未启用分类，需在此过滤）
 * @param enabledCategories 用户启用自动跳过的分类
 * @param durationMs 视频总时长（毫秒）；<= 0 时无法换算，返回空列表
 * @return 进度条区段标识，时间单位毫秒
 */
fun sponsorSeekMarkers(
    segments: List<SponsorSegment>,
    enabledCategories: Set<SponsorCategory>,
    durationMs: Long,
): List<SeekMarker> {
    if (durationMs <= 0L || segments.isEmpty() || enabledCategories.isEmpty()) return emptyList()

    return segments
        .asSequence()
        .filter { it.category in enabledCategories }
        .mapNotNull { segment ->
            val start = segment.startMs.coerceAtLeast(0L)
            val end = segment.endMs.coerceAtMost(durationMs)
            if (end <= start) return@mapNotNull null
            SeekMarker(startMs = start, endMs = end, color = segment.category.markerColor)
        }.sortedBy { it.startMs }
        .toList()
}
