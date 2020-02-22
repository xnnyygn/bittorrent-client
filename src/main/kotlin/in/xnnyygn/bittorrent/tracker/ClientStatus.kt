package `in`.xnnyygn.bittorrent.tracker

import java.util.concurrent.atomic.AtomicLong

@Deprecated("switch to message")
class ClientStatus private constructor(
    private val _uploaded: AtomicLong,
    private val _downloaded: AtomicLong,
    private val _left: AtomicLong
) {
    var uploaded: Long
        get() = _uploaded.get()
        set(value) = _uploaded.lazySet(value)
    var downloaded: Long
        get() = _downloaded.get()
        set(value) = _downloaded.lazySet(value)
    var left: Long
        get() = _left.get()
        set(value) = _left.lazySet(value)
    val isCompleted: Boolean
        get() = (_left.get() == 0L)

    constructor(uploaded: Long = 0, downloaded: Long = 0, left: Long = 0) : this(
        _uploaded = AtomicLong(uploaded),
        _downloaded = AtomicLong(downloaded),
        _left = AtomicLong(left)
    )
}