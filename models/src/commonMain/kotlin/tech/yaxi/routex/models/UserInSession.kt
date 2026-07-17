package tech.yaxi.routex.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Indicates that a user is in session for a non-interactive service call.
 *
 * When a user is in session, YAXI forwards the user's IP address to their bank. That
 * address is either this connection's own source IP ([OnThisConnection]) or an IP
 * address the caller provides ([At]).
 *
 * Without a user in session, banks limit how many requests a caller may make and
 * reject excess requests with an [AccessExceeded] error. A user in session lifts that
 * limit.
 */
@Serializable(with = UserInSessionSerializer::class)
public sealed interface UserInSession {
    /** The user's IP is this connection's own source IP. */
    public data object OnThisConnection : UserInSession

    /** The user is at [ipAddress]. */
    public data class At(
        public val ipAddress: String,
    ) : UserInSession
}

/**
 * Serializes [UserInSession] as a bare string: [UserInSession.OnThisConnection] as
 * `"connection"`, and [UserInSession.At] transparently as its IP address.
 *
 * @suppress
 */
public object UserInSessionSerializer : KSerializer<UserInSession> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UserInSession", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UserInSession,
    ): Unit =
        encoder.encodeString(
            when (value) {
                is UserInSession.OnThisConnection -> "connection"
                is UserInSession.At -> value.ipAddress
            },
        )

    override fun deserialize(decoder: Decoder): UserInSession =
        when (val wire = decoder.decodeString()) {
            "connection" -> UserInSession.OnThisConnection
            else -> UserInSession.At(wire)
        }
}
