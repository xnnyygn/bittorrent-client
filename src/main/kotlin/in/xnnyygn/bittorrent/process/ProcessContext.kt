package `in`.xnnyygn.bittorrent.process

interface ProcessContext {
    val self: ProcessReference
    val parent: ProcessReference

    fun sendTo(location: ProcessLocation, event: Event, sender: ProcessReference)
    fun startChild(name: ProcessName, handler: Process): ProcessReference
    fun stopSelf()
    fun sendToParentAndStopSelf(event: Event)
}