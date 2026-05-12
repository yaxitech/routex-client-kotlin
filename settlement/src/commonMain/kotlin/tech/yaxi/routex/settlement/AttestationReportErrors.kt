package tech.yaxi.routex.settlement

internal sealed class AttestationReportDecodeError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class InvalidSize(
        val actual: Int,
    ) : AttestationReportDecodeError(
            "attestation report has unexpected length: $actual (expected ${AttestationReport.REPORT_LENGTH})",
        )

    class UnsupportedSignatureAlgorithm(
        val code: Int,
    ) : AttestationReportDecodeError(
            "unsupported signature algorithm $code (only ECDSA P-384 + SHA-384 supported)",
        )

    class MalformedInput(
        message: String,
        cause: Throwable? = null,
    ) : AttestationReportDecodeError(message, cause)
}
