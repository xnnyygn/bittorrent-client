package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import java.nio.ByteBuffer
import java.util.BitSet

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

// TODO change type of piecesStatus from PieceStatus to ByteArray
data class BitFieldMessage(val piecesStatus: PiecesStatus) : PeerMessage() {
    constructor(bytes: ByteArray) : this(BitSetPiecesStatus(BitSet.valueOf(bytes), 0))
}

data class RequestMessage(val index: Int, val begin: Long, val length: Long) : PeerMessage() {
    constructor(pieceRequest: PieceRequest) : this(pieceRequest.index, pieceRequest.begin, pieceRequest.length)
}

// TODO change type of piece from ByteBuffer to ByteArray
data class PieceMessage(val index: Int, val begin: Long, val piece: ByteBuffer) : PeerMessage() {
    constructor(index: Int, begin: Long, piece: ByteArray) : this(index, begin, ByteBuffer.wrap(piece))
}

data class CancelMessage(val index: Int, val begin: Long, val length: Long) : PeerMessage() {
    constructor(pieceRequest: PieceRequest) : this(pieceRequest.index, pieceRequest.begin, pieceRequest.length)
}