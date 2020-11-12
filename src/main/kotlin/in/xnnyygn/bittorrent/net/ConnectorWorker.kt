package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.tracker.Peer
import `in`.xnnyygn.bittorrent.tracker.PeerListEvent
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.worker.ContextAwareWorker
import `in`.xnnyygn.bittorrent.worker.Event
import `in`.xnnyygn.bittorrent.worker.QueueNames
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import java.util.LinkedList
import java.util.Queue

private data class PeerDisconnectedEvent(val peer: Peer) : Event

private class FromLocalChannelInitializer(
    private val peer: Peer,
    private val infoHash: ByteArray,
    private val listener: (OutgoingPeerConnection) -> Unit
) : ChannelInitializer<SocketChannel>() {

    override fun initChannel(ch: SocketChannel) {
        val pipeline = ch.pipeline()
        pipeline.addLast(HandshakeFromLocalHandler(infoHash, peer, listener))
    }
}

class ConnectorWorker(
    private val infoHash: ByteArray,
    private val workerNioEventLoopGroup: NioEventLoopGroup,
    private val transmissionConfig: TransmissionConfig
) : ContextAwareWorker() {
    private val pendingPeers: Queue<Peer> = LinkedList()
    private val connectingPeers = mutableSetOf<Peer>()
    private val connectedPeers = mutableSetOf<Peer>()

//    override suspend fun start() = coroutineScope {
//        while (isActive) {
//            val events = eventBus.bulkPoll(QueueName.HANDSHAKE_ALL)
//            for (event in events) {
//                when (event) {
//                    is PeerListEvent -> connectMore(event.peers)
//                    is PostHandshakeOutgoingConnectionEvent -> postHandshake(event.connection)
//                    is PeerDisconnectedEvent -> disconnected(event.peer)
//                }
//            }
//        }
//    }

    override fun handle(event: Event) {
        when (event) {
            is PeerListEvent -> connectMore(event.peers)
            is PostHandshakeOutgoingConnectionEvent -> postHandshake(event.connection)
            is PeerDisconnectedEvent -> disconnected(event.peer)
        }
    }

    private fun postHandshake(connection: OutgoingPeerConnection) {
        connectingPeers.remove(connection.peer)
        connectedPeers.add(connection.peer)
        context.offer(QueueNames.CONNECTIONS, PostHandshakeConnectionEvent(connection))
    }

    private fun disconnected(peer: Peer) {
        connectedPeers.remove(peer)
        connectMore()
    }

    private fun connectMore(peers: List<Peer> = emptyList()) {
        pendingPeers.addAll(peers)
        var connections = connectedPeers.size + connectingPeers.size
        while (connections < transmissionConfig.maxOutgoingConnections && !pendingPeers.isEmpty()) {
            val peer = pendingPeers.poll()!!
            connect(peer)
            connectingPeers.add(peer)
            connections++
        }
    }

    private fun connect(peer: Peer) {
        val bootstrap = Bootstrap()
            .group(workerNioEventLoopGroup)
            .channel(NioSocketChannel::class.java)
            .option(ChannelOption.TCP_NODELAY, true)
            .handler(FromLocalChannelInitializer(peer, infoHash) { connection ->
                context.offer(QueueNames.CONNECTIONS, PostHandshakeOutgoingConnectionEvent(connection))
            })
        val channel = bootstrap.connect(peer.ip, peer.port).channel()
        channel.closeFuture().addListener {
            context.offer(QueueNames.CONNECTOR, PeerDisconnectedEvent(peer))
        }
    }
}