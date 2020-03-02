package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import kotlinx.coroutines.CompletableDeferred
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// thread safe
interface PeerSocket {
    val isClosed: Boolean

    suspend fun write(buffer: ByteBuffer): Int

    suspend fun read(buffer: ByteBuffer): Int

    fun <T> read(buffer: ByteBuffer, attachment: T?, handler: CompletionHandler<Int, T?>)

    suspend fun readFully(buffer: ByteBuffer, expectedBytesToRead: Int = buffer.capacity())

    fun close()
}

data class ReadEvent<T>(
    val buffer: ByteBuffer,
    val attachment: T,
    val bytesRead: Int? = null,
    val exception: Throwable? = null
) : Event

sealed class AbstractPeerSocket(protected val delegate: AsynchronousSocketChannel) :
    PeerSocket {
    @Volatile
    private var closed = false

    override val isClosed: Boolean
        get() = closed

    override suspend fun write(buffer: ByteBuffer): Int = suspendCoroutine { cont ->
        delegate.write(
            buffer, cont,
            ReadWriteCompletionHandler
        )
    }

    private object ReadWriteCompletionHandler : CompletionHandler<Int, Continuation<Int>> {
        override fun completed(result: Int?, attachment: Continuation<Int>?) {
            attachment?.resume(result!!)
        }

        override fun failed(exception: Throwable?, attachment: Continuation<Int>?) {
            attachment?.resumeWithException(exception!!)
        }
    }

    override fun <T> read(buffer: ByteBuffer, attachment: T?, handler: CompletionHandler<Int, T?>) {
        delegate.read(buffer, attachment, handler)
    }

    override suspend fun read(buffer: ByteBuffer): Int = suspendCoroutine { cont ->
        delegate.read(
            buffer, cont,
            ReadWriteCompletionHandler
        )
    }

    override suspend fun readFully(buffer: ByteBuffer, expectedBytesToRead: Int) {
        var totalRead = 0
        do {
            val bytesRead = read(buffer)
            if (bytesRead == -1) {
                throw EOFException("reach end of stream")
            }
            totalRead += bytesRead
        } while (totalRead < expectedBytesToRead)
    }

    override fun close() {
        closed = true
        delegate.close()
    }
}

fun PeerSocket.closeQuietly() {
    try {
        this.close()
    } catch (e: IOException) {
    }
}

class IncomingSocket(delegate: AsynchronousSocketChannel) : AbstractPeerSocket(delegate)

class OutgoingPeerSocket(val peer: Peer) : AbstractPeerSocket(AsynchronousSocketChannel.open()) {

    suspend fun connect(): Unit = suspendCoroutine { cont ->
        delegate.connect(InetSocketAddress(peer.ip, peer.port), cont, ConnectCompletionHandler())
    }

    inner class ConnectCompletionHandler : CompletionHandler<Void, Continuation<Unit>> {
        override fun completed(result: Void?, attachment: Continuation<Unit>?) {
            attachment?.resume(Unit)
        }

        override fun failed(exception: Throwable?, attachment: Continuation<Unit>?) {
            attachment?.resumeWithException(exception!!)
        }
    }
}