package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.tracker.PeerListEvent
import `in`.xnnyygn.bittorrent.worker.AbstractWorker
import java.io.IOException
import java.util.LinkedList
import java.util.Queue

class HandshakeWorker(private val handshakeProtocol: HandshakeProtocol) : AbstractWorker(QueueName.HANDSHAKE) {

    private val pendingPeerQueue: Queue<Peer> = LinkedList<Peer>()
    private val connectedPeers = mutableSetOf<Peer>()

    override suspend fun handle(event: Event) {
        when (event) {
            is PeerListEvent -> {
                pendingPeerQueue.addAll(event.peers)
                handshakeAll(3)
            }
            is PeerDisconnectedEvent -> {
                connectedPeers.remove(event.peer)
                handshakeAll(3)
            }
        }
    }

    private suspend fun handshakeAll(limit: Int): Int {
        require(limit >= 1) { "limit <= 0" }
        var connected = connectedPeers.size
        while (connected < limit && !pendingPeerQueue.isEmpty()) {
            val peer = pendingPeerQueue.poll()!!
            if (connectedPeers.contains(peer)) {
                continue
            }
            if (handshake(peer)) {
                connected++
                connectedPeers.add(peer)
            }
        }
        return connected
    }

    private suspend fun handshake(peer: Peer): Boolean {
        val socket = OutgoingPeerSocket(peer)
        if (!tryConnect(socket)) {
            return false
        }
        if (!tryHandshake(socket)) {
            socket.closeQuietly()
            return false
        }
        val connection = PeerConnection(socket)
        eventBus.offer(QueueName.TRANSMISSION, PeerConnectionEvent(connection))
        connectedPeers.add(peer)
        return true
    }

    private suspend fun tryConnect(socket: OutgoingPeerSocket): Boolean = try {
        socket.connect(socket.peer.ip, socket.peer.port)
        true
    } catch (e: IOException) {
        // log failed to connect to peer
        false
    }

    private suspend fun tryHandshake(socket: OutgoingPeerSocket): Boolean = try {
        handshakeProtocol.send(socket, socket.peer.id)
    } catch (e: IOException) {
        // log handshake failed
        socket.closeQuietly()
        false
    }

    private fun PeerSocket.closeQuietly() {
        try {
            this.close()
        } catch (e: IOException) {
        }
    }
}