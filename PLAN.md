# Live Game State Monitor Tool - Implementation Plan

## Overview

This plan outlines the implementation of a **Live Game State Monitor Tool** for the Riot API MCP Server. This tool will provide real-time monitoring capabilities for ongoing League of Legends matches through the Riot Spectator API v4, enabling AI models to access live game information for analysis, coaching, and strategic applications.

## API Research Summary

### Important Finding: Spectator API Version
- **Current Version**: Spectator API **v4** (not v5)
- **Base URL**: `https://{platform}.api.riotgames.com/lol/spectator/v4/`
- **Key Limitation**: Live data does **NOT** include real-time stats like CS, KDA, items, or gold

### Available Endpoints
1. **Active Game by Summoner**: `/active-games/by-summoner/{encryptedSummonerId}`
2. **Featured Games**: `/featured-games`

### Live Data Available
- Game metadata (gameId, gameLength, gameMode, mapId)
- Participant information (champions, summoner spells, runes, team assignments)
- Banned champions
- Observer encryption key
- Game start time and elapsed time

### Live Data NOT Available
- Current CS (Creep Score)
- Current KDA (Kills/Deaths/Assists)
- Current items or builds
- Current gold amounts
- Real-time positioning or objective states

## Architecture Design

### Package Structure
```
riot/lol/spectator/
├── dto/
│   ├── CurrentGameInfo.java
│   ├── CurrentGameParticipant.java
│   ├── BannedChampion.java
│   ├── Observer.java
│   └── FeaturedGames.java
├── service/
│   └── SpectatorService.java
└── tool/
    └── LiveGameTool.java
```

### Integration Points

#### 1. RestClient Configuration
- **Reuse existing**: `RiotApiConfiguration.java` already provides base RestClient setup
- **Platform-specific**: Follow pattern from `SummonerService.java` for platform-specific clients
- **Error handling**: Leverage existing `RiotApiException` and `GlobalExceptionHandler`

#### 2. Service Layer Dependencies
- **SummonerService**: Required for summoner ID lookups (encrypted summoner ID needed for spectator API)
- **Platform handling**: Use existing `RiotApiPlatformUri` enum

#### 3. Tool Integration
- **MCP Tools**: Follow existing `@Tool` annotation pattern from `SummonerTool.java`
- **Logging**: Use existing SLF4J logging pattern
- **Parameter validation**: Follow existing enum-based platform validation

## Implementation Plan

### Phase 1: Core Data Transfer Objects (DTOs)

#### 1.1 Create `CurrentGameInfo.java`
```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrentGameInfo {
    private long gameId;
    private String gameType;
    private long gameStartTime;
    private long mapId;
    private long gameLength;
    private String platformId;
    private String gameMode;
    private List<BannedChampion> bannedChampions;
    private long gameQueueConfigId;
    private Observer observers;
    private List<CurrentGameParticipant> participants;
}
```

#### 1.2 Create `CurrentGameParticipant.java`
```java
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CurrentGameParticipant {
    private long championId;
    private Perks perks;
    private long profileIconId;
    private boolean bot;
    private long teamId;
    private String summonerName;
    private String summonerId;
    private long spell1Id;
    private long spell2Id;
    private GameCustomizationObject[] gameCustomizationObjects;
}
```

#### 1.3 Create supporting DTOs
- `BannedChampion.java`
- `Observer.java`
- `FeaturedGames.java`
- `Perks.java` (for rune information)

### Phase 2: Service Layer Implementation

#### 2.1 Create `SpectatorService.java`
```java
@Service
@RequiredArgsConstructor
public class SpectatorService {
    private final RestClient riotRestClient;

    public CurrentGameInfo getCurrentGameInfo(RiotApiPlatformUri platform, String encryptedSummonerId);
    public FeaturedGames getFeaturedGames(RiotApiPlatformUri platform);
    private RestClient createPlatformClient(RiotApiPlatformUri platform);
}
```

**Key Methods**:
- `getCurrentGameInfo()`: Get active game for specific summoner
- `getFeaturedGames()`: Get list of featured games
- `createPlatformClient()`: Helper for platform-specific RestClient creation

**Error Handling**:
- Handle 404 responses when summoner is not in game
- Provide meaningful error messages for AI models
- Log API calls for debugging

### Phase 3: MCP Tool Implementation

