package dev.frost819.newbv.app.ui.component.player

import dev.frost819.newbv.app.entity.player.displayName
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment

/**
 * 构造「即将跳过」预提示文案。
 *
 * 文案包含剩余秒数，让用户知道还有多久会跳走，从而决定是否按返回键取消。
 * 剩余秒数向上取整：剩余不足 1 秒时仍显示 1 秒，避免出现「0 秒后跳过」这种
 * 让人以为已经跳过的文本；片段起点已在身后（用户刚拖进片段）时同样按 1 秒处理。
 *
 * @param segment 即将进入的片段
 * @param positionMs 当前播放位置（毫秒）
 * @return 预提示文本
 */
fun sponsorPreSkipTipText(
    segment: SponsorSegment,
    positionMs: Long,
): String {
    val remainingMs = (segment.startMs - positionMs).coerceAtLeast(0L)
    val remainingSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(1L)
    return "${segment.category.displayName}，$remainingSeconds 秒后自动跳过，按返回键取消"
}

/**
 * 构造「即将跳过」预提示文案；没有待跳过片段时返回 null（不显示提示）。
 *
 * @param pending 即将进入的片段，null 表示当前没有预提示
 * @param positionMs 当前播放位置（毫秒）
 */
fun sponsorPreSkipTipTextOrNull(
    pending: SponsorSegment?,
    positionMs: Long,
): String? = pending?.let { sponsorPreSkipTipText(it, positionMs) }
