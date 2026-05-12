package tech.yaxi.routex.settlement

internal sealed class ReportVerificationError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class UnsupportedSignatureAlgorithm(
        val code: Int,
    ) : ReportVerificationError("unsupported attestation signature algorithm: $code")

    class UnexpectedSignatureBits(
        message: String = "unexpected non-zero bits in ECDSA scalar slot",
    ) : ReportVerificationError(message)

    class BadSignature(
        message: String = "attestation report signature is invalid",
        cause: Throwable? = null,
    ) : ReportVerificationError(message, cause)

    class AliasCheckMissing : ReportVerificationError("alias check was not completed by the platform")

    class DebugEnabled : ReportVerificationError("debug mode is enabled in guest policy")

    class VmplTooHigh(
        val vmpl: Long,
    ) : ReportVerificationError("unexpected VMPL $vmpl (must be <= 3)")

    class SigningKeyNotVcek(
        val kind: SigningKeyKind,
    ) : ReportVerificationError("report is not signed by a VCEK (signingKey=$kind)")

    class UnknownCpuFamily(
        message: String,
    ) : ReportVerificationError(message)

    class TcbMismatch(
        val field: String,
        val expected: Int,
        val actual: Int,
    ) : ReportVerificationError(
            "reported TCB does not match VCEK: $field expected=$expected actual=$actual",
        )

    class CommittedVersionBelow(
        val required: FirmwareVersion,
        val actual: FirmwareVersion,
    ) : ReportVerificationError(
            "committed firmware version too low: required=$required actual=$actual",
        )

    class CommittedSnpBelow(
        val required: Int,
        val actual: Int,
    ) : ReportVerificationError("committed SNP TCB too low: required=$required actual=$actual")

    class MicrocodeBelow(
        val required: Int,
        val actual: Int,
    ) : ReportVerificationError("committed microcode version too low: required=$required actual=$actual")

    /**
     * One of the [`AttestationReport.currentMitVector`][AttestationReport.currentMitVector]
     * / [`AttestationReport.launchMitVector`][AttestationReport.launchMitVector] fields
     * either is absent (pre-v5 report under a bulletin that requires verified
     * mitigations) or has at least one required bit cleared.
     *
     * @property field `"current"` or `"launch"`.
     * @property required Required bitmask (all listed bits must be set).
     * @property actual Observed value, or `null` when the report doesn't carry the field.
     */
    class MitVectorBelow(
        val field: String,
        val required: Long,
        val actual: Long?,
    ) : ReportVerificationError(
            buildString {
                append("$field mitigation vector below required bits: ")
                append(actual?.let { "actual=0x${it.toULong().toString(16)} " } ?: "actual=<absent> ")
                append("required=0x${required.toULong().toString(16)}")
            },
        )
}

internal object ReportVerifier {
    fun verify(
        report: AttestationReport,
        chain: VcekChainVerifyResult,
        requirements: Requirements,
    ) {
        when (requirements) {
            is Requirements.Pinned -> verifyPinned(report, chain, requirements)
            is Requirements.Permissive -> verifyPermissive(report, chain)
        }
    }

    private fun verifyPermissive(
        report: AttestationReport,
        chain: VcekChainVerifyResult,
    ) {
        if (report.signatureAlgo != SignatureAlgorithm.ECDSA_P384_SHA384) {
            throw ReportVerificationError.UnsupportedSignatureAlgorithm(report.signatureAlgo.code)
        }
        verifySignatureZeroPadding(report)
        verifyReportSignature(report, chain)
        verifyTcbAgainstVcek(report.reportedTcb, chain.vcekTcb)
    }

    fun verifyBytes(
        reportBytes: ByteArray,
        vcekChainPem: String,
        rootStore: VcekRootStore,
        requirements: Requirements,
    ): Pair<AttestationReport, VcekChainVerifyResult> {
        val chain = VcekChainOps.verify(vcekChainPem, rootStore)
        val report = AttestationReportParser.parse(reportBytes)
        verify(report, chain, requirements)
        return report to chain
    }

