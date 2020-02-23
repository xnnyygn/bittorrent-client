package `in`.xnnyygn.bittorrent.peer

import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

class HandshakeProtocol(private val infoHash: ByteArray, private val selfPeerId: ByteArray) {

    private val logger = LoggerFactory.getLogger(javaClass)
    // read only
    private val sendBufferToRemote: ByteBuffer = ByteBuffer.allocateDirect(48).apply {
        put(19)
        put("Bittorrent protocol".toByteArray()) // 19
        putLong(0) // 8
        put(infoHash) // 20
        flip()
    }
    // write only
    private val sendBufferFromRemote = ByteBuffer.allocateDirect(20)
    // write only
    private val receiveBufferFromRemote = ByteBuffer.allocateDirect(48)
    // read only
    private val receiveBufferToRemote = ByteBuffer.allocateDirect(20).apply {
        put(selfPeerId)
        flip()
    }

    suspend fun send(socket: PeerSocket, peerId: ByteArray): Boolean {
        try {
            writeFully(socket, sendBufferToRemote)
            readFully(socket, sendBufferFromRemote)
            return compareArray(sendBufferFromRemote, 0, peerId)
        } finally {
            sendBufferToRemote.rewind()
            sendBufferFromRemote.rewind()
        }
    }

    // TODO add timeout
    private suspend fun writeFully(socket: PeerSocket, buffer: ByteBuffer) {
        var bytesWrite = 0
        do {
            bytesWrite += socket.write(buffer)
        } while (bytesWrite < buffer.capacity())
    }

    // TODO add timeout
    private suspend fun readFully(socket: PeerSocket, buffer: ByteBuffer) {
        var bytesRead = 0
        do {
            bytesRead += socket.read(buffer)
        } while (bytesRead < buffer.capacity())
        buffer.flip()
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
        try {
            readFully(socket, receiveBufferFromRemote)
            if (!compareArray(receiveBufferFromRemote, 28, infoHash)) {
                logger.warn("info hash not match")
                return false
            }
            writeFully(socket, receiveBufferToRemote)
            return true
        } finally {
            receiveBufferFromRemote.rewind()
            receiveBufferToRemote.rewind()
        }
    }
}