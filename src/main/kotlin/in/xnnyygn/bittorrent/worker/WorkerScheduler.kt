package `in`.xnnyygn.bittorrent.worker

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class WorkerScheduler(nWorkers: Int) {
    private val dispatchers = Array<ExecutorCoroutineDispatcher?>(nWorkers) { null }
    private var index = 0

    companion object {
        val instance: WorkerScheduler by lazy {
            WorkerScheduler(Runtime.getRuntime().availableProcessors())
        }
    }

    fun nextCoroutineDispatcher(): CoroutineDispatcher {
        return getAndIncreaseIndex() ?: addAndIncreaseIndex()
    }

    private fun getAndIncreaseIndex(): CoroutineDispatcher? {
        val dispatcher = dispatchers[index]
        if (dispatcher != null) increaseIndex()
        return dispatcher
    }

    private fun addAndIncreaseIndex(): CoroutineDispatcher {
        val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        dispatchers[index] = dispatcher
        increaseIndex()
        return dispatcher
    }

    private fun increaseIndex() {
        val newIndex = index + 1
        index = (if (newIndex == dispatchers.size) 0 else newIndex)
    }

    fun close() {
        dispatchers.forEach { it?.close() }
    }
}