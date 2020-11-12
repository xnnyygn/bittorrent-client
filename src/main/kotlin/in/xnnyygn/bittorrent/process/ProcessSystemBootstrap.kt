package `in`.xnnyygn.bittorrent.process

class ProcessSystemBootstrap {
    private val system = ProcessSystem()

    fun addChild(name: ProcessName, process: Process): ProcessReference {
        return system.addChild(name, process)
    }

    fun start() {
        system.start()
    }

    fun stop() {
        system.stop()
    }
}