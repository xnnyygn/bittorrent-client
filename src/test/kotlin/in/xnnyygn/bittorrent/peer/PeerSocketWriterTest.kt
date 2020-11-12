package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.buildByteArrayInDataForm
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.net.CancelMessage
import `in`.xnnyygn.bittorrent.net.ChokeMessage
import `in`.xnnyygn.bittorrent.net.KeepAliveMessage
import `in`.xnnyygn.bittorrent.net.PeerMessageTypes
import `in`.xnnyygn.bittorrent.net.PieceMessage
import `in`.xnnyygn.bittorrent.net.RequestMessage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

private class MockPeerSocketForWriter : PeerSocket {
    private val bytesOut = ByteArrayOutputStream()

    override val isClosed: Boolean
        get() = TODO("not implemented")

    override suspend fun write(buffer: ByteBuffer): Int {
        val remaining = buffer.remaining()
        while (buffer.hasRemaining()) {
            bytesOut.write(buffer.get().toInt())
        }
        return remaining
    }

    override suspend fun read(buffer: ByteBuffer): Int {
        TODO("not implemented")
    }

    override fun <T> read(buffer: ByteBuffer, attachment: T, readQueue: MyQueue<Event>) {
        TODO("not implemented")
    }

    override suspend fun readFully(buffer: ByteBuffer, expectedBytesToRead: Int) {
        TODO("not implemented")
    }

    override fun close() {
        TODO("not implemented")
    }

    fun toByteArray(): ByteArray = bytesOut.toByteArray()
}

class PeerSocketWriterTest {
    @Test
    fun testEmpty() {
        val mockSocket = MockPeerSocketForWriter()
        val writer = PeerSocketWriter(mockSocket)
        runBlocking {
            writer.write()
        }
        assertEquals(0, mockSocket.toByteArray().size)
    }

    @Test
    fun testKeepAlive() {
        val mockSocket = MockPeerSocketForWriter()
        val writer = PeerSocketWriter(mockSocket)
        runBlocking {
            writer.write(KeepAliveMessage)
        }
        val bytes = mockSocket.toByteArray()
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bytes)
    }

    @Test
    fun testMultipleMessages() {
        val mockSocket = MockPeerSocketForWriter()
        val writer = PeerSocketWriter(mockSocket)
        runBlocking {
            writer.write(
                KeepAliveMessage,
                ChokeMessage
            )
        }
        val bytes = mockSocket.toByteArray()
        assertArrayEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 1, 0), bytes)
    }

    @Test
    fun testRequest() {
        val mockSocket = MockPeerSocketForWriter()
        val writer = PeerSocketWriter(mockSocket)
        runBlocking {
            writer.write(RequestMessage(0, 0, 1024))
        }
        val bytes = mockSocket.toByteArray()
        val expectedBytes = buildByteArrayInDataForm {
            writeInt(20)
            writeByte(PeerMessageTypes.REQUEST.toInt())
            writeInt(0)
            writeLong(0)
            writeLong(1024)
        }
        assertArrayEquals(expectedBytes, bytes)
    }

    @Test
    fun testPiece() {
        val mockSocket = MockPeerSocketForWriter()
        val writer = PeerSocketWriter(mockSocket)
        runBlocking {
            writer.write(PieceMessage(0, 0, ByteArray(16)))
        }
        val bytes = mockSocket.toByteArray()
        val expectedBytes = buildByteArrayInDataForm {
            writeInt(29)
            writeByte(PeerMessageTypes.PIECE.toInt())
            writeInt(0)
            writeLong(0)
            write(ByteArray(16))
        }
        assertArrayEquals(expectedBytes, bytes)
    }

    @Test
    fun testCancel() {
        val mockSocket = MockPeerSocketForWriter()
        val writer = PeerSocketWriter(mockSocket)
        runBlocking {
            writer.write(CancelMessage(0, 0, 1024))
        }
        val bytes = mockSocket.toByteArray()
        val expectedBytes = buildByteArrayInDataForm {
            writeInt(20)
            writeByte(PeerMessageTypes.CANCEL.toInt())
            writeInt(0)
            writeLong(0)
            writeLong(1024)
        }
        assertArrayEquals(expectedBytes, bytes)
    }
}