package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.PieceSlice
import `in`.xnnyygn.bittorrent.transmission.PieceSliceRequest

internal class DownloadedPieceAssembler(
    val requests: List<PieceSliceRequest>,
    private val index: Int,
    private val pieceSliceLength: Int
) {
    private val pieceSlices = Array<DownloadedPieceSlice?>(requests.size) { null }
    private var pieceSliceCount = 0

    fun piece(begin: Int, piece: PieceSlice): DownloadedPiece? {
        val requestIndex = begin / pieceSliceLength
        if (requestIndex < 0 || requestIndex >= requests.size) {
            return null
        }
        if (pieceSlices[requestIndex] != null) {
            return null
        }
        val request = requests[requestIndex]
        if (request.length != piece.length) {
            throw IllegalStateException("unexpected piece length")
        }
        pieceSlices[requestIndex] = (piece as DownloadedPieceSlice)
        pieceSliceCount++
        if (pieceSliceCount < requests.size) {
            return null
        }
        return DownloadedPiece(index, pieceSlices.filterNotNull())
    }

    fun cancel(): List<PieceSliceRequest> {
        val requestsWaiting = mutableListOf<PieceSliceRequest>()
        for (i in requests.indices) {
            val pieceSlice = pieceSlices[i]
            if (pieceSlice == null) {
                requestsWaiting.add(requests[i])
            } else {
                pieceSlice.release()
            }
        }
        return requestsWaiting
    }
}