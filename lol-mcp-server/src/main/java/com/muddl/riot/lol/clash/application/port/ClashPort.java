package com.muddl.riot.lol.clash.application.port;

import com.muddl.riot.core.enums.RiotApiPlatformUri;
import com.muddl.riot.lol.clash.domain.ClashPlayer;
import java.util.List;

/** Outbound port for Riot Clash-V1 player registrations. Platform-routed. */
public interface ClashPort {

    /** A player's active Clash team registrations. Empty when the player has none. */
    List<ClashPlayer> getPlayersByPuuid(RiotApiPlatformUri platform, String puuid);
}
