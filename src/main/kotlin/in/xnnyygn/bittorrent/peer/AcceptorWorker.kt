package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
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
    private val eventBus: EventBus
) {

    suspend fun start(): Unit = coroutineScope {
        val serverSocket = AsynchronousServerSocketChannel.open()
        serverSocket.bind(InetSocketAddress("localhost", port))
        while (isActive) {
            val socket = accept(serverSocket)
            val peerSocket = IncomingSocket(socket)
            if (handshakeProtocol.receive(peerSocket)) {
                val connection = PeerConnection(peerSocket)
                eventBus.offer(QueueName.TRANSMISSION, PeerConnectionEvent(connection))
            } else {
                peerSocket.close()
            }
        }
    }

    private suspend fun accept(serverSocket: AsynchronousServerSocketChannel): AsynchronousSocketChannel =
        suspendCoroutine { cont ->
            serverSocket.accept(
                cont,
                AcceptCompletionHandler
            )
        }

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