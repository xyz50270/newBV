package dev.frost819.newbv.app.ui.component.player.menu.component

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * [focusAnchorIndexOf] 的单元测试。
 *
 * 这个函数守护一条硬约束：列表的 `focusRestorer` 用同一个 [androidx.compose.ui.focus.FocusRequester]
 * 作为 fallback，锚点索引必须始终落在合法范围内，否则遥控器进入面板时
 * `requestFocus()` 会因 requester 未挂载任何节点而抛 IllegalStateException。
 */
class FocusAnchorIndexTest {
    @Test
    fun `valid selected index is used as anchor`() {
        assertThat(focusAnchorIndexOf(selected = 2, itemCount = 5)).isEqualTo(2)
    }

    @Test
    fun `negative selected falls back to first item`() {
        // 「撤销跳过」「重新加载」这类纯动作列表传 -1
        assertThat(focusAnchorIndexOf(selected = -1, itemCount = 3)).isEqualTo(0)
    }

    @Test
    fun `selected beyond last item falls back to first item`() {
        assertThat(focusAnchorIndexOf(selected = 9, itemCount = 3)).isEqualTo(0)
    }

    @Test
    fun `selected equal to itemCount is out of range`() {
        assertThat(focusAnchorIndexOf(selected = 3, itemCount = 3)).isEqualTo(0)
    }

    @Test
    fun `last valid index is preserved`() {
        assertThat(focusAnchorIndexOf(selected = 2, itemCount = 3)).isEqualTo(2)
    }

    @Test
    fun `empty list returns zero`() {
        assertThat(focusAnchorIndexOf(selected = 0, itemCount = 0)).isEqualTo(0)
    }
}
