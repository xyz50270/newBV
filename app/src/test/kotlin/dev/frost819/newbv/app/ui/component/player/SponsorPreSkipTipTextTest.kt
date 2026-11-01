package dev.frost819.newbv.app.ui.component.player

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import org.junit.jupiter.api.Test

/**
 * [sponsorPreSkipTipText] 的单元测试。
 *
 * 重点覆盖倒计时取整边界与「片段起点在身后」的情况，避免出现
 * 「0 秒后自动跳过」这类误导文案。
 */
class SponsorPreSkipTipTextTest {
    private val segment =
        SponsorSegment(
            uuid = "uuid",
            startMs = 30_000,
            endMs = 40_000,
            category = SponsorCategory.Sponsor,
        )

    @Test
    fun `shows whole seconds remaining before segment`() {
        val text = sponsorPreSkipTipText(segment, positionMs = 20_000)

        assertThat(text).isEqualTo("赞助推广，10 秒后自动跳过，按返回键取消")
    }

    @Test
    fun `rounds partial second up`() {
        val text = sponsorPreSkipTipText(segment, positionMs = 29_500)

        // 还剩 500ms，向上取整为 1 秒，不能显示 0
        assertThat(text).contains("1 秒后自动跳过")
    }

    @Test
    fun `clamps to one second when position is past segment start`() {
        val text = sponsorPreSkipTipText(segment, positionMs = 35_000)

        assertThat(text).contains("1 秒后自动跳过")
    }

    @Test
    fun `null pending returns null text`() {
        assertThat(sponsorPreSkipTipTextOrNull(pending = null, positionMs = 20_000)).isNull()
    }

    @Test
    fun `non null pending returns text`() {
        val text = sponsorPreSkipTipTextOrNull(pending = segment, positionMs = 25_000)

        assertThat(text).isNotNull()
        assertThat(text).contains("5 秒后自动跳过")
    }
}
