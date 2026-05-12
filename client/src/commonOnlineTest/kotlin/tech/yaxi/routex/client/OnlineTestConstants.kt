package tech.yaxi.routex.client

/**
 * Platform-portable constants shared across online-test compilations. Kept in
 * `commonOnlineTest` so future Android host-test and Kotlin/Native online-test
 * compilations can reuse them without dragging in the JVM-only BouncyCastle
 * dependency bound to [OnlineTestSupport].
 */
internal object OnlineTestConstants {
    /** The YAXI demo connection, scripted to hit every dialog code path in staging. */
    const val YAXI_DEMO_CONNECTION_ID: String = "connection-96386142-60e5-4ca9-abcf-944efce5bc1e"
}
