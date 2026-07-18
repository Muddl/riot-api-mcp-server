package com.muddl.riot.lol.challenges.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muddl.riot.account.identity.PlayerIdentityResolver;
import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.domain.ChallengePoints;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import org.junit.jupiter.api.Test;

class ChallengesServiceTest {

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    private final InMemoryChallengesPort port = new InMemoryChallengesPort();
    private final PlayerIdentityResolver resolver = mock(PlayerIdentityResolver.class);
    private final ChallengesService service = new ChallengesService(port, resolver);

    @Test
    void getChallengesByPlayer_resolvesPlayer_thenReturnsData() {
        when(resolver.resolvePuuid("Faker#KR1")).thenReturn("faker-puuid");
        ChallengesPlayerData data = ChallengesPlayerData.builder()
                .totalPoints(ChallengePoints.builder().level("GOLD").build())
                .build();
        port.put("faker-puuid", data);

        assertThat(service.getChallengesByPlayer(PLATFORM, "Faker#KR1")).isSameAs(data);
    }
}
