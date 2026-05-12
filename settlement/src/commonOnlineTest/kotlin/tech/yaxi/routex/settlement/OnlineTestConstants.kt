package tech.yaxi.routex.settlement

/**
 * Platform-portable constants shared across online-test compilations. Kept in
 * `commonOnlineTest` so future Android host-test and Kotlin/Native online-test
 * compilations can reuse them without dragging in any platform-specific test
 * scaffolding bound to the JVM `SettlementOnlineTest`.
 */
internal object OnlineTestConstants {
    /** Expected byte length of the `sessionId` returned by a successful settlement. */
    const val SESSION_ID_BYTE_LENGTH: Int = 32
}
