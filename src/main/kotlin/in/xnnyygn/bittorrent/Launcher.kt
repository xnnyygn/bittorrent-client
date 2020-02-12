package `in`.xnnyygn.bittorrent

import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage <torrent-file-path>")
        return
    }
    val torrent = Torrent.fromFile(File(args[0]))
    println(torrent)
}