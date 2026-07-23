package com.muddl.riot.lol.challenges.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.port.ChallengesPort;
import com.muddl.riot.lol.challenges.domain.ChallengeProgress;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import java.util.Comparator;
import java.util.List;
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

    /** Default number of individual challenges returned when the caller does not specify a count. */
    private static final int DEFAULT_CHALLENGES = 10;

    private final ChallengesPort challengesPort;
    private final PlayerIdentityResolver identityResolver;

    public ChallengesPlayerData getChallengesByPlayer(RiotApiPlatformUri platform, String player, Integer count) {
        String puuid = identityResolver.resolvePuuid(player);
        log.info("Fetching challenge data on platform: {}", platform);
        int limit = (count == null || count <= 0) ? DEFAULT_CHALLENGES : count;
        return boundChallenges(challengesPort.getPlayerDataByPuuid(platform, puuid), limit);
    }

    /**
     * Returns a copy of {@code data} holding only the {@code limit} strongest challenges, with
     * {@code totalChallenges} stamped to the pre-truncation size. {@code totalPoints} and
     * {@code categoryPoints} are always returned in full — they are the summary and cost almost
     * nothing, while Riot's per-challenge array runs to ~500 rows and is ~99% of the payload.
     *
     * <p>Sorted by percentile <em>ascending</em>, because a lower percentile is a rarer
     * achievement. The comparator must tolerate nulls: Riot omits {@code percentile} on challenges
     * a player has not progressed on. See ADR-0016.
     */
    private static ChallengesPlayerData boundChallenges(ChallengesPlayerData data, int limit) {
        if (data == null) {
            return null;
        }
        List<ChallengeProgress> challenges = data.getChallenges();
        if (challenges == null) {
            return data.toBuilder().challenges(List.of()).totalChallenges(0).build();
        }
        return data.toBuilder()
                .challenges(challenges.stream()
                        .sorted(Comparator.comparing(
                                ChallengeProgress::getPercentile, Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(limit)
                        .toList())
                .totalChallenges(challenges.size())
                .build();
    }
}
