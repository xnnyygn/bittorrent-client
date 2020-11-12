package `in`.xnnyygn.bittorrent.worker

class WorkerContext internal constructor(
    workerExecutorFactory: WorkerExecutorFactory,
    val eventBus: EventBus,
    worker: Worker
) {
    internal val executor = workerExecutorFactory.create(ManagedWorker(this, worker))

    fun offer(queueName: String, event: Event) {
        eventBus.offer(queueName, event)
    }

    fun submit(event: Event) {
        executor.submit(event)
    }

    fun delay(delay: Long, event: Event) {
        executor.delay(delay, event)
    }
}