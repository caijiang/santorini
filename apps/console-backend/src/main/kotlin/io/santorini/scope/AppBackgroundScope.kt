package io.santorini.scope

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * @author CJ
 */
class AppBackgroundScope(
    parent: CoroutineContext = Dispatchers.Default
) : CoroutineScope {

    private val job = SupervisorJob()
    override val coroutineContext = parent + job

}