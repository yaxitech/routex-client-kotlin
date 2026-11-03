package tech.yaxi.routex.settlement

import tech.yaxi.routex.settlement.internal.blake2bDigest
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Decrypt
import tech.yaxi.routex.settlement.internal.chacha20Poly1305Encrypt
import tech.yaxi.routex.settlement.internal.hkdfBlake2b512
import tech.yaxi.routex.settlement.internal.mlKem768Encapsulate
import tech.yaxi.routex.settlement.internal.mlKem768IsAvailable
import tech.yaxi.routex.settlement.internal.secureRandomBytes
import tech.yaxi.routex.settlement.internal.x25519GenerateKeyPair
import tech.yaxi.routex.settlement.internal.x25519SharedSecret

/**
 * ChaChaBox seal/unseal primitive (X25519 ephemeral-static Diffie-Hellman, combined with
 * ML-KEM-768 for V2 keys, keyed via HKDF-Blake2b-512 and ChaCha20-Poly1305) used to
 * encrypt request/response bodies between the client and the YAXI TEE.
 *
 * Boxes start with the recipient key's version byte:
 * - V1: `0x01 || x25519_ephemeral_pk(32) || ct`
 * - V2: `0x02 || ml_kem_ct(1088) || x25519_ephemeral_pk(32) || ct`
 *
 * All ciphersuite logic is in `commonMain`; only the crypto primitives are `expect`.
 */
internal object ChaChaBox {
    private const val X25519_KEY_LENGTH: Int = ChaChaBoxKeys.V1.X25519_KEY_LENGTH
    private const val ML_KEM_CIPHERTEXT_LENGTH: Int = 1088
    private const val CHACHA_KEY_LENGTH: Int = 32
    private const val POLY1305_TAG_LENGTH: Int = 16
    private const val NONCE_LENGTH: Int = 12
    private const val V2_KDF_SALT: String = "chacha_box_ietf/v2"

    /** Generates V2 keys where ML-KEM-768 is available, V1 keys otherwise. */
    fun generateKeys(): ChaChaBoxKeys {
        val x25519 = x25519GenerateKeyPair()
        if (!mlKem768IsAvailable()) return ChaChaBoxKeys.V1(x25519.secret, x25519.public)
        return ChaChaBoxKeys.V2(x25519.secret, x25519.public, secureRandomBytes(ChaChaBoxKeys.V2.ML_KEM_SEED_LENGTH))
    }

    fun seal(
        recipientPublicKey: ChaChaBoxPublicKey,
        plaintext: ByteArray,
    ): ByteArray {
        val box =
            wrapFailures(ChaChaBoxError::SealingFailed) {
                when (recipientPublicKey) {
                    is ChaChaBoxPublicKey.V1 -> sealV1(recipientPublicKey, plaintext)
                    is ChaChaBoxPublicKey.V2 -> sealV2(recipientPublicKey, plaintext)
                }
            }
        return byteArrayOf(recipientPublicKey.version) + box
    }

    fun unseal(
        clientKeys: ChaChaBoxKeys,
        ciphertext: ByteArray,
    ): ByteArray {
        if (ciphertext.isEmpty()) throw ChaChaBoxError.UnsealingFailed()
        val version = ciphertext[0]
        // A key opens boxes of its own version only. For V2 keys, an older box is a downgrade:
        // a V1-only sender cannot parse a V2 public key.
        if (version != clientKeys.publicKey.version) throw ChaChaBoxError.UnsupportedVersion(version)
        val box = ciphertext.copyOfRange(1, ciphertext.size)
        return wrapFailures(ChaChaBoxError::UnsealingFailed) {
            when (clientKeys) {
                is ChaChaBoxKeys.V1 -> unsealV1(clientKeys, box)
                is ChaChaBoxKeys.V2 -> unsealV2(clientKeys, box)
            }
        }
    }

    private fun sealV1(
        recipient: ChaChaBoxPublicKey.V1,
        plaintext: ByteArray,
    ): ByteArray {
        val ephemeral = x25519GenerateKeyPair()
        val shared = x25519Agreement(ephemeral.secret, recipient.x25519)
        val key = deriveKeyV1(shared, ephemeral.public, recipient.x25519)
        val nonce = blake2bNonce(ephemeral.public, recipient.x25519)
        return ephemeral.public + chacha20Poly1305Encrypt(key, nonce, plaintext)
    }

