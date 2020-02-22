package `in`.xnnyygn.bittorrent

import `in`.xnnyygn.bittorrent.bencode.BEncodeReader
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class TorrentTest {
    @Test
    @Ignore
    fun testFromFile() {
        val path = "/path/to/torrent/file"
        val parser = BEncodeReader(FileInputStream(File(path)))
        println(parser.parse())
        println(parser.infoHash.contentToString())
    }
}