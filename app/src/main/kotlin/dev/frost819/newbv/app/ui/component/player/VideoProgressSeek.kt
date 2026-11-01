package dev.frost819.newbv.app.ui.component.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.frost819.newbv.core.theme.BVTheme

/**
 * 视频进度条。
 *
 * 使用 Canvas 绘制三层进度线：背景轨道、缓冲进度、播放进度。
 * 三段轨道用「色相 + 明度」双重区分，避免混在一起：
 * 已播放用品牌色（primary），已缓冲用较亮的中性灰，未缓冲用很暗的中性灰。
 * 播放器固定运行在深色主题下，均能保证黑底上的可见性。
 *
 * 若传入 [markers]（SponsorBlock 片段区段），会把轨道对应区间染成该片段的分类色，
 * 用不同颜色标识各类可跳过片段。绘制顺序为「背景 → 缓冲 → 区段 → 播放进度」：
 * 区段必须盖在缓冲之上，否则半透明缓冲色会把分类色冲淡；已播放段仍由品牌色覆盖，
 * 视觉上不干扰当前进度。
 * 支持两种显示模式：
 * - **常显模式**（[isPersistentSeek] = true）：2dp 细线，不显示缓冲进度，
 *   用于播放器底部始终可见的进度条。
 * - **交互模式**（[isPersistentSeek] = false）：8dp 粗线，显示缓冲进度，
 *   用于控制器信息栏中的可交互 seek bar。
 *
 * @param modifier 修饰符
 * @param duration 视频总时长（毫秒）
 * @param position 当前播放位置（毫秒）
 * @param bufferedPercentage 缓冲百分比（0-100）
 * @param isPersistentSeek 是否为常显模式
 * @param markers 可跳过片段的区段标识，空列表时不绘制
 */
@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    isPersistentSeek: Boolean,
    markers: List<SeekMarker> = emptyList(),
) {
    val trackWidthDp = if (isPersistentSeek) 2.dp else 8.dp
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val bufferedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(trackWidthDp)
                .clip(RoundedCornerShape(50)),
    ) {
        val trackWidthPx = trackWidthDp.toPx()

        // 背景轨道
        drawLine(
            color = inactiveTrackColor,
            start = Offset(0f, center.y),
            end = Offset(size.width, center.y),
            strokeWidth = trackWidthPx,
            cap = StrokeCap.Round,
        )

        // 缓冲进度（仅交互模式显示）
        if (!isPersistentSeek && bufferedPercentage > 0) {
            drawLine(
                color = bufferedTrackColor,
                start = Offset(trackWidthPx / 2, center.y),
                end = Offset(size.width * bufferedPercentage / 100, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round,
            )
        }

        // 可跳过片段区段（盖在缓冲之上，否则半透明缓冲色会把分类色冲淡）
        if (markers.isNotEmpty()) {
            val durationMs = duration
            if (durationMs > 0L) {
                markers.forEach { marker ->
                    val startRatio = (marker.startMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val endRatio = (marker.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    val startX = size.width * startRatio
                    val endX = size.width * endRatio
                    // 极短片段至少保留 2dp 可见宽度，否则用户完全看不到该区段存在
                    val minWidthPx = 2.dp.toPx()
                    val drawnEndX =
                        if (endX - startX < minWidthPx) {
                            (startX + minWidthPx).coerceAtMost(size.width)
                        } else {
                            endX
                        }
                    drawLine(
                        color = marker.color,
                        start = Offset(startX, center.y),
                        end = Offset(drawnEndX, center.y),
                        strokeWidth = trackWidthPx,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        // 播放进度
        if (duration > 0) {
            val progressRatio = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            drawLine(
                color = activeTrackColor,
                start = Offset(trackWidthPx / 2, center.y),
                end = Offset(size.width * progressRatio, center.y),
                strokeWidth = trackWidthPx,
                cap = StrokeCap.Round,
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun VideoProgressSeekPersistentPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 100_000L,
            position = 35_000L,
            bufferedPercentage = 60,
            isPersistentSeek = true,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun VideoProgressSeekNormalPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 100_000L,
            position = 35_000L,
            bufferedPercentage = 60,
            isPersistentSeek = false,
            markers =
                listOf(
                    SeekMarker(10_000L, 20_000L, Color(0xFF00D400)),
                    SeekMarker(40_000L, 52_000L, Color(0xFFFFFF00)),
                    SeekMarker(70_000L, 72_000L, Color(0xFFCC00FF)),
                ),
        )
    }
}

// endregion
