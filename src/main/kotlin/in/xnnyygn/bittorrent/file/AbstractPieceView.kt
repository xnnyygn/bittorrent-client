package `in`.xnnyygn.bittorrent.file

abstract class AbstractPieceView<T : PieceSlice, W>(
    open val offsetInPiece: Int,
    open val length: Int,
    protected val slices: List<T>
) {
    protected fun writeTo(target: W) {
        var current = 0
        var skip = true
        val sliceIterator = slices.iterator()
        while (current < length && sliceIterator.hasNext()) {
            val slice = sliceIterator.next()
            if (skip && offsetInPiece < current + slice.length) {
                skip = false
                val sliceOffset = offsetInPiece - current
                doWriteTo(
                    slice,
                    sliceOffset,
                    (slice.length - sliceOffset).coerceAtMost(length - current),
                    target
                )
            } else if (!skip) {
                doWriteTo(
                    slice,
                    0,
                    slice.length.coerceAtMost(length - current),
                    target
                )
            }
            current += slice.length
        }
    }

    protected abstract fun doWriteTo(slice: T, sliceOffset: Int, sliceLength: Int, target: W)
}