package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

private class MockTracker(
    infoHash: ByteArray,
    selfPeer: Peer,
    private val handler: (TrackerRequest) -> TrackerResponse
) : AbstractTracker(infoHash, selfPeer) {
    private val _requests = mutableListOf<TrackerRequest>()

    val requests: List<TrackerRequest>
        get() = _requests

    override suspend fun send(request: TrackerRequest): TrackerResponse {
        _requests.add(request)
        return handler(request)
    }
}

@Ignore("this test takes several seconds to finish")
class TrackerWorkerTest {

    private fun makeMockTracker(handler: (TrackerRequest) -> TrackerResponse): MockTracker {
        val infoHash = ByteArray(20)
        val selfPeer = Peer(ByteArray(20), "localhost", 6881)
        return MockTracker(infoHash, selfPeer, handler)
    }

    // started -> empty -> stopped
    @Test
    fun testLifeCyclePeerEvent() {
        val peers = listOf(Peer(ByteArray(20), "localhost", 6882))
        val mockTracker = makeMockTracker { TrackerResponse(1, peers) }
        val eventBus = EventBus()

        val worker = TrackerWorker(mockTracker, eventBus, 1024)
        runBlocking {
            val job = launch { worker.start() }
            delay(1500)
            job.cancel()
            val events = eventBus.bulkPoll(QueueName.HANDSHAKE_ALL)
            assertEquals(2, events.size)
            assertEquals(peers, (events[0] as PeerListEvent).peers)
        }
        val requests = mockTracker.requests
        assertEquals(3, requests.size)
        assertEquals(PeerEvent.STARTED, requests[0].event)
        assertEquals(PeerEvent.EMPTY, requests[1].event)
        assertEquals(PeerEvent.STOPPED, requests[2].event)
    }

    // started -> empty(failed), wait -> empty(successfully) -> stop
    @Test
    fun testRetry() {
        val peers = listOf(Peer(ByteArray(20), "localhost", 6882))
        var requestNo = 0
        val mockTracker = makeMockTracker {
            requestNo++
            if (requestNo == 2) {
                throw TrackerException("failed")
            }
            TrackerResponse(1, peers)
        }
        val eventBus = EventBus()
        val worker = TrackerWorker(mockTracker, eventBus, 1024, retryTimeout = 1000)

        runBlocking {
            val job = launch { worker.start() }
            delay(2500)
            job.cancel()
            val events = eventBus.bulkPoll(QueueName.HANDSHAKE_ALL)
            assertEquals(2, events.size)
            assertEquals(peers, (events[0] as PeerListEvent).peers)
        }
        val requests = mockTracker.requests
        assertEquals(4, requests.size)
        assertEquals(PeerEvent.STARTED, requests[0].event)
        assertEquals(PeerEvent.EMPTY, requests[1].event)
        assertEquals(PeerEvent.EMPTY, requests[2].event)
        assertEquals(PeerEvent.STOPPED, requests[3].event)
    }

    // started(failed), wait -> started -> empty -> stop
    @Test
    fun testRetryWhenStart() {
        val peers = listOf(Peer(ByteArray(20), "localhost", 6882))
        var requestNo = 0
        val mockTracker = makeMockTracker {
            requestNo++
            if (requestNo == 1) {
                throw TrackerException("failed")
            }
            TrackerResponse(1, peers)
        }
        val eventBus = EventBus()
        val worker = TrackerWorker(mockTracker, eventBus, 1024, retryTimeout = 1000)

        runBlocking {
            val job = launch { worker.start() }
            delay(2500)
            job.cancel()
            val events = eventBus.bulkPoll(QueueName.HANDSHAKE_ALL)
            assertEquals(2, events.size)
            assertEquals(peers, (events[0] as PeerListEvent).peers)
        }
        val requests = mockTracker.requests
        assertEquals(4, requests.size)
        assertEquals(PeerEvent.STARTED, requests[0].event)
        assertEquals(PeerEvent.STARTED, requests[1].event)
        assertEquals(PeerEvent.EMPTY, requests[2].event)
        assertEquals(PeerEvent.STOPPED, requests[3].event)
    }

    @Test
    fun testAddUploaded() {
        val mockTracker = makeMockTracker {
            val peers = listOf(
                Peer(
                    ByteArray(20),
                    "localhost",
                    6882
                )
            )
            TrackerResponse(1, peers)
        }
        val eventBus = EventBus()
        val worker = TrackerWorker(mockTracker, eventBus, 1024, retryTimeout = 1000)

        eventBus.offer(QueueName.TRACKER, ClientUploadedEvent(1024))
        runBlocking {
            val job = launch { worker.start() }
            delay(1500)
            job.cancel()
        }
        val requests = mockTracker.requests
        assertEquals(1024, requests[0].uploaded)
    }
}