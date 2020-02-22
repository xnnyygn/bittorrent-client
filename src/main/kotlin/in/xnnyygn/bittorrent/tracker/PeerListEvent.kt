package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.peer.Peer
import `in`.xnnyygn.bittorrent.eventbus.Event

data class PeerListEvent(val peers: List<Peer>) : Event