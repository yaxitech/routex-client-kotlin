package tech.yaxi.routex.settlement

import kotlin.io.encoding.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.yaxi.routex.settlement.internal.sha256

@Serializable
internal data class SettlementResponse(
    val attestationReport: String,
    val vcek: String,
    val chachaBox: String,
    val systemVersion: SystemVersionEntry,
)

internal data class SettleResult(
    val serverPublicKey: ChaChaBoxPublicKey,
    val sessionId: String,
    val systemVersion: SystemVersionEntry,
)

internal object KeySettlement {
    private val json: Json = Json { ignoreUnknownKeys = true }

    suspend fun verify(
        responseBytes: ByteArray,
        clientKeys: ChaChaBoxKeys,
        vcekRoots: VcekRootStore,
        requirements: Requirements,
        systemVersionKeys: Map<String, ByteArray>,
    ): SettleResult {
        val response = decodeResponse(responseBytes)
        val reportBytes = decodeBase64(response.attestationReport, "attestationReport")
        val chachaBoxBytes = decodeBase64(response.chachaBox, "chachaBox")

        // Verification order: attestation -> chachaBox binding -> systemVersion
        // signature -> measurement match -> chachaBox decrypt.
        val (report, _) =
            try {
                ReportVerifier.verifyBytes(reportBytes, response.vcek, vcekRoots, requirements)
            } catch (e: AttestationReportDecodeError) {
                throw KeySettlementError.AttestationVerificationFailed(
                    "attestation report parse failed: ${e.message}",
                    e,
                )
            } catch (e: VcekChainError) {
                throw KeySettlementError.AttestationVerificationFailed(
                    "VCEK chain verification failed: ${e.message}",
                    e,
                )
            } catch (e: ReportVerificationError) {
                throw KeySettlementError.AttestationVerificationFailed(
                    "attestation verification failed: ${e.message}",
                    e,
                )
            }

        val chachaHash = sha256(chachaBoxBytes)
        val reportBinding = report.reportData.copyOfRange(0, 32)
        if (!chachaHash.contentEquals(reportBinding)) {
            throw KeySettlementError.ChachaBoxBindingMismatch()
        }

        val measurement =
            try {
                SystemVersion.verify(response.systemVersion, systemVersionKeys)
            } catch (e: SystemVersionDecodeError) {
                throw KeySettlementError.SystemVersionInvalid(
                    "system version verification failed: ${e.message}",
                    e,
                )
            }
        val measurementBytes =
            when (measurement) {
                is LaunchMeasurement.V1 -> measurement.bytes
            }
        if (!measurementBytes.contentEquals(report.measurement)) {
            throw KeySettlementError.MeasurementMismatch()
        }

        val plaintext =
            try {
                ChaChaBox.unseal(clientKeys, chachaBoxBytes)
            } catch (e: ChaChaBoxError) {
                throw KeySettlementError.ChachaBoxDecryptFailed(
                    "chacha box decryption failed: ${e.message}",
                    e,
                )
            }
        val (serverPublicKey, sessionId) = decodeSealedPayload(plaintext)
        return SettleResult(serverPublicKey, sessionId, response.systemVersion)
    }

    private fun decodeSealedPayload(plaintext: ByteArray): Pair<ChaChaBoxPublicKey, String> {
        val text = plaintext.decodeToString()
        val obj =
            try {
                json.decodeFromString(JsonObject.serializer(), text)
            } catch (e: Exception) {
                throw KeySettlementError.InvalidSealedPayload(
                    "sealed plaintext is not a JSON object: ${e.message}",
                    cause = e,
                )
            }
        val publicKey =
            obj["publicKey"]?.jsonPrimitive?.content
                ?: throw KeySettlementError.InvalidSealedPayload("sealed payload missing publicKey")
        val sessionId =
            obj["sessionId"]?.jsonPrimitive?.content
                ?: throw KeySettlementError.InvalidSealedPayload("sealed payload missing sessionId")
        val serverPublic =
            try {
                Base64.Default.decode(publicKey)
            } catch (e: IllegalArgumentException) {
                throw KeySettlementError.InvalidSealedPayload(
                    "sealed payload publicKey not valid base64: ${e.message}",
                    cause = e,
                )
            }
        val serverPublicKey =
            try {
                ChaChaBoxPublicKey.fromBytes(serverPublic)
            } catch (e: IllegalArgumentException) {
                throw KeySettlementError.InvalidSealedPayload(
                    "sealed payload publicKey is invalid: ${e.message}",
                    cause = e,
                )
            }
        return serverPublicKey to sessionId
    }

    private fun decodeResponse(responseBytes: ByteArray): SettlementResponse {
        val text = responseBytes.decodeToString()
        return try {
            json.decodeFromString(SettlementResponse.serializer(), text)
        } catch (e: Exception) {
            throw KeySettlementError.MalformedResponse(
                "failed to decode settlement response JSON: ${e.message}",
                e,
            )
        }
    }

    private fun decodeBase64(
        value: String,
        field: String,
    ): ByteArray =
        try {
            Base64.Default.decode(value)
        } catch (e: IllegalArgumentException) {
            throw KeySettlementError.MalformedResponse(
                "$field is not valid base64: ${e.message}",
                e,
            )
        }
}
