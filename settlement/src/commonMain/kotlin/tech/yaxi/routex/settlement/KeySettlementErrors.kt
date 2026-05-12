package tech.yaxi.routex.settlement

/** Reason why a key settlement verification failed. */
public sealed class KeySettlementError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** The settlement response body could not be parsed (invalid JSON or base64). */
    public class MalformedResponse(
        message: String,
        cause: Throwable? = null,
    ) : KeySettlementError(message, cause)

    /**
     * The attestation report itself is not signed by a trusted AMD chain or otherwise
     * fails report-level verification.
     */
    public class AttestationVerificationFailed(
        message: String,
        cause: Throwable? = null,
    ) : KeySettlementError(message, cause)

    /**
     * The system-version entry accompanying the report is malformed or is not signed by
     * a known YAXI system-version key.
     */
    public class SystemVersionInvalid(
        message: String,
        cause: Throwable? = null,
    ) : KeySettlementError(message, cause)

    /**
     * The launch measurement authenticated via the system-version signature does not
     * match the measurement reported inside the attestation report, the TEE is not the
     * one YAXI is expecting to operate.
     */
    public class MeasurementMismatch(
        message: String = "system-version launch measurement does not match attestation report measurement",
    ) : KeySettlementError(message)

    /**
     * The sealed ChaChaBox is not authenticated by the attestation report (sha256 of the
     * box does not match the first 32 bytes of `report_data`).
     */
    public class ChachaBoxBindingMismatch(
        message: String = "sha256(chachaBox) does not match report_data[0..32)",
    ) : KeySettlementError(message)

    /** Decrypting the sealed ChaChaBox with the client's secret key failed. */
    public class ChachaBoxDecryptFailed(
        message: String,
        cause: Throwable? = null,
    ) : KeySettlementError(message, cause)

    /**
     * The plaintext decrypted from the ChaChaBox is structurally invalid (not JSON,
     * missing fields, etc.).
     */
    public class InvalidSealedPayload(
        message: String,
        cause: Throwable? = null,
    ) : KeySettlementError(message, cause)

    /** Sealing/unsealing was attempted before settlement completed. */
    public class NotReady :
        KeySettlementError("Settlement has not completed yet; call settle() before sealing payloads")
}
