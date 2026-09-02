package tech.yaxi.routex.transport

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import tech.yaxi.routex.transport.internal.KtorHttpTransport

public actual fun defaultHttpTransport(): HttpTransport = KtorHttpTransport(HttpClient(Darwin))
