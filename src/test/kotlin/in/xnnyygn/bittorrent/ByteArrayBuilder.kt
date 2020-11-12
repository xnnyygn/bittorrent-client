package `in`.xnnyygn.bittorrent

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

fun buildByteArrayInDataForm(consumer: DataOutputStream.() -> Unit): ByteArray {
    val bytesOut = ByteArrayOutputStream()
    val dataOut = DataOutputStream(bytesOut)
    consumer(dataOut)
    return bytesOut.toByteArray()
}