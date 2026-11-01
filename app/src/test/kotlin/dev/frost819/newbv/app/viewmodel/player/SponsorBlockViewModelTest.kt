package dev.frost819.newbv.app.viewmodel.player

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.app.data.SponsorBlockRepository
import dev.frost819.newbv.data.datastore.Prefs
import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * [SponsorBlockViewModel] 的单元测试。
 *
 * 验证片段加载（开关/参数校验/去重缓存）、位置命中判定、跳过事件、
 * 提示自动消失、切集重置。Repository 全量 mock，不访问真实网络。
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SponsorBlockViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SponsorBlockRepository
    private lateinit var viewModel: SponsorBlockViewModel

    private val sponsorSegment =
        SponsorSegment(
            uuid = "uuid-sponsor",
            startMs = 10_000,
            endMs = 25_000,
            category = SponsorCategory.Sponsor,
            cid = 100L,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Loggers 内部用 runCatching 包裹 Log.println，JVM 环境下未 mock 也不会影响断言
        mockkObjectPrefs()

        repository = mockk(relaxed = true)
        viewModel = SponsorBlockViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun mockkObjectPrefs() {
        mockkObject(Prefs)
        every { Prefs.sponsorBlockEnabled } returns true
        every { Prefs.sponsorBlockCategories } returns listOf(SponsorCategory.Sponsor)
    }

    // ── 加载 ────────────────────────────────────────────────

    @Test
    fun `loadFor fetches segments for current video`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            assertThat(viewModel.state.value.segments).containsExactly(sponsorSegment)
            assertThat(viewModel.state.value.isLoading).isFalse()
            assertThat(viewModel.state.value.enabled).isTrue()
            coVerify {
                repository.getSkipSegments(
                    bvid = "BV1Rx4y1N7jQ",
                    cid = 100L,
                    categories = setOf(SponsorCategory.Sponsor),
                )
            }
        }

    @Test
    fun `loadFor does nothing when disabled`() =
        runTest(testDispatcher) {
            every { Prefs.sponsorBlockEnabled } returns false

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            assertThat(viewModel.state.value.enabled).isFalse()
            assertThat(viewModel.state.value.segments).isEmpty()
            coVerify(exactly = 0) { repository.getSkipSegments(any(), any(), any(), any()) }
        }

    @Test
    fun `loadFor skips request when cid is zero`() =
        runTest(testDispatcher) {
            viewModel.loadFor(aid = 993403941L, cid = 0L)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.getSkipSegments(any(), any(), any(), any()) }
        }

    @Test
    fun `loadFor deduplicates identical target`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns emptyList()

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.getSkipSegments(any(), any(), any(), any()) }
        }

    @Test
    fun `loadFor reloads when category set changes`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns emptyList()

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            every { Prefs.sponsorBlockCategories } returns
                listOf(SponsorCategory.Sponsor, SponsorCategory.Intro)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            coVerify(exactly = 2) { repository.getSkipSegments(any(), any(), any(), any()) }
        }

    @Test
    fun `loadFor swallows repository failure`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } throws
                IllegalStateException("network down")

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            assertThat(viewModel.state.value.segments).isEmpty()
            assertThat(viewModel.state.value.isLoading).isFalse()
        }

    // ── 命中与事件 ──────────────────────────────────────────

    @Test
    fun `onVideoPositionChanged emits skip event when position inside segment`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            viewModel.skipEvent.test {
                viewModel.onVideoPositionChanged(15_000L)

                val event = awaitItem()
                assertThat(event.segment).isEqualTo(sponsorSegment)
            }
        }

    @Test
    fun `onVideoPositionChanged ignores position outside segment`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            viewModel.onVideoPositionChanged(5_000L)

            assertThat(viewModel.state.value.lastSkipped).isNull()
        }

    @Test
    fun `onVideoPositionChanged does not emit twice for same segment`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            viewModel.skipEvent.test {
                viewModel.onVideoPositionChanged(15_000L)
                assertThat(awaitItem().segment).isEqualTo(sponsorSegment)

                viewModel.onVideoPositionChanged(15_500L)
                // 不应对同一片段重复发射
                expectNoEvents()
            }
        }

    @Test
    fun `onVideoPositionChanged ignores disabled category`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            every { Prefs.sponsorBlockCategories } returns listOf(SponsorCategory.Intro)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            viewModel.onVideoPositionChanged(15_000L)

            assertThat(viewModel.state.value.lastSkipped).isNull()
        }

    @Test
    fun `onVideoPositionChanged sets tip text`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            viewModel.onVideoPositionChanged(15_000L)

            assertThat(viewModel.state.value.tipText).isEqualTo("赞助推广，已自动跳过 15 秒")
        }

    @Test
    fun `tip text clears after timeout`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            viewModel.onVideoPositionChanged(15_000L)
            advanceTimeBy(3_100L)
            runCurrent()

            assertThat(viewModel.state.value.tipText).isNull()
        }

    // ── 撤销 ────────────────────────────────────────────────

    @Test
    fun `undoLastSkip emits undo event to segment start`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.onVideoPositionChanged(15_000L)

            viewModel.undoEvent.test {
                assertThat(viewModel.undoLastSkip()).isTrue()

                val event = awaitItem()
                assertThat(event.aid).isEqualTo(993403941L)
                assertThat(event.cid).isEqualTo(100L)
                assertThat(event.positionMs).isEqualTo(sponsorSegment.startMs)
            }
        }

    @Test
    fun `undoLastSkip returns false when nothing was skipped`() =
        runTest(testDispatcher) {
            assertThat(viewModel.undoLastSkip()).isFalse()
        }

    @Test
    fun `undoLastSkip clears lastSkipped so it can only undo once`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.onVideoPositionChanged(15_000L)

            viewModel.undoLastSkip()

            assertThat(viewModel.state.value.lastSkipped).isNull()
            assertThat(viewModel.undoLastSkip()).isFalse()
        }

    @Test
    fun `undo does not retrigger skip while playback stays inside segment`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.onVideoPositionChanged(15_000L)
            viewModel.undoLastSkip()

            // 回跳到片段起点后继续播放，不应再次自动跳过同一片段
            viewModel.onVideoPositionChanged(sponsorSegment.startMs)
            viewModel.onVideoPositionChanged(16_000L)

            assertThat(viewModel.state.value.lastSkipped).isNull()
        }

    @Test
    fun `reload refetches even when target unchanged`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.onVideoPositionChanged(15_000L)

            viewModel.reload()
            advanceUntilIdle()

            coVerify(exactly = 2) { repository.getSkipSegments(any(), any(), any(), any()) }
            // 重新加载后跳过记录被清空，同一片段可再次触发
            assertThat(viewModel.state.value.lastSkipped).isNull()
        }

    @Test
    fun `reload bypasses repository cache`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            assertThat(viewModel.state.value.segments).hasSize(1)

            // 社区数据更新后，普通刷新可能命中缓存；「重新加载」必须真正重发请求
            viewModel.reload()
            advanceUntilIdle()

            coVerify(exactly = 1) {
                repository.getSkipSegments(any(), any(), any(), forceRefresh = true)
            }
        }

    @Test
    fun `loadFor reports load failure`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } throws
                java.io.IOException("network down")

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            // 区分「请求失败」与「确实没有标注」，否则界面上两者表现一致
            assertThat(viewModel.state.value.loadFailed).isTrue()
            assertThat(viewModel.state.value.isLoading).isFalse()
            assertThat(viewModel.state.value.segments).isEmpty()
        }

    @Test
    fun `loadFor clears load failure on retry`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } throws
                java.io.IOException("network down")
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            assertThat(viewModel.state.value.loadFailed).isTrue()

            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.reload()
            advanceUntilIdle()

            assertThat(viewModel.state.value.loadFailed).isFalse()
            assertThat(viewModel.state.value.segments).hasSize(1)
        }

    // ── 重置 ────────────────────────────────────────────────

    @Test
    fun `reset clears segments and skip history`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)
            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.onVideoPositionChanged(15_000L)

            viewModel.reset()

            assertThat(viewModel.state.value.segments).isEmpty()
            assertThat(viewModel.state.value.lastSkipped).isNull()
        }

    @Test
    fun `loadFor new cid reloads segments`() =
        runTest(testDispatcher) {
            coEvery { repository.getSkipSegments(any(), any(), any(), any()) } returns listOf(sponsorSegment)

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()
            viewModel.loadFor(aid = 993403941L, cid = 200L)
            advanceUntilIdle()

            coVerify(exactly = 2) { repository.getSkipSegments(any(), any(), any(), any()) }
        }

    @Test
    fun `loadFor skips request for pgc content`() =
        runTest(testDispatcher) {
            viewModel.loadFor(aid = 993403941L, cid = 100L, isPgc = true)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.getSkipSegments(any(), any(), any(), any()) }
            assertThat(viewModel.state.value.segments).isEmpty()
        }

    @Test
    fun `loadFor skips request when no category selected`() =
        runTest(testDispatcher) {
            every { Prefs.sponsorBlockCategories } returns emptyList()

            viewModel.loadFor(aid = 993403941L, cid = 100L)
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.getSkipSegments(any(), any(), any(), any()) }
            assertThat(viewModel.state.value.categories).isEmpty()
        }

    @Test
    fun `refresh without prior load only syncs prefs`() {
        // 未加载过视频时 refresh 只同步配置，不发起请求
        viewModel.refresh()

        assertThat(viewModel.state.value.enabled).isTrue()
        assertThat(viewModel.state.value.categories).containsExactly(SponsorCategory.Sponsor)
    }
}
