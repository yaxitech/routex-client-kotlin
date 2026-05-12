package tech.yaxi.routex.settlement

import tech.yaxi.routex.settlement.internal.VcekCertificate
import tech.yaxi.routex.settlement.internal.VcekCertificateError
import tech.yaxi.routex.settlement.internal.X509Certificate
import tech.yaxi.routex.settlement.internal.asVcek
import tech.yaxi.routex.settlement.internal.parseX509CertificateChain
import tech.yaxi.routex.settlement.internal.productName
import tech.yaxi.routex.settlement.internal.tcb

/**
 * Source of the trust anchor (and optional intermediate) used when validating a VCEK
 * certificate chain. [Amd] uses the AMD-shipped KDS root that's baked into the
 * library; [Pem] takes caller-supplied PEM strings, typical use is unit-test
 * fixtures that pin a deterministic chain.
 */
internal sealed class VcekRootStore {
    /** AMD-shipped KDS root, baked into [AmdRootStore]. */
    object Amd : VcekRootStore()

    /**
     * Caller-supplied PEM trust material, anchor required, intermediate optional.
     * Both inputs are ASCII-armoured X.509 certificates as `-----BEGIN CERTIFICATE-----`
     * blocks.
     */
    class Pem(
        val anchorPem: String,
        val intermediatePem: String? = null,
    ) : VcekRootStore()
}

internal sealed class VcekChainError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class MalformedPem(
        message: String,
        cause: Throwable? = null,
    ) : VcekChainError(message, cause)

    class ChainBroken(
        message: String,
        cause: Throwable? = null,
    ) : VcekChainError(message, cause)

    class MissingRoot(
        message: String,
        cause: Throwable? = null,
    ) : VcekChainError(message, cause)

    class UnexpectedProduct(
        val productName: String,
    ) : VcekChainError("unexpected VCEK product name: $productName")

    class InvalidExtensionValue(
        message: String,
        cause: Throwable? = null,
    ) : VcekChainError(message, cause)
}

/**
 * Result of verifying a VCEK certificate chain.
 *
 * The leaf certificate is retained internally so the attestation-report verifier can
 * re-use it for the ECDSA P-384 signature check (the leaf VCEK's public key signs the
 * SEV-SNP report, per AMD doc 57230 §3 "Subject Public Key Info: ECDSA on curve P-384").
 */
internal class VcekChainVerifyResult internal constructor(
    val leafSubjectName: String,
    val family: CpuFamily,
    val productName: String,
    internal val leaf: VcekCertificate,
    val vcekTcb: TcbVersion,
)

internal object VcekChainOps {
    fun verify(
        vcekChainPem: String,
        rootStore: VcekRootStore,
    ): VcekChainVerifyResult {
        val parsed = parseChain(vcekChainPem)
        if (parsed.isEmpty()) {
            throw VcekChainError.MalformedPem("empty VCEK chain")
        }
        // The first cert in the supplied chain is the AMD VCEK leaf, re-interpret it
        // as a VcekCertificate so the AMD-specific extension accessors and the ECDSA
        // P-384 verify primitive light up. Intermediates / roots stay as plain
        // X509Certificate.
        val leaf = parsed[0].asVcek()
        val intermediates = parsed.drop(1)
        val roots =
            when (rootStore) {
                VcekRootStore.Amd -> AmdRootStore.certificates
                is VcekRootStore.Pem -> loadPemRoots(rootStore)
            }
        val path = buildPath(leaf, intermediates, roots)
        verifyPathSignatures(path)
        val productName = readVcek("product name") { leaf.productName }
        val tcb = readVcek("tcb extensions") { leaf.tcb }
        return VcekChainVerifyResult(
            leafSubjectName = leaf.subjectName,
            family = inferFamily(productName),
            productName = productName,
            leaf = leaf,
            vcekTcb =
                TcbVersion(
                    bootloader = tcb.bootloader,
                    tee = tcb.tee,
                    snp = tcb.snp,
                    microcode = tcb.microcode,
                    fmc = tcb.fmc ?: 0,
                    raw = 0L,
                ),
        )
    }

    internal fun parseChain(pem: String): List<X509Certificate> =
        try {
            parseX509CertificateChain(pem)
        } catch (e: VcekCertificateError) {
            throw VcekChainError.MalformedPem("failed to parse VCEK PEM chain: ${e.message}", e)
        }

    private fun loadPemRoots(store: VcekRootStore.Pem): List<X509Certificate> {
        val combined =
            buildString {
                append(store.anchorPem)
                if (store.intermediatePem != null) {
                    if (!endsWith("\n")) append('\n')
                    append(store.intermediatePem)
                }
            }
        return try {
            parseX509CertificateChain(combined)
        } catch (e: VcekCertificateError) {
            throw VcekChainError.MissingRoot("failed to parse PEM root bundle: ${e.message}", cause = e)
        }
    }

    private fun buildPath(
        leaf: X509Certificate,
        intermediates: List<X509Certificate>,
        roots: List<X509Certificate>,
    ): List<X509Certificate> {
        val all = intermediates + roots
        val path = mutableListOf(leaf)
        var current: X509Certificate = leaf
        while (true) {
            val issuerDn = current.issuerName
            val issuerCandidates = all.filter { it.subjectName == issuerDn && it !in path }
            if (issuerCandidates.isEmpty()) {
                throw VcekChainError.ChainBroken(
                    "no issuer found for subject=${current.subjectName}",
                )
            }
            val issuer = issuerCandidates.first()
            path += issuer
            if (issuer.subjectName == issuer.issuerName) {
                return path
            }
            current = issuer
        }
    }

    private fun verifyPathSignatures(path: List<X509Certificate>) {
        for (i in 0 until path.size - 1) {
            verifyOne(path[i], path[i + 1], "chain[$i]")
        }
        val root = path.last()
        verifyOne(root, root, "root")
    }

    private fun verifyOne(
        subject: X509Certificate,
        issuer: X509Certificate,
        label: String,
    ) {
        try {
            subject.verifySignatureBy(issuer)
        } catch (e: VcekCertificateError) {
            throw VcekChainError.ChainBroken(
                "signature verification failed for $label: ${e.message}",
                e,
            )
        }
    }

    private fun inferFamily(productName: String): CpuFamily {
        val base = productName.substringBefore('-').trim()
        return when (base) {
            "Milan" -> CpuFamily.MILAN
            "Genoa", "Siena", "Bergamo" -> CpuFamily.GENOA
            "Turin" -> CpuFamily.TURIN
            else -> throw VcekChainError.UnexpectedProduct(productName)
        }
    }

    private inline fun <T> readVcek(
        what: String,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (e: VcekCertificateError.MalformedExtension) {
            throw VcekChainError.InvalidExtensionValue("failed to decode $what: ${e.message}", e)
        }
}
