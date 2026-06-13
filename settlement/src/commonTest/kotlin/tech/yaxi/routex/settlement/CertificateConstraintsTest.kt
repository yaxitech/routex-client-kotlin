package tech.yaxi.routex.settlement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The pinned AMD roots are real CA certificates; the standard-constraint
 * accessors must read their Basic Constraints, Key Usage, and Validity
 * faithfully. This pins the parsing the chain validator relies on, on every
 * platform actual.
 */
class CertificateConstraintsTest {
    private val roots = AmdRootStore.certificates
    private val arks = roots.filter { it.subjectName == it.issuerName }
    private val asks = roots.filter { it.subjectName != it.issuerName }

    @Test
    fun arksAreCasPermittedToSignWithNoPathLengthLimit() {
        assertTrue(arks.isNotEmpty())
        for (ark in arks) {
            assertTrue(ark.isCertificateAuthority)
            assertTrue(ark.allowsCertificateSigning)
            assertNull(ark.maxIntermediateCertificates)
        }
    }

    @Test
    fun asksAreCasLimitedToLeafIssuance() {
        assertTrue(asks.isNotEmpty())
        for (ask in asks) {
            assertTrue(ask.isCertificateAuthority)
            assertTrue(ask.allowsCertificateSigning)
            assertEquals(0, ask.maxIntermediateCertificates)
        }
    }

    @Test
    fun subjectCommonNamesParse() {
        assertTrue(arks.all { it.subjectCommonName?.startsWith("ARK-") == true })
        assertTrue(asks.all { it.subjectCommonName?.startsWith("SEV-") == true })
    }

    @Test
    fun validityWindowsParseAndTheRootsAreCurrentlyValid() {
        val now = Clock.System.now()
        for (cert in roots) {
            assertTrue(cert.validFrom < cert.validUntil)
            assertTrue(cert.validFrom <= now && now <= cert.validUntil)
        }
    }
}
