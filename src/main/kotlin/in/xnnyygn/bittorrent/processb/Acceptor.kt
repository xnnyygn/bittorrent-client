package `in`.xnnyygn.bittorrent.processb

import `in`.xnnyygn.bittorrent.process.ChildrenStrategy
import `in`.xnnyygn.bittorrent.process.Event
import `in`.xnnyygn.bittorrent.process.Process
import `in`.xnnyygn.bittorrent.process.ProcessContext
import `in`.xnnyygn.bittorrent.process.ProcessExecutorStrategy
import `in`.xnnyygn.bittorrent.process.ProcessReference
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel

data class AcceptorInitializedEvent(
    val bossNioEventLoopGroup: NioEventLoopGroup,
    val connections: ProcessReference
) : Event()

class AcceptorInitializer(
    private val port: Int,
    private val workerNioEventLoopGroup: NioEventLoopGroup,
    private val connections: ProcessReference
) : Process() {
    override val executorStrategy: ProcessExecutorStrategy
        get() = ProcessExecutorStrategy.DIRECT
    override val childrenStrategy: ChildrenStrategy
        get() = ChildrenStrategy.EMPTY

    override fun start(context: ProcessContext, parent: ProcessReference) {
        val bossNioEventLoopGroup = NioEventLoopGroup(1)
        val serverBootstrap = ServerBootstrap()
            .group(bossNioEventLoopGroup, workerNioEventLoopGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel?) {
                    val pipeline = ch!!.pipeline()
//                    pipeline.addLast(HandshakeFromRemoteHandler(infoHash, selfPeerId, eventBus))
                }
            })
        serverBootstrap.bind(port).addListener { f ->
            if (f.isSuccess) {
                context.sendToParentAndStopSelf(
                    AcceptorInitializedEvent(
                        bossNioEventLoopGroup,
                        connections
                    )
                )
            }
            // TODO failed case
        }
    }

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
    }
}

class AcceptorProcess(
    private val bossNioEventLoopGroup: NioEventLoopGroup
) : Process() {
    override val executorStrategy: ProcessExecutorStrategy
        get() = ProcessExecutorStrategy.DIRECT
    override val childrenStrategy: ChildrenStrategy
        get() = ChildrenStrategy.EMPTY

    override fun handle(context: ProcessContext, event: Event, sender: ProcessReference) {
    }

    override fun stop(context: ProcessContext, parent: ProcessReference) {
        bossNioEventLoopGroup.shutdownGracefully()
    }
}