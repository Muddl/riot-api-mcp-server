package com.wkaiser.riotapimcpserver.shared.config;

import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
@ConfigurationProperties(prefix = "riot")
public class RiotApiConfigurationProperties {
    public String apiKey;
    public RiotApiRegionUri region;
}
