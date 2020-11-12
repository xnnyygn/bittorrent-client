package `in`.xnnyygn.bittorrent.worker

import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal interface WorkerExecutor {
    val worker: ManagedWorker

    fun submit(event: Event)
    fun delay(delay: Long, event: Event)
    fun shutdown()
}

internal class DefaultWorkerExecutor(
    private val parentScheduledExecutor: ScheduledExecutorService,
    private val parentExecutor: ExecutorService,
    override val worker: ManagedWorker
) : WorkerExecutor {
    @Volatile
    private var shutdown = false
    private val jobSet = SequentialJobSet()
    private val delayedJobSet = DelayedJobSet()

    override fun submit(event: Event) {
        if (shutdown) {
            return
        }
        jobSet.add(parentExecutor) {
            worker.handle(event)
        }
    }

    override fun delay(delay: Long, event: Event) {
        if (shutdown) {
            return
        }
        delayedJobSet.add(delay, parentScheduledExecutor) {
            submit(event)
        }
    }

    override fun shutdown() {
        shutdown = true
        delayedJobSet.cancelAll()
        jobSet.cancelAll()
    }

    private class SequentialJobSet {
        private val queue: Queue<() -> Unit> = LinkedList()
        private val jobs: Queue<Future<*>> = LinkedList()

        fun add(parentExecutor: ExecutorService, action: () -> Unit) {
            synchronized(this) {
                queue.offer(action)
                if (queue.size == 1) {
                    submit(parentExecutor, action)
                }
            }
        }

        private fun submit(parentExecutor: ExecutorService, action: () -> Unit) {
            val job = parentExecutor.submit {
                action()
                removeAndSubmitNext(parentExecutor)
            }
            jobs.offer(job)
        }

        private fun removeAndSubmitNext(parentExecutor: ExecutorService) {
            synchronized(this) {
                jobs.remove()
                queue.remove()
                val action = queue.poll()
                if (action != null) {
                    submit(parentExecutor, action)
                }
            }
        }

        fun cancelAll() {
            synchronized(this) {
                for (job in jobs) {
                    job.cancel(true)
                }
            }
        }
    }
}

private class DelayedJobSet {
    private var nextId = 1
    private val map = mutableMapOf<Int, ScheduledFuture<*>>()

    fun add(delay: Long, parentScheduledExecutor: ScheduledExecutorService, action: () -> Unit) {
        synchronized(this) {
            val id = nextId++
            val delayedJob = parentScheduledExecutor.schedule({
                remove(id)
                action()
            }, delay, TimeUnit.MILLISECONDS)
            map[id] = delayedJob
        }
    }

    private fun remove(id: Int) {
        synchronized(this) {
            map.remove(id)
        }
    }

    fun cancelAll() {
        synchronized(this) {
            for (delayedJob in map.values) {
                delayedJob.cancel(true)
            }
        }
    }
}

internal class PinnedWorkerExecutor(
    private val parentScheduledExecutor: ScheduledExecutorService,
    private val workerRunner: PinnedWorkerRunner,
    override val worker: ManagedWorker
) : WorkerExecutor {
    @Volatile
    private var shutdown = false
    private val delayedJobSet = DelayedJobSet()

    override fun submit(event: Event) {
        if (shutdown) {
            return
        }
        workerRunner.submit {
            worker.handle(event)
        }
    }

    override fun delay(delay: Long, event: Event) {
        if (shutdown) {
            return
        }
        delayedJobSet.add(delay, parentScheduledExecutor) {
            submit(event)
        }
    }

    override fun shutdown() {
        shutdown = true
        delayedJobSet.cancelAll()
    }
}

internal class PinnedWorkerRunner(name: String) {
    private val queue = LinkedBlockingQueue<() -> Unit>()

    @Volatile
    private var stop = false
    private val thread = Thread({ poll() }, name)

    private fun poll() {
        while (!stop) {
            val action = queue.poll()
            action()
        }
    }

    fun start() {
        thread.start()
    }

    fun submit(action: () -> Unit) {
        queue.offer(action)
    }

    fun stop() {
        stop = true
    }
}