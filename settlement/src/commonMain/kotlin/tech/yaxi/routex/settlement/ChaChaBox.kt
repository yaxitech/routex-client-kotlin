package tech.yaxi.routex.settlement

import tech.yaxi.routex.settlement.internal.blake2bDigest
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Decrypt
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Encrypt
import tech.yaxi.routex.settlement.internal.hkdfBlake2b512
import tech.yaxi.routex.settlement.internal.x25519GenerateKeyPair
import tech.yaxi.routex.settlement.internal.x25519SharedSecret

/**
 * ChaChaBox seal/unseal primitive (X25519 ephemeral-static Diffie-Hellman keyed via
 * HKDF-Blake2b-512 and ChaCha20-Poly1305) used to encrypt request/response bodies
 * between the client and the YAXI TEE.
 *
 * All ciphersuite logic is in `commonMain`; only the crypto primitives are `expect`.
 */
internal object ChaChaBox {
    private const val X25519_KEY_LENGTH: Int = ChaChaBoxKeys.V1.X25519_KEY_LENGTH
    private const val CHACHA_KEY_LENGTH: Int = 32
    private const val POLY1305_TAG_LENGTH: Int = 16
    private const val NONCE_LENGTH: Int = 12
    private const val HEAD_LENGTH = X25519_KEY_LENGTH
    private const val MIN_CIPHERTEXT_LENGTH = HEAD_LENGTH + POLY1305_TAG_LENGTH

    fun generateKeys(): ChaChaBoxKeys {
        val pair = x25519GenerateKeyPair()
        return ChaChaBoxKeys.V1(pair.secret, pair.public)
    }

    fun seal(
        recipientPublicKey: ChaChaBoxPublicKey,
        plaintext: ByteArray,
    ): ByteArray =
        when (recipientPublicKey) {
            is ChaChaBoxPublicKey.V1 -> sealV1(recipientPublicKey.x25519, plaintext)
        }

    fun unseal(
        clientKeys: ChaChaBoxKeys,
        ciphertext: ByteArray,
    ): ByteArray =
        when (clientKeys) {
            is ChaChaBoxKeys.V1 -> unsealV1(clientKeys, ciphertext)
        }

    private fun unsealV1(
        clientKeys: ChaChaBoxKeys.V1,
        ciphertext: ByteArray,
    ): ByteArray {
        if (ciphertext.size < MIN_CIPHERTEXT_LENGTH) {
            throw ChaChaBoxDecryptError("ciphertext too short: ${ciphertext.size} < $MIN_CIPHERTEXT_LENGTH")
        }
        val ephemeralPublicKey = ciphertext.copyOfRange(0, HEAD_LENGTH)
        val body = ciphertext.copyOfRange(HEAD_LENGTH, ciphertext.size)
        val nonce = blake2bNonce(ephemeralPublicKey, clientKeys.x25519Public)
        val info = ephemeralPublicKey + clientKeys.x25519Public
        val shared =
            try {
                x25519SharedSecret(clientKeys.x25519Secret, ephemeralPublicKey)
            } catch (e: IllegalArgumentException) {
                throw ChaChaBoxDecryptError("invalid ephemeral public key", e)
            } catch (e: IllegalStateException) {
                throw ChaChaBoxDecryptError("x25519 agreement failed", e)
            }
        val key = hkdfBlake2b512(shared, info, CHACHA_KEY_LENGTH)
        return try {
            chacha20Poly1305Decrypt(key, nonce, body)
        } catch (e: Exception) {
            throw ChaChaBoxDecryptError("chacha20-poly1305 decryption failed", e)
        }
    }

    private fun sealV1(
        recipientX25519Public: ByteArray,
        plaintext: ByteArray,
    ): ByteArray {
        val ephemeral = generateKeys() as ChaChaBoxKeys.V1
        val nonce = blake2bNonce(ephemeral.x25519Public, recipientX25519Public)
        val info = ephemeral.x25519Public + recipientX25519Public
        val shared = x25519SharedSecret(ephemeral.x25519Secret, recipientX25519Public)
        val key = hkdfBlake2b512(shared, info, CHACHA_KEY_LENGTH)
        val body = chacha20Poly1305Encrypt(key, nonce, plaintext)
        return ephemeral.x25519Public + body
    }

    private fun blake2bNonce(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray = blake2bDigest(a + b, NONCE_LENGTH)
}
