package `in`.xnnyygn.bittorrent.process

internal class ProcessSystem : ProcessNode(mutableMapOf()) {
    fun addChild(
        name: ProcessName,
        process: Process
    ): ProcessReference {
        return doAddChild(this, name, process)
    }

    override fun send(event: Event, sender: ProcessReference) {
        // do nothing
    }

    override fun makeLocation(name: ProcessName): ProcessLocation {
        return ProcessLocation(listOf(name))
    }

    fun findChildConsumer(location: ProcessLocation): ManagedProcess? {
        // head + rest
        // init + last
        return null
    }

    fun start() {
        for (consumer in children.values) {
            consumer.handleEvent(StartEvent, this)
        }
    }

    fun submit(action: () -> Unit) {
    }

    fun stop() {
        // stop all actions
        stopChildren()
    }
}