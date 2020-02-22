package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventQueue
import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.BitSet

private class MyListIterator<T>(private var list: MyList<T>) : Iterator<T> {
    override fun hasNext(): Boolean = (list != NoneMyList)

    override fun next(): T {
        val l = list
        check(l is DefaultMyList<T>)
        val item = l.head
        list = l.rest
        return item
    }
}

private sealed class MyList<out T> : Iterable<T> {
    override fun iterator(): Iterator<T> = MyListIterator(this)
}

private object NoneMyList : MyList<Nothing>()

private data class DefaultMyList<T>(val head: T, val rest: MyList<T> = NoneMyList) : MyList<T>()

// (init) -> buffer
// status + buffer -> (status', buffer', [message])
/**
 * socket.read(status.buffer)
 * in callback
 * status.apply()
 */

private data class DecoderResult(
    val status: DecoderStatus,
    val messages: MyList<PeerMessage> = NoneMyList
) {
    fun prependMessage(message: PeerMessage): DecoderResult {
        return DecoderResult(status, DefaultMyList(message, messages))
    }
}

private class DecoderBuffers(pieceCount: Int) {
    val smallBuffer: ByteBuffer = ByteBuffer.allocate(64.coerceAtLeast(4 + 2 + pieceCount / 8))
    val pieceBuffer: ByteBuffer by lazy {
        ByteBuffer.allocateDirect(4 + 1 + (1 shl 16))
    }
}

private interface DecoderStatus {
    val buffer: ByteBuffer
    fun decode(): DecoderResult
}

private class DecoderStartStatus(pieceCount: Int, private val buffers: DecoderBuffers) : DecoderStatus {
    private val bitFieldStatus = DecoderTypeBitFieldStatus(pieceCount, buffers, this)
    private val haveStatus = DecoderTypeHaveStatus(buffers, this)
    private val requestStatus = DecoderTypeRequestStatus(buffers, this)
    private val pieceStatus = DecoderTypePieceStatus((1 shl 16), buffers, this)
    private val cancelStatus = DecoderTypeCancelStatus(buffers, this)

    override val buffer: ByteBuffer
        get() = buffers.smallBuffer

    override fun decode(): DecoderResult {
        if (buffer.remaining() < 4) {
            // compact before returning?
            return DecoderResult(this)
        }
        val length = buffer.getInt()
        if (length == 0) {
            return decode().prependMessage(KeepAliveMessage)
        }
        return DecoderLengthStatus(length, buffers, this).decode()
    }

    fun fixedLengthStatus(type: Byte, length: Int): DecoderStatus {
        val status = fixedLengthStatus(type)
        status.validateLength(length)
        return status
    }

    private fun fixedLengthStatus(type: Byte): FixedLengthDecoderStatus = when (type) {
        PeerMessageTypes.BITFIELD -> bitFieldStatus
        PeerMessageTypes.HAVE -> haveStatus
        PeerMessageTypes.REQUEST -> requestStatus
        PeerMessageTypes.PIECE -> pieceStatus
        PeerMessageTypes.CANCEL -> cancelStatus
        else -> throw IllegalArgumentException("unknown fixed length status $type")
    }
}

private class DecoderLengthStatus(
    private val length: Int,
    private val buffers: DecoderBuffers,
    private val startStatus: DecoderStartStatus
) : DecoderStatus {

    override val buffer: ByteBuffer
        get() = buffers.smallBuffer

    override fun decode(): DecoderResult {
        if (buffer.remaining() < 1) {
            return DecoderResult(this)
        }
        return when (val type = buffer.get()) {
            PeerMessageTypes.CHOKE -> startStatus.decode().prependMessage(ChokeMessage)
            PeerMessageTypes.UNCHOKE -> startStatus.decode().prependMessage(UnchokeMessage)
            PeerMessageTypes.INTERESTED -> startStatus.decode().prependMessage(InterestedMessage)
            PeerMessageTypes.UNINTERESTED -> startStatus.decode().prependMessage(UninterestedMessage)
            else -> startStatus.fixedLengthStatus(type, length).decode()
        }
    }
}

