package tech.yaxi.routex.settlement

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERIA5String
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * A self-signed root supplied in the chain must not anchor against the pinned AMD
 * roots, even when every certificate in the chain is internally well formed.
 *
 * The forged leaf carries the AMD product-name and TCB extensions a genuine VCEK
 * has, so it passes every downstream check; the chain's only defect is that its
 * self-signed root is not a pinned AMD root. The chain must be rejected at the
 * trust-anchor step. Without that anchoring, `VcekChainOps.verify` accepts the
 * chain outright and the attestation is forged.
 */
class VcekChainTrustAnchorTest {
    @Test
    fun rejectsWellFormedChainUnderUntrustedSelfSignedRoot() =
        runTest {
            val rootName = X500Name("CN=Rogue Root")
            val rootKeys = rsaKeyPair()
            val root =
                certificate(
                    subject = rootName,
                    issuer = rootName,
                    subjectKey = rootKeys.public,
                    signingKey = rootKeys.private,
                )

            val leafKeys = rsaKeyPair()
            val leaf =
                certificate(
                    subject = X500Name("CN=Rogue Leaf"),
                    issuer = rootName,
                    subjectKey = leafKeys.public,
                    signingKey = rootKeys.private,
                    extensions = amdVcekExtensions(),
                )

            assertFailsWith<VcekChainError.ChainBroken> {
                VcekChainOps.verify(pem(leaf) + pem(root), VcekRootStore.Amd)
            }
        }

    /** Product name and the four required TCB SPLs, as a genuine Milan VCEK carries. */
    private fun amdVcekExtensions(): List<Pair<String, ASN1Encodable>> =
        listOf(
            "1.3.6.1.4.1.3704.1.2" to DERIA5String("Milan-B0"),
            "1.3.6.1.4.1.3704.1.3.1" to ASN1Integer(0),
            "1.3.6.1.4.1.3704.1.3.2" to ASN1Integer(0),
            "1.3.6.1.4.1.3704.1.3.3" to ASN1Integer(0),
            "1.3.6.1.4.1.3704.1.3.8" to ASN1Integer(0),
        )

    private fun rsaKeyPair() = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun certificate(
        subject: X500Name,
        issuer: X500Name,
        subjectKey: PublicKey,
        signingKey: PrivateKey,
        extensions: List<Pair<String, ASN1Encodable>> = emptyList(),
    ): X509Certificate {
        val builder =
            JcaX509v3CertificateBuilder(issuer, BigInteger.ONE, Date(0), Date(4_102_444_800_000L), subject, subjectKey)
        for ((oid, value) in extensions) {
            builder.addExtension(ASN1ObjectIdentifier(oid), false, value)
        }
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(signingKey)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
    }

    private fun pem(cert: X509Certificate): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(cert.encoded)
        return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n"
    }
}
