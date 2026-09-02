package tech.yaxi.routex.settlement.internal

import kotlin.math.roundToLong
import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import tech.yaxi.routex.settlement.interop.RTXCertificate

internal actual open class X509Certificate internal constructor(
    internal val raw: RTXCertificate,
) {
    actual val subjectDn: DistinguishedName
        get() = DistinguishedName(raw.subjectDN.toByteArray())

    actual val issuerDn: DistinguishedName
        get() = DistinguishedName(raw.issuerDN.toByteArray())

    actual val subjectCommonName: String?
        get() = raw.subjectCommonName

    actual val validFrom: Instant
        get() = raw.notValidBefore.toInstant()

    actual val validUntil: Instant
        get() = raw.notValidAfter.toInstant()

    actual val isCertificateAuthority: Boolean
        get() = raw.isCertificateAuthority

    actual val maxIntermediateCertificates: Int?
        // -1: unconstrained or absent (Objective-C cannot carry an optional Int).
        get() = raw.maxIntermediateCertificates.toInt().takeIf { it >= 0 }

    actual val allowsCertificateSigning: Boolean
        get() = raw.allowsCertificateSigning

    actual suspend fun verifySignatureBy(issuer: X509Certificate) {
        try {
            interopCheck { error -> raw.verifySignatureBy(issuer.raw, error = error) }
        } catch (e: AppleInteropException) {
            throw VcekCertificateError.SignatureInvalid(
                e.message ?: "certificate signature invalid",
                e,
            )
        }
    }

    actual fun extensionAsIa5String(oid: String): String? {
        if (!raw.hasExtension(oid)) return null
        return try {
            interop { error -> raw.ia5StringExtensionWithOid(oid, error = error) }
        } catch (e: AppleInteropException) {
            throw VcekCertificateError.MalformedExtension(
                "extension $oid is not a valid IA5String",
                e,
            )
        }
    }

    actual fun extensionAsInteger(oid: String): Int? {
        if (!raw.hasExtension(oid)) return null
        val value =
            try {
                interop { error -> raw.integerExtensionWithOid(oid, error = error) }.longLongValue
            } catch (e: AppleInteropException) {
                throw VcekCertificateError.MalformedExtension(
                    "extension $oid is not a valid INTEGER",
                    e,
                )
            }
        if (value < 0 || value > Int.MAX_VALUE) {
            throw VcekCertificateError.MalformedExtension(
                "extension $oid INTEGER does not fit in Int (got $value)",
            )
        }
        return value.toInt()
    }
}

internal actual class VcekCertificate internal constructor(
    raw: RTXCertificate,
) : X509Certificate(raw) {
    actual fun verifyEcdsaP384Sha384Signature(
        message: ByteArray,
        rBigEndian: ByteArray,
        sBigEndian: ByteArray,
    ): Boolean =
        raw.verifyEcdsaP384Sha384WithMessage(
            message.toNSData(),
            rawSignature = (rBigEndian + sBigEndian).toNSData(),
        )
}

internal actual fun parseX509CertificateChain(pem: String): List<X509Certificate> {
    val parsed =
        try {
            interop { error -> RTXCertificate.parseChainWithPem(pem, error = error) }
        } catch (e: AppleInteropException) {
            throw VcekCertificateError.MalformedPem(
                e.message ?: "failed to parse PEM certificate",
                e,
            )
        }
    val certificates = parsed.filterIsInstance<RTXCertificate>().map(::X509Certificate)
    if (certificates.isEmpty()) {
        throw VcekCertificateError.MalformedPem("no PEM certificate found")
    }
    return certificates
}

internal actual fun X509Certificate.asVcek(): VcekCertificate = VcekCertificate(raw)

private fun NSDate.toInstant(): Instant = Instant.fromEpochMilliseconds((timeIntervalSince1970 * 1000.0).roundToLong())
