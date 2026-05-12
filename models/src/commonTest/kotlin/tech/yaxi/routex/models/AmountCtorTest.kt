package tech.yaxi.routex.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

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
    fun rejectsUnparseableInput() {
        // The ctor delegates to BigDecimal.parseString; clearly non-numeric inputs
        // throw rather than silently producing zero.
        assertFails { Amount("", "EUR") }
        assertFails { Amount("abc", "EUR") }
    }
}
