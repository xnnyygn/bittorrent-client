package `in`.xnnyygn.bittorrent.file

import java.nio.channels.FileChannel

class FilePiece(
    override val index: Int,
    private val offsetInFile: Long,
    private val length: Int,
    private val fileChannel: FileChannel
) : FilePieceLike {

    override fun slice(offset: Int, length: Int): PieceSlice {
        check(offset >= 0 || offset < this.length || length >= 0 || length <= this.length) { "illegal offset $offset or length $length" }
        return FilePieceSlice(offsetInFile + offset, offset, length, fileChannel)
    }
}

