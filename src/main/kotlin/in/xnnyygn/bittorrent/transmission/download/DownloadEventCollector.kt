package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.net.PeerConnection
import `in`.xnnyygn.bittorrent.worker.EventBus
import `in`.xnnyygn.bittorrent.worker.QueueNames

internal data class BitFieldDownloadEvent(val connection: PeerConnection, val remotePiecesStatus: PiecesStatus) :
    Event
internal data class HaveDownloadEvent(val connection: PeerConnection, val index: Int) :
    Event
internal data class RequestSentDownloadEvent(val connection: PeerConnection, val index: Int) :
    Event
internal data class PieceDownloadedEvent(val connection: PeerConnection, val piece: DownloadedPiece) :
    Event

class DownloadEventCollector(private val connection: PeerConnection, private val eventBus: EventBus) {

    fun bitField(remotePiecesStatus: PiecesStatus) =
        send(BitFieldDownloadEvent(connection, remotePiecesStatus))

    fun have(index: Int) = send(
        HaveDownloadEvent(
            connection,
            index
        )
    )
    fun requestSent(index: Int) = send(
        RequestSentDownloadEvent(
            connection,
            index
        )
    )
    fun pieceDownloaded(piece: DownloadedPiece) = send(
        PieceDownloadedEvent(
            connection,
            piece
        )
    )

    private fun send(event: Event) {
        eventBus.offer(QueueNames.DOWNLOADER, event)
    }
}