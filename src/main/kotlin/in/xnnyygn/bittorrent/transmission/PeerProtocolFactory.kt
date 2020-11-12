package `in`.xnnyygn.bittorrent.transmission

//class PeerProtocolFactory(
//    private val transmissionTask: TransmissionTask,
//    private val connection: PeerConnection,
//    private val clientStatus: ClientStatus,
//    private val eventBus: EventBus
//) {
//    fun createDownloadProtocol(channel: Channel): PeerDownloadProtocol {
//        return PeerDownloadProtocol(
//            clientStatus,
//            channel,
//            transmissionTask,
//            DownloadEventCollector(connection, eventBus)
//        )
//    }
//
//    fun createUploadProtocol(channel: Channel): PeerUploadProtocol {
//        return PeerUploadProtocol(
//            clientStatus,
//            channel,
//            transmissionTask.config,
//            UploadEventCollector(connection, eventBus)
//        )
//    }
//}