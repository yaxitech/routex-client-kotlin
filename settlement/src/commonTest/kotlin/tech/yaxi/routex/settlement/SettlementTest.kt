package tech.yaxi.routex.settlement

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tech.yaxi.routex.settlement.internal.x25519GenerateKeyPair

class SettlementTest {
    @Test
    fun requestBodyCarriesTheKeyVersion() {
        val x25519 = x25519GenerateKeyPair()
        assertRequestBody(ChaChaBoxKeys.V1(x25519.secret, x25519.public), x25519.public, 1)

        withMlKem {
            val keys = ChaChaBox.generateKeys() as ChaChaBoxKeys.V2
            assertRequestBody(keys, keys.x25519Public + keys.mlKemPublic, 2)
        }
    }

    private fun assertRequestBody(
        keys: ChaChaBoxKeys,
        publicKey: ByteArray,
        version: Int,
    ) {
        val body = Json.parseToJsonElement(settlementRequestBody(keys).decodeToString()).jsonObject
        assertEquals(Base64.encode(publicKey), body.getValue("publicKey").jsonPrimitive.content)
        assertEquals(version, body.getValue("version").jsonPrimitive.int)
    }
}
