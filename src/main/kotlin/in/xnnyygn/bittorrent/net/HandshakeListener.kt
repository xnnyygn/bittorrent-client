package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.worker.Event

internal data class PostHandshakeOutgoingConnectionEvent(val connection: OutgoingPeerConnection) :
    Event

//class HandshakeListener(private val eventBus: EventBus) {
//    fun postHandshake(connection: OutgoingPeerConnection) {
//        eventBus.offer(QueueName.HANDSHAKE_ALL, PostHandshakeOutgoingConnectionEvent(connection))
//    }
//}