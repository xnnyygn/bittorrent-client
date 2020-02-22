package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.peer.Peer

class StaticTracker(
    infoHash: ByteArray,
    selfPeer: Peer,
    private val interval: Int,
    private val peers: List<Peer>
) : AbstractTracker(infoHash, selfPeer) {
    override suspend fun send(request: TrackerRequest) = TrackerResponse(interval, peers)
}