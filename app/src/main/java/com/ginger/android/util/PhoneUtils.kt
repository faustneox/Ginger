package com.ginger.android.util

object PhoneUtils {

    @JvmStatic
    fun normalize(raw: String?): String {
        if (raw.isNullOrEmpty()) {
            return ""
        }
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")
        if (digitsOnly.isEmpty()) {
            return ""
        }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    /**
     * Проверяет нормализованный номер телефона.
     * После normalize() строка уже содержит только цифры (и опциональный '+'),
     * поэтому дополнительная проверка регулярным выражением не нужна.
     */
    @JvmStatic
    fun isValid(normalized: String?): Boolean {
        if (normalized.isNullOrEmpty()) {
            return false
        }
        val digits = if (normalized.startsWith("+")) normalized.substring(1) else normalized
        val len = digits.length
        return len in 10..15
    }
}
