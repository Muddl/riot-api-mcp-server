package com.wkaiser.riot.lol;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Always-on smoke test that boots the full Spring application context — no real secrets,
 * no network access. It runs on every {@code ./gradlew build} and catches dependency-injection
 * regressions (missing beans, broken {@code @ConfigurationProperties} bindings) that unit tests
 * alone would miss.
 * <p>
 * Since the monorepo split this test carries extra weight: {@code riot-api-core} and
 * {@code riot-account-core} contribute their beans via auto-configuration rather than component
 * scanning, so a broken {@code AutoConfiguration.imports} file in either library surfaces here
 * and nowhere else in this module.
 * <p>
 * {@code riot.api-key} is supplied as a dummy because {@code application.yml} binds it from the
 * {@code RIOT_API_KEY} environment variable, which is absent in CI; without a value Spring fails
 * placeholder resolution at startup. No outbound HTTP call happens during context startup.
 */
@SpringBootTest(properties = {"riot.api-key=dummy-test-riot-api-key"})
@ActiveProfiles("sse")
class ApplicationContextLoadsTest {

    @Test
    void contextLoads() {
        // Intentionally empty: the assertion is that the Spring application context
        // starts successfully with the full bean graph wired up.
    }
}
