@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package tech.yaxi.routex.models

import kotlin.io.encoding.Base64
import kotlin.jvm.JvmStatic
import kotlin.uuid.Uuid
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Identifier of a specific service connection (bank or other provider integration).
 *
 * Both `"connection-<uuid>"` and bare UUIDs are accepted when parsing from a string;
 * `toString` always emits the prefixed form.
 */
@Serializable(with = ConnectionIdSerializer::class)
public data class ConnectionId(
    public val uuid: Uuid,
) {
    /** Parses a string into a [ConnectionId]. Accepts both the prefixed and bare-UUID forms. */
    public constructor(value: String) : this(Uuid.parse(value.removePrefix(PREFIX)))

    override fun toString(): String = "$PREFIX$uuid"

    public companion object {
        public const val PREFIX: String = "connection-"

        /** Equivalent to the [String] secondary constructor; provided for Java callers. */
        @JvmStatic
        public fun parse(value: String): ConnectionId = ConnectionId(value)
    }
}

/** @suppress */
public object ConnectionIdSerializer : KSerializer<ConnectionId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.ConnectionId", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ConnectionId,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): ConnectionId = ConnectionId(decoder.decodeString())
}

/**
 * Opaque session token returned by a YAXI Open Banking service call.
 *
 * Pass it back on subsequent requests within the same logical session.
 */
@Serializable(with = SessionSerializer::class)
public class Session internal constructor(
    public val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is Session && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "Session(<${bytes.size} bytes>)"
}

/** @suppress */
public object SessionSerializer : KSerializer<Session> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.Session", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Session,
    ) {
        encoder.encodeString(Base64.Default.encode(value.bytes))
    }

    override fun deserialize(decoder: Decoder): Session = Session(Base64.Default.decode(decoder.decodeString()))
}

/**
 * Opaque connection data returned by a successful interactive service call.
 *
 * Persist it and pass it to the non-interactive refresh endpoints, or in place of
 * the [Credentials] of an interactive call, to reuse an established consent without
 * re-prompting the user.
 */
@Serializable(with = ConnectionDataSerializer::class)
public class ConnectionData internal constructor(
    public val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ConnectionData && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ConnectionData(<${bytes.size} bytes>)"
}

/** @suppress */
public object ConnectionDataSerializer : KSerializer<ConnectionData> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.ConnectionData", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ConnectionData,
    ) {
        encoder.encodeString(Base64.Default.encode(value.bytes))
    }

    override fun deserialize(decoder: Decoder): ConnectionData =
        ConnectionData(Base64.Default.decode(decoder.decodeString()))
}

/**
 * Trace identifier returned by the YAXI Open Banking service for the most recent
 * request, used by support to correlate a client-side failure with server-side traces.
 */
public class TraceId {
    public val bytes: ByteArray

    // Secondary (vs primary) so Dokka honors `@suppress`; `:settlement` constructs
    // TraceId across the module boundary, so `internal` isn't an option.

    /** @suppress */
    @InternalRoutexApi
    public constructor(bytes: ByteArray) {
        this.bytes = bytes
    }

    override fun equals(other: Any?): Boolean = other is TraceId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "TraceId(<${bytes.size} bytes>)"
}

/**
 * Opaque context returned alongside a `Confirmation` dialog, a [Redirect], or a
 * [RedirectHandle]. Pass it back to one of the `confirm*` methods on `RoutexClient`
 * to continue the flow.
 */
@Serializable(with = ConfirmationContextSerializer::class)
public class ConfirmationContext internal constructor(
    public val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is ConfirmationContext && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "ConfirmationContext(<${bytes.size} bytes>)"
}

/** @suppress */
public object ConfirmationContextSerializer : KSerializer<ConfirmationContext> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.ConfirmationContext", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: ConfirmationContext,
    ) {
        encoder.encodeString(Base64.Default.encode(value.bytes))
    }

    override fun deserialize(decoder: Decoder): ConfirmationContext =
        ConfirmationContext(Base64.Default.decode(decoder.decodeString()))
}

/**
 * Opaque context returned alongside a `Selection` or `Field` dialog input. Pass it
 * back to one of the `respond*` methods on `RoutexClient` together with the
 * user-supplied response to continue the flow.
 */
@Serializable(with = InputContextSerializer::class)
public class InputContext internal constructor(
    public val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is InputContext && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = "InputContext(<${bytes.size} bytes>)"
}

/** @suppress */
public object InputContextSerializer : KSerializer<InputContext> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("tech.yaxi.routex.models.InputContext", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: InputContext,
    ) {
        encoder.encodeString(Base64.Default.encode(value.bytes))
    }

    override fun deserialize(decoder: Decoder): InputContext =
        InputContext(Base64.Default.decode(decoder.decodeString()))
}
