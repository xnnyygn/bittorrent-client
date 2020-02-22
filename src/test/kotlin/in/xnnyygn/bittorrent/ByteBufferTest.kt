package `in`.xnnyygn.bittorrent

import org.junit.Ignore
import org.junit.Test
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

class ByteBufferTest {
    @Test
    @Ignore
    fun testWriteThenRead() {
        val buffer = ByteBuffer.allocate(64)
        printBufferStat(buffer)
        buffer.putInt(1)
        printBufferStat(buffer)
        buffer.flip()
        printBufferStat(buffer)
        buffer.getInt()
        printBufferStat(buffer)
        try {
            buffer.get()
        } catch (e: BufferUnderflowException) {
            println("buffer overflow")
        }
        buffer.position(0)
        printBufferStat(buffer)
    }

    private fun printBufferStat(buffer: ByteBuffer) {
        println("------------------------------")
        println("capacity: ${buffer.capacity()}")
        println("limit: ${buffer.limit()}")
        println("remaining: ${buffer.remaining()}")
        println("hasRemaining: ${buffer.hasRemaining()}")
        println("position ${buffer.position()}")
    }
}