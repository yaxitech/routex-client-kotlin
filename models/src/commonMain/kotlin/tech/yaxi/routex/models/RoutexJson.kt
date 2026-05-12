package tech.yaxi.routex.models

import kotlinx.serialization.json.Json

/** Project-wide JSON codec for YAXI Routex types. */
public val RoutexJson: Json =
    Json {
        ignoreUnknownKeys = true
    }
