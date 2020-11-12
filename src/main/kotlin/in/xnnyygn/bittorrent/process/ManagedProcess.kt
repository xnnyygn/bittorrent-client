package `in`.xnnyygn.bittorrent.process

internal class ManagedProcess(
    protected val system: ProcessSystem,
    override val parent: ProcessNode,
    protected val location: ProcessLocation,
    protected val process: Process,
    protected val executor: ProcessExecutor,
    children: MutableMap<ProcessName, ManagedProcess>
) : ProcessNode(children), ProcessContext {
    fun handleEvent(event: Event, sender: ProcessReference) {
        executor.execute {
            when (event) {
                StartEvent -> process.start(this, sender)
                StopEvent -> process.stop(this, sender)
                else -> process.handle(this, event, sender)
            }
        }
    }

    override fun send(event: Event, sender: ProcessReference) {
        handleEvent(event, sender)
    }

    override val self: ProcessReference
        get() = this

    override fun sendTo(location: ProcessLocation, event: Event, sender: ProcessReference) {
        val consumer = system.findChildConsumer(location) ?: return
        consumer.handleEvent(event, sender)
    }

    override fun startChild(
        name: ProcessName,
        handler: Process
    ): ProcessReference {
        return doStartChild(system, name, handler)
    }

    override fun makeLocation(name: ProcessName): ProcessLocation {
        return location.append(name)
    }

    fun stop() {
        stopChildren()
        handleEvent(StopEvent, parent)
    }

    override fun stopSelf() {
        handleEvent(StopEvent, parent)
        parent.removeChild(location.name)
    }

    override fun sendToParentAndStopSelf(event: Event) {
        parent.send(event, this)
        stopSelf()
    }
}