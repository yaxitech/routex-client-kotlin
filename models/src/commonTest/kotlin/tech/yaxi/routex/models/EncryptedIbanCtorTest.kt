package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails

class EncryptedIbanCtorTest {
    private val bytes = byteArrayOf(1, 2, 3, 4, 5)
    private val base64 = Base64.Default.encode(bytes)

    @Test
    fun encryptedIbanReferenceFromBase64() {
        val decoded = EncryptedIbanReference(base64)
        assertContentEquals(bytes, decoded.bytes)
    }

    @Test
    fun encryptedIbanReferenceRejectsMalformedBase64() {
        assertFails { EncryptedIbanReference("!!!not base64!!!") }
    }

    @Test
    fun searchFilterEncryptedIbanFromBase64() {
        val decoded = SearchFilter.EncryptedIban(base64)
        assertContentEquals(bytes, decoded.bytes)
    }

    @Test
    fun searchFilterEncryptedIbanRejectsMalformedBase64() {
        assertFails { SearchFilter.EncryptedIban("!!!not base64!!!") }
    }
}
