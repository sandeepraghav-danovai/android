package com.sandeepraghav.passvault.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun `parses a plain row`() {
        assertEquals(
            listOf(listOf("a", "b", "c")),
            CsvParser.parse("a,b,c")
        )
    }

    @Test
    fun `keeps empty fields`() {
        assertEquals(
            listOf(listOf("a", "", "c")),
            CsvParser.parse("a,,c")
        )
    }

    @Test
    fun `comma inside quotes is part of the field`() {
        assertEquals(
            listOf(listOf("Bank, National", "pw")),
            CsvParser.parse("\"Bank, National\",pw")
        )
    }

    @Test
    fun `doubled quote is an escaped quote`() {
        // a password of  a"b  is written  "a""b"
        assertEquals(
            listOf(listOf("a\"b")),
            CsvParser.parse("\"a\"\"b\"")
        )
    }

    @Test
    fun `newline inside quotes stays in the field`() {
        assertEquals(
            listOf(listOf("line1\nline2", "x")),
            CsvParser.parse("\"line1\nline2\",x")
        )
    }

    @Test
    fun `handles CRLF and LF line endings`() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            CsvParser.parse("a,b\r\nc,d")
        )
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d")),
            CsvParser.parse("a,b\nc,d")
        )
    }

    @Test
    fun `drops trailing blank lines`() {
        assertEquals(
            listOf(listOf("a", "b")),
            CsvParser.parse("a,b\n\n\n")
        )
    }

    @Test
    fun `strips a UTF-8 BOM`() {
        assertEquals(
            listOf(listOf("category", "title")),
            CsvParser.parse("﻿category,title")
        )
    }

    @Test
    fun `quote round-trips values that need escaping`() {
        val nasty = "a,b\"c\nd"
        val encoded = CsvParser.quote(nasty)
        assertEquals(listOf(listOf(nasty)), CsvParser.parse(encoded))
    }

    @Test
    fun `leaves ordinary values unquoted`() {
        assertEquals("simple", CsvParser.quote("simple"))
    }
}
