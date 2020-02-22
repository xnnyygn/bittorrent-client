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
    suspend fun findPeers(clientStatus: ClientStatus, started: Boolean = false): TrackerResponse

    suspend fun stop(clientStatus: ClientStatus)
}

abstract class AbstractTracker(private val infoHash: ByteArray, private val selfPeer: Peer) : Tracker {
    override suspend fun findPeers(clientStatus: ClientStatus, started: Boolean): TrackerResponse {
        val event = when {
            clientStatus.isCompleted -> PeerEvent.COMPLETED
            started -> PeerEvent.STARTED
            else -> PeerEvent.EMPTY
        }
        return send(makeRequest(clientStatus, event))
    }

    private fun makeRequest(clientStatus: ClientStatus, event: PeerEvent) = TrackerRequest(
        infoHash,
        selfPeer.id,
        selfPeer.ip,
        selfPeer.port,
        clientStatus.uploaded,
        clientStatus.downloaded,
        clientStatus.left,
        event
    )

    override suspend fun stop(clientStatus: ClientStatus) {
        send(makeRequest(clientStatus, PeerEvent.STOPPED))
    }

    internal abstract suspend fun send(request: TrackerRequest): TrackerResponse
}