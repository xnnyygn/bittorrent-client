package `in`.xnnyygn.bittorrent.file

import io.netty.channel.Channel

class MergedFilePiece(
    override val index: Int,
    slices: List<FilePieceSlice>
) : AbstractMergedPiece<FilePieceSlice>(index, slices) {

    override fun writeToChannel(slice: FilePieceSlice, channel: Channel, sliceOffset: Int, sliceLength: Int) {
        slice.writeToChannel(channel, sliceOffset, sliceLength)
    }
}