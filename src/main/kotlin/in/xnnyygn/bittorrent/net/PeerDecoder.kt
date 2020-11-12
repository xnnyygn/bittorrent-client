package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.transmission.download.DownloadedPieceSlice
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder

class DecoderContext(private val out: MutableList<Any>) {
    fun addMessage(message: PeerMessage) {
        out.add(message)
    }
}

internal interface DecoderState {
    fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState
}

internal object StartDecoderState : DecoderState {
    override fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (buffer.readableBytes() < 4) {
            return this
        }
        val length = buffer.getInt(buffer.readerIndex())
        return if (length == 0) {
            context.addMessage(KeepAliveMessage)
            decode(buffer, context)
        } else {
            LengthDecoderState(length, this).decode(buffer, context)
        }
    }

    override fun toString(): String = "StartDecoderState"
}

private class LengthDecoderState(private val length: Int, private val start: StartDecoderState) : DecoderState {
    override fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (buffer.readableBytes() < 1) {
            return this
        }
        return when (val type = buffer.getByte(buffer.readerIndex())) {
            PeerMessageTypes.CHOKE -> addAndRestart(ChokeMessage, buffer, context)
            PeerMessageTypes.UNCHOKE -> addAndRestart(UnchokeMessage, buffer, context)
            PeerMessageTypes.INTERESTED -> addAndRestart(
                InterestedMessage, buffer, context)
            PeerMessageTypes.UNINTERESTED -> addAndRestart(
                UninterestedMessage, buffer, context)
            PeerMessageTypes.BITFIELD -> BitFieldDecoderState(length - 1, start).decode(buffer, context)
            PeerMessageTypes.HAVE -> HaveDecoderState(length - 1, start).decode(buffer, context)
            PeerMessageTypes.REQUEST -> RequestDecoderState(length - 1, start).decode(buffer, context)
            PeerMessageTypes.PIECE -> PieceDecoderState(length - 1, start).decode(buffer, context)
            PeerMessageTypes.CANCEL -> CancelDecoderState(length - 1, start).decode(buffer, context)
            else -> throw IllegalStateException("unexpected type $type")
        }
    }

    private fun addAndRestart(message: PeerMessage, buffer: ByteBuf, context: DecoderContext): DecoderState {
        context.addMessage(message)
        return start.decode(buffer, context)
    }

    override fun toString(): String = "LengthDecoderState(length=$length)"
}

internal class CancelDecoderState(payloadLength: Int, start: StartDecoderState) :
    AbstractRequestDecoderState(payloadLength, start) {

    override fun makeMessage(index: Int, begin: Int, length: Int): PeerMessage {
        return CancelMessage(index, begin, length)
    }

    override fun toString(): String {
        return "CancelDecoderState(index=$index, begin=$begin)"
    }
}

private class PieceDecoderState(private val payloadLength: Int, private val start: StartDecoderState) : DecoderState {
    private var index: Int? = null
    private var begin: Int? = null

    override fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (index == null) {
            if (buffer.readableBytes() < 4) {
                return this
            }
            index = buffer.readInt()
        }
        return decodeBegin(buffer, context)
    }

    private fun decodeBegin(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (begin == null) {
            if (buffer.readableBytes() < 4) {
                return this
            }
            begin = buffer.readInt()
        }
        return decodePiece(buffer, context)
    }

    private fun decodePiece(buffer: ByteBuf, context: DecoderContext): DecoderState {
        val bytesInBuffer = buffer.readableBytes()
        val length = payloadLength - 8
        if (bytesInBuffer < length) {
            return this
        }
        val piece = buffer.readRetainedSlice(length)
        context.addMessage(
            PieceMessage(
                index!!, begin!!,
                DownloadedPieceSlice(begin!!, piece)
            )
        )
        return start.decode(buffer, context)
    }

    override fun toString(): String {
        return "PieceDecoderState(index=$index, begin=$begin)"
    }
}

internal abstract class AbstractRequestDecoderState(payloadLength: Int, protected val start: StartDecoderState) :
    DecoderState {
    protected var index: Int? = null
    protected var begin: Int? = null

    init {
        check(payloadLength == 12) { "payload length not match" }
    }

    override fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (index == null) {
            if (buffer.readableBytes() < 4) {
                return this
            }
            index = buffer.readInt()
        }
        return decodeBegin(buffer, context)
    }

    private fun decodeBegin(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (begin == null) {
            if (buffer.readableBytes() < 4) {
                return this
            }
            begin = buffer.readInt()
        }
        return decodeLength(buffer, context)
    }

    private fun decodeLength(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (buffer.readableBytes() < 4) {
            return this
        }
        val length = buffer.readInt()
        context.addMessage(makeMessage(index!!, begin!!, length))
        return start.decode(buffer, context)
    }

    protected abstract fun makeMessage(index: Int, begin: Int, length: Int): PeerMessage
}

internal class RequestDecoderState(payloadLength: Int, start: StartDecoderState) :
    AbstractRequestDecoderState(payloadLength, start) {

    override fun makeMessage(index: Int, begin: Int, length: Int): PeerMessage {
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

    override fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState {
        if (buffer.readableBytes() < 4) {
            return this
        }
        val index = buffer.getInt(buffer.readerIndex())
        context.addMessage(HaveMessage(index))
        return start.decode(buffer, context)
    }

    override fun toString(): String = "HaveDecoderState()"
}

internal class BitFieldDecoderState(private val payloadLength: Int, private val start: StartDecoderState) :
    DecoderState {
    private val bytes = ByteArray(payloadLength)
    private var offset = 0

    override fun decode(buffer: ByteBuf, context: DecoderContext): DecoderState {
        val bytesInBuffer = buffer.readableBytes()
        if (bytesInBuffer == 0) {
            return this
        }
        val length = (payloadLength - offset).coerceAtMost(bytesInBuffer)
        buffer.readBytes(bytes, offset, length)
        offset += length
        if (offset < payloadLength) {
            return this
        }
        context.addMessage(BitFieldMessage(bytes))
        return start.decode(buffer, context)
    }

    override fun toString(): String {
        return "BitFieldDecoderState(payloadLength=$payloadLength, offset=$offset)"
    }
}

class PeerDecoder : ByteToMessageDecoder() {
    private var state: DecoderState = StartDecoderState

    override fun decode(ctx: ChannelHandlerContext?, buffer: ByteBuf?, out: MutableList<Any>?) {
        state = state.decode(buffer!!, DecoderContext(out!!))
    }
}