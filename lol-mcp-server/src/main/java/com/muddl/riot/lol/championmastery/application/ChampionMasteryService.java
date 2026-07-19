package com.muddl.riot.lol.championmastery.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.championmastery.application.port.ChampionMasteryPort;
import com.muddl.riot.lol.championmastery.domain.ChampionMastery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot Champion-Mastery-V4 data. Player-keyed: resolves the caller's
 * {@code player} to a PUUID via the shared {@link PlayerIdentityResolver} before calling the port —
 * the {@code league} shape. Depends only on its own port and the resolver.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChampionMasteryService {

    private final ChampionMasteryPort masteryPort;
    private final PlayerIdentityResolver identityResolver;

    public List<ChampionMastery> getMasteryByPlayer(RiotApiPlatformUri platform, String player, Integer count) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching champion mastery on platform: {}", platform);
        return masteryPort.getMasteryByPuuid(platform, puuid, count);
    }
}
