package `in`.xnnyygn.bittorrent.bencode

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BEncodeReaderTest {

    @Test
    fun testParseInvalidNumberNoDigit() {
        try {
            parse("ie".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('e'.toInt(), e.byte)
        }
    }

    private fun parse(bytes: ByteArray): BEncodeElement {
        val input: InputStream = ByteArrayInputStream(bytes)
        return BEncodeReader(input).parse()
    }

    @Test
    fun testParseInvalidNumberWithoutEnd() {
        try {
            parse("i42".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals(-1, e.byte)
        }
    }

    @Test
    fun testParseInvalidNumberNonDigit() {
        try {
            parse("i42a".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('a'.toInt(), e.byte)
        }
    }

    @Test
    fun testParseNumber() {
        val element = parse("i42e".toByteArray())
        assertEquals(42, (element as NumberElement).value)
    }

    @Test
    fun testParseNegativeNumber() {
        val element = parse("i-42e".toByteArray())
        assertEquals(-42, (element as NumberElement).value)
    }

    @Test
    fun testParseByteString() {
        val element = parse("3:foo".toByteArray())
        assertArrayEquals("foo".toByteArray(), (element as ByteStringElement).bytes)
    }

    @Test
    fun testParseByteStringEmpty() {
        val element = parse("0:".toByteArray())
        assertTrue((element as ByteStringElement).bytes.isEmpty())
    }

    @Test
    fun testParseByteStringNegativeLength() {
        try {
            parse("-:".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('-'.toInt(), e.byte)
        }
    }

    @Test(expected = BEncodeException::class)
    fun testParseByteStringNotEnoughBytes() {
        parse("3:fo".toByteArray())
    }

    @Test
    fun testParseByteStringMoreThanExpected() {
        try {
            parse("3:fooA".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('A'.toInt(), e.byte)
        }
    }

    @Test
    fun testParseList() {
        val element = parse("li1ei2ei3ee".toByteArray())
        val elements = (element as ListElement).elements
        assertEquals(3, elements.size)
        assertEquals(1, (elements[0] as NumberElement).value)
        assertEquals(2, (elements[1] as NumberElement).value)
        assertEquals(3, (elements[2] as NumberElement).value)
    }

    @Test
    fun testParseListEmpty() {
        val element = parse("le".toByteArray())
        val elements = (element as ListElement).elements
        assertEquals(0, elements.size)
    }

    @Test
    fun testParseListWithoutEnd() {
        try {
            parse("l".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals(-1, e.byte)
        }
    }

    @Test
    fun testParseListUnexpectedByte() {
        try {
            parse("la".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('a'.toInt(), e.byte)
        }
    }

    @Test
    fun testParseDictionary() {
        val element = parse("d3:fooi1e3:bari4ee".toByteArray())
        val dictionary = element as DictionaryElement
        assertEquals(2, dictionary.size)
        assertEquals(1, dictionary.getNumber("foo"))
        assertEquals(4, dictionary.getNumber("bar"));
    }

    @Test
    fun testParseDictionaryEmpty() {
        val element = parse("de".toByteArray())
        val dictionary = element as DictionaryElement
        assertEquals(0, dictionary.size)
    }

    @Test
    fun testParseDictionaryWithoutEnd() {
        try {
            parse("d".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals(-1, e.byte)
        }
    }

    @Test
    fun testParseDictionaryUnexpectedNumberStart() {
        try {
            parse("di".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('i'.toInt(), e.byte)
        }
    }

    @Test
    fun testParseDictionaryUnexpectedDictionaryStart() {
        try {
            parse("dd".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('d'.toInt(), e.byte)
        }
    }

    @Test
    fun testParseDictionaryUnexpectedListStart() {
        try {
            parse("dl".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('l'.toInt(), e.byte)
        }
    }

    @Test
    fun testParseDictionaryUnexpectedByte() {
        try {
            parse("da".toByteArray())
            fail()
        } catch (e: BEncodeUnexpectedByteException) {
            assertEquals('a'.toInt(), e.byte)
        }
    }
}