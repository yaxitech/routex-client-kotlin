package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Response from a YAXI Open Banking service.
 *
 * The response either carries a final, authenticated [Result] of type [T] or one of
 * three interrupts that require additional user interaction: a [Dialog], a [Redirect],
 * or a [RedirectHandle]. Service methods on `RoutexClient` specialize [T] to the
 * per-service result type (e.g. [AccountsResult], [BalancesResult]) so callers get
 * compile-time access to the decoded payload via [Result.authenticated]`.decodeUnverified()`.
 */
public sealed class Response<T> {
    /**
     * Final, authenticated service result.
     *
     * @property authenticated Lazy [Authenticated] wrapper around the JWT-bearing
     *   payload. Call `.decodeUnverified()` to decode the typed body, `.jwt` to forward
     *   the raw token to a verifying backend.
     * @property session Session token to pass on subsequent requests within the same
     *   logical session.
     * @property connectionData Opaque connection data, usable to reuse the established
     *   consent via the non-interactive refresh endpoints.
     */
    public class Result<T> internal constructor(
        public val authenticated: Authenticated<T>,
        public val session: Session? = null,
        public val connectionData: ConnectionData? = null,
    ) : Response<T>() {
        override fun equals(other: Any?): Boolean =
            other is Result<*> &&
                authenticated.jwt == other.authenticated.jwt &&
                session == other.session &&
                connectionData == other.connectionData

        override fun hashCode(): Int {
            var result = authenticated.jwt.hashCode()
            result = 31 * result + (session?.hashCode() ?: 0)
            result = 31 * result + (connectionData?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String =
            "Response.Result(jwt=<${authenticated.jwt.length} chars>, session=$session, " +
                "connectionData=$connectionData)"
    }

    /** Interrupt: user dialog that must be presented before the flow can continue. */
    public data class Dialog<T>(
        public val dialog: tech.yaxi.routex.models.Dialog,
    ) : Response<T>()

    /** Interrupt: fully-formed user redirect to complete the flow in the user agent. */
    public data class Redirect<T>(
        public val redirect: tech.yaxi.routex.models.Redirect,
    ) : Response<T>()

    /**
     * Interrupt: incomplete user redirect. A final redirect URI must be registered using
     * the handle to receive the URL to send the user to.
     */
    public data class RedirectHandle<T>(
        public val redirectHandle: tech.yaxi.routex.models.RedirectHandle,
    ) : Response<T>()
}

/**
 * Decode a [Response] from its JSON [text] representation, parameterised by the
 * per-service `data` claim type. Throws `SerializationException` for any malformed
 * input. The resulting [Authenticated] wrapper inside a [Response.Result] is lazy;
 * JWT body decoding only happens when the caller calls `.decodeUnverified()`.
 *
 * @suppress
 */
@InternalRoutexApi
public fun <T> decodeResponse(
    text: String,
    dataSerializer: KSerializer<T>,
): Response<T> {
    val element =
        try {
            RoutexJson.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw SerializationException("Response is not valid JSON: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw SerializationException("Response is not valid JSON: ${e.message}", e)
        }
    val obj =
        element as? JsonObject
            ?: throw SerializationException("Expected JSON object for Response, got $element")
    if (obj.size != 1) {
        throw SerializationException("Expected one-key object for Response, got ${obj.keys}")
    }
    val (variant, payload) = obj.entries.first()
    return when (variant) {
        "Result" -> decodeResultVariant(payload, dataSerializer)
        "Dialog" ->
            Response.Dialog(RoutexJson.decodeFromJsonElement(Dialog.serializer(), payload))
        "Redirect" ->
            Response.Redirect(RoutexJson.decodeFromJsonElement(Redirect.serializer(), payload))
        "RedirectHandle" ->
            Response.RedirectHandle(
                RoutexJson.decodeFromJsonElement(RedirectHandle.serializer(), payload),
            )
        else -> throw SerializationException("Unknown Response variant: $variant")
    }
}

/**
 * Encode a [Response] to JSON.
 *
 * @suppress
 */
@InternalRoutexApi
public fun <T> encodeResponse(value: Response<T>): String =
    RoutexJson.encodeToString(JsonObject.serializer(), encodeResponseToJsonObject(value))

internal fun <T> encodeResponseToJsonObject(value: Response<T>): JsonObject =
    when (value) {
        is Response.Result ->
            buildJsonObject {
                put(
                    "Result",
                    buildJsonArray {
                        add(JsonPrimitive(value.authenticated.jwt))
                        add(
                            value.session?.let {
                                RoutexJson.encodeToJsonElement(SessionSerializer, it)
                            } ?: JsonNull,
                        )
                        add(
                            value.connectionData?.let {
                                RoutexJson.encodeToJsonElement(ConnectionDataSerializer, it)
                            } ?: JsonNull,
                        )
                    },
                )
            }
        is Response.Dialog ->
            buildJsonObject {
                put("Dialog", RoutexJson.encodeToJsonElement(Dialog.serializer(), value.dialog))
            }
        is Response.Redirect ->
            buildJsonObject {
                put("Redirect", RoutexJson.encodeToJsonElement(Redirect.serializer(), value.redirect))
            }
        is Response.RedirectHandle ->
            buildJsonObject {
                put(
                    "RedirectHandle",
                    RoutexJson.encodeToJsonElement(RedirectHandle.serializer(), value.redirectHandle),
                )
            }
    }

private fun <T> decodeResultVariant(
    payload: JsonElement,
    dataSerializer: KSerializer<T>,
): Response.Result<T> {
    val arr =
        payload as? JsonArray
            ?: throw SerializationException("Response::Result payload must be an array")
    if (arr.size != 3) {
        throw SerializationException("Response::Result tuple must have 3 elements, got ${arr.size}")
    }
    val jwt = RoutexJson.decodeFromJsonElement(String.serializer(), arr[0])
    val session =
        if (arr[1] is JsonNull) null else RoutexJson.decodeFromJsonElement(SessionSerializer, arr[1])
    val connectionData =
        if (arr[2] is JsonNull) {
            null
        } else {
            RoutexJson.decodeFromJsonElement(ConnectionDataSerializer, arr[2])
        }
    return Response.Result(
        authenticated = Authenticated(jwt) { decodeJwtDataClaim(it, dataSerializer) },
        session = session,
        connectionData = connectionData,
    )
}
