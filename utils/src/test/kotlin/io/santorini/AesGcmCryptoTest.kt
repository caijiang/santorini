package io.santorini

import io.kotest.matchers.shouldBe
import java.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals


/**
 * @author CJ
 */
class AesGcmCryptoTest {

    @Test
    internal fun `works with typescript side`() {
        AesGcmCrypto.decrypt(
            "25-TSDW_Hx48DDuScNYDAEnVcXUPIn_wVnVEsreQec5R",
            Base64.getUrlDecoder().decode("hbKxQGIWbR3ptS64UOSDCQMYRu8-3jJybhyZslBVQs4")
        ) shouldBe "hello"
    }

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