package tech.yaxi.routex.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Date-or-date-time value as used by the transfer service's `requestedExecutionDate`.
 *
 * Accepts three forms:
 *  - a plain ISO date ([Date]);
 *  - a naive (offset-less) ISO date-time ([NaiveDateTime]);
 *  - an offset-aware ISO date-time with a UTC offset or `Z` ([OffsetDateTime]).
 */
@Serializable(with = IsoDateTimeOrDateSerializer::class)
public sealed class IsoDateTimeOrDate {
    /** Plain ISO date (no time component). */
    public data class Date(
        public val value: LocalDate,
    ) : IsoDateTimeOrDate()

    /** ISO date-time without an offset. */
    public data class NaiveDateTime(
        public val value: LocalDateTime,
    ) : IsoDateTimeOrDate()

    /** ISO date-time with an explicit UTC offset. */
    public data class OffsetDateTime(
        public val dateTime: LocalDateTime,
        public val offset: UtcOffset,
    ) : IsoDateTimeOrDate()
}

/** @suppress */
public object IsoDateTimeOrDateSerializer : KSerializer<IsoDateTimeOrDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "tech.yaxi.routex.models.IsoDateTimeOrDate",
            PrimitiveKind.STRING,
        )

    // Always emits seconds; LocalDateTime.toString() elides :00 otherwise, which would break
    // byte-exact round-trip of fixtures like "2024-03-15T14:30:00".
    private val dateTimeFormat =
        LocalDateTime.Format {
            year(padding = Padding.ZERO)
            char('-')
            monthNumber(padding = Padding.ZERO)
            char('-')
            day(padding = Padding.ZERO)
            char('T')
            hour(padding = Padding.ZERO)
            char(':')
            minute(padding = Padding.ZERO)
            char(':')
            second(padding = Padding.ZERO)
        }

    private val offsetFormat = UtcOffset.Formats.ISO

    override fun serialize(
        encoder: Encoder,
        value: IsoDateTimeOrDate,
    ) {
        encoder.encodeString(
            when (value) {
                is IsoDateTimeOrDate.Date -> value.value.toString()
                is IsoDateTimeOrDate.NaiveDateTime -> dateTimeFormat.format(value.value)
                is IsoDateTimeOrDate.OffsetDateTime -> {
                    val offsetString =
                        if (value.offset == UtcOffset.ZERO) {
                            "Z"
                        } else {
                            offsetFormat.format(value.offset)
                        }
                    dateTimeFormat.format(value.dateTime) + offsetString
                }
            },
        )
    }

    override fun deserialize(decoder: Decoder): IsoDateTimeOrDate {
        val raw = decoder.decodeString()
        return try {
            decodeInternal(raw)
        } catch (e: IllegalArgumentException) {
            // kotlinx.datetime's DateTimeFormatException extends IllegalArgumentException.
            throw SerializationException("Invalid IsoDateTimeOrDate value: \"$raw\"", e)
        }
    }

    private fun decodeInternal(raw: String): IsoDateTimeOrDate {
        if (!raw.contains('T')) {
            return IsoDateTimeOrDate.Date(LocalDate.parse(raw))
        }
        val tIndex = raw.indexOf('T')
        val tail = raw.substring(tIndex + 1)
        val offsetStart = tail.indexOfFirst { it == 'Z' || it == '+' || it == '-' }
        if (offsetStart == -1) {
            return IsoDateTimeOrDate.NaiveDateTime(LocalDateTime.parse(raw))
        }
        val offsetString = tail.substring(offsetStart)
        val dateTimeString = raw.substring(0, tIndex + 1 + offsetStart)
        val dateTime = LocalDateTime.parse(dateTimeString)
        val offset =
            if (offsetString == "Z") UtcOffset.ZERO else offsetFormat.parse(offsetString)
        return IsoDateTimeOrDate.OffsetDateTime(dateTime, offset)
    }
}
