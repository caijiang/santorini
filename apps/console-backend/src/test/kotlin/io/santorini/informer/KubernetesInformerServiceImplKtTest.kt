package io.santorini.informer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * @author CJ
 */
class KubernetesInformerServiceImplKtTest {

    @Test
    fun findPureNameFromIdentify() {
        "my-santorini-santorini-console-backend-666f8fd976-85nrg".findPureNameFromIdentify() shouldBe "my-santorini-santorini-console-backend"
        "my-santorini-santorini-console-backend-666f8fd976".findPureNameFromIdentify() shouldBe "my-santorini-santorini-console-backend"
        "backend-666f8fd976".findPureNameFromIdentify() shouldBe "backend"
    }
}