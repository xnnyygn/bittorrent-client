package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.net.PeerConnection

// TODO download strategy, event based
internal class PeerDownloadTaskSet(
    private val pieceCount: Int
) {
    private val connectionTaskMap = mutableMapOf<PeerConnection, PeerDownloadTask>()
    private val pieceTaskMap = mutableMapOf<Int, MutableSet<PeerDownloadTask>>()
    private val requestingMap = mutableMapOf<Int, PeerDownloadTask>()

    fun findOrCreate(connection: PeerConnection): PeerDownloadTask {
        val task = connectionTaskMap[connection]
        if (task != null) {
            return task
        }
        val newTask = PeerDownloadTask(
            connection,
            BitSetPiecesStatus(pieceCount)
        )
        connectionTaskMap[connection] = newTask
        return newTask
    }

    fun bitField(connection: PeerConnection, remotePiecesStatus: PiecesStatus): PeerDownloadTask {
        val task = createTask(connection, remotePiecesStatus)
        for (index in remotePiecesStatus.pieces()) {
            addTaskToPieceMap(index, task)
        }
        return task
    }

    private fun createTask(connection: PeerConnection, remotePiecesStatus: PiecesStatus): PeerDownloadTask {
        val task = connectionTaskMap[connection]
        if (task != null) {
            throw IllegalStateException("receive bit field after creating connection")
        }
        val newTask =
            PeerDownloadTask(connection, remotePiecesStatus)
        connectionTaskMap[connection] = newTask
        return newTask
    }

    private fun addTaskToPieceMap(index: Int, task: PeerDownloadTask) {
        pieceTaskMap.getOrPut(index) { mutableSetOf() }.add(task)
    }

    fun have(connection: PeerConnection, index: Int): PeerDownloadTask {
        val task = findOrCreateForHave(connection, index)
        addTaskToPieceMap(index, task)
        return task
    }

    private fun findOrCreateForHave(connection: PeerConnection, index: Int): PeerDownloadTask {
        val task = connectionTaskMap[connection]
        if (task != null) {
            task.have(index)
            return task
        }
        val newTask = PeerDownloadTask(
            connection,
            BitSetPiecesStatus(pieceCount)
        )
        newTask.have(index)
        connectionTaskMap[connection] = newTask
        return newTask
    }

    fun isRequesting(index: Int): Boolean {
        return requestingMap.containsKey(index)
    }

    fun requestPiece(task: PeerDownloadTask, index: Int) {
        val previousTask = requestingMap[index]
        previousTask?.cancelRequest(index)
        requestingMap[index] = task
        task.requestPiece(index)
    }

    fun cancelTimeoutRequests(timeout: Long) {
        val allCancelledRequests = mutableListOf<PeerDownloadTask.PieceRequest>()
        for (task in requestingMap.values) {
            val cancelledRequests = task.cancelTimeoutRequests(timeout)
            if (cancelledRequests.isNotEmpty()) {
                allCancelledRequests.addAll(cancelledRequests)
            }
        }
        for (request in allCancelledRequests) {
            requestingMap.remove(request.index)
        }
    }

    /**
     * TODO download strategy,
     * piece slice vs piece,
     * one task or multiple for piece slice or piece,
     * different task for different file
      */
    fun tryRequest(index: Int, maxRequestsPerConnection: Int, maxPieceAttempts: Int) {
        if (requestingMap.containsKey(index)) return
        val candidates = pieceTaskMap[index] ?: return
        val task = candidates.sorted().find { d ->
            d.requestCount < maxRequestsPerConnection && d.pieceCorruptedTimes(index) < maxPieceAttempts
        } ?: return
        requestingMap[index] = task
        task.requestPiece(index)
    }
}