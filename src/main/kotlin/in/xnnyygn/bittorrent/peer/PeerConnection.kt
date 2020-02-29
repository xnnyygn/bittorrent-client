package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.BitSet

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

private class PeerMessageReader3(private val socket: PeerSocket, private val eventQueue: MyQueue<Event>) {
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
    private val messageReader = PeerMessageReader3(socket, eventQueue)
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