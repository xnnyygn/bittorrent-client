package `in`.xnnyygn.bittorrent.eventbus

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

private data class EventQueueNode<T>(val event: T, var next: EventQueueNode<T>? = null)

private class Consumer<T>(val continuation: CancellableContinuation<T>) : CancellableContinuation<T> by continuation {
    companion object {
        const val RESUME_BY_INIT = 0
        const val RESUME_BY_PRODUCER = 1
        const val RESUME_BY_CONSUMER = 2
    }

    private val resumeBy = AtomicInteger(RESUME_BY_INIT)

    fun casResumeBy(newStatus: Int): Boolean = resumeBy.compareAndSet(RESUME_BY_INIT, newStatus)
}

class EventQueue<T> {
    private val top = AtomicReference<EventQueueNode<T>>(null)
    private val consumer = AtomicReference<Consumer<List<T>>>(null)

    fun offer(event: T) {
        if (trySignal(Consumer.RESUME_BY_PRODUCER, event)) return
        push(event)
        trySignal(Consumer.RESUME_BY_PRODUCER)
    }

    private fun trySignal(resumeBy: Int, event: T? = null): Boolean {
        val c = consumer.get()
        if (c == null || !c.casResumeBy(Consumer.RESUME_BY_PRODUCER)) {
            return false
        }
        val list = collect()
        if (event != null) list.addLast(event)
        resume(c, list)
        return true
    }

    private fun resume(c: Consumer<List<T>>, events: LinkedList<T>) {
        consumer.lazySet(null)
        c.resume(events)
    }

//    private fun offload(events: LinkedList<T>) {
//        val eventIterator = events.iterator()
//        if (!eventIterator.hasNext()) return
//        val bottom: EventQueueNode<T> = EventQueueNode(eventIterator.next())
//        var next: EventQueueNode<T> = bottom
//        for (event in eventIterator) {
//            next = EventQueueNode(event, next)
//        }
//        while (true) {
//            val t = top.get()
//            bottom.next = t
//            if (top.compareAndSet(t, next)) {
//                return
//            }
//        }
//    }

    private fun push(event: T) {
        while (true) {
            val t = top.get()
            val n = EventQueueNode(event, t)
            if (top.compareAndSet(t, n)) {
                return
            }
        }
    }

    private fun collect(): LinkedList<T> {
        val list = LinkedList<T>()
        while (true) {
            val t = top.get() ?: return list
            if (!top.compareAndSet(t, null)) {
                continue
            }
            var n: EventQueueNode<T>? = t
            while (n != null) {
                list.addFirst(n.event)
                n = n.next
            }
            return list
        }
    }

    suspend fun bulkPoll(): List<T> = suspendCancellableCoroutine sc@{ cont ->
        val list = collect()
        if (list.isNotEmpty()) {
            cont.resume(list)
            return@sc
        }
        val c = Consumer(cont)
        c.invokeOnCancellation { consumer.set(null) }
        consumer.set(c)
        if (top.get() != null && c.casResumeBy(Consumer.RESUME_BY_CONSUMER)) {
            resume(c, collect())
        }
    }
}