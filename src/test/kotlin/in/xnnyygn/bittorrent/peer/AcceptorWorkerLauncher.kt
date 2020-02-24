package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() {
    val handshakeProtocol = HandshakeProtocol(ByteArray(20), ByteArray(20))
    val eventBus = EventBus()
    val acceptorWorker = AcceptorWorker(6881, handshakeProtocol, eventBus, maxConnections = 1)
    runBlocking {
        launch { acceptorWorker.start() }
        while (isActive) {
            println(eventBus.bulkPoll(QueueName.TRANSMISSION))
        }
    }
}