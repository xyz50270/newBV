package dev.frost819.newbv.app.entity.player

import dev.frost819.newbv.sponsorblock.entity.SponsorCategory

/**
 * SponsorBlock 片段分类的显示名称。
 *
 * 显示文案属于应用层（`sponsorblock` 模块保持无 UI 依赖），放在 entity 层
 * 使 ViewModel（提示文案）与设置页（多选列表）共用同一份映射。
 */
val SponsorCategory.displayName: String
    get() =
        when (this) {
            SponsorCategory.Sponsor -> "赞助推广"
            SponsorCategory.SelfPromo -> "自我推广"
            SponsorCategory.Interaction -> "互动提醒（求三连）"
            SponsorCategory.Intro -> "片头"
            SponsorCategory.Outro -> "片尾"
            SponsorCategory.Preview -> "预告/回顾"
            SponsorCategory.Filler -> "与主题无关的内容"
            SponsorCategory.MusicOfftopic -> "音乐视频中非音乐部分"
            SponsorCategory.Padding -> "填充片段（静默画面）"
            SponsorCategory.PoiHighlight -> "重点提示"
            SponsorCategory.Jumpscare -> "惊吓片段"
            SponsorCategory.ExclusiveAccess -> "会员专享内容"
        }
