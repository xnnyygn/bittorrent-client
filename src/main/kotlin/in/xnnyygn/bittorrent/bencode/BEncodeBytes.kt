package `in`.xnnyygn.bittorrent.bencode

internal class BEncodeBytes {
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