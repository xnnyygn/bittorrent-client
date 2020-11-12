package `in`.xnnyygn.bittorrent.process

abstract class Event

object EmptyEvent : Event()

internal object StartEvent : Event()
internal object StopEvent : Event()