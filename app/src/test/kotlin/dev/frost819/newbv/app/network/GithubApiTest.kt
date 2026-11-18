package dev.frost819.newbv.app.network

import com.google.common.truth.Truth.assertThat
import dev.frost819.newbv.BuildConfig
import org.junit.jupiter.api.Test

/**
 * [GithubApi] 的单元测试。
 *
 * 覆盖仓库坐标来源与 404 响应判定：
 * - 仓库坐标必须来自 [BuildConfig]（构建配置单一源），不能在代码里硬编码旧仓库。
 * - GitHub 对「仓库不存在」与「尚无 Release」都返回 404，需按响应体区分，
 *   否则迁移仓库后首次发版前，UI 会把「还没发版」误报成网络故障。
 */
class GithubApiTest {
    @Test
    fun `owner and repo come from build config`() {
        assertThat(GithubApi.OWNER).isEqualTo(BuildConfig.RELEASE_REPO_OWNER)
        assertThat(GithubApi.REPO).isEqualTo(BuildConfig.RELEASE_REPO_NAME)
    }

    @Test
    fun `build config repo coordinates are not blank`() {
        assertThat(GithubApi.OWNER).isNotEmpty()
        assertThat(GithubApi.REPO).isNotEmpty()
    }

    @Test
    fun `repo html url is composed from owner and repo`() {
        assertThat(GithubApi.REPO_HTML_URL)
            .isEqualTo("https://github.com/${GithubApi.OWNER}/${GithubApi.REPO}")
    }

    @Test
    fun `isNotFound detects github 404 body`() {
        val body =
            """
            {
              "message": "Not Found",
              "documentation_url": "https://docs.github.com/rest/releases/releases#get-the-latest-release",
              "status": "404"
            }
            """.trimIndent()

        assertThat(GithubApi.isNotFound(body)).isTrue()
    }

    @Test
    fun `isNotFound ignores valid release json`() {
        val body = """{"tag_name":"v1.0.0","name":"newBV 1.0.0"}"""

        assertThat(GithubApi.isNotFound(body)).isFalse()
    }

    @Test
    fun `isNotFound ignores non json body`() {
        assertThat(GithubApi.isNotFound("<html>502 Bad Gateway</html>")).isFalse()
    }

    @Test
    fun `isNotFound ignores api error without 404 status`() {
        val body = """{"message":"API rate limit exceeded","status":"403"}"""

        assertThat(GithubApi.isNotFound(body)).isFalse()
    }

    @Test
    fun `no release exception message names the configured repo`() {
        val error = NoReleasePublishedException()

        assertThat(error.message).contains(GithubApi.OWNER)
        assertThat(error.message).contains(GithubApi.REPO)
    }

    // ── 备用镜像通道 ────────────────────────────────────────

    @Test
    fun `toMirrorUrl prefixes github url`() {
        val original = "https://github.com/${GithubApi.OWNER}/${GithubApi.REPO}/releases/download/v1.0.0/a.apk"

        val mirrored = GithubApi.toMirrorUrl(original)

        assertThat(mirrored).isEqualTo("https://ghfast.top/$original")
    }

    @Test
    fun `toMirrorUrl is idempotent`() {
        val original = "https://github.com/${GithubApi.OWNER}/${GithubApi.REPO}/releases/download/v1.0.0/a.apk"
        val once = GithubApi.toMirrorUrl(original)

        // 重试路径上会再次转换，重复加前缀会拼出无效地址
        assertThat(GithubApi.toMirrorUrl(once)).isEqualTo(once)
    }

    @Test
    fun `manifest asset name matches ci upload name`() {
        // 与 .github/workflows/release.yml 中生成的文件名、以及客户端请求的附件名必须一致，
        // 否则备用通道会 404，且只在网络受限的环境下才暴露
        assertThat(GithubApi.MANIFEST_ASSET_NAME).isEqualTo("latest.json")
    }
}
