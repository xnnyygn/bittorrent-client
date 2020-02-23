package `in`.xnnyygn.bittorrent

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

//fun main(args: Array<String>) {
//    if (args.isEmpty()) {
//        println("usage <torrent-file-path>")
//        return
//    }
//    val path = args[0]
//    val torrent = File(path).inputStream().use {
//        val parser = BEncodeReader(FileInputStream(File(args[0])))
//        val element = parser.parse()
//        Torrent.fromElement(element, parser.infoHash)
//    }
//    val pieceCount = torrent.pieceCount
//    val selfPeer = Peer(ByteArray(20), "localhost", 6881)
//
//    val localPiecesStatus = BitSetWithBufferPiecesStatus(BitSet(pieceCount), pieceCount, 10)
//    val clientStatus = ClientStatus(0, 0, torrent.allFileLength)
//
//    val handshakeProtocol = HandshakeProtocol(torrent.infoHash, selfPeer.id)
//    val eventBus = EventBus()
//
//    val trackerWorker =
//        TrackerWorker(torrent, selfPeer, clientStatus, eventBus)
//    val handshakeAllWorker =
//        HandshakeWorker(handshakeProtocol, eventBus)
//    val acceptorWorker =
//        AcceptorWorker(6881, handshakeProtocol, eventBus)
//    val transmissionWorker =
//        TransmissionWorker(pieceCount, localPiecesStatus, clientStatus, eventBus)
//    val pieceCacheWorker = PieceCacheWorker(eventBus)
//    val fileWorker = FileWorker(torrent, localPiecesStatus, clientStatus, eventBus)
//
//    runBlocking {
//        val trackerJob = launch(start = CoroutineStart.LAZY) { trackerWorker.start() }
//        val handshakeAllJob = launch { handshakeAllWorker.start() }
//        val acceptorJob = launch { acceptorWorker.start() }
//        val transmissionJob = launch { transmissionWorker.start() }
//        val pieceCacheJob = launch { pieceCacheWorker.start() }
//        val fileJob = launch { fileWorker.start() }
//        trackerJob.start()
//    }
//}

class Foo {
    @Volatile
    private var consumer: Continuation<Int>? = null

    fun signal(v: Int) {
        consumer?.resume(v)
        consumer = null
    }

    suspend fun await(): Int = suspendCoroutine { cont ->
        consumer = cont
    }
}

//fun main(): Unit {
//    val foo = Foo()
//    val executorCoroutineDispatcher = Executors.newFixedThreadPool(4).asCoroutineDispatcher()
//    runBlocking(executorCoroutineDispatcher) {
//        println("start in ${Thread.currentThread()}")
//        launch {
//            println("complete in ${Thread.currentThread()}")
//            Thread.sleep(1000L)
//            foo.signal(1)
//        }
//        val i = foo.await()
//        println("got $i in ${Thread.currentThread()}")
//    }
//    executorCoroutineDispatcher.close()
//}

//fun main() {
//    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
//    runBlocking(Dispatchers.Default) {
//        println("before withContext")
//        // withContext not work, it will wait until job finishes
//        launch(dispatcher) {
//            delay(1000L)
//            println("done withContext")
//        }
//        println("after withContext")
//    }
//    dispatcher.close()
//}

//private class FooWorker(eventBus: EventBus, workerScheduler: WorkerScheduler) :
//    AbstractWorker(QueueName.HANDSHAKE, eventBus, workerScheduler) {
//
//    override suspend fun handle(event: Event) {
//        when (event) {
//            is PeerListEvent -> {
//                println("run in ${Thread.currentThread()}")
//                println(event.peers)
//            }
//        }
//    }
//}

//fun main() {
////    val eventBus = EventBus()
////    val workerScheduler = WorkerScheduler(4)
////    val worker = FooWorker(eventBus, workerScheduler)
////    val random = Random()
////    runBlocking {
////        val workerJob = launch { worker.start() }
////        repeat(5) {
////            eventBus.offer(QueueName.HANDSHAKE, PeerListEvent(emptyList()))
////        }
////        delay(100)
////        workerJob.cancel()
////    }
////    workerScheduler.close()
//}

suspend fun foo() = withContext(NonCancellable) {

}

fun main() {

}