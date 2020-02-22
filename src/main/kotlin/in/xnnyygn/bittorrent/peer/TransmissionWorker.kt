package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.worker.AbstractWorker
import kotlinx.coroutines.CoroutineDispatcher

private class GlobalDownloadStrategy(pieceCount: Int, private val piecesStatus: PiecesStatus) {
    private val pieceMap = List(pieceCount) { mutableSetOf<PeerSession>() }
    private val requestingMap = mutableMapOf<Int, MutableSet<PeerSession>>()

    fun handlePeerMessageEvent(event: PeerMessageEvent) {
        when (val message = event.message) {
            is BitFieldMessage -> message.piecesStatus.pieces().forEach { index ->
                pieceMap[index].add(event.session)
            }
            is HaveMessage -> pieceMap[message.index].add(event.session)
        }
    }

    fun collectRequest(event: SendRequestToRemoteEvent) {
        val request = event.pieceRequest
        requestingMap.getOrPut(request.index) { mutableSetOf() }.add(event.session)
    }

    fun requestMore(maxRequests: Int) {
        var requests = 0
        for (index in piecesStatus.missingPieces()) {
            for (session in pieceMap[index]) {
                if (canRequest(session)) {
                    session.requestMore(index)
                    if (requests++ > maxRequests) {
                        return
                    }
                }
            }
        }
    }

    fun requestLess() {
    }

    private fun canRequest(session: PeerSession): Boolean {
        // find session's request
        TODO()
    }
}

private class SessionList(
    private val pieceCount: Int,
    private val clientStatus: ClientStatus,
    private val eventBus: EventBus
) {
    private val set = mutableSetOf<PeerSession>()

    suspend fun addAndStart(connection: PeerConnection, localPiecesStatus: PiecesStatus) {
        val session = PeerSession(pieceCount, connection, clientStatus, eventBus)
        set.add(session)
        try {
            session.start(localPiecesStatus)
        } finally {
            closeAndRemove(session)
        }
    }

    fun closeAndRemove(session: PeerSession) {
        session.close()
        session.peer?.let { peer ->
            eventBus.offer(QueueName.HANDSHAKE, PeerDisconnectedEvent(peer))
        }
        set.remove(session)
    }
}

class TransmissionWorker(
    pieceCount: Int,
    private val localPiecesStatus: PiecesStatus,
    clientStatus: ClientStatus
) : AbstractWorker(QueueName.TRANSMISSION) {

    private val sessionList =
        SessionList(pieceCount, clientStatus, eventBus)
    private val globalDownloadStrategy =
        GlobalDownloadStrategy(pieceCount, localPiecesStatus)

    override suspend fun handle(event: Event) {
        when (event) {
//            null -> globalDownloadStrategy.requestMore(3)
            is PeerConnectionEvent -> sessionList.addAndStart(event.connection, localPiecesStatus)
            is PeerMessageEvent -> globalDownloadStrategy.handlePeerMessageEvent(event)
            is SendRequestToRemoteEvent -> globalDownloadStrategy.collectRequest(event)
        }
    }
}