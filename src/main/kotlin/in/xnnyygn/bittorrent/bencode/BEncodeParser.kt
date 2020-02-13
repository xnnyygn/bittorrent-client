package `in`.xnnyygn.bittorrent.bencode

import java.io.InputStream

sealed class BEncodeElement

data class NumberElement(val value: Long) : BEncodeElement()

class ByteStringElement(val bytes: ByteArray) : BEncodeElement() {

    fun asString(): String = String(bytes)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteStringElement) return false

        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return "ByteStringElement(bytes.size=${bytes.size})"
    }
}

class DictionaryElement(private val map: Map<String, BEncodeElement>) : BEncodeElement() {

    val size: Int
        get() = map.size

    fun contains(key: String): Boolean = map.contains(key)

    fun getString(key: String): String = (map[key] as ByteStringElement).asString()

    fun getNumber(key: String): Long = (map[key] as NumberElement).value

    fun getByteString(key: String): ByteStringElement = (map[key] as ByteStringElement)

    fun getList(key: String): ListElement = (map[key] as ListElement)

    fun getDictionary(key: String): DictionaryElement = (map[key] as DictionaryElement)

    override fun toString(): String {
        return "DictionaryElement(dictionary=$map)"
    }
}

data class ListElement(val elements: List<BEncodeElement>) : BEncodeElement()

