@file:JvmName("VcekCertificateCommon")

package tech.yaxi.routex.settlement.internal

import kotlin.jvm.JvmName
import kotlin.time.Instant

/**
 * Generic X.509v3 certificate with the surface the VCEK chain validator needs:
 * DER-encoded distinguished names, "this cert is signed by `issuer`" verification,
 * and typed reads of arbitrary X.509v3 extensions by dotted OID.
 *
 * Used as-is for the AMD root (ARK) and intermediate (ASK) certificates, which carry
 * no AMD-specific extensions. The leaf VCEK is modelled by the [VcekCertificate]
 * subclass, which layers AMD-specific typed accessors over the generic extension
 * readers below.
 *
 * Implementations decode the X.509 wire format with a platform-specific parser
 * (BouncyCastle on JVM/Android, `@peculiar/x509` on JS, routex-client-swift's
 * `RoutexCrypto` on Apple); commonMain treats the distinguished names as opaque
 * DER bytes and never embeds a hand-rolled ASN.1 decoder.
 */
internal expect open class X509Certificate {
    /** Distinguished name of the certificate subject. */
    val subjectDn: DistinguishedName

    /** Distinguished name of the certificate issuer. */
    val issuerDn: DistinguishedName

    /** The subject's `commonName` (OID 2.5.4.3), or `null` when absent. */
    val subjectCommonName: String?

    /** Start of the validity window (`notBefore`), inclusive. */
    val validFrom: Instant

    /** End of the validity window (`notAfter`), inclusive. */
    val validUntil: Instant

    /** Whether Basic Constraints marks this certificate as a CA (`cA = true`). */
    val isCertificateAuthority: Boolean

    /**
     * Basic Constraints `pathLenConstraint`: the maximum number of intermediate CA
     * certificates that may follow this one in a chain. `null` when unconstrained or
     * absent (only meaningful when [isCertificateAuthority]).
     */
    val maxIntermediateCertificates: Int?

    /**
     * Whether Key Usage permits certificate signing (`keyCertSign`). `true` when the
     * Key Usage extension is absent, which imposes no restriction.
     */
    val allowsCertificateSigning: Boolean

    /**
     * Verify this certificate's signature was made by [issuer]'s public key. Throws
     * [VcekCertificateError.SignatureInvalid] on mismatch. The self-signed root passes
     * itself as the issuer.
     */
    suspend fun verifySignatureBy(issuer: X509Certificate)

    /**
     * Decode the value of the X.509v3 extension at [oid] as a DER `IA5String`. Returns
     * `null` when the extension is absent. Throws [VcekCertificateError.MalformedExtension]
     * if the extension is present but not a valid IA5String.
     */
    fun extensionAsIa5String(oid: String): String?

    /**
     * Decode the value of the X.509v3 extension at [oid] as a DER `INTEGER` and return
     * it as a non-negative `Int`. Returns `null` when the extension is absent. Throws
     * [VcekCertificateError.MalformedExtension] if the extension is present but not a
     * valid INTEGER, or if its value overflows `Int`.
     */
    fun extensionAsInteger(oid: String): Int?
}

/** DER-encoded X.501 distinguished name, compared by content. */
internal class DistinguishedName(
    val der: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is DistinguishedName && der.contentEquals(other.der)

    override fun hashCode(): Int = der.contentHashCode()
}

/**
 * Versioned Chip Endorsement Key, the leaf certificate in the AMD VCEK trust chain
 * defined by AMD doc 57230 rev. 1.00 ("Versioned Chip Endorsement Key (VCEK)
 * Certificate and KDS Interface Specification").
 *
 * Adds typed accessors for the AMD-specific X.509v3 extensions enumerated in §3.1
 * (Tables 10 / 11), see [productName] and [tcb], plus ECDSA P-384 + SHA-384
 * signature verification using the cert's own public key (per spec §3, "Subject
 * Public Key Info: ECDSA on curve P-384"). The verify primitive is used by the
 * SEV-SNP attestation-report verifier; no other public key is ever fed to it.
 */
internal expect class VcekCertificate : X509Certificate {
    /**
     * Verify an ECDSA P-384 + SHA-384 signature over [message] using THIS certificate's
     * own public key.
     *
     * @param rBigEndian 48-byte big-endian r scalar of the ECDSA signature.
     * @param sBigEndian 48-byte big-endian s scalar of the ECDSA signature.
     */
    fun verifyEcdsaP384Sha384Signature(
        message: ByteArray,
        rBigEndian: ByteArray,
        sBigEndian: ByteArray,
    ): Boolean
}

