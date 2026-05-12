package tech.yaxi.routex.settlement

// SEV-SNP mitigation-vector bit positions (AMD doc 56860 §3.5.2). Lifted to
// file-level (rather than the companion object below) to dodge a sealed-class
// init-order trap: `Requirements.Sb3020.milan` reads these during the
// companion's initializer, but `Requirements.Companion` itself is mid-init at
// that moment, so a const-val living there isn't reliably visible yet on the
// JVM. Top-level vals are folded into a separate `RequirementsKt` synthetic
// class whose static init has no dependency on the sealed hierarchy.
private const val MIT_VECTOR_BIT_0: Long = 1L shl 0
private const val MIT_VECTOR_BIT_1: Long = 1L shl 1
private const val MIT_VECTOR_BIT_2: Long = 1L shl 2
private const val MIT_VECTOR_BIT_4: Long = 1L shl 4
private const val MIT_VECTOR_BIT_5: Long = 1L shl 5

/**
 * Per-CPU-family floor for an [AttestationReport] verification rule.
 *
 * @property minCommittedVersion Minimum committed firmware version the report must
 *   carry.
 * @property minCommittedTcbSnp Minimum committed SNP TCB level the report must carry.
 * @property fallbackMicrocode Conservative microcode floor used when the report is
 *   too old (version &lt; 3) to expose a CPU model/stepping pair, keyed by family
 *   only since per-stepping data is unavailable in that case.
 * @property minMitVector Bitmask of mitigation bits that **must** all be set in
 *   *both* [`AttestationReport.currentMitVector`][AttestationReport.currentMitVector]
 *   and [`AttestationReport.launchMitVector`][AttestationReport.launchMitVector]
 *   (AMD doc 56860 §3.5.2 `CURRENT_MIT_VECTOR` / `LAUNCH_MIT_VECTOR`). A v5+ report
 *   attesting that the listed bits are set is the firmware's signed proof that the
 *   host has called `SNP_VERIFY_MITIGATION` with those mitigations active and that
 *   the guest was launched with them already in place (not retroactively).
 *   Defaults to `0` (no bits required) and applies only when the bulletin requires
 *   verified mitigations.
 */
internal data class PerProduct(
    val minCommittedVersion: FirmwareVersion,
    val minCommittedTcbSnp: Int,
    val fallbackMicrocode: Int,
    val minMitVector: Long = 0L,
)

internal sealed class Requirements {
    /**
     * Test/debug-only opt-out: skips every bulletin-level check. Public release builds
     * never thread this in, the production paths bind one of the [Pinned] subclasses.
     */
    internal object Permissive : Requirements()

    /**
     * Common shape for any pinned AMD security-bulletin requirement set. New bulletins
     * extend this, see [Sb3020] / [Sb3023], and only differ in their per-product
     * floors and per-(model, stepping) microcode table.
     */
    internal sealed class Pinned : Requirements() {
        abstract val milan: PerProduct
        abstract val genoa: PerProduct
        abstract val turin: PerProduct

        fun forFamily(family: CpuFamily): PerProduct =
            when (family) {
                CpuFamily.MILAN -> milan
                CpuFamily.GENOA -> genoa
                CpuFamily.TURIN -> turin
            }

        /**
         * Minimum acceptable microcode level for the given CPU family / model / stepping.
         * Returns `null` when the (family, model, stepping) tuple is not on the
         * bulletin's approved list, callers must treat that as a verification failure.
         */
        abstract fun minMicrocode(
            family: CpuFamily,
            model: Int,
            stepping: Int,
        ): Int?
    }

