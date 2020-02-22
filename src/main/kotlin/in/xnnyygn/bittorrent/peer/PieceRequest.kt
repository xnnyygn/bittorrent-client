package `in`.xnnyygn.bittorrent.peer

data class PieceRequest(
    val index: Int,
    val begin: Long,
    val length: Long,
    val timestamp: Long = System.currentTimeMillis()
)