package tech.yaxi.routex.settlement.internal

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
    salt: ByteArray,
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
            salt.toUint8Array(),
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

internal actual fun secureRandomBytes(length: Int): ByteArray = NobleHashUtils.randomBytes(length).toKotlinByteArray()

internal actual fun mlKem768IsAvailable(): Boolean = true

internal actual fun mlKem768KeyPair(seed: ByteArray): MlKem768KeyPair {
    val keys = NobleMlKem.mlKem768.keygen(seed.toUint8Array())
    return object : MlKem768KeyPair {
        override val encapsulationKey: ByteArray = keys.publicKey.toKotlinByteArray()

        override fun decapsulate(ciphertext: ByteArray): ByteArray =
            NobleMlKem.mlKem768.decapsulate(ciphertext.toUint8Array(), keys.secretKey).toKotlinByteArray()
    }
}

internal actual fun mlKem768IsValidEncapsulationKey(encapsulationKey: ByteArray): Boolean =
    try {
        // The encapsulation key check runs as part of encapsulation.
        NobleMlKem.mlKem768.encapsulate(encapsulationKey.toUint8Array())
        true
    } catch (_: Throwable) {
        false
    }

internal actual fun mlKem768Encapsulate(encapsulationKey: ByteArray): MlKem768Encapsulation {
    val encapsulated = NobleMlKem.mlKem768.encapsulate(encapsulationKey.toUint8Array())
    return MlKem768Encapsulation(
        ciphertext = encapsulated.cipherText.toKotlinByteArray(),
        sharedSecret = encapsulated.sharedSecret.toKotlinByteArray(),
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
