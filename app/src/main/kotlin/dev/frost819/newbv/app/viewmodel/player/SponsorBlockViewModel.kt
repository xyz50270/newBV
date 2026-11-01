package dev.frost819.newbv.app.viewmodel.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.frost819.newbv.app.data.SponsorBlockRepository
import dev.frost819.newbv.app.entity.player.displayName
import dev.frost819.newbv.biliapi.util.toBv
import dev.frost819.newbv.core.log.Loggers
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.sponsorblock.SponsorSkipTracker
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SponsorBlock 自动跳过 ViewModel。
 *
 * 职责：
 * - 按「视频 + 分 P + 启用分类」拉取社区标注的片段（[SponsorBlockRepository] 带进程内缓存）
 * - 接收 UI 层喂入的播放位置，命中片段时发出一次性 [skipEvent]，由 UI 执行 seek
 * - 维护「同一片段只自动跳过一次」的记录（[SponsorSkipTracker]）
 *
 * 与 [PlayerViewModel] 的同步由 UI 层协调（与弹幕相同模式）：
 * - 播放进度 → [onVideoPositionChanged]
 * - 切换视频 / cid 变化 → [loadFor]
 * - 跳过事件 → `PlayerViewModel.seekToTime`
 * - 从设置页返回 → [refresh]（配置变了才重新拉取）
 *
 * 设计约束：
 * - 不持有播放器引用，纯状态 + 事件，可在 JVM 单测中完整验证
 * - 不在 ViewModel 内长驻监听 Prefs，配置在 [loadFor] / [refresh] 时读取
 */
@HiltViewModel
class SponsorBlockViewModel
    @Inject
    constructor(
        private val repository: SponsorBlockRepository,
    ) : ViewModel() {
        private val logger = Loggers.get("SponsorBlockViewModel")

        private val tracker = SponsorSkipTracker()

        private val _state = MutableStateFlow(SponsorBlockState())
        val state = _state.asStateFlow()

        /** 命中待跳过片段的一次性事件，UI 收到后执行 seek。 */
        private val _skipEvent = MutableSharedFlow<SponsorSkipEvent>(extraBufferCapacity = 1)
        val skipEvent = _skipEvent.asSharedFlow()

        /** 撤销跳过的一次性事件，UI 收到后跳回片段起点。 */
        private val _undoEvent = MutableSharedFlow<SponsorUndoEvent>(extraBufferCapacity = 1)
        val undoEvent = _undoEvent.asSharedFlow()

        /** 当前加载目标，供 [refresh] 复用。 */
        private var currentAid: Long = 0L
        private var currentCid: Long = 0L
        private var currentIsPgc: Boolean = false
        private var loadJob: Job? = null
        private var tipJob: Job? = null

        /** 已应用过的「aid + cid + 开关 + 分类」组合，避免重复请求。 */
        private var appliedKey: String? = null

        /**
         * 为指定视频加载片段列表。
         *
         * 参数与「已应用组合」一致时为空操作（含重复回调、返回页面时的重组）。
         * 关闭 SponsorBlock、未选择任何分类、番剧或视频信息不完整时只清空状态，不发起请求。
         *
         * @param aid 视频 AV 号
         * @param cid 分 P CID
         * @param isPgc 是否为番剧内容；番剧分集没有可用的 BV 号，跳过查询
         */
        fun loadFor(
            aid: Long,
            cid: Long,
            isPgc: Boolean = false,
        ) {
            val enabled = Prefs.sponsorBlockEnabled
            val categories = Prefs.sponsorBlockCategories.toSet()
            val key = "$aid#$cid#$isPgc#$enabled#$categories"
            if (key == appliedKey) return

            loadJob?.cancel()
            appliedKey = key
            currentAid = aid
            currentCid = cid
            currentIsPgc = isPgc
            tracker.reset()
            clearTip()

            // 没有启用任何分类时查询无意义（服务端会退回全部分类）；番剧分集没有可用的 BV 号
            val queryable = enabled && !isPgc && categories.isNotEmpty() && aid > 0L && cid > 0L
            if (!queryable) {
                _state.update {
                    it.copy(
                        enabled = enabled,
                        categories = categories,
                        segments = emptyList(),
                        isLoading = false,
                        // 片段列表已清空，旧的撤销目标不再对应任何数据
                        lastSkipped = null,
                    )
                }
                return
            }

            _state.update {
                it.copy(
                    enabled = true,
                    categories = categories,
                    segments = emptyList(),
                    isLoading = true,
                    lastSkipped = null,
                )
            }

            val bvid = aid.toBv()
            loadJob =
                viewModelScope.launch {
                    val segments =
                        runCatching {
                            repository.getSkipSegments(bvid = bvid, cid = cid, categories = categories)
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            logger.warn { "Load sponsor segments failed: aid=$aid, cid=$cid, $error" }
                        }.getOrDefault(emptyList())

                    tracker.setSegments(segments)
                    _state.update { it.copy(segments = segments, isLoading = false) }
                    if (segments.isNotEmpty()) {
                        logger.info { "Loaded ${segments.size} sponsor segments: aid=$aid, cid=$cid" }
                    }
                }
        }

        /**
         * 强制重新拉取当前视频的片段（播放器菜单「重新加载」入口）。
         *
         * 与 [refresh] 的区别：无视「已应用组合」直接重发请求，用于社区数据更新后
         * 手动刷新；会同时清空跳过记录，使本视频的片段重新获得自动跳过机会。
         */
        fun reload() {
            val aid = currentAid
            val cid = currentCid
            if (aid <= 0L || cid <= 0L) {
                refresh()
                return
            }
            appliedKey = null
            loadFor(aid, cid, isPgc = currentIsPgc)
        }

        /**
         * 按最新配置刷新当前视频的片段。
         *
         * 供「从设置页返回播放器」时调用：配置未变化则什么都不做（不重发请求、不清跳过记录），
         * 变化了才按新开关/分类重新拉取。
         */
        fun refresh() {
            if (currentAid <= 0L || currentCid <= 0L) {
                _state.update {
                    it.copy(enabled = Prefs.sponsorBlockEnabled, categories = Prefs.sponsorBlockCategories.toSet())
                }
                return
            }
            // appliedKey 已包含开关与分类，配置未变时 loadFor 会直接返回
            loadFor(currentAid, currentCid, isPgc = currentIsPgc)
        }

        /**
         * 喂入播放位置，命中片段时发出跳过事件并显示提示。
         *
         * 由 UI 层以进度条频率（约 10Hz）调用；未启用或无片段时立即返回，开销可忽略。
         *
         * @param positionMs 当前播放位置（毫秒）
         */
        fun onVideoPositionChanged(positionMs: Long) {
            val snapshot = _state.value
            if (!snapshot.enabled || snapshot.segments.isEmpty()) return

            val target = tracker.findSkipTarget(positionMs, snapshot.categories) ?: return
            val event = SponsorSkipEvent(aid = currentAid, cid = currentCid, segment = target)
            // tryEmit 失败说明缓冲区也满了（UI 当前未收集），撤销跳过记录，
            // 否则该片段会一直被当作「已跳过」而永远不再触发
            if (!_skipEvent.tryEmit(event)) {
                tracker.forget(target.uuid)
                return
            }
            _state.update { it.copy(lastSkipped = target) }
            showTip(target)
        }

        /**
         * 撤销上一次自动跳过：跳回该片段的起点。
         *
         * 该片段的跳过记录保留，因此回跳后再次播到片段内部不会立即被重新跳过——
         * 用户可以完整看完这一段。找不到可撤销记录时返回 false 供 UI 提示。
         *
         * @return 是否成功发出撤销事件
         */
        fun undoLastSkip(): Boolean {
            val target = _state.value.lastSkipped ?: return false
            val event = SponsorUndoEvent(aid = currentAid, cid = currentCid, positionMs = target.startMs)
            if (!_undoEvent.tryEmit(event)) return false
            _state.update { it.copy(lastSkipped = null) }
            showTipText("${target.category.displayName}，已撤销跳过")
            return true
        }

        /** 切换视频时清空跳过记录与提示。 */
        fun reset() {
            loadJob?.cancel()
            appliedKey = null
            tracker.reset()
            clearTip()
            _state.update { it.copy(segments = emptyList(), isLoading = false, lastSkipped = null) }
        }

        private fun showTip(segment: SponsorSegment) {
            showTipText("${segment.category.displayName}，已自动跳过 ${segment.durationMs / 1000} 秒")
        }

        /** 显示一条提示，并在 [TIP_DURATION_MS] 后自动清除。 */
        private fun showTipText(text: String) {
            tipJob?.cancel()
            _state.update { it.copy(tipText = text) }
            tipJob =
                viewModelScope.launch {
                    delay(TIP_DURATION_MS)
                    _state.update { it.copy(tipText = null) }
                }
        }

        private fun clearTip() {
            tipJob?.cancel()
            tipJob = null
            _state.update { it.copy(tipText = null) }
        }

        private companion object {
            /** 跳过提示显示时长（毫秒）。 */
            const val TIP_DURATION_MS = 3_000L
        }
    }

