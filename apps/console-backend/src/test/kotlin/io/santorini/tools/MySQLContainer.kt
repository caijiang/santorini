package io.santorini.tools

import org.jetbrains.exposed.v1.jdbc.Database
import org.testcontainers.containers.MySQLContainer


val <SELF : MySQLContainer<SELF>> MySQLContainer<SELF>.database: Database
    get() = Database.connect(
        jdbcUrl, driverClassName, username, password
    )
