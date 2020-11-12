package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessReference
import `in`.xnnyygn.bittorrent.process.StringProcessName
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import io.netty.channel.nio.NioEventLoopGroup

class TransmissionTask(
    private val torrent: Torrent,
    private val transmissionConfig: TransmissionConfig
) : Process() {
    override fun start(context: ProcessContext, parent: ProcessReference) {
        // TODO load files
        // TODO load manifest file
        context.startChild(
            StringProcessName("mergedFile"),
            MergedFileProcess()
        )
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        when (event) {
            is MergedFileInitializedEvent -> {
                context.startChild(
                    StringProcessName("downloader"),
                    DownloaderProcess(
                        event.piecesStatus.duplicate(),
                        event.uncheckedPieceSaver
                    )
                )
                context.startChild(
                    StringProcessName("uploader"),
                    UploaderProcess(sender)
                )
                val connections = context.startChild(
                    StringProcessName("connections"),
                    ConnectionsProcess(event.piecesStatus.duplicate())
                )
                sender.send(
                    RegisterPiecesStatusListenersEvent(
                        listOf(connections)
                    ), context.self
                )
            }
            is PiecesStatusListenersRegisteredEvent -> {
                val connections = event.listeners[0]
                context.startChild(
                    StringProcessName("acceptorInitializer"),
                    AcceptorInitializer(
                        transmissionConfig.port,
                        NioEventLoopGroup(),
                        connections
                    )
                )
            }
            is AcceptorInitializedEvent -> {
                context.startChild(
                    StringProcessName("acceptor"),
                    AcceptorProcess(event.bossNioEventLoopGroup)
                )
                val connector = context.startChild(
                    StringProcessName(
                        "connector"
                    ), ConnectorProcess(event.connections)
                )
                context.startChild(
                    StringProcessName("tracker"),
                    TrackerProcess(connector)
                )
            }
        }
    }
}