package `in`.xnnyygn.bittorrent.worker

interface Worker {
    fun start(context: WorkerContext) {}
    fun handle(event: Event, context: WorkerContext)
    fun stop(context: WorkerContext) {}
}

abstract class ContextAwareWorker : Worker {
    private var _context: WorkerContext? = null
    protected val context: WorkerContext
        get() = _context!!

    override fun start(context: WorkerContext) {
        _context = context
        start()
    }

    protected open fun start() {}

    override fun handle(event: Event, context: WorkerContext) {
        handle(event)
    }

    protected abstract fun handle(event: Event)

    override fun stop(context: WorkerContext) {
        stop()
    }

    protected open fun stop() {}
}

internal class ManagedWorker(
    internal val context: WorkerContext,
    private val delegate: Worker
) {
    fun start() {
        delegate.start(context)
    }

    fun handle(event: Event) {
        delegate.handle(event, context)
    }

    fun stop() {
        delegate.stop(context)
    }
}