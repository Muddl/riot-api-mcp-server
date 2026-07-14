package com.wkaiser.riotapimcpserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Always-on smoke test that boots the full Spring application context.
 * <p>
 * Unlike the {@code @Disabled} {@code @SpringBootTest} classes elsewhere in this
 * repository (which require a live Riot API key and make real network calls), this
 * test verifies only that the entire bean graph wires up correctly - no real secrets,
 * no network access. It runs on every {@code ./gradlew build} and is intended to catch
 * dependency-injection regressions (missing beans, broken {@code @ConfigurationProperties}
 * bindings, etc.) that unit tests alone would miss.
 * <p>
 * Test properties supplied below exist solely to satisfy startup-time requirements
 * without touching real credentials or the network:
 * <ul>
 *     <li>{@code riot.api-key} - {@code application.yml} binds this from the
 *     {@code RIOT_API_KEY} environment variable ({@code riot.apiKey: ${RIOT_API_KEY}}).
 *     With no such variable set in CI/test environments, Spring fails to resolve the
 *     placeholder at startup. A dummy value here satisfies {@link
 *     com.wkaiser.riotapimcpserver.shared.config.RiotApiProperties} binding.</li>
 *     <li>{@code spring.ai.anthropic.api-key} - same placeholder-resolution problem for
 *     {@code spring.ai.anthropic.api-key: ${ANTHROPIC_API_KEY}}. The Anthropic
 *     autoconfiguration also validates that the key is non-blank when constructing its
 *     client beans, so a dummy (non-empty) value is required for the beans to build.</li>
 * </ul>
 * Both the Riot beans and the Spring AI MCP server / Anthropic chat model beans are left
 * enabled so the real bean graph - the thing this test exists to protect - is exercised.
 * No outbound HTTP call happens during context startup; the dummy Anthropic key only
 * needs to pass client-construction validation, not authenticate an actual request.
 */
@SpringBootTest(properties = {
        "riot.api-key=dummy-test-riot-api-key",
        "spring.ai.anthropic.api-key=dummy-test-anthropic-api-key"
})
class ApplicationContextLoadsTest {

    @Test
    void contextLoads() {
        // Intentionally empty: the assertion is that the Spring application context
        // starts successfully with the full bean graph wired up.
    }
}
