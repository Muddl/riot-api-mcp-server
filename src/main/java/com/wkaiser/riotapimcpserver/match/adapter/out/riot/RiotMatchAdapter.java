package com.wkaiser.riotapimcpserver.match.adapter.out.riot;

import com.wkaiser.riotapimcpserver.match.application.port.MatchPort;
import com.wkaiser.riotapimcpserver.match.domain.Match;
import com.wkaiser.riotapimcpserver.shared.enums.RiotApiRegionUri;
import com.wkaiser.riotapimcpserver.shared.http.RiotApiClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Riot Match-V5 API adapter. Match endpoints are region-routed. */
@Component
@RequiredArgsConstructor
public class RiotMatchAdapter implements MatchPort {

    private final RiotApiClient riotApiClient;

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getMatchIdsByPuuid(
            RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        RestClient client = riotApiClient.regional(region);

        String uri = "/lol/match/v5/matches/by-puuid/{puuid}/ids?";
        if (count != null) {
            uri += "count=" + Math.min(count, 100) + "&";
        }
        if (start != null) {
            uri += "start=" + start + "&";
        }
        if (queue != null) {
            uri += "queue=" + queue;
        }
        if (uri.endsWith("&") || uri.endsWith("?")) {
            uri = uri.substring(0, uri.length() - 1);
        }

        return client.get().uri(uri, puuid).retrieve().body(List.class);
    }

    @Override
    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        return riotApiClient
                .regional(region)
                .get()
                .uri("/lol/match/v5/matches/{matchId}", matchId)
                .retrieve()
                .body(Match.class);
    }
}
