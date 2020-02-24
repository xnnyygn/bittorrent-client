package `in`.xnnyygn.bittorrent.peer

import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

fun main() {
    val socket = Socket()
    socket.connect(InetSocketAddress("localhost", 6881))
    val infoHash = ByteArray(20)
    val output = DataOutputStream(socket.getOutputStream())
    output.write(19)
    output.write("Bittorrent protocol".toByteArray())
    output.writeLong(0)
    output.write(infoHash)
    output.flush()
    val input = socket.getInputStream()
    val bytes = ByteArray(20)
    input.read(bytes)
    println(bytes.contentToString())
    socket.close()
}