/**
 * SponsorBlock 自动跳过状态。
 *
 * @property enabled 当前是否启用（为 false 时不发起请求、不跳过）
 * @property categories 启用自动跳过的分类
 * @property segments 当前分 P 的可跳过片段（按开始时间升序）
 * @property isLoading 是否正在拉取片段
 * @property lastSkipped 最近一次自动跳过的片段
 * @property tipText 跳过提示文本；null 表示不显示
 */
data class SponsorBlockState(
    val enabled: Boolean = false,
    val categories: Set<SponsorCategory> = emptySet(),
    val segments: List<SponsorSegment> = emptyList(),
    val isLoading: Boolean = false,
    val lastSkipped: SponsorSegment? = null,
    val tipText: String? = null,
)

/**
 * 撤销自动跳过事件。
 *
 * @property aid 片段所属视频 AV 号
 * @property cid 片段所属分 P CID
 * @property positionMs 撤销后应跳转到的位置（片段的起点，毫秒）
 */
data class SponsorUndoEvent(
    val aid: Long,
    val cid: Long,
    val positionMs: Long,
)

/**
 * 自动跳过事件。
 *
 * 事件携带 aid/cid：SharedFlow 在播放页被覆盖（如打开详情页）期间会缓冲事件，
 * UI 收到后需校验归属，避免对已切换的视频执行过期跳转。
 *
 * @property aid 片段所属视频 AV 号
 * @property cid 片段所属分 P CID
 * @property segment 即将被跳过的片段，`endMs` 为跳转目标
 */
data class SponsorSkipEvent(
    val aid: Long,
    val cid: Long,
    val segment: SponsorSegment,
)
