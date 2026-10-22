package tech.yaxi.routex.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmountCtorTest {
    @Test
    fun roundTripsAcrossSerializer() {
        // The string-ctor must produce values that serialize byte-identically to their
        // input. Anything else changes what reaches the YAXI API.
        listOf(
            "0",
            "0.00",
            "42",
            "100.00",
            "-19.99",
            "0.000001",
            "1.0000000",
            "12345678901234567890.12345",
        ).forEach { input ->
            val amount = Amount(input, "EUR")
            val json = RoutexJson.encodeToString(Amount.serializer(), amount)
            assertEquals("""{"amount":"$input","currency":"EUR"}""", json, "encode mismatch for '$input'")
            assertEquals(
                amount,
                RoutexJson.decodeFromString(Amount.serializer(), json),
                "round-trip mismatch for '$input'",
            )
        }
    }

    @Test
    fun normalizesNegativeZero() {
        // BigNum collapses -0 to 0; documenting so no caller is surprised by the wire
        // dropping the sign.
        assertEquals("0", Amount("-0", "EUR").amount.toPlainString())
        assertEquals("0.00", Amount("-0.00", "EUR").amount.toPlainString())
    }

    @Test
    fun normalizesNonCanonicalInput() {
        assertEquals("0.5", Amount(".5", "EUR").amount.toPlainString())
        assertEquals("1", Amount("1.", "EUR").amount.toPlainString())
        assertEquals("1000", Amount("1E3", "EUR").amount.toPlainString())
        assertEquals("1000.00", Amount("1_000.0_0", "EUR").amount.toPlainString())
    }

    @Test
    fun appliesExponent() {
        assertEquals("5000", Amount(".05E5", "EUR").amount.toPlainString())
        assertEquals("15.0", Amount("1.50E1", "EUR").amount.toPlainString())
        assertEquals("-15.0", Amount("-1.50E1", "EUR").amount.toPlainString())
        assertEquals("0.050", Amount("50E-3", "EUR").amount.toPlainString())
        assertEquals("0.0000000000000000000000000001", Amount("1E-28", "EUR").amount.toPlainString())
    }

    @Test
    fun rejectsUnparseableInput() {
        // BigNum throws undocumented exception types for some of these and, on JS,
        // parses "." as zero.
        listOf("", "abc", ".", "-.", "1..2", "_1", "1E29", "1E-29").forEach { input ->
            assertFailsWith<IllegalArgumentException>("for '$input'") { Amount(input, "EUR") }
        }
    }
}
