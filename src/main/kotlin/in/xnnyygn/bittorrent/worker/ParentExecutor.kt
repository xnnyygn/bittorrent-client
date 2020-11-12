package `in`.xnnyygn.bittorrent.worker

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ParentExecutor(
    private val scheduledExecutor: ScheduledExecutorService,
    private val executor: ExecutorService
) {
    fun submit(action: () -> Unit): Future<*> {
        return executor.submit(action)
    }

    fun schedule(delay: Long, action: () -> Unit): ScheduledFuture<*> {
        return scheduledExecutor.schedule(action, delay, TimeUnit.MILLISECONDS)
    }

    fun shutdown() {
        scheduledExecutor.shutdown()
        if (!executor.isShutdown) {
            executor.shutdown()
        }
    }

    fun awaitTermination(timeout: Long) {
        val start = System.currentTimeMillis()
        scheduledExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS)
        val elapsedTime = System.currentTimeMillis() - start
        executor.awaitTermination(
            (timeout - elapsedTime).coerceAtLeast(1000), TimeUnit.MILLISECONDS
        )
    }
}
