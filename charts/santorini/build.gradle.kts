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

tasks.register("test") {
    group = "verification"
    this.notCompatibleWithConfigurationCache("不想搞")
    doFirst {
        val work = isCommandAvailable("helm")
        if (work) {
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
        } else {
            logger.warn("因为不存在 helm 跳过 chart 测试")
        }
    }
}