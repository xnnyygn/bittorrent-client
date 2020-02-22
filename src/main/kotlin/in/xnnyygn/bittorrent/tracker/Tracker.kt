package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.peer.Peer

internal enum class PeerEvent {
    STARTED,
    COMPLETED,
    STOPPED,
    EMPTY
}

internal data class TrackerRequest(
    val infoHash: ByteArray,
    val peerId: ByteArray,
    val ip: String? = null,
    val port: Int,
    val uploaded: Long,
    val downloaded: Long,
    val left: Long,
    val event: PeerEvent = PeerEvent.EMPTY
)

data class TrackerResponse(val interval: Int, val peers: List<Peer>)

class TrackerException(msg: String, cause: Throwable?) : RuntimeException(msg, cause) {
    constructor(msg: String) : this(msg, null)
}

interface Tracker {
    suspend fun findPeers(uploaded: Long, downloaded: Long, left: Long, firstRequest: Boolean = false): TrackerResponse

    suspend fun stop(uploaded: Long, downloaded: Long, left: Long)
}

abstract class AbstractTracker(private val infoHash: ByteArray, private val selfPeer: Peer) : Tracker {
    override suspend fun findPeers(
        uploaded: Long,
        downloaded: Long,
        left: Long,
        firstRequest: Boolean
    ): TrackerResponse {
        val event = when {
            left == 0L -> PeerEvent.COMPLETED
            firstRequest -> PeerEvent.STARTED
            else -> PeerEvent.EMPTY
        }
        return send(makeRequest(uploaded, downloaded, left, event))
    }

    private fun makeRequest(uploaded: Long, downloaded: Long, left: Long, event: PeerEvent) = TrackerRequest(
        infoHash,
        selfPeer.id,
        selfPeer.ip,
        selfPeer.port,
        uploaded,
        downloaded,
        left,
        event
    )

    override suspend fun stop(uploaded: Long, downloaded: Long, left: Long) {
        send(makeRequest(uploaded, downloaded, left, PeerEvent.STOPPED))
    }

    internal abstract suspend fun send(request: TrackerRequest): TrackerResponse
}