package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Field of an [Account] that can be referenced in filters or requested from the service.
 *
 * Build filters via [eq] / [notEq] on the per-field objects, e.g.
 * `AccountField.Iban.eq("DE12...")`. The [ValueType] type parameter pins the accepted
 * value type for each field; `null` is meaningful for the nullable variants and means
 * "field is unset on the account".
 */
@kotlinx.serialization.Serializable(with = AccountFieldSerializer::class)
public sealed class AccountField<ValueType> {
    /** ISO 20022 IBAN2007Identifier. */
    public data object Iban : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** Non-IBAN account number, e.g. ISO 20022 BBANIdentifier. */
    public data object Number : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** Masked payment card number. */
    public data object MaskedPan : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** ISO 20022 BICFIIdentifier. */
    public data object Bic : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** National bank code. */
    public data object BankCode : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** ISO 4217 Alpha 3 currency code. Always present on the account, hence non-null. */
    public data object Currency : AccountField<String>() {
        override fun eq(value: String): AccountFilter = AccountFilter.Eq(this, JsonPrimitive(value))

        override fun notEq(value: String): AccountFilter = AccountFilter.NotEq(this, JsonPrimitive(value))
    }

    /** Name of the account, assigned by the ASPSP. */
    public data object Name : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** Display name of the account, assigned by the PSU. */
    public data object DisplayName : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** Legal account owner. */
    public data object OwnerName : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** Product name. */
    public data object ProductName : AccountField<String?>() {
        override fun eq(value: String?): AccountFilter = nullableEq(this, value)

        override fun notEq(value: String?): AccountFilter = nullableNotEq(this, value)
    }

    /** Account status. */
    public data object Status : AccountField<AccountStatus?>() {
        private val serializer = AccountStatus.serializer()

        override fun eq(value: AccountStatus?): AccountFilter = AccountFilter.Eq(this, encodeEnum(value, serializer))

        override fun notEq(value: AccountStatus?): AccountFilter =
            AccountFilter.NotEq(this, encodeEnum(value, serializer))
    }

    /** Account type. */
    public data object Type : AccountField<AccountType?>() {
        private val serializer = AccountType.serializer()

        override fun eq(value: AccountType?): AccountFilter = AccountFilter.Eq(this, encodeEnum(value, serializer))

        override fun notEq(value: AccountType?): AccountFilter =
            AccountFilter.NotEq(this, encodeEnum(value, serializer))
    }

    /** Build a `field == value` filter. */
    public abstract fun eq(value: ValueType): AccountFilter

    /** Build a `field != value` filter. */
    public abstract fun notEq(value: ValueType): AccountFilter
}

/** @suppress */
public object AccountFieldSerializer : KSerializer<AccountField<*>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.AccountField", PrimitiveKind.STRING)

    private val byWire: Map<String, AccountField<*>> =
        listOf(
            "Iban" to AccountField.Iban,
            "Number" to AccountField.Number,
            "MaskedPan" to AccountField.MaskedPan,
            "Bic" to AccountField.Bic,
            "BankCode" to AccountField.BankCode,
            "Currency" to AccountField.Currency,
            "Name" to AccountField.Name,
            "DisplayName" to AccountField.DisplayName,
            "OwnerName" to AccountField.OwnerName,
            "ProductName" to AccountField.ProductName,
            "Status" to AccountField.Status,
            "Type" to AccountField.Type,
        ).toMap()

    private val byKotlin: Map<AccountField<*>, String> = byWire.entries.associate { (k, v) -> v to k }

    override fun serialize(
        encoder: Encoder,
        value: AccountField<*>,
    ) {
        val wire =
            byKotlin[value]
                ?: throw SerializationException("No wire mapping for AccountField $value")
        encoder.encodeString(wire)
    }

    override fun deserialize(decoder: Decoder): AccountField<*> {
        val wire = decoder.decodeString()
        return byWire[wire]
            ?: throw SerializationException("Unknown AccountField wire variant: '$wire'")
    }
}

private fun nullableEq(
    field: AccountField<String?>,
    value: String?,
): AccountFilter = AccountFilter.Eq(field, value?.let(::JsonPrimitive) ?: JsonNull)

private fun nullableNotEq(
    field: AccountField<String?>,
    value: String?,
): AccountFilter = AccountFilter.NotEq(field, value?.let(::JsonPrimitive) ?: JsonNull)

private fun <T> encodeEnum(
    value: T?,
    serializer: KSerializer<T>,
): JsonElement = value?.let { RoutexJson.encodeToJsonElement(serializer, it) } ?: JsonNull
