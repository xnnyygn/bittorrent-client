package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.TorrentInfoFile
import `in`.xnnyygn.bittorrent.transmission.download.DownloadedPiece
import java.io.File

data class FileWorkerSet(val dispatcher: FileDispatcher, val workers: List<FileWorker>)

class FileWorkerFactory(
    private val basePath: String
) {
    fun create(torrent: Torrent): FileWorkerSet {
        val files = torrent.info.files
        if (files.size == 1) {
            return createForSingleFile(torrent.info.name, torrent.info.pieceLength, files[0].length)
        }
        return createForMultipleFile(torrent.info.name, torrent.info.files, torrent.info.pieceLength)
    }

    private fun createForSingleFile(name: String, pieceLength: Int, length: Long): FileWorkerSet {
        val fileWorker = FileWorker(basePath + name)
        val workerMap = mutableMapOf<Int, List<FileRegionWorker>>()
        var pieceIndex = 0
        var position = 0L
        while (position < length) {
            val regionLength: Int =
                if (length - position > pieceLength) pieceLength
                else (length - position).toInt()
            workerMap[pieceIndex] = listOf(
                FileRegionWorker(0, position, regionLength, fileWorker)
            )
            pieceIndex++
            position += pieceLength
        }
        return FileWorkerSet(FileDispatcher(workerMap), listOf(fileWorker))
    }

    private fun createForMultipleFile(
        directoryName: String,
        files: List<TorrentInfoFile>,
        pieceLength: Int
    ): FileWorkerSet {
        val workerMap = mutableMapOf<Int, MutableList<FileRegionWorker>>()
        val workers = mutableListOf<FileWorker>()
        var position = 0L
        for (file in files) {
            val path = basePath + directoryName + File.pathSeparator + file.path
            val fileWorker = FileWorker(path)
            workers.add(fileWorker)
            val fileStart = position
            val fileEnd = position + file.length
            var pieceIndex = (position / pieceLength).toInt()
            val firstRegionLength = regionLength(pieceIndex, pieceLength, fileStart, fileEnd)
            workerMap.getOrPut(pieceIndex) { mutableListOf() }.add(
                FileRegionWorker(
                    (position % pieceLength).toInt(),
                    0,
                    firstRegionLength,
                    fileWorker
                )
            )
            position += firstRegionLength
            while (position < fileEnd) {
                pieceIndex++
                val regionLength = regionLength(pieceIndex, pieceLength, fileStart, fileEnd)
                workerMap.getOrPut(pieceIndex) { mutableListOf() }.add(
                    FileRegionWorker(
                        0,
                        position - fileStart,
                        regionLength,
                        fileWorker
                    )
                )
                position += regionLength
            }
        }
        return FileWorkerSet(FileDispatcher(workerMap), workers)
    }

    private fun regionLength(pieceIndex: Int, pieceLength: Int, fileStart: Long, fileEnd: Long): Int {
        val top = pieceLength.toLong() * pieceIndex
        val bottom = pieceLength.toLong() * (pieceIndex + 1)
        return (bottom.coerceAtMost(fileEnd) - top.coerceAtLeast(fileStart)).toInt()
    }
}

internal class FileRegionWorker(
    private val offsetInPiece: Int,
    private val position: Long,
    private val length: Int,
    private val worker: FileWorker
) {
    fun savePieceToFile(piece: DownloadedPiece, referenceCount: Int) {
        worker.savePieceToFile(piece.region(offsetInPiece, length, referenceCount), position)
    }

    fun loadFilePieceSlice(index: Int) {
        worker.loadFilePieceSlice(index, position, length, offsetInPiece)
    }
}