package tech.yaxi.routex.settlement.internal

import org.khronos.webgl.Uint8Array

internal actual fun sha256(bytes: ByteArray): ByteArray = NobleSha2.sha256(bytes.toUint8Array()).toKotlinByteArray()

internal actual fun blake2bDigest(
    message: ByteArray,
    outLen: Int,
): ByteArray =
    NobleBlake2.blake2b
        .create(js("({dkLen: outLen})"))
        .update(message.toUint8Array())
        .digest()
        .toKotlinByteArray()

internal actual fun hkdfBlake2b512(
    ikm: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray {
    val blake2b64Factory =
        NobleHashUtils.createHasher {
            NobleBlake2.blake2b.create(js("({dkLen: 64})"))
        }
    return NobleHkdf
        .hkdf(
            blake2b64Factory,
            ikm.toUint8Array(),
            Uint8Array(0),
            info.toUint8Array(),
            length,
        ).toKotlinByteArray()
}

internal actual fun chacha20Poly1305Encrypt(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
): ByteArray =
    NobleChaCha
        .chacha20poly1305(key.toUint8Array(), nonce.toUint8Array())
        .encrypt(plaintext.toUint8Array())
        .toKotlinByteArray()

internal actual fun chacha20Poly1305Decrypt(
    key: ByteArray,
    nonce: ByteArray,
    ciphertextWithTag: ByteArray,
): ByteArray =
    NobleChaCha
        .chacha20poly1305(key.toUint8Array(), nonce.toUint8Array())
        .decrypt(ciphertextWithTag.toUint8Array())
        .toKotlinByteArray()

internal actual fun x25519SharedSecret(
    mySecret: ByteArray,
    theirPublic: ByteArray,
): ByteArray =
    NobleCurvesEd25519.x25519
        .getSharedSecret(mySecret.toUint8Array(), theirPublic.toUint8Array())
        .toKotlinByteArray()

internal actual fun x25519GenerateKeyPair(): X25519Keys {
    val secretKey = NobleCurvesEd25519.x25519.utils.randomSecretKey()
    return X25519Keys(
        secret = secretKey.toKotlinByteArray(),
        public = NobleCurvesEd25519.x25519.getPublicKey(secretKey).toKotlinByteArray(),
    )
}

internal actual fun ed25519Verify(
    publicKey: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): Boolean =
    NobleCurvesEd25519.ed25519.verify(
        signature.toUint8Array(),
        message.toUint8Array(),
        publicKey.toUint8Array(),
    )
