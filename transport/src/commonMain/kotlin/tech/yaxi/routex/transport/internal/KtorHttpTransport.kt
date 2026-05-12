package tech.yaxi.routex.transport.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod as KtorHttpMethod
import io.ktor.http.content.ByteArrayContent
import tech.yaxi.routex.transport.HttpError
import tech.yaxi.routex.transport.HttpMethod
import tech.yaxi.routex.transport.HttpRequest
import tech.yaxi.routex.transport.HttpResponse
import tech.yaxi.routex.transport.HttpTransport

internal class KtorHttpTransport(
    private val client: HttpClient,
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        // Hand `Content-Type` to ByteArrayContent so ktor emits exactly one
        // header on the wire. Adding it via the headers block AND letting the
        // engine derive it from the body produces a duplicate per RFC 7230 §3.2.2.
        val contentType = request.headers[HttpHeaders.ContentType]?.let(ContentType::parse)
        val response =
            try {
                client.request(request.url) {
                    method =
                        when (request.method) {
                            HttpMethod.GET -> KtorHttpMethod.Get
                            HttpMethod.POST -> KtorHttpMethod.Post
                        }
                    headers {
                        request.headers.forEach { name, values ->
                            if (!name.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                                values.forEach { append(name, it) }
                            }
                        }
                    }
                    request.body?.let { setBody(ByteArrayContent(it, contentType)) }
                }
            } catch (e: Exception) {
                throw HttpError.TransportFailure("Ktor request failed: ${e.message}", e)
            }

        return HttpResponse(
            status = response.status.value,
            headers = response.headers,
            body = response.readRawBytes(),
        )
    }
}
