package tech.yaxi.routex.transport

import io.ktor.http.Headers
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/**
 * Sample retrying transport. Wraps any [HttpTransport] and retries when:
 *   1. the underlying call throws [HttpError.TransportFailure] (network blip), or
 *   2. the response status is one of [retryStatuses] (default: 502/503/504).
 *
 * Backoff doubles per attempt, starting at [initialBackoffMillis], capped at
 * [maxAttempts] total attempts. Idempotent operations only, settlement and
 * GETs are safe; POSTs that mutate server state should be configured with
 * `retryStatuses = emptySet()` or wrapped at a higher layer.
 */
private class RetryingHttpTransport(
    private val delegate: HttpTransport,
    private val maxAttempts: Int = 3,
    private val initialBackoffMillis: Long = 10,
    private val retryStatuses: Set<Int> = setOf(502, 503, 504),
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse {
        var attempt = 1
        var backoff = initialBackoffMillis
        while (true) {
            val outcome =
                try {
                    delegate.execute(request)
                } catch (e: HttpError.TransportFailure) {
                    if (attempt >= maxAttempts) throw e
                    null
                }
            if (outcome != null && outcome.status !in retryStatuses) return outcome
            if (outcome != null && attempt >= maxAttempts) return outcome
            delay(backoff)
            backoff *= 2
            attempt += 1
        }
    }
}

private class ScriptedTransport(
    private val responses: List<Result<HttpResponse>>,
) : HttpTransport {
    var calls: Int = 0
        private set

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val idx = calls.coerceAtMost(responses.lastIndex)
        calls += 1
        return responses[idx].getOrThrow()
    }
}

private val DUMMY_REQUEST =
    HttpRequest(
        method = HttpMethod.GET,
        url = Url("https://example.invalid/path"),
    )

private fun ok(): HttpResponse = HttpResponse(status = 200, headers = Headers.Empty, body = byteArrayOf(0x42))

private fun status(code: Int): HttpResponse = HttpResponse(status = code, headers = Headers.Empty, body = ByteArray(0))

@OptIn(ExperimentalCoroutinesApi::class)
class RetryingHttpTransportTest {
    @Test
    fun successOnFirstAttemptIssuesNoRetry() =
        runTest {
            val scripted = ScriptedTransport(listOf(Result.success(ok())))
            val response = RetryingHttpTransport(scripted).execute(DUMMY_REQUEST)
            assertEquals(200, response.status)
            assertEquals(1, scripted.calls, "no retry should be needed for a 2xx")
        }

    @Test
    fun retriesOn503ThenSucceeds() =
        runTest {
            val scripted =
                ScriptedTransport(
                    listOf(
                        Result.success(status(503)),
                        Result.success(ok()),
                    ),
                )
            val response = RetryingHttpTransport(scripted).execute(DUMMY_REQUEST)
            assertEquals(200, response.status)
            assertEquals(2, scripted.calls)
        }

    @Test
    fun retriesOnTransportFailureThenSucceeds() =
        runTest {
            val scripted =
                ScriptedTransport(
                    listOf(
                        Result.failure(HttpError.TransportFailure("connection reset")),
                        Result.success(ok()),
                    ),
                )
            val response = RetryingHttpTransport(scripted).execute(DUMMY_REQUEST)
            assertEquals(200, response.status)
            assertEquals(2, scripted.calls)
        }

    @Test
    fun stopsAfterMaxAttemptsAndReturnsLast5xx() =
        runTest {
            val scripted = ScriptedTransport(listOf(Result.success(status(503))))
            val response =
                RetryingHttpTransport(scripted, maxAttempts = 3).execute(DUMMY_REQUEST)
            assertEquals(503, response.status, "after exhausting retries, surface the last 5xx")
            assertEquals(3, scripted.calls)
        }

    @Test
    fun stopsAfterMaxAttemptsAndPropagatesTransportFailure() =
        runTest {
            val scripted =
                ScriptedTransport(
                    listOf(Result.failure(HttpError.TransportFailure("eof"))),
                )
            val error =
                assertFailsWith<HttpError.TransportFailure> {
                    RetryingHttpTransport(scripted, maxAttempts = 2).execute(DUMMY_REQUEST)
                }
            assertEquals("eof", error.message)
            assertEquals(2, scripted.calls)
        }

    @Test
    fun nonRetryableErrorPassesThroughUnchanged() =
        runTest {
            val scripted = ScriptedTransport(listOf(Result.success(status(404))))
            val response = RetryingHttpTransport(scripted).execute(DUMMY_REQUEST)
            assertEquals(404, response.status)
            assertEquals(1, scripted.calls, "4xx is the caller's problem; no retry")
        }

    @Test
    fun backoffGrowsBetweenAttempts() =
        runTest {
            val scripted =
                ScriptedTransport(
                    listOf(
                        Result.success(status(503)),
                        Result.success(status(503)),
                        Result.success(ok()),
                    ),
                )
            val initialBackoff = 25L
            val before = currentTime
            RetryingHttpTransport(
                scripted,
                maxAttempts = 3,
                initialBackoffMillis = initialBackoff,
            ).execute(DUMMY_REQUEST)
            val elapsed = currentTime - before
            // After two retries the cumulative virtual delay is exactly 25 + 50 ms,
            // confirming the doubling backoff fires.
            assertEquals(initialBackoff + initialBackoff * 2, elapsed, "elapsed=$elapsed")
        }
}
