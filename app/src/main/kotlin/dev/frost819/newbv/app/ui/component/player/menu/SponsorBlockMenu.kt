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
 * 子项包括：开关、片段类型、撤销、重新加载。
 *
 * 「开关」与「片段类型」直接读写 [dev.frost819.newbv.data.datastore.Prefs]，
 * 写完后由调用方触发一次重新加载，使配置立即作用于当前视频。
 *
 * @param modifier 修饰符
 * @param enabled 当前是否启用
 * @param enabledCategories 当前启用自动跳过的分类
 * @param segmentCount 当前分 P 已加载的可跳过片段数量
 * @param isLoading 片段是否正在加载
 * @param loadFailed 最近一次加载是否失败
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
    segmentCount: Int,
    isLoading: Boolean,
    loadFailed: Boolean,
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
                    items = listOf(reloadLabel(segmentCount, isLoading, loadFailed)),
                    selected = -1,
                    onSelectedChanged = { onReload() },
                    onFocusBackToParent = backToMenu,
                )
        }
    }
}

/**
 * 构造「重新加载」项文案，附带当前加载结果。
 *
 * 把状态放进动作项本身，是为了区分「请求失败」与「服务端确实没有该视频的标注」——
 * 此前两者在界面上表现完全一致，用户无法判断问题出在获取还是解析环节。
 * 不单独加一行说明文本：值面板是 LazyColumn，额外嵌套会打乱其纵向居中与焦点处理。
 *
 * @param segmentCount 已加载的可跳过片段数量
 * @param isLoading 是否正在加载
 * @param loadFailed 最近一次加载是否失败
 */
internal fun reloadLabel(
    segmentCount: Int,
    isLoading: Boolean,
    loadFailed: Boolean,
): String =
    when {
        isLoading -> "${RELOAD_TEXT}（加载中…）"
        loadFailed -> "${RELOAD_TEXT}（失败）"
        segmentCount > 0 -> "${RELOAD_TEXT}（$segmentCount 个片段）"
        else -> "${RELOAD_TEXT}（无标注）"
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
