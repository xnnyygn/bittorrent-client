package `in`.xnnyygn.bittorrent

import `in`.xnnyygn.bittorrent.bencode.BEncodeReader
import `in`.xnnyygn.bittorrent.tracker.Peer
import `in`.xnnyygn.bittorrent.transmission.TransmissionTask
import `in`.xnnyygn.bittorrent.transmission.TransmissionTaskFactory
import kotlinx.coroutines.runBlocking
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage <torrent-file-path>")
        return
    }
    val torrent = parseTorrent(args[0])
    val transmissionTask = createTransmissionTask(torrent)
    runBlocking {
        transmissionTask.start()
    }
}

private fun createTransmissionTask(torrent: Torrent): TransmissionTask {
    val transmissionTaskFactory = TransmissionTaskFactory(
        torrent,
        "/tmp",
        Peer(ByteArray(20), "localhost", 6881)
    )
    return transmissionTaskFactory.create()
}

private fun parseTorrent(path: String): Torrent {
    return File(path).inputStream().use { input ->
        val parser = BEncodeReader(input)
        val element = parser.parse()
        Torrent.fromElement(element, parser.infoHash)
    }
}