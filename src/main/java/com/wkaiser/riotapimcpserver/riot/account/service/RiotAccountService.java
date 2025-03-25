package com.wkaiser.riotapimcpserver.riot.account.service;

import com.wkaiser.riotapimcpserver.riot.account.dto.RiotAccount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Service for interacting with the Riot Account API.
 * Handles operations related to Riot global accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiotAccountService {
    private final RestClient riotRestClient;
    
    /**
     * Get Riot account information by Riot ID
     * @param gameName The game name portion of the Riot ID
     * @param tagLine The tag line portion of the Riot ID
     * @return Account information
     */
    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        log.info("Fetching account for Riot ID: {}#{}", gameName, tagLine);
        
        return riotRestClient.get()
                .uri("/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}", 
                     gameName, tagLine)
                .retrieve()
                .body(RiotAccount.class);
    }
    
    /**
     * Get Riot account information by PUUID
     * @param puuid The PUUID of the account
     * @return Account information
     */
    public RiotAccount getAccountByPuuid(String puuid) {
        log.info("Fetching account for PUUID: {}", puuid);
        
        return riotRestClient.get()
                .uri("/riot/account/v1/accounts/by-puuid/{puuid}", puuid)
                .retrieve()
                .body(RiotAccount.class);
    }
}
