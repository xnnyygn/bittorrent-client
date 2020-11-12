package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.transmission.download.DownloadedPieceRegion
import `in`.xnnyygn.bittorrent.worker.ContextAwareWorker
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.QueueNames
import java.io.Closeable
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

private data class SavePieceToFileEvent(val pieceRegion: DownloadedPieceRegion, val position: Long) :
    Event

private data class LoadFilePieceSliceEvent(
    val pieceIndex: Int,
    val position: Long,
    val length: Int,
    val offsetInPiece: Int
) : Event

data class FilePieceSliceLoadedEvent(val pieceIndex: Int, val pieceSlice: FilePieceSlice) :
    Event

private class File(private val path: String) {
    private var file: RandomAccessFile? = null
    private var channel: FileChannel? = null

    fun open() {
        val f = RandomAccessFile(path, "rw")
        file = f
        channel = f.channel
    }

    fun loadFilePieceSlice(event: LoadFilePieceSliceEvent): FilePieceSlice = ensureChannel { c ->
        FilePieceSlice(event.position, event.offsetInPiece, event.length, c)
    }

    fun savePiece(pieceRegion: DownloadedPieceRegion, position: Long): Unit = ensureChannel { c ->
        c.position(position)
        pieceRegion.writeToFile(c)
    }

    private fun <T> ensureChannel(block: (FileChannel) -> T): T {
        val c = channel ?: throw IllegalStateException("file not open")
        return block(c)
    }

    fun close() {
        closeQuietly(channel)
        closeQuietly(file)
    }

    private fun closeQuietly(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (e: IOException) {
            }
        }
    }
}

// multiple file
class FileWorker(path: String) : ContextAwareWorker() {
    private val file = File(path)

    override fun start() {
        file.open()
    }

    override fun handle(event: Event) {
        when (event) {
            is LoadFilePieceSliceEvent -> doLoadFilePieceSlice(event)
            is SavePieceToFileEvent -> doSavePieceToFile(event.pieceRegion, event.position)
        }
    }

    fun loadFilePieceSlice(pieceIndex: Int, position: Long, length: Int, offsetInPiece: Int) {
        context.submit(LoadFilePieceSliceEvent(pieceIndex, position, length, offsetInPiece))
    }

    private fun doLoadFilePieceSlice(event: LoadFilePieceSliceEvent) {
        val pieceSlice = file.loadFilePieceSlice(event)
        context.offer(QueueNames.PIECE_CACHE, FilePieceSliceLoadedEvent(event.pieceIndex, pieceSlice))
    }

    fun savePieceToFile(pieceRegion: DownloadedPieceRegion, position: Long) {
        context.submit(SavePieceToFileEvent(pieceRegion, position))
    }

    private fun doSavePieceToFile(pieceRegion: DownloadedPieceRegion, position: Long) {
        file.savePiece(pieceRegion, position)
    }

    override fun stop() {
        file.close()
    }
}