# newBV（xyz50270 分支）

本仓库是 [Frost819/newBV](https://github.com/Frost819/newBV) 的个人分支，只做**增量维护**：
上游已有的功能、架构与文档一律沿用，本仓库只在上游基础上叠加少量改动并自行发布 APK。

> **功能全貌、架构说明、性能数据、构建方式等原始描述，请直接阅读原仓库
> [Frost819/newBV](https://github.com/Frost819/newBV)。**
> 本文档不再重复，只描述本分支相对上游新增的内容。

[![Release](https://img.shields.io/github/v/release/xyz50270/newBV?label=release&logo=github)](https://github.com/xyz50270/newBV/releases)
[![Android Sdk Require](https://img.shields.io/badge/Android-5.0%2B-informational?logo=android)](https://apilevels.com/#:~:text=Jetpack%20Compose%20requires%20a%20minSdk%20of%2021%20or%20higher)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)

---

## 本分支新增内容

### 自动跳过广告片段（SponsorBlock）

接入 [hanydd/BilibiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) 的社区标注数据，
播放时自动跳过赞助推广、片头片尾、自我推广等 12 类片段。

- **播放进度条分类染色**：不同片段类型用不同颜色标识，一眼看出哪里会被跳过；
  极短片段也至少保留可见宽度，不会因为太短而看不见。
- **两处配置入口**：「设置 → 跳过广告」独立分类，以及播放器菜单中的「跳过广告」Tab
  （开关 / 片段类型多选 / 撤销跳过 / 重新加载）。
- **撤销上一次跳过**：误跳可一键跳回原位置。
- **自动短路**：番剧（PGC）内容与未勾选任何片段类型时不发起网络请求。
- **自定义快捷键**：`toggle_sponsor_block`，可绑定遥控器按键快速开关。

实现上把跳过判定抽成独立的纯 JVM 模块（`sponsorblock/`），不依赖 Android 与播放器，
因此可以完整单元测试；运行时走既有的 10Hz 播放进度喂入通道，不额外引入轮询任务。

### 发布链路

- **检查更新指向本仓库**：仓库地址收敛到 `AppConfiguration` 单一配置源，
  经 `BuildConfig` 注入到运行时。更换仓库只需改一处常量。
- **区分「尚未发版」与「检查失败」**：仓库存在但没有 Release 时 GitHub 返回 404，
  现在会显示「暂无发布版本」而不是误报「检查更新失败」。
- **修正发版触发规则**：上游 workflow 的 tag 过滤写成了正则（`v[0-9]+.[0-9]+.[0-9]+`），
  而 GitHub Actions 的 tag 过滤是 glob，`+` 属于字面量字符，导致 Release 工作流从未被触发。
- **发版不再被可选步骤阻断**：未配置 `WORKER_ADMIN_TOKEN` 时跳过 mapping 上传而不是让整个发版失败。

## 下载

APK 见 [Releases](https://github.com/xyz50270/newBV/releases)。

> 本分支使用自行生成的签名密钥，与上游 APK 签名不同。若设备上装有上游版本，
> 需要先卸载再安装本分支的包。

## 与上游的关系

本分支以 [Frost819/newBV](https://github.com/Frost819/newBV) 的 `main` 为基线，
上游更新可正常合并进来。上游代码本身源自
[aaa1115910/bv](https://github.com/aaa1115910/bv)，许可见 [LICENSE](LICENSE)。

## 致谢

- [Frost819/newBV](https://github.com/Frost819/newBV) — 本仓库的直接上游，本项目全部基础工作来自这里
- [aaa1115910/bv](https://github.com/aaa1115910/bv) — newBV 的重构原型，更上游的原始项目
- [hanydd/BilibiliSponsorBlock](https://github.com/hanydd/BilibiliSponsorBlock) — 广告片段标注数据与跳过规则
- [cat3399/blbl](https://github.com/cat3399/blbl) — 部分实现（弹幕分段加载等）参考 / 移植自该项目
- [akdanmaku](https://github.com/Frost819/AkDanmaku) — 弹幕渲染引擎

## License

[MIT](LICENSE) © aaa1115910, Frost819
