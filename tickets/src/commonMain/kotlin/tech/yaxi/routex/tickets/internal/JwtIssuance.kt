package tech.yaxi.routex.tickets.internal

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import tech.yaxi.routex.models.Base64UrlNoPad

@Serializable
private data class JwtHeader(
    val alg: String,
    val typ: String,
    val kid: String,
)

@Serializable
private data class JwtClaims(
    val data: Body,
    val exp: Long,
) {
    @Serializable
    data class Body(
        val service: String,
        val id: String,
        val data: JsonElement?,
    )
}

/**
 * Build the `{ data: { service, id, data }, exp }` JWT-claim object that routex
 * expects.
 *
 * [ticketData] is emitted as `null` (rather than omitted) when the caller has
 * none, to keep the shape stable across a server round-trip; the routex
 * deserializer accepts either form on input.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun buildClaimsJson(
    serviceId: String,
    ticketId: Uuid,
    ticketData: JsonElement?,
    expiresAtSeconds: Long,
): String =
    Json.encodeToString(
        JwtClaims.serializer(),
        JwtClaims(
            data = JwtClaims.Body(service = serviceId, id = ticketId.toString(), data = ticketData),
            exp = expiresAtSeconds,
        ),
    )

/**
 * Produce the compact-form JWT `<headerB64>.<payloadB64>.<signatureB64>` for the
 * supplied [claimsJson] keyed by ([apiKeyId], [apiKeySecret]).
 *
 * The header is encoded via the same JSON encoder as the payload, which means
 * any special characters in [apiKeyId] are JSON-escaped correctly.
 */
internal fun signCompactJwt(
    apiKeyId: String,
    apiKeySecret: ByteArray,
    claimsJson: String,
): String {
    val headerJson =
        Json.encodeToString(JwtHeader.serializer(), JwtHeader(alg = "HS256", typ = "JWT", kid = apiKeyId))
    val headerB64 = Base64UrlNoPad.encode(headerJson.encodeToByteArray())
    val payloadB64 = Base64UrlNoPad.encode(claimsJson.encodeToByteArray())
    val signingInput = "$headerB64.$payloadB64"
    val signature = hmacSha256(apiKeySecret, signingInput.encodeToByteArray())
    val signatureB64 = Base64UrlNoPad.encode(signature)
    return "$signingInput.$signatureB64"
}
