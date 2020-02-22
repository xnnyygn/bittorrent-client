package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.worker.AbstractWorker

class PieceCacheWorker : AbstractWorker(QueueName.PIECE_CACHE) {
    override suspend fun handle(event: Event) {
        when (event) {
            is LoadPieceFromCacheEvent -> event.session.pieceCacheLoaded(
                event.index,
                PieceCache(emptyList())
            )
            is PieceLoadedEvent -> event.session.pieceCacheLoaded(event.index, PieceCache(event.buffers))
        }
    }
}