package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DebtorAccountReferenceTest {
    @Test
    fun accountReferenceIsADebtorAccountReference() {
        val account: DebtorAccountReference = AccountReference("NL58YAXI1234567890", "EUR")
        assertIs<AccountReference>(account)
    }

    @Test
    fun encodesAccountReferenceAsPlainIbanShape() {
        val account: DebtorAccountReference = AccountReference("NL58YAXI1234567890", "EUR")
        val json = RoutexJson.encodeToString(DebtorAccountReferenceSerializer, account)
        assertEquals("""{"iban":"NL58YAXI1234567890","currency":"EUR"}""", json)
    }

    @Test
    fun encodesEncryptedIbanShape() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val account: DebtorAccountReference = EncryptedIbanReference(bytes, "EUR")
        val json = RoutexJson.encodeToString(DebtorAccountReferenceSerializer, account)
        assertEquals("""{"encryptedIban":"${Base64.Default.encode(bytes)}","currency":"EUR"}""", json)
    }

    @Test
    fun decodesPlainIbanShapeIntoAccountReference() {
        val decoded =
            RoutexJson.decodeFromString(
                DebtorAccountReferenceSerializer,
                """{"iban":"NL58YAXI1234567890","currency":"EUR"}""",
            )
        assertEquals(
            AccountReference("NL58YAXI1234567890", "EUR"),
            decoded,
        )
    }

    @Test
    fun decodesEncryptedIbanShape() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val decoded =
            RoutexJson.decodeFromString(
                DebtorAccountReferenceSerializer,
                """{"encryptedIban":"${Base64.Default.encode(bytes)}","currency":"EUR"}""",
            )
        val encrypted = assertIs<EncryptedIbanReference>(decoded)
        assertContentEquals(bytes, encrypted.bytes)
        assertEquals("EUR", encrypted.currency)
    }
}
