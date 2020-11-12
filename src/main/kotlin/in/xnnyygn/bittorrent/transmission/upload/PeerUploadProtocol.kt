package `in`.xnnyygn.bittorrent.transmission.upload

import `in`.xnnyygn.bittorrent.file.FilePieceLike
import `in`.xnnyygn.bittorrent.net.ChokeMessage
import `in`.xnnyygn.bittorrent.net.UnchokeMessage
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.transmission.PieceSliceRequest
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import io.netty.channel.Channel

// TODO rename piece cache
private class PieceLoader(private val cacheCapacity: Int, private val uploadEventCollector: UploadEventCollector) {

    // TODO LRU
    private val pieceCache = mutableMapOf<Int, FilePieceLike>()

    fun load(index: Int): FilePieceLike? {
        val piece = pieceCache[index]
        if (piece != null) {
            return piece
        }
        uploadEventCollector.loadPiece(index)
        return null
    }

    fun pieceLoaded(piece: FilePieceLike) {
        pieceCache[piece.index] = piece
    }

    fun clearCache() {
        pieceCache.clear()
    }
}

private class RequestSet {
    private val map = mutableMapOf<Int, MutableSet<PieceSliceRequest>>()

    fun add(pieceSliceRequest: PieceSliceRequest) {
        map.getOrPut(pieceSliceRequest.index) { mutableSetOf() }.add(pieceSliceRequest)
    }

    fun removeByIndex(index: Int): Collection<PieceSliceRequest> {
        return map.remove(index) ?: emptyList()
    }

    fun remove(index: Int, begin: Int, length: Int) {
        val requests = map[index] ?: return
        val request = requests.find { r ->
            r.begin == begin && r.length == length
        } ?: return
        requests.remove(request)
    }

    fun clear() {
        map.clear()
    }
}

class PeerUploadProtocol(
    private val clientStatus: ClientStatus,
    private val channel: Channel,
    private val transmissionConfig: TransmissionConfig,
    private val uploadEventCollector: UploadEventCollector
) {
    private var chokedByLocal = true
    private var interestedByRemote = false
    private val pieceLoader = PieceLoader(3, uploadEventCollector)
    private val requestSet = RequestSet()

    fun interestedByRemote() {
        interestedByRemote = true
        uploadEventCollector.interestedByRemote()
    }

    fun uninterestedByRemote() {
        interestedByRemote = false
        uploadEventCollector.uninterestedByRemote()
        pieceLoader.clearCache()
        requestSet.clear()
    }

    fun request(index: Int, begin: Int, length: Int) {
        if (chokedByLocal) {
            return
        }
        if (length > transmissionConfig.maxPieceSliceLength) {
            // TODO close and throw error
            return
        }
        val piece = pieceLoader.load(index)
        if (piece == null) {
            requestSet.add(
                PieceSliceRequest(
                    index,
                    begin,
                    length
                )
            )
        } else {
            sendPiece(piece, begin, length)
            channel.flush()
        }
    }

    private fun sendPiece(piece: FilePieceLike, begin: Int, length: Int) {
        piece.slice(begin, length).writeToChannel(channel)
        clientStatus.addUploaded(length)
        // TODO throw error
    }

    fun cancel(index: Int, begin: Int, length: Int) {
        requestSet.remove(index, begin, length)
    }

    //========================USER EVENT========================

    fun sendUnchoke() {
        if (chokedByLocal) {
            chokedByLocal = false
            channel.writeAndFlush(UnchokeMessage)
        }
    }

    fun sendChoke() {
        if (chokedByLocal) {
            return
        }
        chokedByLocal = true
        channel.writeAndFlush(ChokeMessage)
    }

    fun pieceLoaded(piece: FilePieceLike) {
        if (!interestedByRemote) {
            return
        }
        pieceLoader.pieceLoaded(piece)
        val pieceRequests = requestSet.removeByIndex(piece.index)
        if (pieceRequests.isEmpty()) {
            return
        }
        for (pieceRequest in pieceRequests) {
            sendPiece(piece, pieceRequest.begin, pieceRequest.length)
        }
        channel.flush()
    }

    fun pieceNotFound(index: Int) {
        requestSet.removeByIndex(index)
    }
}