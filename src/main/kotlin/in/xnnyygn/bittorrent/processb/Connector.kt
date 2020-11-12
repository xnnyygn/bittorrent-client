package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessReference

class ConnectorProcess(private val connections: ProcessReference) : Process() {
    override fun start(context: ProcessContext, parent: ProcessReference) {
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
    }

    override fun stop(context: ProcessContext, parent: ProcessReference) {
    }
}