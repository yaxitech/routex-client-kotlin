package tech.yaxi.routex.settlement

import kotlin.io.encoding.Base64
import tech.yaxi.routex.settlement.internal.ed25519Verify

internal object SystemVersion {
    fun verify(
        entry: SystemVersionEntry,
        verifyingKeys: Map<String, ByteArray>,
    ): LaunchMeasurement {
        val key =
            verifyingKeys[entry.signature.keyId]
                ?: throw SystemVersionDecodeError.UnknownKeyId(entry.signature.keyId)
        val signatureBytes =
            try {
                Base64.Default.decode(entry.signature.value)
            } catch (e: IllegalArgumentException) {
                throw SystemVersionDecodeError.MalformedInput("signature value is not valid base64", e)
            }
        val createdAtNormalized =
            if (entry.rawCreatedAt.endsWith("Z")) {
                entry.rawCreatedAt.dropLast(1) + "+00:00"
            } else {
                entry.rawCreatedAt
            }
        val measurementBytes =
            when (val m = entry.launchMeasurement) {
                is LaunchMeasurement.V1 -> m.bytes
            }
        val message =
            buildString {
                append(entry.kind)
                append(entry.generation.toString())
                append(createdAtNormalized)
                append(entry.ref)
            }.encodeToByteArray() + measurementBytes
        val valid =
            try {
                ed25519Verify(key, message, signatureBytes)
            } catch (e: Exception) {
                throw SystemVersionDecodeError.InvalidSignature(
                    "system-version signature verification threw: ${e.message}",
                    cause = e,
                )
            }
        if (!valid) {
            throw SystemVersionDecodeError.InvalidSignature()
        }
        return entry.launchMeasurement
    }
}
