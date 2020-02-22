package `in`.xnnyygn.bittorrent.eventbus

class EventBus {
    private val queueMap: Map<QueueName, EventQueue<Event>> = mapOf(
        QueueName.TRACKER to EventQueue(),
        QueueName.HANDSHAKE to EventQueue(),
        QueueName.TRANSMISSION to EventQueue(),
        QueueName.PIECE_CACHE to EventQueue(),
        QueueName.FILE to EventQueue()
    )

    companion object {
        val instance: EventBus by lazy {
            EventBus()
        }
    }

    fun offer(destination: QueueName, payload: Event) {
        queueByName(destination).offer(payload)
    }

    private fun queueByName(name: QueueName): EventQueue<Event> {
        return queueMap[name] ?: throw IllegalArgumentException("unknown queue $name")
    }

    suspend fun bulkPoll(source: QueueName): List<Event> {
        return queueByName(source).bulkPoll()
    }
}