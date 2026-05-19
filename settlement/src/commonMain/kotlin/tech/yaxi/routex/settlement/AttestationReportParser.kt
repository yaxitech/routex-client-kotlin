package tech.yaxi.routex.settlement

internal object AttestationReportParser {
    private const val OFF_VERSION = 0x00
    private const val OFF_GUEST_SVN = 0x04
    private const val OFF_POLICY = 0x08
    private const val OFF_FAMILY_ID = 0x10
    private const val OFF_IMAGE_ID = 0x20
    private const val OFF_VMPL = 0x30
    private const val OFF_SIGNATURE_ALGO = 0x34
    private const val OFF_CURRENT_TCB = 0x38
    private const val OFF_PLAT_INFO = 0x40
    private const val OFF_KEY_INFO = 0x48
    private const val OFF_REPORT_DATA = 0x50
    private const val OFF_MEASUREMENT = 0x90
    private const val OFF_HOST_DATA = 0xC0
    private const val OFF_ID_KEY_DIGEST = 0xE0
    private const val OFF_AUTHOR_KEY_DIGEST = 0x110
    private const val OFF_REPORT_ID = 0x140
    private const val OFF_REPORT_ID_MA = 0x160
    private const val OFF_REPORTED_TCB = 0x180
    private const val OFF_CPUID_FAM_ID = 0x188
    private const val OFF_CPUID_MOD_ID = 0x189
    private const val OFF_CPUID_STEP = 0x18A
    private const val OFF_CHIP_ID = 0x1A0
    private const val OFF_COMMITTED_TCB = 0x1E0
    private const val OFF_CURRENT_BUILD = 0x1E8
    private const val OFF_CURRENT_MINOR = 0x1E9
    private const val OFF_CURRENT_MAJOR = 0x1EA
    private const val OFF_COMMITTED_BUILD = 0x1EC
    private const val OFF_COMMITTED_MINOR = 0x1ED
    private const val OFF_COMMITTED_MAJOR = 0x1EE
    private const val OFF_LAUNCH_TCB = 0x1F0
    private const val OFF_LAUNCH_MIT_VECTOR = 0x1F8
    private const val OFF_CURRENT_MIT_VECTOR = 0x200
    private const val SIG_LEN = 0x200
    private const val ECDSA_SCALAR_LEN = 48
    private const val ECDSA_SCALAR_SLOT_LEN = 72

    private const val MILAN_GENOA_FAMILY: Int = 0x19

