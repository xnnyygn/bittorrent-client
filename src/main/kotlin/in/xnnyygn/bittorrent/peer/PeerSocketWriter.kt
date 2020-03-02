package `in`.xnnyygn.bittorrent.peer

import java.nio.ByteBuffer

private interface MessageWriter {
    fun write(buffer: ByteBuffer): Boolean
}

private class BytesMessageWriter(private val bytes: ByteArray) : MessageWriter {
    private var offset: Int = 0

    override fun write(buffer: ByteBuffer): Boolean {
        offset += buffer.writeBytes(bytes, offset)
        return offset == bytes.size
    }
}

private fun ByteBuffer.writeBytes(bytes: ByteArray, offset: Int): Int {
    val remaining = remaining()
    if (remaining == 0) {
        return 0
    }
    val bytesToTransfer = (bytes.size - offset).coerceAtMost(remaining)
    put(bytes, offset, bytesToTransfer)
    return bytesToTransfer
}

private class PieceMessageWriter(private val message: PieceMessage) : MessageWriter {
    private val bytes1: ByteArray = buildByteArrayInDataForm {
        writeInt(1 + 4 + 8 + message.piece.capacity())
        writeByte(PeerMessageTypes.PIECE.toInt())
        writeInt(message.index)
        writeLong(message.begin)
    }
    private val bytes2: ByteArray = message.piece.array()
    private var offset: Int = 0

    override fun write(buffer: ByteBuffer): Boolean {
        if (offset < bytes1.size) {
            offset += buffer.writeBytes(bytes1, offset)
            if (buffer.isFull()) {
                return false
            }
        }
        offset += buffer.writeBytes(bytes2, offset - bytes1.size)
        return offset == (bytes1.size + bytes2.size)
    }
}

private fun ByteBuffer.isFull(): Boolean = (position() == limit())

class PeerSocketWriter(private val socket: PeerSocket) {
    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(4 + 1 + 8 + 8 + (1 shl 16))

    suspend fun write(messages: List<PeerMessage>) {
        var completed: Boolean
        for (message in messages) {
            do {
                completed = messageWriterFor(message).write(buffer)
                if (buffer.isFull()) {
                    flush()
                }
            } while (!completed)
        }
        if (buffer.isNotEmpty()) {
            flush()
        }
    }

    private fun ByteBuffer.isNotEmpty(): Boolean = (position() > 0)

    private fun messageWriterFor(message: PeerMessage): MessageWriter {
        return when (message) {
            is KeepAliveMessage -> BytesMessageWriter(ByteArray(4))
            is ChokeMessage -> messageWriterForNoPayload(message)
            is UnchokeMessage -> messageWriterForNoPayload(message)
            is InterestedMessage -> messageWriterForNoPayload(message)
            is UninterestedMessage -> messageWriterForNoPayload(message)
            is RequestMessage -> messageWriterForAbstractRequest(message)
            is PieceMessage -> PieceMessageWriter(message)
            is CancelMessage -> messageWriterForAbstractRequest(message)
            else -> throw IllegalStateException("unexpected message $message")
        }
    }

    private fun messageWriterForAbstractRequest(message: AbstractRequestMessage): BytesMessageWriter {
        return BytesMessageWriter(buildByteArrayInDataForm {
            writeInt(20)
            writeByte(message.messageType.toInt())
            writeInt(message.index)
            writeLong(message.begin)
            writeLong(message.length)
        })
    }

    private fun messageWriterForNoPayload(message: NoPayloadPeerMessage): BytesMessageWriter {
        return BytesMessageWriter(buildByteArrayInDataForm {
            writeInt(1)
            writeByte(message.messageType.toInt())
        })
    }

    private suspend fun flush() {
        buffer.flip()
        while (buffer.hasRemaining()) {
            socket.write(buffer)
        }
        buffer.compact()
    }
}