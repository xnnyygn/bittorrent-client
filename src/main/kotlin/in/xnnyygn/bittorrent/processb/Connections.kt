package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessReference

class ConnectionsProcess(private val piecesStatus: PiecesStatus) : Process() {
    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
    }
}