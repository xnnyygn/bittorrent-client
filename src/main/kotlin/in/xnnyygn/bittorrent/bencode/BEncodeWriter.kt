package `in`.xnnyygn.bittorrent.bencode

import java.io.OutputStream

private class ArrayStack<T>(private val delegate: ArrayList<T> = ArrayList()) {

    fun push(b: T) = delegate.add(b)

    fun clear() = delegate.clear()

    fun reverseView() = delegate.asReversed()
}

// not tested
class BEncodeWriter(private val element: BEncodeElement, private val output: OutputStream) {

    private val numberBuffer = ArrayStack<Int>()

    fun write() = writeElement(element)

    private fun writeElement(element: BEncodeElement) {
        when (element) {
            is NumberElement -> writeNumberElement(element.value)
            is ByteStringElement -> writeByteStringElement(element.bytes)
            is ListElement -> {
                writeByte(BEncodeBytes.BYTE_L)
                element.elements.forEach { writeElement(it) }
                writeByte(BEncodeBytes.BYTE_E)
            }
            is DictionaryElement -> {
                writeByte(BEncodeBytes.BYTE_D)
                for ((k, v) in element) {
                    writeByteStringElement(k.toByteArray())
                    writeElement(v)
                }
                writeByte(BEncodeBytes.BYTE_E)
            }
        }
    }

    private fun writeByteStringElement(bytes: ByteArray) {
        writeNumber(bytes.size.toLong())
        writeByte(BEncodeBytes.BYTE_COLON)
        writeBytes(bytes)
    }

    private fun writeNumberElement(value: Long) {
        writeByte(BEncodeBytes.BYTE_I)
        writeNumber(value)
        writeByte(BEncodeBytes.BYTE_E)

    }

    private fun writeNumber(value: Long) {
        var n = value
        if (n < 0) {
            writeByte(BEncodeBytes.BYTE_MINUS)
            n = -n
        }
        if (n == 0L) {
            writeByte(BEncodeBytes.BYTE_ZERO)
        } else {
            while (n > 0) {
                val d = (n % 10).toInt()
                numberBuffer.push(d)
                n /= 10
            }
            for (d in numberBuffer.reverseView()) {
                writeByte(d)
            }
            numberBuffer.clear()
        }
    }

    private fun writeByte(b: Int) {
        output.write(b)
    }

    private fun writeBytes(bytes: ByteArray) {
        output.write(bytes)
    }
}