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

    @Test
    fun `setSegments sorts by start time`() {
        val tracker = SponsorSkipTracker()
        val late = segment(30_000, 40_000, uuid = "late")
        val early = segment(1_000, 2_000, uuid = "early")
        tracker.setSegments(listOf(late, early))

        assertThat(tracker.currentSegments).containsExactly(early, late).inOrder()
    }
}
