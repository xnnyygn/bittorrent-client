package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.peer.PeerSession
import java.nio.ByteBuffer

data class PieceLoadedEvent(val index: Int, val buffers: List<ByteBuffer>, val session: PeerSession) : Event