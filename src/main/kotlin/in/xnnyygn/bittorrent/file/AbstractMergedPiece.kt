package `in`.xnnyygn.bittorrent.file

import io.netty.channel.Channel

abstract class AbstractMergedPiece<T : PieceSlice>(
    override val index: Int,
    protected val slices: List<T>
) : FilePieceLike {

    override fun slice(offset: Int, length: Int): PieceSlice {
        val pieceLength = slices.map { it.length }.sum()
        check(offset >= 0 || offset < pieceLength || length >= 0 || length <= pieceLength) { "illegal offset $offset or length $length" }
        return PieceViewForChannel(offset, length)
    }

    private inner class PieceViewForChannel(
        override val offsetInPiece: Int,
        override val length: Int
    ) : AbstractPieceView<T, Channel>(offsetInPiece, length, slices), PieceSlice {

        override fun doWriteTo(slice: T, sliceOffset: Int, sliceLength: Int, target: Channel) {
            writeToChannel(slice, target, sliceOffset, sliceLength)
        }

        override fun writeToChannel(channel: Channel) {
            writeTo(channel)
        }
    }

    protected abstract fun writeToChannel(slice: T, channel: Channel, sliceOffset: Int, sliceLength: Int)
}