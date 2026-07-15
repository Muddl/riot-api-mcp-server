package com.wkaiser.riotapimcpserver.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RiotApiPropertiesTest {

    @Test
    void binds_apiKey_and_region_from_properties() {
        var source = new MapConfigurationPropertySource(Map.of(
                "riot.api-key", "test-key-123",
                "riot.region", "europe"));

        RiotApiProperties props =
                new Binder(source).bind("riot", RiotApiProperties.class).get();

        assertThat(props.getApiKey()).isEqualTo("test-key-123");
        assertThat(props.getRegion()).isEqualTo(RiotApiRegionUri.EUROPE);
        assertThat(props.getBaseUrlOverride()).isNull();
    }

    @Test
    void region_defaults_to_americas_when_absent() {
        var source = new MapConfigurationPropertySource(Map.of("riot.api-key", "k"));

        RiotApiProperties props =
                new Binder(source).bind("riot", RiotApiProperties.class).get();

        assertThat(props.getRegion()).isEqualTo(RiotApiRegionUri.AMERICAS);
    }
}