#### 3.1 Create `LiveGameTool.java`
```java
@Tool(name = "get_current_game_by_summoner_name", description = "Get current live game information for a summoner by name")
public CurrentGameInfo getCurrentGameBySummonerName(String platformStr, String summonerName);

@Tool(name = "get_current_game_by_summoner_id", description = "Get current live game information for a summoner by encrypted summoner ID")
public CurrentGameInfo getCurrentGameBySummonerId(String platformStr, String encryptedSummonerId);

@Tool(name = "get_featured_games", description = "Get list of current featured games on a platform")
public FeaturedGames getFeaturedGames(String platformStr);

@Tool(name = "check_if_summoner_in_game", description = "Check if a summoner is currently in a live game (returns boolean)")
public boolean isSummonerInGame(String platformStr, String summonerName);
```

**Tool Features**:
- **By Name**: Convenience method that combines summoner lookup + spectator call
- **By ID**: Direct spectator API call for when summoner ID is already known
- **Featured Games**: Access to high-profile matches
- **Status Check**: Simple boolean check for game status

### Phase 4: Integration and Testing

#### 4.1 Service Integration
- Integrate `SummonerService` for name-to-ID resolution
- Handle encrypted summoner ID requirements
- Implement proper error propagation

#### 4.2 Unit Testing
- Mock Riot API responses using existing test patterns
- Test error scenarios (404 for not in game)
- Validate DTO mapping accuracy

#### 4.3 Integration Testing
- Mark as `@Disabled` following project pattern
- Require valid API keys for execution
- Test with real summoner data

## Usage Examples for AI Models

### Example 1: Check Game Status
```
Tool: check_if_summoner_in_game
Parameters: {"platformStr": "NA1", "summonerName": "RiotSchmick"}
Response: true/false
```

### Example 2: Get Live Game Details
```
Tool: get_current_game_by_summoner_name
Parameters: {"platformStr": "NA1", "summonerName": "RiotSchmick"}
Response: {
  "gameId": 123456789,
  "gameLength": 450,
  "gameMode": "CLASSIC",
  "participants": [...],
  "bannedChampions": [...]
}
```

### Example 3: Monitor Featured Games
```
Tool: get_featured_games
Parameters: {"platformStr": "NA1"}
Response: {
  "gameList": [...],
  "clientRefreshInterval": 300
}
```

## Implementation Considerations

### Rate Limiting
- Respect Riot API rate limits
- Implement caching for featured games (5-minute refresh interval)
- Consider request throttling for high-frequency monitoring

### Error Handling
- 404 responses are normal (summoner not in game)
- Provide clear status messages for AI models
- Handle API timeouts gracefully

### Performance
- Minimize API calls by combining summoner lookup + spectator calls
- Cache summoner ID mappings temporarily
- Use platform-specific clients efficiently

### Data Limitations
- Clearly document what live data is NOT available
- Set proper expectations for AI model usage
- Consider future enhancements when real-time stats become available

## Future Enhancements

### Phase 5: Advanced Features (Future)
1. **Game Timeline Tracking**: Store and track game progression over time
2. **Multi-Game Monitoring**: Track multiple games simultaneously
3. **Event Notifications**: Alert on game state changes
4. **Champion Win Rate Integration**: Combine with match history for live predictions
5. **Team Analysis**: Enhanced team composition analysis for live games

### Phase 6: Real-time Integration (If API Supports)
1. **WebSocket Support**: If Riot provides real-time streams
2. **Live Statistics**: If real-time KDA/CS data becomes available
3. **Event Streaming**: Live game events and objectives

## Delivery Scope

### Minimum Viable Product (MVP)
- Core DTOs for spectator data
- SpectatorService with both endpoints
- LiveGameTool with 4 essential MCP tools
- Basic error handling and logging
- Unit tests with mocked responses

### Full Implementation
- Complete DTO mapping for all spectator data
- Comprehensive error handling
- Integration tests (disabled by default)
- Performance optimizations
- Documentation and usage examples

## Dependencies

### Required
- Existing `SummonerService` for ID resolution
- Existing `RiotApiConfiguration` for RestClient setup
- Existing exception handling framework

### New Dependencies
- No additional external dependencies required
- Leverages existing Spring Boot and Jackson setup

## Timeline Estimate

- **Phase 1 (DTOs)**: 1-2 hours
- **Phase 2 (Service)**: 2-3 hours
- **Phase 3 (Tools)**: 2-3 hours
- **Phase 4 (Testing)**: 2-3 hours
- **Total**: 7-11 hours

This Live Game State Monitor Tool will significantly enhance the MCP server's capabilities by providing real-time access to ongoing League of Legends matches, enabling AI models to perform live game analysis, coaching assistance, and strategic planning applications.