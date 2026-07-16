package com.muddl.riot.account.application;

import com.muddl.riot.account.application.port.RiotAccountPort;
import com.muddl.riot.account.domain.RiotAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application service for Riot account lookups. Orchestrates account retrieval
 * through the outbound {@link RiotAccountPort}; holds no HTTP concerns.
 */
@Slf4j
@RequiredArgsConstructor
public class RiotAccountService {

    private final RiotAccountPort accountPort;

    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        log.info("Fetching account for Riot ID: {}#{}", gameName, tagLine);
        return accountPort.getAccountByRiotId(gameName, tagLine);
    }

    public RiotAccount getAccountByPuuid(String puuid) {
        log.info("Fetching account for PUUID: {}", puuid);
        return accountPort.getAccountByPuuid(puuid);
    }
}
