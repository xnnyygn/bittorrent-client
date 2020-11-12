package `in`.xnnyygn.bittorrent.file

interface FilePieceLike {
    val index: Int
    fun slice(offset: Int, length: Int): PieceSlice
}