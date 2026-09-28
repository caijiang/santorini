fun isCommandAvailable(cmd: String): Boolean {
    return try {
        val code = ProcessBuilder()
            .command("sh", "-c", "command -v $cmd")
            .start()
            .onExit()
            .join()
            .exitValue()
        logger.info("command: {} ,exit status:{}", cmd, code)
        code == 0
    } catch (e: Exception) {
        logger.warn("无法执行 sh?", e)
        false
    }
}

/**
 * helm-unittest 插件是否已安装——这才是真前置条件。
 *
 * 不要拿 `wget` 之类脚本内部的实现细节当门禁：探测错了就会把"本机缺工具"伪装成"chart 测试失败"，
 * 而 build.sh 里的 `helm plugin install` 自己又要联网，属于不可控。
 */
fun isHelmUnittestInstalled(): Boolean {
    return try {
        val ps = ProcessBuilder()
            .command("helm", "plugin", "list")
            .redirectErrorStream(true)
            .start()
        val output = ps.inputStream.bufferedReader().use { it.readText() }
        ps.waitFor()
        output.lineSequence().any { it.substringBefore('\t').trim() == "unittest" }
    } catch (e: Exception) {
        logger.warn("无法执行 helm plugin list?", e)
        false
    }
}

/**
 * 是否在 GitHub Actions 里运行——官方注入的固定变量。
 * CI 环境里 helm / helm-unittest 由 workflow 自己保证，本地探测门禁全部跳过。
 */
fun isGitHubActions(): Boolean {
    return System.getenv("GITHUB_ACTIONS") == "true"
}

tasks.register("test") {
    description = "测试 chart"
    group = "verification"
    this.notCompatibleWithConfigurationCache("不想搞")
    doFirst {
        val skipReason = when {
            isGitHubActions() -> null
            !isCommandAvailable("helm") -> "helm 不在 PATH 上"
            !isHelmUnittestInstalled() -> "helm-unittest 插件未安装（helm plugin install https://github.com/helm-unittest/helm-unittest.git --verify=false）"
            else -> null
        }
        if (skipReason != null) {
            logger.warn("跳过 chart 测试：{}", skipReason)
            return@doFirst
        }
        if (isGitHubActions()) logger.lifecycle("GitHub Actions 环境，跳过本地工具探测，直接执行 chart 测试")
        logger.info("helm command available")
        val shell = System.getenv("SHELL") ?: "/bin/sh"
        val ps = ProcessBuilder()
//                .command("helm", "unittest", layout.projectDirectory.asFile.absolutePath)
            .command(shell, layout.projectDirectory.file("build.sh").asFile.absolutePath)
            .start()
            .onExit()
            .join()
        ps.inputReader().lines().forEach { line -> logger.info(line) }
        ps.errorReader().lines().forEach { line -> logger.warn(line) }
        val code = ps.exitValue()
        if (code != 0)
            throw GradleException("helm unittest failed:${code}")
    }
}