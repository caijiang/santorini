package io.santorini

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * @author CJ
 */
class AesGcmCryptoTest {
    @Test
    internal fun go1() {
        val key1 = AesGcmCrypto.generateKey()
        val s1 = Random.nextInt(0, 10).toString()
        assertEquals(s1, AesGcmCrypto.decrypt(AesGcmCrypto.encrypt(s1, key1), key1))

        val s2 = CharArray(1024 * 1024) {
            Random.nextInt(0, 10).digitToChar()
        }.concatToString()

        val e2 = AesGcmCrypto.encrypt(s2, key1)
        println(e2.length)
        assertEquals(s2, AesGcmCrypto.decrypt(e2, key1))
    }
}