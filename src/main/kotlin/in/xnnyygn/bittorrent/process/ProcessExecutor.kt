package `in`.xnnyygn.bittorrent.process

internal interface ProcessExecutor {
    fun execute(action: () -> Unit)
}

internal class SequentialProcessExecutor : ProcessExecutor {
    override fun execute(action: () -> Unit) {
        TODO()
    }
}

internal object DirectProcessExecutor : ProcessExecutor {
    override fun execute(action: () -> Unit) {
        action()
    }
}