package tech.yaxi.routex.settlement.internal

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.Signature
import java.security.cert.CertificateException
import java.security.cert.X509Certificate as JavaX509Certificate
import javax.security.auth.x500.X500Principal
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.DERSequence

internal actual open class X509Certificate internal constructor(
    internal val delegate: JavaX509Certificate,
) {
    actual val subjectName: String
        get() = delegate.subjectX500Principal.getName(X500Principal.RFC2253)

    actual val issuerName: String
        get() = delegate.issuerX500Principal.getName(X500Principal.RFC2253)

    actual fun verifySignatureBy(issuer: X509Certificate) {
        try {
            delegate.verify(issuer.delegate.publicKey, BouncyCastleProviderHolder.provider)
        } catch (e: Exception) {
            throw VcekCertificateError.SignatureInvalid(
                e.message ?: "certificate signature invalid",
                e,
            )
        }
    }

    actual fun extensionAsIa5String(oid: String): String? {
        val inner = readExtensionInner(oid) ?: return null
        return try {
            DERIA5String.getInstance(inner).string
        } catch (e: IllegalStateException) {
            throw VcekCertificateError.MalformedExtension(
                "extension $oid is not a valid IA5String",
                e,
            )
        } catch (e: ClassCastException) {
            throw VcekCertificateError.MalformedExtension(
                "extension $oid is not a valid IA5String",
                e,
            )
        }
    }

    actual fun extensionAsInteger(oid: String): Int? {
        val inner = readExtensionInner(oid) ?: return null
        val asn1 =
            try {
                ASN1Integer.getInstance(inner)
            } catch (e: IllegalArgumentException) {
                throw VcekCertificateError.MalformedExtension(
                    "extension $oid is not a valid INTEGER",
                    e,
                )
            }
        // `BigInteger.intValueExact()` would be cleaner but only landed on Android in
        // API 31; we target 24. `bitLength()` excludes the sign bit, so any non-negative
        // value fitting in 31 bits is safe to narrow.
        val value = asn1.value
        if (value.signum() < 0 || value.bitLength() > Int.SIZE_BITS - 1) {
            throw VcekCertificateError.MalformedExtension(
                "extension $oid INTEGER does not fit in Int (got $value)",
            )
        }
        return value.toInt()
    }

    /**
     * Pull the inner DER-encoded value of the X.509v3 extension named by [oid].
     * `JavaX509Certificate.getExtensionValue` always wraps the actual extension bytes
     * in an outer `OCTET STRING`; we strip that wrapper once and hand the inner bytes
     * to the typed accessors above.
     */
    private fun readExtensionInner(oid: String): ByteArray? {
        val wrapped = delegate.getExtensionValue(oid) ?: return null
        return ASN1InputStream(ByteArrayInputStream(wrapped)).use { stream ->
            (stream.readObject() as? ASN1OctetString)?.octets
                ?: throw VcekCertificateError.MalformedExtension(
                    "extension $oid value is not wrapped in an OCTET STRING",
                )
        }
    }
}

internal actual class VcekCertificate internal constructor(
    delegate: JavaX509Certificate,
) : X509Certificate(delegate) {
    actual fun verifyEcdsaP384Sha384Signature(
        message: ByteArray,
        rBigEndian: ByteArray,
        sBigEndian: ByteArray,
    ): Boolean {
        // SEV-SNP attestation reports carry the ECDSA signature as raw 48-byte r and s
        // scalars; the JCA verifier expects DER `SEQUENCE { INTEGER r, INTEGER s }`.
        val der =
            DERSequence(
                ASN1EncodableVector().apply {
                    add(ASN1Integer(BigInteger(1, rBigEndian)))
                    add(ASN1Integer(BigInteger(1, sBigEndian)))
                },
            ).encoded
        val signer = Signature.getInstance("SHA384withECDSA", BouncyCastleProviderHolder.provider)
        signer.initVerify(delegate.publicKey)
        signer.update(message)
        return signer.verify(der)
    }
}

internal actual fun parseX509CertificateChain(pem: String): List<X509Certificate> {
    val regex = Regex("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----")
    val matches = regex.findAll(pem).toList()
    if (matches.isEmpty()) {
        throw VcekCertificateError.MalformedPem("no PEM certificate found")
    }
    return matches.map { match ->
        try {
            val cert =
                BouncyCastleProviderHolder.certificateFactory.generateCertificate(
                    ByteArrayInputStream(match.value.encodeToByteArray()),
                ) as JavaX509Certificate
            X509Certificate(cert)
        } catch (e: CertificateException) {
            throw VcekCertificateError.MalformedPem("failed to parse PEM certificate: ${e.message}", e)
        }
    }
}

internal actual fun X509Certificate.asVcek(): VcekCertificate = VcekCertificate(delegate)
