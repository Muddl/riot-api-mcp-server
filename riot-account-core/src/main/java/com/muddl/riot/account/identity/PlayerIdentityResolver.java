package com.muddl.riot.account.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.muddl.riot.account.application.RiotAccountService;
import com.muddl.riot.account.domain.RiotAccount;
import java.time.Duration;

/**
 * Resolves a caller-supplied {@code player} — either a {@code GameName#TAG} Riot ID or a raw PUUID —
 * to a PUUID. This is the one part of {@code riot-account-core} every game server is meant to depend
 * on: it lets tools take a single {@code player} parameter instead of forcing the model to chain
 * account → summoner → match itself.
 *
 * <p>It returns a plain PUUID {@code String}, deliberately not a {@link RiotAccount}: the account
 * domain stays confined (ArchUnit-enforced), and identity resolution is the open surface. Contexts
 * that want account <em>data</em> still go through {@code RiotAccountService}. See ADR-0008.
 *
 * <p>Resolution of a Riot ID is cached (Riot ID → PUUID) in a bounded, TTL-expiring Caffeine cache.
 * PUUIDs are stable, but Riot IDs are mutable, so the TTL bounds how stale a mapping can get. A raw
 * PUUID needs no lookup. The cache's {@link Ticker} is injected so tests advance time by hand.
 * Caffeine is an implementation detail — it appears here and in the auto-configuration, never in the
 * public {@link #resolvePuuid(String)} contract.
 */
public class PlayerIdentityResolver {

    private final RiotAccountService accountService;
    private final Cache<String, String> puuidByRiotId;

    public PlayerIdentityResolver(
            RiotAccountService accountService, Ticker ticker, Duration cacheTtl, int cacheMaxSize) {
        this.accountService = accountService;
        this.puuidByRiotId = Caffeine.newBuilder()
                .maximumSize(cacheMaxSize)
                .expireAfterWrite(cacheTtl)
                .ticker(ticker)
                .build();
    }

    /**
     * @param player a {@code GameName#TAG} Riot ID or a raw PUUID
     * @return the resolved PUUID
     * @throws IllegalArgumentException if {@code player} is blank, malformed, or names no account
     */
    public String resolvePuuid(String player) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        String trimmed = player.trim();
        if (trimmed.indexOf('#') < 0) {
            return trimmed; // already a PUUID — nothing to resolve, no Riot call, no cache entry
        }
        String[] parts = trimmed.split("#", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        // Trim each half so "Faker # KR1" normalizes to the same PUUID and cache key as "Faker#KR1"
        // (internal spaces in a game name are preserved — only surrounding whitespace is removed).
        String gameName = parts[0].trim();
        String tagLine = parts[1].trim();
        if (gameName.isBlank() || tagLine.isBlank()) {
            throw new IllegalArgumentException(unparseableMessage(player));
        }
        // get(key, loader) is atomic per key; a loader that throws (unknown Riot ID) propagates and
        // caches nothing, which is exactly the "do not cache failed lookups" behaviour we want.
        return puuidByRiotId.get(gameName + "#" + tagLine, key -> lookupPuuid(gameName, tagLine));
    }

    private String lookupPuuid(String gameName, String tagLine) {
        RiotAccount account = accountService.getAccountByRiotId(gameName, tagLine);
        if (account == null || account.getPuuid() == null || account.getPuuid().isBlank()) {
            throw new IllegalArgumentException("No Riot account found for Riot ID '" + gameName + "#" + tagLine + "'.");
        }
        return account.getPuuid();
    }

    private static String unparseableMessage(String player) {
        return "Cannot resolve player '" + player + "'. Provide a Riot ID as GameName#TAG "
                + "(for example Faker#KR1) or a raw PUUID.";
    }
}
