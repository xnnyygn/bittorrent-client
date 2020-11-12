package `in`.xnnyygn.bittorrent.process

import java.util.concurrent.ConcurrentHashMap

internal abstract class ProcessNode(
    protected val children: MutableMap<ProcessName, ManagedProcess>
) : ProcessReference {
    protected fun doAddChild(
        system: ProcessSystem,
        name: ProcessName,
        handler: Process
    ): ManagedProcess {
        val childLocation = makeLocation(name)
        val grandChildren: MutableMap<ProcessName, ManagedProcess> = when (handler.childrenStrategy) {
            ChildrenStrategy.DYNAMIC -> ConcurrentHashMap()
            ChildrenStrategy.STATIC -> mutableMapOf()
            ChildrenStrategy.EMPTY -> mutableMapOf()
        }
        val executor: ProcessExecutor = when (handler.executorStrategy) {
            ProcessExecutorStrategy.SEQUENTIAL -> SequentialProcessExecutor()
            else -> DirectProcessExecutor
        }
        val child = ManagedProcess(system, this, childLocation, handler, executor, grandChildren)
        children[name] = child
        child.handleEvent(StartEvent, this)
        return child
    }

    protected fun doStartChild(
        system: ProcessSystem,
        name: ProcessName,
        handler: Process
    ): ManagedProcess {
        val client = doAddChild(system, name, handler)
        client.handleEvent(StartEvent, this)
        return client
    }

    protected abstract fun makeLocation(name: ProcessName): ProcessLocation

    fun stopChildren() {
        for (consumer in children.values) {
            consumer.stop()
        }
        children.clear()
    }

    fun removeChild(name: ProcessName): Boolean {
        return (children.remove(name) != null)
    }
}