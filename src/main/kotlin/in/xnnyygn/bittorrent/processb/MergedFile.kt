package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.file.BitSetPiecesStatus
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.process.ChildrenStrategy
import `in`.xnnyygn.bittorrent.process.EmptyEvent
import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessExecutorStrategy
import `in`.xnnyygn.bittorrent.process.ProcessReference
import `in`.xnnyygn.bittorrent.process.StringProcessName
import java.util.concurrent.atomic.AtomicInteger

data class MergedFileInitializedEvent(
    val piecesStatus: PiecesStatus,
    val uncheckedPieceSaver: ProcessReference
) : Event()

data class LoadPieceEvent(val index: Int) : Event()
data class PieceLoadedEvent(val index: Int, val requester: ProcessReference) : Event()

data class RegisterPiecesStatusListenersEvent(val listeners: List<ProcessReference>) : Event()
data class PiecesStatusListenersRegisteredEvent(val listeners: List<ProcessReference>) : Event()

internal data class SavePieceEvent(val index: Int) : Event()
internal data class PieceSavedEvent(val index: Int) : Event()

class MergedFileProcess : Process() {
    private val piecesStatusListeners = mutableSetOf<ProcessReference>()

    override fun start(context: ProcessContext, parent: ProcessReference) {
        // open files
        val file1 = context.startChild(
            StringProcessName("file1"),
            DiskFileProcess()
        )
        val file2 = context.startChild(
            StringProcessName("file1"),
            DiskFileProcess()
        )
        val file3 = context.startChild(
            StringProcessName("file1"),
            DiskFileProcess()
        )
        val fileDispatcher = FileDispatcher(
            mapOf(
                1 to listOf(file1),
                2 to listOf(file2),
                3 to listOf(file3)
            )
        )
        // TODO load manifest file
        val uncheckedPieceSaver =
            context.startChild(
                StringProcessName("uncheckedPieceSaver"),
                UncheckedPieceSaver(fileDispatcher)
            )
        parent.send(
            MergedFileInitializedEvent(
                BitSetPiecesStatus(
                    1024
                ), uncheckedPieceSaver
            ), context.self
        )
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        when (event) {
            is RegisterPiecesStatusListenersEvent -> {
                piecesStatusListeners.addAll(event.listeners)
                sender.send(
                    PiecesStatusListenersRegisteredEvent(
                        event.listeners
                    ), context.self
                )
            }
            is LoadPieceEvent -> {
                context.startChild(
                    StringProcessName("pieceLoadTask${event.index}"),
                    PieceLoadTask(event.index, emptyList(), sender)
                )
            }
            is PieceLoadedEvent -> {
                event.requester.send(event, context.self)
            }
            is PieceSavedEvent -> {
                // localPiecesStatus.addPiece(event.index)
                for (listener in piecesStatusListeners) {
                    listener.send(event, context.self)
                }
            }
        }
    }
}

internal class PieceLoadTask(
    private val index: Int,
    private val files: List<ProcessReference>,
    private val requester: ProcessReference
) : Process() {
    private val countdown = AtomicInteger(files.size)

    override val executorStrategy: ProcessExecutorStrategy
        get() = ProcessExecutorStrategy.DIRECT

    override val childrenStrategy: ChildrenStrategy
        get() = ChildrenStrategy.EMPTY

    override fun start(context: ProcessContext, parent: ProcessReference) {
        for (file in files) {
            file.send(EmptyEvent, context.self)
        }
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        if (countdown.decrementAndGet() <= 0) {
            context.sendToParentAndStopSelf(
                PieceLoadedEvent(
                    index,
                    requester
                )
            )
        }
    }
}

// SEQUENTIAL OR DIRECT?
internal class DiskFileProcess : Process() {
    override val childrenStrategy: ChildrenStrategy
        get() = ChildrenStrategy.EMPTY

    override fun start(context: ProcessContext, parent: ProcessReference) {
        // TODO open file
        // send file opened
        parent.send(EmptyEvent, context.self)
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        // save piece slice
        // load piece
        TODO("Not yet implemented")
    }
    // TODO stop to close file
}

// TODO rename to FileRegionMapping
internal class FileDispatcher(private val map: Map<Int, List<ProcessReference>>) {
    fun regionsOf(pieceIndex: Int): List<ProcessReference> {
        return emptyList()
    }
}

internal class UncheckedPieceSaver(private val fileDispatcher: FileDispatcher) : Process() {
    override val executorStrategy: ProcessExecutorStrategy
        get() = ProcessExecutorStrategy.DIRECT

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        when (event) {
            is SavePieceEvent -> {
                val index = event.index
                val regions = fileDispatcher.regionsOf(index)
                if (regions.isEmpty()) {
                    // do stuff
                    sender.send(EmptyEvent, context.self)
                    return
                }
                context.startChild(
                    StringProcessName("pieceSaveTask$index"),
                    PieceSaveTask(index, regions)
                )
            }
            is PieceSavedEvent -> {
                context.parent.send(event, context.self)
            }
        }
    }
}

internal class PieceSaveTask(private val index: Int, private val regions: List<ProcessReference>) : Process() {
    private val countdown = AtomicInteger(regions.size)

    override val executorStrategy: ProcessExecutorStrategy
        get() = ProcessExecutorStrategy.DIRECT

    override val childrenStrategy: ChildrenStrategy
        get() = ChildrenStrategy.EMPTY

    override fun start(context: ProcessContext, parent: ProcessReference) {
        for (region in regions) {
            region.send(EmptyEvent, context.self)
        }
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
        if (countdown.decrementAndGet() <= 0) {
            context.sendToParentAndStopSelf(PieceSavedEvent(index))
        }
    }
}
