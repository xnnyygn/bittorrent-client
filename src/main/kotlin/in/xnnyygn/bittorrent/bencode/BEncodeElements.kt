package `in`.xnnyygn.bittorrent.bencode

sealed class BEncodeElement {
    companion object {
        fun from(value: Any): BEncodeElement = when (value) {
            is Number -> NumberElement(value.toLong())
            is ByteArray -> ByteStringElement(value)
            is String -> ByteStringElement(value.toByteArray())
            is List<*> -> ListElement(value.map { from(it!!) })
            is Map<*, *> -> {
                val map = mutableMapOf<String, BEncodeElement>()
                for ((k, v) in value) {
                    map[k as String] = from(v!!)
                }
                DictionaryElement(map)
            }
            else -> throw IllegalArgumentException("unexpected value $value")
        }
    }
}

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

class DictionaryElement(private val dictionary: Map<String, BEncodeElement>) : BEncodeElement(),
    Iterable<Map.Entry<String, BEncodeElement>> {

    val size: Int
        get() = dictionary.size

    fun contains(key: String): Boolean = dictionary.contains(key)

    fun getString(key: String): String = (dictionary[key] as ByteStringElement).asString()

    fun getNumber(key: String): Long = (dictionary[key] as NumberElement).value

    fun getByteString(key: String): ByteStringElement = (dictionary[key] as ByteStringElement)

    fun getList(key: String): ListElement = (dictionary[key] as ListElement)

    fun getDictionary(key: String): DictionaryElement = (dictionary[key] as DictionaryElement)

    override fun iterator() = dictionary.toSortedMap().iterator()

    override fun toString(): String {
        return "DictionaryElement(dictionary=$dictionary)"
    }
}

data class ListElement(val elements: List<BEncodeElement>) : BEncodeElement()