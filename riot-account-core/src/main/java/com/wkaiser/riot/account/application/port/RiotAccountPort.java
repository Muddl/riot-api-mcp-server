package com.wkaiser.riot.account.application.port;

import com.wkaiser.riot.account.domain.RiotAccount;

/** Outbound port for retrieving Riot account data. */
public interface RiotAccountPort {

    RiotAccount getAccountByRiotId(String gameName, String tagLine);

    RiotAccount getAccountByPuuid(String puuid);
}
