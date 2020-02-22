package `in`.xnnyygn.bittorrent.file

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.peer.PeerSession

data class LoadPieceFromCacheEvent(val index: Int, val session: PeerSession) : Event