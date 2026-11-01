package dev.frost819.newbv.app.ui.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.frost819.newbv.app.entity.player.displayName
import dev.frost819.newbv.app.ui.component.player.markerColor
import dev.frost819.newbv.app.ui.component.settings.MultiOptionDialog
import dev.frost819.newbv.app.ui.component.settings.SettingListItem
import dev.frost819.newbv.app.ui.component.settings.SettingSwitchListItem
import dev.frost819.newbv.app.ui.screen.settings.SettingsMenuNavItem
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory

/**
 * 广告片段跳过设置页（SponsorBlock）。
 *
 * 数据来自社区标注服务 <https://bsbsb.top>：开启后播放时自动跳过推广等片段。
 * 独立成分类（而不是并入播放设置）是因为该功能需要联网、且片段类型较多，
 * 埋在长列表底部难以发现。
 */
@Composable
fun SponsorBlockSetting(modifier: Modifier = Modifier) {
    val scrollState = rememberScrollState()

    var enabled by remember { mutableStateOf(Prefs.sponsorBlockEnabled) }
    var categories by remember { mutableStateOf(Prefs.sponsorBlockCategories.toSet()) }
    var showCategoriesDialog by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = SettingsMenuNavItem.SponsorBlock.displayName,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingSwitchListItem(
            title = "自动跳过广告片段",
            supportText = "使用 BilibiliSponsorBlock 社区标注自动跳过推广/片头等片段（需联网）",
            checked = enabled,
            onCheckedChange = {
                enabled = it
                Prefs.sponsorBlockEnabled = it
            },
        )
        SettingListItem(
            title = "自动跳过的片段类型",
            supportText = "当前：${categories.summaryText}",
            onClick = { showCategoriesDialog = true },
        )
        SettingListItem(
            title = "关于数据来源",
            supportText = "社区标注服务 bsbsb.top，与浏览器扩展 BilibiliSponsorBlock 数据同步；标注由用户众包提交，可能不准确",
            onClick = {},
        )
    }

    if (showCategoriesDialog) {
        MultiOptionDialog(
            options = SponsorCategory.entries.toList(),
            selectedOptions = categories,
            onDismiss = { showCategoriesDialog = false },
            onToggle = { selected ->
                categories = selected
                Prefs.sponsorBlockCategories = selected.sortedBy { it.ordinal }
            },
            getDisplayName = { it.displayName },
            // 色块与进度条区段颜色一致，用户可在设置里对照
            getSwatchColor = { it.markerColor },
        )
    }
}

/** 片段类型集合的展示文案，空集时明确说明不会跳过任何片段。 */
private val Set<SponsorCategory>.summaryText: String
    get() =
        sortedBy { it.ordinal }
            .joinToString("、") { it.displayName }
            .ifEmpty { "未选择（不会跳过任何片段）" }
