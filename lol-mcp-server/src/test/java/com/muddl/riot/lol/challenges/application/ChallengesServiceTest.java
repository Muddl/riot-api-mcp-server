package com.muddl.riot.lol.challenges.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.domain.ChallengePoints;
import com.muddl.riot.lol.challenges.domain.ChallengeProgress;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ChallengesServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChallengesPort port = new InMemoryChallengesPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ChallengesService service = new ChallengesService(port, resolver);

    private static ChallengesPlayerData dataWith(List<ChallengeProgress> challenges) {
        return ChallengesPlayerData.builder()
                .totalPoints(ChallengePoints.builder().level("DIAMOND").build())
                .challenges(challenges)
                .build();
    }

    private static List<ChallengeProgress> progressOf(int size) {
        // percentile descending with index, so the *last* entries are the best —
        // a service that slices without sorting will fail these tests.
        return IntStream.range(0, size)
                .mapToObj(i -> ChallengeProgress.builder()
                        .challengeId((long) i)
                        .percentile(1.0 - (i / (double) size))
                        .build())
                .toList();
    }

    @Test
    void getChallengesByPlayer_capsAtTen_andKeepsSummary() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        port.put("faker-puuid", dataWith(progressOf(500)));

        ChallengesPlayerData result = service.getChallengesByPlayer(PLATFORM, "Faker#KR1", null);

        assertThat(result.getChallenges()).hasSize(10);
        assertThat(result.getTotalChallenges()).isEqualTo(500);
        assertThat(result.getTotalPoints().getLevel()).isEqualTo("DIAMOND");
    }

    @Test
    void getChallengesByPlayer_sortsByPercentileAscending() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", dataWith(progressOf(500)));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", 3).getChallenges())
                .extracting(ChallengeProgress::getChallengeId)
                .containsExactly(499L, 498L, 497L);
    }

    @Test
    void getChallengesByPlayer_honoursExplicitCount() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", dataWith(progressOf(500)));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", 42).getChallenges())
                .hasSize(42);
    }

    @Test
    void getChallengesByPlayer_zeroOrNegativeCount_clampsToDefault() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", dataWith(progressOf(500)));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", 0).getChallenges())
                .hasSize(10);
        assertThat(service.getChallengesByPlayer(PLATFORM, "p", -9).getChallenges())
                .hasSize(10);
    }

    @Test
    void getChallengesByPlayer_nullPercentiles_sortLastWithoutThrowing() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        List<ChallengeProgress> mixed = new ArrayList<>();
        mixed.add(ChallengeProgress.builder().challengeId(1L).percentile(null).build());
        mixed.add(ChallengeProgress.builder().challengeId(2L).percentile(0.5).build());
        mixed.add(ChallengeProgress.builder().challengeId(3L).percentile(0.01).build());
        port.put("p", dataWith(mixed));

        assertThat(service.getChallengesByPlayer(PLATFORM, "p", null).getChallenges())
                .extracting(ChallengeProgress::getChallengeId)
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void getChallengesByPlayer_nullChallenges_yieldsEmptyListAndZeroTotal() {
        when(resolver.resolvePuuid("p")).thenReturn("p");
        port.put("p", ChallengesPlayerData.builder().build());

        ChallengesPlayerData result = service.getChallengesByPlayer(PLATFORM, "p", null);

        assertThat(result.getChallenges()).isEmpty();
        assertThat(result.getTotalChallenges()).isZero();
    }

    @Test
    void getChallengesByPlayer_nullData_returnsNull() {
        when(resolver.resolvePuuid("unknown")).thenReturn("unknown");

        assertThat(service.getChallengesByPlayer(PLATFORM, "unknown", null)).isNull();
    }
}