/**
 * SEV-SNP TCB component Security Patch Levels (SPLs) decoded from the AMD-specific
 * extensions on a [VcekCertificate]. The OIDs and field semantics are pinned by
 * AMD doc 57230 rev. 1.00 §3.1 (Tables 10 + 11):
 *
 *  - `bootloader`, OID `…1.3.1` (`blSPL`)
 *  - `tee`       , OID `…1.3.2` (`teeSPL`)
 *  - `snp`       , OID `…1.3.3` (`snpSPL`)
 *  - `microcode` , OID `…1.3.8` (`ucodeSPL`)
 *  - `fmc`       , OID `…1.3.9` (`fmcSPL`); Family 1Ah (Turin) only, `null` on Milan/Genoa.
 */
internal data class VcekTcb(
    val bootloader: Int,
    val tee: Int,
    val snp: Int,
    val microcode: Int,
    val fmc: Int?,
)

/**
 * AMD product identifier including silicon stepping, `"Milan-B0"`, `"Genoa-A0"`,
 * `"Turin-A0"`, etc. AMD doc 57230 §3.1 OID `1.3.6.1.4.1.3704.1.2`, ASN.1 IA5String.
 * The spec marks the extension as required, so this property throws
 * [VcekCertificateError.MalformedExtension] on absence.
 */
internal val VcekCertificate.productName: String
    get() = extensionAsIa5String(VcekOids.PRODUCT_NAME) ?: throwMissing(VcekOids.PRODUCT_NAME)

/**
 * SEV-SNP TCB component Security Patch Levels carried as separate AMD-specific
 * extensions (OID arc `1.3.6.1.4.1.3704.1.3.x`). See [VcekTcb] for the per-field
 * mapping. Required SPLs throw [VcekCertificateError.MalformedExtension] on absence;
 * the optional `fmcSPL` (Family 1Ah only) reports `null`.
 */
internal val VcekCertificate.tcb: VcekTcb
    get() =
        VcekTcb(
            bootloader = requireInt(VcekOids.BL_SPL),
            tee = requireInt(VcekOids.TEE_SPL),
            snp = requireInt(VcekOids.SNP_SPL),
            microcode = requireInt(VcekOids.UCODE_SPL),
            fmc = extensionAsInteger(VcekOids.FMC_SPL),
        )

private fun VcekCertificate.requireInt(oid: String): Int = extensionAsInteger(oid) ?: throwMissing(oid)

private fun throwMissing(oid: String): Nothing =
    throw VcekCertificateError.MalformedExtension("required extension $oid is not present")

/**
 * VCEK extension OIDs from AMD doc 57230 rev. 1.00 §3.1 (Tables 10 + 11).
 *
 * Implementation detail of the typed accessors above; not exported. Per-actual code
 * never references these, actuals only see the dotted-OID strings the typed
 * accessors pass through to [X509Certificate.extensionAsIa5String] /
 * [X509Certificate.extensionAsInteger].
 */
private object VcekOids {
    const val PRODUCT_NAME: String = "1.3.6.1.4.1.3704.1.2"
    const val BL_SPL: String = "1.3.6.1.4.1.3704.1.3.1"
    const val TEE_SPL: String = "1.3.6.1.4.1.3704.1.3.2"
    const val SNP_SPL: String = "1.3.6.1.4.1.3704.1.3.3"
    const val UCODE_SPL: String = "1.3.6.1.4.1.3704.1.3.8"
    const val FMC_SPL: String = "1.3.6.1.4.1.3704.1.3.9"
}

/**
 * Parse a PEM bundle into a list of [X509Certificate]s in source order. Throws
 * [VcekCertificateError.MalformedPem] when the input does not contain a valid
 * `BEGIN CERTIFICATE`/`END CERTIFICATE` block.
 */
internal expect fun parseX509CertificateChain(pem: String): List<X509Certificate>

/**
 * Re-interpret a parsed [X509Certificate] as the AMD VCEK leaf. Cheap, the underlying
 * platform-cert handle is shared with the [X509Certificate] receiver; this just unlocks
 * the VCEK-specific accessors.
 */
internal expect fun X509Certificate.asVcek(): VcekCertificate

/** Marker root for VcekCertificate I/O failures. Each subclass names a specific cause. */
internal sealed class VcekCertificateError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class MalformedPem(
        message: String,
        cause: Throwable? = null,
    ) : VcekCertificateError(message, cause)

    class SignatureInvalid(
        message: String,
        cause: Throwable? = null,
    ) : VcekCertificateError(message, cause)

    class MalformedExtension(
        message: String,
        cause: Throwable? = null,
    ) : VcekCertificateError(message, cause)
}
