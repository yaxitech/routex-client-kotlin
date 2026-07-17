package tech.yaxi.routex.settlement.internal

import kotlin.js.Promise
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

internal fun ByteArray.toUint8Array(): Uint8Array {
    val i8 = unsafeCast<Int8Array>()
    return Uint8Array(i8.buffer, i8.byteOffset, i8.length)
}

internal fun Uint8Array.toKotlinByteArray(): ByteArray = Int8Array(buffer, byteOffset, length).unsafeCast<ByteArray>()

internal external interface Blake2bHasher {
    public fun update(data: Uint8Array): Blake2bHasher

    public fun digest(): Uint8Array
}

internal external interface Blake2bFactory {
    public fun create(opts: dynamic = definedExternally): Blake2bHasher
}

internal external interface ChaChaCipher {
    public fun encrypt(plaintext: Uint8Array): Uint8Array

    public fun decrypt(ciphertext: Uint8Array): Uint8Array
}

internal external interface JsX25519Utils {
    public fun randomSecretKey(): Uint8Array
}

internal external interface JsX25519 {
    public val utils: JsX25519Utils

    public fun getPublicKey(secret: Uint8Array): Uint8Array

    public fun getSharedSecret(
        secret: Uint8Array,
        pub: Uint8Array,
    ): Uint8Array
}

internal external interface JsEd25519 {
    public fun verify(
        signature: Uint8Array,
        message: Uint8Array,
        publicKey: Uint8Array,
    ): Boolean
}

internal external interface JsP384 {
    public fun verify(
        signature: Uint8Array,
        message: Uint8Array,
        publicKey: Uint8Array,
        opts: dynamic = definedExternally,
    ): Boolean
}

internal external interface PeculiarExtension {
    public val type: String
    public val value: dynamic
}

@JsModule("@noble/hashes/sha2.js")
internal external object NobleSha2 {
    public fun sha256(data: Uint8Array): Uint8Array
}

@JsModule("@noble/hashes/blake2.js")
internal external object NobleBlake2 {
    public val blake2b: Blake2bFactory
}

@JsModule("@noble/hashes/hkdf.js")
internal external object NobleHkdf {
    public fun hkdf(
        hash: dynamic,
        ikm: Uint8Array,
        salt: Uint8Array,
        info: Uint8Array,
        length: Int,
    ): Uint8Array
}

@JsModule("@noble/hashes/utils.js")
internal external object NobleHashUtils {
    public fun createHasher(fn: () -> Blake2bHasher): dynamic
}

@JsModule("@noble/ciphers/chacha.js")
internal external object NobleChaCha {
    public fun chacha20poly1305(
        key: Uint8Array,
        nonce: Uint8Array,
    ): ChaChaCipher
}

@JsModule("@noble/curves/ed25519.js")
internal external object NobleCurvesEd25519 {
    public val x25519: JsX25519
    public val ed25519: JsEd25519
}

@JsModule("@noble/curves/nist.js")
internal external object NobleCurvesNist {
    public val p384: JsP384
}

@JsModule("@peculiar/x509")
internal external object PeculiarX509 {
    public class X509Certificate(
        pem: String,
    ) {
        public val subject: String
        public val issuer: String
        public val extensions: Array<PeculiarExtension>
        public val publicKey: dynamic

        public fun verify(params: dynamic): Promise<Boolean>
    }
}

@JsModule("asn1js")
internal external object Asn1js {
    public fun fromBER(data: Uint8Array): dynamic
}

@JsModule("@peculiar/asn1-schema")
internal external object PeculiarAsn1Schema {
    public val AsnConvert: dynamic
}

@JsModule("@peculiar/asn1-x509")
internal external object PeculiarAsn1X509 {
    public val SubjectPublicKeyInfo: dynamic
}
