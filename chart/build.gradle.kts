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
            val ps = ProcessBuilder()
                .command("helm", "unittest", layout.projectDirectory.asFile.absolutePath)
                .start()
                .onExit()
                .join()
            ps.inputReader().lines().forEach { line -> logger.info(line) }
            val code = ps.exitValue()
            if (code != 0)
                throw GradleException("helm unittest failed")
        } else {
            logger.warn("因为不存在 helm 跳过 chart 测试")
        }
    }
}