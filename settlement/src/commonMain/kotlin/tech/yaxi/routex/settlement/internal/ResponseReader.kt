package tech.yaxi.routex.settlement.internal

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import tech.yaxi.routex.models.ConnectionInfo
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.Response
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.decodeResponse
import tech.yaxi.routex.settlement.RoutexClientError

/**
 * Decode the wire-form bytes of an OB-service response and return a typed `Response`.
 * Wraps any `SerializationException` as a [RoutexClientError.MalformedResponse].
 *
 * The [dataSerializer] only affects the `Result` variant (which carries an
 * `Authenticated` of the per-service payload). Decoding is lazy, the JWT body is not
 * parsed until `Result.authenticated.decodeUnverified()` is called.
 */
@InternalRoutexApi
public fun <T> readResponse(
    bodyBytes: ByteArray,
    dataSerializer: KSerializer<T>,
): Response<T> {
    val text = bodyBytes.decodeToString()
    return try {
        decodeResponse(text, dataSerializer)
    } catch (e: SerializationException) {
        throw RoutexClientError.MalformedResponse("Failed to decode Response: ${e.message}", e)
    } catch (e: IllegalArgumentException) {
        throw RoutexClientError.MalformedResponse("Failed to decode Response: ${e.message}", e)
    }
}

@InternalRoutexApi
public fun readConnectionInfo(bodyBytes: ByteArray): ConnectionInfo {
    val text = bodyBytes.decodeToString()
    return try {
        RoutexJson.decodeFromString(ConnectionInfo.serializer(), text)
    } catch (e: SerializationException) {
        throw RoutexClientError.MalformedResponse("Failed to decode ConnectionInfo: ${e.message}", e)
    }
}

@InternalRoutexApi
public fun readConnectionInfoList(bodyBytes: ByteArray): List<ConnectionInfo> {
    val text = bodyBytes.decodeToString()
    val listSerializer = ListSerializer(ConnectionInfo.serializer())
    return try {
        RoutexJson.decodeFromString(listSerializer, text)
    } catch (e: SerializationException) {
        throw RoutexClientError.MalformedResponse("Failed to decode ConnectionInfo list: ${e.message}", e)
    }
}
