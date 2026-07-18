package com.muddl.riot.lol.match.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.lol.match.application.port.MatchPort;
import com.muddl.riot.lol.match.domain.Match;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for League of Legends match data. Delegates retrieval to the outbound
 * {@link MatchPort}; holds no HTTP concerns.
 *
 * <p>Player-keyed access ({@link #getMatchIdsByPlayer}) resolves the caller's {@code player} to a
 * PUUID via the shared {@link PlayerIdentityResolver} — the {@code league} shape. The PUUID-keyed
 * methods remain for internal composers such as {@code analytics}, which resolve identity themselves.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchPort matchPort;
    private final PlayerIdentityResolver identityResolver;

    public List<String> getMatchIdsByPlayer(
            RiotApiRegionUri region, String player, Integer count, Integer start, Integer queue) {
        String puuid = identityResolver.resolvePuuid(player);
        return getMatchIdsByPuuid(region, puuid, count, start, queue);
    }

    public List<String> getMatchIdsByPuuid(
            RiotApiRegionUri region, String puuid, Integer count, Integer start, Integer queue) {
        log.info("Fetching match IDs for PUUID: {}", puuid);
        return matchPort.getMatchIdsByPuuid(region, puuid, count, start, queue);
    }

    public Match getMatchById(RiotApiRegionUri region, String matchId) {
        log.info("Fetching match details for match ID: {}", matchId);
        return matchPort.getMatchById(region, matchId);
    }
}
