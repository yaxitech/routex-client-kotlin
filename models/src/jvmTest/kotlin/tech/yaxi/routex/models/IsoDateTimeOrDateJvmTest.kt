package tech.yaxi.routex.models

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class IsoDateTimeOrDateJvmTest {
    @Test
    fun ofJavaLocalDateSerializesToIsoDate() {
        val json =
            RoutexJson.encodeToString(
                IsoDateTimeOrDateSerializer,
                of(java.time.LocalDate.of(2026, 5, 6)),
            )
        assertEquals("\"2026-05-06\"", json)
    }

    @Test
    fun ofJavaLocalDateTimeSerializesToOffsetlessDateTime() {
        val json =
            RoutexJson.encodeToString(
                IsoDateTimeOrDateSerializer,
                of(java.time.LocalDateTime.of(2026, 5, 6, 14, 30, 45)),
            )
        assertEquals("\"2026-05-06T14:30:45\"", json)
    }

    @Test
    fun ofJavaOffsetDateTimeSerializesWithExplicitOffset() {
        val json =
            RoutexJson.encodeToString(
                IsoDateTimeOrDateSerializer,
                of(java.time.OffsetDateTime.of(2026, 5, 6, 14, 30, 45, 0, ZoneOffset.ofHours(2))),
            )
        assertEquals("\"2026-05-06T14:30:45+02:00\"", json)
    }

    @Test
    fun ofJavaOffsetDateTimeAtUtcSerializesToZulu() {
        // ZoneOffset.UTC must collapse to the wire's `Z` suffix (the serializer
        // distinguishes UtcOffset.ZERO from a literal `+00:00`).
        val json =
            RoutexJson.encodeToString(
                IsoDateTimeOrDateSerializer,
                of(java.time.OffsetDateTime.of(2026, 5, 6, 14, 30, 45, 0, ZoneOffset.UTC)),
            )
        assertEquals("\"2026-05-06T14:30:45Z\"", json)
    }
}
