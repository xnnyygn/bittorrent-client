package `in`.xnnyygn.bittorrent.process

enum class ProcessExecutorStrategy {
    SEQUENTIAL,
    UNCONFINED,
    DIRECT
}

enum class ChildrenStrategy {
    DYNAMIC,
    STATIC,
    EMPTY
}

abstract class Process {
    open val executorStrategy: ProcessExecutorStrategy = ProcessExecutorStrategy.SEQUENTIAL
    open val childrenStrategy: ChildrenStrategy = ChildrenStrategy.DYNAMIC

    open fun start(context: ProcessContext, parent: ProcessReference) {}
    abstract fun handle(context: ProcessContext, event: Event, sender: ProcessReference)
    open fun stop(context: ProcessContext, parent: ProcessReference) {}
}