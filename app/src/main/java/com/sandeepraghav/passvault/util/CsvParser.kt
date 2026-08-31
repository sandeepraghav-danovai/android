package com.sandeepraghav.passvault.util

/**
 * Small RFC 4180 CSV reader.
 *
 * Hand-rolled rather than pulled from a library because the rules that matter here are few and
 * the alternative is shipping a dependency that touches plaintext passwords. It handles the
 * cases that actually bite when exporting from other password managers: fields wrapped in
 * quotes, commas and newlines inside those quotes, and doubled quotes as an escape — so a
 * password like `a,b"c` survives the round trip.
 */
object CsvParser {

    /** Parses [text] into rows of fields. Blank trailing lines are dropped. */
    fun parse(text: String): List<List<String>> {
        val input = text.removePrefix("﻿")   // strip a UTF-8 BOM if the file has one
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            // a line that held nothing at all is not a row
            if (row.size > 1 || row.firstOrNull()?.isNotEmpty() == true) rows.add(row)
            row = mutableListOf()
        }

        while (i < input.length) {
            val c = input[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < input.length && input[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> {
                    // swallow CRLF as a single terminator
                    if (i + 1 < input.length && input[i + 1] == '\n') i++
                    endRow()
                }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }

    /** Quotes a value for output, escaping embedded quotes. */
    fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
