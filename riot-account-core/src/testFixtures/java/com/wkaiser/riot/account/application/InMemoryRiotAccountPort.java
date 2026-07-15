package com.wkaiser.riot.account.application;

import com.wkaiser.riot.account.application.port.RiotAccountPort;
import com.wkaiser.riot.account.domain.RiotAccount;
import java.util.HashMap;
import java.util.Map;

/** Hand-written in-memory {@link RiotAccountPort} for fast, HTTP-free service tests. */
public class InMemoryRiotAccountPort implements RiotAccountPort {

    private final Map<String, RiotAccount> byRiotId = new HashMap<>();
    private final Map<String, RiotAccount> byPuuid = new HashMap<>();

    /** Registers an account under both its Riot ID and its PUUID (whichever are present). */
    public InMemoryRiotAccountPort add(RiotAccount account) {
        if (account.getGameName() != null && account.getTagLine() != null) {
            byRiotId.put(riotIdKey(account.getGameName(), account.getTagLine()), account);
        }
        if (account.getPuuid() != null) {
            byPuuid.put(account.getPuuid(), account);
        }
        return this;
    }

    @Override
    public RiotAccount getAccountByRiotId(String gameName, String tagLine) {
        return byRiotId.get(riotIdKey(gameName, tagLine));
    }

    @Override
    public RiotAccount getAccountByPuuid(String puuid) {
        return byPuuid.get(puuid);
    }

    private static String riotIdKey(String gameName, String tagLine) {
        return gameName + "#" + tagLine;
    }
}
