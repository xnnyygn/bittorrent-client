package `in`.xnnyygn.bittorrent.peer

//private class DownloadStrategy(private val pieceCount: Int, private val pieceStatus: PieceStatus) {
//    companion object {
//        const val REQUEST_TIMEOUT = 3000L
//    }
//
//    private val pieceMap = List(pieceCount) { mutableSetOf<PeerSession>() }
//    private val requestingMap = mutableMapOf<Int, MutableSet<PeerSession>>()
//
//    fun chokedByRemote(session: PeerSession) {
//    }
//
//    fun unchokedByRemote(session: PeerSession) {
//        if (!session.interestedByLocal || !canRequest(session)) {
//            return
//        }
//        for (index in pieceStatus.missingPieces()) {
//            if (session.bitField.get(index) && !requestingMap.containsKey(index)) {
//                sendRequest(session, index)
//                return
//            }
//        }
//        for ((index, sessions) in requestingMap) {
//            if (sessions.all { it.requestedButNoResponse(REQUEST_TIMEOUT) }) {
//                sendRequest(session, index)
//            }
//        }
//    }
//
//    fun bitField(bitSet: BitSet, session: PeerSession) {
//        if (bitSet.size() != pieceCount)
//            throw IllegalStateException("expect piece count ${pieceCount}, but was ${bitSet.size()}")
//        session.bitField = bitSet
//        bitSet.stream().forEach { index ->
//            pieceMap[index].add(session)
//        }
//        if (!containsMissingPiece(bitSet)) {
//            return
//        }
//        // send interested if peer contains piece we don't have
//        if (!session.interestedByLocal) {
//            session.interestedByLocal = true
//            session.sendInterested()
//        }
//    }
//
//    fun have(index: Int, session: PeerSession) {
//        require(index in 0 until pieceCount) { "illegal piece index $index" }
//        session.bitField.set(index)
//        // check if we have that piece
//        if (!pieceStatus.hasPiece(index) && canRequest(session)) {
//            sendRequest(session, index)
//        }
//    }
//
//    fun piece(session: PeerSession, index: Int, begin: Long, piece: ByteBuffer) {
//        // task maybe changed
//        if (session.matchPiece(index, begin)) {
//            removeRequest(session, index)
//        }
//        // TODO save to file
//    }
//
//    fun close(session: PeerSession) {
//        session.bitField.stream().forEach { index ->
//            pieceMap[index].remove(session)
//        }
//        val request = session.lastRequestToRemote
//        if (request != null) {
//            removeRequest(session, request.index)
//        }
//    }
//
//    fun requestMore(maxRequests: Int) {
//        var requests = 0
//        for (index in pieceStatus.missingPieces()) {
//            for (session in pieceMap[index]) {
//                if (canRequest(session)) {
//                    sendRequest(session, index)
//                    if (requests++ > maxRequests) {
//                        return
//                    }
//                }
//            }
//        }
//    }
//
//    fun requestLess() {
//        // send cancel
//    }
//
//    private fun containsMissingPiece(bitSet: BitSet): Boolean {
//        for (index in pieceStatus.missingPieces()) {
//            if (bitSet.get(index)) {
//                return true
//            }
//        }
//        return false
//    }
//
//    private fun canRequest(session: PeerSession): Boolean {
//        if (!session.interestedByLocal) {
//            session.interestedByLocal = true
//            session.sendInterested()
//            return false
//        }
//        if (session.chokedByRemote) {
//            return false
//        }
//        val request = session.lastRequestToRemote ?: return true
//        if (System.currentTimeMillis() - request.timestamp <= REQUEST_TIMEOUT) {
//            return false
//        }
//        removeRequest(session, request.index)
//        return true
//    }
//
//    private fun removeRequest(session: PeerSession, index: Int) {
//        session.lastRequestToRemote = null
//        val removed = requestingMap[index]?.remove(session) ?: false
//        if (!removed) {
//            throw IllegalStateException("no request of piece $index")
//        }
//    }
//
//    private fun sendRequest(session: PeerSession, index: Int) {
//        val pieceRequest = PieceRequest(index, 0, 1024, System.currentTimeMillis())
//        requestingMap.getOrPut(index) { mutableSetOf() }.add(session)
//        session.lastRequestToRemote = pieceRequest
//        session.sendRequest(pieceRequest)
//    }
//}
//
//private class UploadStrategy(private val pieceStatus: PieceStatus) {
//    fun chokedByRemote(session: PeerSession) {
//
//    }
//
//    fun unchokeByRemote(session: PeerSession) {
//
//    }
//
//    fun interestedByRemote(session: PeerSession) {
//        if (session.chokedByLocal) {
//            session.chokedByLocal = false
//            session.sendUnchoke()
//        }
//    }
//
//    fun uninterestedByRemote(session: PeerSession) {
//        // cancel sending piece
//    }
//
//    fun request(session: PeerSession, index: Int, begin: Long, length: Long) {
//        // check if we have piece
//        if (!pieceStatus.hasPiece(index)) {
//            return
//        }
//        session.lastRequestFromRemote = PieceRequest(index, begin, length)
//        // TODO send piece
//        // TODO sending queue
//    }
//
//    fun cancel(session: PeerSession, index: Int, begin: Long, length: Long) {
//    }
//
//    fun close(session: PeerSession) {
//    }
//}

//class PeerProtocol(
//    private val pieceCount: Int,
//    private val pieceStatus: PieceStatus
//) {
//    private
//
//    fun add(connection: PeerConnection, eventBus: EventBus): PeerSession {
//        val session = PeerSession(connection, eventBus, pieceCount)
//        if (!pieceStatus.isEmpty()) {
//            session.sendBitField(pieceStatus.toByteArray())
//        }
//        return session
//    }
//
//    fun chokedByRemote(session: PeerSession) {
//        session.chokedByRemote = true
//        downloadStrategy.chokedByRemote(session)
//        uploadStrategy.chokedByRemote(session)
//    }
//
//    fun unchokeByRemote(session: PeerSession) {
//        session.chokedByRemote = false
//        downloadStrategy.unchokedByRemote(session)
//        uploadStrategy.unchokeByRemote(session)
//    }
//
//    fun interestedByRemote(session: PeerSession) {
//        session.interestedByRemote = true
//        uploadStrategy.interestedByRemote(session)
//    }
//
//    fun uninterestedByRemote(session: PeerSession) {
//        session.interestedByRemote = false
//        uploadStrategy.uninterestedByRemote(session)
//    }
//
//    fun bitField(bitSet: BitSet, session: PeerSession) {
//        downloadStrategy.bitField(bitSet, session)
//    }
//
//    fun have(index: Int, session: PeerSession) {
//        downloadStrategy.have(index, session)
//    }
//
//    fun request(session: PeerSession, index: Int, begin: Long, length: Long) {
//        uploadStrategy.request(session, index, begin, length)
//    }
//
//    fun piece(session: PeerSession, index: Int, begin: Long, piece: ByteBuffer) {
//        downloadStrategy.piece(session, index, begin, piece)
//    }
//
//    fun cancel(session: PeerSession, index: Int, begin: Long, length: Long) {
//        uploadStrategy.cancel(session, index, begin, length)
//    }
//
//    fun requestMore() {
//        downloadStrategy.requestMore(3)
//    }
//
//    fun close(session: PeerSession) {
//        downloadStrategy.close(session)
//        uploadStrategy.close(session)
//        session.close()
//    }
//}