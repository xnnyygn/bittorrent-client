package `in`.xnnyygn.bittorrent.peer

import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.ServerSocket

fun main() {
    val serverSocket = ServerSocket()
    serverSocket.bind(InetSocketAddress(6881))
    println("server started")
    while (true) {
        val socket = serverSocket.accept()
        println("new connection")
        val input = DataInputStream(socket.getInputStream())
        val bytes = ByteArray(48)
        input.readFully(bytes)
        println("read ${bytes.contentToString()}")
        val output = socket.getOutputStream()
        val peerId = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
        output.write(peerId, 0, 10)
        Thread.sleep(500)
        output.write(peerId, 10, 10)
        socket.close()
    }
}