package `in`.xnnyygn.bittorrent.transmission

data class PieceSliceRequest(
    val index: Int,
    val begin: Int,
    val length: Int,
    val timestamp: Long = System.currentTimeMillis()
)