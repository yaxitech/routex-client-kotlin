package tech.yaxi.refresh.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Online smoke for the Java demo: runs {@link JavaDemo#main(String[])} against the
 * live YAXI Integration API and asserts that at least one balance line was printed.
 * Skips when the required {@code YAXI_API_*} environment variables are not set so
 * this same source stays usable in customer hands without forcing them to wire up
 * CI.
 */
@EnabledIfEnvironmentVariable(named = "YAXI_API_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "YAXI_API_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "YAXI_API_KEY_SECRET", matches = ".+")
class JavaDemoSmokeTest {

    @Test
    void mainPrintsAtLeastOneBalanceLine() {
        var captured = new ByteArrayOutputStream();
        var originalOut = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            JavaDemo.main(new String[] {});
        } finally {
            System.setOut(originalOut);
        }
        var output = captured.toString(StandardCharsets.UTF_8);
        assertTrue(
            output.lines().anyMatch(line -> line.contains(" EUR  ")),
            "expected a balance line of the form '<TYPE> <amount> EUR  (<iban>)', got:\n" + output);
    }
}