    fun parse(bytes: ByteArray): AttestationReport {
        if (bytes.size != AttestationReport.REPORT_LENGTH) {
            throw AttestationReportDecodeError.InvalidSize(bytes.size)
        }
        val version = readU32Le(bytes, OFF_VERSION)
        val versionI = version.toInt()

        val cpuidFamId = if (versionI >= 3) bytes[OFF_CPUID_FAM_ID].toInt() and 0xFF else null
        val cpuidModId = if (versionI >= 3) bytes[OFF_CPUID_MOD_ID].toInt() and 0xFF else null
        val cpuidStep = if (versionI >= 3) bytes[OFF_CPUID_STEP].toInt() and 0xFF else null

        val turinLike = isTurinLikeLayout(bytes, versionI)

        val guestSvn = readU32Le(bytes, OFF_GUEST_SVN)
        val policy = parsePolicy(readU64Le(bytes, OFF_POLICY))
        val familyId = bytes.copyOfRange(OFF_FAMILY_ID, OFF_FAMILY_ID + 16)
        val imageId = bytes.copyOfRange(OFF_IMAGE_ID, OFF_IMAGE_ID + 16)
        val vmpl = readU32Le(bytes, OFF_VMPL)
        val signatureAlgo = SignatureAlgorithm.fromCode(readU32Le(bytes, OFF_SIGNATURE_ALGO).toInt())

        val currentTcb = parseTcb(bytes, OFF_CURRENT_TCB, turinLike)
        val reportedTcb = parseTcb(bytes, OFF_REPORTED_TCB, turinLike)
        val committedTcb = parseTcb(bytes, OFF_COMMITTED_TCB, turinLike)
        val launchTcb = parseTcb(bytes, OFF_LAUNCH_TCB, turinLike)

        val platInfo = parsePlatformInfo(readU64Le(bytes, OFF_PLAT_INFO))
        val keyInfo = parseKeyInfo(readU32Le(bytes, OFF_KEY_INFO).toInt())

        val reportData = bytes.copyOfRange(OFF_REPORT_DATA, OFF_REPORT_DATA + 64)
        val measurement = bytes.copyOfRange(OFF_MEASUREMENT, OFF_MEASUREMENT + 48)
        val hostData = bytes.copyOfRange(OFF_HOST_DATA, OFF_HOST_DATA + 32)
        val idKeyDigest = bytes.copyOfRange(OFF_ID_KEY_DIGEST, OFF_ID_KEY_DIGEST + 48)
        val authorKeyDigest = bytes.copyOfRange(OFF_AUTHOR_KEY_DIGEST, OFF_AUTHOR_KEY_DIGEST + 48)
        val reportId = bytes.copyOfRange(OFF_REPORT_ID, OFF_REPORT_ID + 32)
        val reportIdMa = bytes.copyOfRange(OFF_REPORT_ID_MA, OFF_REPORT_ID_MA + 32)
        val chipId = bytes.copyOfRange(OFF_CHIP_ID, OFF_CHIP_ID + 64)

        val currentVersion =
            FirmwareVersion(
                major = bytes[OFF_CURRENT_MAJOR].toInt() and 0xFF,
                minor = bytes[OFF_CURRENT_MINOR].toInt() and 0xFF,
                build = bytes[OFF_CURRENT_BUILD].toInt() and 0xFF,
            )
        val committedVersion =
            FirmwareVersion(
                major = bytes[OFF_COMMITTED_MAJOR].toInt() and 0xFF,
                minor = bytes[OFF_COMMITTED_MINOR].toInt() and 0xFF,
                build = bytes[OFF_COMMITTED_BUILD].toInt() and 0xFF,
            )

        val launchMitVector = if (versionI >= 5) readU64Le(bytes, OFF_LAUNCH_MIT_VECTOR) else null
        val currentMitVector = if (versionI >= 5) readU64Le(bytes, OFF_CURRENT_MIT_VECTOR) else null

        val signature = parseSignature(bytes, signatureAlgo)

        return AttestationReport(
            version = version,
            guestSvn = guestSvn,
            policy = policy,
            familyId = familyId,
            imageId = imageId,
            vmpl = vmpl,
            signatureAlgo = signatureAlgo,
            currentTcb = currentTcb,
            platformInfo = platInfo,
            keyInfo = keyInfo,
            reportData = reportData,
            measurement = measurement,
            hostData = hostData,
            idKeyDigest = idKeyDigest,
            authorKeyDigest = authorKeyDigest,
            reportId = reportId,
            reportIdMa = reportIdMa,
            reportedTcb = reportedTcb,
            cpuidFamId = cpuidFamId,
            cpuidModId = cpuidModId,
            cpuidStep = cpuidStep,
            chipId = chipId,
            committedTcb = committedTcb,
            currentVersion = currentVersion,
            committedVersion = committedVersion,
            launchTcb = launchTcb,
            launchMitVector = launchMitVector,
            currentMitVector = currentMitVector,
            signature = signature,
            raw = bytes.copyOf(),
        )
    }

    // Milan/Genoa share family 0x19 and the legacy TCB_VERSION layout.
    // Turin/Venice (and anything newer) use the Turin layout. For v<3 the
    // cpuid fields are absent, so we fall back to a CHIP_ID heuristic.
    private fun isTurinLikeLayout(
        bytes: ByteArray,
        version: Int,
    ): Boolean {
        if (version >= 3) {
            return (bytes[OFF_CPUID_FAM_ID].toInt() and 0xFF) != MILAN_GENOA_FAMILY
        }
        val chipIdRange = OFF_CHIP_ID until OFF_CHIP_ID + 64
        if (chipIdRange.all { bytes[it].toInt() and 0xFF == 0 }) {
            throw AttestationReportDecodeError.MaskedChipIdInLegacyReport
        }
        return (OFF_CHIP_ID + 8 until OFF_CHIP_ID + 64).all { bytes[it].toInt() and 0xFF == 0 }
    }

