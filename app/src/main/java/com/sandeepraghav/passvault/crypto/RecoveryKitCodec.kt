package com.sandeepraghav.passvault.crypto

/**
 * Encodes the raw 256-bit recovery-kit key as a Crockford Base32 string (excludes I/L/O/U to
 * avoid transcription mistakes), grouped into 4-character blocks for readability, e.g.
 * "K7X9-QM3P-7H2D-...". Purely a display/typing format — the AES key is the raw bytes.
 */
object RecoveryKitCodec {

    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val GROUP_SIZE = 4

    fun format(bytes: ByteArray): String {
        val raw = encode(bytes)
        return raw.chunked(GROUP_SIZE).joinToString("-")
    }

    fun parse(input: String): ByteArray {
        val clean = input.uppercase().filter { it in ALPHABET }
        return decode(clean)
    }

    private fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(ALPHABET[(buffer shr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    private fun decode(clean: String): ByteArray {
        val out = ArrayList<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) continue
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }
        return out.toByteArray()
    }
}
