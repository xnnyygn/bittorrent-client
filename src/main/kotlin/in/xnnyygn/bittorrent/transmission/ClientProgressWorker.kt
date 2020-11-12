package `in`.xnnyygn.bittorrent.transmission

import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext

class ClientProgressWorker(
    private val localPiecesStatus: PiecesStatus
) : Worker {
    override fun handle(event: Event, context: WorkerContext) {
    }
}