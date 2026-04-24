package com.juliacai.apptick.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HmacSHA256 password hashing with versioned, self-describing encoding.
 *
 * Encoded format: `v1$<iterations>$<base64-salt>$<base64-hash>`
 *   - The version + iteration count travel with the hash so the iteration count
 *     can be raised in future builds without breaking existing hashes.
 *   - 16-byte random salt per password.
 *   - 200_000 iterations is a balance between security (defense-in-depth against
 *     ADB-backup extraction; the encrypted prefs file is also excluded from
 *     backup) and unlock latency on low-end devices. Brute force of 4-6 digit
 *     PINs is still cheap regardless of KDF strength — the real protection is
 *     not storing the password in recoverable form.
 */
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val DEFAULT_ITERATIONS = 200_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val VERSION = "v1"
    private const val SEPARATOR = "$"

    fun hash(password: String, iterations: Int = DEFAULT_ITERATIONS): String {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, salt, iterations)
        val encoder = Base64.getEncoder().withoutPadding()
        return listOf(
            VERSION,
            iterations.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(derived)
        ).joinToString(SEPARATOR)
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != VERSION) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { decoder.decode(parts[3]) }.getOrNull() ?: return false
        val derived = derive(password, salt, iterations)
        return MessageDigest.isEqual(derived, expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
