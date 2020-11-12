package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.worker.EventBus
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext

class ConnectionsWorker(
    private val torrent: Torrent,
    private val transmissionConfig: TransmissionConfig,
    private val localPiecesStatus: PiecesStatus,
    private val clientStatus: ClientStatus
) : Worker {
    private val connections = mutableSetOf<PeerConnection>()

    override fun handle(event: Event, context: WorkerContext) {
        when (event) {
            is PostHandshakeConnectionEvent -> addAndStart(event.connection, context.eventBus)
            // TODO connection disconnected
            // TODO piece downloaded event
        }
    }

    private fun addAndStart(connection: PeerConnection, eventBus: EventBus) {
        connections.add(connection)
        connection.start(torrent, transmissionConfig, localPiecesStatus.duplicate(), clientStatus, eventBus)
    }
}