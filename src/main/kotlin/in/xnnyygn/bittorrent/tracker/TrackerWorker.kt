package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.peer.Peer
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.Torrent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class TrackerWorker(
    torrent: Torrent,
    selfPeer: Peer,
    private val clientStatus: ClientStatus,
    private val eventBus: EventBus
)  {
    private val tracker: Tracker
    private var startEventSent = false

    init {
        val peer = Peer(ByteArray(20), "localhost", 6882)
        tracker = MockTracker(
            torrent.infoHash,
            selfPeer,
            3,
            listOf(peer)
        )
    }

    suspend fun start(): Unit = coroutineScope {
        do {
            findPeersAndAwait()
        } while (isActive)
        tracker.stop(clientStatus)
    }

    private suspend fun findPeersAndAwait(): Unit = coroutineScope {
        val s = startEventSent
        try {
            val response = tracker.findPeers(clientStatus, !s)
            eventBus.offer(
                QueueName.HANDSHAKE,
                PeerListEvent(response.peers)
            )
            if (!s) {
                startEventSent = true
            }
            delay(response.interval * 1000L)
        } catch (e: TrackerException) {
            // retry
            delay(3000L)
        }
    }
}