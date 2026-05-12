package tech.yaxi.routex.settlement

internal enum class CpuFamily {
    MILAN,
    GENOA,
    TURIN,
}

internal data class TcbVersion(
    val bootloader: Int,
    val tee: Int,
    val snp: Int,
    val microcode: Int,
    val fmc: Int,
    val raw: Long,
)

internal data class GuestPolicy(
    val abiMinor: Int,
    val abiMajor: Int,
    val smtAllowed: Boolean,
    val migrateMaAllowed: Boolean,
    val debugAllowed: Boolean,
    val singleSocketRequired: Boolean,
    val cxlAllowed: Boolean,
    val memAes256Xts: Boolean,
    val raplDis: Boolean,
    val ciphertextHiding: Boolean,
    val pageSwapDisabled: Boolean,
    val raw: Long,
)

internal data class PlatformInfo(
    val smtEnabled: Boolean,
    val tsmeEnabled: Boolean,
    val eccEnabled: Boolean,
    val raplDisabled: Boolean,
    val ciphertextHidingEnabled: Boolean,
    val aliasCheckComplete: Boolean,
    val tioEnabled: Boolean,
    val raw: Long,
)

internal enum class SigningKeyKind {
    VCEK,
    VLEK,
    NONE,
    OTHER,
}

internal data class KeyInfo(
    val authorKeyEn: Boolean,
    val maskChipKey: Boolean,
    val signingKey: Int,
    val signingKeyKind: SigningKeyKind,
    val raw: Int,
)

internal data class FirmwareVersion(
    val major: Int,
    val minor: Int,
    val build: Int,
) {
    fun packed(): Int = (major shl 16) or (minor shl 8) or build
}

internal enum class SignatureAlgorithm(
    val code: Int,
) {
    ECDSA_P384_SHA384(1),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromCode(code: Int): SignatureAlgorithm = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

internal data class ReportSignature(
    val algorithm: SignatureAlgorithm,
    val rLittleEndian: ByteArray,
    val sLittleEndian: ByteArray,
    val raw: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReportSignature) return false
        return algorithm == other.algorithm &&
            rLittleEndian.contentEquals(other.rLittleEndian) &&
            sLittleEndian.contentEquals(other.sLittleEndian) &&
            raw.contentEquals(other.raw)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + rLittleEndian.contentHashCode()
        result = 31 * result + sLittleEndian.contentHashCode()
        result = 31 * result + raw.contentHashCode()
        return result
    }
}

internal class AttestationReport internal constructor(
    val version: Long,
    val guestSvn: Long,
    val policy: GuestPolicy,
    val familyId: ByteArray,
    val imageId: ByteArray,
    val vmpl: Long,
    val signatureAlgo: SignatureAlgorithm,
    val currentTcb: TcbVersion,
    val platformInfo: PlatformInfo,
    val keyInfo: KeyInfo,
    val reportData: ByteArray,
    val measurement: ByteArray,
    val hostData: ByteArray,
    val idKeyDigest: ByteArray,
    val authorKeyDigest: ByteArray,
    val reportId: ByteArray,
    val reportIdMa: ByteArray,
    val reportedTcb: TcbVersion,
    val cpuidFamId: Int?,
    val cpuidModId: Int?,
    val cpuidStep: Int?,
    val chipId: ByteArray,
    val committedTcb: TcbVersion,
    val currentVersion: FirmwareVersion,
    val committedVersion: FirmwareVersion,
    val launchTcb: TcbVersion,
    val launchMitVector: Long?,
    val currentMitVector: Long?,
    val signature: ReportSignature,
    val raw: ByteArray,
) {
    companion object {
        const val REPORT_LENGTH: Int = 1184
        const val SIGNATURE_OFFSET: Int = 0x2A0
        const val SIGNATURE_TBS_LENGTH: Int = SIGNATURE_OFFSET
    }
}
