package com.muddl.riot.lol.challenges.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application service for Riot LoL-Challenges-V1 player data. Player-keyed: resolves {@code player}
 * to a PUUID via {@link PlayerIdentityResolver} before calling the port (the {@code league} shape).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengesService {

    private final ChallengesPort challengesPort;
    private final PlayerIdentityResolver identityResolver;

    public ChallengesPlayerData getChallengesByPlayer(RiotApiPlatformUri platform, String player) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching challenge data on platform: {}", platform);
        return challengesPort.getPlayerDataByPuuid(platform, puuid);
    }
}
