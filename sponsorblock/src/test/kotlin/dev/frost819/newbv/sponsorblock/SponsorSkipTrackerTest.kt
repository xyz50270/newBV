package dev.frost819.newbv.sponsorblock

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import org.junit.jupiter.api.Test

/**
 * [SponsorSkipTracker] 的单元测试。
 *
 * 覆盖：命中判定、分类过滤、去重、用户拖回后重新触发、重叠片段取最晚结束。
 */
class SponsorSkipTrackerTest {
    private fun segment(
        startMs: Long,
        endMs: Long,
        category: SponsorCategory = SponsorCategory.Sponsor,
        uuid: String = "uuid-$startMs-$endMs",
    ) = SponsorSegment(uuid = uuid, startMs = startMs, endMs = endMs, category = category)

    @Test
    fun `findSkipTarget returns segment containing position`() {
        val tracker = SponsorSkipTracker()
        val target = segment(10_000, 20_000)
        tracker.setSegments(listOf(target))

        val result = tracker.findSkipTarget(positionMs = 15_000, enabledCategories = setOf(SponsorCategory.Sponsor))

        assertThat(result).isEqualTo(target)
    }

    @Test
    fun `findSkipTarget treats end time as exclusive`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))

        val result = tracker.findSkipTarget(positionMs = 20_000, enabledCategories = setOf(SponsorCategory.Sponsor))

        assertThat(result).isNull()
    }

    @Test
    fun `findSkipTarget returns null when position is before segment`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))

        val result = tracker.findSkipTarget(positionMs = 9_999, enabledCategories = setOf(SponsorCategory.Sponsor))

        assertThat(result).isNull()
    }

    @Test
    fun `findSkipTarget ignores segments of disabled categories`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(0, 10_000, category = SponsorCategory.Intro)))

        val result = tracker.findSkipTarget(positionMs = 5_000, enabledCategories = setOf(SponsorCategory.Sponsor))

        assertThat(result).isNull()
    }

    @Test
    fun `findSkipTarget ignores segments shorter than minimum duration`() {
        val tracker = SponsorSkipTracker(minDurationMs = 1_000)
        tracker.setSegments(listOf(segment(5_000, 5_500)))

        val result = tracker.findSkipTarget(positionMs = 5_100, enabledCategories = setOf(SponsorCategory.Sponsor))

        assertThat(result).isNull()
    }

    @Test
    fun `findSkipTarget returns each segment only once`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))

        val first = tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))
        val second = tracker.findSkipTarget(15_500, setOf(SponsorCategory.Sponsor))

        assertThat(first).isNotNull()
        assertThat(second).isNull()
    }

    @Test
    fun `findSkipTarget triggers again after user rewinds before segment start`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))

        assertThat(tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))).isNotNull()
        // 用户拖回片段之前
        assertThat(tracker.findSkipTarget(1_000, setOf(SponsorCategory.Sponsor))).isNull()

        val again = tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))
        assertThat(again).isNotNull()
    }

    @Test
    fun `findSkipTarget keeps dedupe when position stays after segment end`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))

        assertThat(tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))).isNotNull()
        // 跳到片段末尾之后，再次往回拖到片段尾部不应重复触发
        assertThat(tracker.findSkipTarget(25_000, setOf(SponsorCategory.Sponsor))).isNull()
        assertThat(tracker.findSkipTarget(19_000, setOf(SponsorCategory.Sponsor))).isNull()
    }

    @Test
    fun `findSkipTarget picks the segment with latest end time for overlapping ranges`() {
        val tracker = SponsorSkipTracker()
        val short = segment(10_000, 15_000, uuid = "short")
        val long = segment(12_000, 30_000, uuid = "long")
        tracker.setSegments(listOf(short, long))

        val result = tracker.findSkipTarget(13_000, setOf(SponsorCategory.Sponsor))

        assertThat(result).isEqualTo(long)
    }

    @Test
    fun `findSkipTarget returns null when no categories enabled`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(0, 10_000)))

        assertThat(tracker.findSkipTarget(5_000, emptySet())).isNull()
    }

    @Test
    fun `findSkipTarget returns null for negative position`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(0, 10_000)))

        assertThat(tracker.findSkipTarget(-1, setOf(SponsorCategory.Sponsor))).isNull()
    }

    @Test
    fun `setSegments clears previous skip history`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))
        assertThat(tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))).isNotNull()

        tracker.setSegments(listOf(segment(10_000, 20_000)))

        assertThat(tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))).isNotNull()
    }

    @Test
    fun `reset clears segments and history`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(10_000, 20_000)))
        tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))

        tracker.reset()

        assertThat(tracker.currentSegments).isEmpty()
        assertThat(tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))).isNull()
    }

    // ── 预提示（到达前 leadMs 内）────────────────────────────

    @Test
    fun `findUpcomingSegment returns segment inside lead window`() {
        val tracker = SponsorSkipTracker()
        val target = segment(30_000, 40_000)
        tracker.setSegments(listOf(target))

        // 起点前 10 秒窗口为 [20_000, 30_000)
        val result = tracker.findUpcomingSegment(25_000, setOf(SponsorCategory.Sponsor), leadMs = 10_000)

        assertThat(result).isEqualTo(target)
    }

    @Test
    fun `findUpcomingSegment is inclusive at window start`() {
        val tracker = SponsorSkipTracker()
        val target = segment(30_000, 40_000)
        tracker.setSegments(listOf(target))

        val result = tracker.findUpcomingSegment(20_000, setOf(SponsorCategory.Sponsor), leadMs = 10_000)

        assertThat(result).isEqualTo(target)
    }

    @Test
    fun `findUpcomingSegment excludes position at segment start`() {
        val tracker = SponsorSkipTracker()
        tracker.setSegments(listOf(segment(30_000, 40_000)))

        // 已进入片段时由 findSkipTarget 处理，预提示必须让位
        val result = tracker.findUpcomingSegment(30_000, setOf(SponsorCategory.Sponsor), leadMs = 10_000)

        assertThat(result).isNull()
    }

    @Test
    fun `findUpcomingSegment clamps window to zero when segment starts early`() {
        // 片段起点仅 3 秒，领先 10 秒会让窗口起点算成 -7000；
        // 必须夹到 0，播放一开始就立即提示
        val tracker = SponsorSkipTracker()
        val target = segment(3_000, 13_000)
        tracker.setSegments(listOf(target))

        val result = tracker.findUpcomingSegment(0, setOf(SponsorCategory.Sponsor), leadMs = 10_000)

        assertThat(result).isEqualTo(target)
    }

    @Test
    fun `findUpcomingSegment skips already cancelled segment`() {
        val tracker = SponsorSkipTracker()
        val target = segment(30_000, 40_000)
        tracker.setSegments(listOf(target))
        tracker.cancel(target.uuid)

        val result = tracker.findUpcomingSegment(25_000, setOf(SponsorCategory.Sponsor), leadMs = 10_000)

        assertThat(result).isNull()
    }

    @Test
    fun `findUpcomingSegment returns earliest when several windows overlap`() {
        val tracker = SponsorSkipTracker()
        val first = segment(20_000, 25_000, uuid = "first")
        val second = segment(25_000, 30_000, uuid = "second")
        tracker.setSegments(listOf(first, second))

        val result = tracker.findUpcomingSegment(15_000, setOf(SponsorCategory.Sponsor), leadMs = 10_000)

        assertThat(result).isEqualTo(first)
    }

    // ── 用户取消 ──────────────────────────────────────────────

    @Test
    fun `cancelled segment is never auto skipped`() {
        val tracker = SponsorSkipTracker()
        val target = segment(10_000, 20_000)
        tracker.setSegments(listOf(target))
        tracker.cancel(target.uuid)

        val result = tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))

        assertThat(result).isNull()
    }

    @Test
    fun `cancelled segment stays cancelled after rewinding before it`() {
        val tracker = SponsorSkipTracker()
        val target = segment(10_000, 20_000)
        tracker.setSegments(listOf(target))
        tracker.cancel(target.uuid)

        // 拖回片段之前：只清「已跳过」记录，取消记录必须保留
        tracker.findSkipTarget(1_000, setOf(SponsorCategory.Sponsor))
        val result = tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))

        assertThat(result).isNull()
    }

    @Test
    fun `setSegments clears cancellation records`() {
        val tracker = SponsorSkipTracker()
        val target = segment(10_000, 20_000)
        tracker.setSegments(listOf(target))
        tracker.cancel(target.uuid)

        // 重新拉取数据后应重新获得跳过机会（与「跳过记录」一致）
        tracker.setSegments(listOf(target))

        assertThat(tracker.findSkipTarget(15_000, setOf(SponsorCategory.Sponsor))).isEqualTo(target)
    }

    @Test
    fun `setSegments sorts by start time`() {
        val tracker = SponsorSkipTracker()
        val late = segment(30_000, 40_000, uuid = "late")
        val early = segment(1_000, 2_000, uuid = "early")
        tracker.setSegments(listOf(late, early))

        assertThat(tracker.currentSegments).containsExactly(early, late).inOrder()
    }
}