    private fun verifyPinned(
        report: AttestationReport,
        chain: VcekChainVerifyResult,
        requirements: Requirements.Pinned,
    ) {
        if (report.signatureAlgo != SignatureAlgorithm.ECDSA_P384_SHA384) {
            throw ReportVerificationError.UnsupportedSignatureAlgorithm(report.signatureAlgo.code)
        }
        verifySignatureZeroPadding(report)
        verifyReportSignature(report, chain)

        if (!report.platformInfo.aliasCheckComplete) {
            throw ReportVerificationError.AliasCheckMissing()
        }
        if (report.policy.debugAllowed) {
            throw ReportVerificationError.DebugEnabled()
        }
        if (report.vmpl > 3L) {
            throw ReportVerificationError.VmplTooHigh(report.vmpl)
        }
        if (report.keyInfo.signingKeyKind != SigningKeyKind.VCEK) {
            throw ReportVerificationError.SigningKeyNotVcek(report.keyInfo.signingKeyKind)
        }

        val perProduct = requirements.forFamily(chain.family)
        if (report.committedVersion.packed() < perProduct.minCommittedVersion.packed()) {
            throw ReportVerificationError.CommittedVersionBelow(
                perProduct.minCommittedVersion,
                report.committedVersion,
            )
        }
        if (report.committedTcb.snp < perProduct.minCommittedTcbSnp) {
            throw ReportVerificationError.CommittedSnpBelow(
                perProduct.minCommittedTcbSnp,
                report.committedTcb.snp,
            )
        }
        verifyMitVector("current", perProduct.minMitVector, report.currentMitVector)
        verifyMitVector("launch", perProduct.minMitVector, report.launchMitVector)

        val cpuModel = report.cpuidModId
        val cpuStep = report.cpuidStep
        val minMicrocode =
            if (cpuModel != null && cpuStep != null) {
                requirements.minMicrocode(chain.family, cpuModel, cpuStep)
                    ?: throw ReportVerificationError.UnknownCpuFamily(
                        "no microcode requirement for family=${chain.family} " +
                            "model=$cpuModel step=$cpuStep",
                    )
            } else {
                // Pre-v3 attestation reports don't expose `cpuid_mod_id` / `cpuid_step`
                // (per AMD doc 56860 §7.3, those fields are in the v3+ layout extension
                // that fills the v2-reserved bytes). Fall back to the per-family floor
                // [PerProduct.fallbackMicrocode].
                //
                // Security property: `fallbackMicrocode` is a floor on `committed_tcb
                // .microcode`; an attacker cannot bypass a stricter (model, stepping)
                // check by claiming `version < 3`, because each family's fallback value
                // is set to the strictest entry in that family's per-stepping table
                // (Milan 0xDE, Genoa 0x56, Turin 0x50 / 0x51 under SB-3023). Picking
                // the stripped-down pre-v3 path therefore lands the report in a floor
                // that is at least as strict as the per-(model, stepping) value the
                // attacker would otherwise pick. The other gates (alias check, debug,
                // vmpl, signing key, committed firmware/SNP version, TCB-vs-VCEK match,
                // mitigation vectors) are not version-gated and apply equally to pre-v3
                // reports, and the mitigation-vector check rejects pre-v5 reports
                // outright when the bulletin requires non-zero bits.
                perProduct.fallbackMicrocode
            }
        if (report.committedTcb.microcode < minMicrocode) {
            throw ReportVerificationError.MicrocodeBelow(minMicrocode, report.committedTcb.microcode)
        }

        verifyTcbAgainstVcek(report.reportedTcb, chain.vcekTcb)
    }

    /**
     * Enforce that every bit in [required] is set in [actual]. A `required` mask of `0`
     * disables the check entirely.
     *
     * Per AMD doc 56860 §3.5.2 the mitigation-vector fields land at offsets 0x1F8 /
     * 0x200 starting in v5 of the attestation-report layout. Pre-v5 reports therefore
     * arrive here with `actual == null`; under a bulletin that requires non-zero bits
     * (e.g. SB-3023), absence is a verification failure: the firmware version alone
     * doesn't prove `SNP_VERIFY_MITIGATION` has been called, only the signed
     * `LAUNCH_MIT_VECTOR` / `CURRENT_MIT_VECTOR` does.
     */
    private fun verifyMitVector(
        field: String,
        required: Long,
        actual: Long?,
    ) {
        if (required == 0L) return
        if (actual == null) {
            throw ReportVerificationError.MitVectorBelow(field, required, actual = null)
        }
        if (actual and required != required) {
            throw ReportVerificationError.MitVectorBelow(field, required, actual)
        }
    }

    private fun verifySignatureZeroPadding(report: AttestationReport) {
        val raw = report.signature.raw
        if (report.signature.algorithm != SignatureAlgorithm.ECDSA_P384_SHA384) return
        val rSlot = raw.copyOfRange(0, 72)
        val sSlot = raw.copyOfRange(72, 144)
        for (i in 48 until 72) {
            if (rSlot[i].toInt() != 0 || sSlot[i].toInt() != 0) {
                throw ReportVerificationError.UnexpectedSignatureBits()
            }
        }
    }

    private fun verifyReportSignature(
        report: AttestationReport,
        chain: VcekChainVerifyResult,
    ) {
        val tbs = report.raw.copyOfRange(0, AttestationReport.SIGNATURE_TBS_LENGTH)
        val rBigEndian = report.signature.rLittleEndian.reversedArray()
        val sBigEndian = report.signature.sLittleEndian.reversedArray()
        val valid =
            try {
                chain.leaf.verifyEcdsaP384Sha384Signature(tbs, rBigEndian, sBigEndian)
            } catch (e: Exception) {
                throw ReportVerificationError.BadSignature(cause = e)
            }
        if (!valid) {
            throw ReportVerificationError.BadSignature()
        }
    }

    private fun verifyTcbAgainstVcek(
        reported: TcbVersion,
        vcek: TcbVersion,
    ) {
        if (reported.bootloader != vcek.bootloader) {
            throw ReportVerificationError.TcbMismatch("bootloader", vcek.bootloader, reported.bootloader)
        }
        if (reported.tee != vcek.tee) {
            throw ReportVerificationError.TcbMismatch("tee", vcek.tee, reported.tee)
        }
        if (reported.snp != vcek.snp) {
            throw ReportVerificationError.TcbMismatch("snp", vcek.snp, reported.snp)
        }
        if (reported.microcode != vcek.microcode) {
            throw ReportVerificationError.TcbMismatch("microcode", vcek.microcode, reported.microcode)
        }
        if (reported.fmc != vcek.fmc) {
            throw ReportVerificationError.TcbMismatch("fmc", vcek.fmc, reported.fmc)
        }
    }
}
