package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.peer.PeerConnection

data class PeerConnectionEvent(val connection: PeerConnection) : Event