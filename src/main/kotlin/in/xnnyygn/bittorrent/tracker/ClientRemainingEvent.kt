package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.eventbus.Event

data class ClientRemainingEvent(val remainingBytes: Long) : Event