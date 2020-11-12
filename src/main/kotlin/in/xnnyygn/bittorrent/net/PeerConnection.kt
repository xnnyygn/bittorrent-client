package `in`.xnnyygn.bittorrent.net

import `in`.xnnyygn.bittorrent.Torrent
import `in`.xnnyygn.bittorrent.file.FilePieceLike
import `in`.xnnyygn.bittorrent.file.PiecesStatus
import `in`.xnnyygn.bittorrent.tracker.ClientStatus
import `in`.xnnyygn.bittorrent.tracker.Peer
import `in`.xnnyygn.bittorrent.transmission.TransmissionConfig
import `in`.xnnyygn.bittorrent.worker.EventBus
import io.netty.channel.Channel

sealed class UserEvent
data class RequestPieceUserEvent(val index: Int) : UserEvent()
data class CancelRequestUserEvent(val index: Int) : UserEvent()
object SendUninterestUserEvent : UserEvent()
object SendUnchokeUserEvent : UserEvent()
object SendChokeUserEvent : UserEvent()
data class PieceLoadedUserEvent(val piece: FilePieceLike) : UserEvent()
data class PieceNotFoundUserEvent(val index: Int) : UserEvent()

sealed class PeerConnection(protected val channel: Channel) {

    fun start(
        torrent: Torrent,
        transmissionConfig: TransmissionConfig,
        localPiecesStatus: PiecesStatus,
        clientStatus: ClientStatus,
        eventBus: EventBus
    ) {
        channel.pipeline().addLast(
            PeerDecoder(),
            PeerEncoder(),
            PeerHandler(torrent, transmissionConfig, this, localPiecesStatus, clientStatus, eventBus)
        )
    }

    fun requestPiece(index: Int) = send(RequestPieceUserEvent(index))
    fun cancelRequest(index: Int) = send(CancelRequestUserEvent(index))
    fun sendUninterest() = send(SendUninterestUserEvent)
    fun sendUnchoke() = send(SendUnchokeUserEvent)
    fun sendChoke() = send(SendChokeUserEvent)
    fun pieceLoaded(piece: FilePieceLike) = send(PieceLoadedUserEvent(piece))
    fun pieceNotFound(index: Int) = send(PieceNotFoundUserEvent(index))

    private fun send(event: UserEvent) {
        channel.pipeline().fireUserEventTriggered(event)
    }
}

class OutgoingPeerConnection(val peer: Peer, channel: Channel) : PeerConnection(channel)
class IncomingPeerConnection(channel: Channel) : PeerConnection(channel)