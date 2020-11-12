package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private object FindPeersEvent : Event

class TrackerWorker(
    private val tracker: Tracker,
    private var clientStatus: ClientStatus,
    private val transmissionConfig: TransmissionConfig
) : Worker {
    private val logger = LoggerFactory.getLogger(javaClass)
    private var firstRequest = true

//    override suspend fun start() = coroutineScope {
//        try {
//            triggerFindPeer()
//            while (isActive) {
//                for (event in eventBus.bulkPoll(QueueName.TRACKER)) {
//                    when (event) {
//                        is FindPeersEvent -> {
//                            val interval = findPeers()
//                            launch {
//                                delay(interval)
//                                triggerFindPeer()
//                            }
//                        }
//                        is ClientUploadedEvent -> clientStatus.addUploaded(event.uploadedBytes)
//                        is ClientDownloadedEvent -> clientStatus.addDownloaded(event.downloadedBytes)
//                        is ClientRemainingEvent -> clientStatus.left = event.remainingBytes
//                        else -> logger.warn("unknown event [$event] for tracker worker")
//                    }
//                }
//            }
//        } finally {
//            if (!firstRequest) {
//                logger.info("send stop event to tracker")
//                withContext(NonCancellable) {
//                    tracker.stop(clientStatus)
//                }
//            }
//        }
//    }

    override fun start(context: WorkerContext) {
        context.submit(FindPeersEvent)
    }

    override fun handle(event: Event, context: WorkerContext) {
        when (event) {
            is FindPeersEvent -> findPeers3(context)
            is ClientUploadedEvent -> clientStatus.addUploaded(event.uploadedBytes)
            is ClientDownloadedEvent -> clientStatus.addDownloaded(event.downloadedBytes)
            is ClientRemainingEvent -> clientStatus.left = event.remainingBytes
            else -> logger.warn("unknown event [$event] for tracker worker")
        }
    }

    private fun findPeers3(context: WorkerContext) {
        val interval = findPeers2()
        context.offer("foo", PeerListEvent(emptyList()))
        context.delay(interval, FindPeersEvent)
    }

    private fun findPeers2(): Long = try {
        val r = firstRequest
        val response = runBlocking {
            tracker.findPeers(clientStatus, r)
        }
        logger.info("got ${response.peers.size} peers from tracker, refresh ${response.interval}s later")
//        eventBus.offer(QueueName.HANDSHAKE_ALL, PeerListEvent(response.peers))
        if (r) {
            firstRequest = false
        }
        response.interval * 1000L
    } catch (e: TrackerException) {
        logger.warn("failed to find peers, caused by [${e.message}], wait for ${transmissionConfig.trackerRetryTimeout}ms to retry")
        transmissionConfig.trackerRetryTimeout
    }
//
//    private fun triggerFindPeer() {
//        eventBus.offer(QueueName.TRACKER, FindPeersEvent)
//    }

//    private suspend fun findPeers(): Long = try {
//        val r = firstRequest
//        val response = tracker.findPeers(clientStatus, r)
//        logger.info("got ${response.peers.size} peers from tracker, refresh ${response.interval}s later")
//        eventBus.offer(QueueName.HANDSHAKE_ALL, PeerListEvent(response.peers))
//        if (r) {
//            firstRequest = false
//        }
//        response.interval * 1000L
//    } catch (e: TrackerException) {
//        logger.warn("failed to find peers, caused by [${e.message}], wait for ${transmissionConfig.trackerRetryTimeout}ms to retry")
//        transmissionConfig.trackerRetryTimeout
//    }
}