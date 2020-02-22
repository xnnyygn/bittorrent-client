package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event

data class PeerDisconnectedEvent(val peer: Peer) : Event