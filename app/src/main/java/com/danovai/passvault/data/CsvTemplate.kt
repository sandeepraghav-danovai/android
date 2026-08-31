package com.danovai.passvault.data

import com.danovai.passvault.util.CsvParser

/**
 * The bulk-import CSV contract.
 *
 * Column order does not matter and header names are matched case-insensitively, so a file
 * exported from another manager usually only needs its header row renamed. [TITLE] and
 * [PASSWORD] are the only required columns; a row missing either is reported and skipped
 * rather than silently imported as a blank credential.
 */
object CsvTemplate {

    const val CATEGORY = "category"
    const val TITLE = "title"
    const val USERNAME = "username"
    const val PASSWORD = "password"
    const val URL = "url"
    const val NOTES = "notes"

    val COLUMNS = listOf(CATEGORY, TITLE, USERNAME, PASSWORD, URL, NOTES)

    /** Category used when a row leaves the column blank. */
    const val DEFAULT_CATEGORY = "Imported"

    const val FILE_NAME = "passvault-import-template.csv"

    /** A ready-to-edit template, including rows that show the quoting rules. */
    fun sample(): String {
        val rows = listOf(
            COLUMNS,
            listOf("Banks", "Example Bank", "you@example.com", "S0me-Long-Passphrase", "https://bank.example.com", "Joint account"),
            listOf("Websites", "Example Site", "myhandle", "another-password", "https://example.com", ""),
            // shows that commas, quotes and newlines are fine as long as the field is quoted
            listOf("Office", "VPN, corporate", "staff-id", "pa\"ss,word", "", "Line one\nLine two")
        )
        return rows.joinToString("\n") { row -> row.joinToString(",") { CsvParser.quote(it) } } + "\n"
    }
}
