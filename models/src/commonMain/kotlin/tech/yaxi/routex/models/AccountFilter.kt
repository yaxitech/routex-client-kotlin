package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Predicate applied to accounts when invoking the accounts service.
 *
 * Combine filters with [and] and [or] to form arbitrary boolean expressions. Use
 * [Supports] to keep only accounts that potentially support a given YAXI Open Banking
 * service.
 */
@Serializable(with = AccountFilterSerializer::class)
public sealed class AccountFilter {
    /**
     * Matches accounts whose [field][tech.yaxi.routex.models.AccountFilter.Eq.field]
     * equals [value][tech.yaxi.routex.models.AccountFilter.Eq.value]. Build via
     * [AccountField.eq].
     */
    @ConsistentCopyVisibility
    public data class Eq internal constructor(
        public val field: AccountField<*>,
        public val value: JsonElement,
    ) : AccountFilter()

    /**
     * Matches accounts whose
     * [field][tech.yaxi.routex.models.AccountFilter.NotEq.field] differs from
     * [value][tech.yaxi.routex.models.AccountFilter.NotEq.value]. Build via
     * [AccountField.notEq].
     */
    @ConsistentCopyVisibility
    public data class NotEq internal constructor(
        public val field: AccountField<*>,
        public val value: JsonElement,
    ) : AccountFilter()

    /** Matches accounts that satisfy both operands. Build via [and]. */
    @ConsistentCopyVisibility
    public data class And internal constructor(
        public val left: AccountFilter,
        public val right: AccountFilter,
    ) : AccountFilter()

    /** Matches accounts that satisfy either operand. Build via [or]. */
    @ConsistentCopyVisibility
    public data class Or internal constructor(
        public val left: AccountFilter,
        public val right: AccountFilter,
    ) : AccountFilter()

    /** Matches accounts that potentially support the given YAXI Open Banking service. */
    @ConsistentCopyVisibility
    public data class Supports internal constructor(
        public val service: SupportedService,
    ) : AccountFilter()

    /** Add an additional filter, logically AND-ing with this one. */
    public fun and(other: AccountFilter): AccountFilter = And(this, other)

    /** Add an alternative filter, logically OR-ing with this one. */
    public fun or(other: AccountFilter): AccountFilter = Or(this, other)
}

private fun String?.toJsonElement(): JsonElement = this?.let(::JsonPrimitive) ?: JsonNull

/** @suppress */
public object AccountFilterSerializer : ExternallyTaggedSerializer<AccountFilter>("AccountFilter") {
    private val fieldSerializer: KSerializer<AccountField<*>> = AccountFieldSerializer
    private val supportedServiceSerializer = SupportedService.serializer()

    override fun variantName(value: AccountFilter): String =
        when (value) {
            is AccountFilter.Eq -> "Eq"
            is AccountFilter.NotEq -> "NotEq"
            is AccountFilter.And -> "And"
            is AccountFilter.Or -> "Or"
            is AccountFilter.Supports -> "Supports"
        }

    override fun childSerializer(variant: String): KSerializer<out AccountFilter> =
        throw SerializationException("AccountFilter decoded via direct JSON; childSerializer not used")

    override fun encodePayload(value: AccountFilter): JsonElement =
        when (value) {
            is AccountFilter.Eq ->
                buildJsonArray {
                    add(RoutexJson.encodeToJsonElement(fieldSerializer, value.field))
                    add(value.value)
                }
            is AccountFilter.NotEq ->
                buildJsonArray {
                    add(RoutexJson.encodeToJsonElement(fieldSerializer, value.field))
                    add(value.value)
                }
            is AccountFilter.And ->
                buildJsonArray {
                    add(RoutexJson.encodeToJsonElement(AccountFilterSerializer, value.left))
                    add(RoutexJson.encodeToJsonElement(AccountFilterSerializer, value.right))
                }
            is AccountFilter.Or ->
                buildJsonArray {
                    add(RoutexJson.encodeToJsonElement(AccountFilterSerializer, value.left))
                    add(RoutexJson.encodeToJsonElement(AccountFilterSerializer, value.right))
                }
            is AccountFilter.Supports ->
                RoutexJson.encodeToJsonElement(supportedServiceSerializer, value.service)
        }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): AccountFilter {
        val jsonDecoder =
            decoder as? kotlinx.serialization.json.JsonDecoder
                ?: throw SerializationException("AccountFilter only supports JSON")
        val obj =
            jsonDecoder.decodeJsonElement() as? kotlinx.serialization.json.JsonObject
                ?: throw SerializationException("Expected JSON object for AccountFilter")
        if (obj.size != 1) {
            throw SerializationException("Expected one-key object for AccountFilter, got ${obj.keys}")
        }
        val (variant, payload) = obj.entries.first()
        val json = jsonDecoder.json
        return when (variant) {
            "Eq" -> {
                val arr = payload.expectArray(2)
                AccountFilter.Eq(
                    json.decodeFromJsonElement(fieldSerializer, arr[0]),
                    arr[1],
                )
            }
            "NotEq" -> {
                val arr = payload.expectArray(2)
                AccountFilter.NotEq(
                    json.decodeFromJsonElement(fieldSerializer, arr[0]),
                    arr[1],
                )
            }
            "And" -> {
                val arr = payload.expectArray(2)
                AccountFilter.And(
                    json.decodeFromJsonElement(AccountFilterSerializer, arr[0]),
                    json.decodeFromJsonElement(AccountFilterSerializer, arr[1]),
                )
            }
            "Or" -> {
                val arr = payload.expectArray(2)
                AccountFilter.Or(
                    json.decodeFromJsonElement(AccountFilterSerializer, arr[0]),
                    json.decodeFromJsonElement(AccountFilterSerializer, arr[1]),
                )
            }
            "Supports" ->
                AccountFilter.Supports(json.decodeFromJsonElement(supportedServiceSerializer, payload))
            else -> throw SerializationException("Unknown AccountFilter variant: $variant")
        }
    }

    private fun JsonElement.expectArray(expectedSize: Int): JsonArray {
        val arr =
            this as? JsonArray
                ?: throw SerializationException("Expected JSON array, got $this")
        if (arr.size != expectedSize) {
            throw SerializationException("Expected array of size $expectedSize, got ${arr.size}")
        }
        return arr
    }
}
