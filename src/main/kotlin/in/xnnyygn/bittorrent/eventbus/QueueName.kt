package `in`.xnnyygn.bittorrent.eventbus

enum class QueueName {
    TRACKER,
    HANDSHAKE_ALL,
    TRANSMISSION,
    CONNECTIONS,
    CONNECTOR,
    DOWNLOADER,
    UPLOADER,
    PIECE_CACHE,
    FILE
}