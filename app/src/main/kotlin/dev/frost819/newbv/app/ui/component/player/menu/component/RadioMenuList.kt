package dev.frost819.newbv.app.ui.component.player.menu.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import dev.frost819.newbv.app.ui.component.player.ifElse

/**
 * 单选菜单列表。
 *
 * 使用 [LazyColumn] 渲染可选项，按 DirectionRight 返回父级。
 * 焦点恢复到当前选中项。
 *
 * @param modifier 修饰符
 * @param items 选项文本列表
 * @param selected 当前选中项索引
 * @param onSelectedChanged 选中变化回调
 * @param onFocusBackToParent 返回父级回调
 */
@Composable
fun RadioMenuList(
    modifier: Modifier = Modifier,
    items: List<String>,
    selected: Int = 0,
    onSelectedChanged: (index: Int) -> Unit,
    onFocusBackToParent: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusAnchorIndex = focusAnchorIndexOf(selected, items.size)
    LazyColumn(
        modifier =
            modifier
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                            return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    }
                    val result = it.key == Key.DirectionRight
                    if (result) onFocusBackToParent()
                    result
                }.focusRestorer(focusRequester),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp),
    ) {
        itemsIndexed(items) { index, item ->
            MenuListItem(
                modifier =
                    Modifier
                        .width(200.dp)
                        .ifElse(index == focusAnchorIndex, Modifier.focusRequester(focusRequester)),
                text = item,
                selected = selected == index,
                onClick = { onSelectedChanged(index) },
            )
        }
    }
}

/**
 * 计算焦点锚点（挂载 [FocusRequester] 的项）应落在哪个索引。
 *
 * 锚点必须总是合法索引：列表的 `focusRestorer` 以同一个 [FocusRequester] 作为
 * fallback，焦点首次进入列表时会调用它 `requestFocus()`；若没有任何项挂载该
 * requester，Compose 会抛 `IllegalStateException("FocusRequester is not initialized")`。
 * 「撤销跳过」「重新加载」这类纯动作列表没有选中的项（传 -1），必须回退到首项，
 * 否则遥控器一进入该面板就崩溃。
 *
 * @param selected 当前选中项索引，可能越界
 * @param itemCount 列表项数量
 * @return 锚点索引；空列表返回 0（不会渲染任何项，调用方需自行保证非空）
 */
internal fun focusAnchorIndexOf(
    selected: Int,
    itemCount: Int,
): Int = if (selected in 0 until itemCount) selected else 0
