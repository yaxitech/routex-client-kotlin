package tech.yaxi.refresh.demo

import java.util.Base64
import kotlin.system.exitProcess

/** Three required environment variables for the YAXI integration environment. */
data class EnvCredentials(val apiUrl: String, val apiKeyId: String, val apiKeySecret: ByteArray) {
    companion object {
        fun fromEnv(): EnvCredentials =
            EnvCredentials(
                apiUrl = require("YAXI_API_URL"),
                apiKeyId = require("YAXI_API_KEY_ID"),
                apiKeySecret = Base64.getDecoder().decode(require("YAXI_API_KEY_SECRET")),
            )

        private fun require(name: String): String =
            System.getenv(name).orEmpty().ifEmpty {
                System.err.println("Missing environment variable: $name")
                System.err.println("See README.md for the required setup.")
                exitProcess(1)
            }
    }
}