private sealed class FixedLengthDecoderStatus(protected val expectedLength: Int) : DecoderStatus {
    val payloadLength: Int = expectedLength - 1

    fun validateLength(length: Int) = check(length == expectedLength) { "unexpected length $length" }
}

private class DecoderTypeBitFieldStatus(
    pieceCount: Int,
    private val buffers: DecoderBuffers,
    private val startStatus: DecoderStartStatus
) : FixedLengthDecoderStatus((if (pieceCount % 8 == 0) pieceCount / 8 + 1 else pieceCount / 8 + 2)) { // TODO refactor me

    override val buffer: ByteBuffer
        get() = buffers.smallBuffer

    override fun decode(): DecoderResult {
        if (buffer.remaining() < payloadLength) {
            return DecoderResult(this)
        }
        val bytes = ByteArray(payloadLength)
        buffer.get(bytes)
        val message = BitFieldMessage(BitSetPiecesStatus(BitSet.valueOf(bytes), 0))
        return startStatus.decode().prependMessage(message)
    }
}

private class DecoderTypeHaveStatus(
    private val buffers: DecoderBuffers,
    private val startStatus: DecoderStartStatus
) : FixedLengthDecoderStatus(5) {

    override val buffer: ByteBuffer
        get() = buffers.smallBuffer

    override fun decode(): DecoderResult {
        if (buffer.remaining() < 4) return DecoderResult(this)
        val index = buffer.getInt()
        val message = HaveMessage(index)
        return startStatus.decode().prependMessage(message)
    }
}

private class DecoderTypeRequestStatus(
    private val buffers: DecoderBuffers,
    private val startStatus: DecoderStartStatus
) : FixedLengthDecoderStatus(21) {
    override val buffer: ByteBuffer
        get() = buffers.smallBuffer

    override fun decode(): DecoderResult {
        if (buffer.remaining() < payloadLength) {
            return DecoderResult(this)
        }
        val index = buffer.getInt()
        val begin = buffer.getLong()
        val length = buffer.getLong()
        val message = RequestMessage(index, begin, length)
        return startStatus.decode().prependMessage(message)
    }
}

private class DecoderTypePieceStatus(
    private val pieceLength: Int,
    private val buffers: DecoderBuffers,
    private val startStatus: DecoderStartStatus
) : FixedLengthDecoderStatus(1 + 4 + 8 + pieceLength) {
    override val buffer: ByteBuffer
        get() = buffers.pieceBuffer

    override fun decode(): DecoderResult {
        if(buffer.remaining() < payloadLength) {
            return DecoderResult(this)
        }
        // first copy, than optimise it
        val index = buffer.getInt()
        val begin = buffer.getLong()
        val piece = ByteArray(pieceLength)
        buffer.get(piece)
        
        val message = PieceMessage(index, begin, ByteBuffer.allocate(0))
        return startStatus.decode().prependMessage(message)
    }
}

