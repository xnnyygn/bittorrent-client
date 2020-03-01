package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

private class MockPeerSocketForDecoder(private val bytes: ByteArray) : PeerSocket {
    override val isClosed: Boolean
        get() = TODO("not implemented")

    override suspend fun write(buffer: ByteBuffer): Int {
        TODO("not implemented")
    }

    override suspend fun read(buffer: ByteBuffer): Int {
        val bytesToCopy = bytes.size.coerceAtMost(buffer.remaining())
        buffer.put(bytes, 0, bytesToCopy)
        return bytesToCopy
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
}

class PeerMessageDecoderTest {
    @Test
    fun testKeepAlive() {
        val bytes = writeAndGetByteArray {
            writeInt(0)
        }
        val decoder = PeerMessageDecoder(MockPeerSocketForDecoder(bytes))
        runBlocking {
            val messages = decoder.decode()
            assertEquals(1, messages.size)
            assertEquals(KeepAliveMessage, messages[0])
        }
    }

    // TODO replace with buildByteArray
    private fun writeAndGetByteArray(consumer: DataOutputStream.() -> Unit): ByteArray {
        val byteOut = ByteArrayOutputStream()
        val dataOut = DataOutputStream(byteOut)
        consumer(dataOut)
        return byteOut.toByteArray()
    }

    @Test
    fun testOneMoreByte() {
        val bytes = writeAndGetByteArray {
            writeInt(0)
            writeByte(0)
        }
        val decoder = PeerMessageDecoder(MockPeerSocketForDecoder(bytes))
        runBlocking {
            val messages = decoder.decode()
            assertEquals(1, messages.size)
            assertEquals(KeepAliveMessage, messages[0])
        }
//        decoder.inspect()
    }

    private fun decodeByState(consumer: DataOutputStream.() -> Unit): Pair<List<PeerMessage>, DecoderState> {
        val buffer = ByteBuffer.wrap(writeAndGetByteArray(consumer))
        val messages = mutableListOf<PeerMessage>()
        return Pair(messages, StartDecoderState.decode(buffer, messages))
    }

    @Test
    fun testTwoMessages() {
        val (messages, state) = decodeByState {
            // keep alive
            writeInt(0)
            // choke
            writeInt(1)
            writeByte(PeerMessageTypes.CHOKE.toInt())
        }
        assertEquals(2, messages.size)
        assertEquals(KeepAliveMessage, messages[0])
        assertEquals(ChokeMessage, messages[1])
        assertEquals(StartDecoderState, state)
    }

    @Test
    fun testIncompleteHaveMessage() {
        val bytes = writeAndGetByteArray {
            // have
            writeInt(5)
            writeByte(4)
        }
        val decoder = PeerMessageDecoder(MockPeerSocketForDecoder(bytes))
        runBlocking {
            val messages = decoder.decode()
            assertEquals(0, messages.size)
        }
//        decoder.inspect()
    }

    @Test
    fun testIncompleteHaveMessageState() {
        val (messages, state) = decodeByState {
            // have
            writeInt(5)
            writeByte(PeerMessageTypes.HAVE.toInt())
        }
        assertEquals(0, messages.size)
        assertTrue(state is HaveDecoderState)
    }

    @Test
    fun testBitField() {
        val (messages, state) = decodeByState {
            // have
            writeInt(5)
            writeByte(PeerMessageTypes.BITFIELD.toInt())
            write(ByteArray(4))
        }
        assertEquals(1, messages.size)
        assertEquals(StartDecoderState, state)
    }

    @Test
    fun testIncompleteBitField() {
        val (messages, state) = decodeByState {
            // bit field
            writeInt(5)
            writeByte(PeerMessageTypes.BITFIELD.toInt())
            write(ByteArray(2))
        }
        assertEquals(0, messages.size)
        assertTrue(state is BitFieldDecoderState)
    }

    @Test
    fun testRequest() {
        val (messages, state) = decodeByState {
            // request
            writeInt(21)
            writeByte(PeerMessageTypes.REQUEST.toInt())
            writeInt(4)
            writeLong(0)
            writeLong(1024)
        }
        assertEquals(1, messages.size)
        assertEquals(RequestMessage(4, 0, 1024), messages[0])
        assertEquals(StartDecoderState, state)
    }

    @Test
    fun testIncompleteRequest() {
        val (messages, state) = decodeByState {
            // request
            writeInt(21)
            writeByte(PeerMessageTypes.REQUEST.toInt())
            writeInt(4)
            writeLong(0)
        }
        assertEquals(0, messages.size)
        assertTrue(state is RequestDecoderState)
    }

    @Test
    fun testCancel() {
        val (messages, state) = decodeByState {
            // cancel
            writeInt(21)
            writeByte(PeerMessageTypes.CANCEL.toInt())
            writeInt(4)
            writeLong(0)
            writeLong(1024)
        }
        assertEquals(1, messages.size)
        assertEquals(CancelMessage(4, 0, 1024), messages[0])
        assertEquals(StartDecoderState, state)
    }

    @Test
    fun testIncompleteCancel() {
        val (messages, state) = decodeByState {
            // cancel
            writeInt(21)
            writeByte(PeerMessageTypes.CANCEL.toInt())
            writeInt(4)
            writeLong(0)
        }
        assertEquals(0, messages.size)
        assertTrue(state is CancelDecoderState)
    }

    @Test
    @Ignore
    fun testPiece() {
        val (messages, state) = decodeByState {
            // piece
            writeInt(1 + 4 + 8 + (1 shl 16))
            writeByte(PeerMessageTypes.PIECE.toInt())
            writeInt(4)
            writeLong(0)
            write(ByteArray(1 shl 16))
        }
        assertEquals(1, messages.size)
        val pieceMessage = messages[0] as PieceMessage
        assertEquals(4, pieceMessage.index)
        assertEquals(0, pieceMessage.begin)
        assertEquals(StartDecoderState, state)
    }
}