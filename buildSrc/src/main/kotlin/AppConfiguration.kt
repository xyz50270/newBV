/**
 * 应用全局配置。
 *
 * 集中管理包名、SDK 版本、版本号等构建配置，
 * 供各模块 build.gradle.kts 引用。
 */
object AppConfiguration {
    const val appId = "dev.frost819.newbv"
    const val applicationId = "dev.frost819.newbv"
    const val compileSdk = 36
    const val minSdk = 21
    const val targetSdk = 36

    /**
     * 检查更新与发布 Release 所使用的 GitHub 仓库。
     *
     * 这里是唯一配置源：app 模块通过 `buildConfigField` 注入到
     * `BuildConfig.RELEASE_REPO_OWNER` / `RELEASE_REPO_NAME`，
     * 运行时代码（GithubApi）与关于页展示都读取它，避免多处硬编码不同步。
     * 迁移仓库时只改这两个常量（以及 `.github/workflows/release.yml` 的发布守卫）。
     */
    const val releaseRepoOwner = "xyz50270"
    const val releaseRepoName = "newBV"

    /**
     * 版本基数（`major.minor.patch`，可带 hotfix 段）。
     *
     * 优先取 CI 注入的 `NEWBV_VERSION_BASE`（由 tag 去掉 `v` 前缀得到），
     * 本地构建回退到最近一个 `v*` tag，因此发版只需打 tag，无需手改常量。
     * 仓库没有 tag 或不在 git 工作区时回退到 `0.0.0`。
     */
    private val versionBase: String
        get() {
            System.getenv("NEWBV_VERSION_BASE")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it.removePrefix("v") }

            return "git describe --tags --abbrev=0".exec()
                .removePrefix("v")
                .takeIf { it.matches(Regex("""\d+\.\d+\.\d+(\.\d+)?""")) }
                ?: "0.0.0"
        }

    val versionName: String
        get() = "$versionBase.r$versionCode.${"git rev-list HEAD --abbrev-commit --max-count=1".exec()}"
    val versionCode: Int
        get() = "git rev-list --count HEAD".exec().toIntOrNull() ?: 1
}

fun String.exec() = String(Runtime.getRuntime().exec(this).inputStream.readBytes()).trim()
