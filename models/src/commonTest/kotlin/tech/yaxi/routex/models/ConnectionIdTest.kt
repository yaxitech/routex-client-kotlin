package tech.yaxi.routex.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ConnectionIdTest {
    private val uuidString = "96386142-60e5-4ca9-abcf-944efce5bc1e"
    private val uuid = Uuid.parse(uuidString)

    @Test
    fun parsesPrefixedWireForm() {
        assertEquals(ConnectionId(uuid), ConnectionId("connection-$uuidString"))
    }

    @Test
    fun parsesBareUuidForm() {
        assertEquals(ConnectionId(uuid), ConnectionId(uuidString))
    }

    @Test
    fun toStringEmitsPrefixedForm() {
        assertEquals("connection-$uuidString", ConnectionId(uuid).toString())
    }

    @Test
    fun rejectsMalformedInput() {
        assertFails { ConnectionId("not-a-uuid") }
        assertFails { ConnectionId("connection-not-a-uuid") }
    }

    @Test
    fun roundTripsViaSerializer() {
        val id = ConnectionId(uuid)
        val encoded = RoutexJson.encodeToString(ConnectionId.serializer(), id)
        assertEquals("\"connection-$uuidString\"", encoded)
        assertEquals(id, RoutexJson.decodeFromString(ConnectionId.serializer(), encoded))
    }
}
