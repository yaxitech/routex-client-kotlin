package tech.yaxi.refresh.demo;

import java.util.Base64;

/** Three required environment variables for the YAXI integration environment. */
public record EnvCredentials(String apiUrl, String apiKeyId, byte[] apiKeySecret) {

    public static EnvCredentials fromEnv() {
        var apiUrl = require("YAXI_API_URL");
        var apiKeyId = require("YAXI_API_KEY_ID");
        var apiKeySecret = Base64.getDecoder().decode(require("YAXI_API_KEY_SECRET"));
        return new EnvCredentials(apiUrl, apiKeyId, apiKeySecret);
    }

    private static String require(String name) {
        var value = System.getenv(name);
        if (value == null || value.isBlank()) {
            System.err.println("Missing environment variable: " + name);
            System.err.println("See README.md for the required setup.");
            System.exit(1);
        }
        return value;
    }
}
