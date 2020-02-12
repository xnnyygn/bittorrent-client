package `in`.xnnyygn.bittorrent

import java.io.InputStream

sealed class BEncodeElement

data class NumberElement(val value: Long) : BEncodeElement()

data class ByteStringElement(val bytes: ByteArray) : BEncodeElement() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteStringElement) return false

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString() = String(bytes)
}

class DictionaryElement(private val map: Map<String, BEncodeElement>) : BEncodeElement() {

    fun contains(key: String): Boolean = map.contains(key)

    fun getString(key: String): String = (map[key] as ByteStringElement).toString()

    fun getNumber(key: String): Long = (map[key] as NumberElement).value

    fun getByteString(key: String): ByteStringElement = (map[key] as ByteStringElement)

    fun getList(key: String): ListElement = (map[key] as ListElement)

    fun getDictionary(key: String): DictionaryElement = (map[key] as DictionaryElement)
}

data class ListElement(val elements: List<BEncodeElement>) : BEncodeElement()

private enum class BEncodeToken {
    DICTIONARY_START,
    LIST_START,
    BYTE_STRING_START,
    NUMBER_START,
    END,
    EOF,
    OTHER
}

private class SingleByteBufferInputStream(private val input: InputStream) {
    private var nextByte: Int? = null

    fun read(): Int {
        if (nextByte != null) {
            val b: Int = nextByte!!
            nextByte = null
            return b
        }
        return input.read()
    }

    fun peek(): Int {
        if (nextByte != null) {
            return nextByte!!
        }
        val b = input.read()
        if (b == -1) { // EOS
            return -1
        }
        nextByte = b
        return b
    }

    fun skipByte(): Boolean {
        if (nextByte != null) {
            nextByte = null
            return true
        }
        val b = input.read()
        if (b == -1) {
            return false
        }
        return true
    }

    fun readBytes(length: Int): Pair<ByteArray, Int> {
        require(length >= 0) { "length < 0" }
        if (length == 0) return Pair(byteArrayOf(), 0)
        val bytes = ByteArray(length)
        if (nextByte == null) {
            return Pair(bytes, input.read(bytes))
        }
        bytes[0] = nextByte!!.toByte()
        nextByte = null
        val bytesRead = input.read(bytes, 1, length - 1)
        if (bytesRead == -1) {
            return Pair(bytes, 1)
        }
        return Pair(bytes, bytesRead + 1)
    }
}

class BEncodeException(msg: String) : RuntimeException(msg)

class BEncodeParser private constructor(private val sbb: SingleByteBufferInputStream) {

    companion object {
        private const val BYTE_ZERO = '0'.toInt()
        private const val BYTE_NINE = '9'.toInt()
        private const val BYTE_D = 'd'.toInt()
        private const val BYTE_E = 'e'.toInt()
        private const val BYTE_I = 'i'.toInt()
        private const val BYTE_L = 'l'.toInt()
        private const val BYTE_MINUS = '-'.toInt()
        private const val BYTE_COLON = ':'.toInt()
    }

    constructor(input: InputStream) : this(SingleByteBufferInputStream(input))

    fun parse(): BEncodeElement = readElement()

    private fun readElement(): BEncodeElement = when (peekToken()) {
        BEncodeToken.DICTIONARY_START -> readDictionaryElement()
        BEncodeToken.LIST_START -> readListElement()
        BEncodeToken.NUMBER_START -> readNumberElement()
        BEncodeToken.BYTE_STRING_START -> ByteStringElement(readByteString())
        else -> throw BEncodeException("unexpected token")
    }

    private fun readListElement(): ListElement {
        sbb.skipByte() // skip l
        val list = mutableListOf<BEncodeElement>()
        var token: BEncodeToken
        while (true) {
            token = peekToken()
            if (token == BEncodeToken.END) break
            if (token == BEncodeToken.EOF) throw BEncodeException("unexpected EOF")
            list.add(readElement())
        }
        return ListElement(list)
    }

    private fun readDictionaryElement(): DictionaryElement {
        sbb.skipByte() // skip d
        val map = mutableMapOf<String, BEncodeElement>()
        var token: BEncodeToken
        while (true) {
            token = peekToken()
            if (token == BEncodeToken.END) break
            if (token != BEncodeToken.BYTE_STRING_START) throw BEncodeException("expect byte string, but was $token")
            val key = readByteString().toString()
            val value = readElement()
            map[key] = value
        }
        return DictionaryElement(map)
    }

    /**
     * i<contents>e
     */
    private fun readNumberElement(): NumberElement {
        sbb.skipByte() // skip i
        val n = readNumber(BYTE_E)
        sbb.skipByte() // skip e
        return NumberElement(n)
    }

    /**
     * <length>:<contents>
     */
    private fun readByteString(): ByteArray {
        val length = readNumber(BYTE_COLON).toInt()
        if (length < 0) throw BEncodeException("unexpected byte string length $length")
        sbb.skipByte() // skip :
        val (bytes, bytesRead) = sbb.readBytes(length)
        if (bytesRead != length) throw BEncodeException("expect $length to read, but was $bytesRead")
        return bytes
    }

    private fun readNumber(sentinel: Int): Long {
        val signOrDigit = sbb.read()
        var n: Long = 0
        if (signOrDigit.isDigit()) {
            n = (signOrDigit - BYTE_ZERO).toLong()
        } else if (signOrDigit != BYTE_MINUS) {
            throw BEncodeException("expected digit or sign, but was $signOrDigit")
        }
        var d: Int
        while (true) {
            d = sbb.read()
            if (d == -1) throw BEncodeException("unexpected EOF")
            if (d == sentinel) break
            if (!d.isDigit()) throw BEncodeException("expected digit, but was $d")
            n *= 10
            n += (d - BYTE_ZERO)
        }
        return if (signOrDigit == BYTE_MINUS) -n else n
    }

    private fun Int.isDigit(): Boolean = (this >= BYTE_ZERO) && (this <= BYTE_NINE)

    private fun peekToken(): BEncodeToken = when (sbb.peek()) {
        -1 -> BEncodeToken.EOF
        BYTE_D -> BEncodeToken.DICTIONARY_START
        BYTE_L -> BEncodeToken.LIST_START
        BYTE_I -> BEncodeToken.NUMBER_START
        BYTE_E -> BEncodeToken.END
        in BYTE_ZERO..BYTE_NINE -> BEncodeToken.BYTE_STRING_START
        else -> BEncodeToken.OTHER
    }
}