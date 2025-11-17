package io.santorini

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * @author CJ
 */
class SessionKtTest {

    @Test
    fun bytes() {
        assertEquals(16, "Sa4Q5BtJvBWY6k2x".toByteArray(Charsets.UTF_8).size)
    }
}