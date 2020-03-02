package `in`.xnnyygn.bittorrent.peer

import `in`.xnnyygn.bittorrent.eventbus.Event
import `in`.xnnyygn.bittorrent.eventbus.EventQueue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive

private data class PeerMessageWriteEvent(val messages: List<PeerMessage>) : Event

private data class PeerActionEvent(val action: () -> Unit): Event

class PeerConnection(private val socket: PeerSocket) {
    private val eventQueue = EventQueue<Event>()
    private val reader = PeerSocketReader(socket)
    private val writer = PeerSocketWriter(socket)

    val peer: Peer?
        get() = (socket as? OutgoingPeerSocket)?.peer

    suspend fun eventLoop(handler: (List<PeerMessage>) -> Unit) = coroutineScope {
        reader.read(eventQueue)
        while (isActive) {
            for (event in eventQueue.bulkPoll()) {
                when (event) {
                    is PeerMessageReadEvent -> {
                        handler(event.messages)
                        reader.read(eventQueue)
                    }
                    is PeerMessageWriteEvent -> writer.write(event.messages)
                    is PeerActionEvent -> event.action()
                    is IoExceptionEvent -> throw event.cause
                    else -> throw IllegalStateException("unexpected event $event")
                }
            }
        }
    }

    fun write(vararg messages: PeerMessage) {
        eventQueue.offer(PeerMessageWriteEvent(messages.toList()))
    }

    fun runInEventLoop(action: () -> Unit) {
        eventQueue.offer(PeerActionEvent(action))
    }

    fun close() {
        socket.close()
    }
}