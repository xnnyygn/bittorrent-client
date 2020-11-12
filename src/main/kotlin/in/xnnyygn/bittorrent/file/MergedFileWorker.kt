package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.net.PeerConnection
import `in`.xnnyygn.bittorrent.transmission.download.DownloadedPiece
import `in`.xnnyygn.bittorrent.transmission.upload.LoadPieceEvent
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext

// TODO separate file
class PiecesStatusFile(path: String) {
    fun load(): PiecesStatus {
        TODO()
    }

    fun hasPiece(): Boolean {
        TODO()
    }

    fun addPiece(index: Int) {
        TODO()
    }

    fun close() {
        TODO()
    }
}

interface MergedFile {
    fun savePiece(piece: DownloadedPiece)
    fun loadPiece(index: Int, worker: Worker)
}

class MergedFileWorker(
    private val piecesStatusFile: PiecesStatusFile,
    private val fileDispatcher: FileDispatcher
) : MergedFile, Worker {
    private val pieceLoader = FilePieceLoader(fileDispatcher)

    // TODO LRU map
    private val pieceMap = mutableMapOf<Int, FilePieceLike>()

    override fun savePiece(piece: DownloadedPiece) {
        // TODO add this as second parameter
        fileDispatcher.savePiece(piece)
        // when done, piece saved
    }

    override fun loadPiece(index: Int, worker: Worker) {
        // TODO run in context
    }

    override fun handle(event: Event, context: WorkerContext) {
        when (event) {
            is LoadPieceEvent -> loadPiece(event.connection, event.index)
            is FilePieceSliceLoadedEvent -> filePieceSliceLoaded(event.pieceIndex, event.pieceSlice)
        }
    }

    private fun pieceSaved(index: Int) {
        piecesStatusFile.addPiece(index)
    }

    private fun loadPiece(connection: PeerConnection, index: Int) {
        val pieceCache = pieceMap[index]
        if (pieceCache != null) {
            connection.pieceLoaded(pieceCache)
        } else {
            pieceLoader.load(index, connection)
        }
    }

    private fun filePieceNotFound(index: Int) {
        TODO()
    }

    private fun filePieceSliceLoaded(pieceIndex: Int, pieceSlice: FilePieceSlice) {
        val piece = pieceLoader.filePieceSliceLoaded(pieceIndex, pieceSlice)
        if (piece != null) {
            pieceMap[piece.index] = piece
        }
    }
}