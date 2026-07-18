package com.muddl.riot.lol.clash.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.application.ClashService;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClashToolTest {

    @Mock
    private ClashService mockService;

    @InjectMocks
    private ClashTool tool;

    private static final RiotApiPlatformUri PLATFORM = RiotApiPlatformUri.NA1;

    @Test
    void getClashByPlayer_passesPlatformAndPlayerThrough() {
        ClashPlayer player = ClashPlayer.builder().teamId("team-1").build();
        when(mockService.getClashByPlayer(PLATFORM, "Faker#KR1")).thenReturn(List.of(player));

        assertThat(tool.getClashByPlayer("NA1", "Faker#KR1")).containsExactly(player);
        verify(mockService).getClashByPlayer(PLATFORM, "Faker#KR1");
    }

    @Test
    void getClashByPlayer_invalidPlatform_throws() {
        assertThatThrownBy(() -> tool.getClashByPlayer("INVALID", "Faker#KR1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
