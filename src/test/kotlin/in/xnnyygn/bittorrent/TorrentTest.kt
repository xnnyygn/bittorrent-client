package `in`.xnnyygn.bittorrent

import org.junit.Ignore
import org.junit.Test
import java.io.File

class TorrentTest {
    @Test
    @Ignore
    fun testFromFile() {
        val path = "/path/to/torrent/file"
        val torrent = Torrent.fromFile(File(path))
        println(torrent)
    }
}