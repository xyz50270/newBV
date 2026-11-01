package dev.frost819.newbv.sponsorblock.entity

/**
 * SponsorBlock 片段分类。
 *
 * [apiName] 为服务端返回与请求参数中使用的分类标识（与上游 BilibiliSponsorBlock 一致），
 * 显示名称由 app 层提供，避免本模块依赖 Android 资源。
 *
 * 参考：https://github.com/hanydd/BilibiliSponsorBlock/wiki/API
 */
enum class SponsorCategory(
    val apiName: String,
) {
    /** 赞助推广（恰饭广告）。 */
    Sponsor("sponsor"),

    /** 自我推广（UP 主推广自己的其他内容）。 */
    SelfPromo("selfpromo"),

    /** 互动提醒（求点赞/投币/关注）。 */
    Interaction("interaction"),

    /** 片头。 */
    Intro("intro"),

    /** 片尾。 */
    Outro("outro"),

    /** 预览/回顾。 */
    Preview("preview"),

    /** 与主题无关的内容。 */
    Filler("filler"),

    /** 音乐视频中非音乐部分。 */
    MusicOfftopic("music_offtopic"),

    /** 填充（静默画面等）。 */
    Padding("padding"),

    /** 重点提示。 */
    PoiHighlight("poi_highlight"),

    /** 惊吓片段。 */
    Jumpscare("jumpscare"),

    /** 会员专享内容提示。 */
    ExclusiveAccess("exclusive_access"),
    ;

    companion object {
        /**
         * 按服务端分类标识解析。
         *
         * @param apiName 服务端返回的分类字符串
         * @return 匹配的分类；未知分类返回 null（调用方应忽略该片段）
         */
        fun fromApiName(apiName: String): SponsorCategory? = entries.firstOrNull { it.apiName == apiName }
    }
}
