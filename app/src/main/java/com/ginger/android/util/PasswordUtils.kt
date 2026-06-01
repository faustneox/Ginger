package com.ginger.android.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Утилита для безопасного хранения паролей.
 * Используется алгоритм PBKDF2WithHmacSHA256 с индивидуальной солью для каждого пользователя.
 *
 * Формат сохранённого хэша: {@code v2$<iterations>$<hex-соль>$<hex-хэш>}
 *
 * Также поддерживается проверка legacy-формата: {@code <hex-соль>:<hex-хэш>}
 */
object PasswordUtils {

    private const val SALT_LENGTH_BYTES = 16
    private const val HASH_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 120_000
    private const val CURRENT_VERSION = "v2"

    /**
     * Хэширует пароль с генерацией случайной соли.
     * Возвращает строку вида {@code v2$<iterations>$<hex-соль>$<hex-хэш>}, которую нужно сохранить в БД.
     */
    @JvmStatic
    fun hashPassword(plainPassword: String): String {
        require(plainPassword.isNotEmpty()) { "Password must not be null or empty" }

        val salt = generateSalt()
        val hash = pbkdf2(salt, plainPassword.toCharArray(), PBKDF2_ITERATIONS)
        return "$CURRENT_VERSION$$PBKDF2_ITERATIONS$${bytesToHex(salt)}$${bytesToHex(hash)}"
    }

    /**
     * Проверяет, соответствует ли введённый пароль сохранённому хэшу.
     * Очищает введённый пароль из памяти после проверки.
     */
    @JvmStatic
    fun verifyPassword(plainPassword: String, storedHash: String): Boolean {
        if (plainPassword.isEmpty() || storedHash.isEmpty()) {
            return false
        }
        return try {
            if (storedHash.startsWith("$CURRENT_VERSION$")) {
                verifyV2(plainPassword, storedHash)
            } else {
                verifyLegacySha256(plainPassword, storedHash)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun verifyV2(plainPassword: String, storedHash: String): Boolean {
        val parts = storedHash.split("$", limit = 4)
        if (parts.size != 4 || parts[0] != CURRENT_VERSION) return false

        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations <= 0) return false

        val salt = hexToBytes(parts[2])
        val expectedHash = hexToBytes(parts[3])
        val actualHash = pbkdf2(salt, plainPassword.toCharArray(), iterations)

        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun verifyLegacySha256(plainPassword: String, storedHash: String): Boolean {
        if (!storedHash.contains(":")) return false

        val parts = storedHash.split(":", limit = 2)
        if (parts.size != 2) return false

        val salt = hexToBytes(parts[0])
        val expectedHash = hexToBytes(parts[1])
        val actualHash = sha256Legacy(salt, plainPassword)

        return MessageDigest.isEqual(expectedHash, actualHash)
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun pbkdf2(salt: ByteArray, passwordChars: CharArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passwordChars, salt, iterations, HASH_LENGTH_BITS)
        return try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            skf.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun sha256Legacy(salt: ByteArray, password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(password.toByteArray(StandardCharsets.UTF_8))
        return md.digest()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex length" }
        return hex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
