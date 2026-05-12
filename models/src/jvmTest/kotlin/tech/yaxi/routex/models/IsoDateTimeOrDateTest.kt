package tech.yaxi.routex.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class IsoDateTimeOrDateTest {
    private val json = Json

    private fun encode(value: IsoDateTimeOrDate): String = json.encodeToString(IsoDateTimeOrDateSerializer, value)

    private fun decode(raw: String): IsoDateTimeOrDate = json.decodeFromString(IsoDateTimeOrDateSerializer, raw)

    private fun wireOf(value: IsoDateTimeOrDate): String =
        (json.encodeToJsonElement(IsoDateTimeOrDateSerializer, value) as JsonPrimitive).content

    @TestFactory
    fun `dispatch picks the right variant from the wire string`(): List<DynamicTest> =
        listOf(
            "2024-03-15" to IsoDateTimeOrDate.Date::class,
            "0001-01-01" to IsoDateTimeOrDate.Date::class,
            "9999-12-31" to IsoDateTimeOrDate.Date::class,
            "2024-03-15T14:30:00" to IsoDateTimeOrDate.NaiveDateTime::class,
            "2024-03-15T00:00:00" to IsoDateTimeOrDate.NaiveDateTime::class,
            "2024-03-15T23:59:59" to IsoDateTimeOrDate.NaiveDateTime::class,
            "2024-03-15T14:30:00Z" to IsoDateTimeOrDate.OffsetDateTime::class,
            "2024-03-15T14:30:00+02:00" to IsoDateTimeOrDate.OffsetDateTime::class,
            "2024-03-15T14:30:00-05:00" to IsoDateTimeOrDate.OffsetDateTime::class,
            "2024-03-15T14:30:00+05:30" to IsoDateTimeOrDate.OffsetDateTime::class,
            "2024-03-15T14:30:00-11:00" to IsoDateTimeOrDate.OffsetDateTime::class,
        ).map { (raw, expected) ->
            DynamicTest.dynamicTest("\"$raw\" -> ${expected.simpleName}") {
                val jsonLiteral = json.encodeToString(String.serializer(), raw)
                val decoded = json.decodeFromString(IsoDateTimeOrDateSerializer, jsonLiteral)
                assertEquals(expected, decoded::class)
            }
        }

    @TestFactory
    fun `Date round-trips the wire representation`(): List<DynamicTest> =
        listOf(
            "2024-03-15",
            "2020-02-29", // leap day
            "0001-01-01",
            "9999-12-31",
        ).map { raw ->
            DynamicTest.dynamicTest(raw) {
                val jsonLiteral = "\"" + raw + "\""
                val decoded = decode(jsonLiteral)
                val reencoded = wireOf(decoded)
                assertEquals(raw, reencoded)
                assertEquals(IsoDateTimeOrDate.Date(LocalDate.parse(raw)), decoded)
            }
        }

    @TestFactory
    fun `NaiveDateTime round-trips seconds-precision wire`(): List<DynamicTest> =
        listOf(
            "2024-03-15T14:30:00",
            "2024-03-15T00:00:00",
            "2024-03-15T23:59:59",
            "2024-02-29T12:00:00", // leap day datetime
        ).map { raw ->
            DynamicTest.dynamicTest(raw) {
                val jsonLiteral = "\"" + raw + "\""
                val decoded = decode(jsonLiteral)
                val reencoded = wireOf(decoded)
                assertEquals(raw, reencoded)
                assertEquals(
                    IsoDateTimeOrDate.NaiveDateTime(LocalDateTime.parse(raw)),
                    decoded,
                )
            }
        }

    @TestFactory
    fun `OffsetDateTime round-trips common offsets`(): List<DynamicTest> =
        listOf(
            "2024-03-15T14:30:00Z",
            "2024-03-15T14:30:00+02:00",
            "2024-03-15T14:30:00-05:00",
            "2024-03-15T14:30:00+05:30", // India
            "2024-03-15T14:30:00-03:30", // Newfoundland
            "2024-03-15T14:30:00+14:00", // max positive (Kiribati)
            "2024-03-15T14:30:00-12:00", // extreme negative
        ).map { raw ->
            DynamicTest.dynamicTest(raw) {
                val jsonLiteral = "\"" + raw + "\""
                val decoded = decode(jsonLiteral)
                val reencoded = wireOf(decoded)
                assertEquals(raw, reencoded)
            }
        }

    @Test
    fun `+00-00 on the wire normalizes to Z on re-emit`() {
        val decoded = decode("\"2024-03-15T14:30:00+00:00\"")
        assertEquals(
            IsoDateTimeOrDate.OffsetDateTime(
                LocalDateTime.parse("2024-03-15T14:30:00"),
                UtcOffset.ZERO,
            ),
            decoded,
        )
        assertEquals("2024-03-15T14:30:00Z", wireOf(decoded))
    }

    @Test
    fun `encoding a NaiveDateTime with trailing zero seconds keeps the seconds on the wire`() {
        val value = IsoDateTimeOrDate.NaiveDateTime(LocalDateTime(2024, 3, 15, 14, 30, 0))
        assertEquals("\"2024-03-15T14:30:00\"", encode(value))
    }

    @TestFactory
    fun `invalid wire strings surface a SerializationException`(): List<DynamicTest> =
        listOf(
            "not-a-date",
            "2024-13-01", // bad month
            "2024-03-32", // bad day
            "2024-03-15T25:00:00", // bad hour
            "2024-03-15T14:60:00", // bad minute
            "2024-03-15T14:30:00+25:00", // bad offset
            "", // empty
            "2024-03-15T", // stub
        ).map { raw ->
            DynamicTest.dynamicTest("\"$raw\"") {
                val jsonLiteral = "\"" + raw + "\""
                assertFailsWith<SerializationException> { decode(jsonLiteral) }
            }
        }
}
