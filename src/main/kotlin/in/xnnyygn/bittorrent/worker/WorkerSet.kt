package `in`.xnnyygn.bittorrent.worker

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class WorkerSet internal constructor(
    private val parentScheduledExecutor: ScheduledExecutorService,
    private val workerRunners: Array<PinnedWorkerRunner>,
    val eventBus: EventBus,
    private val workers: List<ManagedWorker>
) {
    fun startAll() {
        for (worker in workers) {
            worker.start()
        }
        for (runner in workerRunners) {
            // TODO lazy start
            runner.start()
        }
    }

    fun offer(queueName: String, event: Event) {
        eventBus.offer(queueName, event)
    }

    fun shutdown() {
        for (worker in workers) {
            worker.context.executor.shutdown()
        }
        for (runner in workerRunners) {
            runner.stop()
        }
        parentScheduledExecutor.shutdown()
    }

    fun awaitTermination(timeout: Long) {
        parentScheduledExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS)
    }

    fun stopAll() {
        for (worker in workers) {
            worker.stop()
        }
    }
}

class WorkerSetBuilder {
    private val workerMap = mutableMapOf<String, Worker>()
    private val workers = mutableListOf<Worker>()

    fun register(queueName: String, worker: Worker): WorkerSetBuilder {
        workerMap[queueName] = worker
        return this
    }

    fun add(worker: Worker): WorkerSetBuilder {
        workers.add(worker)
        return this
    }

    fun addAll(workers: Collection<Worker>): WorkerSetBuilder {
        this.workers.addAll(workers)
        return this
    }

    fun build(): WorkerSet {
        val parentScheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        val workerRunners =
            Array(Runtime.getRuntime().availableProcessors()) { i -> PinnedWorkerRunner("PinnedWorkerRunner$i") }
        val workerExecutorFactory = PinnedWorkerExecutorFactory(parentScheduledExecutor, workerRunners)
        val eventBus = EventBus(workerExecutorFactory, workerMap)
        val managedWorkers = mutableListOf<ManagedWorker>()
        managedWorkers.addAll(eventBus.contexts.map { it.executor.worker })
        for (worker in workers) {
            val context = WorkerContext(workerExecutorFactory, eventBus, worker)
            managedWorkers.add(ManagedWorker(context, worker))
        }
        return WorkerSet(parentScheduledExecutor, workerRunners, eventBus, managedWorkers)
    }
}