package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.PieceSlice
import `in`.xnnyygn.bittorrent.transmission.PieceSliceRequest

internal class DownloadedPieceAssemblerSet(
    private val pieceLength: Int,
    private val totalLength: Long,
    private val pieceSliceLength: Int
) {
    private val assemblerMap = mutableMapOf<Int, DownloadedPieceAssembler>()

    fun add(index: Int): List<PieceSliceRequest> {
        if (assemblerMap.containsKey(index)) {
            return emptyList()
        }
        val assembler = makeAssembler(index)
        assemblerMap[index] = assembler
        return assembler.requests
    }

    fun piece(index: Int, begin: Int, piece: PieceSlice): DownloadedPiece? {
        val assembler = assemblerMap[index] ?: return null
        return assembler.piece(begin, piece)
    }

    private fun makeAssembler(index: Int): DownloadedPieceAssembler {
        val requests = mutableListOf<PieceSliceRequest>()
        var begin = 0
        val end = pieceLength.coerceAtMost((totalLength - (index + 1) * pieceLength).toInt())
        while (begin < end) {
            requests.add(
                PieceSliceRequest(
                    index,
                    begin,
                    pieceSliceLength.coerceAtMost(end - begin)
                )
            )
            begin += pieceSliceLength
        }
        return DownloadedPieceAssembler(
            requests,
            index,
            pieceSliceLength
        )
    }

    fun remove(index: Int): List<PieceSliceRequest> {
        val assembler = assemblerMap[index] ?: return emptyList()
        val requestsWaiting = assembler.cancel()
        assemblerMap.remove(index)
        return requestsWaiting
    }
}