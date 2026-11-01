package dev.frost819.newbv.sponsorblock

import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment

/**
 * 自动跳过判定器。
 *
 * 只做纯计算：输入当前播放位置，输出「应该跳到哪个片段的末尾」与「即将进入哪个片段」，
 * 不持有播放器引用。
 *
 * 判定规则：
 * - 位置落在某个启用分类的片段内，且该片段尚未被自动跳过 → 返回它
 * - 同一片段只自动跳过一次：用户主动拖回片段内部不会反复被打断
 * - 用户拖到片段起点之前时清除该片段的跳过记录，再次播放到该片段会重新自动跳过
 * - 位置越过片段终点后保留记录，避免用户在片段尾部来回拖动时反复触发
 * - 重叠片段取结束时间最晚的一个（一次跳到位，避免连续跳多次）
 * - 用户显式取消的片段本次播放内不再自动跳过，即使位置回退到片段之前也不恢复
 *
 * 实例与「一次播放会话」绑定：切换视频时调用 [reset] 或新建实例。
 *
 * @param minDurationMs 片段最短时长，过短的片段（社区标注噪声）不跳过
 */
class SponsorSkipTracker(
    private val minDurationMs: Long = DEFAULT_MIN_DURATION_MS,
) {
    companion object {
        /** 默认最短可跳片段时长（毫秒）。 */
        const val DEFAULT_MIN_DURATION_MS = 1_000L

        /** 判定「用户拖回片段之前」的容差（毫秒），吸收进度采样抖动。 */
        private const val REWIND_TOLERANCE_MS = 1_500L
    }

    private var segments: List<SponsorSegment> = emptyList()

    /** 本次播放已自动跳过的片段 UUID。 */
    private val skippedUuids = mutableSetOf<String>()

    /** 本次播放被用户显式取消的片段 UUID。 */
    private val cancelledUuids = mutableSetOf<String>()

    /** 当前生效的片段列表（按开始时间升序）。 */
    val currentSegments: List<SponsorSegment> get() = segments

    /**
     * 替换片段列表（加载完成或重新加载时调用）。
     *
     * 会清空跳过与取消记录：同一视频重新拉取数据后应重新获得跳过机会。
     *
     * @param newSegments 新片段列表，顺序无关
     */
    fun setSegments(newSegments: List<SponsorSegment>) {
        segments = newSegments.sortedBy { it.startMs }
        skippedUuids.clear()
        cancelledUuids.clear()
    }

    /** 清空片段与全部记录（切换视频时调用）。 */
    fun reset() {
        segments = emptyList()
        skippedUuids.clear()
        cancelledUuids.clear()
    }

    /**
     * 根据当前播放位置判断是否需要跳过。
     *
     * 命中时会立即把该片段记为已跳过，因此调用方可以直接执行跳转，
     * 无需再次去重。
     *
     * @param positionMs 当前播放位置（毫秒）
     * @param enabledCategories 用户启用自动跳过的分类
     * @return 需要跳过的片段；无需跳过时返回 null
     */
    fun findSkipTarget(
        positionMs: Long,
        enabledCategories: Set<SponsorCategory>,
    ): SponsorSegment? {
        if (segments.isEmpty() || enabledCategories.isEmpty() || positionMs < 0L) return null

        releaseRewoundSegments(positionMs)

        val target =
            candidateSegments(enabledCategories)
                .filter { it.uuid !in skippedUuids }
                .filter { it.uuid !in cancelledUuids }
                .filter { it.contains(positionMs) }
                .maxByOrNull { it.endMs }

        if (target != null) skippedUuids.add(target.uuid)
        return target
    }

    /**
     * 查找即将进入的片段，用于「到达前若干秒」的预提示。
     *
     * 提示窗口为 `[max(0, start - leadMs), start)`：片段起点距视频开头不足
     * [leadMs] 时窗口从 0 开始，因此一开始播放就应立即提示，而不是把起点
     * 往前推出负数（避免长度较短的视频上出现无意义的计算）。
     *
     * 已被自动跳过或用户取消的片段不会再次提示。
     *
     * @param positionMs 当前播放位置（毫秒）
     * @param enabledCategories 用户启用自动跳过的分类
     * @param leadMs 提前提示的时长（毫秒）
     * @return 即将进入的片段（多个时取最早开始的）；不在任何提示窗口内返回 null
     */
    fun findUpcomingSegment(
        positionMs: Long,
        enabledCategories: Set<SponsorCategory>,
        leadMs: Long,
    ): SponsorSegment? {
        if (segments.isEmpty() || enabledCategories.isEmpty() || positionMs < 0L) return null

        return candidateSegments(enabledCategories)
            .filter { it.uuid !in skippedUuids }
            .filter { it.uuid !in cancelledUuids }
            .filter { positionMs >= (it.startMs - leadMs).coerceAtLeast(0L) && positionMs < it.startMs }
            .minByOrNull { it.startMs }
    }

    /**
     * 记录用户取消了某个片段的自动跳过。
     *
     * 取消是本轮播放内永久的：即使位置回退到片段之前（[releaseRewoundSegments] 会
     * 清除「已跳过」记录）也不会恢复，避免用户取消后又被自动跳过。
     *
     * @param uuid 片段唯一标识
     */
    fun cancel(uuid: String) {
        cancelledUuids.add(uuid)
    }

    /**
     * 撤销某个片段的跳过记录。
     *
     * 供调用方在「命中后跳转事件未能送达」时回滚：若不撤销，该片段会被永久视为已跳过，
     * 本轮播放内再也不会触发自动跳过。
     *
     * @param uuid 片段唯一标识
     */
    fun forget(uuid: String) {
        skippedUuids.remove(uuid)
    }

    /** 可参与判定的片段：分类启用、时长达标。 */
    private fun candidateSegments(enabledCategories: Set<SponsorCategory>): Sequence<SponsorSegment> =
        segments
            .asSequence()
            .filter { it.category in enabledCategories }
            .filter { it.durationMs >= minDurationMs }

    /**
     * 用户拖回到片段起点之前时，清除该片段的跳过记录。
     *
     * 未进入过片段（位置一直早于起点）的片段本来就没有记录，清除无副作用。
     * 被用户取消的片段除外：取消后应始终不再自动跳过。
     */
    private fun releaseRewoundSegments(positionMs: Long) {
        if (skippedUuids.isEmpty()) return
        segments.forEach { segment ->
            if (positionMs < segment.startMs - REWIND_TOLERANCE_MS) {
                skippedUuids.remove(segment.uuid)
            }
        }
    }
}
