package `in`.xnnyygn.bittorrent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

internal class BEncodeParserTest {

    @Test(expected = BEncodeException::class)
    fun testParseInvalidNumber() {
        val input: InputStream = ByteArrayInputStream("ie".toByteArray())
        BEncodeParser(input).parse()
    }

    @Test(expected = BEncodeException::class)
    fun testParseInvalidNumberWithoutSentinel() {
        val input: InputStream = ByteArrayInputStream("i42".toByteArray())
        BEncodeParser(input).parse()
    }

    @Test(expected = BEncodeException::class)
    fun testParseInvalidNumberNonDigit() {
        val input: InputStream = ByteArrayInputStream("i42a".toByteArray())
        BEncodeParser(input).parse()
    }

    @Test
    fun testParseNumber() {
        val input: InputStream = ByteArrayInputStream("i42e".toByteArray())
        assertEquals(42, (BEncodeParser(input).parse() as NumberElement).value)
    }

    @Test
    fun testParseNegativeNumber() {
        val input: InputStream = ByteArrayInputStream("i-42e".toByteArray())
        assertEquals(-42, (BEncodeParser(input).parse() as NumberElement).value)
    }


}