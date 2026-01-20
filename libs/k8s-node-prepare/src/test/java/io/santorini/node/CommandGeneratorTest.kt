package io.santorini.node

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * @author CJ
 */
@Disabled
class CommandGeneratorTest {

    @Test
    fun shell() {
        val generator = testCommandGenerator(javaClass)

        println(generator.shell())
    }
}