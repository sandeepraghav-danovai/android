package com.danovai.passvault.util

import java.security.SecureRandom

object PasswordGenerator {
    private val secureRandom = SecureRandom()
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+?"

    fun generate(length: Int = 20, useUpper: Boolean = true, useDigits: Boolean = true, useSymbols: Boolean = true): String {
        var pool = LOWER
        if (useUpper) pool += UPPER
        if (useDigits) pool += DIGITS
        if (useSymbols) pool += SYMBOLS
        return (1..length).map { pool[secureRandom.nextInt(pool.length)] }.joinToString("")
    }
}

enum class PasswordStrength { WEAK, FAIR, STRONG, VERY_STRONG }

object PasswordStrengthEstimator {
    fun estimate(password: String): PasswordStrength {
        if (password.length < 8) return PasswordStrength.WEAK
        var variety = 0
        if (password.any { it.isLowerCase() }) variety++
        if (password.any { it.isUpperCase() }) variety++
        if (password.any { it.isDigit() }) variety++
        if (password.any { !it.isLetterOrDigit() }) variety++

        return when {
            password.length >= 16 && variety >= 3 -> PasswordStrength.VERY_STRONG
            password.length >= 12 && variety >= 2 -> PasswordStrength.STRONG
            password.length >= 8 && variety >= 2 -> PasswordStrength.FAIR
            else -> PasswordStrength.WEAK
        }
    }
}
