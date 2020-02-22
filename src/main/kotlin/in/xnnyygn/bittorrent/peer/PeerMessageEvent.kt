package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event

data class PeerMessageEvent(val message: PeerMessage, val session: PeerSession) : Event