package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.file.PieceSlice
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise

private class PeerMessageWriter(
    private val buffer: ByteBuf,
    private val context: ChannelHandlerContext,
    private val promise: ChannelPromise
) {
    fun length(length: Int): PeerMessageWriter {
        buffer.writeInt(length)
        return this
    }

    fun type(type: Byte): PeerMessageWriter {
        buffer.writeByte(type.toInt())
        return this
    }

    fun putInt(i: Int): PeerMessageWriter {
        buffer.writeInt(i)
        return this
    }

    fun putBytes(bytes: ByteArray): PeerMessageWriter {
        buffer.writeBytes(bytes)
        return this
    }

    fun putPieceSlice(piece: PieceSlice): PeerMessageWriter {
        contextWriteIfNotEmpty()
        piece.writeToChannel(context.channel())
        return this
    }

    private fun contextWriteIfNotEmpty() {
        if (buffer.isReadable) {
            context.write(buffer, promise)
        }
    }

    fun end() {
        contextWriteIfNotEmpty()
        buffer.clear()
    }
}

class PeerEncoder : ChannelOutboundHandlerAdapter() {
    private var buffer: ByteBuf? = null

    override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
        if (msg != null && msg is PeerMessage) {
            val writer = PeerMessageWriter(buffer!!, ctx!!, promise!!)
            when (msg) {
                is KeepAliveMessage -> writer.length(0).end()
                is ChokeMessage -> writeNoPayload(msg, writer)
                is UnchokeMessage -> writeNoPayload(msg, writer)
                is InterestedMessage -> writeNoPayload(msg, writer)
                is UninterestedMessage -> writeNoPayload(msg, writer)
                is BitFieldMessage -> writeBitField(msg, writer)
                is HaveMessage -> writer.length(5).putInt(msg.index).end()
                is RequestMessage -> writeAbstractRequest(msg, writer)
                is PieceMessage -> writePiece(msg, writer)
                is CancelMessage -> writeAbstractRequest(msg, writer)
//                else -> ctx.close()
            }
        } else {
            super.write(ctx, msg, promise)
        }
    }

    private fun writePiece(message: PieceMessage, writer: PeerMessageWriter) {
        val pieceLength = message.piece.length
        writer
            .length(1 + 4 + 8 + pieceLength)
            .type(PeerMessageTypes.BITFIELD)
            .putInt(message.index)
            .putInt(message.begin)
            .putPieceSlice(message.piece)
            .end()
    }

    private fun writeAbstractRequest(message: AbstractRequestMessage, writer: PeerMessageWriter) {
        writer
            .length(4 + 8 + 8 + 1)
            .type(message.messageType)
            .putInt(message.index)
            .putInt(message.begin)
            .putInt(message.length)
            .end()
    }

    private fun writeBitField(message: BitFieldMessage, writer: PeerMessageWriter) {
        val bytes = message.bytes
        writer
            .length(1 + bytes.size)
            .type(PeerMessageTypes.BITFIELD)
            .putBytes(bytes)
            .end()
    }

    private fun writeNoPayload(message: NoPayloadPeerMessage, writer: PeerMessageWriter) {
        writer
            .length(1)
            .type(message.messageType)
            .end()
    }

    override fun handlerAdded(ctx: ChannelHandlerContext?) {
        buffer = ctx!!.alloc().ioBuffer(25, 25)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        buffer?.release()
    }
}