package com.wkaiser.riotapimcpserver.account.application.port;

import com.wkaiser.riotapimcpserver.account.domain.RiotAccount;

/** Outbound port for retrieving Riot account data. */
public interface RiotAccountPort {

    RiotAccount getAccountByRiotId(String gameName, String tagLine);

    RiotAccount getAccountByPuuid(String puuid);
}
