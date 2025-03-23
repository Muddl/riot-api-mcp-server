package com.wkaiser.riotapimcpserver.shared.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RiotApiRegionUri {
    AMERICAS("americas.api.riotgames.com"),
    ASIA("asia.api.riotgames.com"),
    EUROPE("europe.api.riotgames.com"),
    SEA("sea.api.riotgames.com");

    private final String regionUri;
}
