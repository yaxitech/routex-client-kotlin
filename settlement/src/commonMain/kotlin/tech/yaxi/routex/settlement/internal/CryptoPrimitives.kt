package tech.yaxi.routex.settlement.internal

// Platform-agnostic cryptographic primitive surface consumed by settlement verification
// in `commonMain`. Each function takes raw byte arrays and returns raw byte arrays, no
// platform crypto types leak through.
//
// JVM/Android back these with BouncyCastle. A future Darwin source set will back them
// with CryptoKit / CommonCrypto. No other consumer of this package may take a direct
// dependency on a platform crypto library.

/** SHA-256 digest. */
internal expect fun sha256(bytes: ByteArray): ByteArray

/** Blake2b keyed MAC reduced to [outLen] bytes. Used to derive the ChaChaBox nonce. */
internal expect fun blake2bDigest(
    message: ByteArray,
    outLen: Int,
): ByteArray

/** HKDF-Blake2b-512 with empty salt. */
internal expect fun hkdfBlake2b512(
    ikm: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray

/**
 * ChaCha20-Poly1305 encrypt. Returns `ciphertext || 16-byte tag`.
 * [nonce] must be 12 bytes; [key] must be 32 bytes.
 */
internal expect fun chacha20Poly1305Encrypt(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
): ByteArray

/**
 * ChaCha20-Poly1305 decrypt. Expects `ciphertext || 16-byte tag`.
 * [nonce] must be 12 bytes; [key] must be 32 bytes.
 */
internal expect fun chacha20Poly1305Decrypt(
    key: ByteArray,
    nonce: ByteArray,
    ciphertextWithTag: ByteArray,
): ByteArray

/** X25519 scalar multiplication. Both inputs are 32 bytes. */
internal expect fun x25519SharedSecret(
    mySecret: ByteArray,
    theirPublic: ByteArray,
): ByteArray

/** Freshly-generated X25519 keypair. */
internal expect fun x25519GenerateKeyPair(): X25519Keys

internal data class X25519Keys(
    val secret: ByteArray,
    val public: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is X25519Keys &&
            secret.contentEquals(other.secret) &&
            public.contentEquals(other.public)

    override fun hashCode(): Int = secret.contentHashCode() * 31 + public.contentHashCode()
}

/** Ed25519 signature verification. [publicKey] is a raw 32-byte Ed25519 key. */
internal expect fun ed25519Verify(
    publicKey: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): Boolean

// ECDSA P-384 + SHA-384 verification lives on [VcekCertificate]; the leaf VCEK's own
// public key is the only one we ever feed into it (attestation-report signature).
