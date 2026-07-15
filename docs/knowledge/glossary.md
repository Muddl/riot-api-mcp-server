# Glossary

Riot / League of Legends domain terms used throughout the codebase.

## PUUID

**P**layer **U**niversally **U**nique **ID**entifier. A stable, encrypted, global player
id that is the same across all Riot games and regions. It is the primary key we thread
between contexts: `account` resolves a Riot ID to a PUUID, `summoner` and `match` are
then queried by PUUID. Modeled as a `String`.

## Riot ID (`gameName#tagLine`)

A player's human-readable, cross-game handle, written `gameName#tagLine` (e.g.
`Faker#KR1`). The `gameName` is the display name; the `tagLine` is a short discriminator.
The account context resolves a Riot ID to an account (and its PUUID) via
`/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}`. In tests use a placeholder
like `GameName#TAG`.

## Summoner

A League-of-Legends-specific profile on a particular **platform** (server): summoner
name, level, profile icon, encrypted summoner id, and PUUID. Retrieved from the
Summoner-V4 API. Note: a summoner exists per platform, whereas a PUUID/Riot ID is global.

## Platform vs region

Riot's API uses two routing schemes:

- **Platform** — a specific game server, e.g. `NA1`, `EUW1`, `KR` (`RiotApiPlatformUri`).
  Used for **summoner** and **spectator** endpoints, which are server-scoped.
- **Region** — a super-region aggregating platforms: `AMERICAS`, `EUROPE`, `ASIA`, `SEA`
  (`RiotApiRegionUri`). Used for **account** and **match** endpoints, which are
  region-scoped.

Choosing the wrong one yields 404s. See
[gotchas](gotchas.md#region-vs-platform-routing--do-not-mix-them-up).

## Spectator (live game)

The Spectator-V4 API exposes **currently in-progress** games: participants, champion
bans, and game metadata. It intentionally does **not** provide real-time CS, KDA, gold,
item builds, or positions. A `404` from the by-summoner endpoint means the player is not
in a game right now (mapped to `null` in the adapter, not an error).
