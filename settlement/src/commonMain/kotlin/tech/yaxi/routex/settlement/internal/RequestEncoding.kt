package tech.yaxi.routex.settlement.internal

import kotlinx.serialization.KSerializer
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.RoutexJson

/**
 * Serialize [value] with the supplied serializer using the project-wide `RoutexJson`
 * configuration and return the resulting UTF-8 bytes.
 */
@InternalRoutexApi
public fun <T> encodeRequestBody(
    serializer: KSerializer<T>,
    value: T,
): ByteArray = RoutexJson.encodeToString(serializer, value).encodeToByteArray()
