package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.net.PeerConnection

internal class PeerDownloadTask(
    private val connection: PeerConnection,
    private val remotePiecesStatus: PiecesStatus
) : Comparable<PeerDownloadTask> {

    inner class PieceRequest(val index: Int, private val timestampCreated: Long = System.currentTimeMillis()) {
        private var _timestampSent: Long? = null

        fun requestSent(timestamp: Long = System.currentTimeMillis()) {
            check(_timestampSent == null) { "requestSent invoked more than once" }
            _timestampSent = timestamp
        }

        fun isTimeout(timeout: Long): Boolean {
            return _timestampSent == null && (System.currentTimeMillis() - timestampCreated > timeout)
        }
    }

    private val requestMap = mutableMapOf<Int, PieceRequest>()
    private var nSuccessfulRequest: Int = 0
    private var nTimeout: Int = 0
    private var corruptedPieceMap = mutableMapOf<Int, Int>()

    val requestCount: Int
        get() = requestMap.size

    fun pieceIndices(): Iterator<Int> = remotePiecesStatus.pieces()

    fun cancelTimeoutRequests(timeout: Long): List<PieceRequest> {
        val timeoutRequests = requestMap.values.filter { it.isTimeout(timeout) }
        for (request in timeoutRequests) {
            doCancelRequest(request.index)
        }
        if (timeoutRequests.isNotEmpty()) {
            nTimeout++
        }
        return timeoutRequests
    }

    private fun requestByPieceIndex(index: Int): PieceRequest {
        return requestMap[index] ?: throw IllegalStateException("no request of piece $index")
    }

    fun have(index: Int) {
        remotePiecesStatus.addPiece(index)
    }

    fun requestSent(index: Int) {
        requestByPieceIndex(index).requestSent()
    }

    fun pieceDownloaded(index: Int) {
        requestMap.remove(index)
        nSuccessfulRequest++
    }

    fun pieceCorrupted(index: Int): Int {
        val times = pieceCorruptedTimes(index) + 1
        corruptedPieceMap[index] = times
        return times
    }

    fun pieceCorruptedTimes(index: Int): Int {
        return corruptedPieceMap[index] ?: 0
    }

    fun requestPiece(index: Int) {
        connection.requestPiece(index)
        requestMap[index] = PieceRequest(index)
    }

    fun cancelRequest(index: Int) {
        if (requestMap.containsKey(index)) {
            doCancelRequest(index)
        }
    }

    private fun doCancelRequest(index: Int) {
        requestMap.remove(index)
        connection.cancelRequest(index)
    }

    // healthy comparator
    override fun compareTo(other: PeerDownloadTask): Int {
        if (other.nSuccessfulRequest < this.nSuccessfulRequest) return -1
        if (this.nSuccessfulRequest < other.nSuccessfulRequest) return 1
        if (other.nTimeout < this.nTimeout) return 1
        if (other.corruptedPieceMap.size < this.corruptedPieceMap.size) return 1
        return 0
    }

    fun sendUninterest() {
        connection.sendUninterest()
    }
}