private class BEncodeBytes {
    companion object {
        const val BYTE_EOF: Int = -1
        const val BYTE_ZERO = '0'.toInt()
        const val BYTE_NINE = '9'.toInt()
        const val BYTE_D = 'd'.toInt()
        const val BYTE_E = 'e'.toInt()
        const val BYTE_I = 'i'.toInt()
        const val BYTE_L = 'l'.toInt()
        const val BYTE_MINUS = '-'.toInt()
        const val BYTE_COLON = ':'.toInt()
    }
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
        if (b == BEncodeBytes.BYTE_EOF) { // EOS
            return BEncodeBytes.BYTE_EOF
        }
        nextByte = b
        return b
    }

    fun skipByte(): Int {
        if (nextByte == null) {
            return input.read()
        }
        val b = nextByte!!
        nextByte = null
        return b
    }

    fun readBytes(length: Int): Pair<ByteArray, Int> {
        require(length >= 0) { "length < 0" }
        if (length == 0) {
            return Pair(byteArrayOf(), 0)
        }
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

open class BEncodeException(msg: String) : RuntimeException(msg)

class BEncodeUnexpectedByteException(val byte: Int, msg: String = "unexpected byte $byte") : BEncodeException(msg)

private enum class BEncodeTokenType(val byte: Int? = null) {
    DICTIONARY_START(BEncodeBytes.BYTE_D),
    LIST_START(BEncodeBytes.BYTE_L),
    BYTE_STRING_START,
    NUMBER_START(BEncodeBytes.BYTE_I),
    END(BEncodeBytes.BYTE_E),
    EOF(BEncodeBytes.BYTE_EOF);

    companion object {
        fun fromByte(b: Int): BEncodeTokenType? = when (b) {
            DICTIONARY_START.byte -> DICTIONARY_START
            LIST_START.byte -> LIST_START
            NUMBER_START.byte -> NUMBER_START
            END.byte -> END
            EOF.byte -> EOF
            in BEncodeBytes.BYTE_ZERO..BEncodeBytes.BYTE_NINE -> BYTE_STRING_START
            else -> null
        }

        fun isDigit(b: Int): Boolean = (b >= BEncodeBytes.BYTE_ZERO) && (b <= BEncodeBytes.BYTE_NINE)
    }
}

class BEncodeParser private constructor(private val sbb: SingleByteBufferInputStream) {

    constructor(input: InputStream) : this(
        SingleByteBufferInputStream(
            input
        )
    )

    fun parse(): BEncodeElement = readElement(top = true)

    private fun readElement(top: Boolean = false): BEncodeElement {
        val b = sbb.peek()
        val element = when (BEncodeTokenType.fromByte(b)) {
            BEncodeTokenType.DICTIONARY_START -> readDictionaryElement()
            BEncodeTokenType.LIST_START -> readListElement()
            BEncodeTokenType.NUMBER_START -> readNumberElement()
            BEncodeTokenType.BYTE_STRING_START -> ByteStringElement(
                readByteString()
            )
            // END, EOF, other
            else -> throw BEncodeUnexpectedByteException(b)
        }
        if (top) {
            ensureNoMoreByte()
        }
        return element
    }

    private fun ensureNoMoreByte() {
        val b = sbb.peek()
        if (b != -1) {
            throw BEncodeUnexpectedByteException(
                b,
                "expected EOF, but was $b"
            )
        }
    }

    /**
     * l<element*>e
     */
    private fun readListElement(): ListElement {
        skipByte(BEncodeTokenType.LIST_START) // skip l
        val list = mutableListOf<BEncodeElement>()
        while (true) {
            val b = sbb.peek()
            val tokenType =
                BEncodeTokenType.fromByte(sbb.peek())
            if (tokenType == null || tokenType == BEncodeTokenType.EOF) {
                throw BEncodeUnexpectedByteException(b)
            }
            if (tokenType == BEncodeTokenType.END) {
                skipByte(b)
                break
            }
            // LIST_START, DICTIONARY_START, NUMBER_START, BYTE_STRING_START
            list.add(readElement())
        }
        return ListElement(list)
    }

    /**
     * d<pair>e
     * pair = <byte string><element>
     */
    private fun readDictionaryElement(): DictionaryElement {
        skipByte(BEncodeTokenType.DICTIONARY_START)
        val map = mutableMapOf<String, BEncodeElement>()
        while (true) {
            val b = sbb.peek()
            val tokenType = BEncodeTokenType.fromByte(b)
            if (tokenType == BEncodeTokenType.END) {
                skipByte(b)
                break
            }
            // EOF, DICTIONARY_START, LIST_START, NUMBER_START
            if (tokenType != BEncodeTokenType.BYTE_STRING_START) {
                throw BEncodeUnexpectedByteException(
                    b,
                    "expect byte string, but was $b"
                )
            }
            val key = String(readByteString())
            map[key] = readElement()
        }
        return DictionaryElement(map)
    }

    /**
     * i<contents>e
     */
    private fun readNumberElement(): NumberElement {
        skipByte(BEncodeTokenType.NUMBER_START) // skip i
        val n = readNumber(BEncodeBytes.BYTE_E)
        // e was read
        return NumberElement(n)
    }

    /**
     * <length>:<contents>
     */
    private fun readByteString(): ByteArray {
        val length = readNumber(BEncodeBytes.BYTE_COLON).toInt()
        // : was read
        // if length was negative, it will be rejected by readBytes
        val (bytes, bytesRead) = sbb.readBytes(length)
        if (bytesRead != length) throw BEncodeException("expect $length to read, but was $bytesRead")
        return bytes
    }

    private fun skipByte(tokenType: BEncodeTokenType) {
        require(tokenType.byte != null) { "token type byte required" }
        skipByte(tokenType.byte)
    }

    private fun skipByte(expectedByte: Int) {
        val b = sbb.skipByte()
        if (b != expectedByte) {
            throw BEncodeUnexpectedByteException(
                expectedByte,
                "expected $expectedByte, but was $b"
            )
        }
    }

    /**
     * <sign-or-digit><digit*>e
     */
    private fun readNumber(sentinel: Int): Long {
        val signOrDigit = sbb.read()
        var n: Long = 0
        if (BEncodeTokenType.isDigit(signOrDigit)) {
            n = (signOrDigit - BEncodeBytes.BYTE_ZERO).toLong()
        } else if (signOrDigit != BEncodeBytes.BYTE_MINUS) {
            throw BEncodeUnexpectedByteException(
                signOrDigit,
                "expected digit or sign, but was $signOrDigit"
            )
        }
        var d: Int
        while (true) {
            d = sbb.read()
            if (d == -1) throw BEncodeUnexpectedByteException(
                -1,
                "unexpected EOF"
            )
            if (d == sentinel) break
            if (!BEncodeTokenType.isDigit(d)) throw BEncodeUnexpectedByteException(
                d,
                "expected digit, but was $d"
            )
            n *= 10
            n += (d - BEncodeBytes.BYTE_ZERO)
        }
        return if (signOrDigit == BEncodeBytes.BYTE_MINUS) -n else n
    }
}