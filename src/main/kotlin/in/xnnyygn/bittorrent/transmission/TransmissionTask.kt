package `in`.xnnyygn.bittorrent.transmission

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import `in`.xnnyygn.bittorrent.file.FileWorkerFactory
import `in`.xnnyygn.bittorrent.file.PieceCacheWorker
import `in`.xnnyygn.bittorrent.net.Acceptor
import `in`.xnnyygn.bittorrent.net.ConnectionsWorker
import `in`.xnnyygn.bittorrent.net.ConnectorWorker
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.tracker.Peer
import `in`.xnnyygn.bittorrent.tracker.StaticTracker
import `in`.xnnyygn.bittorrent.tracker.TrackerWorker
import `in`.xnnyygn.bittorrent.transmission.download.DownloadWorker
import `in`.xnnyygn.bittorrent.transmission.upload.UploadWorker
import `in`.xnnyygn.bittorrent.worker.WorkerSet
import `in`.xnnyygn.bittorrent.worker.WorkerSetBuilder
import io.netty.channel.nio.NioEventLoopGroup

class TransmissionTaskFactory(
    private val torrent: Torrent,
    private val basePath: String,
    private val selfPeer: Peer
) {
    private val config = TransmissionConfig()

    fun create(): TransmissionTask {
        val workerSetBuilder = WorkerSetBuilder()
        val tracker = StaticTracker(torrent.infoHash, selfPeer, 3, emptyList())
        val clientStatus = ClientStatus(0, 0, torrent.totalLength)
        workerSetBuilder.register("tracker", TrackerWorker(tracker, clientStatus, config))
        val workerNioEventLoopGroup = NioEventLoopGroup()
        workerSetBuilder.register("connector", ConnectorWorker(torrent.infoHash, workerNioEventLoopGroup, config))

        // TODO local from disk
        val localPiecesStatus = BitSetPiecesStatus(torrent.pieceCount)
        val fileWorkerFactory = FileWorkerFactory(basePath)
        val fileWorkerSet = fileWorkerFactory.create(torrent)
        val fileDispatcher = fileWorkerSet.dispatcher
        workerSetBuilder.register(
            "connections",
            ConnectionsWorker(torrent, config, localPiecesStatus.duplicate(), clientStatus)
        )
        workerSetBuilder.register(
            "downloader",
            DownloadWorker(torrent.info.pieces, config, localPiecesStatus.duplicate(), fileDispatcher)
        )
        workerSetBuilder.register("uploader", UploadWorker(config))
        workerSetBuilder.register("piece-cache", PieceCacheWorker(fileDispatcher))
        workerSetBuilder.addAll(fileWorkerSet.workers)
        workerSetBuilder.register("client-progress", ClientProgressWorker(localPiecesStatus.duplicate()))
        val workerSet = workerSetBuilder.build()
        val acceptor = Acceptor(config.port, torrent.infoHash, selfPeer.id, workerSet.eventBus, workerNioEventLoopGroup)
        return TransmissionTask(workerSet, acceptor, workerNioEventLoopGroup)
    }
}

class TransmissionTask internal constructor(
    private val workerSet: WorkerSet,
    private val acceptor: Acceptor,
    private val workerNioEventLoopGroup: NioEventLoopGroup
) {
    fun start() {
        workerSet.startAll()
        acceptor.start()
    }

    fun stop() {
        workerSet.shutdown()
        acceptor.stop()
        workerNioEventLoopGroup.shutdownGracefully()
        workerSet.awaitTermination(3000L)
        workerSet.stopAll()
    }
}