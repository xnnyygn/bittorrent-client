package `in`.xnnyygn.bittorrent

import org.junit.Test
import java.nio.ByteBuffer

class ByteBufferTest {
    @Test
    fun testDecoderPattern() {
        val buffer = ByteBuffer.allocate(64)
        printBufferStat(buffer)
        // write mode
        buffer.putInt(1)
        printBufferStat(buffer)
        buffer.flip()
        printBufferStat(buffer)
        // read mode
        buffer.get()
        printBufferStat(buffer)
        buffer.compact()
        // write mode
        printBufferStat(buffer)
        buffer.putInt(2)
        printBufferStat(buffer)
    }

    private fun printBufferStat(buffer: ByteBuffer) {
        println("------------------------------")
        println("position ${buffer.position()}")
        println("remaining: ${buffer.remaining()}")
        println("limit: ${buffer.limit()}")
        println("capacity: ${buffer.capacity()}")
    }

    @Test
    fun testEncoderPattern() {
        val buffer = ByteBuffer.allocate(64)
        printBufferStat(buffer)
        // write mode
        buffer.putInt(1)
        printBufferStat(buffer)
        buffer.flip()
        printBufferStat(buffer)
        // read mode
        buffer.get()
        printBufferStat(buffer)
        if(buffer.hasRemaining()) {
            // incomplete read
            // append if buffer has capacity

        } else {
            // compact
        }
    }
}