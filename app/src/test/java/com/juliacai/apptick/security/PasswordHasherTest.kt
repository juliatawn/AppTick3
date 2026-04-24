package com.juliacai.apptick.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun verify_returnsTrueForCorrectPassword() {
        val encoded = PasswordHasher.hash("hunter2", iterations = 1_000)
        assertThat(PasswordHasher.verify("hunter2", encoded)).isTrue()
    }

    @Test
    fun verify_returnsFalseForWrongPassword() {
        val encoded = PasswordHasher.hash("hunter2", iterations = 1_000)
        assertThat(PasswordHasher.verify("hunter3", encoded)).isFalse()
    }

    @Test
    fun hash_isSaltedSoSamePasswordYieldsDifferentEncodings() {
        val a = PasswordHasher.hash("1234", iterations = 1_000)
        val b = PasswordHasher.hash("1234", iterations = 1_000)
        assertThat(a).isNotEqualTo(b)
        assertThat(PasswordHasher.verify("1234", a)).isTrue()
        assertThat(PasswordHasher.verify("1234", b)).isTrue()
    }

    @Test
    fun verify_rejectsMalformedEncodings() {
        assertThat(PasswordHasher.verify("x", "")).isFalse()
        assertThat(PasswordHasher.verify("x", "not-an-encoding")).isFalse()
        assertThat(PasswordHasher.verify("x", "v1\$1000\$bad\$bad")).isFalse()
        assertThat(PasswordHasher.verify("x", "v2\$1000\$AAAA\$AAAA")).isFalse()
    }

    @Test
    fun verify_handlesUnicodePassword() {
        val encoded = PasswordHasher.hash("pässwörd🔒", iterations = 1_000)
        assertThat(PasswordHasher.verify("pässwörd🔒", encoded)).isTrue()
        assertThat(PasswordHasher.verify("password", encoded)).isFalse()
    }

    @Test
    fun hash_doesNotEmbedPlaintextPassword() {
        val plaintext = "S0m3R4ndomP4ss"
        val encoded = PasswordHasher.hash(plaintext, iterations = 1_000)
        assertThat(encoded).doesNotContain(plaintext)
    }
}
