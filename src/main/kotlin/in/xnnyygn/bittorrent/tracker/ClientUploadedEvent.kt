package `in`.xnnyygn.bittorrent.tracker

import `in`.xnnyygn.bittorrent.eventbus.Event

data class ClientUploadedEvent(val uploadedBytes: Long): Event