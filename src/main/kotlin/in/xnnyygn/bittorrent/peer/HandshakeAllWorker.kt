package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.PoisonPillEvent
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.tracker.PeerListEvent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.LinkedList
import java.util.Queue

interface OutgoingHandshake {
    suspend fun handshake(peer: Peer): PeerSocket?
}

private class DefaultOutgoingHandshake(private val handshakeProtocol: HandshakeProtocol) : OutgoingHandshake {
    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun handshake(peer: Peer): PeerSocket? {
        val socket = OutgoingPeerSocket(peer)
        try {
            socket.connect()
        } catch (e: IOException) {
            logger.warn("failed to connect to peer ${socket.peer}, cause [${e.message}]")
            return null
        }
        if (!tryHandshake(socket)) {
            socket.closeQuietly()
            return null
        }
        logger.info("handshake with peer ${socket.peer} successfully")
        return socket
    }

    private suspend fun tryHandshake(socket: OutgoingPeerSocket): Boolean = try {
        handshakeProtocol.send(socket, socket.peer.id)
    } catch (e: IOException) {
        logger.warn("failed to handshake to peer ${socket.peer}, cause [${e.message}]")
        false
    }
}

/**
 * safe for default scheduler
 */
class HandshakeAllWorker(
    private val outgoingHandshake: OutgoingHandshake,
    private val eventBus: EventBus,
    private val maxConnections: Int
) {
    constructor(handshakeProtocol: HandshakeProtocol, eventBus: EventBus, maxConnections: Int = 3) : this(
        DefaultOutgoingHandshake(handshakeProtocol), eventBus, maxConnections
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private val pendingPeerQueue: Queue<Peer> = LinkedList<Peer>()
    private val connectingPeers = mutableSetOf<Peer>()

    suspend fun start() = coroutineScope {
        start_loop@ while (isActive) {
            for (event in eventBus.bulkPoll(QueueName.HANDSHAKE_ALL)) {
                when (event) {
                    is PeerListEvent -> {
                        pendingPeerQueue.addAll(event.peers)
                        handshakeAll()
                    }
                    is PeerDisconnectedEvent -> {
                        logger.info("peer removed, ${event.peer}")
                        connectingPeers.remove(event.peer)
                        handshakeAll()
                    }
                    is PoisonPillEvent -> {
                        event.completableDeferred.complete(Unit)
                        break@start_loop
                    }
                }
            }
        }
    }

    private suspend fun handshakeAll() = coroutineScope {
        while (connectingPeers.size < maxConnections && pendingPeerQueue.isNotEmpty()) {
            val peer = pendingPeerQueue.poll()!!
            if (!connectingPeers.contains(peer)) {
                connectingPeers.add(peer)
                launch { handshake(peer) }
            }
        }
    }

    private suspend fun handshake(peer: Peer) {
        when (val socket = outgoingHandshake.handshake(peer)) {
            null -> eventBus.offer(QueueName.HANDSHAKE_ALL, PeerDisconnectedEvent(peer))
            else -> eventBus.offer(QueueName.TRANSMISSION, PeerConnectionEvent(PeerConnection(socket)))
        }
    }
}