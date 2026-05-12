package tech.yaxi.refresh.demo

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Online smoke for the Kotlin demo: runs `main()` against the live YAXI Integration
 * API and asserts that at least one balance line was printed. Skips when the
 * required `YAXI_API_*` environment variables are not set so this same source
 * stays usable in customer hands without forcing them to wire up CI.
 */
@EnabledIfEnvironmentVariable(named = "YAXI_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "YAXI_API_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "YAXI_API_KEY_SECRET", matches = ".+")
class KotlinDemoSmokeTest {
    @Test
    fun mainPrintsAtLeastOneBalanceLine() {
        val captured = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        try {
            main()
        } finally {
            System.setOut(originalOut)
        }
        val output = captured.toString(Charsets.UTF_8)
        assertTrue(
            output.lineSequence().any { it.contains(" EUR  ") },
            "expected a balance line of the form '<TYPE> <amount> EUR  (<iban>)', got:\n$output",
        )
    }
}
