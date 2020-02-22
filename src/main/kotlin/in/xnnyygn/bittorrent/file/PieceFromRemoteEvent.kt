package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.eventbus.Event
import java.nio.ByteBuffer

data class PieceFromRemoteEvent(val index: Int, val begin: Long, val piece: ByteBuffer) : Event