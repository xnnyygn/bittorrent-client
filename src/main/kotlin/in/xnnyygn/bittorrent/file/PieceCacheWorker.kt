package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.net.PeerConnection
import `in`.xnnyygn.bittorrent.transmission.upload.LoadPieceEvent
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext

class FilePieceLoadTask(
    private val index: Int,
    private val connection: PeerConnection,
    private val nWorker: Int
) {
    private val slices = mutableListOf<FilePieceSlice>()

    fun filePieceSliceLoaded(slice: FilePieceSlice): FilePieceLike? {
        val piece = tryMergePiece(slice)
        if (piece != null) {
            connection.pieceLoaded(piece)
        }
        return piece
    }

    private fun tryMergePiece(slice: FilePieceSlice): FilePieceLike? {
        if (nWorker == 1) {
            return slice.toFilePiece(index)
        }
        slices.add(slice)
        if (slices.size < nWorker) {
            return null
        }
        return MergedFilePiece(index, slices.sortedBy { it.offsetInPiece })
    }
}

class FilePieceLoader(private val fileDispatcher: FileDispatcher) {
    private val taskMap = mutableMapOf<Int, FilePieceLoadTask>()

    fun load(index: Int, connection: PeerConnection) {
        val task = taskMap[index]
        if (task != null) {
            return
        }
        val nWorker = fileDispatcher.loadPiece(index)
        if (nWorker < 1) {
            return
        }
        val newTask = FilePieceLoadTask(index, connection, nWorker)
        taskMap[index] = newTask
    }

    fun filePieceSliceLoaded(index: Int, slice: FilePieceSlice): FilePieceLike? {
        val task = taskMap[index] ?: return null
        val piece = task.filePieceSliceLoaded(slice)
        if (piece != null) {
            taskMap.remove(index)
        }
        return piece
    }
}

class PieceCacheWorker(fileDispatcher: FileDispatcher) : Worker {
    private val pieceLoader = FilePieceLoader(fileDispatcher)

    // TODO LRU map
    private val pieceMap = mutableMapOf<Int, FilePieceLike>()

    override fun handle(event: Event, context: WorkerContext) {
        when (event) {
            is LoadPieceEvent -> loadPiece(event.connection, event.index)
            is FilePieceSliceLoadedEvent -> filePieceSliceLoaded(event.pieceIndex, event.pieceSlice)
        }
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