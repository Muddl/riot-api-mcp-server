package com.muddl.riot.lol.status.adapter.in.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.status.application.StatusService;
import com.muddl.riot.lol.status.domain.PlatformStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatusToolTest {

    @Mock
    private StatusService mockStatusService;

    @InjectMocks
    private StatusTool statusTool;

    @Test
    void getPlatformStatus_passesPlatformThrough() {
        PlatformStatus status = PlatformStatus.builder().id("NA1").build();
        when(mockStatusService.getPlatformStatus(RiotApiPlatformUri.NA1)).thenReturn(status);

        assertThat(statusTool.getPlatformStatus("NA1")).isSameAs(status);
        verify(mockStatusService).getPlatformStatus(RiotApiPlatformUri.NA1);
    }

    @Test
    void getPlatformStatus_invalidPlatform_throws() {
        assertThatThrownBy(() -> statusTool.getPlatformStatus("INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No enum constant");
    }
}
