package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.process.ChildrenStrategy
import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessReference
import `in`.xnnyygn.bittorrent.tracker.Peer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class PeerListEvent(val peers: List<Peer>) : Event()

internal object UpdatePeersEvent : Event()

// client status
class TrackerProcess(private val connector: ProcessReference) : Process() {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()

    override val childrenStrategy: ChildrenStrategy
        get() = ChildrenStrategy.EMPTY

    override fun start(context: ProcessContext, parent: ProcessReference) {
        updatePeers(context)
    }

    private fun updatePeers(context: ProcessContext) {
        // update peers
        val peers = emptyList<Peer>()
        connector.send(PeerListEvent(peers), context.self)
        scheduler.schedule({
            context.self.send(UpdatePeersEvent, context.self)
        }, 2, TimeUnit.SECONDS)
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        when (event) {
            is UpdatePeersEvent -> updatePeers(context)
        }
    }

    override fun stop(context: ProcessContext, parent: ProcessReference) {
        scheduler.shutdown()
    }
}