private class DecoderTypeCancelStatus(
    private val buffers: DecoderBuffers,
    private val startStatus: DecoderStartStatus
) : FixedLengthDecoderStatus(21) {
    override val buffer: ByteBuffer
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun decode(): DecoderResult {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}


private class PeerMessageWriter(private val socket: PeerSocket) {
    private val messageHeaderBuffer = ByteBuffer.allocate(5)
    private val smallPayloadBuffer = ByteBuffer.allocate(4 + 8 + 8)

    private companion object {
        val BYTE_BUFFER_KEEP_ALIVE: ByteBuffer = ByteBuffer.wrap(ByteArray(4))
    }

    suspend fun write(message: PeerMessage) {
        when (message) {
            is KeepAliveMessage -> socket.write(BYTE_BUFFER_KEEP_ALIVE)
            is NoPayloadPeerMessage -> writeMessageWithoutPayload(message.messageType)
            is HaveMessage -> writeMessage(PeerMessageTypes.HAVE, 4) { it.putInt(message.index) }
            is BitFieldMessage -> {
                val bytes = message.piecesStatus.toByteArray()
                writeMessage(PeerMessageTypes.BITFIELD, bytes.size) { it.put(bytes) }
            }
            is RequestMessage -> writeMessage(PeerMessageTypes.REQUEST, 20) { buffer ->
                buffer.putInt(message.index)
                buffer.putLong(message.begin)
                buffer.putLong(message.length)
            }
            is PieceMessage -> writePieceMessage(message)
            is CancelMessage -> writeMessage(PeerMessageTypes.CANCEL, 20) { buffer ->
                buffer.putInt(message.index)
                buffer.putLong(message.begin)
                buffer.putLong(message.length)
            }
        }
    }

    private suspend fun writePieceMessage(message: PieceMessage) {
        writeHeader(PeerMessageTypes.PIECE, 12 + message.piece.remaining())
        smallPayloadBuffer.putInt(message.index)
        smallPayloadBuffer.putLong(message.begin)
        socket.write(smallPayloadBuffer)
        smallPayloadBuffer.clear()
        socket.write(message.piece)
    }

    private suspend fun writeMessage(messageType: Byte, payloadLength: Int, consumer: (ByteBuffer) -> Unit) {
        writeHeader(messageType, payloadLength + 1)
        val buffer =
            if (payloadLength <= smallPayloadBuffer.capacity()) smallPayloadBuffer
            else ByteBuffer.allocate(payloadLength)
        consumer(buffer)
        socket.write(buffer)
        buffer.clear()
    }

    private suspend fun writeMessageWithoutPayload(messageType: Byte) {
        writeHeader(messageType, 1)
    }

    private suspend fun writeHeader(messageType: Byte, length: Int) {
        messageHeaderBuffer.putInt(1)
        messageHeaderBuffer.put(messageType)
        socket.write(messageHeaderBuffer)
        messageHeaderBuffer.clear()
    }
}

data class PeerMessageReadEvent(val message: PeerMessage) : Event

private interface PeerMessageReaderListener {
    fun onMessage(message: PeerMessage)
    fun estimateRemaining(type: Byte, remaining: Int)
}

private class PeerMessageReader2(private val socket: PeerSocket, private val eventQueue: EventQueue<PeerMessage>) {
    companion object {
        const val STATUS_START = 0
        const val STATUS_LENGTH_READ = 1
        const val STATUS_TYPE_READ = 2
        const val STATUS_TYPE_BITFIELD = 10
        const val STATUS_TYPE_HAVE = 11
        const val STATUS_TYPE_REQUEST = 12
        const val STATUS_TYPE_PIECE = 13
        const val STATUS_TYPE_CANCEL = 14
    }

    private val messageBuffer = ByteBuffer.allocate(64)

    private var status = STATUS_START
    private var length: Int = 0
    private var messageStart: Int = 0
    private var remainingMessageLength: Int = 0
    private var type: Byte = 0

    suspend fun read() {
        socket.read(messageBuffer)
        messageBuffer.flip()
        accept(messageBuffer)
        if (status == STATUS_START) {
            if (messageBuffer.capacity() - messageBuffer.limit() < 4) {
                messageBuffer.compact()
            }
        } else {
            remainingMessageLength = length - (messageBuffer.position() - messageStart)
            if (messageBuffer.capacity() - messageStart < length) {
                messageBuffer.compact()
            }
            // TODO read to buffer, no remaining
        }
    }

    private fun accept(buffer: ByteBuffer) {
        when (status) {
            STATUS_START -> statusStart(buffer)
            STATUS_LENGTH_READ -> statusLengthRead(buffer)
            STATUS_TYPE_READ -> typeDispatch(buffer)
            STATUS_TYPE_BITFIELD -> statusTypeBitField(buffer)
        }
    }

    private fun statusStart(buffer: ByteBuffer) {
        if (buffer.remaining() < 4) return
        length = buffer.getInt()
        messageStart = buffer.position()
        if (length == 0) {
            done(KeepAliveMessage, buffer)
            return
        }
        status = STATUS_LENGTH_READ
        statusLengthRead(buffer)
    }

    private fun statusLengthRead(buffer: ByteBuffer) {
        if (buffer.remaining() < 1) return
        type = buffer.get()
        status = STATUS_TYPE_READ
        typeDispatch(buffer)
    }

    private fun typeDispatch(buffer: ByteBuffer) {
        when (type) {
            PeerMessageTypes.CHOKE -> done(ChokeMessage, buffer)
            PeerMessageTypes.UNCHOKE -> done(UnchokeMessage, buffer)
            PeerMessageTypes.INTERESTED -> done(InterestedMessage, buffer)
            PeerMessageTypes.UNINTERESTED -> done(UninterestedMessage, buffer)
            PeerMessageTypes.BITFIELD -> {
                status = STATUS_TYPE_BITFIELD
                statusTypeBitField(buffer)
            }
            PeerMessageTypes.HAVE -> {
                status = STATUS_TYPE_HAVE
                statusTypeHave(buffer)
            }
            PeerMessageTypes.REQUEST -> {
                status = STATUS_TYPE_REQUEST
                statusTypeRequest(buffer)
            }
        }
    }

    private fun statusTypeBitField(buffer: ByteBuffer) {
        val expectedLength = length - 1
    }

    private fun statusTypeHave(buffer: ByteBuffer) {
        if (buffer.remaining() < 4) return
        val index = buffer.getInt()
        done(HaveMessage(index), buffer)
    }

    private fun statusTypeRequest(buffer: ByteBuffer) {
        if (buffer.remaining() < 20) return
        val index = buffer.getInt()
        val begin = buffer.getLong()
        val length = buffer.getLong()
        done(RequestMessage(index, begin, length), buffer)
    }

    private fun statusTypeCancel(buffer: ByteBuffer) {
        if (buffer.remaining() < 20) return
        val index = buffer.getInt()
        val begin = buffer.getLong()
        val length = buffer.getLong()
        done(CancelMessage(index, begin, length), buffer)
    }

    private fun done(message: PeerMessage, buffer: ByteBuffer) {
        eventQueue.offer(message)
        status = STATUS_START
        statusStart(buffer)
    }
}

private class PeerMessageReader(private val socket: PeerSocket, private val eventQueue: MyQueue<Event>) {
    private val messageHeaderBuffer = ByteBuffer.allocate(5)
    private val smallPayloadBuffer = ByteBuffer.allocate(4 + 8 + 8)

    fun read2() {
        // TODO check current status
        socket.read(messageHeaderBuffer, 1, eventQueue)
    }

    fun read3(readEvent: ReadEvent<Int>) {
        when (readEvent.attachment) {
            1 -> {
                val bytesRead = readEvent.bytesRead
                if (bytesRead != null) {
                    if (bytesRead < 4) {
                        socket.read(messageHeaderBuffer, 1, eventQueue)
                        return
                    }
                    val length = messageHeaderBuffer.getInt()
                    if (length == 0) {
                        eventQueue.offer(PeerMessageReadEvent(KeepAliveMessage))
                        // read more?
                        return
                    }
                    if (bytesRead < 5) {
                        socket.read(messageHeaderBuffer, 2, eventQueue)
                        return
                    }
                    // ...
                }
            }
        }
    }

    suspend fun read(pieceCount: Int): PeerMessage {
        socket.readFully(messageHeaderBuffer, 4)
        val length = messageHeaderBuffer.getInt()
        if (length == 0) {
            messageHeaderBuffer.clear()
            return KeepAliveMessage
        }

        socket.readFully(messageHeaderBuffer, 1)
        val messageType = messageHeaderBuffer.get()
        messageHeaderBuffer.clear()
        if (length == 1) {
            return messageWithoutPayload(messageType)
        }

        return when (messageType) {
            PeerMessageTypes.HAVE -> loadPayload(length - 1, 4) { HaveMessage(it.getInt()) }
            PeerMessageTypes.BITFIELD -> loadPayload(length - 1) { buffer ->
                BitFieldMessage(
                    BitSetPiecesStatus(
                        BitSet.valueOf(
                            buffer
                        ), pieceCount
                    )
                )
            }
            PeerMessageTypes.REQUEST -> loadPayload(length - 1, 20) { buffer ->
                val index = buffer.getInt()
                val begin = buffer.getLong()
                val requestLength = buffer.getLong()
                RequestMessage(index, begin, requestLength)
            }
            PeerMessageTypes.PIECE -> loadPiecePayload(length - 1)
            PeerMessageTypes.CANCEL -> loadPayload(length - 1, 20) { buffer ->
                val index = buffer.getInt()
                val begin = buffer.getLong()
                val requestLength = buffer.getLong()
                CancelMessage(index, begin, requestLength)
            }
            else -> throw IllegalStateException("unexpected message type $messageType")
        }
    }

    private fun messageWithoutPayload(messageType: Byte): NoPayloadPeerMessage = when (messageType) {
        PeerMessageTypes.CHOKE -> ChokeMessage
        PeerMessageTypes.UNCHOKE -> UnchokeMessage
        PeerMessageTypes.INTERESTED -> InterestedMessage
        PeerMessageTypes.UNINTERESTED -> UninterestedMessage
        else -> throw IllegalStateException("unexpected message type $messageType without payload")
    }

    private suspend fun loadPiecePayload(payloadLength: Int): PieceMessage {
        socket.readFully(smallPayloadBuffer, 12)
        val index = smallPayloadBuffer.getInt()
        val begin = smallPayloadBuffer.getLong()
        smallPayloadBuffer.clear()
        val pieceBuffer = ByteBuffer.allocate(payloadLength - 12)
        socket.readFully(pieceBuffer)
        return PieceMessage(index, begin, pieceBuffer)
    }

    private suspend fun <T> loadPayload(payloadLength: Int, expectedLength: Int = -1, function: (ByteBuffer) -> T): T {
        if (expectedLength >= 0 && payloadLength != expectedLength) {
            throw IllegalStateException("expect length $expectedLength, but was $payloadLength")
        }
        val buffer =
            if (payloadLength <= smallPayloadBuffer.capacity()) smallPayloadBuffer
            else ByteBuffer.allocate(payloadLength)
        socket.readFully(buffer, payloadLength)
        val result = function(buffer)
        buffer.clear()
        return result
    }
}

class MyQueue<T> {
    fun offer(element: T): Unit = TODO()
    suspend fun take(): T = TODO()
}

data class PeerMessageWriteEvent(val message: PeerMessage) : Event

class PeerConnection(private val socket: PeerSocket) {
    private val eventQueue = MyQueue<Event>()
    private val messageReader = PeerMessageReader(socket, eventQueue)
    private val messageWriter = PeerMessageWriter(socket)

    val peer: Peer?
        get() = (socket as? OutgoingPeerSocket)?.peer

    suspend fun eventLoop(pieceCount: Int, handler: (PeerMessage) -> Unit) {
        while (true) {
            when (val event = eventQueue.take()) {
                is ReadEvent<*> -> messageReader.read3(event as ReadEvent<Int>)
                is PeerMessageReadEvent -> handler(event.message)
                is PeerMessageWriteEvent -> messageWriter.write(event.message)
                // maybe WriteEvent?
                else -> {
                    // handler(event)
                }
            }
            messageReader.read2()
        }
    }

    suspend fun readAndWrite(pieceCount: Int, handler: (PeerMessage) -> Unit) = coroutineScope<Unit> {
        launch {
            while (isActive && !socket.isClosed) {
                val message = messageReader.read(pieceCount)
                handler(message)
            }
        }
//        launch {
//            while (isActive && !socket.isClosed) {
//                val message = eventQueue.take()
//                messageWriter.write(message)
//            }
//        }
    }

    fun writeMessage(message: PeerMessage) {
        eventQueue.offer(PeerMessageReadEvent(message))
    }

    fun close() {
        socket.close()
    }
}