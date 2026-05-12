package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Optional extra fields a search may request the server to populate on each returned
 * [ConnectionInfo].
 */
@Serializable
public enum class ConnectionDetails {
    /** Populate [ConnectionInfo.bics]. */
    @SerialName("bics")
    BICS,
}

/**
 * Predicate applied when searching for service connections.
 *
 * String filters look for the given value anywhere in the related field, case-insensitive.
 * Combine multiple filters by passing them all in the same `filters` list to
 * `RoutexClient.search` / `RoutexRefreshClient.search`, all filters must match for a
 * connection to be returned.
 */
@Serializable(with = SearchFilterSerializer::class)
public sealed class SearchFilter {
    /** List of ISO 3166-1 ALPHA-2 country codes to consider. */
    public data class Countries(
        public val codes: List<String>,
    ) : SearchFilter()

    /** String filter for the provider / product name or any alias. */
    public data class Name(
        public val value: String,
    ) : SearchFilter()

    /** String filter for the BIC. */
    public data class Bic(
        public val value: String,
    ) : SearchFilter()

    /** String filter for the (national) bank code. */
    public data class BankCode(
        public val value: String,
    ) : SearchFilter()

    /** String filter matching any of the provider name, BIC, or bank code fields. */
    public data class Term(
        public val value: String,
    ) : SearchFilter()

    /** Server-side filter matching an encrypted IBAN against the connection. */
    public data class EncryptedIban(
        public val bytes: ByteArray,
    ) : SearchFilter() {
        /**
         * Convenience constructor for the typical backend-to-frontend handoff: the
         * ciphertext is decoded from its Base64 transport form.
         *
         * @throws IllegalArgumentException if [base64] is not valid Base64.
         */
        public constructor(base64: String) : this(Base64.Default.decode(base64))

        override fun equals(other: Any?): Boolean = other is EncryptedIban && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

/** @suppress */
public object SearchFilterSerializer : KSerializer<SearchFilter> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SearchFilter")

    override fun serialize(
        encoder: Encoder,
        value: SearchFilter,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw SerializationException("SearchFilter only supports JSON")
        val obj =
            when (value) {
                is SearchFilter.Countries ->
                    buildJsonObject {
                        put(
                            "countries",
                            buildJsonArray {
                                value.codes.forEach { add(JsonPrimitive(it)) }
                            },
                        )
                    }
                is SearchFilter.Name -> buildJsonObject { put("name", value.value) }
                is SearchFilter.Bic -> buildJsonObject { put("bic", value.value) }
                is SearchFilter.BankCode -> buildJsonObject { put("bankCode", value.value) }
                is SearchFilter.Term -> buildJsonObject { put("term", value.value) }
                is SearchFilter.EncryptedIban ->
                    buildJsonObject {
                        put(
                            "encryptedIban",
                            RoutexJson.encodeToJsonElement(Base64ByteArraySerializer, value.bytes),
                        )
                    }
            }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): SearchFilter {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw SerializationException("SearchFilter only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected JSON object for SearchFilter")
        return when {
            "countries" in obj ->
                SearchFilter.Countries(
                    (obj["countries"] as JsonArray).map {
                        it.jsonPrimitive.content
                    },
                )
            "name" in obj -> SearchFilter.Name(obj["name"]!!.jsonPrimitive.content)
            "bic" in obj -> SearchFilter.Bic(obj["bic"]!!.jsonPrimitive.content)
            "bankCode" in obj -> SearchFilter.BankCode(obj["bankCode"]!!.jsonPrimitive.content)
            "term" in obj -> SearchFilter.Term(obj["term"]!!.jsonPrimitive.content)
            "encryptedIban" in obj ->
                SearchFilter.EncryptedIban(
                    RoutexJson.decodeFromJsonElement(Base64ByteArraySerializer, obj["encryptedIban"]!!),
                )
            else -> throw SerializationException("Unknown SearchFilter variant: ${obj.keys}")
        }
    }
}
