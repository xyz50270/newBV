package dev.frost819.newbv.app.ui.component.player

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import org.junit.jupiter.api.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * [sponsorSeekMarkers] 与分类配色的单元测试。
 *
 * 验证：分类过滤、越界裁剪、非法区间丢弃、排序、颜色按分类区分。
 */
class SponsorSeekMarkersTest {
    private fun segment(
        startMs: Long,
        endMs: Long,
        category: SponsorCategory = SponsorCategory.Sponsor,
        uuid: String = "u-$startMs-$endMs",
    ) = SponsorSegment(uuid = uuid, startMs = startMs, endMs = endMs, category = category)

    @Test
    fun `converts enabled segments to markers`() {
        val markers =
            sponsorSeekMarkers(
                segments = listOf(segment(10_000, 20_000)),
                enabledCategories = setOf(SponsorCategory.Sponsor),
                durationMs = 100_000L,
            )

        assertThat(markers).hasSize(1)
        assertThat(markers[0].startMs).isEqualTo(10_000L)
        assertThat(markers[0].endMs).isEqualTo(20_000L)
        assertThat(markers[0].color).isEqualTo(SponsorCategory.Sponsor.markerColor)
    }

    @Test
    fun `drops segments of disabled categories`() {
        val markers =
            sponsorSeekMarkers(
                segments =
                    listOf(
                        segment(10_000, 20_000, category = SponsorCategory.Intro),
                        segment(30_000, 40_000, category = SponsorCategory.Sponsor),
                    ),
                enabledCategories = setOf(SponsorCategory.Sponsor),
                durationMs = 100_000L,
            )

        assertThat(markers).hasSize(1)
        assertThat(markers[0].startMs).isEqualTo(30_000L)
    }

    @Test
    fun `clamps segments crossing video bounds`() {
        val markers =
            sponsorSeekMarkers(
                segments = listOf(segment(-5_000, 200_000)),
                enabledCategories = setOf(SponsorCategory.Sponsor),
                durationMs = 100_000L,
            )

        assertThat(markers[0].startMs).isEqualTo(0L)
        assertThat(markers[0].endMs).isEqualTo(100_000L)
    }

    @Test
    fun `drops segments entirely outside video duration`() {
        val markers =
            sponsorSeekMarkers(
                segments = listOf(segment(120_000, 130_000)),
                enabledCategories = setOf(SponsorCategory.Sponsor),
                durationMs = 100_000L,
            )

        assertThat(markers).isEmpty()
    }

    @Test
    fun `returns empty when duration unknown`() {
        val markers =
            sponsorSeekMarkers(
                segments = listOf(segment(1_000, 2_000)),
                enabledCategories = setOf(SponsorCategory.Sponsor),
                durationMs = 0L,
            )

        assertThat(markers).isEmpty()
    }

    @Test
    fun `returns empty when no category enabled`() {
        val markers =
            sponsorSeekMarkers(
                segments = listOf(segment(1_000, 2_000)),
                enabledCategories = emptySet(),
                durationMs = 100_000L,
            )

        assertThat(markers).isEmpty()
    }

    @Test
    fun `markers are sorted by start time`() {
        val markers =
            sponsorSeekMarkers(
                segments =
                    listOf(
                        segment(50_000, 60_000, uuid = "late"),
                        segment(10_000, 20_000, uuid = "early"),
                    ),
                enabledCategories = setOf(SponsorCategory.Sponsor),
                durationMs = 100_000L,
            )

        assertThat(markers.map { it.startMs }).containsExactly(10_000L, 50_000L).inOrder()
    }

    @Test
    fun `every category has a distinct marker color`() {
        val colors = SponsorCategory.entries.map { it.markerColor }

        assertThat(colors).hasSize(SponsorCategory.entries.size)
        assertThat(colors.toSet()).hasSize(SponsorCategory.entries.size)
    }

    @Test
    fun `marker colors are fully opaque and non transparent`() {
        SponsorCategory.entries.forEach { category ->
            assertThat(category.markerColor.alpha).isEqualTo(1f)
        }
    }

    @Test
    fun `every marker color is visible on a black background`() {
        // 播放器固定深色主题；8dp 细线要求对纯黑至少 3:1，否则用户看不见该区段
        SponsorCategory.entries.forEach { category ->
            val contrast = contrastRatio(category.markerColor, Color.Black)
            assertThat(contrast).isAtLeast(3.0f)
        }
    }

    @Test
    fun `marker colors are far enough apart in CIELAB space`() {
        // 12 个分类铺满色相环，仍需保证两两可区分；ΔE < 20 时肉眼容易混淆
        val entries = SponsorCategory.entries
        entries.indices.forEach { i ->
            (i + 1 until entries.size).forEach { j ->
                val first = entries[i]
                val second = entries[j]
                val delta = deltaE(first.markerColor, second.markerColor)
                assertThat(delta).isAtLeast(20f)
            }
        }
    }

    /** WCAG 相对亮度对比度。 */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** sRGB 相对亮度（WCAG 定义）。 */
    private fun relativeLuminance(color: Color): Float {
        fun channel(value: Float): Float =
            if (value <= 0.04045f) {
                value / 12.92f
            } else {
                ((value + 0.055f) / 1.055f).pow(2.4f)
            }

        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }

    /** CIELAB (D65) 色差 ΔE*ab。 */
    private fun deltaE(
        a: Color,
        b: Color,
    ): Float {
        val la = lab(a)
        val lb = lab(b)
        return sqrt((la[0] - lb[0]).pow(2f) + (la[1] - lb[1]).pow(2f) + (la[2] - lb[2]).pow(2f))
    }

    private fun lab(color: Color): FloatArray {
        fun lin(value: Float): Float =
            if (value <= 0.04045f) {
                value / 12.92f
            } else {
                ((value + 0.055f) / 1.055f).pow(2.4f)
            }

        val r = lin(color.red)
        val g = lin(color.green)
        val b = lin(color.blue)
        val x = r * 0.4124f + g * 0.3576f + b * 0.1805f
        val y = r * 0.2126f + g * 0.7152f + b * 0.0722f
        val z = r * 0.0193f + g * 0.1192f + b * 0.9505f

        fun f(t: Float): Float = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
        val fx = f(x / 0.9505f)
        val fy = f(y)
        val fz = f(z / 1.089f)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }
}
