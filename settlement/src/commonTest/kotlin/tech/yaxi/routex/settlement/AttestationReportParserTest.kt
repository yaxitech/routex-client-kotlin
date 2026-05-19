package tech.yaxi.routex.settlement

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
internal class AttestationReportParserTest {
    private companion object {
        private const val MILAN_V2_BASE64 =
            "AgAAAAAAAAAAAAMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAADAAAAAAAIcwEAAAAAAAAAAAAAAAAAAADUR7VdGXSRv+Fc8pj53pmGt6fEviRotPbi1Ttx18ZFgQsPLN/KAEBDO+Bj/BqCk/Dz+Nrnt5/ss9HNgr1qk+v9eh5cJmwBCNvJu5T6kmlRMglAkV0Kr7QkZL2ItXnqFY0+Gg3DmyxgvZW5xIDNgYQfAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACSs7R9WfCioQp0xWeIaKgCOM9ZPAGoLzz/uHjpBMKNW///////////////////////////////////////////AwAAAAAACHMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADUlVTscX9OWw/msUO88EBb164wRyft9GYD8qdq72o6vBXXrzjbdXA5Ap8O+s/QjiRDJIhHOMcrCC4vh6RNVB62AwAAAAAACHMENAEABDQBAAMAAAAAAAhzAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAYatPEapmGZdiXyM99CpK1URA7repbqY94XDLwpw3wAXLVAVIgex9K+5WmwLQf4JyAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAIJ1+ub6Rmh0Lrx1X/m6/6rvFO3eMbpd+QLFcqTG7bUTFq54wz9xzRstBrAg7kL9JAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        private const val OFF_VERSION = 0x000
        private const val OFF_CPUID_FAM_ID = 0x188
        private const val OFF_CHIP_ID = 0x1A0
        private const val OFF_RESERVED_TAIL = 0x208
        private const val OFF_SIGNATURE = 0x2A0
    }

    private fun fixture(): ByteArray = Base64.Default.decode(MILAN_V2_BASE64)

    private fun ByteArray.patch(
        offset: Int,
        bytes: ByteArray,
    ): ByteArray =
        apply {
            bytes.copyInto(this, destinationOffset = offset)
        }

    @Test
    fun ignoresReservedBytesWhenReportVersionIsUnknown() {
        val bytes =
            fixture()
                .patch(OFF_VERSION, byteArrayOf(42, 0, 0, 0))
                .patch(OFF_RESERVED_TAIL, ByteArray(OFF_SIGNATURE - OFF_RESERVED_TAIL) { 0xAB.toByte() })
        val report = AttestationReportParser.parse(bytes)
        assertEquals(42L, report.version)
        assertNotEquals(null, report.currentMitVector)
    }

    @Test
    fun usesTurinTcbLayoutWhenCpuidFamIdIsUnknown() {
        val bytes =
            fixture()
                .patch(OFF_VERSION, byteArrayOf(5, 0, 0, 0))
                .patch(OFF_CPUID_FAM_ID, byteArrayOf(0xFF.toByte()))
        val report = AttestationReportParser.parse(bytes)
        assertTrue(report.currentTcb.fmc != 0, "Expected Turin TCB layout (non-zero FMC)")
    }

    @Test
    fun rejectsLegacyReportWithFullyMaskedChipId() {
        val bytes = fixture().patch(OFF_CHIP_ID, ByteArray(64))
        assertFailsWith<AttestationReportDecodeError.MaskedChipIdInLegacyReport> {
            AttestationReportParser.parse(bytes)
        }
    }
}
