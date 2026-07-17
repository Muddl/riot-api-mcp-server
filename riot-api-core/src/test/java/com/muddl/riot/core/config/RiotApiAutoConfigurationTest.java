package com.muddl.riot.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.core.http.BackoffSleeper;
import com.muddl.riot.core.http.RiotApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the library's auto-configuration actually registers what it claims. Without
 * this, a wrong AutoConfiguration.imports file fails silently here and only surfaces
 * downstream as a context-load error in whichever server needs the missing bean.
 */
class RiotApiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RiotApiAutoConfiguration.class))
            .withPropertyValues("riot.api-key=test-key-123");

    @Test
    void registers_riotApiClient_bean() {
        runner.run(context -> assertThat(context).hasSingleBean(RiotApiClient.class));
    }

    @Test
    void binds_riot_properties_from_configuration() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RiotApiProperties.class);
            assertThat(context.getBean(RiotApiProperties.class).getApiKey()).isEqualTo("test-key-123");
        });
    }

    @Test
    void region_defaults_to_americas_when_unset() {
        runner.run(context ->
                assertThat(context.getBean(RiotApiProperties.class).getRegion()).isEqualTo(RiotApiRegionUri.AMERICAS));
    }

    @Test
    void registers_backoff_sleeper_bean() {
        runner.run(context -> assertThat(context).hasSingleBean(BackoffSleeper.class));
    }

    @Test
    void retry_defaults_are_three_attempts_and_one_second() {
        runner.run(context -> {
            assertThat(context.getBean(RiotApiProperties.class).getMaxRetries()).isEqualTo(3);
            assertThat(context.getBean(RiotApiProperties.class).getRetryBackoff())
                    .isEqualTo(java.time.Duration.ofSeconds(1));
        });
    }

    @Test
    void auto_configuration_is_listed_in_the_imports_file() throws Exception {
        // The tests above pass the class in explicitly, which masks the real failure mode:
        // a missing or misspelled entry in AutoConfiguration.imports compiles fine and only
        // breaks in a consuming application. Assert the file's contents directly.
        var url = getClass()
                .getClassLoader()
                .getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertThat(url).as("AutoConfiguration.imports must be on the classpath").isNotNull();
        assertThat(new String(url.openStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                .contains(RiotApiAutoConfiguration.class.getName());
    }
}
