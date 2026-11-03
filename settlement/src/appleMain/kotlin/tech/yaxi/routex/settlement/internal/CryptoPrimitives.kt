package tech.yaxi.routex.settlement.internal

import tech.yaxi.routex.settlement.interop.RTXCryptoPrimitives

private const val ML_KEM_SHARED_SECRET_LENGTH: Int = 32

internal actual fun sha256(bytes: ByteArray): ByteArray = RTXCryptoPrimitives.sha256(bytes.toNSData()).toByteArray()

internal actual fun blake2bDigest(
    message: ByteArray,
    outLen: Int,
): ByteArray = RTXCryptoPrimitives.blake2b(message.toNSData(), outputLength = outLen.toLong()).toByteArray()

internal actual fun hkdfBlake2b512(
    ikm: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray =
    RTXCryptoPrimitives
        .hkdfBlake2b512WithIkm(ikm.toNSData(), salt = salt.toNSData(), info = info.toNSData(), length = length.toLong())
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

internal actual fun secureRandomBytes(length: Int): ByteArray =
    RTXCryptoPrimitives.randomBytes(length.toLong()).toByteArray()

internal actual fun mlKem768IsAvailable(): Boolean = RTXCryptoPrimitives.mlKem768IsAvailable()

internal actual fun mlKem768KeyPair(seed: ByteArray): MlKem768KeyPair {
    val keyPair = interop { error -> RTXCryptoPrimitives.mlKem768KeyPairForSeed(seed.toNSData(), error = error) }
    return object : MlKem768KeyPair {
        override val encapsulationKey: ByteArray = keyPair.encapsulationKey.toByteArray()

        override fun decapsulate(ciphertext: ByteArray): ByteArray =
            interop { error -> keyPair.decapsulate(ciphertext.toNSData(), error = error) }.toByteArray()
    }
}

internal actual fun mlKem768IsValidEncapsulationKey(encapsulationKey: ByteArray): Boolean =
    RTXCryptoPrimitives.mlKem768IsValidEncapsulationKey(encapsulationKey.toNSData())

internal actual fun mlKem768Encapsulate(encapsulationKey: ByteArray): MlKem768Encapsulation {
    val encapsulated =
        interop { error ->
            RTXCryptoPrimitives.mlKem768EncapsulateWithEncapsulationKey(encapsulationKey.toNSData(), error = error)
        }.toByteArray()
    val ciphertextLength = encapsulated.size - ML_KEM_SHARED_SECRET_LENGTH
    return MlKem768Encapsulation(
        ciphertext = encapsulated.copyOfRange(0, ciphertextLength),
        sharedSecret = encapsulated.copyOfRange(ciphertextLength, encapsulated.size),
    )
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
