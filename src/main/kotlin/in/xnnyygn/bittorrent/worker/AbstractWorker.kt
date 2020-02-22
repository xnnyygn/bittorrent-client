package `in`.xnnyygn.bittorrent.worker

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

abstract class AbstractWorker(
    private val source: QueueName,
    _eventBus: EventBus? = null,
    private val workerScheduler: WorkerScheduler? = null
) {
    protected val eventBus: EventBus = (_eventBus ?: EventBus.instance)
    protected val dispatcher: CoroutineDispatcher by lazy {
        (workerScheduler ?: WorkerScheduler.instance).nextCoroutineDispatcher()
    }

    suspend fun start() = coroutineScope {
        while (isActive) {
            for (event in eventBus.bulkPoll(source)) {
                withContext(dispatcher) {
                    handle(event)
                }
            }
        }
    }

    abstract suspend fun handle(event: Event)
}