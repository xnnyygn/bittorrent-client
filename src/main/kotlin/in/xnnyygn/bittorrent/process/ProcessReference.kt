package `in`.xnnyygn.bittorrent.process

interface ProcessReference {
    fun send(event: Event, sender: ProcessReference)
}