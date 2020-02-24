package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AcceptorWorker(
    private val port: Int,
    private val handshakeProtocol: HandshakeProtocol,
    private val eventBus: EventBus,
    maxConnections: Int = 3
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val connectionSemaphore = Semaphore(maxConnections)

    suspend fun start(): Unit = coroutineScope {
        val serverSocket = AsynchronousServerSocketChannel.open()
        serverSocket.bind(InetSocketAddress("localhost", port))
        while (isActive) {
            val socket = accept(serverSocket)
            connectionSemaphore.acquire()
            launch {
                handshake(socket)
                connectionSemaphore.release()
            }
        }
    }

    private suspend fun handshake(socket: AsynchronousSocketChannel) {
        val peerSocket = IncomingSocket(socket)
        if (!tryHandshake(peerSocket)) {
            peerSocket.closeQuietly()
            return
        }
        logger.info("handshake successfully")
        eventBus.offer(QueueName.TRANSMISSION, PeerConnectionEvent(PeerConnection(peerSocket)))
    }

    private suspend fun tryHandshake(peerSocket: PeerSocket): Boolean = try {
        handshakeProtocol.receive(peerSocket)
    } catch (e: IOException) {
        logger.warn("failed to handshake, cause [${e.message}]")
        false
    }

    private suspend fun accept(serverSocket: AsynchronousServerSocketChannel): AsynchronousSocketChannel =
        suspendCoroutine { cont -> serverSocket.accept(cont, AcceptCompletionHandler) }

    private object AcceptCompletionHandler :
        CompletionHandler<AsynchronousSocketChannel, Continuation<AsynchronousSocketChannel>> {
        override fun completed(
            result: AsynchronousSocketChannel?,
            attachment: Continuation<AsynchronousSocketChannel>?
        ) {
            attachment?.resume(result!!)
        }

        override fun failed(exception: Throwable?, attachment: Continuation<AsynchronousSocketChannel>?) {
            attachment?.resumeWithException(exception!!)
        }
    }
}