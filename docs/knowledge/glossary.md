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

## Teamfight Tactics (TFT) terms

Domain terms specific to `tft-mcp-server` (`com.muddl.riot.tft`), added in sub-project 2.

### Set

A TFT **set** is a periodic full reset of the game's traits, units, and augment pool —
Riot's equivalent of a new "season" of content rather than a balance patch. Match and
league data are set-scoped in practice (a player's rank and match history reflect the
current set), though the API itself does not require callers to pass a set identifier.

### Trait

A **trait** (a.k.a. "origin/class" in earlier sets) is a shared tag among **units** —
e.g. a champion might be tagged with two traits — that grants a team-wide bonus once
enough units sharing it are fielded. Modeled per match participant as `traits[]`:
`name`, `num_units` (how many fielded units carry it), `tier_current` (the bonus tier
currently active), and `style` (the visual tier indicator: bronze/silver/gold/chromatic).

### Unit

A **unit** is a TFT champion piece placed on the board. Modeled per match participant as
`units[]`: `character_id` (the champion identity), `tier` (1-, 2-, or 3-star, i.e. how
many copies were combined), `rarity` (gold cost), and `itemNames[]` (equipped item
completions). Not to be confused with LoL's `Participant` — TFT's unit shape is
structurally net-new, not a relocation of any LoL DTO.

### Augment

An **augment** is a random, once-per-round drafted power-up a player picks during a game
(distinct from items, which are itemized on units). Modeled as a per-participant list of
augment identifiers on the match domain; TFT's analytics intentionally does not aggregate
augment win rates in this server — see the design spec's non-goals.

### Placement

A participant's **placement** is their final standing in a TFT game, `1`–`8` (`1` is
first place, `8` is last), analogous to a battle-royale rank rather than LoL's
win/loss. It is the TFT equivalent of LoL's KDA as the headline per-game outcome stat —
`tft_analytics_player_matches` reports `avgPlacement` where the LoL analytics tool
reports KDA.

### Top-4

**Top-4** (or "cashing", informally) means placing `1`–`4` in an 8-player TFT lobby — the
threshold most competitive TFT play optimizes for, since only top-4 finishes gain league
points in ranked queues. `tft_analytics_player_matches` reports `top4Rate` as the share
of analyzed games meeting this threshold, alongside `firstPlaceRate` for outright wins.

### Rated ladder / Hyper Roll

A **rated ladder** is TFT's separate ranking track for non-standard-ranked queues —
currently Hyper Roll (`RANKED_TFT_TURBO`), a faster, gold-forward game mode with its own
skill rating (`ratedTier`/`ratedRating`) instead of the standard tier/division/LP system.
`tft_league_rated_ladder_by_queue` (TFT-League-V1 `/rated-ladders/{queue}/top`) exposes
the top of this ladder; it has no LoL analog since LoL has no equivalent alternate-queue
rating system exposed via league-v4.
