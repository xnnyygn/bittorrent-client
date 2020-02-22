package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.eventbus.Event

data class ClientDownloadedEvent(val downloadedBytes: Long) : Event