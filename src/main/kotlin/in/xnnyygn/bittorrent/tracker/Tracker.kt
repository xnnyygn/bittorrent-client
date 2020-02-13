package `in`.xnnyygn.bittorrent.tracker

import java.lang.RuntimeException

enum class PeerEvent {
    STARTED,
    COMPLETED,
    STOPPED,
    EMPTY
}

data class FindPeerRequest(
    val infoHash: ByteArray,
    val peerId: ByteArray,
    val ip: String? = null,
    val port: Int,
    val uploaded: Int,
    val downloaded: Int,
    val left: Int,
    val event: PeerEvent = PeerEvent.EMPTY
)

data class Peer(val id: ByteArray, val ip: String, val port: Int)

data class FindPeerResponse(val interval: Int, val peers: List<Peer>)

class FindPeerException(msg: String, cause: Throwable?) : RuntimeException(msg, cause) {
    constructor(msg: String) : this(msg, null)
}

interface Tracker {
    fun findPeers(request: FindPeerRequest): FindPeerResponse
}

class MockTracker(private val response: FindPeerResponse) :
    Tracker {
    override fun findPeers(request: FindPeerRequest) = response
}