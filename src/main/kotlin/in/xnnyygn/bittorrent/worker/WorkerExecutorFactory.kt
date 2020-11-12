package `in`.xnnyygn.bittorrent.worker

import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService

internal interface WorkerExecutorFactory {
    fun create(managedWorker: ManagedWorker): WorkerExecutor
}

internal class DefaultWorkerExecutorFactory(
    private val parentScheduledExecutor: ScheduledExecutorService,
    private val parentExecutor: ExecutorService
) : WorkerExecutorFactory {
    override fun create(managedWorker: ManagedWorker): WorkerExecutor {
        return DefaultWorkerExecutor(parentScheduledExecutor, parentExecutor, managedWorker)
    }
}

internal class PinnedWorkerExecutorFactory(
    private val parentScheduledExecutor: ScheduledExecutorService,
    private val workerRunners: Array<PinnedWorkerRunner>
) : WorkerExecutorFactory {
    private var index = 0

    init {
        require(workerRunners.isNotEmpty()) { "no worker runner" }
    }

    override fun create(managedWorker: ManagedWorker): WorkerExecutor {
        return PinnedWorkerExecutor(parentScheduledExecutor, nextRunner(), managedWorker)
    }

    private fun nextRunner(): PinnedWorkerRunner {
        val runner = workerRunners[index++]
        if (index >= workerRunners.size) {
            index %= workerRunners.size
        }
        return runner
    }
}