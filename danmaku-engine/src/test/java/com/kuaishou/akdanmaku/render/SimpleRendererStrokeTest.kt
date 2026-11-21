package com.kuaishou.akdanmaku.render

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [SimpleRenderer] 描边宽度计算的测试。
 *
 * 背景：描边原本固定 3px，但字号会乘以设备密度与用户缩放（0.5~4 倍），
 * 电视上大字号时描边只占字号的 3%，白字叠在浅色画面上几乎看不见。
 * 现改为按字号比例计算，这里守住比例区间与上下限。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimpleRendererStrokeTest {
    @Test
    fun `stroke width scales with text size`() {
        // 40px 字号下按 9% 计算为 3.6px
        assertThat(SimpleRenderer.strokeWidthFor(40f)).isWithin(0.01f).of(3.6f)
        // 80px 字号下应翻倍，而不是停在固定值
        assertThat(SimpleRenderer.strokeWidthFor(80f)).isWithin(0.01f).of(7.2f)
    }

    @Test
    fun `large text gets visibly thicker stroke than before`() {
        // 电视上弹幕字号可达 100px 以上，固定 3px 时描边仅占 3%，几乎不可见
        val stroke = SimpleRenderer.strokeWidthFor(100f)

        assertThat(stroke).isGreaterThan(3f)
        // 描边宽度应达到字号的 5% 以上才能在浅色画面上形成清晰轮廓
        assertThat(stroke / 100f).isAtLeast(0.05f)
    }

    @Test
    fun `stroke width has a lower bound for tiny text`() {
        // 小字号下 9% 会算出不足 2px，低于下限时取最小值保证仍有轮廓
        assertThat(SimpleRenderer.strokeWidthFor(10f)).isAtLeast(2.5f)
    }

    @Test
    fun `stroke width has an upper bound for huge text`() {
        // 极高字号下不限制会让描边吃掉字形内部笔画，必须封顶
        assertThat(SimpleRenderer.strokeWidthFor(10_000f)).isAtMost(14f)
    }

    @Test
    fun `stroke width is never negative or zero`() {
        assertThat(SimpleRenderer.strokeWidthFor(0f)).isGreaterThan(0f)
    }
}