    private fun parseTcb(
        bytes: ByteArray,
        offset: Int,
        turinLike: Boolean,
    ): TcbVersion {
        val b = IntArray(8) { bytes[offset + it].toInt() and 0xFF }
        val raw = readU64Le(bytes, offset)
        return if (turinLike) {
            TcbVersion(
                fmc = b[0],
                bootloader = b[1],
                tee = b[2],
                snp = b[3],
                microcode = b[7],
                raw = raw,
            )
        } else {
            TcbVersion(
                bootloader = b[0],
                tee = b[1],
                snp = b[6],
                microcode = b[7],
                fmc = 0,
                raw = raw,
            )
        }
    }

    private fun parsePolicy(raw: Long): GuestPolicy =
        GuestPolicy(
            abiMinor = (raw and 0xFF).toInt(),
            abiMajor = ((raw ushr 8) and 0xFF).toInt(),
            smtAllowed = bit(raw, 16),
            migrateMaAllowed = bit(raw, 18),
            debugAllowed = bit(raw, 19),
            singleSocketRequired = bit(raw, 20),
            cxlAllowed = bit(raw, 21),
            memAes256Xts = bit(raw, 22),
            raplDis = bit(raw, 23),
            ciphertextHiding = bit(raw, 24),
            pageSwapDisabled = bit(raw, 25),
            raw = raw,
        )

    private fun parsePlatformInfo(raw: Long): PlatformInfo =
        PlatformInfo(
            smtEnabled = bit(raw, 0),
            tsmeEnabled = bit(raw, 1),
            eccEnabled = bit(raw, 2),
            raplDisabled = bit(raw, 3),
            ciphertextHidingEnabled = bit(raw, 4),
            aliasCheckComplete = bit(raw, 5),
            tioEnabled = bit(raw, 7),
            raw = raw,
        )

    private fun parseKeyInfo(raw: Int): KeyInfo {
        val signingKey = (raw ushr 2) and 0x7
        val kind =
            when (signingKey) {
                0 -> SigningKeyKind.VCEK
                1 -> SigningKeyKind.VLEK
                7 -> SigningKeyKind.NONE
                else -> SigningKeyKind.OTHER
            }
        return KeyInfo(
            authorKeyEn = (raw and 1) != 0,
            maskChipKey = ((raw ushr 1) and 1) != 0,
            signingKey = signingKey,
            signingKeyKind = kind,
            raw = raw,
        )
    }

    private fun parseSignature(
        bytes: ByteArray,
        algo: SignatureAlgorithm,
    ): ReportSignature {
        val start = AttestationReport.SIGNATURE_OFFSET
        val raw = bytes.copyOfRange(start, start + SIG_LEN)
        return when (algo) {
            SignatureAlgorithm.ECDSA_P384_SHA384 -> {
                val rSlot = raw.copyOfRange(0, ECDSA_SCALAR_SLOT_LEN)
                val sSlot = raw.copyOfRange(ECDSA_SCALAR_SLOT_LEN, ECDSA_SCALAR_SLOT_LEN * 2)
                ReportSignature(
                    algorithm = algo,
                    rLittleEndian = rSlot.copyOfRange(0, ECDSA_SCALAR_LEN),
                    sLittleEndian = sSlot.copyOfRange(0, ECDSA_SCALAR_LEN),
                    raw = raw,
                )
            }
            else ->
                ReportSignature(
                    algorithm = algo,
                    rLittleEndian = ByteArray(0),
                    sLittleEndian = ByteArray(0),
                    raw = raw,
                )
        }
    }

    private fun bit(
        raw: Long,
        idx: Int,
    ): Boolean = ((raw ushr idx) and 1L) != 0L

    private fun readU32Le(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        val b0 = bytes[offset].toLong() and 0xFFL
        val b1 = bytes[offset + 1].toLong() and 0xFFL
        val b2 = bytes[offset + 2].toLong() and 0xFFL
        val b3 = bytes[offset + 3].toLong() and 0xFFL
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    private fun readU64Le(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = result or ((bytes[offset + i].toLong() and 0xFFL) shl (i * 8))
        }
        return result
    }
}
