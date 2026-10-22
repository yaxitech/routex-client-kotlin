package tech.yaxi.routex.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.serialization.SerializationException

class StandingOrderTest {
    @Test
    fun decodesFullStandingOrder() {
        val order =
            RoutexJson.decodeFromString(
                StandingOrder.serializer(),
                """
                {
                  "startDate": "2023-02-01",
                  "endDate": "2024-01-01",
                  "executionRule": "FollowingWithinAMonth",
                  "frequency": {"months": 1},
                  "day": 1,
                  "skimmingLimit": {"currency": "EUR", "amount": "100"},
                  "endToEndId": "e2e",
                  "amount": {"currency": "EUR", "amount": "1000"},
                  "creditor": {"name": "Stefanie Müller-Schmitt", "iban": "DE89370400440532013000"},
                  "debtor": {"iban": "DE02120300000000202051"},
                  "remittanceInformation": ["Miete"],
                  "purposeCode": "RINP",
                  "additionalInformation": "info"
                }
                """,
            )

        assertEquals(LocalDate(2023, 2, 1), order.startDate)
        assertEquals(LocalDate(2024, 1, 1), order.endDate)
        assertEquals(ExecutionRule.FOLLOWING_WITHIN_A_MONTH, order.executionRule)
        assertEquals(Frequency.Months(1), order.frequency)
        assertEquals(1, order.day)
        assertEquals(Amount("100", "EUR"), order.skimmingLimit)
        assertEquals("e2e", order.endToEndId)
        assertEquals(Amount("1000", "EUR"), order.amount)
        assertEquals(Party(name = "Stefanie Müller-Schmitt", iban = "DE89370400440532013000"), order.creditor)
        assertEquals(Party(iban = "DE02120300000000202051"), order.debtor)
        assertEquals(listOf("Miete"), order.remittanceInformation)
        assertEquals("RINP", order.purposeCode)
        assertEquals("info", order.additionalInformation)
    }

    @Test
    fun decodesMinimalStandingOrder() {
        val order =
            RoutexJson.decodeFromString(
                StandingOrder.serializer(),
                """{"startDate": "2023-02-01", "amount": {"currency": "EUR", "amount": "55.08"}}""",
            )

        assertEquals(null, order.frequency)
        assertEquals(emptyList(), order.remittanceInformation)
        assertEquals(Amount("55.08", "EUR"), order.amount)
    }

    @Test
    fun decodesEveryFrequencyShape() {
        fun decode(json: String) = RoutexJson.decodeFromString(Frequency.serializer(), json)

        assertEquals(Frequency.Days(14), decode("""{"days": 14}"""))
        assertEquals(Frequency.Months(3), decode("""{"months": 3}"""))
        assertEquals(
            Frequency.VariableMonths(listOf(Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER)),
            decode("""{"variableMonths": [1, 4, 7, 10]}"""),
        )
    }

    @Test
    fun encodesMonthsAsNumbers() {
        assertEquals(
            """{"variableMonths":[1,12]}""",
            RoutexJson.encodeToString(
                Frequency.serializer(),
                Frequency.VariableMonths(listOf(Month.JANUARY, Month.DECEMBER)),
            ),
        )
        assertEquals("""{"days":7}""", RoutexJson.encodeToString(Frequency.serializer(), Frequency.Days(7)))
    }

    @Test
    fun rejectsMonthOutOfRange() {
        assertFailsWith<SerializationException> {
            RoutexJson.decodeFromString(Frequency.serializer(), """{"variableMonths": [13]}""")
        }
    }
}
