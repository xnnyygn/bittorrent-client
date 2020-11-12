package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.transmission.download.DownloadedPiece

class FileDispatcher internal constructor(
    private val workerMap: Map<Int, List<FileRegionWorker>>
) {
    fun savePiece(piece: DownloadedPiece) {
        val workers = workerMap[piece.index] ?: return
        for (worker in workers) {
            worker.savePieceToFile(piece, workers.size)
        }
    }

    fun loadPiece(index: Int): Int {
        val workers = workerMap[index] ?: return 0
        for (worker in workers) {
            worker.loadFilePieceSlice(index)
        }
        return workers.size
    }
}