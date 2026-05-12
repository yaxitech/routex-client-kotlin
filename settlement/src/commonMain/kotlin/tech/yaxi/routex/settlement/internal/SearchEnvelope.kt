package tech.yaxi.routex.settlement.internal

import kotlinx.serialization.Serializable
import tech.yaxi.routex.models.ConnectionDetails
import tech.yaxi.routex.models.InternalRoutexApi
import tech.yaxi.routex.models.RoutexJson
import tech.yaxi.routex.models.SearchFilter

/** Wire envelope for the `/search` POST body. */
@Serializable
internal data class SearchEnvelope(
    val filters: List<SearchFilter>? = null,
    val ibanDetection: Boolean? = null,
    val limit: Int? = null,
    val details: List<ConnectionDetails>? = null,
)

/** Build the `/search` request body. */
@InternalRoutexApi
public fun encodeSearchRequestBody(
    filters: List<SearchFilter>,
    ibanDetection: Boolean,
    limit: UInt?,
    details: List<ConnectionDetails>,
): ByteArray {
    val envelope =
        SearchEnvelope(
            filters = filters.takeIf { it.isNotEmpty() },
            ibanDetection = ibanDetection.takeIf { it },
            limit = limit?.toInt(),
            details = details.takeIf { it.isNotEmpty() },
        )
    return RoutexJson.encodeToString(SearchEnvelope.serializer(), envelope).encodeToByteArray()
}
