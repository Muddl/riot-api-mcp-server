package com.muddl.riot.account.adapter.out.riot;

import com.muddl.riot.account.application.port.RiotAccountPort;
import com.muddl.riot.account.domain.RiotAccount;
import com.muddl.riot.core.config.RiotApiProperties;
import com.muddl.riot.core.http.RiotApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;

/** Riot Account API adapter. Account endpoints are region-routed. */
@RequiredArgsConstructor
public class RiotAccountRiotAdapter implements RiotAccountPort {

    private final RiotApiClient riotApiClient;
    private final RiotApiProperties properties;

    @Override
    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        RestClient client = riotApiClient.regional(properties.getRegion());
        return client.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", gameName, tagLine)
                .retrieve()
                .body(RiotAccount.class);
    }

    @Override
    public RiotAccount getAccountByPuuid(String puuid) {
        RestClient client = riotApiClient.regional(properties.getRegion());
        return client.get()
                .uri("/riot/account/v1/accounts/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotAccount.class);
    }
}
