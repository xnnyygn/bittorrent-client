package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.worker.AbstractWorker

class FileWorker(
    private val torrent: Torrent,
    private val localPiecesStatus: PiecesStatus,
    private val clientStatus: ClientStatus
) : AbstractWorker(QueueName.FILE) {

    override suspend fun handle(event: Event) {
        when (event) {
            is LoadPieceFromDiskEvent -> {
            }
            is PieceFromRemoteEvent -> {
            }
        }
    }
}