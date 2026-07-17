package tech.yaxi.routex.settlement.internal

import kotlin.time.Instant
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array

internal actual open class X509Certificate internal constructor(
    internal val jsX509Cert: PeculiarX509.X509Certificate,
) {
    actual val subjectName: String get() = jsX509Cert.subject
    actual val issuerName: String get() = jsX509Cert.issuer

    actual val subjectCommonName: String?
        get() =
            jsX509Cert.subject
                .split(',')
                .map { it.trim() }
                .firstOrNull { it.startsWith("CN=") }
                ?.removePrefix("CN=")

    actual val validFrom: Instant
        get() =
            Instant.fromEpochMilliseconds(
                (jsX509Cert.asDynamic().notBefore.getTime() as Double).toLong(),
            )

    actual val validUntil: Instant
        get() =
            Instant.fromEpochMilliseconds(
                (jsX509Cert.asDynamic().notAfter.getTime() as Double).toLong(),
            )

    // BasicConstraintsExtension.ca: absent extension or cA=false (DEFAULT) → not a CA.
    actual val isCertificateAuthority: Boolean
        get() {
            val ext = jsX509Cert.asDynamic().getExtension("2.5.29.19")
            return ext != null && ext.ca.unsafeCast<Boolean>()
        }

    // BasicConstraintsExtension.pathLength is null/undefined when not set.
    actual val maxIntermediateCertificates: Int?
        get() {
            val ext = jsX509Cert.asDynamic().getExtension("2.5.29.19")
            if (ext == null) return null
            val pathLength = ext.pathLength
            if (pathLength == null) return null
            return (pathLength as Double).toInt()
        }

    // KeyUsagesExtension.usages is a bitmask; KeyUsageFlags.keyCertSign = 32.
    // Absent Key Usage extension means no restriction.
    actual val allowsCertificateSigning: Boolean
        get() {
            val ext = jsX509Cert.asDynamic().getExtension("2.5.29.15")
            if (ext == null) return true
            return ((ext.usages as Double).toInt() and 32) != 0
        }

    actual suspend fun verifySignatureBy(issuer: X509Certificate) {
        val params: dynamic = js("({})")
        params.publicKey = issuer.jsX509Cert.publicKey
        params.signatureOnly = true
        val valid =
            try {
                jsX509Cert.verify(params).await()
            } catch (e: Throwable) {
                throw VcekCertificateError.SignatureInvalid(
                    e.message ?: "certificate signature verification error",
                    e,
                )
            }
        if (!valid) throw VcekCertificateError.SignatureInvalid("certificate signature invalid")
    }

    actual fun extensionAsIa5String(oid: String): String? {
        val extBytes = findExtensionBytes(oid) ?: return null
        val r = Asn1js.fromBER(extBytes)
        checkBerResult(r, oid)
        val tagNumber =
            r.result.idBlock.tagNumber
                .unsafeCast<Int>()
        if (tagNumber != 22) { // 22 = IA5String ASN.1 tag
            throw VcekCertificateError.MalformedExtension("extension $oid is not a valid IA5String")
        }
        return r.result.valueBlock.value
            .unsafeCast<String>()
    }

    actual fun extensionAsInteger(oid: String): Int? {
        val extBytes = findExtensionBytes(oid) ?: return null
        val r = Asn1js.fromBER(extBytes)
        checkBerResult(r, oid)
        val tagNumber =
            r.result.idBlock.tagNumber
                .unsafeCast<Int>()
        if (tagNumber != 2) { // 2 = INTEGER ASN.1 tag
            throw VcekCertificateError.MalformedExtension("extension $oid is not a valid INTEGER")
        }
        val valueDec =
            r.result.valueBlock.valueDec
                .unsafeCast<Double>()
        if (valueDec < 0 || valueDec > Int.MAX_VALUE.toDouble()) {
            throw VcekCertificateError.MalformedExtension(
                "extension $oid INTEGER does not fit in Int (got $valueDec)",
            )
        }
        return valueDec.toInt()
    }

    private fun checkBerResult(
        r: dynamic,
        oid: String,
    ) {
        if (r.offset.unsafeCast<Int>() < 0) {
            throw VcekCertificateError.MalformedExtension("extension $oid: BER parse failed")
        }
    }

    private fun findExtensionBytes(oid: String): Uint8Array? {
        val ext = jsX509Cert.extensions.find { it.type == oid } ?: return null
        return Uint8Array(ext.value.unsafeCast<ArrayBuffer>())
    }
}

internal actual class VcekCertificate internal constructor(
    jsX509Cert: PeculiarX509.X509Certificate,
) : X509Certificate(jsX509Cert) {
    actual fun verifyEcdsaP384Sha384Signature(
        message: ByteArray,
        rBigEndian: ByteArray,
        sBigEndian: ByteArray,
    ): Boolean {
        val spki =
            PeculiarAsn1Schema.AsnConvert.parse(
                jsX509Cert.publicKey.rawData,
                PeculiarAsn1X509.SubjectPublicKeyInfo,
            )
        val rawKey = Uint8Array(spki.subjectPublicKey.unsafeCast<ArrayBuffer>())

        // Build the raw 96-byte (r || s) signature expected by @noble/curves p384.
        val sig = Uint8Array(96)
        sig.set(rBigEndian.toUint8Array(), 0)
        sig.set(sBigEndian.toUint8Array(), 48)

        val opts: dynamic = js("({})")
        opts.lowS = false

        return try {
            NobleCurvesNist.p384.verify(sig, message.toUint8Array(), rawKey, opts)
        } catch (_: Throwable) {
            false
        }
    }
}

private val pemRegex = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")

internal actual fun parseX509CertificateChain(pem: String): List<X509Certificate> {
    val matches = pemRegex.findAll(pem).toList()
    if (matches.isEmpty()) throw VcekCertificateError.MalformedPem("no PEM certificate found")
    return matches.map { match ->
        try {
            X509Certificate(PeculiarX509.X509Certificate(match.value))
        } catch (e: Throwable) {
            throw VcekCertificateError.MalformedPem(
                "failed to parse PEM certificate: ${e.message}",
                e,
            )
        }
    }
}

internal actual fun X509Certificate.asVcek(): VcekCertificate = VcekCertificate(jsX509Cert)
