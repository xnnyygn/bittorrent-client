package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import `in`.xnnyygn.bittorrent.file.PieceSlice
import `in`.xnnyygn.bittorrent.net.CancelMessage
import `in`.xnnyygn.bittorrent.net.InterestedMessage
import `in`.xnnyygn.bittorrent.net.RequestMessage
import `in`.xnnyygn.bittorrent.net.UninterestedMessage
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import io.netty.channel.Channel
import java.util.BitSet

class PeerDownloadProtocol(
    private val clientStatus: ClientStatus,
    private val channel: Channel,
    private val torrent: Torrent,
    transmissionConfig: TransmissionConfig,
    private val downloadEventCollector: DownloadEventCollector
) {
    private var chokedByRemote: Boolean = true
    private var interestedByLocal = false
    private val pendingRequests = mutableSetOf<Int>()
    private val assemblerSet =
        DownloadedPieceAssemblerSet(
            torrent.info.pieceLength,
            torrent.totalLength,
            transmissionConfig.pieceSliceLength
        )

    fun chokedByRemote() {
        chokedByRemote = true
    }

    fun unchokeByRemote() {
        chokedByRemote = false
        assert(interestedByLocal)
        for (index in pendingRequests) {
            sendRequest(index)
        }
        pendingRequests.clear()
    }

    fun bitField(bytes: ByteArray) {
        val piecesStatus = BitSetPiecesStatus(BitSet.valueOf(bytes), torrent.pieceCount)
        downloadEventCollector.bitField(piecesStatus)
    }

    fun have(index: Int) {
        downloadEventCollector.have(index)
    }

    fun piece(index: Int, begin: Int, piece: PieceSlice) {
        clientStatus.addDownloaded(piece.length)
        val downloadedPiece = assemblerSet.piece(index, begin, piece) ?: return
        downloadEventCollector.pieceDownloaded(downloadedPiece)
    }

    //========================USER EVENT========================

    fun requestPiece(index: Int) {
        // send interested if not sent
        if (!sendInterest() || chokedByRemote) {
            pendingRequests.add(index)
        } else {
            sendRequest(index)
        }
    }

    private fun sendInterest(): Boolean {
        if (interestedByLocal) {
            return true
        }
        interestedByLocal = true
        channel.writeAndFlush(InterestedMessage)
        return false
    }

    private fun sendRequest(index: Int) {
        val pieceRequests = assemblerSet.add(index)
        for (pieceRequest in pieceRequests) {
            channel.write(RequestMessage(pieceRequest.index, pieceRequest.begin, pieceRequest.length))
        }
        channel.flush()
        downloadEventCollector.requestSent(index)
    }

    fun cancelRequest(index: Int) {
        if (pendingRequests.remove(index)) {
            return
        }
        val requestsWaiting = assemblerSet.remove(index)
        if (requestsWaiting.isEmpty()) {
            return
        }
        for (pieceRequest in requestsWaiting) {
            channel.write(CancelMessage(pieceRequest.index, pieceRequest.begin, pieceRequest.length))
        }
        channel.flush()
    }

    fun sendUninterest() {
        if (interestedByLocal) {
            channel.writeAndFlush(UninterestedMessage)
            interestedByLocal = false
        }
    }
}