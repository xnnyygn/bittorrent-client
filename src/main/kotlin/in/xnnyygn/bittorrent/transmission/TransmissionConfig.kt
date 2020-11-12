package `in`.xnnyygn.bittorrent.transmission

class TransmissionConfig(
    val pieceSliceLength: Int = 1 shl 14, // 16KB?
    val maxRequestsPerConnection: Int = 3,
    val requestSentTimeout: Long = 3000,
    // TODO rename
    val maxPieceAttempts: Int = 3,
    val maxUploadConnections: Int = 4,
    val maxPieceSliceLength: Int = 1 shl 14,
    val trackerRetryTimeout: Long = 3000,
    val maxOutgoingConnections: Int = 10,
    val port: Int = 6881
)