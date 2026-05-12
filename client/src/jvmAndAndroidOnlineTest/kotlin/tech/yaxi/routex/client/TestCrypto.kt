package tech.yaxi.routex.client

import java.security.SecureRandom
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * Encrypt [plaintext] using the "on-file-data" HKDF-Blake2b-512 + ChaCha20-Poly1305
 * recipe. Returned bytes are `nonce || ciphertext || tag`, the same wire shape the
 * YAXI service produces when returning encrypted identifiers. Test-only.
 */
internal fun encryptOnFileData(
    plaintext: ByteArray,
    secret: ByteArray,
): ByteArray {
    val hkdf = HKDFBytesGenerator(Blake2bDigest(512))
    hkdf.init(HKDFParameters(secret, null, "on-file-data".encodeToByteArray()))
    val key = ByteArray(32).also { hkdf.generateBytes(it, 0, it.size) }
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = ChaCha20Poly1305()
    cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
    val out = ByteArray(cipher.getOutputSize(plaintext.size))
    val len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
    cipher.doFinal(out, len)
    return nonce + out
}