    /**
     * Mitigations required for AMD-SB-3020.
     *
     * See https://www.amd.com/en/resources/product-security/bulletin/amd-sb-3020.html
     *
     * Sets the per-(model, stepping) microcode floor and requires bit 1 of the
     * mitigation vector on Milan/Genoa, bit 0 on Turin (per AMD doc 56860 §3.5.2).
     */
    internal object Sb3020 : Pinned() {
        override val milan: PerProduct =
            PerProduct(
                minCommittedVersion = FirmwareVersion(major = 0x1, minor = 0x37, build = 0x23),
                minCommittedTcbSnp = 0x1B,
                fallbackMicrocode = 0xDE,
                minMitVector = MIT_VECTOR_BIT_1,
            )
        override val genoa: PerProduct =
            PerProduct(
                minCommittedVersion = FirmwareVersion(major = 0x1, minor = 0x37, build = 0x31),
                minCommittedTcbSnp = 0x1B,
                fallbackMicrocode = 0x56,
                minMitVector = MIT_VECTOR_BIT_1,
            )
        override val turin: PerProduct =
            PerProduct(
                minCommittedVersion = FirmwareVersion(major = 0x1, minor = 0x37, build = 0x41),
                minCommittedTcbSnp = 0x04,
                fallbackMicrocode = 0x50,
                minMitVector = MIT_VECTOR_BIT_0,
            )

        override fun minMicrocode(
            family: CpuFamily,
            model: Int,
            stepping: Int,
        ): Int? =
            when (family) {
                CpuFamily.MILAN ->
                    when {
                        model == 1 && stepping == 1 -> 0xDE
                        model == 1 && stepping == 2 -> 0x45
                        else -> null
                    }
                CpuFamily.GENOA ->
                    when {
                        model == 0x11 && stepping == 1 -> 0x56
                        model == 0x11 && stepping == 2 -> 0x51
                        model == 0xA0 && stepping == 2 -> 0x1B
                        else -> null
                    }
                CpuFamily.TURIN ->
                    when {
                        model == 2 && stepping == 1 -> 0x50
                        model == 0x11 && stepping == 0 -> 0x4D
                        else -> null
                    }
            }
    }

    /**
     * Mitigations required for AMD-SB-3023.
     *
     * See https://www.amd.com/en/resources/product-security/bulletin/amd-sb-3023.html
     *
     * Strict superset of [Sb3020]:
     *  - Microcode floors are bumped on Milan-X (0x47), Turin Classic (0x51), Turin
     *    Dense (0x4E); other (model, stepping) pairs match SB-3020.
     *  - Genoa adds bit 0 to the required mitigation vector (mitigating
     *    CVE-2024-21953 on top of the SB-3020 set).
     *  - Turin adds bits 1, 2, 4, 5 (mitigating CVE-2025-29946/48/52, CVE-2025-0029,
     *    on top of the SB-3020 set).
     *  - Milan keeps the SB-3020 mitigation set (bit 1 only).
     */
    internal object Sb3023 : Pinned() {
        override val milan: PerProduct = Sb3020.milan
        override val genoa: PerProduct =
            Sb3020.genoa.copy(minMitVector = MIT_VECTOR_BIT_0 or MIT_VECTOR_BIT_1)
        override val turin: PerProduct =
            Sb3020.turin.copy(
                minMitVector =
                    MIT_VECTOR_BIT_0 or MIT_VECTOR_BIT_1 or
                        MIT_VECTOR_BIT_2 or MIT_VECTOR_BIT_4 or MIT_VECTOR_BIT_5,
            )

        override fun minMicrocode(
            family: CpuFamily,
            model: Int,
            stepping: Int,
        ): Int? =
            when (family) {
                CpuFamily.MILAN ->
                    when {
                        model == 1 && stepping == 1 -> 0xDE
                        model == 1 && stepping == 2 -> 0x47
                        else -> null
                    }
                CpuFamily.GENOA ->
                    when {
                        model == 0x11 && stepping == 1 -> 0x56
                        model == 0x11 && stepping == 2 -> 0x51
                        model == 0xA0 && stepping == 2 -> 0x1B
                        else -> null
                    }
                CpuFamily.TURIN ->
                    when {
                        model == 2 && stepping == 1 -> 0x51
                        model == 0x11 && stepping == 0 -> 0x4E
                        else -> null
                    }
            }
    }

    companion object {
        /**
         * Current top-shelf bulletin set; bound by every public verification
         * entry-point when no [Requirements] is passed.
         */
        val Default: Pinned get() = Sb3023
    }
}
