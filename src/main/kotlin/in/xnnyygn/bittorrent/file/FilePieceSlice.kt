package `in`.xnnyygn.bittorrent.file

import io.netty.channel.Channel
import io.netty.channel.DefaultFileRegion
import java.nio.channels.FileChannel

class FilePieceSlice(
    private val position: Long,
    override val offsetInPiece: Int,
    override val length: Int,
    private val fileChannel: FileChannel
) : PieceSlice {

    override fun writeToChannel(channel: Channel) {
        channel.write(DefaultFileRegion(fileChannel, position, length.toLong()))
    }

    fun writeToChannel(channel: Channel, offset: Int, length: Int) {
        checkOffsetAndLength(offset, length, this.length)
        channel.write(DefaultFileRegion(fileChannel, position + offset, length.toLong()))
    }

    fun toFilePiece(index: Int): FilePiece {
        return FilePiece(index, position, length, fileChannel)
    }
}