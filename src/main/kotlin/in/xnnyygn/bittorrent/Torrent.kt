package `in`.xnnyygn.bittorrent

import `in`.xnnyygn.bittorrent.bencode.BEncodeElement
import `in`.xnnyygn.bittorrent.bencode.DictionaryElement
import `in`.xnnyygn.bittorrent.transmission.download.DownloadedPiece

data class TorrentInfoFile(val length: Long, val path: String)

class TorrentInfo(val files: List<TorrentInfoFile>, val name: String, val pieceLength: Int, val pieces: ByteArray) {
    override fun toString(): String {
        return "TorrentInfo(files=$files, name='$name', pieceLength=$pieceLength, pieces.size=${pieces.size})"
    }
}

data class Torrent(val announce: String, val info: TorrentInfo, val infoHash: ByteArray) {
    val totalLength: Long = info.files.map { it.length }.sum()

    // TODO test
    val pieceCount: Int = Math.floorDiv(totalLength, info.pieceLength).toInt()

    fun validatePiece(piece: DownloadedPiece): Boolean {
        if (piece.index < 0 || piece.index >= pieceCount) return false
        val hash = piece.hash()
        if (hash.size != 20) return false
        val offset = piece.index * 20
        for (i in 0 until 20) {
            if (info.pieces[offset + i] != hash[i]) {
                return false
            }
        }
        return true
    }

    // TODO move to another class
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