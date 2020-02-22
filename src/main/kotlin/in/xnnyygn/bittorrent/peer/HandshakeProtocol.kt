package `in`.xnnyygn.bittorrent.peer

import java.nio.ByteBuffer

class HandshakeProtocol(private val infoHash: ByteArray, private val selfPeerId: ByteArray) {

    private val handshakeMessage: ByteBuffer

    init {
        val buffer = ByteBuffer.allocate(48)
        buffer.put(19) // 1
        buffer.put("Bittorrent protocol".toByteArray()) // 19
        buffer.putLong(0) // 8
        buffer.put(infoHash) // 20
        handshakeMessage = buffer
    }

    suspend fun send(socket: PeerSocket, peerId: ByteArray): Boolean {
        socket.write(handshakeMessage)
        val buffer = ByteBuffer.allocate(20)
        socket.readFully(buffer)
        return compareArray(buffer, 0, peerId)
    }

    private fun compareArray(buffer: ByteBuffer, offset: Int, peerId: ByteArray): Boolean {
        for (i in 0 until 20) {
            if (buffer[i + offset] != peerId[i]) {
                return false
            }
        }
        return true
    }

    suspend fun receive(socket: PeerSocket): Boolean {
        val buffer = ByteBuffer.allocate(48)
        socket.readFully(buffer)
        if (!compareArray(buffer, 28, infoHash)) {
            // info hash not match
            return false
        }
        socket.write(ByteBuffer.wrap(selfPeerId))
        return true
    }
}