package dev.frost819.newbv.app.ui.component.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Text
import dev.frost819.newbv.core.focus.touchClickable

/**
 * 选项弹窗中的单选项。
 *
 * 显示文本 + RadioButton，点击选中。RadioButton 不可聚焦。
 *
 * @param text 选项文本。
 * @param selected 是否选中。
 * @param onClick 点击回调。
 * @param swatchColor 标题左侧色块颜色；null 时不显示（用于颜色与选项含义对应的场景）。
 */
@Composable
fun SettingsMenuSelectItem(
    modifier: Modifier = Modifier,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    swatchColor: Color? = null,
) {
    var hasFocus by remember { mutableStateOf(false) }

    ListItem(
        modifier =
            modifier
                .onFocusChanged { hasFocus = it.hasFocus }
                .touchClickable(onClick = onClick),
        headlineContent = { Text(text = text) },
        leadingContent =
            swatchColor?.let { color ->
                {
                    Box(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .background(color = color, shape = RoundedCornerShape(3.dp)),
                    )
                }
            },
        trailingContent = {
            RadioButton(
                modifier = Modifier.focusable(false),
                selected = selected,
                onClick = { },
                colors =
                    RadioButtonDefaults.colors(
                        selectedColor =
                            if (hasFocus) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        unselectedColor =
                            if (hasFocus) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.border
                            },
                    ),
            )
        },
        onClick = onClick,
        selected = selected,
    )
}