    private fun unsealV1(
        clientKeys: ChaChaBoxKeys.V1,
        box: ByteArray,
    ): ByteArray {
        require(box.size >= X25519_KEY_LENGTH + POLY1305_TAG_LENGTH)
        val ephemeralPublicKey = box.copyOfRange(0, X25519_KEY_LENGTH)
        val shared = x25519Agreement(clientKeys.x25519Secret, ephemeralPublicKey)
        val key = deriveKeyV1(shared, ephemeralPublicKey, clientKeys.x25519Public)
        val nonce = blake2bNonce(ephemeralPublicKey, clientKeys.x25519Public)
        return chacha20Poly1305Decrypt(key, nonce, box.copyOfRange(X25519_KEY_LENGTH, box.size))
    }

    private fun sealV2(
        recipient: ChaChaBoxPublicKey.V2,
        plaintext: ByteArray,
    ): ByteArray {
        val encapsulated = mlKem768Encapsulate(recipient.mlKem)
        val ephemeral = x25519GenerateKeyPair()
        val shared = x25519Agreement(ephemeral.secret, recipient.x25519)
        val key = deriveKeyV2(shared, encapsulated.sharedSecret, encapsulated.ciphertext, ephemeral.public, recipient)
        val nonce = blake2bNonce(ephemeral.public, recipient.x25519)
        return encapsulated.ciphertext + ephemeral.public + chacha20Poly1305Encrypt(key, nonce, plaintext)
    }

    private fun unsealV2(
        clientKeys: ChaChaBoxKeys.V2,
        box: ByteArray,
    ): ByteArray {
        val headLength = ML_KEM_CIPHERTEXT_LENGTH + X25519_KEY_LENGTH
        require(box.size >= headLength + POLY1305_TAG_LENGTH)
        val mlKemCiphertext = box.copyOfRange(0, ML_KEM_CIPHERTEXT_LENGTH)
        val ephemeralPublicKey = box.copyOfRange(ML_KEM_CIPHERTEXT_LENGTH, headLength)
        val mlKemShared = clientKeys.mlKem.decapsulate(mlKemCiphertext)
        val shared = x25519Agreement(clientKeys.x25519Secret, ephemeralPublicKey)
        val key = deriveKeyV2(shared, mlKemShared, mlKemCiphertext, ephemeralPublicKey, clientKeys.publicKey)
        val nonce = blake2bNonce(ephemeralPublicKey, clientKeys.x25519Public)
        return chacha20Poly1305Decrypt(key, nonce, box.copyOfRange(headLength, box.size))
    }

    private fun deriveKeyV1(
        x25519Shared: ByteArray,
        ephemeralPublicKey: ByteArray,
        recipientX25519: ByteArray,
    ): ByteArray = hkdfBlake2b512(x25519Shared, ByteArray(0), ephemeralPublicKey + recipientX25519, CHACHA_KEY_LENGTH)

    private fun deriveKeyV2(
        x25519Shared: ByteArray,
        mlKemShared: ByteArray,
        mlKemCiphertext: ByteArray,
        ephemeralPublicKey: ByteArray,
        recipient: ChaChaBoxPublicKey.V2,
    ): ByteArray {
        val info =
            byteArrayOf(ChaChaBoxPublicKey.V2.VERSION) + mlKemCiphertext + ephemeralPublicKey +
                recipient.x25519 + recipient.mlKem
        return hkdfBlake2b512(x25519Shared + mlKemShared, V2_KDF_SALT.encodeToByteArray(), info, CHACHA_KEY_LENGTH)
    }

    /** X25519 agreement that rejects the all-zero result of a low-order public key. */
    private fun x25519Agreement(
        secret: ByteArray,
        public: ByteArray,
    ): ByteArray {
        val shared = x25519SharedSecret(secret, public)
        // Accumulate instead of short-circuiting so the check takes constant time.
        require(shared.fold(0) { acc, byte -> acc or byte.toInt() } != 0) { "X25519 shared secret is all zero" }
        return shared
    }

    /** Runs [block], wrapping any failure in [error] so that callers only see [ChaChaBoxError]. */
    private inline fun wrapFailures(
        error: (Throwable) -> ChaChaBoxError,
        block: () -> ByteArray,
    ): ByteArray =
        try {
            block()
        } catch (e: Throwable) {
            throw error(e)
        }

    private fun blake2bNonce(
        a: ByteArray,
        b: ByteArray,
    ): ByteArray = blake2bDigest(a + b, NONCE_LENGTH)
}
