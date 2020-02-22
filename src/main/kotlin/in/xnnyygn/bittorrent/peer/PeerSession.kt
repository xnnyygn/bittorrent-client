package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import `in`.xnnyygn.bittorrent.file.LoadPieceFromCacheEvent
import `in`.xnnyygn.bittorrent.file.PieceCache
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import java.nio.ByteBuffer

private sealed class AbstractTransmissionProtocol(
    protected val connection: PeerConnection
) {
    protected var chokedByRemote: Boolean = false

    open fun chokedByRemote() {
        chokedByRemote = true
    }

    open fun unchokeByRemote() {
        chokedByRemote = false
    }
}

private class DownloadProtocol(
    pieceCount: Int,
    connection: PeerConnection,
    private val clientStatus: ClientStatus,
    private val eventPublisher: EventPublisher
) : AbstractTransmissionProtocol(connection) {
    private var interestedByLocal: Boolean = false
    private var pieceStatus = BitSetPiecesStatus(pieceCount)
    private var lastRequestToRemote: PieceRequest? = null

    fun bitField(piecesStatus: PiecesStatus) {
    }

    fun have(index: Int) {
    }

    fun piece(index: Int, begin: Long, piece: ByteBuffer) {
    }

    fun sendRequest(index: Int) {
    }

    fun cancelRequest(index: Int) {
    }

    private fun sendRequest(index: Int, begin: Long, length: Long) {
        connection.writeMessage(RequestMessage(index, begin, length))
        eventPublisher.sendRequestToRemote(PieceRequest(index, begin, length))
    }

    fun close() {
    }
}

private class LruMap<K, V>(size: Int) {
    fun get(key: K): V? = TODO()
    fun remove(key: K): Unit = TODO()
    fun put(key: K, value: V): Unit = TODO()
}

private class PieceLoader(size: Int, private val eventPublisher: EventPublisher) {
    private val localPieceCache = LruMap<Int, PieceCache>(size)

    // TODO changed to Piece?
    fun load(index: Int): List<ByteBuffer>? {
        val cachedItem = localPieceCache.get(index)
        if (cachedItem != null) {
            val buffers = cachedItem.buffers
            if (buffers != null) {
                return buffers
            }
            localPieceCache.remove(index)
        }
        eventPublisher.loadPiece(index)
        return null
    }

    fun pieceCacheLoaded(index: Int, cache: PieceCache) {
        localPieceCache.put(index, cache)
    }
}

private class UploadProtocol(
    connection: PeerConnection,
    private val clientStatus: ClientStatus,
    eventPublisher: EventPublisher
) :
    AbstractTransmissionProtocol(connection) {
    private var chokedByLocal: Boolean = true
    private var interestedByRemote: Boolean = false
    private var lastRequestFromRemote: PieceRequest? = null
    private val pieceLoader = PieceLoader(3, eventPublisher)

    fun interestedByRemote() {
    }

    fun uninterestedByRemote() {
    }

    fun request(index: Int, begin: Long, length: Long) {
        // TODO create sending task
        val buffers = pieceLoader.load(index)
        if (buffers != null) {
            // sending
        }
    }

    fun cancel(index: Int, begin: Long, length: Long) {
    }

    // TODO not found
    fun pieceCacheLoaded(index: Int, cache: PieceCache) {
        // check sending progress, resume sending
        pieceLoader.pieceCacheLoaded(index, cache)
    }

    fun close() {
    }
}

private data class RequestMoreEvent(val index: Int) : Event

data class SendRequestToRemoteEvent(val pieceRequest: PieceRequest, val session: PeerSession) : Event

private data class CancelRequestEvent(val pieceRequest: PieceRequest) : Event

private interface EventPublisher {
    fun sendRequestToRemote(pieceRequest: PieceRequest)
    fun loadPiece(index: Int)
}

class PeerSession(
    private val pieceCount: Int,
    private val connection: PeerConnection,
    clientStatus: ClientStatus,
    private val eventBus: EventBus
) {
    private val eventPublisher = object : EventPublisher {
        override fun sendRequestToRemote(pieceRequest: PieceRequest) {
            val event = SendRequestToRemoteEvent(pieceRequest, this@PeerSession)
            eventBus.offer(QueueName.TRANSMISSION, event)
        }

        override fun loadPiece(index: Int) {
            val event =
                LoadPieceFromCacheEvent(index, this@PeerSession)
            eventBus.offer(QueueName.PIECE_CACHE, event)
        }
    }
    private val downloadProtocol = DownloadProtocol(pieceCount, connection, clientStatus, eventPublisher)
    private val uploadProtocol = UploadProtocol(connection, clientStatus, eventPublisher)

    val peer: Peer?
        get() = connection.peer

    suspend fun start(localPiecesStatus: PiecesStatus) {
        if (!localPiecesStatus.isEmpty) {
            connection.writeMessage(BitFieldMessage(localPiecesStatus))
        }
        connection.readAndWrite(pieceCount) { message ->
            when (message) {
                is ChokeMessage -> chokedByRemote()
                is UnchokeMessage -> unchokeByRemote()
                is InterestedMessage -> interestedByRemote()
                is UninterestedMessage -> uninterestedByRemote()
                is BitFieldMessage -> bitField(message)
                is HaveMessage -> have(message)
                is RequestMessage -> request(message)
                is PieceMessage -> piece(message)
                is CancelMessage -> cancel(message)
                else -> throw IllegalStateException("unexpected message $message")
            }
        }
        // handle send request event
        // handle cancel request event
        // handle piece cache loaded event
    }

    fun requestMore(index: Int) {
        // process in connection event loop
    }

    fun requestLess(pieceRequest: PieceRequest) {
    }

    fun pieceCacheLoaded(index: Int, cache: PieceCache) {
    }

    private fun publish(message: PeerMessage) {
        eventBus.offer(QueueName.TRANSMISSION, PeerMessageEvent(message, this))
    }

    private fun chokedByRemote() {
        downloadProtocol.chokedByRemote()
        uploadProtocol.chokedByRemote()
    }

    private fun unchokeByRemote() {
        downloadProtocol.unchokeByRemote()
        uploadProtocol.unchokeByRemote()
    }

    private fun interestedByRemote() {
        uploadProtocol.interestedByRemote()
    }

    private fun uninterestedByRemote() {
        uploadProtocol.uninterestedByRemote()
    }

    private fun bitField(message: BitFieldMessage) {
        downloadProtocol.bitField(message.piecesStatus)
        publish(message)
    }

    private fun have(message: HaveMessage) {
        downloadProtocol.have(message.index)
        publish(message)
    }

    private fun request(message: RequestMessage) {
        uploadProtocol.request(message.index, message.begin, message.length)
    }

    private fun piece(message: PieceMessage) {
        downloadProtocol.piece(message.index, message.begin, message.piece)
    }

    private fun cancel(message: CancelMessage) {
        uploadProtocol.cancel(message.index, message.begin, message.length)
    }

    fun close() {
        connection.close()
    }
}