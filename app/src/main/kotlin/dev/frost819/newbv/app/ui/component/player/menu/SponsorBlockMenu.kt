package dev.frost819.newbv.app.ui.component.player.menu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.frost819.newbv.app.entity.player.displayName
import dev.frost819.newbv.app.ui.component.player.markerColor
import dev.frost819.newbv.app.ui.component.player.menu.component.CheckBoxMenuList
import dev.frost819.newbv.app.ui.component.player.menu.component.PlayerThreeLevelMenu
import dev.frost819.newbv.app.ui.component.player.menu.component.RadioMenuList
import dev.frost819.newbv.app.viewmodel.player.MenuFocusState
import dev.frost819.newbv.app.viewmodel.player.VideoPlayerSponsorBlockMenuItem
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory

/**
 * 广告片段跳过设置面板。
 *
 * 布局与其他设置面板一致：右侧子项列表 + 左侧值面板。
 * 子项包括：开关、片段类型、重新加载。
 *
 * 「开关」与「片段类型」直接读写 [dev.frost819.newbv.data.datastore.Prefs]，
 * 写完后由调用方触发一次重新加载，使配置立即作用于当前视频。
 *
 * @param modifier 修饰符
 * @param enabled 当前是否启用
 * @param enabledCategories 当前启用自动跳过的分类
 * @param onToggleEnabled 开关变化回调
 * @param onCategoriesChange 分类集合变化回调
 * @param onReload 重新拉取当前视频片段回调
 * @param canUndo 是否存在可撤销的上一次跳过
 * @param onUndo 撤销上一次跳过回调
 * @param onFocusStateChange 焦点状态变化回调
 */
@Composable
fun SponsorBlockMenuList(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    enabledCategories: Set<SponsorCategory>,
    onToggleEnabled: () -> Unit,
    onCategoriesChange: (Set<SponsorCategory>) -> Unit,
    onReload: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit,
) {
    PlayerThreeLevelMenu(
        modifier = modifier,
        categories = VideoPlayerSponsorBlockMenuItem.entries,
        categoryLabel = { it.displayName },
        onFocusStateChange = onFocusStateChange,
    ) { selectedItem, itemModifier, backToMenu ->
        when (selectedItem) {
            VideoPlayerSponsorBlockMenuItem.Switch ->
                RadioMenuList(
                    modifier = itemModifier,
                    items = listOf(SWITCH_ON_TEXT, SWITCH_OFF_TEXT),
                    selected = if (enabled) 0 else 1,
                    onSelectedChanged = { index ->
                        // 只在状态真的变化时回调，避免重复写 Prefs 触发无谓的重新加载
                        if ((index == 0) != enabled) onToggleEnabled()
                    },
                    onFocusBackToParent = backToMenu,
                )

            VideoPlayerSponsorBlockMenuItem.Categories ->
                CheckBoxMenuList(
                    modifier = itemModifier,
                    items = SponsorCategory.entries.map { it.displayName },
                    selected =
                        SponsorCategory.entries.mapIndexedNotNull { index, category ->
                            index.takeIf { category in enabledCategories }
                        },
                    onSelectedChanged = { indices ->
                        val selectedCategories =
                            indices.mapNotNull { SponsorCategory.entries.getOrNull(it) }.toSet()
                        onCategoriesChange(selectedCategories)
                    },
                    onFocusBackToParent = backToMenu,
                    // 色块与进度条区段颜色一致
                    swatchColors = SponsorCategory.entries.map { it.markerColor },
                )

            VideoPlayerSponsorBlockMenuItem.Undo ->
                RadioMenuList(
                    modifier = itemModifier,
                    items = listOf(if (canUndo) UNDO_TEXT else UNDO_UNAVAILABLE_TEXT),
                    selected = -1,
                    onSelectedChanged = { if (canUndo) onUndo() },
                    onFocusBackToParent = backToMenu,
                )

            VideoPlayerSponsorBlockMenuItem.Reload ->
                RadioMenuList(
                    modifier = itemModifier,
                    items = listOf(RELOAD_TEXT),
                    selected = -1,
                    onSelectedChanged = { onReload() },
                    onFocusBackToParent = backToMenu,
                )
        }
    }
}

/** 开关值面板文案。 */
private const val SWITCH_ON_TEXT = "开启"

/** 开关值面板文案。 */
private const val SWITCH_OFF_TEXT = "关闭"

/** 重新加载面板文案。 */
private const val RELOAD_TEXT = "重新加载片段数据"

/** 撤销面板文案（有可撤销记录时）。 */
private const val UNDO_TEXT = "撤销上一次跳过"

/** 撤销面板文案（无可撤销记录时）。 */
private const val UNDO_UNAVAILABLE_TEXT = "暂无可撤销的跳过"
