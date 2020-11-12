package `in`.xnnyygn.bittorrent.tracker

// peer id: byte array
// peer(id, ...)
// active peer, connected peer(isValid)
data class Peer(val id: ByteArray, val ip: String, val port: Int) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Peer) return false
        if (!id.contentEquals(other.id)) return false
        return true
    }

    override fun hashCode(): Int {
        return id.contentHashCode()
    }
}