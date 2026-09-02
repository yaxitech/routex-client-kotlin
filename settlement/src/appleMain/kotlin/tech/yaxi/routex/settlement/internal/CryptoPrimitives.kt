package tech.yaxi.routex.settlement.internal

import tech.yaxi.routex.settlement.interop.RTXCryptoPrimitives

internal actual fun sha256(bytes: ByteArray): ByteArray = RTXCryptoPrimitives.sha256(bytes.toNSData()).toByteArray()

internal actual fun blake2bDigest(
    message: ByteArray,
    outLen: Int,
): ByteArray = RTXCryptoPrimitives.blake2b(message.toNSData(), outputLength = outLen.toLong()).toByteArray()

internal actual fun hkdfBlake2b512(
    ikm: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray =
    RTXCryptoPrimitives
        .hkdfBlake2b512WithIkm(ikm.toNSData(), info = info.toNSData(), length = length.toLong())
        .toByteArray()

internal actual fun chacha20Poly1305Encrypt(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
): ByteArray =
    interop { error ->
        RTXCryptoPrimitives.chacha20Poly1305SealWithKey(
            key.toNSData(),
            nonce = nonce.toNSData(),
            plaintext = plaintext.toNSData(),
            error = error,
        )
    }.toByteArray()

internal actual fun chacha20Poly1305Decrypt(
    key: ByteArray,
    nonce: ByteArray,
    ciphertextWithTag: ByteArray,
): ByteArray =
    interop { error ->
        RTXCryptoPrimitives.chacha20Poly1305OpenWithKey(
            key.toNSData(),
            nonce = nonce.toNSData(),
            ciphertextWithTag = ciphertextWithTag.toNSData(),
            error = error,
        )
    }.toByteArray()

internal actual fun x25519SharedSecret(
    mySecret: ByteArray,
    theirPublic: ByteArray,
): ByteArray =
    interop { error ->
        RTXCryptoPrimitives.x25519SharedSecretWithSecret(
            mySecret.toNSData(),
            publicKey = theirPublic.toNSData(),
            error = error,
        )
    }.toByteArray()

internal actual fun x25519GenerateKeyPair(): X25519Keys {
    val secret = RTXCryptoPrimitives.x25519GenerateSecretKey()
    val public =
        interop { error ->
            RTXCryptoPrimitives.x25519PublicKeyForSecret(secret, error = error)
        }
    return X25519Keys(secret = secret.toByteArray(), public = public.toByteArray())
}

internal actual fun ed25519Verify(
    publicKey: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): Boolean =
    RTXCryptoPrimitives.ed25519VerifyWithPublicKey(
        publicKey.toNSData(),
        message = message.toNSData(),
        signature = signature.toNSData(),
    )
