package `in`.xnnyygn.bittorrent.bencode

import java.io.InputStream
import java.lang.IllegalStateException
import java.security.MessageDigest

private interface BasicInputStream {
    companion object {
        const val EOF = -1
    }

    fun read(): Int
    fun peek(): Int
    fun skipByte(): Int
    fun readBytes(length: Int): Pair<ByteArray, Int>
}

private class SingleByteBufferInputStream(private val delegate: InputStream) : BasicInputStream {
    private var nextByte: Int? = null

    override fun read(): Int {
        if (nextByte != null) {
            val b: Int = nextByte!!
            nextByte = null
            return b
        }
        return delegate.read()
    }

    override fun peek(): Int {
        if (nextByte != null) {
            return nextByte!!
        }
        val b = delegate.read()
        if (b == BasicInputStream.EOF) {
            return BasicInputStream.EOF
        }
        nextByte = b
        return b
    }

    override fun skipByte(): Int {
        if (nextByte == null) {
            return delegate.read()
        }
        val b = nextByte!!
        nextByte = null
        return b
    }

    override fun readBytes(length: Int): Pair<ByteArray, Int> {
        require(length >= 0) { "length < 0" }
        if (length == 0) {
            return Pair(byteArrayOf(), 0)
        }
        val bytes = ByteArray(length)
        if (nextByte == null) {
            return Pair(bytes, delegate.read(bytes))
        }
        bytes[0] = nextByte!!.toByte()
        nextByte = null
        val bytesRead = delegate.read(bytes, 1, length - 1)
        if (bytesRead == -1) {
            return Pair(bytes, 1)
        }
        return Pair(bytes, bytesRead + 1)
    }
}

private class Sha1MessageDigestInputStream(private val delegate: BasicInputStream) : BasicInputStream {

    private val digest = MessageDigest.getInstance("SHA-1")
    private var start = false
    var result: ByteArray? = null
        private set

    override fun read(): Int {
        val b = delegate.read()
        updateIfStarted(b)
        return b
    }

    private fun updateIfStarted(b: Int) {
        if (!start) {
            return
        }
        if (b == BasicInputStream.EOF) {
            return
        }
        digest.update(b.toByte())
    }

    override fun peek(): Int = delegate.peek()

    override fun skipByte(): Int {
        val b = delegate.skipByte()
        updateIfStarted(b)
        return b
    }

    override fun readBytes(length: Int): Pair<ByteArray, Int> {
        val pair = delegate.readBytes(length)
        val (bytes, bytesRead) = pair
        if (bytesRead > 0) {
            digest.update(bytes)
        }
        return pair
    }

    fun startDigest() {
        if (start) {
            throw IllegalStateException("started")
        }
        start = true
    }

    fun doneDigest() {
        if (result != null) {
            throw IllegalStateException("done")
        }
        result = digest.digest()
    }
}

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

class BEncodeReader private constructor(private val input: Sha1MessageDigestInputStream) {

    constructor(input: InputStream) : this(Sha1MessageDigestInputStream(SingleByteBufferInputStream(input)))

    val infoHash: ByteArray
        get() = input.result ?: throw BEncodeException("parsing required")

    fun parse(): BEncodeElement = readElement(top = true)

    private fun readElement(top: Boolean = false): BEncodeElement {
        val b = input.peek()
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
        val b = input.peek()
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
            val b = input.peek()
            val tokenType =
                BEncodeTokenType.fromByte(input.peek())
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
            val b = input.peek()
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
            if (key == "info") {
                input.startDigest()
                map[key] = readElement()
                input.doneDigest()
            } else {
                map[key] = readElement()
            }
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
        val (bytes, bytesRead) = input.readBytes(length)
        if (bytesRead != length) throw BEncodeException("expect $length to read, but was $bytesRead")
        return bytes
    }

    private fun skipByte(tokenType: BEncodeTokenType) {
        require(tokenType.byte != null) { "token type byte required" }
        skipByte(tokenType.byte)
    }

    private fun skipByte(expectedByte: Int) {
        val b = input.skipByte()
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
        val signOrDigit = input.read()
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
            d = input.read()
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