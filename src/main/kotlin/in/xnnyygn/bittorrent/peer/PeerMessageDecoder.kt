package `in`.xnnyygn.bittorrent.peer

import java.nio.ByteBuffer

internal interface DecoderState {
    fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState
}

internal object StartDecoderState : DecoderState {
    override fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (buffer.remaining() < 4) {
            return this
        }
        val length = buffer.getInt()
        return if (length == 0) {
            messages.add(KeepAliveMessage)
            decode(buffer, messages)
        } else {
            LengthDecoderState(length, this).decode(buffer, messages)
        }
    }

    override fun toString(): String = "StartDecoderState"
}

private class LengthDecoderState(private val length: Int, private val start: StartDecoderState) : DecoderState {
    override fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (buffer.remaining() < 1) {
            return this
        }
        return when (val type = buffer.get()) {
            PeerMessageTypes.CHOKE -> addAndRestart(ChokeMessage, buffer, messages)
            PeerMessageTypes.UNCHOKE -> addAndRestart(UnchokeMessage, buffer, messages)
            PeerMessageTypes.INTERESTED -> addAndRestart(InterestedMessage, buffer, messages)
            PeerMessageTypes.UNINTERESTED -> addAndRestart(UninterestedMessage, buffer, messages)
            PeerMessageTypes.BITFIELD -> BitFieldDecoderState(length - 1, start).decode(buffer, messages)
            PeerMessageTypes.HAVE -> HaveDecoderState(length - 1, start).decode(buffer, messages)
            PeerMessageTypes.REQUEST -> RequestDecoderState(length - 1, start).decode(buffer, messages)
            PeerMessageTypes.PIECE -> PieceDecoderState(length - 1, start).decode(buffer, messages)
            PeerMessageTypes.CANCEL -> CancelDecoderState(length - 1, start).decode(buffer, messages)
            else -> throw IllegalStateException("unexpected type $type")
        }
    }

    private fun addAndRestart(
        message: PeerMessage,
        buffer: ByteBuffer,
        messages: MutableList<PeerMessage>
    ): DecoderState {
        messages.add(message)
        return start.decode(buffer, messages)
    }

    override fun toString(): String = "LengthDecoderState(length=$length)"
}

internal class CancelDecoderState(payloadLength: Int, start: StartDecoderState) :
    AbstractRequestDecoderState(payloadLength, start) {
    override fun makeMessage(index: Int, begin: Long, length: Long): PeerMessage {
        return CancelMessage(index, begin, length)
    }

    override fun toString(): String {
        return "CancelDecoderState(index=$index, begin=$begin)"
    }
}

private class PieceDecoderState(payloadLength: Int, private val start: StartDecoderState) : DecoderState {
    private var index: Int? = null
    private var begin: Long? = null
    private val piece = ByteArray(1 shl 16)
    private var offset = 0

    init {
        check(payloadLength == (4 + 8 + (1 shl 16))) { "payload length of piece message not match" }
    }

    override fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (index == null) {
            if (buffer.remaining() < 4) {
                return this
            }
            index = buffer.getInt()
        }
        return decodeBegin(buffer, messages)
    }

    private fun decodeBegin(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (begin == null) {
            if (buffer.remaining() < 8) {
                return this
            }
            begin = buffer.getLong()
        }
        return decodePiece(buffer, messages)
    }

    private fun decodePiece(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        val bytesInBuffer = buffer.remaining()
        if (bytesInBuffer == 0) {
            return this
        }
        // TODO extract function
        val bytesToTransfer = ((1 shl 16) - offset).coerceAtMost(bytesInBuffer)
        buffer.get(piece, offset, bytesToTransfer)
        offset += bytesToTransfer
        if (offset < (1 shl 16)) {
            return this
        }
        messages.add(PieceMessage(index!!, begin!!, piece))
        return start.decode(buffer, messages)
    }

    override fun toString(): String {
        return "PieceDecoderState(index=$index, begin=$begin, offset=$offset)"
    }
}

internal abstract class AbstractRequestDecoderState(payloadLength: Int, protected val start: StartDecoderState) :
    DecoderState {
    protected var index: Int? = null
    protected var begin: Long? = null

    init {
        check(payloadLength == 20) { "payload length not match" }
    }

    override fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (index == null) {
            if (buffer.remaining() < 4) {
                return this
            }
            index = buffer.getInt()
        }
        return decodeBegin(buffer, messages)
    }

    private fun decodeBegin(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (begin == null) {
            if (buffer.remaining() < 8) {
                return this
            }
            begin = buffer.getLong()
        }
        return decodeLength(buffer, messages)
    }

    private fun decodeLength(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (buffer.remaining() < 8) {
            return this
        }
        val length = buffer.getLong()
        messages.add(makeMessage(index!!, begin!!, length))
        return start.decode(buffer, messages)
    }

    protected abstract fun makeMessage(index: Int, begin: Long, length: Long): PeerMessage
}

internal class RequestDecoderState(payloadLength: Int, start: StartDecoderState) :
    AbstractRequestDecoderState(payloadLength, start) {
    override fun makeMessage(index: Int, begin: Long, length: Long): PeerMessage {
        return RequestMessage(index, begin, length)
    }

    override fun toString(): String {
        return "RequestDecoderState(index=$index, begin=$begin)"
    }
}

internal class HaveDecoderState(payloadLength: Int, private val start: StartDecoderState) : DecoderState {
    init {
        check(payloadLength == 4) { "payload length of have message must be 4" }
    }

    override fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        if (buffer.remaining() < 4) {
            return this
        }
        val index = buffer.getInt()
        messages.add(HaveMessage(index))
        return start.decode(buffer, messages)
    }

    override fun toString(): String = "HaveDecoderState()"
}

internal class BitFieldDecoderState(private val payloadLength: Int, private val start: StartDecoderState) :
    DecoderState {
    private val bitSet = ByteArray(payloadLength)
    private var offset = 0

    override fun decode(buffer: ByteBuffer, messages: MutableList<PeerMessage>): DecoderState {
        val bytesInBuffer = buffer.remaining()
        if (bytesInBuffer == 0) {
            return this
        }
        val bytesToTransfer = (payloadLength - offset).coerceAtMost(bytesInBuffer)
        buffer.get(bitSet, offset, bytesToTransfer)
        offset += bytesToTransfer
        if (offset < payloadLength) {
            return this
        }
        messages.add(BitFieldMessage(bitSet))
        return start.decode(buffer, messages)
    }

    override fun toString(): String {
        return "BitFieldDecoderState(payloadLength=$payloadLength, offset=$offset)"
    }
}

// TODO rename to reader
class PeerMessageDecoder(private val socket: PeerSocket) {
    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(4 + 1 + 8 + 8 + (1 shl 16))
    private var state: DecoderState = StartDecoderState

    suspend fun decode(): List<PeerMessage> {
        socket.read(buffer)
        buffer.flip() // to read mode
        val messages = mutableListOf<PeerMessage>()
        state = state.decode(buffer, messages)
        buffer.compact() // to write mode
        return messages
    }
}