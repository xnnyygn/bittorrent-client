package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.PoisonPillEvent
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import `in`.xnnyygn.bittorrent.tracker.PeerListEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.nio.ByteBuffer

private class MockPeerSocket : PeerSocket {
    override val isClosed: Boolean
        get() = TODO("not implemented")

    override suspend fun write(buffer: ByteBuffer): Int = TODO("not implemented")

    override suspend fun read(buffer: ByteBuffer): Int = TODO("not implemented")

    override fun <T> read(buffer: ByteBuffer, attachment: T, readQueue: MyQueue<Event>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun readFully(buffer: ByteBuffer, expectedBytesToRead: Int) {
        TODO("not implemented")
    }

    override fun close() = TODO("not implemented")
}

@Ignore("delay in these tests")
class HandshakeAllWorkerTest {

    @Test
    fun testAllPass() {
        val outgoingHandshake = object : OutgoingHandshake {
            override suspend fun handshake(peer: Peer): PeerSocket? {
                println("handshake successfully")
                return MockPeerSocket()
            }
        }
        val eventBus = EventBus()
        val worker = HandshakeAllWorker(outgoingHandshake, eventBus, 1)
        runBlocking {
            launch { worker.start() }
            val peers = listOf(
                Peer(ByteArray(20), "localhost", 6881),
                Peer(ByteArray(20), "localhost", 6882)
            )
            eventBus.offer(QueueName.HANDSHAKE_ALL, PeerListEvent(peers))
            val completableDeferred = CompletableDeferred<Unit>()
            eventBus.offer(QueueName.HANDSHAKE_ALL, PoisonPillEvent(completableDeferred))
            completableDeferred.join()
            val events = eventBus.bulkPoll(QueueName.TRANSMISSION)
            assertEquals(1, events.size)
        }
    }

    @Test
    fun testFirstFailed() {
        val outgoingHandshake = object : OutgoingHandshake {
            private var peerNo = 0
            override suspend fun handshake(peer: Peer): PeerSocket? {
                return if ((++peerNo) == 1) {
                    null
                } else {
                    println("handshake successfully")
                    MockPeerSocket()
                }
            }
        }
        val eventBus = EventBus()
        val worker = HandshakeAllWorker(outgoingHandshake, eventBus, 1)
        runBlocking {
            val job = launch { worker.start() }
            val peers = listOf(
                Peer(ByteArray(20), "localhost", 6881),
                Peer(ByteArray(20), "localhost", 6882)
            )
            eventBus.offer(QueueName.HANDSHAKE_ALL, PeerListEvent(peers))
            delay(500)
            job.cancel()
            val events = eventBus.bulkPoll(QueueName.TRANSMISSION)
            assertEquals(1, events.size)
        }
    }
}