package tech.yaxi.routex.settlement.internal

import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
 * Single shared BouncyCastle provider + X.509 certificate factory. Co-owned by
 * [VcekCertificate] (chain parsing, signature verification) and any other
 * jvmAndAndroidMain helper that needs a JCA provider, instantiating BouncyCastle is
 * non-trivial, so we cache one instance for the lifetime of the JVM.
 */
internal object BouncyCastleProviderHolder {
    val provider: BouncyCastleProvider = BouncyCastleProvider()
    val certificateFactory: CertificateFactory = CertificateFactory.getInstance("X.509", provider)
}

private const val X25519_KEY_LENGTH: Int = 32
private const val POLY1305_TAG_LENGTH: Int = 16
private const val BLAKE2B_512_BITS: Int = 512

internal actual fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal actual fun blake2bDigest(
    message: ByteArray,
    outLen: Int,
): ByteArray {
    val digest = Blake2bDigest(outLen * Byte.SIZE_BITS)
    digest.update(message, 0, message.size)
    val out = ByteArray(outLen)
    digest.doFinal(out, 0)
    return out
}

internal actual fun hkdfBlake2b512(
    ikm: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray {
    val hkdf = HKDFBytesGenerator(Blake2bDigest(BLAKE2B_512_BITS))
    hkdf.init(HKDFParameters(ikm, ByteArray(0), info))
    val out = ByteArray(length)
    hkdf.generateBytes(out, 0, length)
    return out
}

internal actual fun chacha20Poly1305Encrypt(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
): ByteArray {
    val cipher = ChaCha20Poly1305()
    cipher.init(true, AEADParameters(KeyParameter(key), POLY1305_TAG_LENGTH * Byte.SIZE_BITS, nonce))
    val out = ByteArray(cipher.getOutputSize(plaintext.size))
    val produced = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
    val finalLen = cipher.doFinal(out, produced)
    val totalLen = produced + finalLen
    return if (totalLen == out.size) out else out.copyOf(totalLen)
}

internal actual fun chacha20Poly1305Decrypt(
    key: ByteArray,
    nonce: ByteArray,
    ciphertextWithTag: ByteArray,
): ByteArray {
    val cipher = ChaCha20Poly1305()
    cipher.init(false, AEADParameters(KeyParameter(key), POLY1305_TAG_LENGTH * Byte.SIZE_BITS, nonce))
    val out = ByteArray(cipher.getOutputSize(ciphertextWithTag.size))
    val produced = cipher.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, out, 0)
    val finalLen = cipher.doFinal(out, produced)
    val totalLen = produced + finalLen
    return if (totalLen == out.size) out else out.copyOf(totalLen)
}

internal actual fun x25519SharedSecret(
    mySecret: ByteArray,
    theirPublic: ByteArray,
): ByteArray {
    require(mySecret.size == X25519_KEY_LENGTH) { "X25519 secret key must be $X25519_KEY_LENGTH bytes" }
    require(theirPublic.size == X25519_KEY_LENGTH) { "X25519 public key must be $X25519_KEY_LENGTH bytes" }
    val priv = X25519PrivateKeyParameters(mySecret, 0)
    val pub = X25519PublicKeyParameters(theirPublic, 0)
    val agreement = X25519Agreement().apply { init(priv) }
    val shared = ByteArray(agreement.agreementSize)
    agreement.calculateAgreement(pub, shared, 0)
    return shared
}

internal actual fun x25519GenerateKeyPair(): X25519Keys {
    val priv = X25519PrivateKeyParameters(SecureRandom())
    return X25519Keys(priv.encoded, priv.generatePublicKey().encoded)
}

internal actual fun ed25519Verify(
    publicKey: ByteArray,
    message: ByteArray,
    signature: ByteArray,
): Boolean {
    val signer = Ed25519Signer()
    signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
    signer.update(message, 0, message.size)
    return signer.verifySignature(signature)
}

// ECDSA P-384 + SHA-384 verification lives on [VcekCertificate], see
// `verifyEcdsaP384Sha384Signature`. The leaf VCEK's own public key is the only one we
// ever need it for (SEV-SNP attestation-report signature).
