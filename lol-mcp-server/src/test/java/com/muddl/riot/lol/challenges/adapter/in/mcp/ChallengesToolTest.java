package com.muddl.riot.lol.challenges.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.challenges.application.ChallengesService;
import com.muddl.riot.lol.challenges.domain.ChallengesPlayerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChallengesToolTest {

    @Mock
    private ChallengesService mockService;

    @InjectMocks
    private ChallengesTool tool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getChallengesByPlayer_passesPlatformAndPlayerThrough() {
        ChallengesPlayerData data = ChallengesPlayerData.builder().build();
        when(mockService.getChallengesByPlayer(PLATFORM, "Faker#KR1", null)).thenReturn(data);

        assertThat(tool.getChallengesByPlayer("NA1", "Faker#KR1", null)).isSameAs(data);
        verify(mockService).getChallengesByPlayer(PLATFORM, "Faker#KR1", null);
    }

    @Test
    void getChallengesByPlayer_passesCountThrough() {
        ChallengesPlayerData data = ChallengesPlayerData.builder().build();
        when(mockService.getChallengesByPlayer(PLATFORM, "Faker#KR1", 5)).thenReturn(data);

        assertThat(tool.getChallengesByPlayer("NA1", "Faker#KR1", 5)).isSameAs(data);
        verify(mockService).getChallengesByPlayer(PLATFORM, "Faker#KR1", 5);
    }

    @Test
    void getChallengesByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getChallengesByPlayer("INVALID", "Faker#KR1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
