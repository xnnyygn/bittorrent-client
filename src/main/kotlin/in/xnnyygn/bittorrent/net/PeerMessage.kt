package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.file.PieceSlice

internal class PeerMessageTypes {
    companion object {
        const val CHOKE: Byte = 0
        const val UNCHOKE: Byte = 1
        const val INTERESTED: Byte = 2
        const val UNINTERESTED: Byte = 3
        const val HAVE: Byte = 4
        const val BITFIELD: Byte = 5
        const val REQUEST: Byte = 6
        const val PIECE: Byte = 7
        const val CANCEL: Byte = 8
    }
}

sealed class PeerMessage

object KeepAliveMessage : PeerMessage()

sealed class NoPayloadPeerMessage(val messageType: Byte) : PeerMessage()

object ChokeMessage : NoPayloadPeerMessage(PeerMessageTypes.CHOKE)

object UnchokeMessage : NoPayloadPeerMessage(PeerMessageTypes.UNCHOKE)

object InterestedMessage : NoPayloadPeerMessage(PeerMessageTypes.INTERESTED)

object UninterestedMessage : NoPayloadPeerMessage(PeerMessageTypes.UNINTERESTED)

data class HaveMessage(val index: Int) : PeerMessage()

data class BitFieldMessage(val bytes: ByteArray) : PeerMessage() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BitFieldMessage) return false

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }
}

sealed class AbstractRequestMessage(val messageType: Byte, val index: Int, val begin: Int, val length: Int) :
    PeerMessage()

class RequestMessage(index: Int, begin: Int, length: Int) :
    AbstractRequestMessage(PeerMessageTypes.REQUEST, index, begin, length)

data class PieceMessage(val index: Int, val begin: Int, val piece: PieceSlice) : PeerMessage()

class CancelMessage(index: Int, begin: Int, length: Int) :
    AbstractRequestMessage(PeerMessageTypes.CANCEL, index, begin, length)