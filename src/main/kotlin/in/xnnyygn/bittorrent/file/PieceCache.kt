package `in`.xnnyygn.bittorrent.file

import java.nio.ByteBuffer

class PieceCache(buffers: List<ByteBuffer>) {
    @Volatile
    var buffers: List<ByteBuffer>? = buffers
        private set

    fun invalidate() {
        buffers = null
    }
}