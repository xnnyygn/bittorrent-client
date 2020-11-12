package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.tracker.Peer
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.EventBus
import `in`.xnnyygn.bittorrent.worker.QueueNames
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

data class PostHandshakeConnectionEvent(val connection: PeerConnection) :
    Event

sealed class AbstractHandshakeHandler(private val bufferSize: Int) : ChannelInboundHandlerAdapter() {

    private var done = false
    private var buffer: ByteBuf? = null

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        if (!done && msg != null && msg is ByteBuf) {
            val b = buffer!!
            b.writeBytes(msg)
            msg.release()
            if (b.readableBytes() >= bufferSize) {
                handshake(ctx!!, b)
            }
        } else {
            super.channelRead(ctx, msg)
        }
    }

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        buffer = ctx.alloc().ioBuffer(bufferSize, bufferSize)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext?) {
        buffer?.release()
    }

    protected abstract fun handshake(ctx: ChannelHandlerContext, buffer: ByteBuf)

    protected fun done() {
        done = true
        buffer?.release()
        buffer = null
    }
}

private fun ByteBuf.matchSlice(offset: Int, bytes: ByteArray): Boolean {
    skipBytes(offset)
    for (i in 0 until (bytes.size.coerceAtMost(readableBytes()))) {
        if (readByte() != bytes[i]) {
            return false
        }
    }
    return true
}

class HandshakeFromLocalHandler(
    private val infoHash: ByteArray,
    private val remotePeer: Peer,
    private val listener: (OutgoingPeerConnection) -> Unit
) : AbstractHandshakeHandler(20) {

    override fun channelActive(ctx: ChannelHandlerContext) {
        val buffer: ByteBuf = Unpooled.buffer(48, 48).apply {
            writeByte(19)
            writeBytes("Bittorrent protocol".toByteArray())
            writeLong(0)
            writeBytes(infoHash)
        }
        ctx.write(buffer)
        super.channelActive(ctx)
    }

    override fun handshake(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        if (buffer.matchSlice(0, remotePeer.id)) {
            done()
            listener(OutgoingPeerConnection(remotePeer, ctx.channel()))
        } else {
            ctx.close()
        }
    }
}

class HandshakeFromRemoteHandler(
    private val infoHash: ByteArray,
    private val selfPeerId: ByteArray,
    private val eventBus: EventBus // TODO change to listener
) : AbstractHandshakeHandler(48) {

    override fun handshake(ctx: ChannelHandlerContext, buffer: ByteBuf) {
        if (buffer.matchSlice(28, infoHash)) {
            ctx.writeAndFlush(Unpooled.wrappedBuffer(selfPeerId))
            done()
            eventBus.offer(
                QueueNames.CONNECTIONS,
                PostHandshakeConnectionEvent(IncomingPeerConnection(ctx.channel()))
            )
        } else {
            ctx.close()
        }
    }
}