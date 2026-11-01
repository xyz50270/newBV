package dev.frost819.newbv.sponsorblock.entity

/**
 * 一个可跳过的社区标注片段。
 *
 * 时间范围左闭右开：`startMs <= position < endMs`。
 *
 * @property uuid 片段唯一标识（服务端 UUID，用于本次播放内去重）
 * @property startMs 片段开始时间（毫秒）
 * @property endMs 片段结束时间（毫秒）
 * @property category 片段分类
 * @property cid 片段所属分 P 的 CID；null 表示服务端未标注（视为所有分 P 通用）
 * @property votes 社区投票数（仅用于展示/调试）
 */
data class SponsorSegment(
    val uuid: String,
    val startMs: Long,
    val endMs: Long,
    val category: SponsorCategory,
    val cid: Long? = null,
    val votes: Int = 0,
) {
    /** 片段时长（毫秒）。 */
    val durationMs: Long get() = endMs - startMs

    /**
     * 判断给定播放位置是否落在片段内。
     *
     * @param positionMs 播放位置（毫秒）
     */
    fun contains(positionMs: Long): Boolean = positionMs >= startMs && positionMs < endMs
}
