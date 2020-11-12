package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.worker.EventBus
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelInitializer
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.LoggerFactory
import java.io.IOException

class Acceptor(
    private val port: Int,
    private val infoHash: ByteArray,
    private val selfPeerId: ByteArray,
    private val eventBus: EventBus,
    private val workerNioEventLoopGroup: NioEventLoopGroup
) : ChannelInitializer<SocketChannel>() {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val bossNioEventLoopGroup = NioEventLoopGroup(1)

    fun start() {
        val serverBootstrap = ServerBootstrap()
            .group(bossNioEventLoopGroup, workerNioEventLoopGroup)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(this)
        try {
            serverBootstrap.bind(port).sync()
            logger.debug("node listen on port $port")
        } catch (e: InterruptedException) {
            throw IOException("failed to bind port", e)
        }
    }

    override fun initChannel(ch: SocketChannel?) {
        val pipeline = ch!!.pipeline()
        pipeline.addLast(HandshakeFromRemoteHandler(infoHash, selfPeerId, eventBus))
    }

    fun stop() {
        bossNioEventLoopGroup.shutdownGracefully()
    }
}