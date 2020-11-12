package `in`.xnnyygn.bittorrent.file

import io.netty.channel.Channel

interface PieceSlice {
    val offsetInPiece: Int
    val length: Int
    fun writeToChannel(channel: Channel)
    // TODO add writeToChannel(channel, offset, length)
}

fun checkOffsetAndLength(offset: Int, length: Int, limit: Int) {
    check((offset in (0 until limit)) && (length in (0..limit))) { "illegal offset $offset or length $length" }
}