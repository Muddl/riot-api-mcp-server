package com.muddl.riot.tft.analytics.application;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.core.enums.RiotApiRegionUri;
import com.muddl.riot.tft.analytics.domain.PlayerMatchAnalytics;
import com.muddl.riot.tft.match.application.MatchService;
import com.muddl.riot.tft.match.domain.Participant;
import com.muddl.riot.tft.match.domain.TftMatch;
import com.muddl.riot.tft.match.domain.Trait;
import com.muddl.riot.tft.match.domain.Unit;
import com.muddl.riot.tft.summoner.application.SummonerService;
import com.muddl.riot.tft.summoner.domain.Summoner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes summoner + match data into TFT-native analytics: average placement, top-4 rate, and the
 * player's most-played traits and units over recent games.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final PlayerIdentityResolver identityResolver;
    private final SummonerService summonerService;
    private final MatchService matchService;

    public PlayerMatchAnalytics getPlayerMatchAnalytics(
            String player, RiotApiPlatformUri platform, RiotApiRegionUri region, int matchCount) {
        log.info("Generating TFT match analytics for player on platform: {}", platform);

        String puuid = identityResolver.resolvePuuid(player);
        Summoner summoner = summonerService.getSummonerByPuuid(platform, puuid);

        List<String> matchIds = matchService.getMatchIdsByPuuid(region, puuid, matchCount, 0);

        List<Participant> parts = new ArrayList<>();
        for (String matchId : matchIds) {
            TftMatch match = matchService.getMatchById(region, matchId);
            if (match == null || match.getInfo() == null || match.getInfo().getParticipants() == null) {
                continue;
            }
            for (Participant p : match.getInfo().getParticipants()) {
                if (puuid.equals(p.getPuuid())) {
                    parts.add(p);
                    break;
                }
            }
        }

        int total = parts.size();
        if (total == 0) {
            return PlayerMatchAnalytics.builder()
                    .riotId(player)
                    .summonerLevel(summoner == null ? 0 : summoner.getSummonerLevel())
                    .matchCount(0)
                    .build();
        }

        double avgPlacement =
                parts.stream().mapToInt(Participant::getPlacement).average().orElse(0);
        long top4 = parts.stream().filter(p -> p.getPlacement() <= 4).count();
        long firsts = parts.stream().filter(p -> p.getPlacement() == 1).count();
        double avgLevel =
                parts.stream().mapToInt(Participant::getLevel).average().orElse(0);
        double avgGoldLeft =
                parts.stream().mapToInt(Participant::getGoldLeft).average().orElse(0);

        return PlayerMatchAnalytics.builder()
                .riotId(player)
                .summonerLevel(summoner == null ? 0 : summoner.getSummonerLevel())
                .matchCount(total)
                .avgPlacement(String.format("%.2f", avgPlacement))
                .top4Rate(String.format("%.2f%%", (double) top4 / total * 100))
                .firstPlaceRate(String.format("%.2f%%", (double) firsts / total * 100))
                .avgLevel(String.format("%.2f", avgLevel))
                .avgGoldLeft(String.format("%.2f", avgGoldLeft))
                .mostPlayedTraits(topThreeTraits(parts))
                .mostPlayedUnits(topThreeUnits(parts))
                .build();
    }

    /** Top-3 active traits (tier_current > 0) by frequency across the analysed games. */
    private List<String> topThreeTraits(List<Participant> parts) {
        Map<String, Long> counts = parts.stream()
                .flatMap(p -> p.getTraits() == null ? java.util.stream.Stream.<Trait>empty() : p.getTraits().stream())
                .filter(t -> t.getTierCurrent() > 0)
                .collect(Collectors.groupingBy(Trait::getName, Collectors.counting()));
        return topThree(counts);
    }

    /** Top-3 fielded units by frequency across the analysed games. */
    private List<String> topThreeUnits(List<Participant> parts) {
        Map<String, Long> counts = parts.stream()
                .flatMap(p -> p.getUnits() == null ? java.util.stream.Stream.<Unit>empty() : p.getUnits().stream())
                .collect(Collectors.groupingBy(Unit::getCharacterId, Collectors.counting()));
        return topThree(counts);
    }

    private List<String> topThree(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + " games)")
                .collect(Collectors.toList());
    }
}
