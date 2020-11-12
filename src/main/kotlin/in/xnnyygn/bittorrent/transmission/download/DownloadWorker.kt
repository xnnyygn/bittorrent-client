package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.FileDispatcher
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.net.PeerConnection
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext

private object DownloaderRefreshEvent : Event

class DownloadWorker(
    private val piecesHash: ByteArray,
    private val transmissionConfig: TransmissionConfig,
    private val localPiecesStatus: PiecesStatus,
    private val fileDispatcher: FileDispatcher
) : Worker {
    private val taskSet =
        PeerDownloadTaskSet(localPiecesStatus.pieceCount)

//    override suspend fun start() = coroutineScope {
//        while (isActive) {
//            val events = eventBus.bulkPoll(QueueName.DOWNLOADER)
//            for (event in events) {
//                when (event) {
//                    is BitFieldDownloadEvent -> bitField(event.connection, event.remotePiecesStatus)
//                    is HaveDownloadEvent -> have(event.connection, event.index)
//                    is RequestSentDownloadEvent -> requestSent(event.connection, event.index)
//                    is PieceDownloadedEvent -> pieceDownloaded(event.connection, event.piece)
//                    is DownloaderRefreshEvent -> refresh() // TODO refresh every 3 seconds
//                }
//            }
//        }
//    }

    override fun handle(event: Event, context: WorkerContext) {
        when (event) {
            is BitFieldDownloadEvent -> bitField(event.connection, event.remotePiecesStatus)
            is HaveDownloadEvent -> have(event.connection, event.index)
            is RequestSentDownloadEvent -> requestSent(event.connection, event.index)
            is PieceDownloadedEvent -> pieceDownloaded(event.connection, event.piece)
            is DownloaderRefreshEvent -> refresh() // TODO refresh every 3 seconds
        }
    }

    private fun bitField(connection: PeerConnection, remotePiecesStatus: PiecesStatus) {
        val task = taskSet.bitField(connection, remotePiecesStatus)
        tryRequestPieces(task)
    }

    private fun have(connection: PeerConnection, index: Int) {
        val task = taskSet.have(connection, index)
        tryRequestPieces(task)
    }

    private fun tryRequestPieces(downloadTask: PeerDownloadTask) {
        var requestCount = downloadTask.requestCount
        if (requestCount >= transmissionConfig.maxRequestsPerConnection) {
            return
        }
        var missingPiece = false
        for (index in downloadTask.pieceIndices()) {
            if (localPiecesStatus.hasPiece(index)) continue
            missingPiece = true
            if (taskSet.isRequesting(index)) continue
            taskSet.requestPiece(downloadTask, index)
            requestCount++
            if (requestCount >= transmissionConfig.maxRequestsPerConnection) {
                return
            }
        }
        if (!missingPiece) {
            downloadTask.sendUninterest()
        }
    }

    private fun requestSent(connection: PeerConnection, index: Int) {
        val task = taskSet.findOrCreate(connection)
        task.requestSent(index)
    }

    private fun pieceDownloaded(connection: PeerConnection, piece: DownloadedPiece) {
        val task = taskSet.findOrCreate(connection)
        task.pieceDownloaded(piece.index)
        if (pieceDownloaded(piece)) {
            localPiecesStatus.addPiece(piece.index)
            tryRequestPieces(task)
        } else {
            piece.release()
            // retry
            if (task.pieceCorrupted(piece.index) < transmissionConfig.maxPieceAttempts) {
                task.requestPiece(piece.index)
            }
        }
    }

    private fun pieceDownloaded(piece: DownloadedPiece): Boolean {
        if (validatePiece(piece)) {
            return false
        }
        fileDispatcher.savePiece(piece)
        return true
    }

    private fun validatePiece(piece: DownloadedPiece): Boolean {
        if (piece.index < 0 || piece.index >= localPiecesStatus.pieceCount) return false
        val hash = piece.hash()
        if (hash.size != 20) return false
        val offset = piece.index * 20
        for (i in 0 until 20) {
            if (piecesHash[offset + i] != hash[i]) {
                return false
            }
        }
        return true
    }

    private fun refresh() {
        taskSet.cancelTimeoutRequests(transmissionConfig.requestSentTimeout)
        for (index in localPiecesStatus.missingPieces()) {
            taskSet.tryRequest(
                index,
                transmissionConfig.maxRequestsPerConnection,
                transmissionConfig.maxPieceAttempts
            )
        }
    }
}