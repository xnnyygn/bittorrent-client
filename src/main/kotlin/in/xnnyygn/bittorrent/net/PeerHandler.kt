package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.transmission.download.DownloadEventCollector
import `in`.xnnyygn.bittorrent.transmission.download.PeerDownloadProtocol
import `in`.xnnyygn.bittorrent.transmission.upload.PeerUploadProtocol
import `in`.xnnyygn.bittorrent.transmission.upload.UploadEventCollector
import `in`.xnnyygn.bittorrent.worker.EventBus
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter

// TODO package constructor parameters to a protocol factory
class PeerHandler(
    private val torrent: Torrent,
    private val transmissionConfig: TransmissionConfig,
    private val connection: PeerConnection,
    private val localPiecesStatus: PiecesStatus, // no need to update
    private val clientStatus: ClientStatus,
    private val eventBus: EventBus
) : ChannelInboundHandlerAdapter() {

    private var downloadProtocol: PeerDownloadProtocol? = null
    private var uploadProtocol: PeerUploadProtocol? = null

    // TODO pass peer protocol factory and local pieces status via channel
    override fun handlerAdded(ctx: ChannelHandlerContext?) {
        val channel = ctx!!.channel()
        downloadProtocol = PeerDownloadProtocol(
            clientStatus,
            channel,
            torrent,
            transmissionConfig,
            DownloadEventCollector(connection, eventBus)
        )
        uploadProtocol = PeerUploadProtocol(
            clientStatus,
            channel,
            transmissionConfig,
            UploadEventCollector(connection, eventBus)
        )

        // start
        if (!localPiecesStatus.isEmpty) {
            channel.write(BitFieldMessage(localPiecesStatus.toByteArray()))
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext?, cause: Throwable?) {
        cause?.printStackTrace()
        ctx!!.close()
    }

    override fun channelRead(ctx: ChannelHandlerContext?, msg: Any?) {
        when (msg) {
            is ChokeMessage -> downloadProtocol!!.chokedByRemote()
            is UnchokeMessage -> downloadProtocol!!.unchokeByRemote()
            is InterestedMessage -> uploadProtocol!!.interestedByRemote()
            is UninterestedMessage -> uploadProtocol!!.uninterestedByRemote()
            is BitFieldMessage -> downloadProtocol!!.bitField(msg.bytes)
            is HaveMessage -> downloadProtocol!!.have(msg.index)
            is RequestMessage -> uploadProtocol!!.request(msg.index, msg.begin, msg.length)
            is PieceMessage -> downloadProtocol!!.piece(msg.index, msg.begin, msg.piece)
            is CancelMessage -> uploadProtocol!!.cancel(msg.index, msg.begin, msg.length)
        }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext?, evt: Any?) {
        when (evt) {
            is RequestPieceUserEvent -> downloadProtocol!!.requestPiece(evt.index)
            is CancelRequestUserEvent -> downloadProtocol!!.cancelRequest(evt.index)
            is SendUninterestUserEvent -> downloadProtocol!!.sendUninterest()
            is SendUnchokeUserEvent -> uploadProtocol!!.sendUnchoke()
            is SendChokeUserEvent -> uploadProtocol!!.sendChoke()
            is PieceLoadedUserEvent -> uploadProtocol!!.pieceLoaded(evt.piece)
            is PieceNotFoundUserEvent -> uploadProtocol!!.pieceNotFound(evt.index)
        }
    }
}