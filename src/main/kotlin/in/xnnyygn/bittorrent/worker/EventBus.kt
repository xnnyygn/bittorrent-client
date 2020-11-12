package `in`.xnnyygn.bittorrent.worker

class EventBus internal constructor(
    workerExecutorFactory: WorkerExecutorFactory,
    workerMap: Map<String, Worker>
) {
    private val contextMap = workerMap.mapValues { (_, worker) ->
        WorkerContext(workerExecutorFactory, this, worker)
    }

    internal val contexts: Collection<WorkerContext>
        get() = contextMap.values

    fun offer(queueName: String, event: Event) {
        contextMap[queueName]?.submit(event)
    }
}