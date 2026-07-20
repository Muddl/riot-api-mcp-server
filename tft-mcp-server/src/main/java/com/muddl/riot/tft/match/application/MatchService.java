package com.muddl.riot.tft.match.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.match.application.port.MatchPort;
import com.muddl.riot.tft.match.domain.TftMatch;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for TFT match data. Player-keyed access resolves {@code player -> PUUID} via
 * the shared {@link PlayerIdentityResolver}; the PUUID-keyed overload remains for the analytics
 * composer, which resolves identity itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPort matchPort;
    private final PlayerIdentityResolver identityResolver;

    public List<String> getMatchIdsByPlayer(RiotApiRegionUri region, String player, Integer count, Integer start) {
        String puuid = identityResolver.resolvePuuid(player);
        return getMatchIdsByPuuid(region, puuid, count, start);
    }

    public List<String> getMatchIdsByPuuid(RiotApiRegionUri region, String puuid, Integer count, Integer start) {
        log.info("Fetching TFT match IDs for PUUID: {}", puuid);
        return matchPort.getMatchIdsByPuuid(region, puuid, count, start);
    }

    public TftMatch getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching TFT match detail for match ID: {}", matchId);
        return matchPort.getMatchById(region, matchId);
    }
}
