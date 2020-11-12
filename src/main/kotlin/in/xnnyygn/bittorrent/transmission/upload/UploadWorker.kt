package `in`.xnnyygn.bittorrent.transmission.upload

import `in`.xnnyygn.bittorrent.net.PeerConnection
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.Worker
import `in`.xnnyygn.bittorrent.worker.WorkerContext
import java.util.LinkedList
import java.util.Queue

class UploadWorker(
    private val transmissionConfig: TransmissionConfig
) : Worker {
    private val uploadConnections = mutableSetOf<PeerConnection>()
    private val pendingConnections: Queue<PeerConnection> = LinkedList()

    override fun handle(event: Event, context: WorkerContext) {
        when (event) {
            is InterestedByRemoteUploadEvent -> interestedByRemote(event.connection)
            is UninterestedByRemoteUploadEvent -> uninterestedByRemote(event.connection)
        }
    }

    private fun uninterestedByRemote(connection: PeerConnection) {
        if (!uploadConnections.remove(connection)) {
            return
        }
        val candidate = pendingConnections.poll() ?: return
        addAndSendUnchoke(candidate)
    }

    private fun addAndSendUnchoke(connection: PeerConnection) {
        uploadConnections.add(connection)
        connection.sendUnchoke()
    }

    private fun interestedByRemote(connection: PeerConnection) {
        if (uploadConnections.contains(connection)) {
            return
        }
        if (uploadConnections.size >= transmissionConfig.maxUploadConnections) {
            pendingConnections.offer(connection)
        } else {
            addAndSendUnchoke(connection)
        }
    }
}