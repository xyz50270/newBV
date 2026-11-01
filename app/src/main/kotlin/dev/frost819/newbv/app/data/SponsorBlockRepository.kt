package dev.frost819.newbv.app.data

import dev.frost819.newbv.sponsorblock.entity.SponsorCategory
import dev.frost819.newbv.sponsorblock.entity.SponsorSegment
import dev.frost819.newbv.sponsorblock.http.SponsorBlockApi
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SponsorBlock 片段仓库。
 *
 * 在 [SponsorBlockApi] 之上增加进程内缓存：同一分 P 在一次应用生命周期内只请求一次，
 * 避免「切走再切回」「播放器重建」时重复请求社区服务。
 *
 * 缓存键为 `bvid#cid#分类集合`：分类集合参与键，是因为服务端按分类过滤返回，
 * 用户改动启用分类后必须重新请求才能拿到新增分类的片段。
 *
 * @param client 共享 OkHttpClient（连接池复用，不要在调用处新建）
 */
@Singleton
class SponsorBlockRepository
    @Inject
    constructor(
        client: OkHttpClient,
    ) {
        private val api = SponsorBlockApi(client)

        private val cache =
            object : LinkedHashMap<String, List<SponsorSegment>>(CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(
                    eldest: MutableMap.MutableEntry<String, List<SponsorSegment>>,
                ): Boolean = size > CACHE_SIZE
            }

        /**
         * 查询指定分 P 的可跳过片段。
         *
         * @param bvid 视频 BV 号
         * @param cid 分 P CID
         * @param categories 需要查询的分类
         * @return 片段列表；网络失败或无数据时为空列表（不抛出异常，避免影响播放）
         */
        suspend fun getSkipSegments(
            bvid: String,
            cid: Long,
            categories: Collection<SponsorCategory>,
        ): List<SponsorSegment> {
            if (bvid.isBlank()) return emptyList()
            val key = "$bvid#$cid#$categories"
            synchronized(cache) { cache[key] }?.let { return it }

            val segments = api.getSkipSegments(bvid = bvid, cid = cid, categories = categories)
            // 空结果也缓存：社区服务对无标注视频返回 404 很常见，重复请求同样是浪费
            synchronized(cache) { cache[key] = segments }
            return segments
        }

        private companion object {
            /** 缓存条目上限（按视频数量，1 条仅占几十字节）。 */
            const val CACHE_SIZE = 64
        }
    }
