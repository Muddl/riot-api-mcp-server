package com.muddl.riot.tft.match.adapter.out.riot;

import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.core.http.RiotApiClient;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Riot TFT-Match-V1 API adapter. Match endpoints are region-routed. */
@Component
@RequiredArgsConstructor
public class RiotTftMatchAdapter implements MatchPort {

    private final RiotApiClient riotApiClient;

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start) {
        RestClient client = riotApiClient.regional(region);

        String uri = "/tft/match/v1/matches/by-puuid/{puuid}/ids?";
        if (count != null) {
            uri += "count=" + Math.min(count, 100) + "&";
        }
        if (start != null) {
            uri += "start=" + start + "&";
        }
        if (uri.endsWith("&") || uri.endsWith("?")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        return client.get().uri(uri, puuid).retrieve().body(List.class);
    }

    @Override
    public TftMatch getMatchById(RiotApiRegionUri region, String matchId) {
        return riotApiClient
                .regional(region)
                .get()
                .uri("/tft/match/v1/matches/{matchId}", matchId)
                .retrieve()
                .body(TftMatch.class);
    }
}
