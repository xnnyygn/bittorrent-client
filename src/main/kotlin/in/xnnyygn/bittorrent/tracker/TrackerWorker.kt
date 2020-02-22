package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventBus
import `in`.xnnyygn.bittorrent.eventbus.QueueName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private object FindPeersEvent : Event

class TrackerWorker(
    private val tracker: Tracker,
    private val eventBus: EventBus,
    private var clientRemaining: Long,
    private val retryTimeout: Long = 3000
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var firstRequest = true
    private var clientUploaded = 0L
    private var clientDownloaded = 0L

    suspend fun start(context: CoroutineContext = EmptyCoroutineContext) {
        withContext(context) {
            try {
                eventBus.offer(QueueName.TRACKER, FindPeersEvent)
                while (isActive) {
                    for (event in eventBus.bulkPoll(QueueName.TRACKER)) {
                        when (event) {
                            // TODO test launch in single thread dispatcher
                            is FindPeersEvent -> launch { findPeersAndAwait() }
                            is ClientUploadedEvent -> clientUploaded += event.uploadedBytes
                            is ClientDownloadedEvent -> clientDownloaded += event.downloadedBytes
                            is ClientRemainingEvent -> clientRemaining = event.remainingBytes
                            else -> logger.warn("unknown event [$event] for tracker worker")
                        }
                    }
                }
            } finally {
                if (!firstRequest) {
                    logger.info("send stop event to tracker")
                    tracker.stop(clientUploaded, clientDownloaded, clientRemaining)
                }
            }
        }
    }

    private suspend fun findPeersAndAwait(): Unit = coroutineScope {
        val r = firstRequest
        try {
            val response = tracker.findPeers(clientUploaded, clientDownloaded, clientRemaining, r)
            logger.info("got ${response.peers.size} peers from tracker, refresh ${response.interval}s later")
            eventBus.offer(QueueName.HANDSHAKE, PeerListEvent(response.peers))
            if (r) {
                firstRequest = false
            }
            delay(response.interval * 1000L)
        } catch (e: TrackerException) {
            logger.warn("failed to find peers, caused by [${e.message}], wait for ${retryTimeout}ms to retry")
            delay(retryTimeout)
        }
        eventBus.offer(QueueName.TRACKER, FindPeersEvent)
    }
}