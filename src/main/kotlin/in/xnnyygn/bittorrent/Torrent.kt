package `in`.xnnyygn.bittorrent

import `in`.xnnyygn.bittorrent.bencode.BEncodeElement
import `in`.xnnyygn.bittorrent.bencode.DictionaryElement

data class TorrentInfoFile(val length: Long, val path: String)

class TorrentInfo(val files: List<TorrentInfoFile>, val name: String, val pieceLength: Int, val pieces: ByteArray) {
    override fun toString(): String {
        return "TorrentInfo(files=$files, name='$name', pieceLength=$pieceLength, pieces.size=${pieces.size})"
    }
}

data class Torrent(val announce: String, val info: TorrentInfo, val infoHash: ByteArray) {
    val allFileLength: Long
        get() = info.files.map { it.length }.sum()

    val pieceCount: Int
        get() = (allFileLength / info.pieceLength).toInt()

    companion object {
        fun fromElement(element: BEncodeElement, infoHash: ByteArray): Torrent {
            val dictionary = element as DictionaryElement
            val announce = dictionary.getString("announce")
            val infoElement = dictionary.getDictionary("info")
            return Torrent(announce, buildTorrentInfo(infoElement), infoHash)
        }

        private fun buildTorrentInfo(element: BEncodeElement): TorrentInfo {
            val dictionary = element as DictionaryElement
            val name = dictionary.getString("name")
            val pieceLength = dictionary.getNumber("piece length").toInt()
            val pieces = dictionary.getByteString("pieces").bytes
            val files = collectFiles(dictionary, name)
            return TorrentInfo(files, name, pieceLength, pieces)
        }

        private fun collectFiles(dictionary: DictionaryElement, name: String): List<TorrentInfoFile> {
            if (dictionary.contains("length")) {
                val length = dictionary.getNumber("length")
                return listOf(TorrentInfoFile(length, name))
            }
            val filesElement = dictionary.getList("files")
            return filesElement.elements.map { buildTorrentInfoFile(it as DictionaryElement) }
        }

        private fun buildTorrentInfoFile(dictionary: DictionaryElement): TorrentInfoFile {
            val length = dictionary.getNumber("length")
            val path = dictionary.getString("path")
            return TorrentInfoFile(length, path)
        }
    }
}