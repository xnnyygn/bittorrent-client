package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessReference

class DownloaderProcess(
    private val localPiecesStatus: PiecesStatus,
    private val uncheckedPieceSaver: ProcessReference
) : Process() {
    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
    }
}