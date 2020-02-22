package `in`.xnnyygn.bittorrent.file

import java.util.BitSet

interface PiecesStatus {
    val isEmpty: Boolean
    fun toByteArray(): ByteArray
    fun pieces(): Iterator<Int>
    fun missingPieces(): Iterator<Int>
    fun hasPiece(index: Int): Boolean
    fun addPiece(index: Int)
}

private class BitSetPieceStatusMissingPiecesIterator(
    private val bitSet: BitSet,
    private val pieceCount: Int
) : Iterator<Int> {
    private var index = 0

    override fun hasNext(): Boolean {
        if (index >= pieceCount)
            return false
        if (!bitSet.get(index)) {
            return true
        }
        val nextClearBitIndex = bitSet.nextClearBit(index)
        if (nextClearBitIndex > index) {
            index = nextClearBitIndex
            return true
        }
        return false
    }

    override fun next(): Int {
        if (index >= pieceCount || bitSet.get(index)) {
            throw IllegalStateException("call hasNext firstly")
        }
        return index
    }
}

class BitSetPiecesStatus(private val bitSet: BitSet, private val pieceCount: Int) :
    PiecesStatus {
    constructor(pieceCount: Int) : this(BitSet(pieceCount), pieceCount)

    override val isEmpty: Boolean
        get() = bitSet.isEmpty

    override fun toByteArray(): ByteArray = bitSet.toByteArray()

    // TODO check if piece count required
    override fun pieces(): Iterator<Int> {
        return bitSet.stream().iterator()
    }

    override fun missingPieces(): Iterator<Int> =
        BitSetPieceStatusMissingPiecesIterator(bitSet, pieceCount)

    override fun hasPiece(index: Int): Boolean {
        require(index in 0 until pieceCount) { "illegal index" }
        return bitSet.get(index)
    }

    override fun addPiece(index: Int) {
        require(index in 0 until pieceCount) { "illegal index" }
        bitSet.set(index)
    }
}

private class BitSetBufferIterator(private val buffer: IntArray, private val limit: Int) : Iterator<Int> {
    private var index = 0

    override fun hasNext(): Boolean = (index < limit)

    override fun next(): Int {
        val item = buffer[index]
        index++
        return item
    }
}

private class BitSetBuffer(size: Int) : Iterable<Int> {
    private val buffer = IntArray(size)
    private var index = 0

    val isEmpty: Boolean
        get() = index == 0

    fun add(item: Int): Boolean {
        if (index == buffer.size - 1) {
            return false
        }
        buffer[index] = item
        index++
        return true
    }

    fun hasItem(item: Int): Boolean {
        for (index in 0 until index) {
            if (buffer[index] == item) {
                return true
            }
        }
        return false
    }

    override fun iterator(): Iterator<Int> =
        BitSetBufferIterator(buffer, index)
}

private class BitSetWithBufferPieceStatusMissingPiecesIterator(
    private val delegate: Iterator<Int>,
    private val bufferedItems: Set<Int>
) : Iterator<Int> {
    private var nextItem: Int? = null

    override fun hasNext(): Boolean {
        while (true) {
            if (!delegate.hasNext()) {
                return false
            }
            val nextItem = delegate.next()
            if (bufferedItems.contains(nextItem)) {
                continue
            }
            this.nextItem = nextItem
            return true
        }
    }

    override fun next(): Int = nextItem!!
}

class BitSetWithBufferPiecesStatus(bitSet: BitSet, private val pieceCount: Int, private val bufferSize: Int) :
    PiecesStatus {
    // TODO change to atomic reference
    @Volatile
    private var delegate: BitSetPiecesStatus =
        BitSetPiecesStatus(bitSet, pieceCount)
    @Volatile
    private var buffer = BitSetBuffer(bufferSize)

    override val isEmpty: Boolean
        get() = delegate.isEmpty && buffer.isEmpty

    override fun toByteArray(): ByteArray {
        if (buffer.isEmpty) {
            return delegate.toByteArray()
        }
        val bitSet = BitSet.valueOf(delegate.toByteArray())
        for (i in buffer) {
            bitSet.set(i)
        }
        return bitSet.toByteArray()
    }

    override fun pieces(): Iterator<Int> = TODO()

    override fun missingPieces(): Iterator<Int> =
        BitSetWithBufferPieceStatusMissingPiecesIterator(
            delegate.missingPieces(),
            buffer.toSet()
        )

    override fun hasPiece(index: Int): Boolean {
        return delegate.hasPiece(index) || buffer.hasItem(index)
    }

    override fun addPiece(index: Int) {
        if (buffer.add(index)) {
            return
        }
        val bitSet = BitSet.valueOf(delegate.toByteArray())
        for (i in buffer) {
            bitSet.set(i)
        }
        bitSet.set(index)
        delegate = BitSetPiecesStatus(bitSet, pieceCount)
        buffer = BitSetBuffer(bufferSize)
    }
}