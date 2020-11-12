package `in`.xnnyygn.bittorrent.transmission.download

import `in`.xnnyygn.bittorrent.file.AbstractMergedPiece
import `in`.xnnyygn.bittorrent.file.AbstractPieceView
import `in`.xnnyygn.bittorrent.file.checkOffsetAndLength
import io.netty.channel.Channel
import io.netty.util.AbstractReferenceCounted
import io.netty.util.ReferenceCounted
import java.nio.channels.FileChannel
import java.security.MessageDigest

class DownloadedPiece(
    override val index: Int,
    slices: List<DownloadedPieceSlice>
) : AbstractMergedPiece<DownloadedPieceSlice>(index, slices) {

    fun region(offset: Int, length: Int, referenceCount: Int): DownloadedPieceRegion {
        val pieceLength = slices.map { it.length }.sum()
        checkOffsetAndLength(offset, length, pieceLength)
        return DownloadedPieceViewForFile(offset, length, ReferenceCountedDownloadedPiece(slices, referenceCount))
    }

    fun hash(): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        for (slice in slices) {
            slice.update(digest)
        }
        return digest.digest()
    }

    fun release() {
        for (slice in slices) {
            slice.release()
        }
    }

    override fun writeToChannel(slice: DownloadedPieceSlice, channel: Channel, sliceOffset: Int, sliceLength: Int) {
        slice.writeToChannel(channel, sliceOffset, sliceLength)
    }
}

private class ReferenceCountedDownloadedPiece(
    val slices: List<DownloadedPieceSlice>,
    referenceCount: Int
) : AbstractReferenceCounted() {
    init {
        setRefCnt(referenceCount)
    }

    override fun touch(hint: Any?): ReferenceCounted {
        return this
    }

    override fun deallocate() {
        for (slice in slices) {
            slice.release()
        }
    }
}

interface DownloadedPieceRegion {
    fun writeToFile(fileChannel: FileChannel)
}

private class DownloadedPieceViewForFile(
    offsetInPiece: Int,
    length: Int,
    private val piece: ReferenceCountedDownloadedPiece
) : AbstractPieceView<DownloadedPieceSlice, FileChannel>(offsetInPiece, length, piece.slices),
    DownloadedPieceRegion {

    override fun writeToFile(fileChannel: FileChannel) {
        writeTo(fileChannel)
        // TODO last piece
        piece.release()
    }

    override fun doWriteTo(slice: DownloadedPieceSlice, sliceOffset: Int, sliceLength: Int, target: FileChannel) {
        slice.writeToFile(target, sliceOffset, sliceLength)
    }
}
