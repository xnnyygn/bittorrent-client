package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.process.ProcessSystemBootstrap
import `in`.xnnyygn.bittorrent.process.StringProcessName
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig

private fun parseTorrent(): Torrent {
    TODO()
}

fun main() {
    val torrent: Torrent = parseTorrent()
    val bootstrap = ProcessSystemBootstrap()
    bootstrap.addChild(
        StringProcessName("transmissionTask"),
        TransmissionTask(torrent, TransmissionConfig())
    )
    bootstrap.start()
}
