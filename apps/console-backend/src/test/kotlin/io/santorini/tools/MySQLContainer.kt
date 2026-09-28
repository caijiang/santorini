package io.santorini.tools

import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.MySQLContainer


val <SELF : MySQLContainer<SELF>> MySQLContainer<SELF>.database: Database
    get() = Database.connect(
        jdbcUrl, driverClassName, username, password
    )

/**
 * 给单个测试类一个独占的内存库。
 *
 * 不走 [io.santorini.consoleModuleEntry] 默认的 `jdbc:h2:mem:test;DB_CLOSE_DELAY=-1`：
 * 那一个库在同一个测试 JVM 内是**所有测试类共享**的，谁先跑、谁写了几行，会直接改变后面类的断言结果
 * —— 类执行顺序一变，"有时候过有时候不过"就来了。这里按名字隔离，去掉顺序依赖。
 */
fun isolatedTestDatabase(name: String): Database = Database.connect(
    url = "jdbc:h2:mem:test_$name;DB_CLOSE_DELAY=-1",
    user = "root",
    password = "",
)
