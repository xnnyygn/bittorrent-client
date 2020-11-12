package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.PieceSlice
import `in`.xnnyygn.bittorrent.file.checkOffsetAndLength
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import java.nio.channels.FileChannel
import java.security.MessageDigest

class DownloadedPieceSlice(
    override val offsetInPiece: Int,
    private val underlying: ByteBuf
) : PieceSlice {

    override val length: Int
        get() = underlying.readableBytes()

    fun update(digest: MessageDigest) {
        digest.update(underlying.nioBuffer())
    }

    override fun writeToChannel(channel: Channel) {
        channel.write(underlying.slice())
    }

    fun writeToChannel(channel: Channel, offset: Int, length: Int) {
        checkOffsetAndLength(offset, length, this.length)
        channel.write(underlying.slice(offset, length))
    }

    fun writeToFile(fileChannel: FileChannel, offset: Int, length: Int) {
        checkOffsetAndLength(offset, length, this.length)
        fileChannel.write(underlying.nioBuffer(offset, length))
    }

    fun release() {
        underlying.release()
    }
}