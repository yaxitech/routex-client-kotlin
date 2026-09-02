package tech.yaxi.routex.settlement

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tech.yaxi.routex.settlement.internal.blake2bDigest
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Decrypt
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Encrypt
import tech.yaxi.routex.settlement.internal.ed25519Verify
import tech.yaxi.routex.settlement.internal.hkdfBlake2b512
import tech.yaxi.routex.settlement.internal.sha256
import tech.yaxi.routex.settlement.internal.x25519GenerateKeyPair
import tech.yaxi.routex.settlement.internal.x25519SharedSecret

/**
 * Known-answer and round-trip tests for the platform crypto actuals; the
 * same vectors gate every target.
 */
class CryptoPrimitivesTest {
    @Test
    fun sha256MatchesKnownAnswer() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun blake2bMatchesKnownAnswers() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d1" +
                "7d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            blake2bDigest("abc".encodeToByteArray(), outLen = 64).toHex(),
        )
        // The 12-byte variant derives the ChaChaBox nonce.
        assertEquals(
            "48b4f1f9042a2d5033b382b5",
            blake2bDigest("abc".encodeToByteArray(), outLen = 12).toHex(),
        )
    }

    @Test
    fun hkdfBlake2b512MatchesKnownAnswer() {
        val okm =
            hkdfBlake2b512(
                ikm = "0b0c0d0e0f101112131415161718191a1b1c1d1e1f20".hexToBytes(),
                info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes(),
                length = 42,
            )
        assertEquals(
            "af39a42f4e8f8cbcf7bfa5511f0217c9d738664174046e83ca6b4765c6b8438926699931cee7226c0ce4",
            okm.toHex(),
        )
    }

    @Test
    fun chacha20Poly1305RoundTripsAndAuthenticates() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { (it + 1).toByte() }
        val plaintext = "routex apple interop test vector".encodeToByteArray()

        val sealed = chacha20Poly1305Encrypt(key, nonce, plaintext)
        assertEquals(plaintext.size + 16, sealed.size)
        assertContentEquals(plaintext, chacha20Poly1305Decrypt(key, nonce, sealed))

        val tampered = sealed.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFails { chacha20Poly1305Decrypt(key, nonce, tampered) }
    }

    @Test
    fun x25519SharedSecretIsSymmetric() {
        val alice = x25519GenerateKeyPair()
        val bob = x25519GenerateKeyPair()
        val aliceShared = x25519SharedSecret(alice.secret, bob.public)
        val bobShared = x25519SharedSecret(bob.secret, alice.public)
        assertEquals(32, aliceShared.size)
        assertContentEquals(aliceShared, bobShared)
    }

    @Test
    fun ed25519VerifiesKnownSignature() {
        val publicKey = "9e887695b9e4af741f2798b44fb48d5f438e19575c83577882774b396390bd8a".hexToBytes()
        val message = "routex apple interop test vector".encodeToByteArray()
        val signature =
            (
                "4131e4cbb0e09adbceb289f5b9a7d4c1ef295f83a793b59b2d6507cd2778aa6f" +
                    "3baa32ced3a76dfe1302e69733de190fc804ec56fa390569d0ea0f3b7b9d6701"
            ).hexToBytes()

        assertTrue(ed25519Verify(publicKey, message, signature))
        val tampered = message.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertFalse(ed25519Verify(publicKey, tampered, signature))
    }

    @Test
    fun chaChaBoxRoundTrips() {
        val keys = ChaChaBox.generateKeys()
        val plaintext = "sealed for the settlement handshake".encodeToByteArray()
        val sealed = ChaChaBox.seal(keys.publicKey, plaintext)
        assertContentEquals(plaintext, ChaChaBox.unseal(keys, sealed))
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> byte.toUByte().toString(16).padStart(2, '0') }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
