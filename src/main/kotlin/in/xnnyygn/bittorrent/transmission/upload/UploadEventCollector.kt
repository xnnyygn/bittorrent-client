package `in`.xnnyygn.bittorrent.transmission.upload

import `in`.xnnyygn.bittorrent.net.PeerConnection
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.EventBus
import `in`.xnnyygn.bittorrent.worker.QueueNames

data class InterestedByRemoteUploadEvent(val connection: PeerConnection) :
    Event

data class UninterestedByRemoteUploadEvent(val connection: PeerConnection) :
    Event

data class LoadPieceEvent(val connection: PeerConnection, val index: Int) :
    Event

class UploadEventCollector(
    private val connection: PeerConnection,
    private val eventBus: EventBus
) {
    fun interestedByRemote() = send(
        InterestedByRemoteUploadEvent(
            connection
        )
    )

    fun uninterestedByRemote() = send(
        UninterestedByRemoteUploadEvent(
            connection
        )
    )

    private fun send(event: Event) {
        eventBus.offer(QueueNames.UPLOADER, event)
    }

    fun loadPiece(index: Int) {
        eventBus.offer(
            QueueNames.PIECE_CACHE,
            LoadPieceEvent(connection, index)
        )
    }
}