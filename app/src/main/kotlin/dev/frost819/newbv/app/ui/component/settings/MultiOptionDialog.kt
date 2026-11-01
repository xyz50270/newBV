package dev.frost819.newbv.app.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/**
 * 通用多选弹窗。
 *
 * 与 [OptionDialog] 的区别是允许同时选中多项：点击即切换并立即回调，
 * 弹窗保持打开，用户自行返回关闭。适合「启用哪些类型」这类集合配置。
 *
 * @param options 可选项数组。
 * @param selectedOptions 当前已选中的集合。
 * @param onDismiss 关闭回调。
 * @param onToggle 单项切换回调，参数为切换后的完整选中集合。
 * @param getDisplayName 选项 → 显示名称转换。
 * @param getSwatchColor 选项 → 左侧色块颜色；返回 null 时不显示色块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiOptionDialog(
    modifier: Modifier = Modifier,
    options: List<T>,
    selectedOptions: Set<T>,
    onDismiss: () -> Unit,
    onToggle: (Set<T>) -> Unit,
    getDisplayName: (T) -> String,
    getSwatchColor: ((T) -> Color)? = null,
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val maxHeightDp =
        with(density) {
            (windowInfo.containerSize.height * 0.6f).toDp()
        }

    BasicAlertDialog(
        modifier = modifier.padding(vertical = 24.dp),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .wrapContentHeight()
                        .heightIn(max = maxHeightDp)
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options) { option ->
                    SettingsMenuSelectItem(
                        text = getDisplayName(option),
                        selected = option in selectedOptions,
                        swatchColor = getSwatchColor?.invoke(option),
                        onClick = {
                            val next =
                                if (option in selectedOptions) {
                                    selectedOptions - option
                                } else {
                                    selectedOptions + option
                                }
                            onToggle(next)
                        },
                    )
                }
            }
        }
    }
}
