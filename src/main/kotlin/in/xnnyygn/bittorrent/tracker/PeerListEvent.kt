package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.worker.Event

data class PeerListEvent(val peers: List<Peer>) : Event