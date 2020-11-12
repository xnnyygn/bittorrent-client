package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.tracker.Peer
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Test

@Ignore("test server is required")
class HandshakeProtocolTest {

    @Test
    fun testSend() {
        val infoHash = byteArrayOf(1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4)
        val peerId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        val protocol = HandshakeProtocol(infoHash, ByteArray(20))
        runBlocking {
            repeat(3) {
                val socket = OutgoingPeerSocket(
                    Peer(
                        peerId,
                        "localhost",
                        6881
                    )
                )
                socket.connect()
                val result = protocol.send(socket, peerId)
                println("handshake $result")
            }
        }
    }
}