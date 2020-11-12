package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.worker.Event

internal data class ClientDownloadedEvent(val downloadedBytes: Int) :
    Event
internal data class ClientUploadedEvent(val uploadedBytes: Int) :
    Event
internal data class ClientRemainingEvent(val remainingBytes: Long) :
    Event

// TODO rename?
class ClientStatus(
    private var _uploaded: Long = 0L,
    private var _downloaded: Long = 0L,
    private var _left: Long
) {
    val uploaded: Long
        get() = _uploaded
    val downloaded: Long
        get() = _downloaded
    var left: Long
        get() = _left
        internal set(value) {
            _left = value
        }

    internal fun addDownloaded(bytes: Int) {
        _downloaded += bytes
    }

    internal fun addUploaded(bytes: Int) {
        _uploaded += bytes
    }

    fun downloaded(bytes: Int) {
//        eventBus.offer(QueueName.TRACKER, ClientDownloadedEvent(bytes))
    }

    fun uploaded(bytes: Int) {
//        eventBus.offer(QueueName.TRACKER, ClientUploadedEvent(bytes))
    }

    fun remaining(bytes: Long) {
//        eventBus.offer(QueueName.TRACKER, ClientRemainingEvent(bytes))
    }
}