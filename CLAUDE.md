# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **Riot API MCP Server** - a Spring Boot application that serves as middleware between AI models and the Riot Games API. It exposes tools for retrieving and analyzing League of Legends game data through the Model Context Protocol (MCP), allowing AI models to access Riot API functionality via standardized tool calls.

## Common Development Commands

### Build and Test
- `./gradlew build` - Build the project and run all tests
- `./gradlew test` - Run JUnit 5 tests only
- `./gradlew clean` - Clean build artifacts
- `./gradlew bootRun` - Run the Spring Boot application locally

### Development Notes
- Integration tests are disabled by default (`@Disabled`) as they require valid Riot API keys
- The application runs on Java 21 with Spring Boot 3.4.4
- Lombok is used throughout for reducing boilerplate code

## Architecture Overview

### Tool-Based MCP Architecture
The application exposes functionality to AI models through Spring AI's `@Tool` annotation pattern:

- **RiotAccountTool**: Account lookup by Riot ID or PUUID
- **SummonerTool**: League of Legends summoner information
- **AnalyticsTool**: Advanced analytics combining multiple API endpoints

### Service Layer Structure
```
├── riot/
│   ├── account/ - Riot account management (cross-game)
│   ├── lol/
│   │   ├── summoner/ - LoL summoner data
│   │   ├── match/ - Match history and details
│   │   └── analytics/ - Advanced analytics engine
└── shared/ - Configuration, exceptions, enums
```

### Regional Architecture
- **Region Enums**: `RiotApiRegionUri` (AMERICAS, EUROPE, ASIA, SEA) for match/account data
- **Platform Enums**: `RiotApiPlatformUri` (NA1, EUW1, etc.) for summoner-specific data
- **RestClient Configuration**: Automatic regional routing with API key headers

### Analytics Engine
The `AnalyticsService` demonstrates the application's core value proposition:
1. Combines data from Account, Summoner, and Match APIs
2. Calculates comprehensive statistics (KDA, win rate, champion preferences)
3. Provides formatted, AI-friendly analytics responses
4. Handles edge cases (zero games, zero deaths for KDA)

## Configuration

### Required Environment Setup
- **Riot API Key**: Set in `application.yml` under `riot.apiKey`
- **Region**: Configure default region in `riot.region`
- **Anthropic API Key**: Required for AI integration (already configured)

### Key Configuration Files
- `application.yml`: Main configuration including API keys and MCP server settings
- `build.gradle`: Dependencies including Spring AI MCP starter
- `RiotApiConfiguration.java`: RestClient setup with error handling

## Development Patterns

### Tool Implementation Pattern
```java
@Tool(name = "tool_name", description = "Clear description for AI")
public ReturnType methodName(Parameters params) {
    log.info("MCP Tool - Action description");
    return service.performAction(params);
}
```

### Service Integration Pattern
Services orchestrate multiple API calls:
1. Account lookup (cross-game identifier)
2. Platform-specific data retrieval
3. Data aggregation and analysis
4. Formatted response generation

### Error Handling
- Custom `RiotApiException` for API errors
- `GlobalExceptionHandler` for consistent error responses
- HTTP status code preservation from Riot API

## Testing Strategy

- **Unit Tests**: Test individual service and tool methods
- **Integration Tests**: Disabled by default, require live API keys
- **Test Data**: Use placeholder Riot IDs in format "GameName#TAG"
- **Mocking**: Mock Riot API responses for reliable unit testing

## MCP Server Details

- **Type**: SYNC (synchronous request/response)
- **Endpoint**: `/mcp/messages` for SSE communication
- **Tools**: Auto-discovered via Spring AI's `@Tool` annotation scanning
- **Integration**: Designed for Claude and other AI models supporting MCP